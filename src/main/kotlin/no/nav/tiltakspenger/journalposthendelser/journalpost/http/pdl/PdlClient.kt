package no.nav.tiltakspenger.journalposthendelser.journalpost.http.pdl

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import no.nav.tiltakspenger.journalposthendelser.infra.graphql.GraphQLResponse
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.json.objectMapper
import tools.jackson.module.kotlin.readValue

/**
 * HTTP-klient for PDL (persondataløsningen) sitt GraphQL-API.
 *
 * Kildekode: https://github.com/navikt/pdl
 * Dokumentasjon: https://pdl-docs.ansatt.nav.no/
 * API-spec: https://github.com/navikt/pdl/blob/15bdc571f0357f97f524dc496fb16217ff4aa94d/apps/api/src/main/resources/schemas/pdl.graphqls#L17 og https://pdl-playground.dev.intern.nav.no/ og https://pdl-pip-api.intern.dev.nav.no/swagger-ui/index.html (Swagger)
 * Slack: #pdl
 * Teamkatalog: https://teamkatalogen.nav.no/team/034cbcd2-ac28-4e2e-88c8-345945933f70
 *
 * Spørringen henter ikke historiske identer, kun gjeldende.
 */
class PdlClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val getToken: suspend () -> AccessToken,
) {
    private val log = KotlinLogging.logger {}
    private val hentIdenterQuery =
        PdlClient::class
            .java
            .getResource("/graphql/getIdent.graphql")!!
            .readText()
            .replace(Regex("[\n\t]"), "")

    suspend fun hentGjeldendeIdent(fnr: String, journalpostId: JournalpostId): String? {
        val httpResponse = httpClient.post("$basePath/graphql") {
            bearerAuth(getToken().token)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            header("Tema", "IND")
            header("behandlingsnummer", "B470")
            setBody(
                HentIdenterRequest(
                    query = hentIdenterQuery,
                    variables = PdlVariables(
                        ident = fnr,
                    ),
                ),
            )
        }
        val responseBody = httpResponse.bodyAsText()
        if (!httpResponse.status.isSuccess()) {
            log.error { "Noe gikk galt ved kall til PDL for journalpostId $journalpostId: feilkode: ${httpResponse.status}, melding: $responseBody" }
            throw RuntimeException("Noe gikk galt ved kall til PDL")
        }
        val hentIdenterResponse = objectMapper.readValue<GraphQLResponse<HentIdenterResponse>?>(responseBody)

        if (hentIdenterResponse == null) {
            log.error { "Kall til PDL feilet for journalpostId $journalpostId" }
            return null
        }
        if (hentIdenterResponse.errors != null) {
            hentIdenterResponse.errors.forEach { log.error { "PDL returnerte feilmelding: $it" } }
            return null
        }
        if (hentIdenterResponse.data.hentIdenter == null || hentIdenterResponse.data.hentIdenter.identer.isEmpty()) {
            log.error { "Fant ingen identer i PDL for journalpostId $journalpostId" }
            return null
        }
        val gjeldendeIdent = hentIdenterResponse.data.hentIdenter.identer.firstOrNull {
            it.gruppe == IdentGruppe.FOLKEREGISTERIDENT
        } ?: hentIdenterResponse.data.hentIdenter.identer.firstOrNull {
            it.gruppe == IdentGruppe.NPID
        }
        return gjeldendeIdent?.ident
    }
}

data class HentIdenterRequest(val query: String, val variables: PdlVariables)

data class PdlVariables(val ident: String)

data class HentIdenterResponse(
    val hentIdenter: Identliste?,
)

data class Identliste(
    val identer: List<IdentInformasjon>,
)

data class IdentInformasjon(
    val gruppe: IdentGruppe,
    val ident: String,
)

enum class IdentGruppe {
    AKTORID,
    FOLKEREGISTERIDENT,
    NPID,
}
