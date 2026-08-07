# tiltakspenger-journalposthendelser

Applikasjon for å motta og behandle journalposthendelser fra topicen "teamdokumenthandtering.aapen-dok-journalfoering" og hendelser av typen "JournalpostMottatt" for tema IND (Individstønad/Tiltakspenger).

Appen knytter innkommende dokumenter til en sak i tiltakspenger og legger dem i kø for saksbehandling ved å opprette oppgave.

## Hva appen gjør

Appen er en ren Kafka-consumer.
Den har ingen fagendepunkter — HTTP-flaten er kun `/isalive`, `/isready` og `/metrics`.

Consumeren plukker ut hendelser av typen `JournalpostMottatt` eller `TemaEndret` med tema `IND`, og forkaster resten.
For hver hendelse som skal behandles:

1. Henter metadata om journalposten fra SAF (journalstatus, `datoOpprettet`, brevkode, tittel og bruker).
2. Sjekker om journalposten allerede er ferdig behandlet.
   Er den journalført, eller finnes det allerede en åpen oppgave på den, hopper vi over — med mindre vi har en påbegynt, uferdig behandling lagret fra før.
3. Slår opp gjeldende ident i PDL.
   Mangler journalposten bruker, opprettes en fordelingsoppgave, og behandlingen stopper der.
4. Henter eller oppretter saksnummer i tiltakspenger-saksbehandling-api, og knytter saken til journalposten i dokarkiv.
5. Papirsøknader (brevkode `NAV 76-13.45`) ferdigstilles i dokarkiv og får en behandle sak-oppgave.
   Øvrige dokumenter får en journalføringsoppgave.

Hvert fullførte steg lagres i tabellen `journalposthendelse` med tidspunkt.
Feiler et steg, logges feilen og consumeren kaster videre slik at offset ikke committes, og hendelsen forsøkes på nytt.
Ved nytt forsøk hopper appen over stegene som allerede er gjort.

Oppgave-API-et har ingen innebygd duplikatkontroll, så appen søker selv etter åpen oppgave av samme type før den oppretter en ny, og gjenbruker oppgave-id-en om den finnes.

Brevkodene vi kjenner igjen står i [`Brevkode.kt`](src/main/kotlin/no/nav/tiltakspenger/journalposthendelser/journalpost/domene/Brevkode.kt).
Dokumentasjon for joarkhendelser: <https://confluence.adeo.no/spaces/BOA/pages/432217891/Joarkhendelser>.

## Integrasjoner

| Tjeneste | Brukes til |
| --- | --- |
| SAF | Henter metadata om journalposten (GraphQL) |
| PDL | Slår opp gjeldende ident for bruker (GraphQL) |
| tiltakspenger-saksbehandling-api | Henter eller oppretter saksnummer |
| dokarkiv | Knytter sak til journalposten og ferdigstiller den |
| oppgave | Søker etter og oppretter oppgaver |

Alle kallene går gjennom `httpklient` fra tiltakspenger-libs, med systemtoken fra Texas.
URL-er og scopes settes per miljø i [`.nais`](.nais).

## Database

Appen har én Cloud SQL Postgres-instans per miljø, med én tabell (`journalposthendelse`) som holder styr på hvor langt hver journalpost er kommet.
Skjemaet migreres med Flyway fra [`src/main/resources/db/migration`](src/main/resources/db/migration).

## Metrikker

Tellerne ligger i [`MetricRegister.kt`](src/main/kotlin/no/nav/tiltakspenger/journalposthendelser/infra/MetricRegister.kt) og har prefiks `tpts_jphendelser`:
`_journalposthendelser_count`, `_soknad_count`, `_klager_count`, `_meldekort_count` og `_annen_brevkode_count`.

Merk at rå `sum()` kun viser tall siden siste utrulling — tellerne nullstilles når podene starter på nytt.

## Bygg og test

```sh
./gradlew build
```

Bygget krever Docker: repository-testene kjører mot Postgres i testcontainers.

`check` kjører i tillegg til testene:

- **Kover** — 100 % linjedekning for all produksjonskode utenom de eksplisitte unntakene i `build.gradle.kts`.
- **spotless/ktlint** — formatering; `./gradlew spotlessApply` retter opp.
- **verifiserHttpKlienter** — feiler hvis en annen HTTP-klient enn libs sin havner på runtime-classpathen.
- **Konsist** — de delte arkitekturreglene fra tiltakspenger-libs.

Appen kjøres ikke opp lokalt: Kafka-consumeren startes kun når appen kjører i nais, og øvrig konfigurasjon leses fra miljøvariabler som bare finnes der.
Verifiser endringer med testene.

## Deploy

Deployen bruker de delte workflowene i [metarepoet](https://github.com/navikt/tiltakspenger/blob/main/.github/workflows/README.md).
Push til `main` bygger og deployer til dev og deretter prod ([`deploy-prod.yml`](.github/workflows/deploy-prod.yml)).
`deploy-dev.yml` kan kjøres manuelt for å deploye en gren til dev.

Repoet har per-miljø nais-manifester ([`.nais/nais-dev.yml`](.nais/nais-dev.yml) og [`.nais/nais-prod.yml`](.nais/nais-prod.yml)) og ingen vars-filer.

## Java-versjon

Vi ønsker å holde oss på en **LTS-versjon** av Java (f.eks. 17, 21, 25).
Unngå å oppgradere til mellomliggende (non-LTS) versjoner.

Når Java-versjonen skal endres må følgende steder oppdateres samtidig:

- `build.gradle.kts` – `jvmToolchain(XX)`, som styrer hvilken JDK Kotlin kompilerer med.
- `Dockerfile` – `FROM gcr.io/distroless/javaXX-debianYY` (runtime-image).
  Sjekk at en tilsvarende tag finnes på [distroless](https://github.com/GoogleContainerTools/distroless).

Java-versjonen i CI kommer fra `java-version`-defaulten i de delte workflowene i metarepoet, og settes ikke i dette repoet.
Skal appen ligge på en annen versjon enn resten av flåten, må callerne i `.github/workflows` sende inputen eksplisitt.

Bygg og kjør testene med den nye versjonen for å verifisere: `./gradlew clean build`.

## Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

### For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #tiltakspenger-værsågod.
