package com.spotterkanji.domain.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The direction classifier (V-10).
 *
 * Several cases carry **boxes measured off real images** rather than invented
 * ones, and they are labelled where that is so. The distinction matters: the
 * invented ones say the arithmetic is right, and the measured ones say the rule
 * survives contact with real typesetting — which an earlier draft of this rule
 * did not.
 */
class WritingDirectionTest {

    @Test
    fun a_wide_run_reads_across() {
        val verdict = classifyDirection(TextBox(0, 0, 100, 20), charCount = 5)
        assertEquals(WritingDirection.Horizontal, verdict.direction)
        assertTrue("should be a confident call, was ${verdict.margin}", verdict.isConfident)
    }

    @Test
    fun a_tall_run_reads_down() {
        val verdict = classifyDirection(TextBox(0, 0, 20, 100), charCount = 5)
        assertEquals(WritingDirection.Vertical, verdict.direction)
        assertTrue("should be a confident call, was ${verdict.margin}", verdict.isConfident)
    }

    /**
     * **Measured: 【重要】 from the notice fixture, 214×77 over four characters.**
     *
     * This is the case that killed the previous formulation. Dividing the long
     * axis by the character count gives 0.69 here, well outside the 1.05–1.22
     * band the generated fixtures suggested, so any threshold near 1.0 calls an
     * ordinary heading vertical. The comparison gets it right with room to
     * spare.
     */
    @Test
    fun a_real_heading_reads_across_despite_a_low_ratio() {
        val verdict = classifyDirection(TextBox(274, 45, 488, 122), charCount = 4)
        assertEquals(WritingDirection.Horizontal, verdict.direction)
        assertTrue(verdict.isConfident)
    }

    /** Measured: `ご理解のほどお願い申し上げ` from the same fixture. */
    @Test
    fun a_real_column_reads_down() {
        val verdict = classifyDirection(TextBox(189, 399, 237, 977), charCount = 13)
        assertEquals(WritingDirection.Vertical, verdict.direction)
        assertTrue(verdict.isConfident)
    }

    /**
     * A single glyph is square and carries **no** direction. Measured margins
     * for real one-character elements were 0.00–0.02.
     *
     * Reporting no confidence is the correct answer, not a shortcoming — it is
     * what lets the layout inherit a direction from neighbours instead of
     * committing to a coin flip.
     */
    @Test
    fun a_single_character_is_undecidable() {
        val verdict = classifyDirection(TextBox(0, 0, 21, 19), charCount = 1)
        assertFalse("a lone glyph must not be confident, was ${verdict.margin}", verdict.isConfident)
    }

    @Test
    fun a_degenerate_box_does_not_divide_by_zero() {
        val verdict = classifyDirection(TextBox(5, 5, 5, 5), charCount = 3)
        assertFalse(verdict.isConfident)
    }

    @Test(expected = IllegalArgumentException::class)
    fun an_empty_run_is_rejected() {
        classifyDirection(TextBox(0, 0, 10, 10), charCount = 0)
    }
}
