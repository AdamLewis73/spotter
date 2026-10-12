package com.spotterkanji.domain.review

import com.spotterkanji.domain.dictionary.DictionaryEntry
import com.spotterkanji.domain.dictionary.Sense
import com.spotterkanji.domain.srs.CardKind
import com.spotterkanji.domain.user.StudyItem
import com.spotterkanji.domain.user.StudyItemId
import com.spotterkanji.domain.user.StudyItemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Answer checking and card building (D-107, D-108). Every case here fails
 * silently in the app — a right answer marked wrong teaches the learner to
 * distrust the grade, and a wrong one marked right teaches the wrong word.
 */
class ReviewTest {

    // ---- Romaji to kana

    @Test
    fun `romaji becomes kana`() {
        mapOf(
            "jouzu" to "じょうず", "joute" to "じょうて", "uwate" to "うわて",
            "sensei" to "せんせい", "seisan" to "せいさん", "gakusei" to "がくせい",
            "amai" to "あまい", "otona" to "おとな", "toukyou" to "とうきょう",
        ).forEach { (romaji, kana) -> assertEquals(romaji, kana, romajiToKana(romaji, final = true)) }
    }

    @Test
    fun `small tsu, n and long vowels follow IME rules`() {
        assertEquals("がっこう", romajiToKana("gakkou", final = true))
        assertEquals("かんじ", romajiToKana("kanji", final = true))
        assertEquals("こんにちは", romajiToKana("konnichiha", final = true))
        assertEquals("みんな", romajiToKana("minna", final = true))
        assertEquals("しんぶん", romajiToKana("shinbun", final = true))
        assertEquals("きんえん", romajiToKana("kin'en", final = true))
        assertEquals("らーめん", romajiToKana("ra-men", final = true))
        assertEquals("じょうず", romajiToKana("JOUZU", final = true))
    }

    /** A trailing n stays a letter while typing — the next key may make it な. */
    @Test
    fun `a trailing n waits until submit`() {
        assertEquals("せいさn", romajiToKana("seisan"))
        assertEquals("せいさん", romajiToKana("seisan", final = true))
    }

    @Test
    fun `kana passes through and katakana reads as hiragana`() {
        assertEquals("じょうず", romajiToKana("じょうず"))
        assertEquals("らーめん", katakanaToHiragana("ラーメン"))
    }

    // ---- Meaning

    private val jouzu = listOf(
        listOf("skillful", "skilled", "proficient", "good (at)", "adept", "clever"),
        listOf("flattery"),
    )
    private val amai = listOf(
        listOf("sweet-tasting", "sweet", "sugary", "sugared"),
        listOf("indulgent", "lenient"),
    )

    @Test
    fun `a listed synonym is right`() {
        assertTrue(meaningMatches("skilled", jouzu))
        assertTrue(meaningMatches("Skillful", jouzu))
        assertTrue(meaningMatches("  adept ", jouzu))
    }

    @Test
    fun `any listed sense is right, not only the main one`() {
        assertTrue(meaningMatches("lenient", amai))
        assertTrue(meaningMatches("flattery", jouzu))
    }

    @Test
    fun `bracketed parts match with or without them`() {
        assertTrue(meaningMatches("good", jouzu))
        assertTrue(meaningMatches("good at", jouzu))
    }

    @Test
    fun `articles and to are ignored`() {
        assertTrue(meaningMatches("a teacher", listOf(listOf("teacher", "instructor"))))
        assertTrue(meaningMatches("take", listOf(listOf("to take", "to pick up"))))
        assertTrue(meaningMatches("to take", listOf(listOf("to take"))))
    }

    @Test
    fun `small typos pass in longer words only`() {
        assertTrue(meaningMatches("skilful", jouzu))      // one letter, 8-letter word
        assertTrue(meaningMatches("teachr", listOf(listOf("teacher"))))
        assertFalse(meaningMatches("cut", listOf(listOf("cat"))))  // 3 letters: exact only
    }

    @Test
    fun `a wrong meaning is wrong`() {
        assertFalse(meaningMatches("talented", jouzu))
        assertFalse(meaningMatches("child", listOf(listOf("adult", "grown-up"))))
        assertFalse(meaningMatches("", jouzu))
        assertFalse(meaningMatches("   ", jouzu))
    }

    /** "I was right" saves the learner's answer, which is accepted from then on. */
    @Test
    fun `a saved synonym is right`() {
        assertFalse(meaningMatches("talented", jouzu))
        assertTrue(meaningMatches("talented", jouzu, synonyms = listOf("talented")))
    }

    // ---- Reading

    private val others = listOf(OtherReading("うわて", "upper part"), OtherReading("かみて", "upper part"))

    @Test
    fun `every reading of the saved word is right`() {
        val accepted = listOf("じょうず", "じょうしゅ", "じょうて")
        assertEquals(ReadingVerdict.Right("じょうず"), checkReading("jouzu", accepted, others))
        assertEquals(ReadingVerdict.Right("じょうて"), checkReading("joute", accepted, others))
        assertEquals(ReadingVerdict.Right("じょうず"), checkReading("ジョウズ", accepted, others))
    }

    @Test
    fun `a reading of another word is a retry, not a mark`() {
        val verdict = checkReading("uwate", listOf("じょうず"), others)
        assertEquals(ReadingVerdict.OtherWord(OtherReading("うわて", "upper part")), verdict)
    }

    @Test
    fun `anything else is wrong, and nothing is empty`() {
        assertEquals(ReadingVerdict.Wrong("じょうぞ"), checkReading("jouzo", listOf("じょうず"), others))
        assertEquals(ReadingVerdict.Empty, checkReading("  ", listOf("じょうず"), others))
    }

    // ---- Building a card

    private fun item(text: String, reading: String, gloss: String = "teacher; instructor") = StudyItem(
        id = StudyItemId("id-$text-$reading"),
        key = StudyItemKey(text, reading),
        snapshotGloss = gloss,
        entSeq = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deletedAt = null,
    )

    private fun entry(reading: String, entSeq: Long, vararg glosses: String) =
        DictionaryEntry(text = "上手", reading = reading, senses = listOf(Sense(glosses.toList())), entSeq = entSeq)

    /** 上手: three readings in one JMdict entry, and two other words written the same. */
    private val jouzuEntries = listOf(
        entry("じょうず", 1, "skillful", "skilled"),
        entry("うわて", 2, "upper part"),
        entry("かみて", 2, "upper part"),
        entry("じょうしゅ", 1, "skillful", "skilled"),
        entry("じょうて", 1, "skillful", "skilled"),
    )

    @Test
    fun `the saved reading's entry decides which readings count`() {
        val card = buildReviewCard(item("上手", "じょうず"), jouzuEntries, emptyList())
        assertTrue(card.resolved)
        assertEquals(listOf("じょうず", "じょうしゅ", "じょうて"), card.acceptedReadings)
        assertEquals(listOf("うわて", "かみて"), card.otherWords.map { it.reading })
        assertEquals(listOf("skillful", "skilled"), card.mainSense)
    }

    @Test
    fun `a word saved under another reading is checked against that one`() {
        val card = buildReviewCard(item("上手", "うわて"), jouzuEntries, emptyList())
        assertEquals(listOf("うわて", "かみて"), card.acceptedReadings)
        assertEquals(listOf("じょうず", "じょうしゅ", "じょうて"), card.otherWords.map { it.reading })
        assertEquals(listOf("upper part"), card.mainSense)
    }

    /** V-20: a word the dictionary dropped is still reviewable, from its snapshot. */
    @Test
    fun `an unresolvable word falls back to its snapshot`() {
        val card = buildReviewCard(item("先生", "せんせい", "teacher; instructor"), emptyList(), listOf("sensei"))
        assertFalse(card.resolved)
        assertEquals(listOf("teacher", "instructor"), card.mainSense)
        assertEquals(listOf("せんせい"), card.acceptedReadings)
        assertTrue(meaningMatches("instructor", card.senses, card.synonyms))
        assertTrue(meaningMatches("sensei", card.senses, card.synonyms))
    }

    // ---- Fuzz seed

    @Test
    fun `the fuzz seed is fixed by the review and differs between reviews`() {
        val id = StudyItemId("abc")
        assertEquals(fuzzSeed(id, CardKind.REVIEW, 1000), fuzzSeed(id, CardKind.REVIEW, 1000))
        assertNotEquals(fuzzSeed(id, CardKind.REVIEW, 1000), fuzzSeed(id, CardKind.REVIEW, 1001))
        assertNotEquals(fuzzSeed(id, CardKind.REVIEW, 1000), fuzzSeed(id, CardKind.WRITING, 1000))
        // Pinned to a value computed independently (FNV-1a in Python), so a
        // change to the function — which would move every replayed due date —
        // fails here first.
        assertEquals(-4854094843812333479L, fuzzSeed(StudyItemId("x"), CardKind.REVIEW, 0))
    }
}
