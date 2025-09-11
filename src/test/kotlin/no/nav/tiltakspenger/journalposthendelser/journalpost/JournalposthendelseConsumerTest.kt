package no.nav.tiltakspenger.journalposthendelser.journalpost

import io.mockk.clearAllMocks
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.tiltakspenger.libs.kafka.config.LocalKafkaConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class JournalposthendelseConsumerTest {
    private val journalpostService = mockk<JournalpostService>()
    private val consumer = JournalposthendelseConsumer(
        topic = "test-topic",
        kafkaConfig = LocalKafkaConfig(),
        journalpostService = journalpostService,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `prosesserer hendelser for tiltakspenger`() = runTest {
        val hendelseRecord = lagMockkJournalfoeringHendelseRecord(
            hendelsesType = "JournalpostMottatt",
            temaNytt = "IND",
        )

        coJustRun { journalpostService.hentJournalpost(any()) }

        consumer.consume("key", hendelseRecord)

        coVerify { journalpostService.hentJournalpost(12345L) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["TemaEndret", "EndeligJournalført", "JournalpostUtgått"])
    fun `prosesserer ikke andre typer hendelser`(hendelsesType: String) = runTest {
        val hendelseRecord = lagMockkJournalfoeringHendelseRecord(
            hendelsesType = hendelsesType,
            temaNytt = "IND",
        )

        consumer.consume("key", hendelseRecord)

        coVerify(exactly = 0) { journalpostService.hentJournalpost(any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["AAP", "DAG", "SYM"])
    fun `prosesserer ikke andre temaer enn tiltakspenger`(temaNytt: String) = runTest {
        val hendelseRecord = lagMockkJournalfoeringHendelseRecord(
            hendelsesType = "JournalpostMottatt",
            temaNytt = temaNytt,
        )

        consumer.consume("key", hendelseRecord)

        coVerify(exactly = 0) { journalpostService.hentJournalpost(any()) }
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
        return hendelseRecord
    }
}
