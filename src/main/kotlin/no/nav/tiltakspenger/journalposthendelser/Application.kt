package no.nav.tiltakspenger.journalposthendelser

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.AttributeKey
import no.nav.tiltakspenger.journalposthendelser.context.ApplicationContext
import no.nav.tiltakspenger.journalposthendelser.routes.journalposthendelser

fun main() {
    System.setProperty("logback.configurationFile", Configuration.logbackConfigFile)

    val log = KotlinLogging.logger {}

    start(log = log)
}

fun start(
    log: KLogger,
    port: Int = Configuration.applicationHttpPort,
    applicationContext: ApplicationContext = ApplicationContext(),
) {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        log.error(e) { e.message }
    }

    log.info { "starting server" }

    val server = embeddedServer(
        factory = Netty,
        port = port,
        module = {
            journalposthendelser()
        },
    )
    server.application.attributes.put(isReadyKey, true)

    if (Configuration.isNais()) {
        applicationContext.journalposthendelseConsumer.run()
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.application.attributes.put(isReadyKey, false)
            server.stop(gracePeriodMillis = 5_000, timeoutMillis = 30_000)
        },
    )
    server.start(wait = true)
}

val isReadyKey = AttributeKey<Boolean>("isReady")

fun Application.isReady() = attributes.getOrNull(isReadyKey) == true
