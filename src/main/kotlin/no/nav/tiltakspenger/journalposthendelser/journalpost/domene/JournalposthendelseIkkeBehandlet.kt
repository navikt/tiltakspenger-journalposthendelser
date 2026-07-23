package no.nav.tiltakspenger.journalposthendelser.journalpost.domene

/**
 * Behandlingen av en journalposthendelse feilet i et av de utgående kallene.
 * Feilen er allerede logget én gang der den oppsto (i servicen som hadde konteksten); consumeren kaster på denne slik at Kafka-retryen prøver hendelsen på nytt.
 */
data object JournalposthendelseIkkeBehandlet
