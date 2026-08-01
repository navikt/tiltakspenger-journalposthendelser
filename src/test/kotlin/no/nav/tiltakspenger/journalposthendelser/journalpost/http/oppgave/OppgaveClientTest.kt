package no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave

import com.marcinziolo.kotlin.wiremock.equalTo
import com.marcinziolo.kotlin.wiremock.post
import com.marcinziolo.kotlin.wiremock.returns
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.journalposthendelser.testutils.testTokenProvider
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import org.junit.jupiter.api.Test

class OppgaveClientTest {
    private val journalpostId = JournalpostId("4567")
    private val correlationId = CorrelationId.generate()

    private fun klient(baseUrl: String, transport: HttpTransport? = null) = if (transport == null) {
        OppgaveClient(baseUrl = baseUrl, clock = fixedClock, authTokenProvider = testTokenProvider)
    } else {
        OppgaveClient(baseUrl = baseUrl, clock = fixedClock, authTokenProvider = testTokenProvider, transport = transport)
    }

    private val request = OpprettOppgaveRequest.opprettOppgaveRequestForPapirsoknad(
        fnr = "12345678910",
        journalpostId = journalpostId,
        clock = fixedClock,
    )

    @Test
    fun `oppretter oppgave med default HttpKlient-oppsett`() {
        withWireMockServer { wiremock ->
            wiremock.post {
                url equalTo "/api/v1/oppgaver"
            } returns {
                statusCode = 201
                header = "Content-Type" to "application/json"
                body = """{"id": 9876}"""
            }

            runTest {
                klient(wiremock.baseUrl()).opprettOppgave(request, correlationId).getOrFail() shouldBe 9876
            }
        }
    }

    @Test
    fun `finnOppgaver bygger query med tema, oppgavetyper, journalpostId og statuskategori`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"antallTreffTotalt": 1, "oppgaver": [{"id": 9876}]}""")

        val respons = klient("http://oppgave", transport).finnOppgaver(
            journalpostId = journalpostId,
            oppgavetyper = listOf(OppgaveType.BEHANDLE_SAK.kode, OppgaveType.JOURNALFORING.kode),
            correlationId = correlationId,
        ).getOrFail()

        respons shouldBe FinnOppgaveResponse(antallTreffTotalt = 1, oppgaver = listOf(OppgaveResponse(9876)))
        val uri = transport.mottatteKall.single().uri.toString()
        uri shouldContain "tema=IND"
        uri shouldContain "oppgavetype=BEH_SAK"
        uri shouldContain "oppgavetype=JFR"
        uri shouldContain "journalpostId=4567"
        uri shouldContain "statuskategori=AAPEN"
    }

    @Test
    fun `200 ved opprettelse er ikke godtatt - kun 201`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"id": 9876}""", statusCode = 200)

        val feil = klient("http://oppgave", transport).opprettOppgave(request, correlationId)
            .shouldBeInstanceOf<arrow.core.Either.Left<HttpKlientError>>()
            .value

        feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 200
    }

    @Test
    fun `feilstatus gir UventetStatus med responsbody`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(400, body = """{"feilmelding": "ugyldig oppgave"}""")

        val feil = klient("http://oppgave", transport).opprettOppgave(request, correlationId)
            .shouldBeInstanceOf<arrow.core.Either.Left<HttpKlientError>>()
            .value

        val uventetStatus = feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        uventetStatus.statusCode shouldBe 400
        uventetStatus.body shouldContain "ugyldig oppgave"
    }
}
