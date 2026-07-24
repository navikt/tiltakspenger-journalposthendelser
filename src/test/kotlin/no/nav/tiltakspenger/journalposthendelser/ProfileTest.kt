package no.nav.tiltakspenger.journalposthendelser

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test

class ProfileTest {
    @Test
    fun `har de tre miljøprofilene`() {
        Profile.entries shouldContainExactly listOf(Profile.LOCAL, Profile.DEV, Profile.PROD)
    }
}
