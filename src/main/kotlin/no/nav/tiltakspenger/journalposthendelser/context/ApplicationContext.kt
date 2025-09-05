package no.nav.tiltakspenger.journalposthendelser.context

import no.nav.tiltakspenger.journalposthendelser.Configuration
import no.nav.tiltakspenger.journalposthendelser.azure.v2.AzureAdV2Cache
import no.nav.tiltakspenger.journalposthendelser.azure.v2.AzureAdV2Client
import no.nav.tiltakspenger.journalposthendelser.infra.httpClientApache
import no.nav.tiltakspenger.journalposthendelser.journalpost.JournalpostService
import no.nav.tiltakspenger.journalposthendelser.journalpost.JournalposthendelseConsumer
import no.nav.tiltakspenger.journalposthendelser.journalpost.SafJournalpostClient

open class ApplicationContext {
    open val azureAdV2Client by lazy {
        AzureAdV2Client(
            httpClient = httpClientApache(60),
            azureAdV2Cache = AzureAdV2Cache(),
            azureAppClientId = Configuration.clientIdV2,
            azureAppClientSecret = Configuration.clientSecretV2,
            azureTokenEndpoint = Configuration.aadAccessTokenV2Url,
        )
    }

    open val safJournalpostClient by lazy {
        SafJournalpostClient(
            basePath = Configuration.safUrl,
            scope = Configuration.safScope,
            httpClient = httpClientApache(60),
            accessTokenClientV2 = azureAdV2Client,
        )
    }

    open val journalpostService by lazy {
        JournalpostService(
            safJournalpostClient = safJournalpostClient,
        )
    }

    open val journalposthendelseConsumer by lazy {
        JournalposthendelseConsumer(
            topic = Configuration.topic,
            journalpostService = journalpostService,
        )
    }
}
