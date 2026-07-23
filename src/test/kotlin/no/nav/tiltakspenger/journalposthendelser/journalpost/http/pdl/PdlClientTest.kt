package no.nav.tiltakspenger.journalposthendelser.journalpost.http.pdl

import com.marcinziolo.kotlin.wiremock.equalTo
import com.marcinziolo.kotlin.wiremock.post
import com.marcinziolo.kotlin.wiremock.returns
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.journalposthendelser.testutils.testTokenProvider
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import org.junit.jupiter.api.Test

internal class PdlClientTest {
    private val fnr = "12345678910"

    private fun klient(baseUrl: String, transport: HttpTransport? = null) = if (transport == null) {
        PdlClient(baseUrl = baseUrl, clock = fixedClock, authTokenProvider = testTokenProvider)
    } else {
        PdlClient(baseUrl = baseUrl, clock = fixedClock, authTokenProvider = testTokenProvider, transport = transport)
    }

    //language=JSON
    private val happyJson = """
        {
          "data": {
            "hentIdenter": {
              "identer": [
                { "gruppe": "AKTORID", "ident": "999" },
                { "gruppe": "FOLKEREGISTERIDENT", "ident": "10987654321" }
              ]
            }
          },
          "errors": null
        }
    """.trimIndent()

    @Test
    fun `henter gjeldende ident med default HttpKlient-oppsett og sender PDL-headerne`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/graphql"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = happyJson
            }

            runTest {
                val respons = klient(wiremock.baseUrl()).hentGjeldendeIdent(fnr)

                respons.getOrFail() shouldBe "10987654321"
            }
        }
    }

    @Test
    fun `sender Tema- og behandlingsnummer-header`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(happyJson)

        klient("http://pdl", transport).hentGjeldendeIdent(fnr).getOrFail()

        val kall = transport.mottatteKall.single()
        kall.request.headers().firstValue("Tema").get() shouldBe "IND"
        kall.request.headers().firstValue("behandlingsnummer").get() shouldBe "B470"
        kall.bodyTekst shouldContain fnr
    }

    @Test
    fun `NPID brukes når folkeregisterident mangler`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(
            """{"data": {"hentIdenter": {"identer": [{"gruppe": "NPID", "ident": "555"}]}}, "errors": null}""",
        )

        klient("http://pdl", transport).hentGjeldendeIdent(fnr).getOrFail() shouldBe "555"
    }

    @Test
    fun `not_found gir null - fant ikke person`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(
            """{"data": null, "errors": [{"message": "Fant ikke person", "locations": null, "path": null, "extensions": {"code": "not_found", "classification": null}}]}""",
        )

        klient("http://pdl", transport).hentGjeldendeIdent(fnr).getOrFail() shouldBe null
    }

    @Test
    fun `tom identliste gir null`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"data": {"hentIdenter": {"identer": []}}, "errors": null}""")

        klient("http://pdl", transport).hentGjeldendeIdent(fnr).getOrFail() shouldBe null
    }

    @Test
    fun `server_error fra graphql gir GraphQLFeil`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(
            """{"data": null, "errors": [{"message": "boom", "locations": null, "path": null, "extensions": {"code": "server_error", "classification": null}}]}""",
        )

        val feil = klient("http://pdl", transport).hentGjeldendeIdent(fnr)
            .shouldBeInstanceOf<arrow.core.Either.Left<KanIkkeHenteIdent>>()
            .value

        feil.shouldBeInstanceOf<KanIkkeHenteIdent.GraphQLFeil>().feilkoder shouldBe listOf("server_error")
    }
}
