package com.spotterkanji.domain.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-68, on the JVM: readings that mean the same thing collapse into one block,
 * and none of them goes missing on the way.
 *
 * The failure worth guarding is not a crash but a disappearance — a merge rule
 * that drops a reading, or one that swallows a *marking*, leaves a screen that
 * looks tidier and says less than it should (V-21).
 */
class MergedReadingTest {

    private fun entry(
        reading: String,
        glosses: List<List<String>>,
        status: ReadingStatus = ReadingStatus.CURRENT,
        common: Boolean = false,
    ) = DictionaryEntry(
        text = "先生",
        reading = reading,
        senses = glosses.map { Sense(it) },
        isCommon = common,
        readingStatus = status,
    )

    private val teacher = listOf(listOf("teacher", "instructor", "master"))
    private val teacherAndPast = listOf(
        listOf("teacher", "instructor", "master"),
        listOf("previous existence"),
    )

    /** The case that prompted it: 先生 renders five readings as three blocks. */
    @Test
    fun readings_with_identical_meanings_share_one_block() {
        val merged = listOf(
            entry("せんせい", teacher, common = true),
            entry("せんしょう", teacherAndPast),
            entry("せんじょう", teacherAndPast),
            entry("ぜんじょう", teacherAndPast),
            entry("シーサン", listOf(listOf("man", "boy"))),
        ).mergedByMeaning()

        assertEquals(3, merged.size)
        assertEquals(listOf("せんせい"), merged[0].entries.map { it.reading })
        assertEquals(
            listOf("せんしょう", "せんじょう", "ぜんじょう"),
            merged[1].entries.map { it.reading },
        )
        assertEquals(listOf("シーサン"), merged[2].entries.map { it.reading })
    }

    /** Nothing may vanish. Five readings in, five readings out. */
    @Test
    fun merging_never_loses_a_reading() {
        val entries = listOf(
            entry("せんせい", teacher),
            entry("せんしょう", teacherAndPast),
            entry("せんじょう", teacherAndPast),
            entry("ぜんじょう", teacherAndPast),
            entry("シーサン", listOf(listOf("man", "boy"))),
        )
        val out = entries.mergedByMeaning().flatMap { it.entries }

        assertEquals(entries.map { it.reading }, out.map { it.reading })
    }

    /** Different meanings stay apart, however similar they look. */
    @Test
    fun readings_that_differ_at_all_are_not_merged() {
        val merged = listOf(
            entry("うわて", listOf(listOf("upper part"), listOf("dexterity"))),
            entry("かみて", listOf(listOf("upper part"), listOf("stage left"))),
        ).mergedByMeaning()

        assertEquals(2, merged.size)
    }

    /**
     * 上手: a current reading and two archaic ones that mean exactly the same
     * thing. They share a line, the current one leads and keeps the badge, and
     * every archaic reading keeps its own marking.
     */
    @Test
    fun a_current_reading_may_lead_a_group_holding_archaic_ones() {
        val merged = listOf(
            entry("じょうず", teacher, common = true),
            entry("じょうしゅ", teacher, ReadingStatus.ARCHAIC, common = true),
            entry("じょうて", teacher, ReadingStatus.ARCHAIC, common = true),
        ).mergedByMeaning()

        assertEquals(1, merged.size)
        val group = merged.single()
        assertEquals("じょうず", group.primary.reading)
        assertTrue(group.primary.showsCommonBadge)
        assertEquals(2, group.entries.count { it.readingStatus.isMarked })
        // The group as a whole is NOT dimmed — it is じょうず's line (D-68).
        assertFalse(group.allMarked)
    }

    /** A group of nothing but marked readings still steps back as a whole. */
    @Test
    fun a_group_of_only_marked_readings_is_marked() {
        val merged = listOf(
            entry("じょうしゅ", teacher, ReadingStatus.ARCHAIC),
            entry("じょうて", teacher, ReadingStatus.ARCHAIC),
        ).mergedByMeaning()

        assertTrue(merged.single().allMarked)
    }

    /**
     * Meanings are the key, not the part of speech. Splitting on a tag the
     * screen barely shows would leave the repetition it exists to remove.
     */
    @Test
    fun a_differing_part_of_speech_does_not_split_a_group() {
        val merged = listOf(
            DictionaryEntry("x", "あ", listOf(Sense(listOf("run"), partsOfSpeech = listOf("v1")))),
            DictionaryEntry("x", "い", listOf(Sense(listOf("run"), partsOfSpeech = listOf("n")))),
        ).mergedByMeaning()

        assertEquals(1, merged.size)
    }

    /** The senses shown are the group's, and there is exactly one copy of them. */
    @Test
    fun the_group_reports_one_set_of_senses() {
        val merged = listOf(
            entry("せんしょう", teacherAndPast),
            entry("せんじょう", teacherAndPast),
        ).mergedByMeaning().single()

        assertEquals(2, merged.senses.size)
        assertEquals(listOf("teacher", "instructor", "master"), merged.senses.first().glosses)
    }
}
