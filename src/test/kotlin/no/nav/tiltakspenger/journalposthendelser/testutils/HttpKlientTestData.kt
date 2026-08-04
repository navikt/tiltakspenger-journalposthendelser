package no.nav.tiltakspenger.journalposthendelser.testutils

import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.HttpKlientTidsstempler
import no.nav.tiltakspenger.libs.httpklient.Tidsgrenser
import no.nav.tiltakspenger.libs.httpklient.UriSynlighet
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider
import java.net.URI
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val testTokenProvider = object : AuthTokenProvider {
    override suspend fun hentToken(skipCache: Boolean) = AccessToken("token", Instant.now(fixedClock).plusSeconds(3600))
}

/** Minimal metadata til feiltyper i tester som ikke bryr seg om HTTP-detaljene. */
fun tomHttpKlientMetadata(statusCode: Int? = 200) = HttpKlientMetadata(
    method = "POST",
    uri = URI.create("https://example.test/endepunkt"),
    uriSynlighet = UriSynlighet.VanligLogg,
    tidsgrenser = Tidsgrenser(svar = 30.seconds, oppkobling = 10.seconds),
    rawRequestString = "{}",
    rawResponseString = "{}",
    requestHeaders = emptyMap(),
    responseHeaders = emptyMap(),
    statusCode = statusCode,
    attempts = 1,
    attemptDurations = emptyList(),
    totalDuration = Duration.ZERO,
    tidsstempler = HttpKlientTidsstempler.INGEN,
)
