package no.nav.tiltakspenger.journalposthendelser.consumer

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.tiltakspenger.journalposthendelser.Configuration
import no.nav.tiltakspenger.journalposthendelser.KAFKA_CONSUMER_GROUP_ID
import no.nav.tiltakspenger.libs.kafka.Consumer
import no.nav.tiltakspenger.libs.kafka.ManagedKafkaConsumer
import no.nav.tiltakspenger.libs.kafka.config.KafkaConfig
import no.nav.tiltakspenger.libs.kafka.config.KafkaConfigImpl
import no.nav.tiltakspenger.libs.kafka.config.LocalKafkaConfig
import org.apache.kafka.common.serialization.StringDeserializer

class JournalposthendelseConsumer(
    topic: String,
    groupId: String = KAFKA_CONSUMER_GROUP_ID,
    kafkaConfig: KafkaConfig = if (Configuration.isNais()) KafkaConfigImpl(autoOffsetReset = "latest") else LocalKafkaConfig(),
) : Consumer<String, JournalfoeringHendelseRecord> {
    private val log = KotlinLogging.logger { }

    private val consumer = ManagedKafkaConsumer(
        topic = topic,
        config = kafkaConfig.consumerConfig(
            keyDeserializer = StringDeserializer(),
            valueDeserializer = KafkaAvroDeserializer(),
            groupId = groupId,
        ),
        consume = ::consume,
    )

    override suspend fun consume(key: String, value: JournalfoeringHendelseRecord) {
        if (value.hendelsesType == "JournalpostMottatt" && value.temaNytt == "IND") {
            log.info { "Hendelse er av typen JournalpostMottatt ${value.journalpostId}" }
        }
    }

    override fun run() = consumer.run()
}
