package com.spotterkanji.domain.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The identity rule, which is a checkpoint in `roadmap.md` because every row of
 * review history is keyed to it (D-12, D-27).
 *
 * The failure this guards against is silent. Keying on text alone merges words
 * that merely look alike, and nothing anywhere reports an error — the learner
 * simply finds, months later, that their reviews have been teaching them a
 * reading the word does not have in the sense they saved it for.
 */
class StudyItemKeyTest {

    /** The canonical case, and the reason the rule exists. */
    @Test
    fun `上手 is three different words`() {
        val skilled = StudyItemKey("上手", "じょうず")
        val upperHand = StudyItemKey("上手", "うわて")
        val stageLeft = StudyItemKey("上手", "かみて")

        assertNotEquals(skilled, upperHand)
        assertNotEquals(upperHand, stageLeft)
        assertNotEquals(skilled, stageLeft)
        assertEquals(3, setOf(skilled, upperHand, stageLeft).size)
    }

    /** The same word saved twice is the same key, which is what makes save idempotent. */
    @Test
    fun `same text and reading is the same word`() {
        assertEquals(StudyItemKey("先生", "せんせい"), StudyItemKey("先生", "せんせい"))
    }

    /**
     * A word and a kanji written identically are not the same study item (D-27).
     *
     * 生 scanned alone opens the kanji screen (D-49), so both can genuinely be
     * saved, and they are different things to review.
     */
    @Test
    fun `type separates a kanji from a word of the same text`() {
        val word = StudyItemKey("生", "なま", StudyItemType.WORD)
        val kanji = StudyItemKey("生", "", StudyItemType.KANJI)

        assertNotEquals(word, kanji)
    }

    /**
     * Half the identity is not the identity. A word saved with an empty reading
     * would collide with every other reading of the same text the moment one is
     * saved, so it is rejected at construction rather than at the database.
     */
    @Test
    fun `a word without a reading is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            StudyItemKey("上手", "")
        }
    }

    /** A kanji's written form is its whole identity, so an empty reading is correct there. */
    @Test
    fun `a kanji needs no reading`() {
        assertEquals("生", StudyItemKey("生", "", StudyItemType.KANJI).text)
    }

    @Test
    fun `text is required`() {
        assertThrows(IllegalArgumentException::class.java) {
            StudyItemKey("", "せんせい")
        }
    }
}
