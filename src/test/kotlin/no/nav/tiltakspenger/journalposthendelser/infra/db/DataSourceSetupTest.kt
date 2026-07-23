package no.nav.tiltakspenger.journalposthendelser.infra.db

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.journalposthendelser.Configuration
import no.nav.tiltakspenger.journalposthendelser.Profile
import org.junit.jupiter.api.Test
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource

class DataSourceSetupTest {
    @Test
    fun `oppretter datasource og migrerer med bade gcp- og lokal-oppsettet`() {
        PostgreSQLContainer("postgres:17-alpine").use { postgres ->
            postgres.start()
            val skilletegn = if (postgres.jdbcUrl.contains('?')) "&" else "?"
            val jdbcUrl = "${postgres.jdbcUrl}${skilletegn}user=${postgres.username}&password=${postgres.password}"

            // Tving frem Configuration-init i LOCAL-profil før vi later som vi er på Nais; med Nais-profil ville initialiseringen feilet på manglende miljøvariabler.
            Configuration.applicationProfile() shouldBe Profile.LOCAL
            try {
                System.setProperty("NAIS_CLUSTER_NAME", "dev-gcp")
                DataSourceSetup.createDatasource(jdbcUrl).use { dataSource ->
                    antallKjørteMigreringer(dataSource) shouldBeGreaterThan 0

                    // Lokal-varianten mot samme database; migreringene er alt kjørt, så dette skal være en no-op.
                    System.clearProperty("NAIS_CLUSTER_NAME")
                    flywayMigrate(dataSource)
                }
            } finally {
                System.clearProperty("NAIS_CLUSTER_NAME")
            }
        }
    }

    private fun antallKjørteMigreringer(dataSource: DataSource): Int =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from flyway_schema_history where success").use { resultSet ->
                    resultSet.next()
                    resultSet.getInt(1)
                }
            }
        }
}
