package com.spotterkanji.domain.scan

/**
 * One recognized fragment — an ML Kit `Element`, stripped of Android types.
 *
 * **Fragment boundaries are not word boundaries.** ML Kit does not know where
 * Japanese words begin and end; that is the tokenizer's job. Fragments are
 * useful for their positions, not their segmentation.
 */
data class ScanFragment(
    val text: String,
    val box: TextBox,
) {
    init {
        require(text.isNotEmpty()) { "a fragment with no text has no position to describe" }
    }
}

/**
 * A run of fragments the recognizer grouped together — an ML Kit `Line`.
 *
 * **In vertical text a "line" is a column**, running top to bottom. The name
 * follows ML Kit's, not typography's.
 *
 * Taking lines as input rather than a flat list of fragments is the load-bearing
 * decision in this package, and it is evidence-led. ML Kit's *grouping* was
 * measured to be sound — columns hold together, and each is read top-to-bottom
 * correctly — while only the *order* of the groups is wrong (D-75). So this
 * package sorts what the recognizer grouped; it does not attempt to rebuild
 * grouping from bare rectangles, which is a far harder problem that the
 * measurements said we do not have.
 *
 * One measured caveat it must tolerate: a line can arrive **split across several
 * fragments**, and ruby routinely causes exactly that (V-26). Nothing here may
 * assume one fragment per line.
 */
data class ScanLine(
    val fragments: List<ScanFragment>,
) {
    init {
        require(fragments.isNotEmpty()) { "a line needs at least one fragment" }
    }

    val text: String = fragments.joinToString("") { it.text }

    val box: TextBox = fragments.map { it.box }.reduce { acc, b -> acc.union(b) }

    val charCount: Int get() = text.length

    /**
     * The extent of a single glyph across the line — height for horizontal text,
     * width for vertical.
     *
     * This is the size signal ruby detection compares against (V-26). It is the
     * *cross-axis* measure because that is the one that does not grow with the
     * number of characters, which makes it comparable between a two-character
     * annotation and the twenty-character line it annotates.
     */
    fun glyphSize(direction: WritingDirection): Int = when (direction) {
        WritingDirection.Horizontal -> box.height
        WritingDirection.Vertical -> box.width
    }
}
