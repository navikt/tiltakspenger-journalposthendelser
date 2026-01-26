package no.nav.tiltakspenger.journalposthendelser

enum class Profile {
    LOCAL,
    DEV,
    PROD,
}

private const val APPLICATION_NAME = "tiltakspenger-journalposthendelser"
const val KAFKA_CONSUMER_GROUP_ID = "$APPLICATION_NAME-consumer-v2"

object Configuration {
    val applicationHttpPort = 8080
    val logbackConfigFile = "logback.xml"
    val safUrl: String = getEnvVar("SAF_URL")
    val safScope: String = getEnvVar("SAF_SCOPE")
    val saksbehandlingApiUrl: String = getEnvVar("SAKSBEHANDLING_API_URL")
    val saksbehandlingApiScope: String = getEnvVar("SAKSBEHANDLING_API_SCOPE")
    val oppgaveUrl: String = getEnvVar("OPPGAVE_URL")
    val oppgaveScope: String = getEnvVar("OPPGAVE_SCOPE")
    val dokarkivUrl: String = getEnvVar("DOKARKIV_URL")
    val dokarkivScope: String = getEnvVar("DOKARKIV_SCOPE")
    val pdlUrl: String = getEnvVar("PDL_URL")
    val pdlScope: String = getEnvVar("PDL_SCOPE")

    val topic = "teamdokumenthandtering.aapen-dok-journalfoering"
    val naisTokenIntrospectionEndpoint = getEnvVar("NAIS_TOKEN_INTROSPECTION_ENDPOINT")
    val naisTokenEndpoint = getEnvVar("NAIS_TOKEN_ENDPOINT")
    val tokenExchangeEndpoint = getEnvVar("NAIS_TOKEN_EXCHANGE_ENDPOINT")

    val jdbcUrl = getEnvVar("DB_JDBC_URL")

    fun isNais() = applicationProfile() != Profile.LOCAL

    fun isProd() = applicationProfile() == Profile.PROD

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
