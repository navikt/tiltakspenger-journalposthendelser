package no.nav.tiltakspenger.journalposthendelser.journalpost

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.dokarkiv.DokarkivClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.http.saksbehandlingapi.SaksbehandlingApiClient
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseDB
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseRepo
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.nå
import java.time.Clock

class JournalpostService(
    private val saksbehandlingApiClient: SaksbehandlingApiClient,
    private val dokarkivClient: DokarkivClient,
    private val journalposthendelseRepo: JournalposthendelseRepo,
    private val clock: Clock,
) {
    val log = KotlinLogging.logger {}

    suspend fun oppdaterEllerFerdigstillJournalpost(
        journalposthendelseDB: JournalposthendelseDB,
        correlationId: CorrelationId,
    ): JournalposthendelseDB {
        val journalposthendelseDBOppdatertJP = oppdaterJournalpost(journalposthendelseDB, correlationId)

        if (journalposthendelseDB.gjelderPapirsoknad()) {
            return ferdigstillJournalpost(journalposthendelseDBOppdatertJP, correlationId)
        }
        return journalposthendelseDBOppdatertJP
    }

    private suspend fun oppdaterJournalpost(
        journalposthendelseDB: JournalposthendelseDB,
        correlationId: CorrelationId,
    ): JournalposthendelseDB {
        if (!journalposthendelseDB.harOppdatertJournalpost() && journalposthendelseDB.kanOppdatereJournalpost()) {
            val saksnummer =
                saksbehandlingApiClient.hentEllerOpprettSaksnummer(journalposthendelseDB.fnr!!, correlationId)
            dokarkivClient.knyttSakTilJournalpost(
                journalpostId = journalposthendelseDB.journalpostId,
                saksnummer = saksnummer,
                fnr = journalposthendelseDB.fnr,
                gjelderPapirsoknad = journalposthendelseDB.gjelderPapirsoknad(),
                correlationId = correlationId,
            )
            val nå = nå(clock)
            val journalposthendelseDBOppdatertJP = journalposthendelseDB.copy(
                saksnummer = saksnummer,
                journalpostOppdatertTidspunkt = nå,
                sistEndret = nå,
            )
            journalposthendelseRepo.lagre(journalposthendelseDBOppdatertJP)
            log.info { "Oppdaterte journalpost med id ${journalposthendelseDB.journalpostId}" }
            return journalposthendelseDBOppdatertJP
        }
        log.info { "Journalpost med id ${journalposthendelseDB.journalpostId} er allerede oppdatert" }
        return journalposthendelseDB
    }

    private suspend fun ferdigstillJournalpost(
        journalposthendelseDB: JournalposthendelseDB,
        correlationId: CorrelationId,
    ): JournalposthendelseDB {
        if (!journalposthendelseDB.harFerdigstiltJournalpost() && journalposthendelseDB.harOppdatertJournalpost()) {
            dokarkivClient.ferdigstillJournalpost(
                journalpostId = journalposthendelseDB.journalpostId,
                correlationId = correlationId,
            )
            val nå = nå(clock)
            val journalposthendelseDBFerdigstiltJP = journalposthendelseDB.copy(
                journalpostFerdigstiltTidspunkt = nå,
                sistEndret = nå,
            )
            journalposthendelseRepo.lagre(journalposthendelseDBFerdigstiltJP)
            log.info { "Ferdigstilte journalpost med id ${journalposthendelseDB.journalpostId}" }
            return journalposthendelseDBFerdigstiltJP
        }
        log.info { "Journalpost med id ${journalposthendelseDB.journalpostId} er allerede ferdigstilt" }
        return journalposthendelseDB
    }
}
