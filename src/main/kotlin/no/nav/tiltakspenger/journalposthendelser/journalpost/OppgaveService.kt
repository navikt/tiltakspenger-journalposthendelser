package no.nav.tiltakspenger.journalposthendelser.journalpost

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.JournalposthendelseIkkeBehandlet
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveType
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OpprettOppgaveRequest
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseDB
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseRepo
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.httpklient.loggFeil
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import java.time.Clock

class OppgaveService(
    private val oppgaveClient: OppgaveClient,
    private val journalposthendelseRepo: JournalposthendelseRepo,
    private val clock: Clock,
    private val sikkerlogg: Sikkerlogg = Sikkerlogg,
) {
    val log = KotlinLogging.logger {}

    suspend fun opprettOppgaveForPapirsoknad(
        journalposthendelseDB: JournalposthendelseDB,
        correlationId: CorrelationId,
    ): Either<JournalposthendelseIkkeBehandlet, Unit> {
        if (!journalposthendelseDB.harOpprettetOppgave() && journalposthendelseDB.harFerdigstiltJournalpost()) {
            val request = OpprettOppgaveRequest.opprettOppgaveRequestForPapirsoknad(
                fnr = journalposthendelseDB.fnr!!,
                journalpostId = journalposthendelseDB.journalpostId,
                clock = clock,
            )
            return opprettOppgaveMedDuplikatkontroll(request, correlationId)
                .map { oppgaveId ->
                    lagreOppgave(journalposthendelseDB, oppgaveId, OppgaveType.BEHANDLE_SAK)
                    log.info { "Opprettet behandle sak-oppgave med id $oppgaveId for journalpost med id ${journalposthendelseDB.journalpostId}" }
                }
        }
        log.warn { "Har allerede opprettet oppgave for journalpost med id ${journalposthendelseDB.journalpostId}" }
        return Unit.right()
    }

    suspend fun opprettJournalforingsoppgave(
        journalposthendelseDB: JournalposthendelseDB,
        tittel: String?,
        correlationId: CorrelationId,
    ): Either<JournalposthendelseIkkeBehandlet, Unit> {
        if (!journalposthendelseDB.harOpprettetOppgave() && journalposthendelseDB.harOppdatertJournalpost()) {
            val request = OpprettOppgaveRequest.opprettOppgaveRequestForJournalforingsoppgave(
                fnr = journalposthendelseDB.fnr!!,
                journalpostId = journalposthendelseDB.journalpostId,
                journalpostTittel = tittel ?: "Mottatt dokument",
                clock = clock,
            )
            return opprettOppgaveMedDuplikatkontroll(request, correlationId)
                .map { oppgaveId ->
                    lagreOppgave(journalposthendelseDB, oppgaveId, OppgaveType.JOURNALFORING)
                    log.info { "Opprettet journalføringsoppgave med id $oppgaveId for journalpost med id ${journalposthendelseDB.journalpostId}" }
                }
        }
        log.info { "Har allerede opprettet oppgave for journalpost med id ${journalposthendelseDB.journalpostId}" }
        return Unit.right()
    }

    suspend fun opprettFordelingsoppgave(
        journalposthendelseDB: JournalposthendelseDB,
        correlationId: CorrelationId,
    ): Either<JournalposthendelseIkkeBehandlet, Unit> {
        if (!journalposthendelseDB.harOpprettetOppgave() && journalposthendelseDB.manglerBruker()) {
            val request = OpprettOppgaveRequest.opprettOppgaveRequestForFordelingsoppgave(
                journalpostId = journalposthendelseDB.journalpostId,
                clock = clock,
            )
            return opprettOppgaveMedDuplikatkontroll(request, correlationId)
                .map { oppgaveId ->
                    lagreOppgave(journalposthendelseDB, oppgaveId, OppgaveType.FORDELING)
                    log.info { "Opprettet fordelingsoppgave med id $oppgaveId for journalpost med id ${journalposthendelseDB.journalpostId}" }
                }
        }
        log.info { "Har allerede opprettet oppgave for journalpost med id ${journalposthendelseDB.journalpostId}" }
        return Unit.right()
    }

    suspend fun finnesApenOppgave(
        journalpostId: JournalpostId,
        correlationId: CorrelationId,
    ): Either<JournalposthendelseIkkeBehandlet, Boolean> {
        return oppgaveClient.finnOppgaver(
            journalpostId = journalpostId,
            oppgavetyper = listOf(
                OppgaveType.BEHANDLE_SAK.kode,
                OppgaveType.JOURNALFORING.kode,
                OppgaveType.FORDELING.kode,
            ),
            correlationId = correlationId,
        ).mapLeft { feil ->
            feil.loggFeil(log, "søk etter åpen oppgave", "journalpostId: $journalpostId, correlationId: ${correlationId.value}", sikkerlogg)
            JournalposthendelseIkkeBehandlet
        }.map { oppgaveResponse ->
            val finnes = oppgaveResponse.antallTreffTotalt > 0 && oppgaveResponse.oppgaver.isNotEmpty()
            if (finnes) {
                log.warn { "Åpen oppgave for journalpostId: $journalpostId finnes fra før, correlationId: ${correlationId.value}" }
            }
            finnes
        }
    }

    /**
     * Oppgave-API-et har ingen innebygd dedup, så vi sjekker selv om det allerede finnes en åpen oppgave av samme type før vi oppretter.
     * Finnes den, gjenbrukes den eksisterende oppgave-id-en.
     */
    private suspend fun opprettOppgaveMedDuplikatkontroll(
        request: OpprettOppgaveRequest,
        correlationId: CorrelationId,
    ): Either<JournalposthendelseIkkeBehandlet, Int> {
        val journalpostId = JournalpostId(request.journalpostId)
        return oppgaveClient.finnOppgaver(
            journalpostId = journalpostId,
            oppgavetyper = listOf(request.oppgavetype),
            correlationId = correlationId,
        ).mapLeft { feil ->
            feil.loggFeil(log, "duplikatsjekk mot oppgave", "journalpostId: $journalpostId, correlationId: ${correlationId.value}", sikkerlogg)
            JournalposthendelseIkkeBehandlet
        }.flatMap { oppgaveResponse ->
            if (oppgaveResponse.antallTreffTotalt > 0 && oppgaveResponse.oppgaver.isNotEmpty()) {
                log.warn { "Åpen oppgave av type ${request.oppgavetype} for journalpostId: $journalpostId finnes fra før, correlationId: ${correlationId.value}" }
                oppgaveResponse.oppgaver.first().id.right()
            } else {
                oppgaveClient.opprettOppgave(request, correlationId).mapLeft { feil ->
                    feil.loggFeil(log, "oppretting av ${request.oppgavetype}-oppgave", "journalpostId: $journalpostId, correlationId: ${correlationId.value}", sikkerlogg)
                    JournalposthendelseIkkeBehandlet
                }
            }
        }
    }

    private fun lagreOppgave(
        journalposthendelseDB: JournalposthendelseDB,
        oppgaveId: Int,
        oppgavetype: OppgaveType,
    ) {
        val nå = nå(clock)
        journalposthendelseRepo.lagre(
            journalposthendelseDB.copy(
                oppgaveId = oppgaveId.toString(),
                oppgavetype = oppgavetype,
                oppgaveOpprettetTidspunkt = nå,
                sistEndret = nå,
            ),
        )
    }
}
