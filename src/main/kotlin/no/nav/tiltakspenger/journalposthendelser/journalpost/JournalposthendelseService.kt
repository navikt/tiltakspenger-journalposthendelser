package no.nav.tiltakspenger.journalposthendelser.journalpost

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.journalposthendelser.Configuration
import no.nav.tiltakspenger.journalposthendelser.infra.MetricRegister
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.Brevkode
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.JournalpostMetadata
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.pdl.PdlClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saf.SafJournalpostClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseDB
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseRepo
import no.nav.tiltakspenger.libs.common.CorrelationId
import java.time.LocalDateTime

class JournalposthendelseService(
    private val safJournalpostClient: SafJournalpostClient,
    private val journalposthendelseRepo: JournalposthendelseRepo,
    private val pdlClient: PdlClient,
    private val journalpostService: JournalpostService,
    private val oppgaveService: OppgaveService,
) {
    val log = KotlinLogging.logger {}

    suspend fun behandleJournalpostHendelse(journalpostId: String) {
        val correlationId = CorrelationId.generate()
        val journalpostMetadata = safJournalpostClient.getJournalpostMetadata(journalpostId.toString())
            ?: throw IllegalStateException(
                "Unable to find journalpost with id $journalpostId",
            )

        log.info {
            """Journalpost journalpostId=$journalpostId,
                erJournalfort=${journalpostMetadata.erJournalfort},
                datoOpprettet=${journalpostMetadata.datoOpprettet},
                brevkode=${journalpostMetadata.brevkode},
            """.trimIndent()
        }

        val journalposthendelseDB = journalposthendelseRepo.hent(journalpostId)

        // Skal ikke behandle disse i prod ennå
        if (!Configuration.isProd()) {
            if (skalBehandleJournalposthendelse(journalposthendelseDB, journalpostMetadata, correlationId)) {
                log.info { "Behandler mottatt journalpost $journalpostId" }
                registrerMetrikker(journalpostMetadata)
                val journalposthendelseDBUnderArbeid = journalposthendelseDB ?: JournalposthendelseDB(
                    journalpostId = journalpostId,
                    brevkode = journalpostMetadata.brevkode,
                    opprettet = LocalDateTime.now(),
                    sistEndret = LocalDateTime.now(),
                )
                val fnr = hentIdent(journalpostMetadata)
                if (fnr == null) {
                    log.warn { "Fant ikke person for journalpost med id $journalpostId, oppretter fordelingsoppgave" }
                    oppgaveService.opprettFordelingsoppgave(journalposthendelseDBUnderArbeid, correlationId)
                } else {
                    val oppdatertJournalposthendelseDB = journalpostService.oppdaterEllerFerdigstillJournalpost(
                        journalposthendelseDB = journalposthendelseDBUnderArbeid.copy(fnr = fnr),
                        correlationId = correlationId,
                    )
                    if (oppdatertJournalposthendelseDB.gjelderPapirsoknad()) {
                        oppgaveService.opprettOppgaveForPapirsoknad(oppdatertJournalposthendelseDB, correlationId)
                    } else {
                        oppgaveService.opprettJournalforingsoppgave(
                            journalposthendelseDB = oppdatertJournalposthendelseDB,
                            tittel = journalpostMetadata.tittel,
                            correlationId = correlationId,
                        )
                    }
                }
                log.info { "Ferdig med å behandle mottatt journalpost $journalpostId" }
            } else {
                log.info { "Behandler ikke journalpost $journalpostId som er ferdig behandlet" }
            }
        } else {
            if (!journalpostMetadata.erJournalfort) {
                registrerMetrikker(journalpostMetadata)
            }
        }
    }

    private suspend fun skalBehandleJournalposthendelse(
        journalposthendelseDB: JournalposthendelseDB?,
        journalpostMetadata: JournalpostMetadata,
        correlationId: CorrelationId,
    ): Boolean {
        val finnesApenOppgave = oppgaveService.finnesApenOppgave(journalpostMetadata.journalpostId, correlationId)
        return (!journalpostMetadata.erJournalfort && !finnesApenOppgave) || (journalposthendelseDB != null && !journalposthendelseDB.erFerdigBehandlet())
    }

    private suspend fun hentIdent(journalpostMetadata: JournalpostMetadata): String? {
        if (journalpostMetadata.manglerBruker()) {
            log.warn { "Journalpost med id ${journalpostMetadata.journalpostId} mangler bruker" }
            return null
        }
        return journalpostMetadata.bruker.id?.let {
            pdlClient.hentGjeldendeIdent(
                fnr = it,
                journalpostId = journalpostMetadata.journalpostId,
            )
        }
    }

    private fun registrerMetrikker(journalpost: JournalpostMetadata) {
        if (journalpost.brevkode == Brevkode.SØKNAD.brevkode) {
            MetricRegister.SØKNAD_MOTTATT.inc()
        } else if (journalpost.brevkode == Brevkode.KLAGE.brevkode) {
            MetricRegister.KLAGE_MOTTATT.inc()
        } else if (journalpost.brevkode == Brevkode.MELDEKORT.brevkode) {
            MetricRegister.MELDEKORT_MOTTATT.inc()
        } else {
            log.info { "Annen brevkode mottatt: ${journalpost.brevkode}" }
            MetricRegister.ANNEN_BREVKODE_MOTTATT.inc()
        }
    }
}
