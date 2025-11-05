package no.nav.tiltakspenger.journalposthendelser.journalpost.kafka

import io.mockk.clearAllMocks
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.tiltakspenger.journalposthendelser.journalpost.JournalposthendelseService
import no.nav.tiltakspenger.libs.kafka.config.LocalKafkaConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class JournalposthendelseConsumerTest {
    private val journalposthendelseService = mockk<JournalposthendelseService>()
    private val consumer = JournalposthendelseConsumer(
        topic = "test-topic",
        kafkaConfig = LocalKafkaConfig(),
        journalposthendelseService = journalposthendelseService,
    )

    fun journalføringshendelseFraKafka(
        hendelsesType: String = "TemaEndret",
    ) = JournalføringshendelseFraKafka(
        hendelsesId = "hendelseId",
        versjon = 1,
        hendelsesType = hendelsesType,
        journalpostId = "12345",
        journalpostStatus = "MOTTATT",
        temaGammelt = null,
        temaNytt = "IND",
        mottaksKanal = "NAV_NO",
        kanalReferanseId = null,
        behandlingstema = null,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `prosesserer JournalpostMottatt-hendelser for tiltakspenger`() = runTest {
        val hendelseRecord = lagMockkJournalfoeringHendelseRecord(
            hendelsesType = "JournalpostMottatt",
            temaNytt = "IND",
        )

        coJustRun { journalposthendelseService.behandleJournalpostHendelse(any()) }

        consumer.consume("key", hendelseRecord)

        coVerify { journalposthendelseService.behandleJournalpostHendelse(journalføringshendelseFraKafka(hendelsesType = "JournalpostMottatt")) }
    }

    @Test
    fun `prosesserer TemaEndret-hendelser for tiltakspenger`() = runTest {
        val hendelseRecord = lagMockkJournalfoeringHendelseRecord(
            hendelsesType = "TemaEndret",
            temaNytt = "IND",
        )

        coJustRun { journalposthendelseService.behandleJournalpostHendelse(any()) }

        consumer.consume("key", hendelseRecord)

        coVerify { journalposthendelseService.behandleJournalpostHendelse(journalføringshendelseFraKafka(hendelsesType = "TemaEndret")) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["EndeligJournalført", "JournalpostUtgått"])
    fun `prosesserer ikke andre typer hendelser`(hendelsesType: String) = runTest {
        val hendelseRecord = lagMockkJournalfoeringHendelseRecord(
            hendelsesType = hendelsesType,
            temaNytt = "IND",
        )

        consumer.consume("key", hendelseRecord)

        coVerify(exactly = 0) { journalposthendelseService.behandleJournalpostHendelse(any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["AAP", "DAG", "SYM"])
    fun `prosesserer ikke andre temaer enn tiltakspenger`(temaNytt: String) = runTest {
        val hendelseRecord = lagMockkJournalfoeringHendelseRecord(
            hendelsesType = "JournalpostMottatt",
            temaNytt = temaNytt,
        )

        consumer.consume("key", hendelseRecord)

        coVerify(exactly = 0) { journalposthendelseService.behandleJournalpostHendelse(any()) }
    }

    fun lagMockkJournalfoeringHendelseRecord(
        hendelsesType: String,
        temaNytt: String,
    ): JournalfoeringHendelseRecord {
        val hendelseRecord = mockk<JournalfoeringHendelseRecord>(relaxed = true)
        every { hendelseRecord.hendelsesType } returns hendelsesType
        every { hendelseRecord.temaNytt } returns temaNytt
        every { hendelseRecord.journalpostId } returns (12345L)
        every { hendelseRecord.mottaksKanal } returns ("NAV_NO")
        every { hendelseRecord.hendelsesId } returns ("hendelseId")
        every { hendelseRecord.versjon } returns (1)
        every { hendelseRecord.temaGammelt } returns (null)
        every { hendelseRecord.kanalReferanseId } returns (null)
        every { hendelseRecord.behandlingstema } returns (null)
        every { hendelseRecord.journalpostStatus } returns ("MOTTATT")

        return hendelseRecord
    }
}
