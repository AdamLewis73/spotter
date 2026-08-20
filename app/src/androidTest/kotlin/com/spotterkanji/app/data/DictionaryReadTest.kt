package com.spotterkanji.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spotterkanji.domain.dictionary.ReadingStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the bundled dictionary is actually readable on a device — that the
 * asset extracts, that Room's schema validation accepts a database built by
 * Python, and that a real word resolves.
 *
 * This has to be an instrumented test rather than a JVM one: it needs the real
 * asset, the real SQLite, and Room's real open path.
 */
@RunWith(AndroidJUnit4::class)
class DictionaryReadTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = DictionaryProvider.repository(context)

    @Test
    fun sensei_resolves_with_its_reading_and_meaning() = runBlocking {
        val entries = repository.lookup("先生")

        assertTrue("先生 should be in the dictionary", entries.isNotEmpty())
        val sensei = entries.first()
        assertEquals("せんせい", sensei.reading)
        assertTrue(
            "expected a teacher gloss, got ${sensei.senses.flatMap { it.glosses }}",
            sensei.senses.flatMap { it.glosses }.any { it.contains("teacher") },
        )
    }

    /**
     * The identity rule, exercised on the word that motivated it: 上手 is three
     * different words, not one (D-12), and all three must come back.
     */
    @Test
    fun jouzu_returns_every_reading_not_just_one() = runBlocking {
        val readings = repository.lookup("上手").map { it.reading }.toSet()

        assertTrue(
            "expected じょうず, うわて and かみて — got $readings",
            setOf("じょうず", "うわて", "かみて").all { it in readings },
        )
    }

    /**
     * Unranked entries sort first in SQLite because NULL sorts first, and ~74%
     * of the dictionary is unranked. Getting this wrong buries the common words
     * the app exists to explain, without erroring (V-04).
     *
     * Scoped to the current readings, which is a real narrowing of what this
     * test used to assert. V-21 sorts by status first, so 上手's ranked-but-
     * obsolete じょうしゅ (rank 12, inherited from the writing) now follows the
     * unranked-but-current うわて. Frequency still orders readings a learner
     * might use; it no longer promotes one they should not.
     */
    @Test
    fun common_readings_come_before_unranked_ones() = runBlocking {
        val entries = repository.lookup("上手")
            .filter { it.readingStatus == ReadingStatus.CURRENT }
        val firstUnranked = entries.indexOfFirst { it.frequencyRank == null }
        val lastRanked = entries.indexOfLast { it.frequencyRank != null }

        if (firstUnranked != -1 && lastRanked != -1) {
            assertTrue(
                "unranked entry at $firstUnranked precedes ranked at $lastRanked",
                lastRanked < firstUnranked,
            )
        }
    }

    /**
     * V-21 end to end, on the real dictionary rather than on hand-built rows.
     *
     * 上手 is the case `verification.md` names: じょうて and じょうしゅ are real
     * historical readings that today render exactly like じょうず. The unit tests
     * prove the mapping; this proves the tags actually arrive from the database
     * and survive the repository.
     */
    @Test
    fun jouzu_marks_its_obsolete_readings_and_leads_with_a_current_one() = runBlocking {
        val entries = repository.lookup("上手")
        val byReading = entries.associateBy { it.reading }

        assertEquals(ReadingStatus.CURRENT, byReading.getValue("じょうず").readingStatus)
        assertEquals(ReadingStatus.ARCHAIC, byReading.getValue("じょうて").readingStatus)
        assertEquals(ReadingStatus.ARCHAIC, byReading.getValue("じょうしゅ").readingStatus)

        // The ordering half. Before this change the screen opened on じょうしゅ,
        // because it ties じょうず on frequency and wins on kana order.
        assertEquals("じょうず", entries.first().reading)
        assertTrue(
            "no archaic reading may precede a current one — got ${entries.map { it.reading }}",
            entries.indexOfLast { it.readingStatus == ReadingStatus.CURRENT } <
                entries.indexOfFirst { it.readingStatus == ReadingStatus.ARCHAIC },
        )

        // Inherited from the written form, so the data calls it common (V-04).
        assertTrue(byReading.getValue("じょうしゅ").isCommon)
        assertFalse(
            "an obsolete reading must not be badged common",
            byReading.getValue("じょうしゅ").showsCommonBadge,
        )
    }

    /**
     * D-66's hide half, on one of the commonest words in the language: JMdict
     * carries ちゅうこく for 中国 as a search-only misreading, and it used to
     * render first — above ちゅうごく, badged common.
     */
    @Test
    fun search_only_misreadings_do_not_reach_the_screen() = runBlocking {
        val readings = repository.lookup("中国").map { it.reading }

        assertEquals(listOf("ちゅうごく"), readings)
    }

    /**
     * D-66's never-to-nothing half. 3,143 written forms have no reading but a
     * search-only one; hiding those unconditionally would report a word the
     * dictionary plainly holds as missing, which is D-40's failure arriving
     * from a different direction.
     */
    @Test
    fun a_word_with_only_search_only_readings_still_resolves() = runBlocking {
        val entries = repository.lookup("あっかんべえ")

        assertTrue("あっかんべえ must still resolve", entries.isNotEmpty())
        assertEquals(ReadingStatus.SEARCH_ONLY, entries.first().readingStatus)
    }

    /**
     * V-21's reverse check, and the one most likely to be skipped: a marker
     * applied globally is as wrong as one never applied, and looks just as
     * plausible. 明日 is the sharpest test of it — all three readings carry a
     * `gikun` tag or sit beside one, and none of them is obsolete.
     */
    @Test
    fun words_without_obsolete_readings_are_marked_nowhere() = runBlocking {
        listOf("先生", "明日", "生産").forEach { word ->
            val entries = repository.lookup(word)
            assertTrue(
                "$word should carry no marked reading — got " +
                    entries.map { "${it.reading}:${it.readingStatus}" },
                entries.none { it.readingStatus.isMarked },
            )
        }

        // 明日 あした is gikun and current. Conflating the two would mark the
        // ordinary reading of an everyday word as archaic.
        val ashita = repository.lookup("明日").first { it.reading == "あした" }
        assertTrue("あした should be flagged gikun", ashita.isGikun)
        assertEquals(ReadingStatus.CURRENT, ashita.readingStatus)
    }

    /** A word that is genuinely absent returns empty rather than throwing. */
    @Test
    fun an_unknown_word_returns_nothing() = runBlocking {
        assertEquals(emptyList<Any>(), repository.lookup("架空語"))
    }
}
