package no.nav.tiltakspenger.journalposthendelser.infra.db

import io.kotest.matchers.ints.shouldBeGreaterThan
import org.junit.jupiter.api.Test
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource

class DataSourceSetupTest {
    @Test
    fun `oppretter datasource og kjører Flyway-migreringene`() {
        // Kjører i LOCAL-profil (ingen NAIS_CLUSTER_NAME satt) — ingen mutasjon av global system-env.
        // Flyway ignorerer den manglende db/local-migration-lokasjonen og bruker db/migration.
        PostgreSQLContainer("postgres:17-alpine").use { postgres ->
            postgres.start()
            val skilletegn = if (postgres.jdbcUrl.contains('?')) "&" else "?"
            val jdbcUrl = "${postgres.jdbcUrl}${skilletegn}user=${postgres.username}&password=${postgres.password}"

            DataSourceSetup.createDatasource(jdbcUrl).use { dataSource ->
                antallKjørteMigreringer(dataSource) shouldBeGreaterThan 0
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
