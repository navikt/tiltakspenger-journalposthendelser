package no.nav.tiltakspenger.journalposthendelser.journalpost.repository

import java.time.LocalDateTime

data class JournalposthendelseDB(
    val journalpostId: String,
    val fnr: String?,
    val saksnummer: String?,
    val brevkode: String?,
    val journalpostOppdatertTidspunkt: LocalDateTime?,
    val oppgaveId: String?,
    val oppgavetype: String?,
    val oppgaveOpprettetTidspunkt: LocalDateTime?,
    val opprettet: LocalDateTime,
    val sistEndret: LocalDateTime,
)
