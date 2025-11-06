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
    connectTimeoutMillis: Long = 2000,
    requestTimeoutMillis: Long = 5000,
    socketTimeoutMillis: Long = 2000,
) = HttpClient(Apache).config(connectTimeoutMillis, requestTimeoutMillis, socketTimeoutMillis)

private fun HttpClient.config(
    connectTimeoutMillis: Long,
    requestTimeoutMillis: Long,
    socketTimeoutMillis: Long,
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
            this.connectTimeoutMillis = Duration.ofSeconds(connectTimeoutMillis).toMillis()
            this.requestTimeoutMillis = Duration.ofSeconds(requestTimeoutMillis).toMillis()
            this.socketTimeoutMillis = Duration.ofSeconds(socketTimeoutMillis).toMillis()
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
