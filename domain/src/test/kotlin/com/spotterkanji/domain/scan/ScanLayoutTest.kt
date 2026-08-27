package com.spotterkanji.domain.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 4, against layouts written by hand so the right answer is known exactly.
 *
 * This is the reason the package lives in `:domain` (D-76). Every case here is a
 * few lines of arithmetic instead of a photograph, a font and an emulator, which
 * is what makes it affordable to have many of them — and many cheap cases with
 * known answers are the only real defence against a stage whose bugs do not
 * crash but merely put the tap one character out.
 */
class ScanLayoutTest {

    // ---- fixtures --------------------------------------------------------

    /** A run reading left-to-right, its glyphs [glyph] wide and tall. */
    private fun across(text: String, left: Int, top: Int, glyph: Int = 20) = ScanLine(
        listOf(ScanFragment(text, TextBox(left, top, left + glyph * text.length, top + glyph))),
    )

    /** A run reading top-to-bottom. */
    private fun down(text: String, left: Int, top: Int, glyph: Int = 20) = ScanLine(
        listOf(ScanFragment(text, TextBox(left, top, left + glyph, top + glyph * text.length))),
    )

    // ---- reading order (D-75, V-10) --------------------------------------

    /**
     * Columns set at a normal measure — real ones sit 0.3–1.0 glyph widths
     * apart — so they cluster into one block and are ordered within it.
     */
    @Test
    fun vertical_columns_read_right_to_left() {
        val layout = ScanLayout.of(
            listOf(
                down("東京都の学生", left = 100, top = 0),
                down("先生と生産", left = 130, top = 0),
            ),
        )
        assertEquals("先生と生産\n東京都の学生", layout.text)
    }

    /**
     * The case ML Kit gets wrong, and the one the stagger fixture proved is not
     * a position sort: the right column starts above the left and still reads
     * first.
     *
     * The stagger here is proportioned like the measured fixture, where the
     * columns still shared most of their vertical extent. A stagger large enough
     * to separate them entirely is a different situation — see
     * [columns_that_share_no_vertical_extent_read_top_to_bottom].
     */
    @Test
    fun vertical_column_order_ignores_how_the_columns_line_up() {
        val layout = ScanLayout.of(
            listOf(
                down("東京都の学生", left = 100, top = 40),
                down("先生と生産", left = 130, top = 0),
            ),
        )
        assertEquals("先生と生産\n東京都の学生", layout.text)
    }

    @Test
    fun horizontal_lines_read_top_to_bottom() {
        val layout = ScanLayout.of(
            listOf(
                across("東京都の学生", left = 0, top = 100),
                across("先生と生産", left = 0, top = 0),
            ),
        )
        assertEquals("先生と生産\n東京都の学生", layout.text)
    }

    /**
     * A horizontal heading above vertical body — the notice fixture's shape.
     *
     * No single global sort produces this. The heading clears the body
     * vertically, so it forms its own band and leads; the body columns then read
     * right to left within theirs.
     */
    @Test
    fun a_horizontal_heading_leads_the_vertical_body_beneath_it() {
        val layout = ScanLayout.of(
            listOf(
                down("本文です", left = 100, top = 200),
                down("右の列", left = 200, top = 200),
                across("お知らせ", left = 100, top = 0),
            ),
        )
        assertEquals("お知らせ\n右の列\n本文です", layout.text)
    }

    /**
     * Independent texts at the same height are read across, right to left — the
     * shop-lantern case, where each unit is its own block and no single global
     * sort of the fragments would mean anything.
     */
    @Test
    fun separate_columns_in_a_row_read_right_to_left_as_separate_things() {
        val layout = ScanLayout.of(
            listOf(
                down("左の提灯", left = 0, top = 0),
                down("中の提灯", left = 500, top = 0),
                down("右の提灯", left = 1000, top = 0),
            ),
        )
        assertEquals("右の提灯\n中の提灯\n左の提灯", layout.text)
    }

    /**
     * Two columns that share **no** vertical extent are not a spread to be read
     * right-to-left; they are two things, one above the other. Reading order
     * falls back to top-to-bottom.
     *
     * Recorded because it is genuinely ambiguous rather than obviously right —
     * pinned here so the behaviour is a decision rather than an accident.
     */
    @Test
    fun columns_that_share_no_vertical_extent_read_top_to_bottom() {
        val layout = ScanLayout.of(
            listOf(
                down("上の段です", left = 100, top = 0),
                down("下の段です", left = 400, top = 500),
            ),
        )
        assertEquals("上の段です\n下の段です", layout.text)
    }

    /**
     * Input that is nothing but single characters gives the classifier nothing
     * to work with — every glyph is square and every verdict unconfident — so
     * the whole scan falls back to horizontal.
     *
     * Pinned deliberately. It is the one input where the direction is a guess,
     * and horizontal is the right guess because it is overwhelmingly the more
     * common setting.
     */
    @Test
    fun an_all_single_character_scan_falls_back_to_horizontal() {
        val layout = ScanLayout.of(
            listOf(
                down("生", left = 0, top = 0),
                down("先", left = 500, top = 0),
            ),
        )
        assertEquals("生\n先", layout.text)
    }

    // ---- ruby (V-26) -----------------------------------------------------

    @Test
    fun ruby_above_horizontal_text_is_kept_out_of_the_token_stream() {
        val layout = ScanLayout.of(
            listOf(
                across("せんせい", left = 0, top = 30, glyph = 10),
                across("先生です", left = 0, top = 50, glyph = 20),
            ),
        )
        assertEquals("先生です", layout.text)
    }

    @Test
    fun ruby_to_the_right_of_vertical_text_is_kept_out_of_the_token_stream() {
        val layout = ScanLayout.of(
            listOf(
                down("先生です", left = 100, top = 0, glyph = 20),
                down("せんせい", left = 120, top = 0, glyph = 10),
            ),
        )
        assertEquals("先生です", layout.text)
    }

    /**
     * **The case that makes the two-signal rule non-negotiable.**
     *
     * Shrine donor plaques and shop lanterns set company names, prefectures and
     * titles markedly smaller than the main name — *inline, in the same column*.
     * Those are words. A size-only rule reads them as annotation and deletes
     * them from the token stream with no error and no visible symptom.
     *
     * What distinguishes ruby is not that it is small but that it is displaced
     * to the side. This text is collinear, so it stays.
     */
    @Test
    fun small_text_inline_in_the_same_column_is_words_not_ruby() {
        val layout = ScanLayout.of(
            listOf(
                down("東京都", left = 100, top = 0, glyph = 10),
                down("山田太郎", left = 100, top = 40, glyph = 20),
            ),
        )
        assertTrue(
            "the prefecture must survive; it is body text, not ruby. got '${layout.text}'",
            layout.text.contains("東京都"),
        )
        assertTrue(layout.text.contains("山田太郎"))
    }

    @Test
    fun a_tap_on_ruby_resolves_to_the_word_beneath_it() {
        val layout = ScanLayout.of(
            listOf(
                across("せんせい", left = 0, top = 30, glyph = 10),
                across("先生", left = 0, top = 50, glyph = 20),
            ),
        )
        // Over the second half of the ruby, which sits above 生.
        val offset = layout.offsetAt(x = 30, y = 35)
        assertEquals("tapping ruby should fall through to the base", 1, offset)
        assertEquals('生', layout.text[offset!!])
    }

    // ---- line breaks (V-28) ----------------------------------------------

    /**
     * A flowing paragraph: the lines run to the measure, so a word may
     * legitimately continue across the break and no separator is written. Here
     * 生産 spans the first break and must survive.
     *
     * Three lines, not two, because two cannot establish a measure — see
     * [two_full_measure_lines_are_still_kept_apart].
     */
    @Test
    fun lines_that_run_to_the_measure_are_one_flow() {
        val layout = ScanLayout.of(
            listOf(
                across("すべての人間は生", left = 0, top = 0),
                across("産について語りま", left = 0, top = 20),
                across("した", left = 0, top = 40),
            ),
        )
        assertEquals("すべての人間は生産について語りました", layout.text)
    }

    /**
     * Two lines cannot be told apart from a two-line sign, so they are kept
     * apart even when both appear to reach the measure.
     *
     * The wrap test is circular at two lines: the block's far edge comes from
     * its own lines, so the longer one always seems to reach it. V-28 says fail
     * toward missing a word rather than inventing one, and this is that.
     */
    @Test
    fun two_full_measure_lines_are_still_kept_apart() {
        val layout = ScanLayout.of(
            listOf(
                across("先生と生", left = 0, top = 0),
                across("産業のあゆ", left = 0, top = 20),
            ),
        )
        assertEquals("先生と生\n産業のあゆ", layout.text)
    }

    /**
     * A stacked sign: the first line stops well short of the measure, so it
     * ended rather than wrapped. Joining would invent 生産 — a word nobody
     * wrote — which V-28 rates as the worse error.
     */
    @Test
    fun stacked_short_lines_are_separate_things() {
        val layout = ScanLayout.of(
            listOf(
                across("先生", left = 0, top = 0),
                across("産業のあゆみ", left = 0, top = 20),
            ),
        )
        assertEquals("先生\n産業のあゆみ", layout.text)
        assertTrue("must not invent 生産 across the break", !layout.text.contains("生産"))
    }

    @Test
    fun different_blocks_are_always_separated() {
        val layout = ScanLayout.of(
            listOf(
                across("先生", left = 0, top = 0),
                across("産業", left = 0, top = 900),
            ),
        )
        assertEquals("先生\n産業", layout.text)
    }

    // ---- tap resolution (V-11) -------------------------------------------

    /**
     * V-11 itself: on 先生と生産, tapping 産 must give 生産 — not 先生, not と.
     * Checked at every character, because an off-by-one here is systematically
     * wrong while looking merely flaky.
     */
    @Test
    fun every_character_of_a_horizontal_run_resolves_to_itself() {
        val text = "先生と生産"
        val layout = ScanLayout.of(listOf(across(text, left = 100, top = 50, glyph = 20)))

        for (index in text.indices) {
            val centreX = 100 + index * 20 + 10
            val offset = layout.offsetAt(centreX, 60)
            assertEquals("character $index ('${text[index]}')", index, offset)
        }
    }

    @Test
    fun every_character_of_a_vertical_run_resolves_to_itself() {
        val text = "先生と生産"
        val layout = ScanLayout.of(listOf(down(text, left = 100, top = 50, glyph = 20)))

        for (index in text.indices) {
            val centreY = 50 + index * 20 + 10
            val offset = layout.offsetAt(110, centreY)
            assertEquals("character $index ('${text[index]}')", index, offset)
        }
    }

    @Test
    fun a_tap_on_bare_image_resolves_to_nothing() {
        val layout = ScanLayout.of(listOf(across("先生", left = 100, top = 50)))
        assertNull(layout.offsetAt(5000, 5000))
    }

    // ---- invariants ------------------------------------------------------

    /**
     * The bookkeeping invariant everything downstream rests on: a placement's
     * offset must address its own character. An off-by-one does not throw and
     * does not look wrong — it silently shifts every tap by one.
     */
    @Test
    fun every_placement_addresses_its_own_character() {
        val layout = ScanLayout.of(
            listOf(
                down("東京都の学生", left = 100, top = 0),
                down("先生と生産", left = 200, top = 0),
                across("お知らせ", left = 100, top = 400),
            ),
        )
        assertTrue(layout.placements.isNotEmpty())
        for (placement in layout.placements) {
            assertEquals(
                "offset ${placement.offset} should hold '${placement.char}'",
                placement.char,
                layout.text[placement.offset],
            )
        }
    }

    /**
     * Separators own an offset and no rectangle, deliberately. A lookup table
     * assuming every offset has a box is wrong at exactly these positions.
     */
    @Test
    fun separator_offsets_have_no_rectangle() {
        val layout = ScanLayout.of(
            listOf(
                across("先生", left = 0, top = 0),
                across("産業のあゆみ", left = 0, top = 20),
            ),
        )
        val separator = layout.text.indexOf('\n')
        assertTrue("expected a separator in '${layout.text}'", separator >= 0)
        assertNull(layout.boxAt(separator))
    }

    @Test
    fun a_line_split_into_several_fragments_stays_one_line() {
        // Ruby routinely fragments the column it annotates, so this is the
        // normal case rather than an edge case.
        val line = ScanLine(
            listOf(
                ScanFragment("先生", TextBox(0, 0, 40, 20)),
                ScanFragment("と生産", TextBox(40, 0, 100, 20)),
            ),
        )
        val layout = ScanLayout.of(listOf(line))
        assertEquals("先生と生産", layout.text)
        assertEquals(5, layout.placements.size)
        assertEquals(4, layout.offsetAt(90, 10))
    }

    @Test
    fun no_lines_lays_out_to_nothing() {
        assertTrue(ScanLayout.of(emptyList()).isEmpty)
    }
}
