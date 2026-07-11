package no.nav.tiltakspenger.journalposthendelser.journalpost.http.saksbehandlingapi

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.CorrelationId

/**
 * HTTP-klient for tiltakspenger-saksbehandling-api (egen tjeneste).
 *
 * Kildekode: https://github.com/navikt/tiltakspenger-saksbehandling-api
 * Dokumentasjon: README-en i kildekode-repoet
 * API-spec: -
 * Slack: #tiltakspenger-værsågod (eget team)
 * Teamkatalog: https://teamkatalogen.nav.no/team/15bca3d2-2584-4167-85ba-faab1f1cfb53
 */
class SaksbehandlingApiClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val getToken: suspend () -> AccessToken,
) {
    val log = KotlinLogging.logger {}

    suspend fun hentEllerOpprettSaksnummer(fnr: String, correlationId: CorrelationId): String {
        val httpResponse = httpClient.post("$basePath/saksnummer") {
            header("Nav-Call-Id", correlationId.toString())
            bearerAuth(getToken().token)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(FnrDTO(fnr))
        }
        when (httpResponse.status) {
            HttpStatusCode.OK -> return httpResponse.body<SaksnummerResponse>().saksnummer
            else -> throw RuntimeException("saksbehandling-api svarte med feilkode ved henting av saksnummer: ${httpResponse.status.value}")
        }
    }
}

data class FnrDTO(
    val fnr: String,
)

data class SaksnummerResponse(
    val saksnummer: String,
)
