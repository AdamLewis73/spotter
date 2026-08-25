package com.spotterkanji.app.scan

import android.graphics.Rect

/**
 * Stage 2's output: the text ML Kit read, plus where each piece of it sat in the
 * photograph (`architecture.md`).
 *
 * ML Kit hands back a tree — `Text` → `TextBlock` → `Line` → `Element` — and the
 * tokenizer in stage 3 wants a single string with character offsets. Flattening
 * that tree therefore *defines* the offsets, which is why [elements] records
 * where each one landed rather than throwing the positions away and recovering
 * them later. Two things fall out of doing it here:
 *
 *  - The concatenation happens **once**. Stage 4 (Phase 5) has to map character
 *    offsets back to pixels, and if it re-walked the tree itself the two walks
 *    could disagree — silently, and in a way that shifts every tap by a
 *    character.
 *  - Nothing in this file interpolates *within* an element, orders columns for
 *    vertical text, or separates furigana. Those are stage 4 and stay in Phase 5
 *    (V-10, V-11, V-26). This is the raw material for that work, not a start on
 *    it.
 *
 * **`Element` boundaries are not word boundaries.** ML Kit does not know where
 * Japanese words begin and end — that is exactly what stage 3 is for. Elements
 * are useful for their positions, not their segmentation.
 */
internal data class RecognizedText(
    /** Every element in reading order, lines separated by [LINE_SEPARATOR]. */
    val text: String,
    val elements: List<RecognizedElement>,
) {
    val isEmpty: Boolean get() = text.isBlank()

    internal companion object {
        /**
         * Lines are joined with a newline, not butted together.
         *
         * This is a correctness choice, not formatting. A shop sign reading 先生
         * on one line and 産業 on the next would concatenate to `先生産業`, and
         * the tokenizer would happily find 生産 — a word spanning a line break
         * that nobody wrote. Kuromoji treats a newline as a boundary, so the
         * separator prevents the invented compound.
         *
         * **This is a conservative default, not the answer — V-28.** Japanese
         * does not hyphenate: 禁則処理 constrains only which characters may begin
         * or end a line, so a word may equally split *across* a break with no
         * marker at all, and this separator hides those. Both directions fail
         * silently and no fixed separator escapes both. The choice between them
         * is deliberate: **inventing a word is worse than missing one**, because
         * a missed word means the learner taps 生 and gets 生, while an invented
         * one means a confident, plausible, wrong answer in an app whose whole
         * claim is meaning in context (D-44).
         *
         * Deciding it properly is geometric and needs stage 4 — the same signal
         * V-10 uses to find columns and V-26 uses to separate ruby. Phase 5.
         *
         * It costs one character offset that belongs to no element. That is
         * deliberate and stage 4 must expect it: a tap can never land on a
         * separator, because no rectangle maps to one.
         */
        const val LINE_SEPARATOR = "\n"

        val EMPTY = RecognizedText(text = "", elements = emptyList())
    }
}

/**
 * One ML Kit element, and the slice of [RecognizedText.text] it produced.
 *
 * [box] is in **image pixel coordinates** — the coordinate space of the captured
 * bitmap, not of the screen. Converting between the two is stage 4's job and
 * depends on how the frame is displayed, which is why it is not done here.
 */
internal data class RecognizedElement(
    val text: String,
    val box: Rect,
    /** Index into [RecognizedText.text] where this element's text begins. */
    val startOffset: Int,
) {
    val endOffset: Int get() = startOffset + text.length
}
