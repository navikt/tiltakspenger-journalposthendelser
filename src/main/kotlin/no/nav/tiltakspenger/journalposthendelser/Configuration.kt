package no.nav.tiltakspenger.journalposthendelser

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

enum class Profile {
    LOCAL,
    DEV,
    PROD,
}

private const val APPLICATION_NAME = "tiltakspenger-journalposthendelser"
const val KAFKA_CONSUMER_GROUP_ID = "$APPLICATION_NAME-consumer"

object Configuration {
    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "application.httpPort" to 8080.toString(),
                "logback.configurationFile" to "logback.xml",
                "KAFKA_BROKERS" to System.getenv("KAFKA_BROKERS"),
                "KAFKA_TRUSTSTORE_PATH" to System.getenv("KAFKA_TRUSTSTORE_PATH"),
                "KAFKA_KEYSTORE_PATH" to System.getenv("KAFKA_KEYSTORE_PATH"),
                "KAFKA_CREDSTORE_PASSWORD" to System.getenv("KAFKA_CREDSTORE_PASSWORD"),
            ),
        )

    private val prodProperties =
        ConfigurationMap(
            mapOf(
                "application.profile" to Profile.PROD.toString(),
            ),
        )

    private val devProperties =
        ConfigurationMap(
            mapOf(
                "application.profile" to Profile.DEV.toString(),
            ),
        )

    private val localProperties =
        ConfigurationMap(
            mapOf(
                "application.profile" to Profile.LOCAL.toString(),
                "application.httpPort" to 8084.toString(),
                "logback.configurationFile" to "logback.local.xml",
                "KAFKA_BROKERS" to "",
                "KAFKA_TRUSTSTORE_PATH" to "",
                "KAFKA_KEYSTORE_PATH" to "",
                "KAFKA_CREDSTORE_PASSWORD" to "",
            ),
        )

    fun applicationProfile() =
        when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
            "prod-gcp" -> Profile.PROD
            "dev-gcp" -> Profile.DEV
            else -> Profile.LOCAL
        }

    private fun config() =
        when (applicationProfile()) {
            Profile.PROD ->
                systemProperties() overriding prodProperties overriding defaultProperties

            Profile.DEV ->
                systemProperties() overriding devProperties overriding defaultProperties

            Profile.LOCAL -> {
                systemProperties() overriding localProperties overriding defaultProperties
            }
        }

    fun logbackConfigurationFile() = config()[Key("logback.configurationFile", stringType)]

    fun httpPort() = config()[Key("application.httpPort", intType)]

    fun isNais() = applicationProfile() != Profile.LOCAL
}
