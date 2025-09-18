package no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.CorrelationId

/**
 * https://oppgave.dev.intern.nav.no/
 */
class OppgaveClient(
    private val httpClient: HttpClient,
    basePath: String,
    private val getToken: suspend () -> AccessToken,
) {
    private val logger = KotlinLogging.logger {}
    private val apiPath = "$basePath/api/v1/oppgaver"

    suspend fun opprettOppgaveForPapirsoknad(
        fnr: String,
        journalpostId: String,
        correlationId: CorrelationId,
    ): Int {
        val request = OpprettOppgaveRequest.opprettOppgaveRequestForPapirsoknad(
            fnr = fnr,
            journalpostId = journalpostId,
        )
        return opprettOppgaveMedDuplikatkontroll(request, correlationId)
            .also { logger.info { "Opprettet behandle sak-oppgave med id $it for journalpostId $journalpostId" } }
    }

    suspend fun opprettJournalforingsoppgave(
        fnr: String,
        journalpostId: String,
        journalpostTittel: String,
        correlationId: CorrelationId,
    ): Int {
        val request = OpprettOppgaveRequest.opprettOppgaveRequestForJournalforingsoppgave(
            fnr = fnr,
            journalpostId = journalpostId,
            journalpostTittel = journalpostTittel,
        )
        return opprettOppgaveMedDuplikatkontroll(request, correlationId)
            .also { logger.info { "Opprettet journalføringsoppgave med id $it for journalpostId $journalpostId" } }
    }

    suspend fun opprettFordelingsoppgave(journalpostId: String, correlationId: CorrelationId): Int {
        val request = OpprettOppgaveRequest.opprettOppgaveRequestForFordelingsoppgave(
            journalpostId = journalpostId,
        )
        return opprettOppgaveMedDuplikatkontroll(request, correlationId)
            .also { logger.info { "Opprettet fordelingsoppgave med id $it for journalpostId $journalpostId" } }
    }

    private suspend fun opprettOppgaveMedDuplikatkontroll(
        opprettOppgaveRequest: OpprettOppgaveRequest,
        correlationId: CorrelationId,
    ): Int {
        val oppgaveResponse = finnOppgave(
            journalpostId = opprettOppgaveRequest.journalpostId,
            oppgaveType = opprettOppgaveRequest.oppgavetype,
            correlationId = correlationId,
        )
        if (oppgaveResponse.antallTreffTotalt > 0 && oppgaveResponse.oppgaver.isNotEmpty()) {
            logger.warn { "Åpen oppgave av type ${opprettOppgaveRequest.oppgavetype} for journalpostId: ${opprettOppgaveRequest.journalpostId} finnes fra før, correlationId: ${correlationId.value}" }
            return oppgaveResponse.oppgaver.first().id
        }
        return opprettOppgave(opprettOppgaveRequest, correlationId)
    }

    private suspend fun opprettOppgave(
        opprettOppgaveRequest: OpprettOppgaveRequest,
        correlationId: CorrelationId,
    ): Int {
        val httpResponse = httpClient.post(apiPath) {
            header("X-Correlation-ID", correlationId.toString())
            bearerAuth(getToken().token)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(opprettOppgaveRequest)
        }

        if (httpResponse.status == HttpStatusCode.Created) {
            return httpResponse.body<OppgaveResponse>().id
        } else {
            val errorResponse = httpResponse.bodyAsText()
            logger.error { "Noe gikk galt ved oppretting av oppgave for journalpost med id ${opprettOppgaveRequest.journalpostId}: ${httpResponse.status.value}, $errorResponse, correlationId ${correlationId.value}" }
            throw RuntimeException("Oppgave svarte med feilmelding ved oppretting av oppgave: ${httpResponse.status.value}")
        }
    }

    private suspend fun finnOppgave(
        journalpostId: String,
        oppgaveType: String,
        correlationId: CorrelationId,
    ): FinnOppgaveResponse {
        val httpResponse = httpClient.get("$apiPath?tema=$TEMA_TILTAKSPENGER&oppgavetype=$oppgaveType&journalpostId=$journalpostId&statuskategori=AAPEN") {
            header("X-Correlation-ID", correlationId.toString())
            bearerAuth(getToken().token)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
        }

        if (httpResponse.status == HttpStatusCode.OK) {
            return httpResponse.body<FinnOppgaveResponse>()
        } else {
            val errorResponse = httpResponse.bodyAsText()
            logger.error { "Noe gikk galt ved duplikatsjekk mot oppgave for journalpost med id $journalpostId: ${httpResponse.status.value}, $errorResponse, correlationId ${correlationId.value}" }
            throw RuntimeException("Oppgave svarte med feilmelding ved duplikatsjekk: ${httpResponse.status.value}")
        }
    }
}
