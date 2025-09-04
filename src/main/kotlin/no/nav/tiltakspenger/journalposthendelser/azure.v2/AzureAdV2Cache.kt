package no.nav.tiltakspenger.journalposthendelser.azure.v2

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import java.security.MessageDigest
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AzureAdV2Cache {
    val log = KotlinLogging.logger {}

    private val cache =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(500)
            .build<String, AzureAdV2Token>()

    fun getAccessToken(scope: String): AzureAdV2Token? {
        val key = getSha256Key(scope)
        return cache.getIfPresent(key)?.let {
            when (it.expires.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
                true ->
                    cache.invalidate(key).let {
                        log.info { "Token in cache has expired" }
                        null
                    }

                else -> it
            }
        }
    }

    fun putValue(scope: String, azureAdV2Token: AzureAdV2Token): AzureAdV2Token {
        cache.put(getSha256Key(scope), azureAdV2Token)
        return azureAdV2Token
    }

    private fun getSha256Key(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).fold("") { str, it ->
            str + "%02x".format(it)
        }
}
