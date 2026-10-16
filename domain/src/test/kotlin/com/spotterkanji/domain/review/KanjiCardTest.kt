package com.spotterkanji.domain.review

import com.spotterkanji.domain.dictionary.KanjiDetail
import com.spotterkanji.domain.dictionary.KanjiExample
import com.spotterkanji.domain.dictionary.KanjiReadingGroup
import com.spotterkanji.domain.user.StudyItem
import com.spotterkanji.domain.user.StudyItemId
import com.spotterkanji.domain.user.StudyItemKey
import com.spotterkanji.domain.user.StudyItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The kanji card (D-118): any one reading passes, any meaning passes, and the
 * answer shows every reading with a word. A reading wrongly refused teaches the
 * learner to distrust the grade; one wrongly accepted is the rule, not a fault.
 */
class KanjiCardTest {

    /** 生 as KANJIDIC2 has it, trimmed: markers and all. */
    private val sei = KanjiDetail(
        character = "生",
        meanings = listOf("life", "genuine", "birth"),
        onReadings = listOf("セイ", "ショウ"),
        kunReadings = listOf("い.きる", "う.まれる", "うま.れる", "なま", "なま-", "き", "む.す", "-う"),
        strokeCount = 5,
        readingGroups = listOf(
            KanjiReadingGroup("セイ", "on", listOf(KanjiExample("先生", "せんせい", "teacher"))),
            KanjiReadingGroup("ショウ", "on", listOf(KanjiExample("一生", "いっしょう", "a lifetime"))),
            KanjiReadingGroup("い", "kun", listOf(KanjiExample("生きる", "いきる", "to live"))),
            KanjiReadingGroup("なま", "kun", listOf(KanjiExample("生ビール", "なまビール", "draft beer"))),
        ),
        asWord = emptyList(),
        strokePaths = emptyList(),
    )

    private val item = StudyItem(
        id = StudyItemId("id-生"),
        key = StudyItemKey("生", "", StudyItemType.KANJI),
        snapshotGloss = "life, genuine, birth",
        entSeq = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deletedAt = null,
    )

    private fun reading(typed: String, card: ReviewCard) = checkReading(typed, card.acceptedReadings, card.otherWords)

    @Test
    fun `any one reading passes, typed as romaji or kana`() {
        val card = buildKanjiReviewCard(item, sei, emptyList())
        listOf("sei", "shou", "セイ", "nama", "ki", "musu").forEach {
            assertTrue(it, reading(it, card) is ReadingVerdict.Right)
        }
    }

    /** A card shows the kanji alone, so a kun'yomi's stem is enough; the full form counts too. */
    @Test
    fun `a kun reading passes as its stem or in full`() {
        val card = buildKanjiReviewCard(item, sei, emptyList())
        assertTrue(reading("i", card) is ReadingVerdict.Right)
        assertTrue(reading("ikiru", card) is ReadingVerdict.Right)
        assertTrue(reading("umareru", card) is ReadingVerdict.Right)
        assertTrue("うま.れる's stem", reading("uma", card) is ReadingVerdict.Right)
    }

    @Test
    fun `markers are not part of any answer`() {
        val answers = kanjiAnswers(sei.onReadings, sei.kunReadings).keys
        assertFalse(answers.any { '.' in it || '-' in it })
        assertTrue("-う is the suffix う", "う" in answers)
    }

    @Test
    fun `a reading 生 does not have is wrong`() {
        val card = buildKanjiReviewCard(item, sei, emptyList())
        assertTrue(reading("ka", card) is ReadingVerdict.Wrong)
        assertTrue(reading("seikatsu", card) is ReadingVerdict.Wrong)
    }

    @Test
    fun `any of the kanji's meanings passes`() {
        val card = buildKanjiReviewCard(item, sei, emptyList())
        listOf("life", "Birth", "genuine").forEach { assertTrue(it, meaningMatches(it, card.senses, card.synonyms)) }
        assertFalse(meaningMatches("death", card.senses, card.synonyms))
    }

    /** The reveal marks the reading typed, whichever form it was typed in. */
    @Test
    fun `the reveal knows which reading was answered`() {
        val reveal = buildKanjiReviewCard(item, sei, emptyList()).kanji!!
        assertEquals("い", reveal.keyFor("いきる"))
        assertEquals("い", reveal.keyFor("い"))
        assertEquals("せい", reveal.keyFor("せい"))
        assertEquals("せい", reveal.keyFor("セイ"))
        assertNull(reveal.keyFor("か"))
    }

    @Test
    fun `readings without a common word are listed apart, on'yomi in katakana`() {
        val noExample = sei.copy(readingGroups = sei.readingGroups.filterNot { it.reading == "ショウ" }) // ショウ loses its word
        val reveal = buildKanjiReviewCard(item, noExample, emptyList()).kanji!!
        assertEquals(listOf("セイ", "い", "なま"), reveal.readings.map { it.reading })
        assertTrue("ショウ" in reveal.rarer)
        assertTrue("む" in reveal.rarer)
        assertFalse("shown readings are not repeated", "なま" in reveal.rarer)
    }

    /** D-40: a kanji the dictionary lost stays reviewable, on its meaning alone. */
    @Test
    fun `a kanji the dictionary no longer has asks its meaning only`() {
        val card = buildKanjiReviewCard(item, null, emptyList())
        assertFalse(card.asksReading)
        assertTrue(meaningMatches("genuine", card.senses, card.synonyms))
    }
}
