package no.nav.tiltakspenger.journalposthendelser.journalpost

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import no.nav.tiltakspenger.journalposthendelser.azure.v2.AzureAdV2Client
import java.time.LocalDateTime

/*
 https://confluence.adeo.no/spaces/BOA/pages/309563005/saf
 */
class SafJournalpostClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val accessTokenClientV2: AzureAdV2Client,
    private val scope: String,
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
        val accessToken = accessTokenClientV2.getAccessToken(scope)
        if (accessToken?.accessToken == null) {
            throw RuntimeException("Klarte ikke hente ut accesstoken for Saf")
        }

        val findJournalpostRequest =
            FindJournalpostRequest(
                query = journalPostQuery,
                variables = Variables(journalpostId),
            )

        val findJournalpostResponse =
            httpClient
                .post(basePath) {
                    setBody(findJournalpostRequest)
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${accessToken.accessToken}")
                        append("X-Correlation-ID", journalpostId)
                        append(HttpHeaders.ContentType, "application/json")
                    }
                }
                .body<GraphQLResponse<FindJournalpostResponse>?>()

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
