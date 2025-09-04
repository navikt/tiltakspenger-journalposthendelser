package no.nav.tiltakspenger.journalposthendelser.context

import no.nav.tiltakspenger.journalposthendelser.Configuration
import no.nav.tiltakspenger.journalposthendelser.consumer.JournalposthendelseConsumer

open class ApplicationContext {
    open val journalposthendelseConsumer by lazy {
        JournalposthendelseConsumer(
            topic = Configuration.topic,
        )
    }
}
