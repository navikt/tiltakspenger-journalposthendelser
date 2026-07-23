package no.nav.tiltakspenger.journalposthendelser.testutils

import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.HttpKlientTidsstempler
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider
import java.time.Instant
import kotlin.time.Duration

val testTokenProvider = object : AuthTokenProvider {
    override suspend fun hentToken(skipCache: Boolean) = AccessToken("token", Instant.now(fixedClock).plusSeconds(3600))
}

/** Minimal metadata til feiltyper i tester som ikke bryr seg om HTTP-detaljene. */
fun tomHttpKlientMetadata(statusCode: Int? = 200) = HttpKlientMetadata(
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
