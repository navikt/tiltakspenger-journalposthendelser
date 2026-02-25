package no.nav.tiltakspenger.journalposthendelser.infra

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.serialization.jackson3.JacksonConverter
import no.nav.tiltakspenger.libs.json.objectMapper
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import java.time.Duration

fun httpClientApache(
    connectTimeoutSeconds: Long = 5,
    requestTimeoutSeconds: Long = 10,
    socketTimeoutSeconds: Long = 5,
) = HttpClient(Apache5).config(connectTimeoutSeconds, requestTimeoutSeconds, socketTimeoutSeconds)

private fun HttpClient.config(
    connectTimeoutSeconds: Long,
    requestTimeoutSeconds: Long,
    socketTimeoutSeconds: Long,
) =
    this.config {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter(objectMapper))
        }
        install(HttpTimeout) {
            this.connectTimeoutMillis = Duration.ofSeconds(connectTimeoutSeconds).toMillis()
            this.requestTimeoutMillis = Duration.ofSeconds(requestTimeoutSeconds).toMillis()
            this.socketTimeoutMillis = Duration.ofSeconds(socketTimeoutSeconds).toMillis()
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            retryOnServerErrors(maxRetries)
            retryOnException(retryOnTimeout = true)
            constantDelay(millis = 1000L)
        }
        install(Logging) {
            logger =
                object : Logger {
                    override fun log(message: String) {
                        Sikkerlogg.debug { message }
                    }
                }
            level = LogLevel.INFO
        }
    }
