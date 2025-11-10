package no.nav.tiltakspenger.journalposthendelser.journalpost.kafka

import org.apache.kafka.common.serialization.Deserializer

interface KafkaConfig {
    fun commonConfig(): Map<String, *>

    fun <K, V> consumerConfig(
        keyDeserializer: Deserializer<K>,
        valueDeserializer: Deserializer<V>,
        groupId: String,
    ): Map<String, *>

    fun <K, V> avroConsumerConfig(
        keyDeserializer: Deserializer<K>,
        valueDeserializer: Deserializer<V>,
        groupId: String,
        useSpecificAvroReader: Boolean,
    ): Map<String, *>

    fun producerConfig(): Map<String, *>
}
