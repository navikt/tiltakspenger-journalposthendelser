package no.nav.tiltakspenger.journalposthendelser

enum class Profile {
    LOCAL,
    DEV,
    PROD,
}

private const val APPLICATION_NAME = "tiltakspenger-journalposthendelser"
const val KAFKA_CONSUMER_GROUP_ID = "$APPLICATION_NAME-consumer"

object Configuration {
    val topic = getEnvVar("AAPEN_DOK_JOURNALFOERING_TOPIC")
    val kafkaBrokers = getEnvVar("KAFKA_BROKERS")
    val kafkaTruststorePath = getEnvVar("KAFKA_TRUSTSTORE_PATH")
    val kafkaKeystorePath = getEnvVar("KAFKA_KEYSTORE_PATH")
    val kafkaCredstorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD")
    val applicationHttpPort = getEnvVar("PORT", 8084.toString()).toInt()
    val logbackConfigFile = if (isNais()) "logback.xml" else "logback.local.xml"

    fun isNais() = applicationProfile() != Profile.LOCAL

    fun applicationProfile() =
        when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
            "prod-gcp" -> Profile.PROD
            "dev-gcp" -> Profile.DEV
            else -> Profile.LOCAL
        }

    private fun getEnvVar(varName: String, localVar: String = "") = if (isNais()) {
        System.getenv(varName)
            ?: throw RuntimeException("Missing required variable \"$varName\"")
    } else {
        localVar
    }
}
