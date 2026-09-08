package no.nav.tiltakspenger.journalposthendelser

enum class Profile {
    LOCAL,
    DEV,
    PROD,
}

sealed interface EnvironmentConfig {
    val profile: Profile
    val httpPort: Int
    val logbackConfigurationFile: String

    val dbJdbcUrl: String

    /** Til sikkerlogg-henvisningen; satt av nais i podene, null lokalt (da blir henvisningen ren tekst uten lenke). */
    val naisAppName: String?
    val gcpTeamProjectId: String?

    val tokenEndpoint: String
    val tokenIntrospectionEndpoint: String
    val tokenExchangeEndpoint: String

    val safUrl: String
    val safScope: String

    val saksbehandlingApiUrl: String
    val saksbehandlingApiScope: String

    val oppgaveUrl: String
    val oppgaveScope: String

    val dokarkivUrl: String
    val dokarkivScope: String

    val pdlUrl: String
    val pdlScope: String
}

data object LocalConfig : EnvironmentConfig {
    override val profile = Profile.LOCAL
    override val httpPort = 8080
    override val logbackConfigurationFile = "logback.local.xml"

    // Appen har ingen lokal kjørehistorikk mot eksterne tjenester; url'er og scopes er tomme lokalt.
    override val dbJdbcUrl = ""

    override val naisAppName: String? = null
    override val gcpTeamProjectId: String? = null

    override val tokenEndpoint = ""
    override val tokenIntrospectionEndpoint = ""
    override val tokenExchangeEndpoint = ""

    override val safUrl = ""
    override val safScope = ""

    override val saksbehandlingApiUrl = ""
    override val saksbehandlingApiScope = ""

    override val oppgaveUrl = ""
    override val oppgaveScope = ""

    override val dokarkivUrl = ""
    override val dokarkivScope = ""

    override val pdlUrl = ""
    override val pdlScope = ""
}

data object DevConfig : EnvironmentConfig {
    override val profile = Profile.DEV
    override val httpPort = 8080
    override val logbackConfigurationFile = "logback.xml"

    override val dbJdbcUrl: String = System.getenv("DB_JDBC_URL")

    override val naisAppName: String? = System.getenv("NAIS_APP_NAME")
    override val gcpTeamProjectId: String? = System.getenv("GCP_TEAM_PROJECT_ID")

    override val tokenEndpoint: String = System.getenv("NAIS_TOKEN_ENDPOINT")
    override val tokenIntrospectionEndpoint: String = System.getenv("NAIS_TOKEN_INTROSPECTION_ENDPOINT")
    override val tokenExchangeEndpoint: String = System.getenv("NAIS_TOKEN_EXCHANGE_ENDPOINT")

    override val safUrl = "https://saf-q2.dev-fss-pub.nais.io"
    override val safScope = "api://dev-fss.teamdokumenthandtering.saf/.default"

    override val saksbehandlingApiUrl = "http://tiltakspenger-saksbehandling-api"
    override val saksbehandlingApiScope = "api://dev-gcp.tpts.tiltakspenger-saksbehandling-api/.default"

    override val oppgaveUrl = "https://oppgave.dev-fss-pub.nais.io"
    override val oppgaveScope = "api://dev-fss.oppgavehandtering.oppgave/.default"

    override val dokarkivUrl = "https://dokarkiv-q2.dev-fss-pub.nais.io"
    override val dokarkivScope = "api://dev-fss.teamdokumenthandtering.dokarkiv/.default"

    override val pdlUrl = "https://pdl-api.dev-fss-pub.nais.io"
    override val pdlScope = "api://dev-fss.pdl.pdl-api/.default"
}

data object ProdConfig : EnvironmentConfig {
    override val profile = Profile.PROD
    override val httpPort = 8080
    override val logbackConfigurationFile = "logback.xml"

    override val dbJdbcUrl: String = System.getenv("DB_JDBC_URL")

    override val naisAppName: String? = System.getenv("NAIS_APP_NAME")
    override val gcpTeamProjectId: String? = System.getenv("GCP_TEAM_PROJECT_ID")

    override val tokenEndpoint: String = System.getenv("NAIS_TOKEN_ENDPOINT")
    override val tokenIntrospectionEndpoint: String = System.getenv("NAIS_TOKEN_INTROSPECTION_ENDPOINT")
    override val tokenExchangeEndpoint: String = System.getenv("NAIS_TOKEN_EXCHANGE_ENDPOINT")

    override val safUrl = "https://saf.prod-fss-pub.nais.io"
    override val safScope = "api://prod-fss.teamdokumenthandtering.saf/.default"

    override val saksbehandlingApiUrl = "http://tiltakspenger-saksbehandling-api"
    override val saksbehandlingApiScope = "api://prod-gcp.tpts.tiltakspenger-saksbehandling-api/.default"

    override val oppgaveUrl = "https://oppgave.prod-fss-pub.nais.io"
    override val oppgaveScope = "api://prod-fss.oppgavehandtering.oppgave/.default"

    override val dokarkivUrl = "https://dokarkiv.prod-fss-pub.nais.io"
    override val dokarkivScope = "api://prod-fss.teamdokumenthandtering.dokarkiv/.default"

    override val pdlUrl = "https://pdl-api.prod-fss-pub.nais.io"
    override val pdlScope = "api://prod-fss.pdl.pdl-api/.default"
}
