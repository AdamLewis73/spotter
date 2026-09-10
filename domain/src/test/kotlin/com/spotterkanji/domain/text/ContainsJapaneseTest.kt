package com.spotterkanji.domain.text

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
