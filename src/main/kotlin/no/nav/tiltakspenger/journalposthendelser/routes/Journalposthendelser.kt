package no.nav.tiltakspenger.journalposthendelser.routes

import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import no.nav.tiltakspenger.journalposthendelser.context.ApplicationContext

fun Application.journalposthendelser(
    applicationContext: ApplicationContext,
) {
    routing {
        healthRoutes()
    }
}
