package no.nav.tiltakspenger.journalposthendelser

enum class Profile {
    LOCAL,
    DEV,
    PROD,
}

private const val APPLICATION_NAME = "tiltakspenger-journalposthendelser"
const val KAFKA_CONSUMER_GROUP_ID = "$APPLICATION_NAME-consumer"

object Configuration {
    val applicationHttpPort = getEnvVar("PORT", 8084.toString()).toInt()
    val logbackConfigFile = getEnvVar("LOGBACK_CONFIG_FILE", "logback.local.xml")
    val safUrl: String = getEnvVar("SAF_URL")
    val safScope: String = getEnvVar("SAF_SCOPE")
    val topic = getEnvVar("AAPEN_DOK_JOURNALFOERING_TOPIC")
    val naisTokenIntrospectionEndpoint = getEnvVar("NAIS_TOKEN_INTROSPECION_ENDPOINT")
    val naisTokenEndpoint = getEnvVar("NAIS_TOKEN_ENDPOINT")
    val tokenExchangeEndpoint = getEnvVar("NAIS_TOKEN_EXCHANGE_ENDPOINT")

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
