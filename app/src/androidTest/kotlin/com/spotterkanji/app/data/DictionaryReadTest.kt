package com.spotterkanji.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
     */
    @Test
    fun common_readings_come_before_unranked_ones() = runBlocking {
        val entries = repository.lookup("上手")
        val firstUnranked = entries.indexOfFirst { it.frequencyRank == null }
        val lastRanked = entries.indexOfLast { it.frequencyRank != null }

        if (firstUnranked != -1 && lastRanked != -1) {
            assertTrue(
                "unranked entry at $firstUnranked precedes ranked at $lastRanked",
                lastRanked < firstUnranked,
            )
        }
    }

    /** A word that is genuinely absent returns empty rather than throwing. */
    @Test
    fun an_unknown_word_returns_nothing() = runBlocking {
        assertEquals(emptyList<Any>(), repository.lookup("架空語"))
    }
}
