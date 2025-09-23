-- Gir tilgang til eksisterende tabeller i 'public' schema til cloudsqliamuser hvis rollen finnes

DO
$$
    BEGIN
        IF (SELECT exists(SELECT rolname FROM pg_roles WHERE rolname = 'cloudsqliamuser'))
        THEN
            grant all on all tables in schema public to cloudsqliamuser;
        END IF;
    END
$$;