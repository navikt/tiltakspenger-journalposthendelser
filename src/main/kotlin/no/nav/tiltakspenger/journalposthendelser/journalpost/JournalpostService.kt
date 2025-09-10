package no.nav.tiltakspenger.journalposthendelser.journalpost

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.journalposthendelser.infra.MetricRegister

class JournalpostService(
    private val safJournalpostClient: SafJournalpostClient,
) {
    val log = KotlinLogging.logger {}

    suspend fun hentJournalpost(journalpostId: Long) {
        val journalpost = safJournalpostClient.getJournalpostMetadata(journalpostId.toString())
            ?: throw IllegalStateException(
                "Unable to find journalpost with id $journalpostId",
            )

        log.info {
            """Journalpost journalpostId=$journalpostId,
                journalpostErIkkeJournalfort=${journalpost.journalpostErIkkeJournalfort},
                datoOpprettet=${journalpost.datoOpprettet},
                antallDokumenter=${journalpost.dokumenter?.size ?: 0},
                brevkoder=${journalpost.dokumenter?.mapNotNull { it.brevkode }},
            """.trimIndent()
        }

        if (journalpost.journalpostErIkkeJournalfort) {
            val brevkoder = journalpost.dokumenter?.mapNotNull { it.brevkode } ?: emptyList()

            if (brevkoder.contains(Brevkode.SØKNAD.brevkode)) {
                prosesserSøknad(journalpost)
            } else if (brevkoder.contains(Brevkode.KLAGE.brevkode)) {
                prosesserKlage(journalpost)
            } else {
                MetricRegister.ANNEN_BREVKODE_MOTTATT.inc()
            }
        }
    }

    // TODO Kan vi anta at det er papirsøknad hvis den ikke er journalført?
    private fun prosesserSøknad(journalpost: JournalpostMetadata) {
        MetricRegister.SØKNAD_MOTTATT.inc()
        // TODO Opprett oppgave i Gosys eller varsle om det i benken i tp-sak?
    }

    private fun prosesserKlage(journalpost: JournalpostMetadata) {
        MetricRegister.KLAGE_MOTTATT.inc()
        // TODO Opprett oppgave i Gosys?
    }
}
