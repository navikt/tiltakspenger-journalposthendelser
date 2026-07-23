package no.nav.tiltakspenger.journalposthendelser

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ConfigurationTest {
    @Test
    fun `lokal profil gir tomme miljovariabler og lokale defaults`() {
        Configuration.applicationProfile() shouldBe Profile.LOCAL
        Configuration.isNais() shouldBe false
        Configuration.isProd() shouldBe false

        Configuration.applicationHttpPort shouldBe 8080
        Configuration.logbackConfigFile shouldBe "logback.xml"
        Configuration.topic shouldBe "teamdokumenthandtering.aapen-dok-journalfoering"

        Configuration.safUrl shouldBe ""
        Configuration.safScope shouldBe ""
        Configuration.saksbehandlingApiUrl shouldBe ""
        Configuration.saksbehandlingApiScope shouldBe ""
        Configuration.oppgaveUrl shouldBe ""
        Configuration.oppgaveScope shouldBe ""
        Configuration.dokarkivUrl shouldBe ""
        Configuration.dokarkivScope shouldBe ""
        Configuration.pdlUrl shouldBe ""
        Configuration.pdlScope shouldBe ""
        Configuration.naisTokenIntrospectionEndpoint shouldBe ""
        Configuration.naisTokenEndpoint shouldBe ""
        Configuration.tokenExchangeEndpoint shouldBe ""
        Configuration.jdbcUrl shouldBe ""
        Configuration.electorPath() shouldBe ""

        Configuration.naisAppName shouldBe null
        Configuration.gcpTeamProjectId shouldBe null
    }

    @Test
    fun `profilen styres av NAIS_CLUSTER_NAME og pakrevde miljovariabler kastes det pa i Nais`() {
        // Tving frem objekt-initialisering i LOCAL-profil først; med Nais-profil ville initialiseringen feilet på manglende miljøvariabler.
        Configuration.applicationProfile() shouldBe Profile.LOCAL
        try {
            System.setProperty("NAIS_CLUSTER_NAME", "prod-gcp")
            Configuration.applicationProfile() shouldBe Profile.PROD
            Configuration.isNais() shouldBe true
            Configuration.isProd() shouldBe true
            shouldThrow<RuntimeException> { Configuration.electorPath() }

            System.setProperty("NAIS_CLUSTER_NAME", "dev-gcp")
            Configuration.applicationProfile() shouldBe Profile.DEV
            Configuration.isProd() shouldBe false
        } finally {
            System.clearProperty("NAIS_CLUSTER_NAME")
        }
    }
}
