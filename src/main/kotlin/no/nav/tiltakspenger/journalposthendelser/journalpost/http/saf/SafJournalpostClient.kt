package no.nav.tiltakspenger.journalposthendelser.journalpost.http.saf

import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.JournalpostMetadata
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.json.objectMapper
import java.time.LocalDateTime

/**
 * Dokumentasjon for SAF
 * https://confluence.adeo.no/x/fY5zEg
 */
class SafJournalpostClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val getToken: suspend () -> AccessToken,
) {
    val log = KotlinLogging.logger {}
    private val journalPostQuery =
        SafJournalpostClient::class
            .java
            .getResource("/graphql/findJournalpost.graphql")!!
            .readText()
            .replace(Regex("[\n\t]"), "")

    suspend fun getJournalpostMetadata(
        journalpostId: String,
    ): JournalpostMetadata? {
        val accessToken = getToken().token

        val findJournalpostRequest =
            FindJournalpostRequest(
                query = journalPostQuery,
                variables = Variables(journalpostId),
            )

        val httpResponse =
            httpClient
                .post("$basePath/graphql") {
                    setBody(findJournalpostRequest)
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $accessToken")
                        append("X-Correlation-ID", journalpostId)
                        append(HttpHeaders.ContentType, "application/json")
                    }
                }
        val findJournalpostResponseString = httpResponse.bodyAsText()
        if (!httpResponse.status.isSuccess()) {
            log.error { "Noe gikk galt ved kall til SAF for journalpostId $journalpostId: feilkode: ${httpResponse.status}, melding: $findJournalpostResponseString" }
            throw RuntimeException("Noe gikk galt ved kall til SAF")
        }

        val findJournalpostResponse =
            objectMapper.readValue<GraphQLResponse<FindJournalpostResponse>?>(findJournalpostResponseString)

        if (findJournalpostResponse == null) {
            log.error { "Kall til SAF feilet for $journalpostId" }
            return null
        }

        if (findJournalpostResponse.errors != null) {
            findJournalpostResponse.errors.forEach { log.error { "Saf kastet error: $it" } }
            return null
        }

        if (findJournalpostResponse.data.journalpost.journalstatus == null) {
            log.error { "Klarte ikke hente data fra SAF $journalpostId" }
            return null
        }

        val journalpost = findJournalpostResponse.data.journalpost

        return journalpost.let {
            JournalpostMetadata(
                bruker =
                Bruker(
                    it.bruker?.id,
                    it.bruker?.type,
                ),
                journalpostErIkkeJournalfort = erIkkeJournalfort(it.journalstatus),
                datoOpprettet = dateTimeStringTilLocalDateTime(it.datoOpprettet),
                dokumentInfoIdPdf = journalpost.dokumenter?.first()?.dokumentInfoId,
                dokumenter = journalpost.dokumenter,
            )
        }
    }

    private fun erIkkeJournalfort(journalstatus: Journalstatus?): Boolean {
        return journalstatus?.name?.let {
            it.equals("MOTTATT", true) || it.equals("FEILREGISTRERT", true)
        }
            ?: false
    }

    fun dateTimeStringTilLocalDateTime(dateTime: String?): LocalDateTime? {
        dateTime?.let {
            return try {
                LocalDateTime.parse(dateTime)
            } catch (e: Exception) {
                log.error { "Journalpost har ikke en gyldig datoOpprettet $dateTime, $e" }
                null
            }
        }
        log.error { "Journalpost mangler datoOpprettet $dateTime" }
        return null
    }
}

data class GraphQLResponse<T>(
    val data: T,
    val errors: List<ResponseError>?,
)

data class ResponseError(
    val message: String?,
    val locations: List<ErrorLocation>?,
    val path: List<String>?,
    val extensions: ErrorExtension?,
)

data class ErrorLocation(
    val line: String?,
    val column: String?,
)

data class ErrorExtension(
    val code: String?,
    val classification: String?,
)

data class FindJournalpostRequest(val query: String, val variables: Variables)

data class Variables(val id: String)

data class FindJournalpostResponse(
    val journalpost: Journalpost,
)

data class Journalpost(
    val avsenderMottaker: AvsenderMottaker?,
    val bruker: Bruker?,
    val datoOpprettet: String?,
    val dokumenter: List<Dokument>?,
    val journalposttype: String,
    val journalstatus: Journalstatus?,
    val kanal: String?,
    val kanalnavn: String?,
    val opprettetAvNavn: String?,
    val sak: Sak?,
    val skjerming: String?,
    val tema: String?,
    val temanavn: String?,
    val tittel: String?,
)

enum class Journalstatus {
    MOTTATT,
    JOURNALFOERT,
    FERDIGSTILT,
    EKSPEDERT,
    UNDER_ARBEID,
    FEILREGISTRERT,
    UTGAAR,
    AVBRUTT,
    UKJENT_BRUKER,
    RESERVERT,
    OPPLASTING_DOKUMENT,
    UKJENT,
}

data class Sak(
    val fagsakId: String?,
    val fagsaksystem: String?,
    val sakstype: String?,
)

data class Dokument(
    val tittel: String?,
    val dokumentInfoId: String,
    val brevkode: String?,
    val dokumentvarianter: List<Dokumentvarianter>,
)

data class Dokumentvarianter(
    val variantformat: Variantformat,
)

enum class Variantformat {
    ARKIV,
    FULLVERSJON,
    PRODUKSJON,
    PRODUKSJON_DLF,
    SLADDET,
    ORIGINAL,
}

data class AvsenderMottaker(
    val id: String?,
    val navn: String?,
)

data class Bruker(
    val id: String?,
    val type: BrukerIdType?,
)

enum class BrukerIdType {
    AKTOERID,
    FNR,
    ORGNR,
}
