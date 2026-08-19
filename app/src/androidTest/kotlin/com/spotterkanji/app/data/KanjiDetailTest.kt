package com.spotterkanji.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Examples tab is D-04 — the one screen the competitive review found nobody
 * else has — so its query is worth testing against the real dictionary rather
 * than trusting.
 */
@RunWith(AndroidJUnit4::class)
class KanjiDetailTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = DictionaryProvider.repository(context)

    /**
     * The worked example from `overview.md`: 生 carries different sounds and
     * senses depending on the company it keeps, and the screen shows that
     * without authoring a word of explanation.
     */
    @Test
    fun sei_groups_words_by_the_reading_the_kanji_takes() = runBlocking {
        val detail = repository.kanjiDetail("生")!!

        val sei = detail.readingGroups.single { it.reading == "セイ" }
        val words = sei.examples.map { it.text }
        assertTrue(
            "expected common セイ words such as 先生 or 学生 — got $words",
            words.any { it == "先生" || it == "学生" },
        )
        assertTrue("more than one reading group expected", detail.readingGroups.size > 1)
    }

    /**
     * On'yomi before kun'yomi, which is the order every dictionary uses and the
     * order D-04's worked example shows.
     */
    @Test
    fun onyomi_groups_come_before_kunyomi() = runBlocking {
        val types = repository.kanjiDetail("生")!!.readingGroups.mapNotNull { it.type }
        val firstKun = types.indexOf("kun")
        val lastOn = types.lastIndexOf("on")

        if (firstKun != -1 && lastOn != -1) {
            assertTrue("on at $lastOn should precede kun at $firstKun", lastOn < firstKun)
        }
    }

    /**
     * Readings are stored in the script that identifies their type: on'yomi in
     * katakana, kun'yomi in hiragana (D-37). Getting this wrong renders every
     * on'yomi heading in the wrong script without erroring (V-01).
     */
    @Test
    fun reading_scripts_follow_the_dictionary_convention() = runBlocking {
        val detail = repository.kanjiDetail("生")!!

        assertTrue("on'yomi should be katakana", detail.onReadings.any { it == "セイ" })
        assertTrue("kun'yomi should be hiragana", detail.kunReadings.any { it.startsWith("なま") })
    }

    /** D-49: a character that is also a word carries its own senses. */
    @Test
    fun a_kanji_that_is_also_a_word_carries_its_own_senses() = runBlocking {
        val detail = repository.kanjiDetail("生")!!

        assertTrue("生 is a word in its own right", detail.asWord.isNotEmpty())
    }

    /**
     * Not every character is in KANJIDIC2, and a missing one is a real case
     * rather than an error — a saved item must still render when its data goes
     * missing (D-40).
     */
    @Test
    fun an_unknown_character_returns_null() = runBlocking {
        assertNull(repository.kanjiDetail("A"))
    }

    /** No group should be headed by an unresolved reading (D-52). */
    @Test
    fun every_group_has_a_reading_to_be_headed_by() = runBlocking {
        listOf("生", "先", "手").forEach { character ->
            repository.kanjiDetail(character)!!.readingGroups.forEach { group ->
                assertTrue("$character had a blank reading group", group.reading.isNotBlank())
                assertEquals(
                    "$character / ${group.reading} had no examples",
                    true,
                    group.examples.isNotEmpty(),
                )
            }
        }
    }
}
