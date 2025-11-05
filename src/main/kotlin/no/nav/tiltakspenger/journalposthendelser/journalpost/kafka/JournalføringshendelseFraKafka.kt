package no.nav.tiltakspenger.journalposthendelser.journalpost.kafka

import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord

/**
 * https://confluence.adeo.no/spaces/BOA/pages/432217891/Joarkhendelser
 *
 * @param hendelsesId JournalpostId + tidspunkt hendinga skjedde i Joark-db med bindestrek mellom, t.d. 552206794-2022-03-10T09:51:48
 * @param hendelsesType En av: JournalpostMottatt, TemaEndret, EndeligJournalført, JournalpostUtgått
 * @param journalpostStatus En av:
 *   - MOTTATT: Dokumenta på journalposten er registrert i arkivet, men ikkje journalført/ferdigstilt enno.
 *   - JOURNALFOERT	Journalposten er ferdigstilt, og ansvaret for vidare handsaming er overført til fagsystemet. Journalen er låst for vidare endringar.
 *   - UKJENT_BRUKER Journalposten er tatt ut av sakshandsaming fordi det ikkje er mogleg å identifisere kven journalposten gjeld.
 *   - UTGAAR Journalposten er tatt ut av sakshandsaming grunna feil i samband med mottak eller journalføring.
 *   - OPPLASTING_DOKUMENT Journalposten er i ein midlertidig status på veg mot MOTTATT. NB: Statusen blir kun brukt på dagpengeområdet, og skal bli fasa ut.
 * @param temaGammelt Ved endring av tema vil denne innehalde før-verdi av tema (K_FAGOMRADE-kolonna i journalpost-tabellen).
 * @param temaNytt Gjeldande verdi for tema på journalposten.
 * @param mottaksKanal f.eks. NAV_NO
 * @param kanalReferanseId For mange innkomande dokument i arkivet blir det lagra ein globalt unik referanseId som kan nyttast for sporing tilbake til kanalmottaket.
 * @param behandlingstema Underkategori av tema - blir brukt for spesielle fagområder med særskilde behov for kategorisering.
 */
data class JournalføringshendelseFraKafka(
    val hendelsesId: String?,
    val versjon: Int,
    val hendelsesType: String?,
    val journalpostId: String,
    val journalpostStatus: String?,
    val temaGammelt: String?,
    val temaNytt: String?,
    val mottaksKanal: String?,
    val kanalReferanseId: String?,
    val behandlingstema: String?,
) {
    val erHendelsestypeJournalpostMottatt: Boolean by lazy { hendelsesType == "JournalpostMottatt" }
    val erHendelsestypeTemaEndret: Boolean by lazy { hendelsesType == "TemaEndret" }
    val erTemaIND: Boolean by lazy { temaNytt == "IND" }
    val skalBehandles: Boolean by lazy {
        (erHendelsestypeJournalpostMottatt || erHendelsestypeTemaEndret) && erTemaIND
    }
    val erJournalført: Boolean by lazy { journalpostStatus != "MOTTATT" }
}

fun JournalfoeringHendelseRecord.toJournalføringshendelseFraKafka(): JournalføringshendelseFraKafka =
    JournalføringshendelseFraKafka(
        hendelsesId = this.hendelsesId,
        versjon = this.versjon,
        hendelsesType = this.hendelsesType,
        journalpostId = this.journalpostId.toString(),
        journalpostStatus = this.journalpostStatus,
        temaGammelt = this.temaGammelt,
        temaNytt = this.temaNytt,
        mottaksKanal = this.mottaksKanal,
        kanalReferanseId = this.kanalReferanseId,
        behandlingstema = this.behandlingstema,
    )
