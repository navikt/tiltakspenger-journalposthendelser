package no.nav.tiltakspenger.journalposthendelser.journalpost.repository

import kotliquery.Row
import kotliquery.queryOf
import no.nav.tiltakspenger.libs.persistering.infrastruktur.PostgresSessionFactory

class JournalposthendelseRepo(
    private val sessionFactory: PostgresSessionFactory,
) {
    fun hent(
        journalpostId: String,
    ): JournalposthendelseDB? {
        return sessionFactory.withSession { session ->
            session.run(
                queryOf(
                    //language=sql
                    """
                    select *
                    from journalposthendelse
                    where journalpost_id = :journalpost_id;
                    """.trimIndent(),
                    mapOf(
                        "journalpost_id" to journalpostId,
                    ),
                ).map { fromRow(it) }.asSingle,
            )
        }
    }

    fun lagre(
        journalposthendelseDB: JournalposthendelseDB,
    ) {
        sessionFactory.withSession { session ->
            session.run(
                queryOf(
                    lagreJournalposthendelseSql,
                    mapOf(
                        "journalpost_id" to journalposthendelseDB.journalpostId,
                        "fnr" to journalposthendelseDB.fnr,
                        "saksnummer" to journalposthendelseDB.saksnummer,
                        "brevkode" to journalposthendelseDB.brevkode,
                        "journalpost_oppdatert_tidspunkt" to journalposthendelseDB.journalpostOppdatertTidspunkt,
                        "oppgave_id" to journalposthendelseDB.oppgaveId,
                        "oppgavetype" to journalposthendelseDB.oppgavetype,
                        "oppgave_opprettet_tidspunkt" to journalposthendelseDB.oppgaveOpprettetTidspunkt,
                        "opprettet" to journalposthendelseDB.opprettet,
                        "sist_endret" to journalposthendelseDB.sistEndret,
                    ),
                ).asUpdate,
            )
        }
    }

    private fun fromRow(row: Row): JournalposthendelseDB {
        return JournalposthendelseDB(
            journalpostId = (row.string("journalpost_id")),
            fnr = row.stringOrNull("fnr"),
            saksnummer = row.stringOrNull("saksnummer"),
            brevkode = row.stringOrNull("brevkode"),
            journalpostOppdatertTidspunkt = row.localDateTimeOrNull("journalpost_oppdatert_tidspunkt"),
            oppgaveId = row.stringOrNull("oppgave_id"),
            oppgavetype = row.stringOrNull("oppgavetype"),
            oppgaveOpprettetTidspunkt = row.localDateTimeOrNull("oppgave_opprettet_tidspunkt"),
            opprettet = row.localDateTime("opprettet"),
            sistEndret = row.localDateTime("sist_endret"),
        )
    }

    private val lagreJournalposthendelseSql =
        """
        insert into journalposthendelse (
        journalpost_id,
        fnr,
        saksnummer,
        brevkode,
        journalpost_oppdatert_tidspunkt,
        oppgave_id,
        oppgavetype,
        oppgave_opprettet_tidspunkt,
        opprettet,
        sist_endret
        ) values (
        :journalpost_id,
        :fnr,
        :saksnummer,
        :brevkode,
        :journalpost_oppdatert_tidspunkt,
        :oppgave_id,
        :oppgavetype,
        :oppgave_opprettet_tidspunkt,
        :opprettet,
        :sist_endret
        ) on conflict (journalpost_id) do update set
        fnr = :fnr,
        saksnummer = :saksnummer,
        brevkode = :brevkode,
        journalpost_oppdatert_tidspunkt = :journalpost_oppdatert_tidspunkt,
        oppgave_id = :oppgave_id,
        oppgavetype = :oppgavetype,
        oppgave_opprettet_tidspunkt = :oppgave_opprettet_tidspunkt,
        sist_endret = :sist_endret
        """.trimIndent()
}
