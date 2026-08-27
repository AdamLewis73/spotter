package com.spotterkanji.domain.scan

/**
 * A rectangle in **image pixel coordinates** — the coordinate space of the
 * captured photograph, never of the screen.
 *
 * This exists so the scan geometry can live in `:domain` (D-76). ML Kit hands
 * back `android.graphics.Rect`, but everything this package does with a
 * rectangle is arithmetic, and typing it as an Android class would strand that
 * arithmetic in `:app` where testing it costs an emulator round trip. `:app`
 * converts once, at the recognizer boundary.
 *
 * Converting image pixels to screen pixels is a separate job and deliberately
 * not here: it depends on how the frozen frame is laid out, which is a `:app`
 * concern and a different source of bugs (`phase-04-camera.md` flags the
 * `ContentScale.Crop` interaction).
 *
 * Coordinates follow the screen convention — y increases **downwards** — so
 * [top] is numerically smaller than [bottom].
 */
data class TextBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right >= left) { "right ($right) must not be left of left ($left)" }
        require(bottom >= top) { "bottom ($bottom) must not be above top ($top)" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom

    /**
     * How far this box and [other] overlap along x, in pixels. Negative when
     * they are disjoint, and then its magnitude is the gap between them.
     *
     * Signed rather than clamped at zero on purpose: callers here care about
     * both "do these share a column" and "how far apart are they", and a
     * clamped version silently answers only the first.
     */
    fun overlapX(other: TextBox): Int = minOf(right, other.right) - maxOf(left, other.left)

    /** As [overlapX], along y. */
    fun overlapY(other: TextBox): Int = minOf(bottom, other.bottom) - maxOf(top, other.top)

    fun union(other: TextBox) = TextBox(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )

    companion object {
        /** The smallest box containing all of [boxes]. Null when empty. */
        fun union(boxes: Iterable<TextBox>): TextBox? =
            boxes.reduceOrNull { acc, box -> acc.union(box) }
    }
}
