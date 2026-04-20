package no.nav.tiltakspenger.journalposthendelser.journalpost.repository

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.Brevkode
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveType
import no.nav.tiltakspenger.journalposthendelser.testutils.TestDataHelper
import no.nav.tiltakspenger.journalposthendelser.testutils.shouldBeCloseTo
import no.nav.tiltakspenger.journalposthendelser.testutils.withMigratedDb
import no.nav.tiltakspenger.libs.common.JournalpostId
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
                journalpostId = JournalpostId("1234567"),
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

            hentLagretJournalpostId(testDataHelper, journalposthendelseDB.journalpostId) shouldBe journalposthendelseDB.journalpostId.toString()

            val journalposthendelseFraDb = repo.hent(journalposthendelseDB.journalpostId)
            journalposthendelseFraDb.shouldNotBeNull()
            journalposthendelseFraDb.journalpostId shouldBe journalposthendelseDB.journalpostId
            journalposthendelseFraDb.fnr shouldBe journalposthendelseDB.fnr
            journalposthendelseFraDb.brevkode shouldBe journalposthendelseDB.brevkode
            journalposthendelseFraDb.saksnummer shouldBe journalposthendelseDB.saksnummer
            journalposthendelseFraDb.journalpostOppdatertTidspunkt shouldBeCloseTo journalposthendelseDB.journalpostOppdatertTidspunkt
            journalposthendelseFraDb.journalpostFerdigstiltTidspunkt shouldBeCloseTo journalposthendelseDB.journalpostFerdigstiltTidspunkt
            journalposthendelseFraDb.oppgaveId shouldBe journalposthendelseDB.oppgaveId
            journalposthendelseFraDb.oppgavetype shouldBe journalposthendelseDB.oppgavetype
            journalposthendelseFraDb.oppgaveOpprettetTidspunkt shouldBeCloseTo journalposthendelseDB.oppgaveOpprettetTidspunkt
            journalposthendelseFraDb.opprettet shouldBeCloseTo journalposthendelseDB.opprettet
            journalposthendelseFraDb.sistEndret shouldBeCloseTo journalposthendelseDB.sistEndret
        }
    }

    @Test
    fun `kan oppdatere journalposthendelse`() {
        withMigratedDb { testDataHelper ->
            val repo = testDataHelper.journalposthendelseRepo
            val journalposthendelseDB = JournalposthendelseDB(
                journalpostId = JournalpostId("1234567"),
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

            hentLagretJournalpostId(testDataHelper, oppdatertJournalposthendelseDB.journalpostId) shouldBe oppdatertJournalposthendelseDB.journalpostId.toString()

            val journalposthendelseFraDb = repo.hent(oppdatertJournalposthendelseDB.journalpostId)
            journalposthendelseFraDb.shouldNotBeNull()
            journalposthendelseFraDb.journalpostId shouldBe oppdatertJournalposthendelseDB.journalpostId
            journalposthendelseFraDb.fnr shouldBe oppdatertJournalposthendelseDB.fnr
            journalposthendelseFraDb.brevkode shouldBe oppdatertJournalposthendelseDB.brevkode
            journalposthendelseFraDb.saksnummer shouldBe oppdatertJournalposthendelseDB.saksnummer
            journalposthendelseFraDb.journalpostOppdatertTidspunkt shouldBeCloseTo oppdatertJournalposthendelseDB.journalpostOppdatertTidspunkt
            journalposthendelseFraDb.journalpostFerdigstiltTidspunkt shouldBeCloseTo oppdatertJournalposthendelseDB.journalpostFerdigstiltTidspunkt
            journalposthendelseFraDb.oppgaveId shouldBe oppdatertJournalposthendelseDB.oppgaveId
            journalposthendelseFraDb.oppgavetype shouldBe oppdatertJournalposthendelseDB.oppgavetype
            journalposthendelseFraDb.oppgaveOpprettetTidspunkt shouldBeCloseTo oppdatertJournalposthendelseDB.oppgaveOpprettetTidspunkt
            journalposthendelseFraDb.opprettet shouldBeCloseTo oppdatertJournalposthendelseDB.opprettet
            journalposthendelseFraDb.sistEndret shouldBeCloseTo oppdatertJournalposthendelseDB.sistEndret
        }
    }

    private fun hentLagretJournalpostId(testDataHelper: TestDataHelper, journalpostId: JournalpostId): String? {
        return testDataHelper.sessionFactory.withSession { session ->
            session.run(
                queryOf(
                    //language=sql
                    """
                    select journalpost_id
                    from journalposthendelse
                    where journalpost_id = :journalpost_id;
                    """.trimIndent(),
                    mapOf(
                        "journalpost_id" to journalpostId.toString(),
                    ),
                ).map { it.string("journalpost_id") }.asSingle,
            )
        }
    }
}
