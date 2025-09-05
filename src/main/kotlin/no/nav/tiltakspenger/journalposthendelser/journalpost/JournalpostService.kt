package no.nav.tiltakspenger.journalposthendelser.journalpost

import io.github.oshai.kotlinlogging.KotlinLogging

class JournalpostService(
    private val safJournalpostClient: SafJournalpostClient,
) {
    val log = KotlinLogging.logger {}

    suspend fun hentJournalpost(journalpostId: Long): JournalpostMetadata? {
        val journalpost = safJournalpostClient.getJournalpostMetadata(journalpostId.toString())

        log.info {
            """Journalpost $journalpostId,
                journalpostErIkkeJournalfort=${journalpost?.journalpostErIkkeJournalfort},
                datoOpprettet=${journalpost?.datoOpprettet},
                antallDokumenter=${journalpost?.dokumenter?.size ?: 0},
                brevkoder=${journalpost?.dokumenter?.mapNotNull { it.brevkode }},
            """.trimIndent()
        }

        return journalpost
    }
}
