package no.nav.tiltakspenger.journalposthendelser.testutils

import no.nav.tiltakspenger.libs.common.TikkendeKlokke
import no.nav.tiltakspenger.libs.persistering.domene.SessionFactory
import no.nav.tiltakspenger.libs.persistering.test.common.TestDatabaseConfig
import java.time.Clock
import no.nav.tiltakspenger.libs.persistering.test.common.TestDatabaseManager as LibsTestDatabaseManager

internal class TestDatabaseManager(
    config: TestDatabaseConfig = TestDatabaseConfig(),
) {
    private val delegate = LibsTestDatabaseManager(
        config = config,
        idGeneratorsFactory = { },
    )

    val sessionFactory: SessionFactory get() = delegate.sessionFactory

    /**
     * @param runIsolated Tømmer databasen før denne testen for kjøre i isolasjon. Brukes når man gjør operasjoner på tvers av saker.
     */
    fun withMigratedDbTestDataHelper(
        runIsolated: Boolean = false,
        clock: Clock = TikkendeKlokke(),
        test: (TestDataHelper) -> Unit,
    ) {
        delegate.withMigratedDb(runIsolated = runIsolated, clock = clock) { _, _, _ ->
            test(TestDataHelper(delegate.dataSource(runIsolated), clock))
        }
    }

    fun withMigratedDb(
        runIsolated: Boolean = false,
        clock: Clock = TikkendeKlokke(),
        test: (SessionFactory, Clock) -> Unit,
    ) {
        delegate.withMigratedDb(runIsolated = runIsolated, clock = clock) { sessionFactory, _, clk ->
            test(sessionFactory, clk)
        }
    }
}
