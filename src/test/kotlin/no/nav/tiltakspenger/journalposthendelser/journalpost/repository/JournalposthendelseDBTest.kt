package no.nav.tiltakspenger.journalposthendelser.journalpost.repository

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.Brevkode
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveType
import no.nav.tiltakspenger.journalposthendelser.testutils.fnrGenerator
import no.nav.tiltakspenger.libs.common.JournalpostId
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class JournalposthendelseDBTest {
    private val tidspunkt = LocalDateTime.of(2026, 7, 23, 12, 0)

    private fun journalposthendelseDB(
        fnr: String? = fnrGenerator.generer().verdi,
        brevkode: String? = Brevkode.KLAGE.brevkode,
        saksnummer: String? = "34567",
        journalpostOppdatertTidspunkt: LocalDateTime? = tidspunkt,
        journalpostFerdigstiltTidspunkt: LocalDateTime? = null,
        oppgaveId: String? = "9876",
        oppgavetype: OppgaveType? = OppgaveType.JOURNALFORING,
        oppgaveOpprettetTidspunkt: LocalDateTime? = tidspunkt,
    ) = JournalposthendelseDB(
        journalpostId = JournalpostId("4567"),
        fnr = fnr,
        saksnummer = saksnummer,
        brevkode = brevkode,
        journalpostOppdatertTidspunkt = journalpostOppdatertTidspunkt,
        journalpostFerdigstiltTidspunkt = journalpostFerdigstiltTidspunkt,
        oppgaveId = oppgaveId,
        oppgavetype = oppgavetype,
        oppgaveOpprettetTidspunkt = oppgaveOpprettetTidspunkt,
        opprettet = tidspunkt,
        sistEndret = tidspunkt,
    )

    @Test
    fun `papirsoknad med fnr er ferdig behandlet nar journalposten er oppdatert og ferdigstilt og oppgave er opprettet`() {
        journalposthendelseDB(
            brevkode = Brevkode.SØKNAD.brevkode,
            journalpostFerdigstiltTidspunkt = tidspunkt,
        ).erFerdigBehandlet() shouldBe true
    }

    @Test
    fun `papirsoknad med fnr er ikke ferdig behandlet uten ferdigstilt journalpost`() {
        journalposthendelseDB(
            brevkode = Brevkode.SØKNAD.brevkode,
        ).erFerdigBehandlet() shouldBe false
    }

    @Test
    fun `journalpost med fnr som ikke er papirsoknad er ferdig behandlet nar journalposten er oppdatert og oppgave er opprettet`() {
        journalposthendelseDB().erFerdigBehandlet() shouldBe true
    }

    @Test
    fun `journalpost med fnr som ikke er papirsoknad er ikke ferdig behandlet uten oppgave`() {
        journalposthendelseDB(
            oppgaveId = null,
            oppgavetype = null,
            oppgaveOpprettetTidspunkt = null,
        ).erFerdigBehandlet() shouldBe false
    }

    @Test
    fun `journalpost uten fnr er ferdig behandlet nar oppgave er opprettet`() {
        journalposthendelseDB(
            fnr = null,
            saksnummer = null,
            journalpostOppdatertTidspunkt = null,
        ).erFerdigBehandlet() shouldBe true
    }

    @Test
    fun `journalpost uten fnr er ikke ferdig behandlet uten oppgave`() {
        journalposthendelseDB(
            fnr = null,
            saksnummer = null,
            journalpostOppdatertTidspunkt = null,
            oppgaveId = null,
            oppgavetype = null,
            oppgaveOpprettetTidspunkt = null,
        ).erFerdigBehandlet() shouldBe false
    }
}
