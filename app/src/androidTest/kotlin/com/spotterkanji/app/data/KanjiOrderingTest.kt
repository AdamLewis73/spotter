package com.spotterkanji.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ordering and deduplication on the Examples tab.
 *
 * Both of these were wrong when the screen was first looked at, and neither
 * threw: the groups came out in an arbitrary order and each was padded with
 * alternative spellings of the same word. A screen whose entire job is to make a
 * pattern visible fails quietly when it is merely cluttered.
 */
@RunWith(AndroidJUnit4::class)
class KanjiOrderingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = DictionaryProvider.repository(context)

    /**
     * セイ carries 先生 and 学生; ショウ carries 一生. The commoner reading has to
     * lead, or the first thing a learner sees is the rarer pattern.
     */
    @Test
    fun the_reading_with_the_commonest_words_leads() = runBlocking {
        val readings = repository.kanjiDetail("生")!!.readingGroups.map { it.reading }

        val sei = readings.indexOf("セイ")
        val shou = readings.indexOf("ショウ")
        assertTrue("both readings should be present — got $readings", sei >= 0 && shou >= 0)
        assertTrue("セイ ($sei) should precede ショウ ($shou)", sei < shou)
    }

    /**
     * 一生けんめい, 一生けん命 and 一生懸命 are one word written three ways, and 学生
     * has the pre-reform 學生 beside it. Showing each is accurate and useless.
     */
    @Test
    fun alternative_spellings_of_one_word_appear_once() = runBlocking {
        repository.kanjiDetail("生")!!.readingGroups.forEach { group ->
            val readings = group.examples.map { it.reading }
            assertEquals(
                "${group.reading} repeated a reading: $readings",
                readings.size,
                readings.distinct().size,
            )
        }
    }

    /** The cap keeps a group scannable — 生's い group holds well over a hundred words. */
    @Test
    fun a_group_never_grows_past_the_cap() = runBlocking {
        repository.kanjiDetail("生")!!.readingGroups.forEach { group ->
            assertTrue(
                "${group.reading} had ${group.examples.size} examples",
                group.examples.size <= 8,
            )
        }
    }
}
