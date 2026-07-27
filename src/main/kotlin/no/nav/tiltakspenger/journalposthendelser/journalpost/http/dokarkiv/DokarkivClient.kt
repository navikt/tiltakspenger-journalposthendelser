package no.nav.tiltakspenger.journalposthendelser.journalpost.http.dokarkiv

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.retry.Retry
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import java.net.URI
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
 * Retryen replikerer den gamle ktor-klienten: fire forsøk totalt med konstant 1 s delay.
 * retryIkkeIdempotente er satt for paritet med den gamle klienten; begge kallene er reelt idempotente (PUT, og ferdigstilling av en allerede ferdigstilt journalpost er en no-op).
 *
 * @param transport Transporten som gjør nettverkskallet; default er produksjonstransporten, tester sender inn `FakeHttpTransport` slik at hele den reelle pipelinen kjører.
 */
class DokarkivClient(
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

    private val apiPath = "$baseUrl/rest/journalpostapi/v1/journalpost"

    suspend fun knyttSakTilJournalpost(
        journalpostId: JournalpostId,
        saksnummer: String,
        fnr: String,
        gjelderPapirsoknad: Boolean,
    ): Either<HttpKlientError, Unit> {
        return httpKlient.putJsonUtenSvar(
            uri = URI.create("$apiPath/$journalpostId"),
            body = OppdaterJournalpostRequest(
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
        ).map { }
    }

    suspend fun ferdigstillJournalpost(
        journalpostId: JournalpostId,
    ): Either<HttpKlientError, Unit> {
        return httpKlient.patchJsonUtenSvar(
            uri = URI.create("$apiPath/$journalpostId/ferdigstill"),
            body = FerdigstillJournalpostRequest(),
        ).map { }
    }
}
