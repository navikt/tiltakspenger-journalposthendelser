package no.nav.tiltakspenger.journalposthendelser.context

import no.nav.tiltakspenger.journalposthendelser.Configuration
import no.nav.tiltakspenger.journalposthendelser.azure.v2.AzureAdV2Cache
import no.nav.tiltakspenger.journalposthendelser.azure.v2.AzureAdV2Client
import no.nav.tiltakspenger.journalposthendelser.consumer.JournalposthendelseConsumer
import no.nav.tiltakspenger.journalposthendelser.infra.httpClientApache
import no.nav.tiltakspenger.journalposthendelser.saf.SafJournalpostClient

open class ApplicationContext {
    open val journalposthendelseConsumer by lazy {
        JournalposthendelseConsumer(
            topic = Configuration.topic,
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

    open val azureAdV2Client by lazy {
        AzureAdV2Client(
            httpClient = httpClientApache(60),
            azureAdV2Cache = AzureAdV2Cache(),
            azureAppClientId = Configuration.clientIdV2,
            azureAppClientSecret = Configuration.clientSecretV2,
            azureTokenEndpoint = Configuration.aadAccessTokenV2Url,
        )
    }
}
