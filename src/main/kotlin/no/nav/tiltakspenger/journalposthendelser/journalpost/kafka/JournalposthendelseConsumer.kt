package no.nav.tiltakspenger.journalposthendelser.journalpost.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.tiltakspenger.journalposthendelser.Configuration
import no.nav.tiltakspenger.journalposthendelser.KAFKA_CONSUMER_GROUP_ID
import no.nav.tiltakspenger.journalposthendelser.journalpost.JournalposthendelseService
import no.nav.tiltakspenger.libs.kafka.avro.infra.AvroKafkaConfig
import no.nav.tiltakspenger.libs.kafka.infra.Consumer
import no.nav.tiltakspenger.libs.kafka.infra.KafkaConfig
import no.nav.tiltakspenger.libs.kafka.infra.ManagedKafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

/**
 * Dokumentasjon for Joarkhendelser https://confluence.adeo.no/x/Ix-DGQ
 */
class JournalposthendelseConsumer(
    topic: String,
    groupId: String = KAFKA_CONSUMER_GROUP_ID,
    avroKafkaConfig: AvroKafkaConfig = if (Configuration.isNais()) AvroKafkaConfig.fraNaisEnv(autoOffsetReset = "earliest") else AvroKafkaConfig(kafkaConfig = KafkaConfig(kafkaBrokers = "localhost:9092"), schemaRegistryUrl = "mock://test"),
    private val journalposthendelseService: JournalposthendelseService,
) : Consumer<String, JournalfoeringHendelseRecord> {
    private val log = KotlinLogging.logger { }

    private val consumer = ManagedKafkaConsumer(
        topic = topic,
        config = avroKafkaConfig.avroConsumerConfig(
            keyDeserializer = StringDeserializer(),
            valueDeserializer = KafkaAvroDeserializer(),
            groupId = groupId,
        ),
        consume = ::consume,
    )

    override suspend fun consume(key: String, value: JournalfoeringHendelseRecord) {
        val hendelse = value.toJournalføringshendelseFraKafka()
        if (hendelse.skalBehandles) {
            log.info { "Mottok journalposthendelse som skal behandles. $hendelse" }
            journalposthendelseService.behandleJournalpostHendelse(hendelse).onLeft {
                // Feilen er allerede logget i servicen; kastet hindrer offset-commit slik at hendelsen forsøkes på nytt.
                throw IllegalStateException("Behandling av journalposthendelse ${hendelse.journalpostId} feilet; se feillogg.")
            }
        }
    }

    override fun run() = consumer.run()

    /** Stopper consumeren og venter på at en pågående batch er ferdig behandlet og committet. */
    fun stop() = consumer.stop()
}
