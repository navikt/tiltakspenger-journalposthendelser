package no.nav.tiltakspenger.journalposthendelser.journalpost

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.journalposthendelser.infra.MetricRegister
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.Brevkode
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.JournalpostMetadata
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.JournalposthendelseIkkeBehandlet
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.pdl.KanIkkeHenteIdent
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.pdl.PdlClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saf.KanIkkeHenteJournalpost
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saf.SafJournalpostClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.kafka.JournalføringshendelseFraKafka
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseDB
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseRepo
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.httpklient.loggFeil
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import java.time.Clock

class JournalposthendelseService(
    private val safJournalpostClient: SafJournalpostClient,
    private val journalposthendelseRepo: JournalposthendelseRepo,
    private val pdlClient: PdlClient,
    private val journalpostService: JournalpostService,
    private val oppgaveService: OppgaveService,
    private val clock: Clock,
    private val sikkerlogg: Sikkerlogg = Sikkerlogg,
) {
    val log = KotlinLogging.logger {}

    suspend fun behandleJournalpostHendelse(
        hendelse: JournalføringshendelseFraKafka,
    ): Either<JournalposthendelseIkkeBehandlet, Unit> = either {
        val journalpostId = JournalpostId(hendelse.journalpostId)
        val correlationId = CorrelationId.generate()
        val journalpostMetadata = safJournalpostClient.getJournalpostMetadata(journalpostId)
            .mapLeft { feil ->
                feil.logg(journalpostId)
                JournalposthendelseIkkeBehandlet
            }
            .bind()

        log.info {
            """Journalpost journalpostId=$journalpostId,
                erJournalfort=${journalpostMetadata.erJournalfort},
                datoOpprettet=${journalpostMetadata.datoOpprettet},
                brevkode=${journalpostMetadata.brevkode},
            """.trimIndent()
        }
        if (journalpostMetadata.datoOpprettet == null) {
            log.warn { "Journalpost med id $journalpostId mangler gyldig datoOpprettet" }
        }
        if (journalpostMetadata.brevkode == null) {
            log.warn { "Journalpost med id $journalpostId mangler PDF eller brevkode" }
        }

        val journalposthendelseDB = journalposthendelseRepo.hent(journalpostId)
        val finnesApenOppgave = oppgaveService.finnesApenOppgave(journalpostId, correlationId).bind()
        val skalBehandles = (!journalpostMetadata.erJournalfort && !finnesApenOppgave) ||
            (journalposthendelseDB != null && !journalposthendelseDB.erFerdigBehandlet())
        if (!skalBehandles) {
            log.info { "Behandler ikke journalpost $journalpostId som er ferdig behandlet" }
            return@either
        }

        log.info { "Behandler mottatt journalpost $journalpostId" }
        registrerMetrikker(journalpostMetadata.brevkode)
        val nå = nå(clock)
        val journalposthendelseDBUnderArbeid = journalposthendelseDB ?: JournalposthendelseDB(
            journalpostId = journalpostId,
            brevkode = journalpostMetadata.brevkode,
            opprettet = nå,
            sistEndret = nå,
        )
        val fnr = hentIdent(journalpostMetadata).bind()
        if (fnr == null) {
            log.warn { "Fant ikke person for journalpost med id $journalpostId, oppretter fordelingsoppgave" }
            oppgaveService.opprettFordelingsoppgave(journalposthendelseDBUnderArbeid, correlationId).bind()
        } else {
            val oppdatertJournalposthendelseDB = journalpostService.oppdaterEllerFerdigstillJournalpost(
                journalposthendelseDB = journalposthendelseDBUnderArbeid.copy(fnr = fnr),
                correlationId = correlationId,
            ).bind()
            if (oppdatertJournalposthendelseDB.gjelderPapirsoknad()) {
                oppgaveService.opprettOppgaveForPapirsoknad(oppdatertJournalposthendelseDB, correlationId).bind()
            } else {
                oppgaveService.opprettJournalforingsoppgave(
                    journalposthendelseDB = oppdatertJournalposthendelseDB,
                    tittel = journalpostMetadata.tittel,
                    correlationId = correlationId,
                ).bind()
            }
        }
        log.info { "Ferdig med å behandle mottatt journalpost $journalpostId" }
        registrerMetrikker(journalpostMetadata.brevkode)
    }

    private suspend fun hentIdent(
        journalpostMetadata: JournalpostMetadata,
    ): Either<JournalposthendelseIkkeBehandlet, String?> {
        if (journalpostMetadata.manglerBruker()) {
            log.warn { "Journalpost med id ${journalpostMetadata.journalpostId} mangler bruker" }
            return null.right()
        }
        val brukerId = journalpostMetadata.bruker.id ?: return null.right()
        return pdlClient.hentGjeldendeIdent(brukerId).mapLeft { feil ->
            feil.logg(journalpostMetadata.journalpostId)
            JournalposthendelseIkkeBehandlet
        }
    }

    /** Én logghendelse per feilsituasjon: vanlig logg uten rådata, sikkerlogg med rå request/respons. */
    private fun KanIkkeHenteJournalpost.logg(journalpostId: JournalpostId) {
        when (this) {
            is KanIkkeHenteJournalpost.KallFeilet -> httpKlientError.loggFeil(
                logger = log,
                operasjon = "henting av journalpost fra SAF",
                kontekst = "journalpostId: $journalpostId",
                sikkerlogg = sikkerlogg,
            )

            is KanIkkeHenteJournalpost.GraphQLFeil -> {
                log.error { "SAF svarte med GraphQL-feil ved henting av journalpost $journalpostId: feilkoder=$feilkoder. ${sikkerlogg.seSikkerlogg}" }
                sikkerlogg.error { "SAF svarte med GraphQL-feil ved henting av journalpost $journalpostId: feilkoder=$feilkoder. response: ${httpKlientMetadata.rawResponseString}" }
            }

            is KanIkkeHenteJournalpost.UfullstendigJournalpost -> {
                log.error { "Journalpost $journalpostId fra SAF mangler journalstatus. ${sikkerlogg.seSikkerlogg}" }
                sikkerlogg.error { "Journalpost $journalpostId fra SAF mangler journalstatus. response: ${httpKlientMetadata.rawResponseString}" }
            }
        }
    }

    /** Én logghendelse per feilsituasjon: vanlig logg uten rådata, sikkerlogg med rå request/respons. */
    private fun KanIkkeHenteIdent.logg(journalpostId: JournalpostId) {
        when (this) {
            is KanIkkeHenteIdent.KallFeilet -> httpKlientError.loggFeil(
                logger = log,
                operasjon = "henting av gjeldende ident fra PDL",
                kontekst = "journalpostId: $journalpostId",
                sikkerlogg = sikkerlogg,
            )

            is KanIkkeHenteIdent.GraphQLFeil -> {
                log.error { "PDL svarte med GraphQL-feil ved henting av ident for journalpost $journalpostId: feilkoder=$feilkoder. ${sikkerlogg.seSikkerlogg}" }
                sikkerlogg.error { "PDL svarte med GraphQL-feil ved henting av ident for journalpost $journalpostId: feilkoder=$feilkoder. response: ${httpKlientMetadata.rawResponseString}" }
            }
        }
    }

    private fun registrerMetrikker(brevkode: String?) {
        when (brevkode) {
            Brevkode.SØKNAD.brevkode -> MetricRegister.SØKNAD_MOTTATT.inc()

            Brevkode.KLAGE.brevkode -> MetricRegister.KLAGE_MOTTATT.inc()

            Brevkode.MELDEKORT.brevkode -> MetricRegister.MELDEKORT_MOTTATT.inc()

            else -> {
                log.info { "Annen brevkode mottatt: $brevkode" }
                MetricRegister.ANNEN_BREVKODE_MOTTATT.inc()
            }
        }

        MetricRegister.JOURNALPOSTHENDELSE_MOTTATT.inc()
    }
}
