package no.nav.tiltakspenger.journalposthendelser.journalpost

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.Brevkode
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveType
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseDB
import no.nav.tiltakspenger.journalposthendelser.testutils.withMigratedDb
import no.nav.tiltakspenger.libs.common.CorrelationId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class OppgaveServiceTest {
    private val oppgaveClient = mockk<OppgaveClient>()
    private val journalpostId = "4567"
    private val fnr = "12345678910"
    private val saksnummer = "34567"
    private val oppgaveId = 9876
    private val tittel = "Klage på tiltakspenger"

    @BeforeEach
    fun clearMockData() {
        clearMocks(oppgaveClient)
        coEvery { oppgaveClient.opprettOppgaveForPapirsoknad(any(), any(), any()) } returns oppgaveId
        coEvery { oppgaveClient.opprettJournalforingsoppgave(any(), any(), any(), any()) } returns oppgaveId
        coEvery { oppgaveClient.opprettFordelingsoppgave(any(), any()) } returns oppgaveId
    }

    @Test
    fun `opprettOppgaveForPapirsoknad - papirsoknad - oppretter oppgave`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val oppgaveService = OppgaveService(oppgaveClient, journalposthendelseRepo)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    fnr = fnr,
                    brevkode = Brevkode.SØKNAD.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = LocalDateTime.now(),
                    journalpostFerdigstiltTidspunkt = LocalDateTime.now(),
                    opprettet = LocalDateTime.now(),
                    sistEndret = LocalDateTime.now(),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                oppgaveService.opprettOppgaveForPapirsoknad(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                )

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalposthendelseDB.journalpostId)
                journalposthendelseFraDB?.oppgaveId shouldBe oppgaveId.toString()
                journalposthendelseFraDB?.oppgavetype shouldBe OppgaveType.BEHANDLE_SAK
                journalposthendelseFraDB?.oppgaveOpprettetTidspunkt shouldNotBe null

                coVerify(exactly = 1) { oppgaveClient.opprettOppgaveForPapirsoknad(fnr, journalpostId, any()) }
            }
        }
    }

    @Test
    fun `opprettOppgaveForPapirsoknad - papirsoknad, har opprettet oppgave - oppretter ingenting`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val oppgaveService = OppgaveService(oppgaveClient, journalposthendelseRepo)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    fnr = fnr,
                    brevkode = Brevkode.SØKNAD.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = LocalDateTime.now(),
                    journalpostFerdigstiltTidspunkt = LocalDateTime.now(),
                    oppgaveId = oppgaveId.toString(),
                    oppgavetype = OppgaveType.BEHANDLE_SAK,
                    oppgaveOpprettetTidspunkt = LocalDateTime.now(),
                    opprettet = LocalDateTime.now(),
                    sistEndret = LocalDateTime.now(),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                oppgaveService.opprettOppgaveForPapirsoknad(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                )

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalposthendelseDB.journalpostId)
                journalposthendelseFraDB?.oppgaveId shouldBe oppgaveId.toString()
                journalposthendelseFraDB?.oppgavetype shouldBe OppgaveType.BEHANDLE_SAK
                journalposthendelseFraDB?.oppgaveOpprettetTidspunkt shouldNotBe null

                coVerify(exactly = 0) { oppgaveClient.opprettOppgaveForPapirsoknad(any(), any(), any()) }
            }
        }
    }

    @Test
    fun `opprettJournalforingsoppgave - klage - oppretter oppgave`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val oppgaveService = OppgaveService(oppgaveClient, journalposthendelseRepo)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    fnr = fnr,
                    brevkode = Brevkode.KLAGE.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = LocalDateTime.now(),
                    opprettet = LocalDateTime.now(),
                    sistEndret = LocalDateTime.now(),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                oppgaveService.opprettJournalforingsoppgave(
                    journalposthendelseDB,
                    tittel,
                    CorrelationId.generate(),
                )

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalposthendelseDB.journalpostId)
                journalposthendelseFraDB?.oppgaveId shouldBe oppgaveId.toString()
                journalposthendelseFraDB?.oppgavetype shouldBe OppgaveType.JOURNALFORING
                journalposthendelseFraDB?.oppgaveOpprettetTidspunkt shouldNotBe null

                coVerify(exactly = 1) { oppgaveClient.opprettJournalforingsoppgave(fnr, journalpostId, tittel, any()) }
            }
        }
    }

    @Test
    fun `opprettJournalforingsoppgave - klage, har opprettet oppgave - oppretter ingenting`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val oppgaveService = OppgaveService(oppgaveClient, journalposthendelseRepo)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    fnr = fnr,
                    brevkode = Brevkode.KLAGE.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = LocalDateTime.now(),
                    oppgaveId = oppgaveId.toString(),
                    oppgavetype = OppgaveType.JOURNALFORING,
                    oppgaveOpprettetTidspunkt = LocalDateTime.now(),
                    opprettet = LocalDateTime.now(),
                    sistEndret = LocalDateTime.now(),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                oppgaveService.opprettJournalforingsoppgave(
                    journalposthendelseDB,
                    tittel,
                    CorrelationId.generate(),
                )

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalposthendelseDB.journalpostId)
                journalposthendelseFraDB?.oppgaveId shouldBe oppgaveId.toString()
                journalposthendelseFraDB?.oppgavetype shouldBe OppgaveType.JOURNALFORING
                journalposthendelseFraDB?.oppgaveOpprettetTidspunkt shouldNotBe null

                coVerify(exactly = 0) { oppgaveClient.opprettJournalforingsoppgave(any(), any(), any(), any()) }
            }
        }
    }

    @Test
    fun `opprettFordelingsoppgave - klage - oppretter oppgave`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val oppgaveService = OppgaveService(oppgaveClient, journalposthendelseRepo)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    brevkode = Brevkode.KLAGE.brevkode,
                    opprettet = LocalDateTime.now(),
                    sistEndret = LocalDateTime.now(),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                oppgaveService.opprettFordelingsoppgave(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                )

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalposthendelseDB.journalpostId)
                journalposthendelseFraDB?.oppgaveId shouldBe oppgaveId.toString()
                journalposthendelseFraDB?.oppgavetype shouldBe OppgaveType.FORDELING
                journalposthendelseFraDB?.oppgaveOpprettetTidspunkt shouldNotBe null

                coVerify(exactly = 1) { oppgaveClient.opprettFordelingsoppgave(journalpostId, any()) }
            }
        }
    }

    @Test
    fun `opprettFordelingsoppgave - klage, har opprettet oppgave - oppretter ingenting`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val oppgaveService = OppgaveService(oppgaveClient, journalposthendelseRepo)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    brevkode = Brevkode.KLAGE.brevkode,
                    oppgaveId = oppgaveId.toString(),
                    oppgavetype = OppgaveType.FORDELING,
                    oppgaveOpprettetTidspunkt = LocalDateTime.now(),
                    opprettet = LocalDateTime.now(),
                    sistEndret = LocalDateTime.now(),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                oppgaveService.opprettFordelingsoppgave(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                )

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalposthendelseDB.journalpostId)
                journalposthendelseFraDB?.oppgaveId shouldBe oppgaveId.toString()
                journalposthendelseFraDB?.oppgavetype shouldBe OppgaveType.FORDELING
                journalposthendelseFraDB?.oppgaveOpprettetTidspunkt shouldNotBe null

                coVerify(exactly = 0) { oppgaveClient.opprettFordelingsoppgave(any(), any()) }
            }
        }
    }
}
