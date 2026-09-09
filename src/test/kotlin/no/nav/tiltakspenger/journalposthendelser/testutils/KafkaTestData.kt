package no.nav.tiltakspenger.journalposthendelser.testutils

import no.nav.tiltakspenger.libs.kafka.avro.infra.AvroKafkaConfig
import no.nav.tiltakspenger.libs.kafka.infra.KafkaConfig

/**
 * Avro-oppsettet consumer-testene konstruerer mot.
 * Verken brokeren eller schema-registryet kontaktes så lenge consumeren ikke startes.
 */
val lokalAvroKafkaConfig = AvroKafkaConfig(
    kafkaConfig = KafkaConfig(kafkaBrokers = "localhost:9092"),
    schemaRegistryUrl = "mock://test",
)
