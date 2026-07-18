package no.nav.tiltakspenger.journalposthendelser

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.journalposthendelser.context.ApplicationContext
import no.nav.tiltakspenger.journalposthendelser.routes.setupRoutes
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Bakgrunnsprosessoppsett
import no.nav.tiltakspenger.libs.ktor.common.oppstart.KafkaConsumerOppsett
import no.nav.tiltakspenger.libs.ktor.common.oppstart.startApp
import no.nav.tiltakspenger.libs.tid.zoneIdOslo
import java.time.Clock

// Påkrevd av Bakgrunnsprosessoppsett, men ubrukt her siden appen ikke har skedulerte jobber (kun Kafka-consumer).
private const val CALL_ID_MDC_KEY = "call-id"

fun main() {
    System.setProperty("logback.configurationFile", Configuration.logbackConfigFile)

    val log = KotlinLogging.logger {}

    start(log = log, clock = Clock.system(zoneIdOslo))
}

fun start(
    log: KLogger,
    clock: Clock,
    port: Int = Configuration.applicationHttpPort,
    isNais: Boolean = Configuration.isNais(),
    applicationContext: ApplicationContext = ApplicationContext(clock),
) {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        log.error(e) { e.message }
    }
    log.info { "starting server" }

    startApp(
        log = log,
        port = port,
        isNais = isNais,
        oppsett = Bakgrunnsprosessoppsett(
            mdcCallIdKey = CALL_ID_MDC_KEY,
            electorPath = Configuration::electorPath,
            kafkaConsumers = if (isNais) {
                listOf(
                    KafkaConsumerOppsett(
                        navn = "journalposthendelse-consumer",
                        start = { applicationContext.journalposthendelseConsumer.run() },
                        stopp = {},
                    ),
                )
            } else {
                emptyList()
            },
            clock = clock,
        ),
    ) { readiness ->
        setupRoutes(readiness = readiness)
    }
}
