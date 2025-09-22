package no.nav.tiltakspenger.journalposthendelser.journalpost

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveType
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseDB
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseRepo
import no.nav.tiltakspenger.libs.common.CorrelationId
import java.time.LocalDateTime

class OppgaveService(
    private val oppgaveClient: OppgaveClient,
    private val journalposthendelseRepo: JournalposthendelseRepo,
) {
    val log = KotlinLogging.logger {}

    suspend fun opprettOppgaveForPapirsoknad(
        journalposthendelseDB: JournalposthendelseDB,
        correlationId: CorrelationId,
    ) {
        if (!journalposthendelseDB.harOpprettetOppgave() && journalposthendelseDB.harFerdigstiltJournalpost()) {
            val oppgaveId = oppgaveClient.opprettOppgaveForPapirsoknad(
                fnr = journalposthendelseDB.fnr!!,
                journalpostId = journalposthendelseDB.journalpostId,
                correlationId = correlationId,
            )
            val oppdatertJournalposthendelseDB = journalposthendelseDB.copy(
                oppgaveId = oppgaveId.toString(),
                oppgavetype = OppgaveType.BEHANDLE_SAK,
                oppgaveOpprettetTidspunkt = LocalDateTime.now(),
                sistEndret = LocalDateTime.now(),
            )
            journalposthendelseRepo.lagre(oppdatertJournalposthendelseDB)
            log.info { "Opprettet behandle sak-oppgave for journalpost med id ${journalposthendelseDB.journalpostId}" }
        } else {
            log.warn { "Har allerede opprettet oppgave for journalpost med is ${journalposthendelseDB.journalpostId}" }
        }
    }

    suspend fun opprettJournalforingsoppgave(
        journalposthendelseDB: JournalposthendelseDB,
        tittel: String?,
        correlationId: CorrelationId,
    ) {
        if (!journalposthendelseDB.harOpprettetOppgave() && journalposthendelseDB.harOppdatertJournalpost()) {
            val oppgaveId = oppgaveClient.opprettJournalforingsoppgave(
                fnr = journalposthendelseDB.fnr!!,
                journalpostId = journalposthendelseDB.journalpostId,
                journalpostTittel = tittel ?: "Mottatt dokument",
                correlationId = correlationId,
            )
            val oppdatertJournalposthendelseDB = journalposthendelseDB.copy(
                oppgaveId = oppgaveId.toString(),
                oppgavetype = OppgaveType.JOURNALFORING,
                oppgaveOpprettetTidspunkt = LocalDateTime.now(),
                sistEndret = LocalDateTime.now(),
            )
            journalposthendelseRepo.lagre(oppdatertJournalposthendelseDB)
            log.info { "Opprettet journalføringsoppgave for journalpost med id ${journalposthendelseDB.journalpostId}" }
        } else {
            log.info { "Har allerede opprettet oppgave for journalpost med is ${journalposthendelseDB.journalpostId}" }
        }
    }

    suspend fun opprettFordelingsoppgave(
        journalposthendelseDB: JournalposthendelseDB,
        correlationId: CorrelationId,
    ) {
        if (!journalposthendelseDB.harOpprettetOppgave() && journalposthendelseDB.manglerBruker()) {
            val oppgaveId = oppgaveClient.opprettFordelingsoppgave(
                journalpostId = journalposthendelseDB.journalpostId,
                correlationId = correlationId,
            )
            val oppdatertJournalposthendelseDB = journalposthendelseDB.copy(
                oppgaveId = oppgaveId.toString(),
                oppgavetype = OppgaveType.FORDELING,
                oppgaveOpprettetTidspunkt = LocalDateTime.now(),
                sistEndret = LocalDateTime.now(),
            )
            journalposthendelseRepo.lagre(oppdatertJournalposthendelseDB)
            log.info { "Opprettet fordelingsoppgave for journalpost med id ${journalposthendelseDB.journalpostId}" }
        } else {
            log.info { "Har allerede opprettet oppgave for journalpost med is ${journalposthendelseDB.journalpostId}" }
        }
    }
}
