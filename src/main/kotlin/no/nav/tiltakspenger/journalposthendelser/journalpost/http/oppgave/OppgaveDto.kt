package no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave

import java.time.DayOfWeek
import java.time.LocalDate

const val TEMA_TILTAKSPENGER: String = "IND"

data class OpprettOppgaveRequest(
    val personident: String?,
    val opprettetAvEnhetsnr: String = "9999",
    val journalpostId: String,
    val beskrivelse: String?,
    val tema: String = TEMA_TILTAKSPENGER,
    val oppgavetype: String,
    val aktivDato: LocalDate = LocalDate.now(),
    val fristFerdigstillelse: LocalDate = finnFristForFerdigstillingAvOppgave(LocalDate.now().plusDays(3)),
    val prioritet: PrioritetType = PrioritetType.NORM,
) {
    companion object {
        fun opprettOppgaveRequestForPapirsoknad(
            fnr: String,
            journalpostId: String,
        ) = OpprettOppgaveRequest(
            personident = fnr,
            journalpostId = journalpostId,
            beskrivelse = "Ny søknad om tiltakspenger mottatt på papir. Behandles i ny løsning.",
            oppgavetype = OppgaveType.BEHANDLE_SAK.kode,
        )

        fun opprettOppgaveRequestForJournalforingsoppgave(
            fnr: String,
            journalpostId: String,
            journalpostTittel: String,
        ) = OpprettOppgaveRequest(
            personident = fnr,
            journalpostId = journalpostId,
            beskrivelse = journalpostTittel,
            oppgavetype = OppgaveType.JOURNALFORING.kode,
        )

        fun opprettOppgaveRequestForFordelingsoppgave(
            journalpostId: String,
        ) = OpprettOppgaveRequest(
            personident = null,
            journalpostId = journalpostId,
            beskrivelse = null,
            oppgavetype = OppgaveType.FORDELING.kode,
        )
    }
}

fun finnFristForFerdigstillingAvOppgave(ferdigstillDato: LocalDate): LocalDate {
    return finnNesteArbeidsdag(ferdigstillDato)
}

fun finnNesteArbeidsdag(ferdigstillDato: LocalDate): LocalDate =
    when (ferdigstillDato.dayOfWeek) {
        DayOfWeek.SATURDAY -> ferdigstillDato.plusDays(2)
        DayOfWeek.SUNDAY -> ferdigstillDato.plusDays(1)
        else -> ferdigstillDato
    }

enum class PrioritetType {
    HOY,
    NORM,
    LAV,
}

data class OppgaveResponse(
    val id: Int,
)

enum class OppgaveType(val kode: String) {
    BEHANDLE_SAK("BEH_SAK"),
    JOURNALFORING("JFR"),
    FORDELING("FDR"),
}

data class FinnOppgaveResponse(
    val antallTreffTotalt: Int,
    val oppgaver: List<OppgaveResponse>,
)
