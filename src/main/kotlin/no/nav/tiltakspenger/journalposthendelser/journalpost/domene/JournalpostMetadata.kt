package no.nav.tiltakspenger.journalposthendelser.journalpost.domene

import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saf.Bruker
import java.time.LocalDateTime

data class JournalpostMetadata(
    val journalpostId: String,
    val bruker: Bruker,
    val erJournalfort: Boolean,
    val datoOpprettet: LocalDateTime?,
    val brevkode: String?,
    val tittel: String?,
) {
    fun manglerBruker() = bruker.id == null || bruker.type == null
}
