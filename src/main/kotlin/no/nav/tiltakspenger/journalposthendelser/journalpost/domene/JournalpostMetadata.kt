package no.nav.tiltakspenger.journalposthendelser.journalpost.domene

import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saf.Bruker
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saf.Dokument
import java.time.LocalDateTime

data class JournalpostMetadata(
    val bruker: Bruker,
    val dokumenter: List<Dokument>?,
    val journalpostErIkkeJournalfort: Boolean,
    val datoOpprettet: LocalDateTime?,
    val dokumentInfoIdPdf: String?,
)
