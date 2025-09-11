package no.nav.tiltakspenger.journalposthendelser.journalpost

/**
 * Dokumentkoder for brevtyper vi mottar, se under IND:
 * https://kodeverk.ansatt.nav.no/hierarki/TemaSkjema
 */
enum class Brevkode(
    val brevkode: String,
) {
    // https://kodeverk.ansatt.nav.no/kodeverk/NAVSkjema/25310
    SØKNAD("NAV 76-13.45"),

    // https://kodeverk.ansatt.nav.no/kodeverk/NAVSkjema/30310
    KLAGE("NAV 90-00.08 K"),

    // https://kodeverk.ansatt.nav.no/kodeverk/NAVSkjema/30309
    ANKE("NAV 90-00.08 A"),

    // https://kodeverk.ansatt.nav.no/kodeverk/NAVSkjema/36050
    MELDEKORT("NAV 00-10.02"),
}
