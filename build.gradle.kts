import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val jvmVersion = JvmTarget.JVM_21
val mainClassFile = "no.nav.tiltakspenger.journalposthendelser.ApplicationKt"

val felleslibVersion = "0.0.569"
val ktorVersion = "3.2.3"
val confluentVersion = "8.0.0"
val avroVersion = "1.12.0"

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

plugins {
    application
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
    id("com.diffplug.spotless") version "7.2.1"
    id("com.github.ben-manes.versions") version "0.52.0"
    // https://github.com/androa/gradle-plugin-avro
    id("io.github.androa.gradle.plugin.avro") version "0.0.12"
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

dependencies {
    //libs
    implementation("com.github.navikt.tiltakspenger-libs:kafka:$felleslibVersion")


    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation(kotlin("stdlib"))
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
    implementation("org.jetbrains:annotations:26.0.2-1")
    implementation("com.natpryce:konfig:1.6.10.0")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")

    // Http
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")

    // Avro
    implementation("io.confluent:kafka-avro-serializer:${confluentVersion}")
    implementation("org.apache.avro:avro:${avroVersion}")
}

spotless {
    kotlin {
        ktlint()
            .editorConfigOverride(
                mapOf(
                    "ktlint_standard_max-line-length" to "off",
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
        compilerOptions {
            jvmTarget.set(jvmVersion)
            freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
        }
    }

    test {
        // JUnit 5 support
        useJUnitPlatform()
        // https://phauer.com/2018/best-practices-unit-testing-kotlin/
        systemProperty("junit.jupiter.testinstance.lifecycle.default", "per_class")
        // https://github.com/mockito/mockito/issues/3037#issuecomment-1588199599
        jvmArgs("-XX:+EnableDynamicAgentLoading")
        testLogging {
            // We only want to log failed and skipped tests when running Gradle.
            events("skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    jar {
        dependsOn(configurations.runtimeClasspath)

        manifest {
            attributes["Main-Class"] = mainClassFile
            attributes["Class-Path"] =
                configurations.runtimeClasspath
                    .get()
                    .joinToString(separator = " ") { file -> file.name }
        }
    }

    register<Copy>("gitHooks") {
        from(file(".scripts/pre-commit"))
        into(file(".git/hooks"))
    }

    build {
        dependsOn("gitHooks")
    }
}
