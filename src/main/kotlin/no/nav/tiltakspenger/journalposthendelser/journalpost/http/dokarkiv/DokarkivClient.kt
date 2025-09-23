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

/**
 * Dokumentasjon for Dokarkiv
 * https://confluence.adeo.no/spaces/BOA/pages/320340890/journalpostapi
 * https://dokarkiv-q2.dev-fss-pub.nais.io/swagger-ui/index.html
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
        journalpostId: String,
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
        journalpostId: String,
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
