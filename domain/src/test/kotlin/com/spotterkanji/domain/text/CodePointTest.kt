package com.spotterkanji.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kanji beyond the basic plane, which a Kotlin `Char` cannot hold.
 *
 * 𠮟 (U+20B9F) is the standard form of 叱 since 2010; 𩸽 (U+29E3D) is hokke on
 * izakaya menus; 𠮷 (U+20BB7) is the 吉 on Yoshinoya's own sign. Each is two
 * `Char`s in a Kotlin string, and testing those one at a time found no kanji at
 * all: no component box, and a lone 𩸽 skipped the kanji screen (D-49).
 */
class CodePointTest {

    @Test
    fun `a kanji past the basic plane is one character, not two halves`() {
        assertEquals(2, "𠮟".length)
        assertEquals(listOf("𠮟"), kanjiCharacters("𠮟る"))
        assertEquals(listOf("𠮷", "野", "家"), kanjiCharacters("𠮷野家"))
    }

    @Test
    fun `basic-plane kanji are unchanged`() {
        assertEquals(listOf("先", "生"), kanjiCharacters("先生"))
        assertEquals(listOf("生"), kanjiCharacters("生きる"))
        assertEquals(listOf("日", "日"), kanjiCharacters("日日"))
    }

    @Test
    fun `a lone kanji is one kanji whatever its length in Chars`() {
        assertTrue("𩸽".isSingleKanji())
        assertTrue("生".isSingleKanji())
        assertFalse("先生".isSingleKanji())
        assertFalse("き".isSingleKanji())
        assertFalse("".isSingleKanji())
    }

    /** Neither half of a surrogate pair is a kanji, which is the whole bug. */
    @Test
    fun `the halves of a pair are not kanji on their own`() {
        assertFalse("𠮟"[0].isKanji())
        assertFalse("𠮟"[1].isKanji())
        assertTrue(isKanji(0x20B9F))
    }

    @Test
    fun `offsets count in Chars, so they line up with token offsets`() {
        assertEquals(
            listOf(0x20B9F to 0, 'る'.code to 2),
            "𠮟る".codePointsWithIndex().toList(),
        )
    }

    /** Chips are for words: brackets, punctuation and bare digits are not. */
    @Test
    fun `kana or kanji is what makes a chip worth showing`() {
        listOf("理解", "の", "ミュージアム", "𠮟る", "ｶﾀｶﾅ").forEach {
            assertTrue("$it is writing", it.hasKanaOrKanji())
        }
        listOf("【", "、", "。", "(", "6", "·", "『", "ＪＲ").forEach {
            assertFalse("$it is not", it.hasKanaOrKanji())
        }
    }
}
