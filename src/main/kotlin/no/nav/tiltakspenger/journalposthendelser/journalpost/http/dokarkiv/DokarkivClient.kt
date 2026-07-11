package no.nav.tiltakspenger.journalposthendelser.journalpost.http.dokarkiv

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.JournalpostId

/**
 * HTTP-klient for dokarkiv sitt journalpostapi.
 *
 * Kildekode: https://github.com/navikt/dokarkiv
 * Dokumentasjon: https://confluence.adeo.no/display/BOA/dokarkiv og https://confluence.adeo.no/display/BOA/opprettJournalpost
 * API-spec: https://dokarkiv.dev.intern.nav.no/swagger-ui/index.html
 * Slack: #team-dokumentløsninger (https://nav-it.slack.com/archives/C6W9E5GPJ)
 * Teamkatalog: https://teamkatalogen.nav.no/team/f3388fcd-898e-40da-8d02-0bf1e3a79120
 *
 * For å kunne ferdigstille journalpost må journalposten være knyttet til en sak.
 */
class DokarkivClient(
    private val httpClient: HttpClient,
    basePath: String,
    private val getToken: suspend () -> AccessToken,
) {
    private val logger = KotlinLogging.logger {}
    private val apiPath = "$basePath/rest/journalpostapi/v1/journalpost"

    suspend fun knyttSakTilJournalpost(
        journalpostId: JournalpostId,
        saksnummer: String,
        fnr: String,
        gjelderPapirsoknad: Boolean,
        correlationId: CorrelationId,
    ) {
        val httpResponse = httpClient.put("$apiPath/$journalpostId") {
            bearerAuth(getToken().token)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(
                OppdaterJournalpostRequest(
                    sak = Sak(
                        fagsakId = saksnummer,
                    ),
                    bruker = OppdaterJournalpostRequest.Bruker(
                        id = fnr,
                    ),
                    avsenderMottaker = if (gjelderPapirsoknad) {
                        OppdaterJournalpostRequest.AvsenderMottaker(
                            id = fnr,
                        )
                    } else {
                        null
                    },
                ),
            )
        }
        if (!httpResponse.status.isSuccess()) {
            val errorResponse = httpResponse.bodyAsText()
            logger.error { "Noe gikk galt ved oppdatering av journalpost med id $journalpostId: ${httpResponse.status.value}, $errorResponse, correlationId ${correlationId.value}" }
            throw RuntimeException("Dokarkiv svarte med feilmelding ved oppdatering av journalpost: ${httpResponse.status.value}")
        }
    }

    suspend fun ferdigstillJournalpost(
        journalpostId: JournalpostId,
        correlationId: CorrelationId,
    ) {
        val httpResponse = httpClient.patch("$apiPath/$journalpostId/ferdigstill") {
            bearerAuth(getToken().token)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(FerdigstillJournalpostRequest())
        }
        if (!httpResponse.status.isSuccess()) {
            val errorResponse = httpResponse.bodyAsText()
            logger.error { "Noe gikk galt ved ferdigstilling av journalpost med id $journalpostId: ${httpResponse.status.value}, $errorResponse, correlationId ${correlationId.value}" }
            throw RuntimeException("Dokarkiv svarte med feilmelding ved ferdigstilling av journalpost: ${httpResponse.status.value}")
        }
    }
}
