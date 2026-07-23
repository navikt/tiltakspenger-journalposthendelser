package no.nav.tiltakspenger.journalposthendelser.routes

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Readiness
import org.junit.jupiter.api.Test

class RouteSettingsTest {
    @Test
    fun `setter opp helseruter og metrics`() = testApplication {
        application { setupRoutes(readiness = Readiness()) }

        client.get("/isalive").status shouldBe HttpStatusCode.OK
        // En nyopprettet Readiness er ikke klar før livssyklusen har satt den klar.
        client.get("/isready").status shouldBe HttpStatusCode.ServiceUnavailable

        val metrics = client.get("/metrics")
        metrics.status shouldBe HttpStatusCode.OK
        metrics.bodyAsText() shouldContain "ktor_http_server_requests"
    }
}
