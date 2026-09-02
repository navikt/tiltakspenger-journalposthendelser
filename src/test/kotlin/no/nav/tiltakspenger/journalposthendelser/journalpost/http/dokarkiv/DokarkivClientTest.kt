package no.nav.tiltakspenger.journalposthendelser.journalpost.http.dokarkiv

import com.marcinziolo.kotlin.wiremock.equalTo
import com.marcinziolo.kotlin.wiremock.put
import com.marcinziolo.kotlin.wiremock.returns
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.journalposthendelser.testutils.fnrGenerator
import no.nav.tiltakspenger.journalposthendelser.testutils.testTokenProvider
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import org.junit.jupiter.api.Test

class DokarkivClientTest {
    private val journalpostId = JournalpostId("4567")
    private val fnr = fnrGenerator.generer().verdi
    private val saksnummer = "202412345"

    private fun klient(baseUrl: String, transport: HttpTransport? = null) = if (transport == null) {
        DokarkivClient(baseUrl = baseUrl, clock = fixedClock, authTokenProvider = testTokenProvider)
    } else {
        DokarkivClient(baseUrl = baseUrl, clock = fixedClock, authTokenProvider = testTokenProvider, transport = transport)
    }

    @Test
    fun `knytter sak til journalpost med default HttpKlient-oppsett`() {
        withWireMockServer { wiremock ->
            wiremock.put {
                url equalTo "/rest/journalpostapi/v1/journalpost/4567"
            } returns {
                statusCode = 200
                header = "Content-Type" to "application/json"
            }

            runTest {
                klient(wiremock.baseUrl()).knyttSakTilJournalpost(
                    journalpostId = journalpostId,
                    saksnummer = saksnummer,
                    fnr = fnr,
                    gjelderPapirsoknad = true,
                ).getOrFail() shouldBe Unit
            }
        }
    }

    @Test
    fun `papirsoknad setter avsenderMottaker, digital gjor det ikke`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøTomRespons(200)
        transport.leggIKøTomRespons(200)
        val klient = klient("http://dokarkiv", transport)

        klient.knyttSakTilJournalpost(journalpostId, saksnummer, fnr, gjelderPapirsoknad = true).getOrFail()
        klient.knyttSakTilJournalpost(journalpostId, saksnummer, fnr, gjelderPapirsoknad = false).getOrFail()

        transport.mottatteKall[0].bodyTekst shouldContain """"avsenderMottaker":{"id":"$fnr""""
        transport.mottatteKall[1].bodyTekst shouldContain """"avsenderMottaker":null"""
    }

    @Test
    fun `ferdigstiller journalpost med PATCH`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøTomRespons(200)

        klient("http://dokarkiv", transport).ferdigstillJournalpost(journalpostId).getOrFail()

        val kall = transport.mottatteKall.single()
        kall.metode shouldBe "PATCH"
        kall.uri.toString() shouldBe "http://dokarkiv/rest/journalpostapi/v1/journalpost/4567/ferdigstill"
        kall.bodyTekst shouldContain "journalfoerendeEnhet"
    }

    @Test
    fun `feilstatus gir UventetStatus med responsbody`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(400, body = """{"feilmelding": "mangler sak"}""")

        val feil = klient("http://dokarkiv", transport).ferdigstillJournalpost(journalpostId)
            .shouldBeInstanceOf<arrow.core.Either.Left<HttpKlientError>>()
            .value

        val uventetStatus = feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        uventetStatus.statusCode shouldBe 400
        uventetStatus.body shouldContain "mangler sak"
    }
}
