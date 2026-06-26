# tiltakspenger-journalposthendelser

Applikasjon for å motta og behandle journalposthendelser fra topicen "teamdokumenthandtering.aapen-dok-journalfoering" og hendelser av typen "JournalpostMottatt" for tema IND (Individstønad/Tiltakspenger).

En del av satsningen ["Flere i arbeid – P4"](https://memu.no/artikler/stor-satsing-skal-fornye-navs-utdaterte-it-losninger-og-digitale-verktoy/)

## Java-versjon

Vi ønsker å holde oss på en **LTS-versjon** av Java (f.eks. 17, 21, 25). Unngå å oppgradere til mellomliggende (non-LTS) versjoner.

Når Java-versjonen skal endres må følgende steder oppdateres samtidig:

- `build.gradle.kts` – `jvmVersion` (`JvmTarget.JVM_XX`), som styrer kompileringsmålet (target).
- `Dockerfile` – `FROM gcr.io/distroless/javaXX-debianYY` (runtime-image). Sjekk at en tilsvarende tag finnes på [distroless](https://github.com/GoogleContainerTools/distroless).
- `.github/workflows/.test-and-build.yml` – `java-version`.
- `.github/workflows/codeql.yml` – `java-version`.
- `.github/workflows/dependabot-auto-merge.yml` – `java-version`.

Bygg og kjør testene med den nye versjonen for å verifisere: `./gradlew clean build`.
