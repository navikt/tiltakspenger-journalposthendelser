package no.nav.tiltakspenger.journalposthendelser.journalpost

import io.github.oshai.kotlinlogging.KotlinLogging

class JournalpostService(
    private val safJournalpostClient: SafJournalpostClient,
) {
    val log = KotlinLogging.logger {}

    suspend fun hentJournalpost(journalpostId: Long): JournalpostMetadata? {
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

        /*
            TODO - utifra brevkode, må vi bestemme hva vi skal gjøre.
                Vi må filtrere vekk de som er ferdigstilt, og jobbe videre kun med dem som ikke er ferdigstilt.
                I utgangspunktet:
                    For klage: Så skal vi bare lage en oppgave i gosys. Kanskje denne appen skal gjøre det?
                    For søknader: Disse skal mest sannsynlig sendes til saksbeahndlings-api for videre behandling.

                Eksempel kodeverk:
                    NAV 76-13.45 - søknad
                    NAV 90-00.08 K - klage
         */

        return journalpost
    }
}
