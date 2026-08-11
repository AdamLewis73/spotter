package com.spotterkanji.domain.tokenize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Plain JUnit, no emulator, no Robolectric — the practical payoff of :domain
 * being a Kotlin/JVM module rather than an Android library (D-60).
 */
class TokenTest {

    @Test
    fun `length is the half-open span`() {
        assertEquals(2, Token("先生", start = 0, endExclusive = 2).length)
    }

    @Test
    fun `offsets are character positions, not byte positions`() {
        // 先生と生産 — 生産 starts at character 3 even though each character is
        // three bytes in UTF-8. Getting this wrong shifts every tap target.
        val text = "先生と生産"
        val token = Token("生産", start = 3, endExclusive = 5)
        assertEquals(token.text, text.substring(token.start, token.endExclusive))
    }

    @Test
    fun `an empty or reversed span is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Token("", start = 2, endExclusive = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Token("生", start = 4, endExclusive = 1)
        }
    }
}
