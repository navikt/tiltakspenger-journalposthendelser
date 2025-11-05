package no.nav.tiltakspenger.journalposthendelser.journalpost.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.tiltakspenger.journalposthendelser.Configuration
import no.nav.tiltakspenger.journalposthendelser.KAFKA_CONSUMER_GROUP_ID
import no.nav.tiltakspenger.journalposthendelser.journalpost.JournalposthendelseService
import no.nav.tiltakspenger.libs.kafka.Consumer
import no.nav.tiltakspenger.libs.kafka.ManagedKafkaConsumer
import no.nav.tiltakspenger.libs.kafka.config.KafkaConfig
import no.nav.tiltakspenger.libs.kafka.config.KafkaConfigImpl
import no.nav.tiltakspenger.libs.kafka.config.LocalKafkaConfig
import org.apache.kafka.common.serialization.StringDeserializer

/**
 * Dokumentasjon for Joarkhendelser
 * https://confluence.adeo.no/x/Ix-DGQ
 */
class JournalposthendelseConsumer(
    topic: String,
    groupId: String = KAFKA_CONSUMER_GROUP_ID,
    kafkaConfig: KafkaConfig = if (Configuration.isNais()) KafkaConfigImpl(autoOffsetReset = "earliest") else LocalKafkaConfig(),
    private val journalposthendelseService: JournalposthendelseService,
) : Consumer<String, JournalfoeringHendelseRecord> {
    private val log = KotlinLogging.logger { }

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
        if (hendelse.skalBehandles) {
            log.info { "Mottok journalposthendelse som skal behandles. $hendelse" }
            journalposthendelseService.behandleJournalpostHendelse(hendelse)
        }
    }

    override fun run() = consumer.run()
}
