package com.spotterkanji.domain.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V-21 in unit-test form, minus the pixels.
 *
 * The failure this guards is silence: an obsolete reading rendering exactly like
 * a current one errors nowhere, looks complete, and teaches kana that has not
 * been used in centuries. None of the logic needs a device, so it is tested
 * here where it runs in milliseconds (D-60).
 */
class ReadingStatusTest {

    private fun entry(reading: String, tags: List<String> = emptyList(), common: Boolean = false) =
        DictionaryEntry(
            text = "上手",
            reading = reading,
            senses = emptyList(),
            isCommon = common,
            readingStatus = ReadingStatus.of(tags),
            isGikun = ReadingStatus.isGikun(tags),
        )

    @Test
    fun `an untagged reading is current and unmarked`() {
        assertEquals(ReadingStatus.CURRENT, ReadingStatus.of(emptyList()))
        assertFalse(ReadingStatus.CURRENT.isMarked)
    }

    @Test
    fun `each re_inf code maps to its own status`() {
        assertEquals(ReadingStatus.ARCHAIC, ReadingStatus.of(listOf("ok")))
        assertEquals(ReadingStatus.IRREGULAR, ReadingStatus.of(listOf("ik")))
        assertEquals(ReadingStatus.RARE, ReadingStatus.of(listOf("rk")))
        assertEquals(ReadingStatus.SEARCH_ONLY, ReadingStatus.of(listOf("sk")))
    }

    /**
     * The trap in the obvious implementation. 明日 あした, 大人 おとな and 海豚
     * いるか all carry `gikun`, and all three are the ordinary current reading —
     * a rule of "tagged means suspect" would label あした archaic.
     */
    @Test
    fun `gikun is not a defect and never marks a reading`() {
        assertEquals(ReadingStatus.CURRENT, ReadingStatus.of(listOf("gikun")))
        assertTrue(ReadingStatus.isGikun(listOf("gikun")))
    }

    /** 15 readings in the dictionary carry both; the archaic half must win. */
    @Test
    fun `gikun combined with ok is archaic and still gikun`() {
        val tags = listOf("gikun", "ok")
        assertEquals(ReadingStatus.ARCHAIC, ReadingStatus.of(tags))
        assertTrue(ReadingStatus.isGikun(tags))
    }

    /** A tag added by a future JMdict release must not crash the app (D-66). */
    @Test
    fun `an unknown code is treated as current`() {
        assertEquals(ReadingStatus.CURRENT, ReadingStatus.of(listOf("wibble")))
        assertEquals(ReadingStatus.ARCHAIC, ReadingStatus.of(listOf("wibble", "ok")))
    }

    /**
     * The bug V-21 actually describes, and it is an ordering bug as much as a
     * labelling one: じょうしゅ and じょうず share 上手's frequency rank, so the
     * query falls back to alphabetical kana and the archaic reading leads.
     */
    @Test
    fun `current readings sort ahead of archaic ones`() {
        val ordered = listOf(
            entry("じょうしゅ", listOf("ok")),
            entry("じょうず"),
            entry("じょうて", listOf("ok")),
            entry("うわて"),
        ).forDisplay()

        assertEquals(listOf("じょうず", "うわて", "じょうしゅ", "じょうて"), ordered.map { it.reading })
    }

    /** Frequency order inside a status survives — the sort has to be stable. */
    @Test
    fun `ordering within a status is left alone`() {
        val ordered = listOf(
            entry("なま"),
            entry("いく"),
            entry("うぶ"),
        ).forDisplay()

        assertEquals(listOf("なま", "いく", "うぶ"), ordered.map { it.reading })
    }

    /** 中国 ちゅうこく is a search-only misreading and must not reach a screen. */
    @Test
    fun `search-only readings are dropped when something else can be shown`() {
        val ordered = listOf(
            entry("ちゅうこく", listOf("sk")),
            entry("ちゅうごく"),
        ).forDisplay()

        assertEquals(listOf("ちゅうごく"), ordered.map { it.reading })
    }

    /**
     * The other half of the rule, and the reason it is not a plain filter: 3,143
     * written forms have nothing but a search-only reading. Dropping theirs
     * would report a word the dictionary holds as missing (D-40, D-66).
     */
    @Test
    fun `a word whose only reading is search-only still renders`() {
        val ordered = listOf(entry("あっかんべえ", listOf("sk"))).forDisplay()

        assertEquals(listOf("あっかんべえ"), ordered.map { it.reading })
        assertEquals(ReadingStatus.SEARCH_ONLY, ordered.single().readingStatus)
    }

    /**
     * V-21's reverse check: a marker applied globally is as wrong as one never
     * applied, and looks just as plausible.
     */
    @Test
    fun `a word with no tagged readings is marked nowhere`() {
        val ordered = listOf(entry("せんせい", common = true), entry("せんしょう")).forDisplay()

        assertTrue(ordered.none { it.readingStatus.isMarked })
        assertTrue(ordered.first().showsCommonBadge)
    }

    /**
     * じょうしゅ inherits 上手's priority through the writing (V-04), so the data
     * calls an obsolete reading common. The badge is suppressed rather than the
     * ingest changed — the rank is still wanted for ranking words.
     */
    @Test
    fun `a marked reading never shows the common badge`() {
        assertFalse(entry("じょうしゅ", listOf("ok"), common = true).showsCommonBadge)
        assertTrue(entry("じょうず", common = true).showsCommonBadge)
    }
}
