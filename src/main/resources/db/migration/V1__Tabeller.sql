DO
$$
    BEGIN
        IF
            EXISTS
                    (SELECT 1 from pg_roles where rolname = 'cloudsqliamuser')
        THEN
            GRANT USAGE ON SCHEMA public TO cloudsqliamuser;
            GRANT
                SELECT
                ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
            ALTER
                DEFAULT PRIVILEGES IN SCHEMA public GRANT
                SELECT
                ON TABLES TO cloudsqliamuser;
        END IF;
    END
$$;

CREATE TABLE journalposthendelse
(
    journalpost_id                  VARCHAR PRIMARY KEY,
    fnr                             VARCHAR,
    saksnummer                      VARCHAR,
    brevkode                        VARCHAR,
    journalpost_oppdatert_tidspunkt timestamp with time zone,
    oppgave_id                      VARCHAR,
    oppgavetype                     VARCHAR,
    oppgave_opprettet_tidspunkt     timestamp with time zone,
    opprettet                       timestamp with time zone default CURRENT_TIMESTAMP not null,
    sist_endret                     timestamp with time zone default CURRENT_TIMESTAMP not null
);
