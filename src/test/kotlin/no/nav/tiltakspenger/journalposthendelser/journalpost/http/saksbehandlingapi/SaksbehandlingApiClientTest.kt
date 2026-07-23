package no.nav.tiltakspenger.journalposthendelser.journalpost.http.saksbehandlingapi

import com.marcinziolo.kotlin.wiremock.equalTo
import com.marcinziolo.kotlin.wiremock.post
import com.marcinziolo.kotlin.wiremock.returns
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.journalposthendelser.testutils.testTokenProvider
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import org.junit.jupiter.api.Test

internal class SaksbehandlingApiClientTest {
    private val fnr = "12345678910"
    private val correlationId = CorrelationId.generate()

    private fun klient(baseUrl: String, transport: HttpTransport? = null) = if (transport == null) {
        SaksbehandlingApiClient(baseUrl = baseUrl, clock = fixedClock, authTokenProvider = testTokenProvider)
    } else {
        SaksbehandlingApiClient(baseUrl = baseUrl, clock = fixedClock, authTokenProvider = testTokenProvider, transport = transport)
    }

    @Test
    fun `henter saksnummer med default HttpKlient-oppsett`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/saksnummer"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = """{"saksnummer": "202412345"}"""
            }

            runTest {
                klient(wiremock.baseUrl()).hentEllerOpprettSaksnummer(fnr, correlationId).getOrFail() shouldBe "202412345"
            }
        }
    }

    @Test
    fun `sender fnr i body og correlationId som Nav-Call-Id`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"saksnummer": "202412345"}""")

        klient("http://saksbehandling-api", transport).hentEllerOpprettSaksnummer(fnr, correlationId).getOrFail()

        val kall = transport.mottatteKall.single()
        kall.bodyTekst shouldContain fnr
        kall.request.headers().firstValue("Nav-Call-Id").get() shouldBe correlationId.toString()
    }

    @Test
    fun `toString på FnrDTO maskerer fnr`() {
        FnrDTO(fnr).toString() shouldBe "FnrDTO(fnr=*****)"
    }

    @Test
    fun `feilstatus gir UventetStatus med responsbody`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(400, body = """{"melding": "ugyldig fnr"}""")

        val feil = klient("http://saksbehandling-api", transport).hentEllerOpprettSaksnummer(fnr, correlationId)
            .shouldBeInstanceOf<arrow.core.Either.Left<HttpKlientError>>()
            .value

        val uventetStatus = feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        uventetStatus.statusCode shouldBe 400
        uventetStatus.body shouldContain "ugyldig fnr"
    }
}
