package no.nav.tiltakspenger.journalposthendelser.infra

import io.prometheus.metrics.core.metrics.Counter

const val METRICS_NS = "tpts_jphendelser"

object MetricRegister {
    val JOURNALPOSTHENDELSE_MOTTATT: Counter = Counter.builder()
        .name("${METRICS_NS}_journalposthendelser_count")
        .help("Antall mottatte journalposthendelser")
        .withoutExemplars()
        .register()

    val SØKNAD_MOTTATT: Counter = Counter.builder()
        .name("${METRICS_NS}_papirsøknad_count")
        .help("Antall papirsøknader")
        .withoutExemplars()
        .register()

    val KLAGE_MOTTATT: Counter = Counter.builder()
        .name("${METRICS_NS}_klager_count")
        .help("Antall klagesaker")
        .withoutExemplars()
        .register()

    val ANNEN_BREVKODE_MOTTATT: Counter = Counter.builder()
        .name("${METRICS_NS}_annen_brevkode_count")
        .help("Antall mottatte journalposterhendelser som ikke skal håndteres per nå")
        .withoutExemplars()
        .register()
}
