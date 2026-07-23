package no.nav.tiltakspenger.journalposthendelser.journalpost

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.journalposthendelser.journalpost.domene.JournalposthendelseIkkeBehandlet
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.dokarkiv.DokarkivClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saksbehandlingapi.SaksbehandlingApiClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseDB
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseRepo
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.httpklient.loggFeil
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import java.time.Clock

class JournalpostService(
    private val saksbehandlingApiClient: SaksbehandlingApiClient,
    private val dokarkivClient: DokarkivClient,
    private val journalposthendelseRepo: JournalposthendelseRepo,
    private val clock: Clock,
    private val sikkerlogg: Sikkerlogg = Sikkerlogg,
) {
    val log = KotlinLogging.logger {}

    suspend fun oppdaterEllerFerdigstillJournalpost(
        journalposthendelseDB: JournalposthendelseDB,
        correlationId: CorrelationId,
    ): Either<JournalposthendelseIkkeBehandlet, JournalposthendelseDB> {
        return oppdaterJournalpost(journalposthendelseDB, correlationId).flatMap { oppdatert ->
            if (journalposthendelseDB.gjelderPapirsoknad()) {
                ferdigstillJournalpost(oppdatert, correlationId)
            } else {
                oppdatert.right()
            }
        }
    }

    private suspend fun oppdaterJournalpost(
        journalposthendelseDB: JournalposthendelseDB,
        correlationId: CorrelationId,
    ): Either<JournalposthendelseIkkeBehandlet, JournalposthendelseDB> {
        if (!journalposthendelseDB.harOppdatertJournalpost() && journalposthendelseDB.kanOppdatereJournalpost()) {
            val journalpostId = journalposthendelseDB.journalpostId
            return saksbehandlingApiClient.hentEllerOpprettSaksnummer(journalposthendelseDB.fnr!!, correlationId)
                .mapLeft { feil ->
                    feil.loggFeil(log, "henting av saksnummer fra saksbehandling-api", "journalpostId: $journalpostId, correlationId: ${correlationId.value}", sikkerlogg)
                    JournalposthendelseIkkeBehandlet
                }.flatMap { saksnummer ->
                    dokarkivClient.knyttSakTilJournalpost(
                        journalpostId = journalpostId,
                        saksnummer = saksnummer,
                        fnr = journalposthendelseDB.fnr,
                        gjelderPapirsoknad = journalposthendelseDB.gjelderPapirsoknad(),
                    ).mapLeft { feil ->
                        feil.loggFeil(log, "oppdatering av journalpost i dokarkiv", "journalpostId: $journalpostId, correlationId: ${correlationId.value}", sikkerlogg)
                        JournalposthendelseIkkeBehandlet
                    }.map { saksnummer }
                }.map { saksnummer ->
                    val nå = nå(clock)
                    val journalposthendelseDBOppdatertJP = journalposthendelseDB.copy(
                        saksnummer = saksnummer,
                        journalpostOppdatertTidspunkt = nå,
                        sistEndret = nå,
                    )
                    journalposthendelseRepo.lagre(journalposthendelseDBOppdatertJP)
                    log.info { "Oppdaterte journalpost med id $journalpostId" }
                    journalposthendelseDBOppdatertJP
                }
        }
        log.info { "Journalpost med id ${journalposthendelseDB.journalpostId} er allerede oppdatert" }
        return journalposthendelseDB.right()
    }

    private suspend fun ferdigstillJournalpost(
        journalposthendelseDB: JournalposthendelseDB,
        correlationId: CorrelationId,
    ): Either<JournalposthendelseIkkeBehandlet, JournalposthendelseDB> {
        if (!journalposthendelseDB.harFerdigstiltJournalpost() && journalposthendelseDB.harOppdatertJournalpost()) {
            val journalpostId = journalposthendelseDB.journalpostId
            return dokarkivClient.ferdigstillJournalpost(journalpostId)
                .mapLeft { feil ->
                    feil.loggFeil(log, "ferdigstilling av journalpost i dokarkiv", "journalpostId: $journalpostId, correlationId: ${correlationId.value}", sikkerlogg)
                    JournalposthendelseIkkeBehandlet
                }.map {
                    val nå = nå(clock)
                    val journalposthendelseDBFerdigstiltJP = journalposthendelseDB.copy(
                        journalpostFerdigstiltTidspunkt = nå,
                        sistEndret = nå,
                    )
                    journalposthendelseRepo.lagre(journalposthendelseDBFerdigstiltJP)
                    log.info { "Ferdigstilte journalpost med id $journalpostId" }
                    journalposthendelseDBFerdigstiltJP
                }
        }
        log.info { "Journalpost med id ${journalposthendelseDB.journalpostId} er allerede ferdigstilt" }
        return journalposthendelseDB.right()
    }
}
