package no.nav.tiltakspenger.journalposthendelser.journalpost.repository

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.Brevkode
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveType
import no.nav.tiltakspenger.journalposthendelser.testutils.shouldBeCloseTo
import no.nav.tiltakspenger.journalposthendelser.testutils.withMigratedDb
import no.nav.tiltakspenger.libs.common.TikkendeKlokke
import no.nav.tiltakspenger.libs.common.nå
import org.junit.jupiter.api.Test
import java.time.Clock

class JournalposthendelseRepoTest {
    private val clock: Clock = TikkendeKlokke()

    @Test
    fun `kan lagre og hente journalposthendelse`() {
        withMigratedDb { testDataHelper ->
            val repo = testDataHelper.journalposthendelseRepo
            val journalposthendelseDB = JournalposthendelseDB(
                journalpostId = "1234567",
                fnr = "12345678910",
                brevkode = Brevkode.SØKNAD.brevkode,
                saksnummer = "202509151003",
                journalpostOppdatertTidspunkt = nå(clock).minusMinutes(3),
                journalpostFerdigstiltTidspunkt = nå(clock).minusMinutes(2),
                oppgaveId = "900000",
                oppgavetype = OppgaveType.BEHANDLE_SAK,
                oppgaveOpprettetTidspunkt = nå(clock).minusMinutes(1),
                opprettet = nå(clock).minusMinutes(5),
                sistEndret = nå(clock),
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
                journalpostOppdatertTidspunkt = nå(clock).minusMinutes(3),
                journalpostFerdigstiltTidspunkt = nå(clock).minusMinutes(2),
                oppgaveId = null,
                oppgavetype = null,
                oppgaveOpprettetTidspunkt = null,
                opprettet = nå(clock).minusMinutes(5),
                sistEndret = nå(clock).minusMinutes(2),
            )
            repo.lagre(journalposthendelseDB)

            val oppdatertJournalposthendelseDB = journalposthendelseDB.copy(
                oppgaveId = "900000",
                oppgavetype = OppgaveType.BEHANDLE_SAK,
                oppgaveOpprettetTidspunkt = nå(clock).minusMinutes(1),
                sistEndret = nå(clock),
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
