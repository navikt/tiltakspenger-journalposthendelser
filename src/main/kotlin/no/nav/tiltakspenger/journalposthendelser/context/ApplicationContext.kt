package no.nav.tiltakspenger.journalposthendelser.context

import no.nav.tiltakspenger.journalposthendelser.Configuration
import no.nav.tiltakspenger.journalposthendelser.infra.httpClientApache
import no.nav.tiltakspenger.journalposthendelser.journalpost.JournalpostService
import no.nav.tiltakspenger.journalposthendelser.journalpost.JournalposthendelseConsumer
import no.nav.tiltakspenger.journalposthendelser.journalpost.SafJournalpostClient
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import no.nav.tiltakspenger.libs.texas.client.TexasClient
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient

open class ApplicationContext {
    val texasClient: TexasClient = TexasHttpClient(
        introspectionUrl = Configuration.naisTokenIntrospectionEndpoint,
        tokenUrl = Configuration.naisTokenEndpoint,
        tokenExchangeUrl = Configuration.tokenExchangeEndpoint,
    )

    open val safJournalpostClient by lazy {
        SafJournalpostClient(
            basePath = Configuration.safUrl,
            httpClient = httpClientApache(60),
            getToken = { texasClient.getSystemToken(Configuration.safScope, IdentityProvider.AZUREAD, rewriteAudienceTarget = false) },
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
