package no.nav.tiltakspenger.journalposthendelser.journalpost

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.Brevkode
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.JournalposthendelseIkkeBehandlet
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.FinnOppgaveResponse
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveResponse
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveType
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseDB
import no.nav.tiltakspenger.journalposthendelser.testutils.tomHttpKlientMetadata
import no.nav.tiltakspenger.journalposthendelser.testutils.withMigratedDb
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.TikkendeKlokke
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock

class OppgaveServiceTest {
    private val oppgaveClient = mockk<OppgaveClient>()
    private val journalpostId = JournalpostId("4567")
    private val fnr = "12345678910"
    private val saksnummer = "34567"
    private val oppgaveId = 9876
    private val tittel = "Klage på tiltakspenger"
    private val clock: Clock = TikkendeKlokke()

    @BeforeEach
    fun clearMockData() {
        clearMocks(oppgaveClient)
        coEvery { oppgaveClient.finnOppgaver(any(), any(), any()) } returns FinnOppgaveResponse(antallTreffTotalt = 0, oppgaver = emptyList()).right()
        coEvery { oppgaveClient.opprettOppgave(any(), any()) } returns oppgaveId.right()
    }

    private fun uventetStatus() = HttpKlientError.UventetStatus(
        statusCode = 500,
        body = "{}",
        metadata = tomHttpKlientMetadata(500),
    )

    @Test
    fun `finnesApenOppgave - oppgavekallet feiler - returnerer JournalposthendelseIkkeBehandlet`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                coEvery { oppgaveClient.finnOppgaver(any(), any(), any()) } returns uventetStatus().left()
                val oppgaveService = OppgaveService(oppgaveClient, testDataHelper.journalposthendelseRepo, clock)

                oppgaveService.finnesApenOppgave(
                    journalpostId,
                    CorrelationId.generate(),
                ) shouldBe JournalposthendelseIkkeBehandlet.left()
            }
        }
    }

    @Test
    fun `opprettFordelingsoppgave - duplikatsjekken feiler - returnerer JournalposthendelseIkkeBehandlet`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                coEvery { oppgaveClient.finnOppgaver(any(), any(), any()) } returns uventetStatus().left()
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val oppgaveService = OppgaveService(oppgaveClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    brevkode = Brevkode.KLAGE.brevkode,
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                oppgaveService.opprettFordelingsoppgave(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                ) shouldBe JournalposthendelseIkkeBehandlet.left()

                coVerify(exactly = 0) { oppgaveClient.opprettOppgave(any(), any()) }
            }
        }
    }

    @Test
    fun `opprettFordelingsoppgave - apen oppgave finnes fra for - gjenbruker oppgave-iden`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                coEvery { oppgaveClient.finnOppgaver(any(), any(), any()) } returns FinnOppgaveResponse(
                    antallTreffTotalt = 1,
                    oppgaver = listOf(OppgaveResponse(oppgaveId)),
                ).right()
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val oppgaveService = OppgaveService(oppgaveClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    brevkode = Brevkode.KLAGE.brevkode,
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                oppgaveService.opprettFordelingsoppgave(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                )

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalposthendelseDB.journalpostId)
                journalposthendelseFraDB?.oppgaveId shouldBe oppgaveId.toString()
                journalposthendelseFraDB?.oppgavetype shouldBe OppgaveType.FORDELING

                coVerify(exactly = 0) { oppgaveClient.opprettOppgave(any(), any()) }
            }
        }
    }

    @Test
    fun `opprettJournalforingsoppgave - opprettelsen feiler - returnerer JournalposthendelseIkkeBehandlet`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                coEvery { oppgaveClient.opprettOppgave(any(), any()) } returns uventetStatus().left()
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val oppgaveService = OppgaveService(oppgaveClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    fnr = fnr,
                    brevkode = Brevkode.KLAGE.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = nå(clock),
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                oppgaveService.opprettJournalforingsoppgave(
                    journalposthendelseDB,
                    tittel,
                    CorrelationId.generate(),
                ) shouldBe JournalposthendelseIkkeBehandlet.left()
            }
        }
    }

    @Test
    fun `opprettOppgaveForPapirsoknad - papirsoknad - oppretter oppgave`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val oppgaveService = OppgaveService(oppgaveClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    fnr = fnr,
                    brevkode = Brevkode.SØKNAD.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = nå(clock),
                    journalpostFerdigstiltTidspunkt = nå(clock),
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
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

                coVerify(exactly = 1) { oppgaveClient.opprettOppgave(match { it.oppgavetype == OppgaveType.BEHANDLE_SAK.kode && it.personident == fnr && it.journalpostId == journalpostId.toString() }, any()) }
            }
        }
    }

    @Test
    fun `opprettOppgaveForPapirsoknad - papirsoknad, har opprettet oppgave - oppretter ingenting`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val oppgaveService = OppgaveService(oppgaveClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    fnr = fnr,
                    brevkode = Brevkode.SØKNAD.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = nå(clock),
                    journalpostFerdigstiltTidspunkt = nå(clock),
                    oppgaveId = oppgaveId.toString(),
                    oppgavetype = OppgaveType.BEHANDLE_SAK,
                    oppgaveOpprettetTidspunkt = nå(clock),
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
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

                coVerify(exactly = 0) { oppgaveClient.opprettOppgave(any(), any()) }
            }
        }
    }

    @Test
    fun `opprettJournalforingsoppgave - klage - oppretter oppgave`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val oppgaveService = OppgaveService(oppgaveClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    fnr = fnr,
                    brevkode = Brevkode.KLAGE.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = nå(clock),
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
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

                coVerify(exactly = 1) { oppgaveClient.opprettOppgave(match { it.oppgavetype == OppgaveType.JOURNALFORING.kode && it.personident == fnr && it.beskrivelse == tittel }, any()) }
            }
        }
    }

    @Test
    fun `opprettJournalforingsoppgave - klage, har opprettet oppgave - oppretter ingenting`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val oppgaveService = OppgaveService(oppgaveClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    fnr = fnr,
                    brevkode = Brevkode.KLAGE.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = nå(clock),
                    oppgaveId = oppgaveId.toString(),
                    oppgavetype = OppgaveType.JOURNALFORING,
                    oppgaveOpprettetTidspunkt = nå(clock),
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
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

                coVerify(exactly = 0) { oppgaveClient.opprettOppgave(any(), any()) }
            }
        }
    }

    @Test
    fun `opprettFordelingsoppgave - klage - oppretter oppgave`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val oppgaveService = OppgaveService(oppgaveClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    brevkode = Brevkode.KLAGE.brevkode,
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
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

                coVerify(exactly = 1) { oppgaveClient.opprettOppgave(match { it.oppgavetype == OppgaveType.FORDELING.kode && it.personident == null && it.journalpostId == journalpostId.toString() }, any()) }
            }
        }
    }

    @Test
    fun `opprettFordelingsoppgave - klage, har opprettet oppgave - oppretter ingenting`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val oppgaveService = OppgaveService(oppgaveClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    brevkode = Brevkode.KLAGE.brevkode,
                    oppgaveId = oppgaveId.toString(),
                    oppgavetype = OppgaveType.FORDELING,
                    oppgaveOpprettetTidspunkt = nå(clock),
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
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

                coVerify(exactly = 0) { oppgaveClient.opprettOppgave(any(), any()) }
            }
        }
    }
}
