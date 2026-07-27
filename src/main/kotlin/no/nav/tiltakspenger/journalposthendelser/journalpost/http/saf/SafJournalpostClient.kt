package no.nav.tiltakspenger.journalposthendelser.journalpost.http.saf

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import no.nav.tiltakspenger.journalposthendelser.infra.graphql.GraphQLResponse
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.JournalpostMetadata
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.kall.NavHeadere
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import no.nav.tiltakspenger.libs.httpklient.infra.retry.Retry
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import java.net.URI
import java.time.Clock
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * HTTP-klient for SAF (sak- og arkivfasade) sitt GraphQL-API.
 *
 * Kildekode: https://github.com/navikt/saf
 * Dokumentasjon: https://confluence.adeo.no/display/BOA/saf
 * API-spec: -
 * Slack: #team-dokumentløsninger (https://nav-it.slack.com/archives/C6W9E5GPJ)
 * Teamkatalog: https://teamkatalogen.nav.no/team/f3388fcd-898e-40da-8d02-0bf1e3a79120
 *
 * Timeoutene er arvet fra den gamle ktor-klientens «slow API»-variant (SAF kan være treg).
 * Retryen replikerer den gamle ktor-klienten: fire forsøk totalt med konstant 1 s delay.
 * retryIkkeIdempotente er satt fordi GraphQL-oppslaget går som POST, men er et rent leseoppslag uten sideeffekter.
 *
 * @param transport Transporten som gjør nettverkskallet; default er produksjonstransporten, tester sender inn `FakeHttpTransport` slik at hele den reelle pipelinen kjører.
 */
class SafJournalpostClient(
    baseUrl: String,
    clock: Clock,
    authTokenProvider: AuthTokenProvider,
    connectTimeout: Duration = 10.seconds,
    timeout: Duration = 15.seconds,
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

    private val graphqlUri = URI.create("$baseUrl/graphql")

    private val journalPostQuery =
        SafJournalpostClient::class
            .java
            .getResource("/graphql/findJournalpost.graphql")!!
            .readText()
            .replace(Regex("[\n\t]"), "")

    suspend fun getJournalpostMetadata(
        journalpostId: JournalpostId,
    ): Either<KanIkkeHenteJournalpost, JournalpostMetadata> {
        return httpKlient.postJson<GraphQLResponse<FindJournalpostResponse>>(
            uri = graphqlUri,
            body = FindJournalpostRequest(
                query = journalPostQuery,
                variables = Variables(journalpostId.toString()),
            ),
            headere = listOf(NavHeadere.xCorrelationId(journalpostId.toString())),
            godta = Statusregel.Eksakt(200),
        ).mapLeft {
            KanIkkeHenteJournalpost.KallFeilet(it)
        }.flatMap { respons ->
            respons.body.tilJournalpostMetadata(journalpostId, respons.metadata)
        }
    }

    /**
     * GraphQL svarer av design 200 OK på alle svar; funksjonelle feil ligger i errors-lista.
     * Alle feilkodene er fatale her: en journalposthendelse gjelder alltid en journalpost som skal finnes, så «not_found» er like unormalt som «server_error» — consumeren kaster og lar Kafka-retryen prøve på nytt.
     */
    private fun GraphQLResponse<FindJournalpostResponse>.tilJournalpostMetadata(
        journalpostId: JournalpostId,
        metadata: HttpKlientMetadata,
    ): Either<KanIkkeHenteJournalpost, JournalpostMetadata> {
        val graphQLFeil = errors.orEmpty()
        if (graphQLFeil.isNotEmpty()) {
            return KanIkkeHenteJournalpost.GraphQLFeil(
                feilkoder = graphQLFeil.map { it.extensions?.code ?: "ukjent" },
                httpKlientMetadata = metadata,
            ).left()
        }
        val journalpost = data?.journalpost
        if (journalpost?.journalstatus == null) {
            return KanIkkeHenteJournalpost.UfullstendigJournalpost(metadata).left()
        }
        return JournalpostMetadata(
            journalpostId = journalpostId,
            bruker = Bruker(
                journalpost.bruker?.id,
                journalpost.bruker?.type,
            ),
            erJournalfort = journalpost.journalstatus != Journalstatus.MOTTATT,
            datoOpprettet = journalpost.datoOpprettet?.let { dato ->
                Either.catch { LocalDateTime.parse(dato) }.getOrNull()
            },
            brevkode = finnBrevkodeForPdf(journalpost.dokumenter),
            tittel = journalpost.tittel,
        ).right()
    }

    /** Brevkoden hentes fra dokumentene som har en arkivvariant (PDF); journalposter uten PDF gir null. */
    private fun finnBrevkodeForPdf(dokumentListe: List<Dokument>?): String? {
        val dokumenter = dokumentListe?.filter {
            it.dokumentvarianter.any { variant -> variant.variantformat == Variantformat.ARKIV }
        }
        if (dokumenter.isNullOrEmpty()) {
            return null
        }
        return dokumenter.firstOrNull { it.brevkode != null }?.brevkode
    }
}

data class FindJournalpostRequest(val query: String, val variables: Variables)

data class Variables(val id: String)

data class FindJournalpostResponse(
    val journalpost: Journalpost?,
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
