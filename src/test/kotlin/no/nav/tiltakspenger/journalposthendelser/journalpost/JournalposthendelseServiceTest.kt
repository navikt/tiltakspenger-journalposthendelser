package no.nav.tiltakspenger.journalposthendelser.journalpost

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.Brevkode
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.JournalpostMetadata
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.dokarkiv.DokarkivClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.FinnOppgaveResponse
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveResponse
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveType
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.pdl.PdlClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saf.Bruker
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saf.BrukerIdType
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saf.SafJournalpostClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saksbehandlingapi.SaksbehandlingApiClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseDB
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseRepo
import no.nav.tiltakspenger.journalposthendelser.testutils.shouldBeCloseTo
import no.nav.tiltakspenger.journalposthendelser.testutils.withMigratedDb
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class JournalposthendelseServiceTest {
    private val safJournalpostClient = mockk<SafJournalpostClient>()
    private val pdlClient = mockk<PdlClient>()
    private val saksbehandlingApiClient = mockk<SaksbehandlingApiClient>()
    private val dokarkivClient = mockk<DokarkivClient>(relaxed = true)
    private val oppgaveClient = mockk<OppgaveClient>()
    private val journalpostId = "4567"
    private val fnr = "12345678910"
    private val saksnummer = "34567"
    private val oppgaveId = 9876
    private val tittel = "Klage på tiltakspenger"

    @BeforeEach
    fun clearMockData() {
        clearMocks(safJournalpostClient, pdlClient, saksbehandlingApiClient, dokarkivClient, oppgaveClient)
        coEvery { pdlClient.hentGjeldendeIdent(any(), any()) } returns fnr
        coEvery { saksbehandlingApiClient.hentEllerOpprettSaksnummer(any(), any()) } returns saksnummer
        coEvery { oppgaveClient.finnOppgave(any(), any(), any()) } returns FinnOppgaveResponse(
            antallTreffTotalt = 0,
            oppgaver = emptyList(),
        )
        coEvery { oppgaveClient.opprettOppgaveForPapirsoknad(any(), any(), any()) } returns oppgaveId
        coEvery { oppgaveClient.opprettJournalforingsoppgave(any(), any(), any(), any()) } returns oppgaveId
        coEvery { oppgaveClient.opprettFordelingsoppgave(any(), any()) } returns oppgaveId
    }

    @Test
    fun `behandleJournalpostHendelse - saf-klient returnerer ingen journalpost - kaster feil`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                coEvery { safJournalpostClient.getJournalpostMetadata(any()) } returns null
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalposthendelseService = getJournalposthendelseService(journalposthendelseRepo)

                assertThrows<IllegalStateException> { journalposthendelseService.behandleJournalpostHendelse(journalpostId) }
            }
        }
    }

    @Test
    fun `behandleJournalpostHendelse - journalpost er journalfort, ingen oppgave, finnes ikke i db - ignorerer journalposthendelse`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                coEvery { safJournalpostClient.getJournalpostMetadata(any()) } returns getJournalpostMetadata(erJournalfort = true)
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalposthendelseService = getJournalposthendelseService(journalposthendelseRepo)

                journalposthendelseService.behandleJournalpostHendelse(journalpostId)

                journalposthendelseRepo.hent(journalpostId) shouldBe null

                coVerify(exactly = 0) { pdlClient.hentGjeldendeIdent(any(), any()) }
                coVerify(exactly = 0) { saksbehandlingApiClient.hentEllerOpprettSaksnummer(any(), any()) }
                coVerify(exactly = 0) { dokarkivClient.knyttSakTilJournalpost(any(), any(), any()) }
                coVerify(exactly = 0) { dokarkivClient.ferdigstillJournalpost(any(), any()) }
                coVerify(exactly = 0) { oppgaveClient.opprettOppgaveForPapirsoknad(any(), any(), any()) }
            }
        }
    }

    @Test
    fun `behandleJournalpostHendelse - journalpost er journalfort, ingen oppgave, ferdig behandlet i db - ignorerer journalposthendelse`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                coEvery { safJournalpostClient.getJournalpostMetadata(any()) } returns getJournalpostMetadata(erJournalfort = true)
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalposthendelseService = getJournalposthendelseService(journalposthendelseRepo)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    fnr = fnr,
                    saksnummer = saksnummer,
                    brevkode = Brevkode.SØKNAD.brevkode,
                    journalpostOppdatertTidspunkt = LocalDateTime.now().minusMinutes(2),
                    journalpostFerdigstiltTidspunkt = LocalDateTime.now().minusMinutes(2),
                    oppgaveId = oppgaveId.toString(),
                    oppgavetype = OppgaveType.BEHANDLE_SAK,
                    oppgaveOpprettetTidspunkt = LocalDateTime.now().minusMinutes(1),
                    opprettet = LocalDateTime.now().minusMinutes(2),
                    sistEndret = LocalDateTime.now().minusMinutes(1),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                journalposthendelseService.behandleJournalpostHendelse(journalpostId)

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalpostId)
                journalposthendelseFraDB shouldNotBe null
                journalposthendelseFraDB?.sistEndret shouldBeCloseTo journalposthendelseDB.sistEndret

                coVerify(exactly = 0) { pdlClient.hentGjeldendeIdent(any(), any()) }
                coVerify(exactly = 0) { saksbehandlingApiClient.hentEllerOpprettSaksnummer(any(), any()) }
                coVerify(exactly = 0) { dokarkivClient.knyttSakTilJournalpost(any(), any(), any()) }
                coVerify(exactly = 0) { dokarkivClient.ferdigstillJournalpost(any(), any()) }
                coVerify(exactly = 0) { oppgaveClient.opprettOppgaveForPapirsoknad(any(), any(), any()) }
            }
        }
    }

    @Test
    fun `behandleJournalpostHendelse - journalpost er ikke journalfort, har oppgave, finnes ikke i db - ignorerer journalposthendelse`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                coEvery { safJournalpostClient.getJournalpostMetadata(any()) } returns getJournalpostMetadata()
                coEvery { oppgaveClient.finnOppgave(any(), any(), any()) } returns FinnOppgaveResponse(
                    antallTreffTotalt = 1,
                    oppgaver = listOf(OppgaveResponse(oppgaveId)),
                )
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalposthendelseService = getJournalposthendelseService(journalposthendelseRepo)

                journalposthendelseService.behandleJournalpostHendelse(journalpostId)

                journalposthendelseRepo.hent(journalpostId) shouldBe null

                coVerify(exactly = 0) { pdlClient.hentGjeldendeIdent(any(), any()) }
                coVerify(exactly = 0) { saksbehandlingApiClient.hentEllerOpprettSaksnummer(any(), any()) }
                coVerify(exactly = 0) { dokarkivClient.knyttSakTilJournalpost(any(), any(), any()) }
                coVerify(exactly = 0) { dokarkivClient.ferdigstillJournalpost(any(), any()) }
                coVerify(exactly = 0) { oppgaveClient.opprettOppgaveForPapirsoknad(any(), any(), any()) }
            }
        }
    }

    @Test
    fun `behandleJournalpostHendelse - journalpost er journalfort, ingen oppgave og ikke ferdig behandlet i db - behandler journalposthendelse`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                coEvery { safJournalpostClient.getJournalpostMetadata(any()) } returns getJournalpostMetadata(erJournalfort = true)
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalposthendelseService = getJournalposthendelseService(journalposthendelseRepo)
                val journalposthendelseDB = JournalposthendelseDB(
                    journalpostId = journalpostId,
                    fnr = fnr,
                    saksnummer = saksnummer,
                    brevkode = Brevkode.SØKNAD.brevkode,
                    journalpostOppdatertTidspunkt = LocalDateTime.now().minusMinutes(2),
                    journalpostFerdigstiltTidspunkt = LocalDateTime.now().minusMinutes(2),
                    opprettet = LocalDateTime.now().minusMinutes(2),
                    sistEndret = LocalDateTime.now().minusMinutes(2),
                )
                journalposthendelseRepo.lagre(journalposthendelseDB)

                journalposthendelseService.behandleJournalpostHendelse(journalpostId)

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalpostId)
                journalposthendelseFraDB shouldNotBe null
                journalposthendelseFraDB?.oppgaveId shouldBe oppgaveId.toString()
                journalposthendelseFraDB?.oppgavetype shouldBe OppgaveType.BEHANDLE_SAK
                journalposthendelseFraDB?.oppgaveOpprettetTidspunkt shouldBeCloseTo LocalDateTime.now()
                journalposthendelseFraDB?.sistEndret shouldBeCloseTo LocalDateTime.now()

                coVerify(exactly = 1) { pdlClient.hentGjeldendeIdent(fnr, journalpostId) }
                coVerify(exactly = 0) { saksbehandlingApiClient.hentEllerOpprettSaksnummer(any(), any()) }
                coVerify(exactly = 0) { dokarkivClient.knyttSakTilJournalpost(any(), any(), any()) }
                coVerify(exactly = 0) { dokarkivClient.ferdigstillJournalpost(any(), any()) }
                coVerify(exactly = 1) { oppgaveClient.opprettOppgaveForPapirsoknad(fnr, journalpostId, any()) }
            }
        }
    }

    @Test
    fun `behandleJournalpostHendelse - ikke behandlet, mangler bruker - oppretter fordelingsoppgave`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                coEvery { safJournalpostClient.getJournalpostMetadata(any()) } returns getJournalpostMetadata(brukerId = null)
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalposthendelseService = getJournalposthendelseService(journalposthendelseRepo)

                journalposthendelseService.behandleJournalpostHendelse(journalpostId)

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalpostId)
                journalposthendelseFraDB shouldNotBe null
                journalposthendelseFraDB?.fnr shouldBe null
                journalposthendelseFraDB?.saksnummer shouldBe null
                journalposthendelseFraDB?.brevkode shouldBe Brevkode.SØKNAD.brevkode
                journalposthendelseFraDB?.journalpostOppdatertTidspunkt shouldBe null
                journalposthendelseFraDB?.journalpostFerdigstiltTidspunkt shouldBe null
                journalposthendelseFraDB?.oppgaveId shouldBe oppgaveId.toString()
                journalposthendelseFraDB?.oppgavetype shouldBe OppgaveType.FORDELING
                journalposthendelseFraDB?.oppgaveOpprettetTidspunkt shouldBeCloseTo LocalDateTime.now()
                journalposthendelseFraDB?.sistEndret shouldBeCloseTo LocalDateTime.now()

                coVerify(exactly = 0) { pdlClient.hentGjeldendeIdent(any(), any()) }
                coVerify(exactly = 0) { saksbehandlingApiClient.hentEllerOpprettSaksnummer(any(), any()) }
                coVerify(exactly = 0) { dokarkivClient.knyttSakTilJournalpost(any(), any(), any()) }
                coVerify(exactly = 0) { dokarkivClient.ferdigstillJournalpost(any(), any()) }
                coVerify(exactly = 1) { oppgaveClient.opprettFordelingsoppgave(journalpostId, any()) }
            }
        }
    }

    @Test
    fun `behandleJournalpostHendelse - ikke behandlet, klage - oppretter journalforingsoppgave`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                val aktorId = "2345432"
                coEvery { safJournalpostClient.getJournalpostMetadata(any()) } returns getJournalpostMetadata(
                    brukerId = aktorId,
                    brukerIdType = BrukerIdType.AKTOERID,
                    brevkode = Brevkode.KLAGE,
                )
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalposthendelseService = getJournalposthendelseService(journalposthendelseRepo)

                journalposthendelseService.behandleJournalpostHendelse(journalpostId)

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalpostId)
                journalposthendelseFraDB shouldNotBe null
                journalposthendelseFraDB?.fnr shouldBe fnr
                journalposthendelseFraDB?.saksnummer shouldBe saksnummer
                journalposthendelseFraDB?.brevkode shouldBe Brevkode.KLAGE.brevkode
                journalposthendelseFraDB?.journalpostOppdatertTidspunkt shouldBeCloseTo LocalDateTime.now()
                journalposthendelseFraDB?.journalpostFerdigstiltTidspunkt shouldBe null
                journalposthendelseFraDB?.oppgaveId shouldBe oppgaveId.toString()
                journalposthendelseFraDB?.oppgavetype shouldBe OppgaveType.JOURNALFORING
                journalposthendelseFraDB?.oppgaveOpprettetTidspunkt shouldBeCloseTo LocalDateTime.now()
                journalposthendelseFraDB?.sistEndret shouldBeCloseTo LocalDateTime.now()

                coVerify(exactly = 1) { pdlClient.hentGjeldendeIdent(aktorId, journalpostId) }
                coVerify(exactly = 1) { saksbehandlingApiClient.hentEllerOpprettSaksnummer(fnr, any()) }
                coVerify(exactly = 1) { dokarkivClient.knyttSakTilJournalpost(journalpostId, saksnummer, any()) }
                coVerify(exactly = 0) { dokarkivClient.ferdigstillJournalpost(any(), any()) }
                coVerify(exactly = 1) { oppgaveClient.opprettJournalforingsoppgave(fnr, journalpostId, tittel, any()) }
            }
        }
    }

    @Test
    fun `behandleJournalpostHendelse - ikke behandlet, papirsoknad - journalforer og oppretter behandle sak-oppgave`() {
        withMigratedDb(runIsolated = true) { testDataHelper ->
            runTest {
                coEvery { safJournalpostClient.getJournalpostMetadata(any()) } returns getJournalpostMetadata()
                val journalposthendelseRepo = testDataHelper.journalposthendelseRepo
                val journalposthendelseService = getJournalposthendelseService(journalposthendelseRepo)

                journalposthendelseService.behandleJournalpostHendelse(journalpostId)

                val journalposthendelseFraDB = journalposthendelseRepo.hent(journalpostId)
                journalposthendelseFraDB shouldNotBe null
                journalposthendelseFraDB?.fnr shouldBe fnr
                journalposthendelseFraDB?.saksnummer shouldBe saksnummer
                journalposthendelseFraDB?.brevkode shouldBe Brevkode.SØKNAD.brevkode
                journalposthendelseFraDB?.journalpostOppdatertTidspunkt shouldBeCloseTo LocalDateTime.now()
                journalposthendelseFraDB?.journalpostFerdigstiltTidspunkt shouldBeCloseTo LocalDateTime.now()
                journalposthendelseFraDB?.oppgaveId shouldBe oppgaveId.toString()
                journalposthendelseFraDB?.oppgavetype shouldBe OppgaveType.BEHANDLE_SAK
                journalposthendelseFraDB?.oppgaveOpprettetTidspunkt shouldBeCloseTo LocalDateTime.now()
                journalposthendelseFraDB?.sistEndret shouldBeCloseTo LocalDateTime.now()

                coVerify(exactly = 1) { pdlClient.hentGjeldendeIdent(fnr, journalpostId) }
                coVerify(exactly = 1) { saksbehandlingApiClient.hentEllerOpprettSaksnummer(fnr, any()) }
                coVerify(exactly = 1) { dokarkivClient.knyttSakTilJournalpost(journalpostId, saksnummer, any()) }
                coVerify(exactly = 1) { dokarkivClient.ferdigstillJournalpost(journalpostId, any()) }
                coVerify(exactly = 1) { oppgaveClient.opprettOppgaveForPapirsoknad(fnr, journalpostId, any()) }
            }
        }
    }

    private fun getJournalpostMetadata(
        journalpostId: String = this.journalpostId,
        brukerId: String? = fnr,
        brukerIdType: BrukerIdType? = BrukerIdType.FNR,
        erJournalfort: Boolean = false,
        brevkode: Brevkode? = Brevkode.SØKNAD,
    ) =
        JournalpostMetadata(
            journalpostId = journalpostId,
            bruker = Bruker(
                id = brukerId,
                type = brukerIdType,
            ),
            erJournalfort = erJournalfort,
            datoOpprettet = LocalDateTime.now().minusMinutes(2),
            brevkode = brevkode?.brevkode,
            tittel = tittel,
        )

    private fun getJournalposthendelseService(journalposthendelseRepo: JournalposthendelseRepo) = JournalposthendelseService(
        safJournalpostClient = safJournalpostClient,
        journalposthendelseRepo = journalposthendelseRepo,
        pdlClient = pdlClient,
        journalpostService = JournalpostService(
            saksbehandlingApiClient = saksbehandlingApiClient,
            dokarkivClient = dokarkivClient,
            journalposthendelseRepo = journalposthendelseRepo,
        ),
        oppgaveService = OppgaveService(
            oppgaveClient = oppgaveClient,
            journalposthendelseRepo = journalposthendelseRepo,
        ),
    )
}
