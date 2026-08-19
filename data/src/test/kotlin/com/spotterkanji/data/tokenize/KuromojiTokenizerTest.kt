package com.spotterkanji.data.tokenize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JVM tests — Kuromoji is a pure JVM library, so none of this needs a
 * device even though it lives in an Android module.
 */
class KuromojiTokenizerTest {

    private val tokenizer = KuromojiTokenizer()

    /** The worked example from `overview.md`, end to end. */
    @Test
    fun `splits the documented example into three tokens`() {
        val tokens = tokenizer.tokenize("先生と生産")

        assertEquals(listOf("先生", "と", "生産"), tokens.map { it.text })
    }

    /**
     * Offsets are what the scan overlay will use to map a tap back to a word
     * (`architecture.md` stage 4). An off-by-one here lands taps on a
     * neighbouring word, which reads as flaky OCR rather than as a bug (V-11).
     */
    @Test
    fun `offsets index back into the source text`() {
        val text = "先生と生産"
        val tokens = tokenizer.tokenize(text)

        tokens.forEach { token ->
            assertEquals(
                "token ${token.text} does not match its own offsets",
                token.text,
                text.substring(token.start, token.endExclusive),
            )
        }
        assertEquals(0, tokens.first().start)
        assertEquals(text.length, tokens.last().endExclusive)
    }

    /**
     * The reason [com.spotterkanji.domain.tokenize.Token.baseForm] exists: a sign
     * reads 生きた, and the dictionary only has 生きる. Without the base form the
     * lookup silently returns nothing.
     */
    @Test
    fun `an inflected verb carries its dictionary form`() {
        val tokens = tokenizer.tokenize("生きた")

        val verb = tokens.first()
        assertEquals("生き", verb.text)
        assertEquals("生きる", verb.baseForm)
    }

    /** No base form is reported when the surface form already is one. */
    @Test
    fun `an uninflected noun has no separate base form`() {
        assertNull(tokenizer.tokenize("先生").single().baseForm)
    }

    /**
     * Particles keep their place in the offsets — dropping them would corrupt
     * every position after them — but are marked so the UI can decline to offer
     * "case marking particle" as something to learn.
     */
    @Test
    fun `particles are tokens but not content words`() {
        val tokens = tokenizer.tokenize("先生と生産")

        val particle = tokens.single { it.text == "と" }
        assertEquals("助詞", particle.partOfSpeech)
        assertTrue("particles should not be offered as content", !particle.isContentWord)
        assertTrue(tokens.single { it.text == "先生" }.isContentWord)
    }

    /**
     * Kuromoji writes the literal string "*" for absent fields. Letting that
     * through produces a lookup for "*", which returns nothing and looks like a
     * missing dictionary entry rather than a bug.
     */
    @Test
    fun `kuromoji's asterisk placeholder never escapes`() {
        val tokens = tokenizer.tokenize("先生と生産を見た")

        assertTrue(
            "a literal * leaked into a base form",
            tokens.none { it.baseForm == "*" || it.partOfSpeech == "*" },
        )
    }

    @Test
    fun `blank input yields no tokens`() {
        assertEquals(emptyList<Any>(), tokenizer.tokenize("   "))
    }
}
