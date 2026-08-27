package com.spotterkanji.domain.scan

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The whole layout, against **rectangles measured off a real photograph**.
 *
 * Every box below is what ML Kit actually returned for a museum closure notice:
 * a horizontal two-line heading above eight vertical body columns, recognized
 * in the wrong order (D-75) and with the heading dropped into the middle of it.
 * Nothing here is invented, including the recognition errors — 台風 came back as
 * 合風, 閲覧室 as 関質室, 休館 as 休節. They are kept verbatim because the point
 * of this test is the geometry, and cleaning up ML Kit's output would make it a
 * test of a photograph we did not take.
 *
 * This is the case the hand-written fixtures cannot be: real measure, real
 * ragged column ends, real heading. It exercises every stage at once — grouping,
 * classification, block ordering, column ordering and the line-break policy —
 * and the expected value is simply *the notice as a person reads it*.
 *
 * The photograph itself is third-party and is not in the repository (see
 * `.gitignore`). These nine rectangles are what survives of it, which is all
 * that was ever needed.
 */
class RealNoticeLayoutTest {

    private fun line(text: String, l: Int, t: Int, r: Int, b: Int) =
        ScanLine(listOf(ScanFragment(text, TextBox(l, t, r, b))))

    /** In ML Kit's own (wrong) order, exactly as it was returned. */
    private val asRecognized = listOf(
        line("ます。", 58, 189, 82, 240),
        line("ご理解のほどお願い申し上げ", 90, 189, 111, 458),
        line("【重要】", 143, 26, 219, 54),
        line("お知らせ", 129, 61, 233, 87),
        line("(木)より開幕いたします。", 120, 191, 141, 431),
        line("企画展『「怖い」本』6月4日", 147, 190, 172, 469),
        line("ます。", 181, 190, 202, 237),
        line("アム·関質室を臨時休節いたし", 210, 190, 233, 475),
        line("明日6月3日(水)はミュージ", 241, 191, 264, 463),
        line("合風6号核近の影響により、", 271, 190, 295, 456),
    )

    /**
     * The heading leads, the body reads right to left, and the columns join into
     * sentences.
     *
     * The sentence breaks are the part worth staring at, because nothing in this
     * test knows what a sentence is. Both `ます。` columns stop far short of the
     * measure — they are where the text ran out, not where it wrapped — so the
     * line-break rule separates there and joins everywhere else (V-28). The
     * result is the notice broken into exactly its three sentences, purely from
     * where the ink stops.
     */
    @Test
    fun the_notice_reads_as_a_person_would_read_it() {
        val layout = ScanLayout.of(asRecognized)

        assertEquals(
            listOf(
                "【重要】",
                "お知らせ",
                "合風6号核近の影響により、明日6月3日(水)はミュージアム·関質室を臨時休節いたします。",
                "企画展『「怖い」本』6月4日(木)より開幕いたします。",
                "ご理解のほどお願い申し上げます。",
            ).joinToString("\n"),
            layout.text,
        )
    }

    /**
     * A tap in the top-right column must land in the first body sentence, not
     * in whatever ML Kit happened to emit first.
     *
     * Without the reordering this resolves into `ます。` — the *last* thing on
     * the notice — while looking entirely plausible.
     */
    @Test
    fun a_tap_on_the_first_column_resolves_into_the_first_sentence() {
        val layout = ScanLayout.of(asRecognized)

        // Near the top of the rightmost body column: 合風6号…
        val offset = layout.offsetAt(x = 283, y = 200)!!
        val sentence = layout.text.split("\n").first { it.contains("合風") }

        assertEquals('合', layout.text[offset])
        assertEquals(0, sentence.indexOf(layout.text[offset]))
    }

    @Test
    fun every_placement_addresses_its_own_character() {
        val layout = ScanLayout.of(asRecognized)
        for (placement in layout.placements) {
            assertEquals(placement.char, layout.text[placement.offset])
        }
    }
}
