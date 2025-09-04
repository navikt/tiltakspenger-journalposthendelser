package no.nav.tiltakspenger.journalposthendelser.saf

import java.time.LocalDateTime

data class JournalpostMetadata(
    val bruker: Bruker,
    @Deprecated("dokumenter skal ta over for denne") val dokumentInfoId: String?,
    val dokumenter: List<DokumentMedTittel>?,
    val jpErIkkeJournalfort: Boolean,
    val datoOpprettet: LocalDateTime?,
    val dokumentInfoIdPdf: String?,
)
