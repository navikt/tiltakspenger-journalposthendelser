package no.nav.tiltakspenger.journalposthendelser.journalpost.repository

import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.Brevkode
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveType
import java.time.LocalDateTime

data class JournalposthendelseDB(
    val journalpostId: String,
    val fnr: String? = null,
    val saksnummer: String? = null,
    val brevkode: String? = null,
    val journalpostOppdatertTidspunkt: LocalDateTime? = null,
    val journalpostFerdigstiltTidspunkt: LocalDateTime? = null,
    val oppgaveId: String? = null,
    val oppgavetype: OppgaveType? = null,
    val oppgaveOpprettetTidspunkt: LocalDateTime? = null,
    val opprettet: LocalDateTime,
    val sistEndret: LocalDateTime,
) {
    fun gjelderPapirsoknad() = brevkode == Brevkode.SØKNAD.brevkode

    fun kanOppdatereJournalpost() = fnr != null

    fun harOppdatertJournalpost() = fnr != null && saksnummer != null && journalpostOppdatertTidspunkt != null

    fun harFerdigstiltJournalpost() = journalpostFerdigstiltTidspunkt != null

    fun manglerBruker() = fnr == null

    fun harOpprettetOppgave() = oppgaveId != null && oppgavetype != null && oppgaveOpprettetTidspunkt != null

    fun erFerdigBehandlet(): Boolean {
        return if (gjelderPapirsoknad() && fnr != null) {
            soknadErFerdigBehandlet()
        } else if (fnr != null) {
            journalpostMedFnrErFerdigBehandlet()
        } else {
            journalpostUtenFnrErFerdigBehandlet()
        }
    }

    private fun soknadErFerdigBehandlet() = harOppdatertJournalpost() && harFerdigstiltJournalpost() && harOpprettetOppgave()

    private fun journalpostMedFnrErFerdigBehandlet() = harOppdatertJournalpost() && harOpprettetOppgave()

    private fun journalpostUtenFnrErFerdigBehandlet() = harOpprettetOppgave()
}
