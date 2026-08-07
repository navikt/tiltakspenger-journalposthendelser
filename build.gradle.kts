import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

val mainClassFile = "no.nav.tiltakspenger.journalposthendelser.ApplicationKt"

val felleslibVersion = "0.0.20260804205752"
val ktorVersion = "3.4.3"
val confluentVersion = "8.1.1"
val avroVersion = "1.12.1"
val jackson2Version = "2.22.1"
val lz4Version = "1.11.1"
// Samme versjon som `kafka` i tiltakspenger-libs sin versjonskatalog; se constraint-blokka under for hvorfor den må være strict.
val kafkaVersion = "4.3.1"
val caffeineVersion = "3.2.4"
val mockkVersion = "1.14.11"
val prometeusVersion = "1.17.0"
val testContainersVersion = "2.0.5"
val kotestVersion = "6.2.3"

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

// Avro-pluginen drar inn `avro-tools` på buildscript-classpathen, og derfra kommer
// `avro-mapred` → `avro-ipc-jetty` → Jetty 9.4, som er EOL og ikke får sikkerhetsfikser.
// Sammen med gammel `commons-lang3` og `avro-compiler` 1.12.0 sto det for 16 Dependabot-alerts
// med scope `development`.
// Ingenting av det havner i imaget, men støyen skjulte de reelle runtime-funnene i alert-lista.
//
// Vi ekskluderer `avro-ipc-jetty` og ikke hele `avro-tools`, selv om schemaet vårt her er `.avsc`
// og kodegenereringen ville klart seg uten resten: `.avdl`-schemaer trenger `avro-idl` fra samme
// tre, og en bred ekskludering ville feilet med NoClassDefFoundError på `org/apache/avro/idl/IdlReader`
// den dagen noen legger til et. Samme avgrensning som i saksbehandling-api, som allerede bruker `.avdl`.
buildscript {
    configurations["classpath"].exclude(group = "org.apache.avro", module = "avro-ipc-jetty")
    dependencies {
        constraints {
            // Kodeinjeksjon i Avros Java-SDK (GHSA-rp46-r563-jrc7); samme versjon som `avroVersion`.
            add("classpath", "org.apache.avro:avro-compiler:1.12.1")
            // Ukontrollert rekursjon på lange inndata (GHSA-j288-q9x7-2f5v).
            add("classpath", "org.apache.commons:commons-lang3:3.18.0")
            // Avro drar inn jackson-bom 2.20.0 her. Buildscript-classpathen er en egen konfigurasjon,
            // så `implementation(platform(...))`-pinningen i dependencies-blokka når den ikke.
            // Versjonen er skrevet ut fordi buildscript-blokka evalueres før script-valene finnes; hold den i sync med `jackson2Version`.
            add("classpath", "com.fasterxml.jackson.core:jackson-core:2.22.1")
            add("classpath", "com.fasterxml.jackson.core:jackson-databind:2.22.1")
        }
    }
}

plugins {
    application
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("com.diffplug.spotless") version "8.8.0"
    id("com.github.ben-manes.versions") version "0.59.0"
    // https://github.com/androa/gradle-plugin-avro
    id("io.github.androa.gradle.plugin.avro") version "0.0.12"
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

application {
    mainClass.set(mainClassFile)
}

configurations.all {
    // ekskluder JUnit 4
    exclude(group = "junit", module = "junit")
}

dependencies {
    //libs
    implementation("com.github.navikt.tiltakspenger-libs:common:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:periodisering:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:json:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:kafka:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:kafka-avro:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:ktor-common:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:logging:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:persistering-domene:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:persistering-infrastruktur:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:texas:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:httpklient-infrastruktur:$felleslibVersion")
    testImplementation("com.github.navikt.tiltakspenger-libs:persistering-test-common:$felleslibVersion")
    testImplementation("com.github.navikt.tiltakspenger-libs:test-common:$felleslibVersion")
    testImplementation(testFixtures("com.github.navikt.tiltakspenger-libs:httpklient-infrastruktur:$felleslibVersion"))

    // Brukes direkte i klient- og service-koden (Either); gjøres eksplisitt i stedet for å arves transitivt fra libs.
    implementation("io.arrow-kt:arrow-core:2.2.3")

    // Lås versjonene på alle Kotlin-komponenter til samme versjon
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation(kotlin("stdlib"))

    // Lås alle io.netty:* til samme versjon. r2dbc-postgresql/reactor-netty (transitiv via
    // persistering-infrastruktur) drar inn netty 4.1.x, mens ktor-server-netty bruker 4.2.x.
    // Uten dette havner både netty-codec (4.1) og netty-codec-base (4.2) på classpath med
    // duplikate baseklasser (ByteToMessageDecoder m.fl.), som med `-cp lib/*` lastes i feil
    // rekkefølge og brekker HTTP-pipelinen.
    implementation(platform("io.netty:netty-bom:4.2.16.Final"))

    // Vår egen kode er på jackson3 (tools.jackson), men jackson 2 kommer inn transitivt via
    // Confluents kafka-avro-serializer (kafka-schema-registry-client avhenger av jackson-databind)
    // og drar med seg jackson-bom 2.20.0. Den har bl.a. to PolymorphicTypeValidator-omgåelser
    // (GHSA-rmj7-2vxq-3g9f, GHSA-j3rv-43j4-c7qm) og en SSRF via InetSocketAddress-deserialisering
    // (GHSA-hgj6-7826-r7m5). Vi styrer versjonen selv i stedet for å vente på at Confluent bumper -
    // samme versjon som `jackson2` i tiltakspenger-libs sin versjonskatalog.
    implementation(platform("com.fasterxml.jackson:jackson-bom:$jackson2Version"))

    constraints {
        // Confluent publiserer sin egen fork av kafka-clients som `8.1.1-ccs`. Den taper ikke
        // konfliktoppløsningen mot Apache 4.3.1 fra libs:kafka - Gradle leser "8.1.1-ccs" som
        // høyere enn "4.3.1" - så uten `strictly` er det Confluent-forken som havner i imaget.
        // Den er bygd på Kafka 4.1 og drar inn den avviklede `org.lz4:lz4-java` 1.8.0, som har
        // både out-of-bounds-lesing (GHSA-vqf4-7m7x-wgfc) og en informasjonslekkasje i den trygge
        // dekomprimereren (GHSA-cmp6-m4wj-q63q) - sistnevnte uten fiks på de koordinatene.
        // Med Apache-versjonen kommer i stedet `at.yawk.lz4:lz4-java`, som vedlikeholdes.
        implementation("org.apache.kafka:kafka-clients") {
            version { strictly(kafkaVersion) }
        }
        // Apache kafka-clients drar inn lz4-java 1.10.2, der de native XXHash-implementasjonene
        // kan krasje JVM-en på ugyldige byte-intervaller (GHSA-xx22-p4ch-683r).
        implementation("at.yawk.lz4:lz4-java:$lz4Version")
    }

    implementation("ch.qos.logback:logback-classic:1.5.38")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("org.jetbrains:annotations:26.1.0")
    implementation("com.natpryce:konfig:1.6.10.0")
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.4")

    // Http
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-http:${ktorVersion}")
    implementation("io.ktor:ktor-server-metrics-micrometer:${ktorVersion}")

    implementation("io.micrometer:micrometer-registry-prometheus:${prometeusVersion}")

    // Autentisering og validering av tokens
    implementation("io.ktor:ktor-server-auth:${ktorVersion}")

    // Jackson
    implementation("io.ktor:ktor-serialization-jackson3:${ktorVersion}")

    // DB
    implementation("org.flywaydb:flyway-database-postgresql:12.10.0")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("com.github.seratch:kotliquery:1.9.1")

    // Avro
    implementation("io.confluent:kafka-avro-serializer:${confluentVersion}")
    implementation("org.apache.avro:avro:${avroVersion}")

    // Caching
    implementation("com.github.ben-manes.caffeine:caffeine:${caffeineVersion}")

    // Test
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Delte arkitekturregler; drar inn konsist transitivt (api-avhengighet). Egen versjon inntil felleslibVersion bumpes.
    testImplementation("com.github.navikt.tiltakspenger-libs:konsist-regler:$felleslibVersion")
    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.mockk:mockk:${mockkVersion}")
    testImplementation("io.mockk:mockk-dsl-jvm:${mockkVersion}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.ktor:ktor-server-test-host:${ktorVersion}")

    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:$testContainersVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testContainersVersion")
}

// --- Kover --------------------------------------------------------------------
// Holder 100 % linjedekning for all produksjonskode utenom eksplisitte unntak.
// Dekningen verifiseres på `check`, og bygget feiler hvis terskelen ikke holdes.
// Rapporter genereres ved å kjøre koverHtmlReport eller koverXmlReport.
kover {
    reports {
        total {
            filters {
                excludes {
                    classes(
                        // Generert Avro-kode fra src/main/avro – ikke vår kode, testes ikke.
                        "no.nav.joarkjournalfoeringhendelser.*",
                        // TODO jah: Bootstrap som starter selve serveren (main/start); vurder å teste start() ved å gjøre startApp-oppsettet verifiserbart uten å blokkere.
                        "no.nav.tiltakspenger.journalposthendelser.ApplicationKt",
                        // TODO jah: Wiring av produksjonsavhengigheter; krever reell db-url via Configuration ved instansiering. Vurder å gjøre datasourcen injiserbar slik at konteksten kan instansieres i test.
                        "no.nav.tiltakspenger.journalposthendelser.context.ApplicationContext",
                        // TODO jah: Profil/miljøvariabler leses fra global system-env (System.getenv/getProperty); PROD/DEV-grenene kan ikke dekkes uten å mutere JVM-global tilstand delt mellom tester. Gjør profil/cluster-navn injiserbart.
                        "no.nav.tiltakspenger.journalposthendelser.Configuration*",
                        // TODO jah: gcp/lokal-Flyway-grenen velges av global Configuration.isNais(); testes ikke uten å mutere system-env. Gjør profilvalget injiserbart.
                        "no.nav.tiltakspenger.journalposthendelser.infra.db.FlywayMigrateKt",
                    )
                }
            }
            verify {
                onCheck = true
                rule("all produksjonskode utenom eksplisitte unntak skal ha 100 % linjedekning") {
                    bound {
                        minValue = 100
                        coverageUnits = CoverageUnit.LINE
                        aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                    }
                }
            }
        }
    }
}

tasks.named("koverXmlReport") {
    val xmlReport = layout.buildDirectory.file("reports/kover/report.xml")
    doLast {
        val xml = xmlReport.get().asFile
        val classCount = xml.readText().split("<class ").size - 1
        if (classCount == 0) throw GradleException("Kover-rapporten inneholder ingen klasser – ekskluderingsfilteret er trolig for grådig.")
    }
}

spotless {
    kotlin {
        ktlint()
            .editorConfigOverride(
                mapOf(
                    "ktlint_standard_max-line-length" to "off",
                    // Fjerner ubrukte importer automatisk i spotlessApply, og feiler i spotlessCheck.
                    // Eksplisitt aktivert fordi default code style (intellij_idea) deaktiverer den.
                    "ktlint_standard_no-unused-imports" to "enabled",
                    "ktlint_standard_function-signature" to "disabled",
                    "ktlint_standard_function-expression-body" to "disabled",
                ),
            )
    }
}

tasks {
    dependencyUpdates.configure {
        rejectVersionIf {
            isNonStable(candidate.version)
        }
    }

    kotlin {
        jvmToolchain(25)
        compilerOptions {
            freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
        }
    }

    test {
        // JUnit 5-støtte
        useJUnitPlatform()
        // https://phauer.com/2018/best-practices-unit-testing-kotlin/
        systemProperty("junit.jupiter.testinstance.lifecycle.default", "per_class")
        // https://github.com/mockito/mockito/issues/3037#issuecomment-1588199599
        jvmArgs("-XX:+EnableDynamicAgentLoading")
        testLogging {
            // Vi logger bare feilede og hoppede tester når Gradle kjører.
            events("skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    register("checkFlywayMigrationNames") {
        val sqlMigrationDir = project.file("src/main/resources/db/migration")
        val kotlinMigrationDir = project.file("src/main/kotlin/db/migration")
        doLast {
            val sqlFiles =
                sqlMigrationDir
                    .walk()
                    .filter { it.isFile && it.extension == "sql" }
                    .toList()

            val invalidSqlFiles =
                sqlFiles
                    .filterNot { it.name.matches(Regex("V[0-9]+__[a-zA-Z0-9][\\w]+\\.sql")) }
                    .map { it.name }

            if (invalidSqlFiles.isNotEmpty()) {
                throw GradleException("Invalid SQL migration filenames:\n${invalidSqlFiles.joinToString("\n")}")
            }
            val kotlinFiles =
                kotlinMigrationDir
                    .walk()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                    .toList()

            val invalidKotlinFiles =
                kotlinFiles
                    .filterNot { it.name.matches(Regex("V[0-9]+__[a-zA-Z0-9][\\w]+\\.(kt|java)")) }
                    .map { it.name }

            if (invalidKotlinFiles.isNotEmpty()) {
                throw GradleException("Invalid Kotlin/Java migration filenames:\n${invalidKotlinFiles.joinToString("\n")}")
            }

            // Sjekk for dupliserte versjoner på tvers av ALLE migreringstyper
            val allFiles = sqlFiles + kotlinFiles
            val duplicateVersions =
                allFiles
                    .mapNotNull {
                        it.name
                            .split("__")
                            .firstOrNull()
                            ?.removePrefix("V")
                            ?.toIntOrNull()
                    }.groupBy { it }
                    .filter { it.value.size > 1 }
                    .keys

            if (duplicateVersions.isNotEmpty()) {
                throw GradleException(
                    "Duplicate version numbers found:\n${duplicateVersions.joinToString("\n") { "Version $it is used multiple times" }}",
                )
            }

            println("All migration filenames are valid and version numbers are unique.")
        }
    }

    register<Copy>("gitHooks") {
        group = "git hooks"
        description = "Installerer git-hooks fra .gitHooks/ til .git/hooks/."
        from(file(".gitHooks"))
        into(file(".git/hooks"))
        filePermissions { unix("rwxr-xr-x") }
    }

    build {
        dependsOn("gitHooks")
    }
}

// --- Ingen andre HTTP-klienter enn libs sin httpklient -------------------------
// Konsist-reglene (IngenAndreHttpKlienter) dekker det vi selv skriver og deklarerer.
// Denne dekker det siste hullet: en klient som kommer inn transitivt gjennom en annen
// avhengighet, uten at den står i noen import eller i denne fila.
//
// Ktor-klienten står bevisst IKKE på lista, og skal ikke legges til: `ktor-server-auth`
// eksponerer `ktor-client-core` som `api` (OAuth-provideren bruker den), så den ligger på
// både compile- og runtime-classpathen så lenge vi bruker ktor sin server-auth. Ktor-klienten
// håndheves derfor i kilden (konsist-regelen) og i byggfila, ikke her.
val verifiserHttpKlienter =
    tasks.register("verifiserHttpKlienter") {
        group = "verification"
        description = "Feiler hvis en annen HTTP-klient enn libs sin httpklient ligger på runtime-classpathen."
        // Lista ligger inne i tasken, ikke som script-val: configuration cache kan ikke
        // serialisere referanser til byggskript-objekter fanget i doLast.
        val forbudteHttpKlienter =
            listOf(
                "com.squareup.okhttp3",
                "com.squareup.retrofit2",
                // Apache HttpComponents står bevisst IKKE på lista i dette repoet, av samme grunn som
                // ktor-klienten: `io.confluent:kafka-avro-serializer` drar inn
                // `kafka-schema-registry-client`, som avhenger av `httpclient5` for oppslag mot
                // schema registry. Den ligger dermed på runtime-classpathen så lenge vi konsumerer
                // Avro-topics, uten at vi selv bruker den. Apache håndheves derfor i kilden
                // (konsist-regelen IngenAndreHttpKlienter), ikke her.
                "com.github.kittinunf.fuel",
                "com.konghq:unirest",
                "io.vertx:vertx-web-client",
                "org.http4k:http4k-client",
                "io.github.openfeign",
            )
        val artefakter = configurations.named("runtimeClasspath").get().incoming.artifacts
        // Filene som input gir Gradle task-avhengighetene: uten dem kan ikke artefaktene slås opp
        // før jar-taskene til et inkludert bygg har kjørt (composite build mot libs).
        inputs.files(artefakter.artifactFiles).withPropertyName("runtimeClasspath")
        val runtimeKomponenter =
            artefakter.resolvedArtifacts
                .map { liste -> liste.map { artefakt -> artefakt.id.componentIdentifier.displayName } }
        doLast {
            val funn = runtimeKomponenter.get().filter { komponent -> forbudteHttpKlienter.any { it in komponent } }
            if (funn.isNotEmpty()) {
                throw GradleException(
                    "Andre HTTP-klienter enn libs sin httpklient på runtime-classpathen:\n" +
                        funn.distinct().sorted().joinToString("\n") { "- $it" },
                )
            }
        }
    }

tasks.named("check") { dependsOn(verifiserHttpKlienter) }
