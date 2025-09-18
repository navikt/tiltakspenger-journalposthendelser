package no.nav.tiltakspenger.journalposthendelser.journalpost.http.dokarkiv

data class OppdaterJournalpostRequest(
    val sak: Sak?,
)

data class Sak(
    val fagsakId: String,
    val fagsaksystem: String = "TILTAKSPENGER",
    val sakstype: String = "FAGSAK",
)

data class FerdigstillJournalpostRequest(
    val journalfoerendeEnhet: String = "9999",
)
