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
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.dokarkiv.DokarkivClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saksbehandlingapi.SaksbehandlingApiClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseDB
import no.nav.tiltakspenger.journalposthendelser.testutils.fnrGenerator
import no.nav.tiltakspenger.journalposthendelser.testutils.tomHttpKlientMetadata
import no.nav.tiltakspenger.journalposthendelser.testutils.withMigratedDb
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.TikkendeKlokke
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock

// TODO jah: HTTP-klientene mockes her.
// Kunne vært ekte e2e ved å bruke reelle klienter med FakeHttpTransport (som klienttestene gjør), så hele flyten dekkes uten mockk.
class JournalpostServiceTest {
    private val saksbehandlingApiClient = mockk<SaksbehandlingApiClient>()
    private val dokarkivClient = mockk<DokarkivClient>(relaxed = true)
    private val saksnummer = "34567"
    private val clock: Clock = TikkendeKlokke()

    @BeforeEach
    fun clearMockData() {
        clearMocks(saksbehandlingApiClient, dokarkivClient)
        coEvery { saksbehandlingApiClient.hentEllerOpprettSaksnummer(any(), any()) } returns saksnummer.right()
        coEvery { dokarkivClient.knyttSakTilJournalpost(any(), any(), any(), any()) } returns Unit.right()
        coEvery { dokarkivClient.ferdigstillJournalpost(any()) } returns Unit.right()
    }

    private fun uventetStatus() = HttpKlientError.UventetStatus(
        statusCode = 500,
        body = "{}",
        metadata = tomHttpKlientMetadata(500),
    )

    @Test
    fun `oppdaterEllerFerdigstillJournalpost - papirsoknad - oppdaterer og ferdigstiller`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalpostService =
                    JournalpostService(saksbehandlingApiClient, dokarkivClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = JournalpostId("4567"),
                    fnr = fnrGenerator.generer().verdi,
                    brevkode = Brevkode.SØKNAD.brevkode,
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                val returnertJournalposthendelseDB = journalpostService.oppdaterEllerFerdigstillJournalpost(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                ).getOrFail()

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
                        journalposthendelseDB.fnr!!,
                        true,
                    )
                }
                coVerify(exactly = 1) {
                    dokarkivClient.ferdigstillJournalpost(
                        journalposthendelseDB.journalpostId,
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
                    JournalpostService(saksbehandlingApiClient, dokarkivClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = JournalpostId("4567"),
                    fnr = fnrGenerator.generer().verdi,
                    brevkode = Brevkode.SØKNAD.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = nå(clock),
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                val returnertJournalposthendelseDB = journalpostService.oppdaterEllerFerdigstillJournalpost(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                ).getOrFail()

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
                coVerify(exactly = 0) { dokarkivClient.knyttSakTilJournalpost(any(), any(), any(), any()) }
                coVerify(exactly = 1) {
                    dokarkivClient.ferdigstillJournalpost(
                        journalposthendelseDB.journalpostId,
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
                    JournalpostService(saksbehandlingApiClient, dokarkivClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = JournalpostId("4567"),
                    fnr = fnrGenerator.generer().verdi,
                    brevkode = Brevkode.SØKNAD.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = nå(clock),
                    journalpostFerdigstiltTidspunkt = nå(clock),
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                val returnertJournalposthendelseDB = journalpostService.oppdaterEllerFerdigstillJournalpost(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                ).getOrFail()

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
                coVerify(exactly = 0) { dokarkivClient.knyttSakTilJournalpost(any(), any(), any(), any()) }
                coVerify(exactly = 0) {
                    dokarkivClient.ferdigstillJournalpost(
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
                    JournalpostService(saksbehandlingApiClient, dokarkivClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = JournalpostId("4567"),
                    fnr = fnrGenerator.generer().verdi,
                    brevkode = Brevkode.KLAGE.brevkode,
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                val returnertJournalposthendelseDB = journalpostService.oppdaterEllerFerdigstillJournalpost(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                ).getOrFail()

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
                        journalposthendelseDB.fnr!!,
                        false,
                    )
                }
                coVerify(exactly = 0) {
                    dokarkivClient.ferdigstillJournalpost(
                        any(),
                    )
                }
            }
        }
    }

    @Test
    fun `oppdaterEllerFerdigstillJournalpost - henting av saksnummer feiler - returnerer JournalposthendelseIkkeBehandlet`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                coEvery { saksbehandlingApiClient.hentEllerOpprettSaksnummer(any(), any()) } returns uventetStatus().left()
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalpostService =
                    JournalpostService(saksbehandlingApiClient, dokarkivClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = JournalpostId("4567"),
                    fnr = fnrGenerator.generer().verdi,
                    brevkode = Brevkode.SØKNAD.brevkode,
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                journalpostService.oppdaterEllerFerdigstillJournalpost(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                ) shouldBe JournalposthendelseIkkeBehandlet.left()

                coVerify(exactly = 0) { dokarkivClient.knyttSakTilJournalpost(any(), any(), any(), any()) }
            }
        }
    }

    @Test
    fun `oppdaterEllerFerdigstillJournalpost - oppdatering i dokarkiv feiler - returnerer JournalposthendelseIkkeBehandlet`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                coEvery { dokarkivClient.knyttSakTilJournalpost(any(), any(), any(), any()) } returns uventetStatus().left()
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalpostService =
                    JournalpostService(saksbehandlingApiClient, dokarkivClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = JournalpostId("4567"),
                    fnr = fnrGenerator.generer().verdi,
                    brevkode = Brevkode.SØKNAD.brevkode,
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                journalpostService.oppdaterEllerFerdigstillJournalpost(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                ) shouldBe JournalposthendelseIkkeBehandlet.left()

                coVerify(exactly = 0) { dokarkivClient.ferdigstillJournalpost(any()) }
            }
        }
    }

    @Test
    fun `oppdaterEllerFerdigstillJournalpost - ferdigstilling i dokarkiv feiler - returnerer JournalposthendelseIkkeBehandlet`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                coEvery { dokarkivClient.ferdigstillJournalpost(any()) } returns uventetStatus().left()
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalpostService =
                    JournalpostService(saksbehandlingApiClient, dokarkivClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = JournalpostId("4567"),
                    fnr = fnrGenerator.generer().verdi,
                    brevkode = Brevkode.SØKNAD.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = nå(clock),
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                journalpostService.oppdaterEllerFerdigstillJournalpost(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                ) shouldBe JournalposthendelseIkkeBehandlet.left()
            }
        }
    }

    @Test
    fun `oppdaterEllerFerdigstillJournalpost - klage, allerede oppdatert - oppdaterer ingenting`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalpostService =
                    JournalpostService(saksbehandlingApiClient, dokarkivClient, journalposthendelseRepo, clock)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = JournalpostId("4567"),
                    fnr = fnrGenerator.generer().verdi,
                    brevkode = Brevkode.KLAGE.brevkode,
                    saksnummer = saksnummer,
                    journalpostOppdatertTidspunkt = nå(clock),
                    opprettet = nå(clock),
                    sistEndret = nå(clock),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                val returnertJournalposthendelseDB = journalpostService.oppdaterEllerFerdigstillJournalpost(
                    journalposthendelseDB,
                    CorrelationId.generate(),
                ).getOrFail()

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
                coVerify(exactly = 0) { dokarkivClient.knyttSakTilJournalpost(any(), any(), any(), any()) }
                coVerify(exactly = 0) {
                    dokarkivClient.ferdigstillJournalpost(
                        any(),
                    )
                }
            }
        }
    }
}
