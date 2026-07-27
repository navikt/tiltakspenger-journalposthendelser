package no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.JournalpostId
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
 * HTTP-klient for oppgave-API-et (opprettelse og søk av oppgaver).
 *
 * Kildekode: https://github.com/navikt/oppgave
 * Dokumentasjon: https://confluence.adeo.no/spaces/BOA/pages/791031394/dokarkiv+tjenesteoversikt og https://kodeverk-web.intern.nav.no/kodeverk/Oppgavetyper
 * API-spec: https://oppgave.intern.dev.nav.no/ (Swagger)
 * Slack: #team-oppgavehåndtering
 * Teamkatalog: https://teamkatalogen.nav.no/team/1672d05d-46ed-4406-a3a4-8343db75c285
 *
 * Duplikatkontrollen (finn åpen oppgave før opprettelse) er forretningslogikk og ligger i [no.nav.tiltakspenger.journalposthendelser.journalpost.OppgaveService].
 * Retryen replikerer den gamle ktor-klienten: fire forsøk totalt med konstant 1 s delay.
 * retryIkkeIdempotente er satt for paritet med den gamle klienten, som også retryet POST-ene; duplikatkontrollen i servicen begrenser konsekvensen av et gjentatt opprett-kall.
 *
 * @param transport Transporten som gjør nettverkskallet; default er produksjonstransporten, tester sender inn `FakeHttpTransport` slik at hele den reelle pipelinen kjører.
 */
class OppgaveClient(
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

    private val apiPath = "$baseUrl/api/v1/oppgaver"

    suspend fun opprettOppgave(
        request: OpprettOppgaveRequest,
        correlationId: CorrelationId,
    ): Either<HttpKlientError, Int> {
        return httpKlient.postJson<OppgaveResponse>(
            uri = URI.create(apiPath),
            body = request,
            headere = listOf(NavHeadere.xCorrelationId(correlationId.toString())),
            godta = Statusregel.Eksakt(201),
        ).map { it.body.id }
    }

    suspend fun finnOppgaver(
        journalpostId: JournalpostId,
        oppgavetyper: List<String>,
        correlationId: CorrelationId,
    ): Either<HttpKlientError, FinnOppgaveResponse> {
        val query = buildList {
            add("tema=$TEMA_TILTAKSPENGER")
            oppgavetyper.forEach { add("oppgavetype=$it") }
            add("journalpostId=$journalpostId")
            add("statuskategori=AAPEN")
        }.joinToString("&")
        return httpKlient.getJson<FinnOppgaveResponse>(
            uri = URI.create("$apiPath?$query"),
            headere = listOf(NavHeadere.xCorrelationId(correlationId.toString())),
            godta = Statusregel.Eksakt(200),
        ).map { it.body }
    }
}
