package no.nav.tiltakspenger.journalposthendelser.journalpost

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.Brevkode
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.dokarkiv.DokarkivClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saksbehandlingapi.SaksbehandlingApiClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseDB
import no.nav.tiltakspenger.journalposthendelser.testutils.withMigratedDb
import no.nav.tiltakspenger.libs.common.CorrelationId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class JournalpostServiceTest {
    private val saksbehandlingApiClient = mockk<SaksbehandlingApiClient>()
    private val dokarkivClient = mockk<DokarkivClient>(relaxed = true)
    private val saksnummer = "34567"

    @BeforeEach
    fun clearMockData() {
        clearMocks(saksbehandlingApiClient, dokarkivClient)
        coEvery { saksbehandlingApiClient.hentEllerOpprettSaksnummer(any(), any()) } returns saksnummer
    }

    @Test
    fun `oppdaterEllerFerdigstillJournalpost - papirsoknad - oppdaterer og ferdigstiller`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalpostService =
                    JournalpostService(saksbehandlingApiClient, dokarkivClient, journalposthendelseRepo)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = "4567",
                    fnr = "12345678910",
                    brevkode = Brevkode.SØKNAD.brevkode,
                    opprettet = LocalDateTime.now(),
                    sistEndret = LocalDateTime.now(),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                val returnertJournalposthendelseDB = journalpostService.oppdaterEllerFerdigstillJournalpost(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                )

                returnertJournalposthendelseDB.saksnummer shouldBe saksnummer
                returnertJournalposthendelseDB.journalpostOppdatertTidspunkt shouldNotBe null
                returnertJournalposthendelseDB.journalpostFerdigstiltTidspunkt shouldNotBe null
                returnertJournalposthendelseDB.oppgaveId shouldBe null

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalposthendelseDB.journalpostId)
                journalposthendelseFraDB?.saksnummer shouldBe saksnummer
                journalposthendelseFraDB?.journalpostOppdatertTidspunkt shouldNotBe null
                journalposthendelseFraDB?.journalpostFerdigstiltTidspunkt shouldNotBe null
                journalposthendelseFraDB?.oppgaveId shouldBe null

                coVerify(exactly = 1) {
                    saksbehandlingApiClient.hentEllerOpprettSaksnummer(
                        journalposthendelseDB.fnr!!,
                        any(),
                    )
                }
                coVerify(exactly = 1) {
                    dokarkivClient.knyttSakTilJournalpost(
                        journalposthendelseDB.journalpostId,
                        saksnummer,
                        any(),
                    )
                }
                coVerify(exactly = 1) {
                    dokarkivClient.ferdigstillJournalpost(
                        journalposthendelseDB.journalpostId,
                        any(),
                    )
                }
            }
        }
    }

    @Test
    fun `oppdaterEllerFerdigstillJournalpost - papirsoknad, oppdatert, ikke ferdigstilt - ferdigstiller`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalpostService =
                    JournalpostService(saksbehandlingApiClient, dokarkivClient, journalposthendelseRepo)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = "4567",
                    fnr = "12345678910",
                    brevkode = Brevkode.SØKNAD.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = LocalDateTime.now(),
                    opprettet = LocalDateTime.now(),
                    sistEndret = LocalDateTime.now(),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                val returnertJournalposthendelseDB = journalpostService.oppdaterEllerFerdigstillJournalpost(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                )

                returnertJournalposthendelseDB.saksnummer shouldBe saksnummer
                returnertJournalposthendelseDB.journalpostOppdatertTidspunkt shouldNotBe null
                returnertJournalposthendelseDB.journalpostFerdigstiltTidspunkt shouldNotBe null
                returnertJournalposthendelseDB.oppgaveId shouldBe null

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalposthendelseDB.journalpostId)
                journalposthendelseFraDB?.saksnummer shouldBe saksnummer
                journalposthendelseFraDB?.journalpostOppdatertTidspunkt shouldNotBe null
                journalposthendelseFraDB?.journalpostFerdigstiltTidspunkt shouldNotBe null
                journalposthendelseFraDB?.oppgaveId shouldBe null

                coVerify(exactly = 0) { saksbehandlingApiClient.hentEllerOpprettSaksnummer(any(), any()) }
                coVerify(exactly = 0) { dokarkivClient.knyttSakTilJournalpost(any(), any(), any()) }
                coVerify(exactly = 1) {
                    dokarkivClient.ferdigstillJournalpost(
                        journalposthendelseDB.journalpostId,
                        any(),
                    )
                }
            }
        }
    }

    @Test
    fun `oppdaterEllerFerdigstillJournalpost - papirsoknad, ferdigstilt - oppdaterer ingenting`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalpostService =
                    JournalpostService(saksbehandlingApiClient, dokarkivClient, journalposthendelseRepo)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = "4567",
                    fnr = "12345678910",
                    brevkode = Brevkode.SØKNAD.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = LocalDateTime.now(),
                    journalpostFerdigstiltTidspunkt = LocalDateTime.now(),
                    opprettet = LocalDateTime.now(),
                    sistEndret = LocalDateTime.now(),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                val returnertJournalposthendelseDB = journalpostService.oppdaterEllerFerdigstillJournalpost(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                )

                returnertJournalposthendelseDB.saksnummer shouldBe saksnummer
                returnertJournalposthendelseDB.journalpostOppdatertTidspunkt shouldNotBe null
                returnertJournalposthendelseDB.journalpostFerdigstiltTidspunkt shouldNotBe null
                returnertJournalposthendelseDB.oppgaveId shouldBe null

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalposthendelseDB.journalpostId)
                journalposthendelseFraDB?.saksnummer shouldBe saksnummer
                journalposthendelseFraDB?.journalpostOppdatertTidspunkt shouldNotBe null
                journalposthendelseFraDB?.journalpostFerdigstiltTidspunkt shouldNotBe null
                journalposthendelseFraDB?.oppgaveId shouldBe null

                coVerify(exactly = 0) { saksbehandlingApiClient.hentEllerOpprettSaksnummer(any(), any()) }
                coVerify(exactly = 0) { dokarkivClient.knyttSakTilJournalpost(any(), any(), any()) }
                coVerify(exactly = 0) {
                    dokarkivClient.ferdigstillJournalpost(
                        any(),
                        any(),
                    )
                }
            }
        }
    }

    @Test
    fun `oppdaterEllerFerdigstillJournalpost - klage - oppdaterer, ferdigstiller ikke`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalpostService =
                    JournalpostService(saksbehandlingApiClient, dokarkivClient, journalposthendelseRepo)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = "4567",
                    fnr = "12345678910",
                    brevkode = Brevkode.KLAGE.brevkode,
                    opprettet = LocalDateTime.now(),
                    sistEndret = LocalDateTime.now(),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                val returnertJournalposthendelseDB = journalpostService.oppdaterEllerFerdigstillJournalpost(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                )

                returnertJournalposthendelseDB.saksnummer shouldBe saksnummer
                returnertJournalposthendelseDB.journalpostOppdatertTidspunkt shouldNotBe null
                returnertJournalposthendelseDB.journalpostFerdigstiltTidspunkt shouldBe null
                returnertJournalposthendelseDB.oppgaveId shouldBe null

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalposthendelseDB.journalpostId)
                journalposthendelseFraDB?.saksnummer shouldBe saksnummer
                journalposthendelseFraDB?.journalpostOppdatertTidspunkt shouldNotBe null
                journalposthendelseFraDB?.journalpostFerdigstiltTidspunkt shouldBe null
                journalposthendelseFraDB?.oppgaveId shouldBe null

                coVerify(exactly = 1) {
                    saksbehandlingApiClient.hentEllerOpprettSaksnummer(
                        journalposthendelseDB.fnr!!,
                        any(),
                    )
                }
                coVerify(exactly = 1) {
                    dokarkivClient.knyttSakTilJournalpost(
                        journalposthendelseDB.journalpostId,
                        saksnummer,
                        any(),
                    )
                }
                coVerify(exactly = 0) {
                    dokarkivClient.ferdigstillJournalpost(
                        any(),
                        any(),
                    )
                }
            }
        }
    }

    @Test
    fun `oppdaterEllerFerdigstillJournalpost - klage, allerede oppdatert - oppdaterer ingenting`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalpostService =
                    JournalpostService(saksbehandlingApiClient, dokarkivClient, journalposthendelseRepo)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = "4567",
                    fnr = "12345678910",
                    brevkode = Brevkode.KLAGE.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = LocalDateTime.now(),
                    opprettet = LocalDateTime.now(),
                    sistEndret = LocalDateTime.now(),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                val returnertJournalposthendelseDB = journalpostService.oppdaterEllerFerdigstillJournalpost(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                )

                returnertJournalposthendelseDB.saksnummer shouldBe saksnummer
                returnertJournalposthendelseDB.journalpostOppdatertTidspunkt shouldNotBe null
                returnertJournalposthendelseDB.journalpostFerdigstiltTidspunkt shouldBe null
                returnertJournalposthendelseDB.oppgaveId shouldBe null

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalposthendelseDB.journalpostId)
                journalposthendelseFraDB?.saksnummer shouldBe saksnummer
                journalposthendelseFraDB?.journalpostOppdatertTidspunkt shouldNotBe null
                journalposthendelseFraDB?.journalpostFerdigstiltTidspunkt shouldBe null
                journalposthendelseFraDB?.oppgaveId shouldBe null

                coVerify(exactly = 0) { saksbehandlingApiClient.hentEllerOpprettSaksnummer(any(), any()) }
                coVerify(exactly = 0) { dokarkivClient.knyttSakTilJournalpost(any(), any(), any()) }
                coVerify(exactly = 0) {
                    dokarkivClient.ferdigstillJournalpost(
                        any(),
                        any(),
                    )
                }
            }
        }
    }
}
