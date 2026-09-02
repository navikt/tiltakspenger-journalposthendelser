package no.nav.tiltakspenger.journalposthendelser.journalpost.http.saf

import com.marcinziolo.kotlin.wiremock.equalTo
import com.marcinziolo.kotlin.wiremock.post
import com.marcinziolo.kotlin.wiremock.returns
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.journalposthendelser.testutils.testTokenProvider
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SafJournalpostClientTest {
    private val journalpostId = JournalpostId("4567")

    private fun klient(baseUrl: String, transport: HttpTransport? = null) = if (transport == null) {
        SafJournalpostClient(baseUrl = baseUrl, clock = fixedClock, authTokenProvider = testTokenProvider)
    } else {
        SafJournalpostClient(baseUrl = baseUrl, clock = fixedClock, authTokenProvider = testTokenProvider, transport = transport)
    }

    //language=JSON
    private val happyJson = """
        {
          "data": {
            "journalpost": {
              "avsenderMottaker": { "id": "12845678910", "navn": "Test Testesen" },
              "bruker": { "id": "12845678910", "type": "FNR" },
              "datoOpprettet": "2024-01-02T03:04:05",
              "dokumenter": [
                {
                  "tittel": "Søknad",
                  "dokumentInfoId": "111",
                  "brevkode": "NAV 76-13.45",
                  "dokumentvarianter": [ { "variantformat": "ARKIV" } ]
                }
              ],
              "journalposttype": "I",
              "journalstatus": "MOTTATT",
              "kanal": null,
              "kanalnavn": null,
              "opprettetAvNavn": null,
              "sak": { "fagsakId": "234", "fagsaksystem": "TILTAKSPENGER", "sakstype": "FAGSAK" },
              "skjerming": null,
              "tema": "IND",
              "temanavn": null,
              "tittel": "Søknad om tiltakspenger"
            }
          },
          "errors": null
        }
    """.trimIndent()

    @Test
    fun `henter journalpostmetadata med default HttpKlient-oppsett`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/graphql"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
                body = happyJson
            }

            runTest {
                val metadata = klient(wiremock.baseUrl()).getJournalpostMetadata(journalpostId).getOrFail()

                metadata.journalpostId shouldBe journalpostId
                metadata.bruker shouldBe Bruker("12845678910", BrukerIdType.FNR)
                metadata.erJournalfort shouldBe false
                metadata.datoOpprettet shouldBe LocalDateTime.parse("2024-01-02T03:04:05")
                metadata.brevkode shouldBe "NAV 76-13.45"
                metadata.tittel shouldBe "Søknad om tiltakspenger"
            }
        }
    }

    @Test
    fun `journalfort status og ugyldig datoOpprettet tolereres`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(
            happyJson
                .replace("MOTTATT", "JOURNALFOERT")
                .replace("2024-01-02T03:04:05", "ikke-en-dato"),
        )
        val metadata = klient("http://saf", transport).getJournalpostMetadata(journalpostId).getOrFail()

        metadata.erJournalfort shouldBe true
        metadata.datoOpprettet shouldBe null
    }

    @Test
    fun `journalpost uten arkivvariant gir null brevkode`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(happyJson.replace("ARKIV", "SLADDET"))

        val metadata = klient("http://saf", transport).getJournalpostMetadata(journalpostId).getOrFail()

        metadata.brevkode shouldBe null
    }

    @Test
    fun `graphql-feil gir GraphQLFeil`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"data": null, "errors": [{"message": "ingen tilgang", "locations": [{"line": "1", "column": "2"}], "path": null, "extensions": {"code": "forbidden", "classification": null}}]}""")

        val feil = klient("http://saf", transport).getJournalpostMetadata(journalpostId)
            .shouldBeInstanceOf<arrow.core.Either.Left<KanIkkeHenteJournalpost>>()
            .value

        feil.shouldBeInstanceOf<KanIkkeHenteJournalpost.GraphQLFeil>().feilkoder shouldBe listOf("forbidden")
    }

    @Test
    fun `journalpost uten journalstatus gir UfullstendigJournalpost`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"data": {"journalpost": null}, "errors": null}""")

        val feil = klient("http://saf", transport).getJournalpostMetadata(journalpostId)
            .shouldBeInstanceOf<arrow.core.Either.Left<KanIkkeHenteJournalpost>>()
            .value

        feil.shouldBeInstanceOf<KanIkkeHenteJournalpost.UfullstendigJournalpost>()
    }

    @Test
    fun `uventet status gir KallFeilet etter retry`() = runTest {
        val transport = FakeHttpTransport()
        // Retry.Fast(maksForsøk = 4) konsumerer ett køet svar per forsøk for retryable statuser som 500.
        repeat(4) { transport.leggIKøStatus(500, body = "kaboom") }

        val feil = klient("http://saf", transport).getJournalpostMetadata(journalpostId)
            .shouldBeInstanceOf<arrow.core.Either.Left<KanIkkeHenteJournalpost>>()
            .value

        feil.shouldBeInstanceOf<KanIkkeHenteJournalpost.KallFeilet>()
        transport.mottatteKall.size shouldBe 4
    }
}
