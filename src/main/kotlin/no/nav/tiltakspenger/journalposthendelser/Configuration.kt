package no.nav.tiltakspenger.journalposthendelser

private const val APPLICATION_NAME = "tiltakspenger-journalposthendelser"
const val KAFKA_CONSUMER_GROUP_ID = "$APPLICATION_NAME-consumer-v2"

private fun hentConfigForMiljø(): EnvironmentConfig {
    return when (System.getenv("NAIS_CLUSTER_NAME")) {
        "prod-gcp" -> ProdConfig
        "dev-gcp" -> DevConfig
        else -> LocalConfig
    }
}

object Configuration : EnvironmentConfig by hentConfigForMiljø() {
    val topic = "teamdokumenthandtering.aapen-dok-journalfoering"
    val avroSerializablePackages = "no.nav.joarkjournalfoeringhendelser"

    fun isNais(): Boolean = profile != Profile.LOCAL

    fun isProd(): Boolean = profile == Profile.PROD
}
