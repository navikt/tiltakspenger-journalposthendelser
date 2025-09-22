package no.nav.tiltakspenger.journalposthendelser.journalpost.http.dokarkiv

import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.TEMA_TILTAKSPENGER

data class OppdaterJournalpostRequest(
    val sak: Sak,
    val bruker: Bruker,
    val tema: String = TEMA_TILTAKSPENGER,
) {
    data class Bruker(
        val id: String,
        val idType: String = "FNR",
    )
}

data class Sak(
    val fagsakId: String,
    val fagsaksystem: String = "TILTAKSPENGER",
    val sakstype: String = "FAGSAK",
)

data class FerdigstillJournalpostRequest(
    val journalfoerendeEnhet: String = "9999",
)
