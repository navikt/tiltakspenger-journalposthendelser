package no.nav.tiltakspenger.journalposthendelser.journalpost

import java.time.LocalDateTime

data class JournalpostMetadata(
    val bruker: Bruker,
    val dokumenter: List<Dokument>?,
    val journalpostErIkkeJournalfort: Boolean,
    val datoOpprettet: LocalDateTime?,
    val dokumentInfoIdPdf: String?,
)
