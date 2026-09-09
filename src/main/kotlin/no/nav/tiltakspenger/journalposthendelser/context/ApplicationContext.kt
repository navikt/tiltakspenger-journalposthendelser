package no.nav.tiltakspenger.journalposthendelser.context

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.tiltakspenger.journalposthendelser.Configuration
import no.nav.tiltakspenger.journalposthendelser.infra.db.DataSourceSetup
import no.nav.tiltakspenger.journalposthendelser.journalpost.JournalpostService
import no.nav.tiltakspenger.journalposthendelser.journalpost.JournalposthendelseService
import no.nav.tiltakspenger.journalposthendelser.journalpost.OppgaveService
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.dokarkiv.DokarkivClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.oppgave.OppgaveClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.pdl.PdlClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saf.SafJournalpostClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saksbehandlingapi.SaksbehandlingApiClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.kafka.JournalposthendelseConsumer
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseRepo
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.libs.logging.infra.KotlinLoggingSikkerlogg
import no.nav.tiltakspenger.libs.persistering.infrastruktur.PostgresSessionFactory
import no.nav.tiltakspenger.libs.persistering.infrastruktur.SessionCounter
import no.nav.tiltakspenger.libs.texas.client.TexasClient
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.libs.texas.client.TexasSystemTokenProvider
import java.time.Clock

open class ApplicationContext(
    clock: Clock,
    /**
     * Registeret Kafka-consumeren og Ktor fører målingene sine i, og som `/metrics` skraper.
     * Injiseres fra komposisjonsroten slik at appen har nøyaktig ett register, og slik at målingene havner i det samme registeret som blir skrapet.
     * Testene sender inn sitt eget register, siden et prosessnavn bare kan registreres én gang per register.
     */
    val meterRegistry: PrometheusMeterRegistry,
) {
    private val log: KLogger = KotlinLogging.logger { }
    val dataSource = DataSourceSetup.createDatasource(Configuration.dbJdbcUrl)
    val sessionCounter = SessionCounter(log)
    val sessionFactory = PostgresSessionFactory(dataSource, sessionCounter)

    val journalposthendelseRepo = JournalposthendelseRepo(sessionFactory)

    val sikkerlogg: Sikkerlogg = KotlinLoggingSikkerlogg(
        appNavn = Configuration.naisAppName,
        gcpProsjektId = Configuration.gcpTeamProjectId,
    )

    val texasClient: TexasClient = TexasHttpClient(
        introspectionUrl = Configuration.tokenIntrospectionEndpoint,
        tokenUrl = Configuration.tokenEndpoint,
        tokenExchangeUrl = Configuration.tokenExchangeEndpoint,
        clock = clock,
    )

    private fun systemTokenProvider(scope: String) = TexasSystemTokenProvider(
        texasClient = texasClient,
        audienceTarget = scope,
    )

    val safJournalpostClient = SafJournalpostClient(
        baseUrl = Configuration.safUrl,
        clock = clock,
        authTokenProvider = systemTokenProvider(Configuration.safScope),
    )
    val pdlClient = PdlClient(
        baseUrl = Configuration.pdlUrl,
        clock = clock,
        authTokenProvider = systemTokenProvider(Configuration.pdlScope),
    )
    val saksbehandlingApiClient = SaksbehandlingApiClient(
        baseUrl = Configuration.saksbehandlingApiUrl,
        clock = clock,
        authTokenProvider = systemTokenProvider(Configuration.saksbehandlingApiScope),
    )
    val oppgaveClient = OppgaveClient(
        baseUrl = Configuration.oppgaveUrl,
        clock = clock,
        authTokenProvider = systemTokenProvider(Configuration.oppgaveScope),
    )
    val dokarkivClient = DokarkivClient(
        baseUrl = Configuration.dokarkivUrl,
        clock = clock,
        authTokenProvider = systemTokenProvider(Configuration.dokarkivScope),
    )

    val journalpostService = JournalpostService(
        saksbehandlingApiClient = saksbehandlingApiClient,
        dokarkivClient = dokarkivClient,
        journalposthendelseRepo = journalposthendelseRepo,
        clock = clock,
        sikkerlogg = sikkerlogg,
    )
    val oppgaveService = OppgaveService(
        oppgaveClient = oppgaveClient,
        journalposthendelseRepo = journalposthendelseRepo,
        clock = clock,
        sikkerlogg = sikkerlogg,
    )

    val journalposthendelseService = JournalposthendelseService(
        safJournalpostClient = safJournalpostClient,
        journalposthendelseRepo = journalposthendelseRepo,
        pdlClient = pdlClient,
        journalpostService = journalpostService,
        oppgaveService = oppgaveService,
        clock = clock,
        sikkerlogg = sikkerlogg,
    )

    val journalposthendelseConsumer = JournalposthendelseConsumer(
        topic = Configuration.topic,
        journalposthendelseService = journalposthendelseService,
        clock = clock,
        meterRegistry = meterRegistry,
    )
}
