package no.nav.tiltakspenger.journalposthendelser.journalpost

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.json.objectMapper
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue

class JournalpostIdJsonTest {
    @Test
    fun `serialiserer JournalpostId som json-string`() {
        val json = objectMapper.writeValueAsString(JournalpostIdWrapper(journalpostId = JournalpostId("4567")))

        json shouldBe """{"journalpostId":"4567"}"""
    }

    @Test
    fun `deserialiserer JournalpostId fra json-string`() {
        val json = """{"journalpostId":"4567"}"""

        objectMapper.readValue<JournalpostIdWrapper>(json) shouldBe JournalpostIdWrapper(journalpostId = JournalpostId("4567"))
    }

    private data class JournalpostIdWrapper(
        val journalpostId: JournalpostId,
    )
}
