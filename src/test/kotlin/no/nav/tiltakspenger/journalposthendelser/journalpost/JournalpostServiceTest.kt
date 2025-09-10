package no.nav.tiltakspenger.journalposthendelser.journalpost

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JournalpostServiceTest {
    private val safJournalpostClient = mockk<SafJournalpostClient>()
    private val journalpostService = JournalpostService(
        safJournalpostClient = safJournalpostClient,
    )

    @Test
    fun `kaster feil om klienten ikke returnerer en journalpost`() = runTest {
        coEvery { safJournalpostClient.getJournalpostMetadata(any()) } returns null
        assertThrows<IllegalStateException> { journalpostService.hentJournalpost(123L) }
    }
}
