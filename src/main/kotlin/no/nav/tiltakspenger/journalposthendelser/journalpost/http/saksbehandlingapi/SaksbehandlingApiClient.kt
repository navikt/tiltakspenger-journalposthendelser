package no.nav.tiltakspenger.journalposthendelser.journalpost.http.saksbehandlingapi

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
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
 * HTTP-klient for tiltakspenger-saksbehandling-api (egen tjeneste).
 *
 * Kildekode: https://github.com/navikt/tiltakspenger-saksbehandling-api
 * Dokumentasjon: README-en i kildekode-repoet
 * API-spec: -
 * Slack: #tiltakspenger-værsågod (eget team)
 * Teamkatalog: https://teamkatalogen.nav.no/team/15bca3d2-2584-4167-85ba-faab1f1cfb53
 *
 * Retryen replikerer den gamle ktor-klienten: fire forsøk totalt med konstant 1 s delay.
 * retryIkkeIdempotente er satt for paritet med den gamle klienten; kallet er reelt idempotent (hent-eller-opprett).
 *
 * @param transport Transporten som gjør nettverkskallet; default er produksjonstransporten, tester sender inn `FakeHttpTransport` slik at hele den reelle pipelinen kjører.
 */
class SaksbehandlingApiClient(
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

    private val saksnummerUri = URI.create("$baseUrl/saksnummer")

    suspend fun hentEllerOpprettSaksnummer(
        fnr: String,
        correlationId: CorrelationId,
    ): Either<HttpKlientError, String> {
        return httpKlient.postJson<SaksnummerResponse>(
            uri = saksnummerUri,
            body = FnrDTO(fnr),
            headere = listOf(NavHeadere.navCallId(correlationId.toString())),
            godta = Statusregel.Eksakt(200),
        ).map { it.body.saksnummer }
    }
}

data class FnrDTO(
    val fnr: String,
) {
    /** [fnr] er PII og skal ikke bli med om noen logger hele objektet. */
    override fun toString() = "FnrDTO(fnr=*****)"
}

data class SaksnummerResponse(
    val saksnummer: String,
)
