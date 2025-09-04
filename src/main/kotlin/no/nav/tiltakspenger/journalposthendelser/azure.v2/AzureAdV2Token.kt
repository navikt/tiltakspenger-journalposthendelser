package no.nav.tiltakspenger.journalposthendelser.azure.v2

import java.time.OffsetDateTime

data class AzureAdV2Token(
    val accessToken: String,
    val expires: OffsetDateTime,
)
