package no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave

import java.time.Clock
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
    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate = finnFristForFerdigstillingAvOppgave(aktivDato.plusDays(3)),
    val prioritet: PrioritetType = PrioritetType.NORM,
) {
    companion object {
        fun opprettOppgaveRequestForPapirsoknad(
            fnr: String,
            journalpostId: String,
            clock: Clock,
        ) = OpprettOppgaveRequest(
            personident = fnr,
            journalpostId = journalpostId,
            beskrivelse = "Ny søknad om tiltakspenger mottatt på papir. Behandles i ny løsning.",
            oppgavetype = OppgaveType.BEHANDLE_SAK.kode,
            aktivDato = LocalDate.now(clock),
        )

        fun opprettOppgaveRequestForJournalforingsoppgave(
            fnr: String,
            journalpostId: String,
            journalpostTittel: String,
            clock: Clock,
        ) = OpprettOppgaveRequest(
            personident = fnr,
            journalpostId = journalpostId,
            beskrivelse = journalpostTittel,
            oppgavetype = OppgaveType.JOURNALFORING.kode,
            aktivDato = LocalDate.now(clock),
        )

        fun opprettOppgaveRequestForFordelingsoppgave(
            journalpostId: String,
            clock: Clock,
        ) = OpprettOppgaveRequest(
            personident = null,
            journalpostId = journalpostId,
            beskrivelse = null,
            oppgavetype = OppgaveType.FORDELING.kode,
            aktivDato = LocalDate.now(clock),
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
