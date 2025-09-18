package no.nav.tiltakspenger.journalposthendelser.routes

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry

fun Application.setupRoutes() {
    routing { healthRoutes() }
    metrics()
}

fun Application.metrics() {
    val appMicrometerRegistry = PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT,
        PrometheusRegistry.defaultRegistry,
        Clock.SYSTEM,
    )

    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
    }
    routing {
        get("/metrics") {
            call.respond(appMicrometerRegistry.scrape())
        }
    }
}
