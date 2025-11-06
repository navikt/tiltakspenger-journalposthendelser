package no.nav.tiltakspenger.journalposthendelser.infra

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.jackson.jackson
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import java.time.Duration

fun httpClientApache(
    connectTimeoutSeconds: Long = 2,
    requestTimeoutSeconds: Long = 5,
    socketTimeoutSeconds: Long = 2,
) = HttpClient(Apache).config(connectTimeoutSeconds, requestTimeoutSeconds, socketTimeoutSeconds)

private fun HttpClient.config(
    connectTimeoutSeconds: Long,
    requestTimeoutSeconds: Long,
    socketTimeoutSeconds: Long,
) =
    this.config {
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                registerKotlinModule()
                enable(SerializationFeature.INDENT_OUTPUT)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
        install(HttpTimeout) {
            this.connectTimeoutMillis = Duration.ofSeconds(connectTimeoutSeconds).toMillis()
            this.requestTimeoutMillis = Duration.ofSeconds(requestTimeoutSeconds).toMillis()
            this.socketTimeoutMillis = Duration.ofSeconds(socketTimeoutSeconds).toMillis()
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            retryOnServerErrors(maxRetries)
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
