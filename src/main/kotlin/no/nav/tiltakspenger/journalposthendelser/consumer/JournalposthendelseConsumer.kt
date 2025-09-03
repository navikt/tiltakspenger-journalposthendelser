package no.nav.tiltakspenger.journalposthendelser.consumer

import io.github.oshai.kotlinlogging.KotlinLogging
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
    kafkaConfig: KafkaConfig = if (Configuration.isNais()) KafkaConfigImpl(autoOffsetReset = "none") else LocalKafkaConfig(),
) : Consumer<String, String> {
    private val log = KotlinLogging.logger { }

    private val consumer = ManagedKafkaConsumer(
        topic = topic,
        config = kafkaConfig.consumerConfig(
            keyDeserializer = StringDeserializer(),
            valueDeserializer = StringDeserializer(),
            groupId = groupId,
        ),
        consume = ::consume,
    )

    override suspend fun consume(key: String, value: String) {
        /***
         * TODO - vi må filtrere på typen 'JournalpostMottatt' på et eller annet tidspunkt
         */
        log.info { "Mottatt journalposthendelse med key $key" }
    }

    override fun run() = consumer.run()
}
