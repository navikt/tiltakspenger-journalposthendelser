package no.nav.tiltakspenger.journalposthendelser.journalpost.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.tiltakspenger.journalposthendelser.KAFKA_CONSUMER_GROUP_ID
import no.nav.tiltakspenger.journalposthendelser.journalpost.JournalposthendelseService
import no.nav.tiltakspenger.libs.kafka.Consumer
import org.apache.kafka.common.serialization.StringDeserializer

/**
 * Dokumentasjon for Joarkhendelser
 * https://confluence.adeo.no/x/Ix-DGQ
 */
class JournalposthendelseConsumer(
    topic: String,
    groupId: String = KAFKA_CONSUMER_GROUP_ID,
    kafkaConfig: KafkaConfig = KafkaConfigImpl(autoOffsetReset = "earliest"),
    private val journalposthendelseService: JournalposthendelseService,
) : Consumer<String, JournalfoeringHendelseRecord> {
    private val log = KotlinLogging.logger { }
    private val harVentet = mutableMapOf<String, Boolean>()

    private val consumer = ManagedKafkaConsumer(
        topic = topic,
        config = kafkaConfig.avroConsumerConfig(
            keyDeserializer = StringDeserializer(),
            valueDeserializer = KafkaAvroDeserializer(),
            groupId = groupId,
            useSpecificAvroReader = true,
        ),
        consume = ::consume,
    )

    override suspend fun consume(key: String, value: JournalfoeringHendelseRecord) {
        val hendelse = value.toJournalføringshendelseFraKafka()
        if (harVentet[hendelse.journalpostId] != true) {
            val delayTime = (2000..15000).random()
            log.info { "Venter i $delayTime millisekunder.." }
            harVentet[hendelse.journalpostId] = true
            delay(delayTime.toLong())
        }
        if (hendelse.skalBehandles) {
            log.info { "Mottok journalposthendelse som skal behandles. $hendelse" }
            journalposthendelseService.behandleJournalpostHendelse(hendelse)
        }
    }

    override fun run() = consumer.run()
}
