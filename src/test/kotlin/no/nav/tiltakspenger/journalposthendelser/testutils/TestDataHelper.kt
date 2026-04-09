package no.nav.tiltakspenger.journalposthendelser.testutils

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.journalposthendelser.journalpost.repository.JournalposthendelseRepo
import no.nav.tiltakspenger.libs.common.TikkendeKlokke
import no.nav.tiltakspenger.libs.persistering.infrastruktur.PostgresSessionFactory
import no.nav.tiltakspenger.libs.persistering.infrastruktur.SessionCounter
import java.time.Clock
import javax.sql.DataSource

internal class TestDataHelper(
    dataSource: DataSource,
    val clock: Clock,
) {
    private val log = KotlinLogging.logger {}
    private val sessionCounter = SessionCounter(log)
    val sessionFactory = PostgresSessionFactory(dataSource, sessionCounter)
    val journalposthendelseRepo = JournalposthendelseRepo(sessionFactory)
}

private val dbManager = TestDatabaseManager()

/**
 * @param runIsolated Tømmer databasen før denne testen for kjøre i isolasjon. Brukes når man gjør operasjoner på tvers av saker.
 */
internal fun withMigratedDb(
    runIsolated: Boolean = true,
    clock: Clock = TikkendeKlokke(),
    test: (TestDataHelper) -> Unit,
) {
    dbManager.withMigratedDbTestDataHelper(runIsolated = runIsolated, test = test, clock = clock)
}
