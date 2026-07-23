package no.nav.tiltakspenger.journalposthendelser.journalpost.http.saf

import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata

/**
 * Feil ved henting av journalpost fra SAF.
 * Alle variantene er fatale for behandlingen av journalposthendelsen: consumeren kaster og lar Kafka-retryen prøve på nytt.
 */
sealed interface KanIkkeHenteJournalpost {
    /** Selve HTTP-kallet feilet (transport, timeout eller uventet status). */
    data class KallFeilet(val httpKlientError: HttpKlientError) : KanIkkeHenteJournalpost

    /**
     * SAF svarte 200 OK, men med funksjonelle feil i errors-lista.
     * GraphQL svarer av design 200 OK på alle svar; feilkodene ligger i `extensions.code`.
     */
    data class GraphQLFeil(
        val feilkoder: List<String>,
        val httpKlientMetadata: HttpKlientMetadata,
    ) : KanIkkeHenteJournalpost

    /** SAF svarte uten feil, men journalposten mangler journalstatus — vi kan ikke avgjøre om den er journalført. */
    data class UfullstendigJournalpost(val httpKlientMetadata: HttpKlientMetadata) : KanIkkeHenteJournalpost
}
