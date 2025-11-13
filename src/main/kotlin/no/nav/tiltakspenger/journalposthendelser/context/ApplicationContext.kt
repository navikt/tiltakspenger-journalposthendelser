package no.nav.tiltakspenger.journalposthendelser.context

import io.github.oshai.kotlinlogging.KLogger
import no.nav.tiltakspenger.journalposthendelser.Configuration
import no.nav.tiltakspenger.journalposthendelser.infra.db.DataSourceSetup
import no.nav.tiltakspenger.journalposthendelser.infra.httpClientApache
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
import no.nav.tiltakspenger.libs.persistering.infrastruktur.PostgresSessionFactory
import no.nav.tiltakspenger.libs.persistering.infrastruktur.SessionCounter
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import no.nav.tiltakspenger.libs.texas.client.TexasClient
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient

open class ApplicationContext(log: KLogger) {
    val dataSource = DataSourceSetup.createDatasource(Configuration.jdbcUrl)
    val sessionCounter = SessionCounter(log)
    val sessionFactory = PostgresSessionFactory(dataSource, sessionCounter)

    val journalposthendelseRepo = JournalposthendelseRepo(sessionFactory)

    val texasClient: TexasClient = TexasHttpClient(
        introspectionUrl = Configuration.naisTokenIntrospectionEndpoint,
        tokenUrl = Configuration.naisTokenEndpoint,
        tokenExchangeUrl = Configuration.tokenExchangeEndpoint,
    )

    val httpClient = httpClientApache()
    val httpClientSlowApis = httpClientApache(
        connectTimeoutSeconds = 10,
        requestTimeoutSeconds = 15,
        socketTimeoutSeconds = 10,
    )
    val safJournalpostClient = SafJournalpostClient(
        basePath = Configuration.safUrl,
        httpClient = httpClientSlowApis,
        getToken = {
            texasClient.getSystemToken(
                Configuration.safScope,
                IdentityProvider.AZUREAD,
                rewriteAudienceTarget = false,
            )
        },
    )
    val pdlClient = PdlClient(
        basePath = Configuration.pdlUrl,
        httpClient = httpClient,
        getToken = {
            texasClient.getSystemToken(
                Configuration.pdlScope,
                IdentityProvider.AZUREAD,
                rewriteAudienceTarget = false,
            )
        },
    )
    val saksbehandlingApiClient = SaksbehandlingApiClient(
        basePath = Configuration.saksbehandlingApiUrl,
        httpClient = httpClient,
        getToken = {
            texasClient.getSystemToken(
                Configuration.saksbehandlingApiScope,
                IdentityProvider.AZUREAD,
                rewriteAudienceTarget = false,
            )
        },
    )
    val oppgaveClient = OppgaveClient(
        basePath = Configuration.oppgaveUrl,
        httpClient = httpClient,
        getToken = {
            texasClient.getSystemToken(
                Configuration.oppgaveScope,
                IdentityProvider.AZUREAD,
                rewriteAudienceTarget = false,
            )
        },
    )
    val dokarkivClient = DokarkivClient(
        basePath = Configuration.dokarkivUrl,
        httpClient = httpClient,
        getToken = {
            texasClient.getSystemToken(
                Configuration.dokarkivScope,
                IdentityProvider.AZUREAD,
                rewriteAudienceTarget = false,
            )
        },
    )

    val journalpostService = JournalpostService(
        saksbehandlingApiClient = saksbehandlingApiClient,
        dokarkivClient = dokarkivClient,
        journalposthendelseRepo = journalposthendelseRepo,
    )
    val oppgaveService = OppgaveService(
        oppgaveClient = oppgaveClient,
        journalposthendelseRepo = journalposthendelseRepo,
    )

    val journalposthendelseService = JournalposthendelseService(
        safJournalpostClient = safJournalpostClient,
        journalposthendelseRepo = journalposthendelseRepo,
        pdlClient = pdlClient,
        journalpostService = journalpostService,
        oppgaveService = oppgaveService,
    )

    val journalposthendelseConsumer = JournalposthendelseConsumer(
        topic = Configuration.topic,
        journalposthendelseService = journalposthendelseService,
    )
}
