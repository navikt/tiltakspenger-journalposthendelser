package no.nav.tiltakspenger.journalposthendelser.journalpost.repository

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.Brevkode
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveType
import no.nav.tiltakspenger.journalposthendelser.testutils.shouldBeCloseTo
import no.nav.tiltakspenger.journalposthendelser.testutils.withMigratedDb
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class JournalposthendelseRepoTest {
    @Test
    fun `kan lagre og hente journalposthendelse`() {
        withMigratedDb { testDataHelper ->
            val repo = testDataHelper.journalposthendelseRepo
            val journalposthendelseDB = JournalposthendelseDB(
                journalpostId = "1234567",
                fnr = "12345678910",
                brevkode = Brevkode.SØKNAD.brevkode,
                saksnummer = "202509151003",
                journalpostOppdatertTidspunkt = LocalDateTime.now().minusMinutes(3),
                journalpostFerdigstiltTidspunkt = LocalDateTime.now().minusMinutes(2),
                oppgaveId = "900000",
                oppgavetype = OppgaveType.BEHANDLE_SAK,
                oppgaveOpprettetTidspunkt = LocalDateTime.now().minusMinutes(1),
                opprettet = LocalDateTime.now().minusMinutes(5),
                sistEndret = LocalDateTime.now(),
            )

            repo.lagre(journalposthendelseDB)

            val journalposthendelseFraDb = repo.hent(journalposthendelseDB.journalpostId)
            journalposthendelseFraDb?.fnr shouldBe journalposthendelseDB.fnr
            journalposthendelseFraDb?.brevkode shouldBe journalposthendelseDB.brevkode
            journalposthendelseFraDb?.saksnummer shouldBe journalposthendelseDB.saksnummer
            journalposthendelseFraDb?.journalpostOppdatertTidspunkt shouldBeCloseTo journalposthendelseDB.journalpostOppdatertTidspunkt
            journalposthendelseFraDb?.journalpostFerdigstiltTidspunkt shouldBeCloseTo journalposthendelseDB.journalpostFerdigstiltTidspunkt
            journalposthendelseFraDb?.oppgaveId shouldBe journalposthendelseDB.oppgaveId
            journalposthendelseFraDb?.oppgavetype shouldBe journalposthendelseDB.oppgavetype
            journalposthendelseFraDb?.oppgaveOpprettetTidspunkt shouldBeCloseTo journalposthendelseDB.oppgaveOpprettetTidspunkt
            journalposthendelseFraDb?.opprettet shouldBeCloseTo journalposthendelseDB.opprettet
            journalposthendelseFraDb?.sistEndret shouldBeCloseTo journalposthendelseDB.sistEndret
        }
    }

    @Test
    fun `kan oppdatere journalposthendelse`() {
        withMigratedDb { testDataHelper ->
            val repo = testDataHelper.journalposthendelseRepo
            val journalposthendelseDB = JournalposthendelseDB(
                journalpostId = "1234567",
                fnr = "12345678910",
                brevkode = Brevkode.SØKNAD.brevkode,
                saksnummer = "202509151003",
                journalpostOppdatertTidspunkt = LocalDateTime.now().minusMinutes(3),
                journalpostFerdigstiltTidspunkt = LocalDateTime.now().minusMinutes(2),
                oppgaveId = null,
                oppgavetype = null,
                oppgaveOpprettetTidspunkt = null,
                opprettet = LocalDateTime.now().minusMinutes(5),
                sistEndret = LocalDateTime.now().minusMinutes(2),
            )
            repo.lagre(journalposthendelseDB)

            val oppdatertJournalposthendelseDB = journalposthendelseDB.copy(
                oppgaveId = "900000",
                oppgavetype = OppgaveType.BEHANDLE_SAK,
                oppgaveOpprettetTidspunkt = LocalDateTime.now().minusMinutes(1),
                sistEndret = LocalDateTime.now(),
            )
            repo.lagre(oppdatertJournalposthendelseDB)

            val journalposthendelseFraDb = repo.hent(oppdatertJournalposthendelseDB.journalpostId)
            journalposthendelseFraDb?.fnr shouldBe oppdatertJournalposthendelseDB.fnr
            journalposthendelseFraDb?.brevkode shouldBe oppdatertJournalposthendelseDB.brevkode
            journalposthendelseFraDb?.saksnummer shouldBe oppdatertJournalposthendelseDB.saksnummer
            journalposthendelseFraDb?.journalpostOppdatertTidspunkt shouldBeCloseTo oppdatertJournalposthendelseDB.journalpostOppdatertTidspunkt
            journalposthendelseFraDb?.journalpostFerdigstiltTidspunkt shouldBeCloseTo oppdatertJournalposthendelseDB.journalpostFerdigstiltTidspunkt
            journalposthendelseFraDb?.oppgaveId shouldBe oppdatertJournalposthendelseDB.oppgaveId
            journalposthendelseFraDb?.oppgavetype shouldBe oppdatertJournalposthendelseDB.oppgavetype
            journalposthendelseFraDb?.oppgaveOpprettetTidspunkt shouldBeCloseTo oppdatertJournalposthendelseDB.oppgaveOpprettetTidspunkt
            journalposthendelseFraDb?.opprettet shouldBeCloseTo oppdatertJournalposthendelseDB.opprettet
            journalposthendelseFraDb?.sistEndret shouldBeCloseTo oppdatertJournalposthendelseDB.sistEndret
        }
    }
}
