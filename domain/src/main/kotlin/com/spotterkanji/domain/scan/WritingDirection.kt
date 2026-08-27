package com.spotterkanji.domain.scan

import kotlin.math.abs

/**
 * Which way a run of Japanese text reads.
 *
 * [Vertical] is 縦書き — top to bottom, and **columns run right to left**, which
 * is the part that gets forgotten and the part D-75 exists for.
 */
enum class WritingDirection { Horizontal, Vertical }

/**
 * A direction, and how close the call was.
 *
 * [margin] is the gap between the two competing scores. It is not decoration:
 * measured across every fixture available, the rule never produced a confident
 * wrong answer, and every case where it *was* unreliable reported a small
 * margin. So the margin is what lets the classifier say **"I don't know"**
 * rather than guessing — which matters, because a single character genuinely
 * carries no direction at all and scores ~0.00.
 */
data class DirectionVerdict(
    val direction: WritingDirection,
    val margin: Double,
) {
    val isConfident: Boolean get() = margin >= CONFIDENT_MARGIN

    companion object {
        /**
         * Below this, treat the verdict as unusable and inherit from neighbours.
         *
         * Measured: unreliable cases clustered at 0.00–0.26, and every reliable
         * multi-character run scored well above 1.0. 0.5 sits in the empty space
         * between, and no measured case fell near it.
         */
        const val CONFIDENT_MARGIN = 0.5
    }
}

/**
 * Decides whether a box holding [charCount] characters reads across or down.
 *
 * The rule: for *n* characters, a horizontal run is about *n* times as wide as
 * it is tall, and a vertical run about *n* times as tall as it is wide. So score
 * both and take the nearer.
 *
 * **State it as a comparison, never as a threshold**, and this is a correction
 * paid for in a wrong answer. An earlier draft quoted "long-axis ratio ÷ n lands
 * in 1.05–1.22", measured off generated fixtures — but generated text is too
 * uniform to be evidence. Real typesetting spans 0.69–1.01, with the perfectly
 * ordinary heading 【重要】 at the bottom of that range, so a threshold anywhere
 * near 1.0 misclassifies it. The comparison has no constant in it and no such
 * failure.
 *
 * CJK glyphs being uniformly square is what makes this work at all. Runs
 * containing half-width digits or Latin letters distort both scores; they
 * degrade toward a small margin rather than toward a confident error, which is
 * the failure mode to prefer.
 */
fun classifyDirection(box: TextBox, charCount: Int): DirectionVerdict {
    require(charCount > 0) { "cannot classify a run of no characters" }

    // A zero-extent box has no shape to read. Report no confidence rather than
    // dividing by zero, and let the caller inherit a direction from neighbours.
    if (box.width == 0 || box.height == 0) {
        return DirectionVerdict(WritingDirection.Horizontal, margin = 0.0)
    }

    val n = charCount.toDouble()
    val horizontalError = abs(box.width.toDouble() / box.height - n)
    val verticalError = abs(box.height.toDouble() / box.width - n)

    val direction =
        if (horizontalError < verticalError) WritingDirection.Horizontal
        else WritingDirection.Vertical

    return DirectionVerdict(direction, margin = abs(horizontalError - verticalError))
}
