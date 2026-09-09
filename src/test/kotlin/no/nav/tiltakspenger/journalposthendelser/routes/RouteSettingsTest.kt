package no.nav.tiltakspenger.journalposthendelser.routes

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.mockk
import no.nav.tiltakspenger.journalposthendelser.journalpost.kafka.JournalposthendelseConsumer
import no.nav.tiltakspenger.journalposthendelser.testutils.lokalAvroKafkaConfig
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Readiness
import org.junit.jupiter.api.Test

class RouteSettingsTest {
    @Test
    fun `setter opp helseruter og metrics`() = testApplication {
        application {
            setupRoutes(readiness = Readiness(), meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT))
        }

        client.get("/isalive").status shouldBe HttpStatusCode.OK
        // En nyopprettet Readiness er ikke klar før livssyklusen har satt den klar.
        client.get("/isready").status shouldBe HttpStatusCode.ServiceUnavailable

        val metrics = client.get("/metrics")
        metrics.status shouldBe HttpStatusCode.OK
        metrics.bodyAsText() shouldContain "ktor_http_server_requests"
    }

    /**
     * Verifiserer at registeret Kafka-consumeren skriver målingene sine til, er det samme registeret `/metrics` skraper.
     * Det er hele poenget med at `ApplicationContext` eier registeret og sender det både til `setupRoutes` og til consumeren: sender vi inn et annet register ett av stedene, forsvinner seriene stille, og varselregelen «Meldingsleser har stoppet» får aldri data.
     *
     * Consumeren konstrueres, men startes ikke.
     * Meldingsleser-målingene registreres i konstruktøren til `ManagedKafkaConsumer`, mens `run()` ville krevd en ekte Kafka-broker.
     * Appen har ingen skedulerte jobber, så testen trenger ikke `konfigurerOppstart` og dekker kun meldingsleser-siden.
     *
     * Testen lager sitt eget register i stedet for prod-registeret fra `prometheusMeterRegistry()`.
     * Et prosessnavn kan bare registreres én gang per register, og det globale registeret deles med alle andre tester i samme JVM.
     */
    @Test
    fun `consumeren fører målingene sine i registeret metrics skraper`() = testApplication {
        val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        application { setupRoutes(readiness = Readiness(), meterRegistry = meterRegistry) }

        JournalposthendelseConsumer(
            topic = "test-topic-metrics",
            avroKafkaConfig = lokalAvroKafkaConfig,
            journalposthendelseService = mockk(),
            clock = fixedClock,
            meterRegistry = meterRegistry,
        )

        val metrics = client.get("/metrics")
        metrics.status shouldBe HttpStatusCode.OK
        val body = metrics.bodyAsText()
        body shouldContain """tpts_bakgrunnsprosess_intervall_sekunder{prosess="test-topic-metrics",type="meldingsleser"}"""
        body shouldContain """tpts_bakgrunnsprosess_sist_vellykket_tidspunkt_sekunder{prosess="test-topic-metrics",type="meldingsleser"}"""
        body shouldContain "ktor_http_server_requests"
    }
}
