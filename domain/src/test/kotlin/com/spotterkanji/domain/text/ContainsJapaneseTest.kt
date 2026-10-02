package com.spotterkanji.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deciding which runs of a mixed sentence need the Japanese font (D-34).
 *
 * The failure this guards against is silent in the usual way: a Japanese word
 * set in IBM Plex does not throw or render as boxes, it quietly falls back to
 * the system font and looks almost right.
 */
class ContainsJapaneseTest {

    @Test
    fun `kanji need the Japanese font`() {
        assertTrue("先生".containsJapanese())
    }

    /**
     * The case that stops this being built on [isKanji]. That predicate excludes
     * kana deliberately, because kana do not get component chips — but a
     * kana-only word needs Noto Sans JP exactly as much as a kanji one.
     */
    @Test
    fun `a kana-only word needs it too, though isKanji says no`() {
        assertFalse("こんにちは".any { it.isKanji() })
        assertTrue("こんにちは".containsJapanese())
        assertTrue("カタカナ".containsJapanese())
    }

    @Test
    fun `half-width katakana and Japanese punctuation count`() {
        assertTrue("ｶﾀｶﾅ".containsJapanese())
        assertTrue("「」".containsJapanese())
    }

    /**
     * An English list name must NOT be switched to the Japanese face just for
     * sitting in the same sentence as a Japanese word.
     */
    @Test
    fun `plain English does not`() {
        assertFalse("Street Signs".containsJapanese())
        assertFalse("".containsJapanese())
    }

    /** A list the user named in Japanese does, which is why list names are checked at all. */
    @Test
    fun `a mixed name does`() {
        assertTrue("駅 signs".containsJapanese())
    }
}

/** Which stretches of user-typed text get the Japanese font (D-98). */
class JapaneseRunsTest {

    private fun runs(text: String) = text.japaneseRuns().map { text.substring(it.first, it.last + 1) }

    @Test
    fun `an English name has no Japanese runs`() {
        assertEquals(emptyList<String>(), runs("Street signs"))
    }

    @Test
    fun `a mixed name switches only the Japanese part`() {
        assertEquals(listOf("食べ物"), runs("Food 食べ物"))
        assertEquals(listOf("食べ物"), runs("食べ物 food"))
    }

    @Test
    fun `a space inside Japanese does not split the run`() {
        assertEquals(listOf("駅 の看板"), runs("駅 の看板"))
    }

    @Test
    fun `full-width letters and Japanese punctuation need the font too`() {
        assertEquals(listOf("ＪＲ"), runs("ＪＲ lines"))
        assertEquals(listOf("「先生」"), runs("say 「先生」"))
    }

    @Test
    fun `a kanji past the basic plane is kept whole`() {
        assertEquals(listOf("𠮟る"), runs("𠮟る"))
        assertTrue("𠮟".containsJapanese())
    }
}
