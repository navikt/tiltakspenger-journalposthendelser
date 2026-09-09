package no.nav.tiltakspenger.journalposthendelser.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Readiness
import no.nav.tiltakspenger.libs.ktor.common.oppstart.healthRoutes

fun Application.setupRoutes(readiness: Readiness, meterRegistry: PrometheusMeterRegistry) {
    routing { healthRoutes(erKlar = readiness::erKlar) }
    metrics(meterRegistry)
}

/**
 * Kobler Ktor-metrikkene og `/metrics` til registeret appen allerede eier.
 * Registeret kommer inn som parameter i stedet for å konstrueres her, slik at Kafka-consumeren fører målingene sine i nøyaktig det registeret som skrapes.
 * Konstruksjonen hører hjemme i komposisjonsroten, se `prometheusMeterRegistry()` i `Application.kt`.
 */
fun Application.metrics(meterRegistry: PrometheusMeterRegistry) {
    install(MicrometerMetrics) {
        registry = meterRegistry
    }
    routing {
        get("/metrics") {
            call.respondText(
                text = meterRegistry.scrape(),
                status = HttpStatusCode.OK,
            )
        }
    }
}
