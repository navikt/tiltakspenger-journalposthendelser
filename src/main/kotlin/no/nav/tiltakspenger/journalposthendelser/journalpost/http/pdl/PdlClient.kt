package no.nav.tiltakspenger.journalposthendelser.journalpost.http.pdl

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import no.nav.tiltakspenger.journalposthendelser.infra.graphql.GraphQLResponse
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.kall.NavHeadere
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import no.nav.tiltakspenger.libs.httpklient.infra.retry.Retry
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import java.net.URI
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
 * Retryen replikerer den gamle ktor-klienten: fire forsøk totalt med konstant 1 s delay.
 * retryIkkeIdempotente er satt fordi GraphQL-oppslaget går som POST, men er et rent leseoppslag uten sideeffekter.
 *
 * @param transport Nettverks-sømmen til [HttpKlient]; default er produksjonstransporten, tester sender inn `FakeHttpTransport` slik at hele den reelle pipelinen kjører.
 */
class PdlClient(
    baseUrl: String,
    clock: Clock,
    authTokenProvider: AuthTokenProvider,
    connectTimeout: Duration = 5.seconds,
    timeout: Duration = 10.seconds,
    transport: HttpTransport = JavaHttpTransport(connectTimeout = connectTimeout),
) {
    private val httpKlient: HttpKlient = HttpKlient(
        clock = clock,
        config = HttpKlientConfig(
            timeout = timeout,
            auth = KlientAuth.System(authTokenProvider),
            retry = Retry.Fast(maksForsøk = 4, delay = 1.seconds, retryIkkeIdempotente = true),
        ),
        transport = transport,
    )

    private val graphqlUri = URI.create("$baseUrl/graphql")

    private val hentIdenterQuery =
        PdlClient::class
            .java
            .getResource("/graphql/getIdent.graphql")!!
            .readText()
            .replace(Regex("[\n\t]"), "")

    /**
     * Henter gjeldende folkeregisterident (eller NPID) for [fnr].
     * `Right(null)` betyr «fant ikke person» og håndteres som domeneutfall (fordelingsoppgave) av kalleren.
     */
    suspend fun hentGjeldendeIdent(fnr: String): Either<KanIkkeHenteIdent, String?> {
        return httpKlient.postJson<GraphQLResponse<HentIdenterResponse>>(
            uri = graphqlUri,
            body = HentIdenterRequest(
                query = hentIdenterQuery,
                variables = PdlVariables(ident = fnr),
            ),
            headere = listOf(
                NavHeadere.tema("IND"),
                NavHeadere.behandlingsnummer("B470"),
            ),
            godta = Statusregel.Eksakt(200),
        ).mapLeft {
            KanIkkeHenteIdent.KallFeilet(it)
        }.flatMap { respons ->
            respons.body.tilGjeldendeIdent(respons.metadata)
        }
    }

    /**
     * GraphQL svarer av design 200 OK på alle svar; funksjonelle feil ligger i errors-lista.
     * `not_found`/`bad_request` betyr «fant ikke person» og gir `Right(null)`; øvrige feilkoder (`server_error` o.l.) er reelle feil og gir Left.
     */
    private fun GraphQLResponse<HentIdenterResponse>.tilGjeldendeIdent(
        metadata: HttpKlientMetadata,
    ): Either<KanIkkeHenteIdent, String?> {
        val graphQLFeil = errors.orEmpty()
        if (graphQLFeil.isNotEmpty()) {
            return if (graphQLFeil.all { it.extensions?.code == "not_found" || it.extensions?.code == "bad_request" }) {
                null.right()
            } else {
                KanIkkeHenteIdent.GraphQLFeil(
                    feilkoder = graphQLFeil.map { it.extensions?.code ?: "ukjent" },
                    httpKlientMetadata = metadata,
                ).left()
            }
        }
        val identer = data?.hentIdenter?.identer.orEmpty()
        val gjeldendeIdent = identer.firstOrNull { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }
            ?: identer.firstOrNull { it.gruppe == IdentGruppe.NPID }
        return gjeldendeIdent?.ident.right()
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
