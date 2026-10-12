package com.spotterkanji.domain.text

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The kanji test decides two visible behaviours: which characters get a
 * component chip, and whether a lone token routes to the kanji screen (D-49).
 * Getting it wrong shows as empty chips or a blank kanji screen, never as an
 * error.
 */
class JapaneseTest {

    @Test
    fun `common kanji are recognised`() {
        listOf('生', '先', '手', '上', '学', '産', '鬱').forEach {
            assertTrue("$it should be kanji", it.isKanji())
        }
    }

    /**
     * Kana are not kanji. 生きる contributes one chip, not four — and the き, る
     * of okurigana have no kanji entry to show.
     */
    @Test
    fun `kana are not kanji`() {
        listOf('き', 'る', 'せ', 'ん', 'ア', 'カ', 'ー').forEach {
            assertFalse("$it should not be kanji", it.isKanji())
        }
    }

    @Test
    fun `latin, digits and punctuation are not kanji`() {
        listOf('A', 'z', '1', '-', ' ', '。', '、').forEach {
            assertFalse("$it should not be kanji", it.isKanji())
        }
    }

    /**
     * Saveable in v1 means "contains a kanji" (D-110). A word with kana around
     * its kanji is saveable; a word of kana alone is not, whichever script.
     */
    @Test
    fun `words with a kanji can be saved`() {
        listOf("先生", "生きる", "お茶", "上手", "𠮟る", "𩸽").forEach {
            assertTrue("$it contains a kanji", it.containsKanji())
        }
    }

    @Test
    fun `kana-only words and non-words cannot be saved`() {
        listOf("トイレ", "これ", "すごい", "ラーメン", "ｶﾀｶﾅ", "ＪＲ", "").forEach {
            assertFalse("$it contains no kanji", it.containsKanji())
        }
    }
}
