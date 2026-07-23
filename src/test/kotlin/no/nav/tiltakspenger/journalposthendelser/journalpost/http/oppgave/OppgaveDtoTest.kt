package no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class OppgaveDtoTest {
    @Test
    fun `frist pa en hverdag beholdes`() {
        val fredag = LocalDate.of(2026, 7, 24)

        finnFristForFerdigstillingAvOppgave(fredag) shouldBe fredag
    }

    @Test
    fun `frist pa en lordag flyttes til mandag`() {
        finnFristForFerdigstillingAvOppgave(LocalDate.of(2026, 7, 25)) shouldBe LocalDate.of(2026, 7, 27)
    }

    @Test
    fun `frist pa en sondag flyttes til mandag`() {
        finnFristForFerdigstillingAvOppgave(LocalDate.of(2026, 7, 26)) shouldBe LocalDate.of(2026, 7, 27)
    }
}
