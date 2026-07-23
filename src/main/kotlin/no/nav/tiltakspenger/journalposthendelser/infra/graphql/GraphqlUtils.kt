package no.nav.tiltakspenger.journalposthendelser.infra.graphql

/**
 * [data] er nullable fordi GraphQL-API-ene svarer 200 OK med `data: null` og utfylt [errors] ved funksjonelle feil.
 */
data class GraphQLResponse<T>(
    val data: T?,
    val errors: List<GraphQLResponseError>?,
)

data class GraphQLResponseError(
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
