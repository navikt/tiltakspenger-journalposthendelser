package no.nav.tiltakspenger.journalposthendelser

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.tiltakspenger.journalposthendelser.context.ApplicationContext
import no.nav.tiltakspenger.journalposthendelser.routes.setupRoutes
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Bakgrunnsprosessoppsett
import no.nav.tiltakspenger.libs.ktor.common.oppstart.KafkaConsumerOppsett
import no.nav.tiltakspenger.libs.ktor.common.oppstart.startApp
import no.nav.tiltakspenger.libs.tid.zoneIdOslo
import java.time.Clock
import io.micrometer.core.instrument.Clock as MicrometerClock

fun main() {
    System.setProperty("logback.configurationFile", Configuration.logbackConfigurationFile)
    System.setProperty("org.apache.avro.SERIALIZABLE_PACKAGES", Configuration.avroSerializablePackages)

    val log = KotlinLogging.logger {}

    start(log = log, clock = Clock.system(zoneIdOslo))
}

/**
 * Registeret appen eksponerer på `/metrics`, og som Kafka-consumeren fører målingene sine i.
 * Det er bevisst bundet til Prometheus sitt globale register: [no.nav.tiltakspenger.journalposthendelser.infra.MetricRegister] registrerer tellerne sine rett på [PrometheusRegistry.defaultRegistry], og de skal fortsatt bli med i skrapingen.
 * [MicrometerClock] er Micrometers egen klokke og har ingenting med appens [Clock] å gjøre; den brukes kun til å konstruere registeret.
 *
 * Tester skal aldri bruke denne.
 * Et globalt register er prosessglobal tilstand som ikke kan varieres per test, og et prosessnavn kan bare registreres én gang per register.
 * Testene lager i stedet sitt eget `PrometheusMeterRegistry(PrometheusConfig.DEFAULT)`.
 */
fun prometheusMeterRegistry(): PrometheusMeterRegistry = PrometheusMeterRegistry(
    PrometheusConfig.DEFAULT,
    PrometheusRegistry.defaultRegistry,
    MicrometerClock.SYSTEM,
)

fun start(
    log: KLogger,
    clock: Clock,
    port: Int = Configuration.httpPort,
    host: String = "0.0.0.0",
    isNais: Boolean = Configuration.isNais(),
    applicationContext: ApplicationContext = ApplicationContext(clock, prometheusMeterRegistry()),
) {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        log.error(e) { e.message }
    }
    log.info { "starting server" }

    startApp(
        log = log,
        port = port,
        host = host,
        isNais = isNais,
        oppsett = Bakgrunnsprosessoppsett(
            kafkaConsumers = if (isNais) {
                listOf(
                    KafkaConsumerOppsett(
                        navn = "journalposthendelse-consumer",
                        start = { applicationContext.journalposthendelseConsumer.run() },
                        stopp = { applicationContext.journalposthendelseConsumer.stop() },
                    ),
                )
            } else {
                emptyList()
            },
        ),
    ) { readiness ->
        setupRoutes(readiness = readiness, meterRegistry = applicationContext.meterRegistry)
    }
}
