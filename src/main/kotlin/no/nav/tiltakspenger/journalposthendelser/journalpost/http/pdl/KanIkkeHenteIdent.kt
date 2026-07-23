package no.nav.tiltakspenger.journalposthendelser.journalpost.http.pdl

import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata

/**
 * Feil ved henting av gjeldende ident fra PDL.
 * «Fant ikke person» er ikke en feil, men et domeneutfall (fordelingsoppgave) — den modelleres som `Right(null)` fra klienten.
 */
sealed interface KanIkkeHenteIdent {
    /** Selve HTTP-kallet feilet (transport, timeout eller uventet status). */
    data class KallFeilet(val httpKlientError: HttpKlientError) : KanIkkeHenteIdent

    /**
     * PDL svarte 200 OK, men med funksjonelle feil i errors-lista som ikke betyr «fant ikke person».
     * GraphQL svarer av design 200 OK på alle svar; feilkodene ligger i `extensions.code`.
     */
    data class GraphQLFeil(
        val feilkoder: List<String>,
        val httpKlientMetadata: HttpKlientMetadata,
    ) : KanIkkeHenteIdent
}
