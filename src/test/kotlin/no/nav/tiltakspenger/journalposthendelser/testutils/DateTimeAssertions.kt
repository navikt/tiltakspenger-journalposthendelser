package no.nav.tiltakspenger.journalposthendelser.testutils

import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.LocalDateTime

infix fun LocalDateTime?.shouldBeCloseTo(expected: LocalDateTime?) {
    if (this == null) {
        expected shouldBe null
    } else {
        expected!!.shouldBeWithin(Duration.ofSeconds(10), this)
    }
}
