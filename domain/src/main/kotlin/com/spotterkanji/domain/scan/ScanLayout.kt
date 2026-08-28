package com.spotterkanji.domain.scan

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where one character sits in the photograph.
 *
 * [offset] indexes [ScanLayout.text], which is what the tokenizer works in, so
 * this type is the whole bridge between stage 3's offsets and stage 2's pixels
 * (`architecture.md` stage 4).
 */
data class CharacterPlacement(
    val offset: Int,
    val char: Char,
    val box: TextBox,
    val direction: WritingDirection,
)

/**
 * The scan, laid out: text in true reading order, and a rectangle per character.
 *
 * This is stage 4. It takes what the recognizer grouped and answers the four
 * geometric questions the roadmap insists are one question, because none can be
 * settled from the text alone and each constrains the others:
 *
 *  1. **Which way does this read?** (V-10) — [classifyDirection], per line, with
 *     unconfident lines inheriting from their neighbours.
 *  2. **What order do the groups go in?** (D-75) — 縦書き columns run right to
 *     left, and ML Kit emits them left to right, so the order is imposed here.
 *  3. **Is this small text an annotation or is it words?** (V-26) — ruby leaves
 *     the token stream but keeps its rectangles, so a tap on it still resolves.
 *  4. **Is this line a continuation or a separate thing?** (V-28) — decided from
 *     whether the line above ran to the measure, not from a fixed separator.
 *
 * **Offsets exist that own no rectangle.** Separators occupy a position in
 * [text] and map to nothing, by design — a tap can never land on one. Anything
 * assuming every offset has a box is wrong at exactly those positions.
 */
class ScanLayout private constructor(
    val text: String,
    val placements: List<CharacterPlacement>,
    private val rubySlots: List<RubySlot>,
) {
    private val byOffset: Map<Int, CharacterPlacement> = placements.associateBy { it.offset }

    val isEmpty: Boolean get() = text.isEmpty()

    /** The rectangle for one character, or null if that offset is a separator. */
    fun boxAt(offset: Int): TextBox? = byOffset[offset]?.box

    /**
     * The rectangle covering a range of offsets — the box of a whole word.
     *
     * **This is what D-22 asks for.** A saved word records where it sat in the
     * photograph, so moving to word crops later needs no image reprocessing at
     * all; without it, the upgrade means re-running OCR across every saved
     * image. The box costs four integers and is already known here, which is the
     * whole argument: capture cheap metadata now even when unused.
     *
     * Nothing stores it yet — there is no user-data schema until Phase 6 — so
     * this exists to make sure the Phase 5 side of that bargain is actually
     * held. Offsets owning no rectangle (separators) are skipped, and a range
     * covering none returns null.
     *
     * *One caveat for the caller:* where V-28 joined two lines into one flow, a
     * word can span them, and the union is then a rectangle enclosing both plus
     * the gap. A superset of the word rather than a tight crop — rare, and
     * harmless for the purpose, but not a promise of tightness.
     */
    fun boxFor(offsets: IntRange): TextBox? =
        TextBox.union(offsets.mapNotNull { byOffset[it]?.box })

    /**
     * Which character was tapped, in image pixel coordinates.
     *
     * Returns null for a tap on bare image — the caller decides whether that
     * dismisses a selection or does nothing.
     *
     * A tap on **ruby** resolves to the base text beneath it rather than to
     * nothing. Ruby is excluded from [text] (V-26), but it is still ink on the
     * photograph and a learner who taps せんせい plainly means 先生; returning
     * nothing there would read as the overlay being broken.
     */
    fun offsetAt(x: Int, y: Int): Int? {
        placements.firstOrNull { it.box.contains(x, y) }?.let { return it.offset }

        val slot = rubySlots.firstOrNull { run -> run.boxes.any { it.contains(x, y) } }
            ?: return null
        return slot.nearestBaseOffset(x, y)
    }

    /**
     * Ruby rectangles, and the base characters they annotate.
     *
     * Held so a tap on an annotation can fall through to the word it describes.
     */
    internal class RubySlot(
        val boxes: List<TextBox>,
        private val base: List<CharacterPlacement>,
        private val direction: WritingDirection,
    ) {
        /**
         * The base character nearest the tap **along the reading axis**.
         *
         * Only the reading axis is compared, because the cross-axis distance is
         * whatever the ruby offset happens to be and says nothing about which
         * character was meant. Tapping above the third glyph means the third
         * glyph.
         */
        fun nearestBaseOffset(x: Int, y: Int): Int? {
            if (base.isEmpty()) return null
            val target = if (direction == WritingDirection.Horizontal) x else y
            return base.minByOrNull {
                val centre = if (direction == WritingDirection.Horizontal) {
                    (it.box.left + it.box.right) / 2
                } else {
                    (it.box.top + it.box.bottom) / 2
                }
                abs(centre - target)
            }?.offset
        }
    }

    companion object {
        /**
         * Separator written between runs that are **not** one flow.
         *
         * Kuromoji treats a newline as a boundary, which is what stops a word
         * being invented across a gap that nobody wrote (V-28).
         */
        const val SEPARATOR = "\n"

        /**
         * Ruby is at most this fraction of the size of the text it annotates.
         *
         * Measured at 0.5–0.72 on real typesetting. 0.75 leaves headroom without
         * reaching ordinary small body text, which sat far closer to parity.
         */
        private const val RUBY_MAX_SIZE_RATIO = 0.75

        /**
         * How far two lines may sit apart, in glyphs, and still be one block.
         *
         * Japanese inter-column leading (行間) runs about 0.5–1.0 em for body
         * text and up to roughly 1.5 for an airy setting; measured real columns
         * sat at 0.33–0.94. Independent objects — separate signs, lanterns,
         * donor plaques — sit further apart than that, and the distance is the
         * only thing distinguishing them, because a row of equal-length signs
         * and a justified paragraph are otherwise geometrically identical.
         *
         * So this constant is doing more work than it looks. Set too wide, rows
         * of adjacent signs merge into one block, all appear to reach the same
         * measure, and get joined into invented words — the failure V-28 rates
         * as the worse one. 1.5 sits at the top of the typographic range and
         * below the object-separation range.
         */
        private const val BLOCK_GAP_GLYPHS = 1.5

        /**
         * How near a line must come to the block's far edge to count as having
         * *wrapped* rather than *ended*, in glyphs.
         */
        private const val FULL_MEASURE_GLYPHS = 1.5

        /**
         * A block needs this many lines before its lines may be joined at all.
         *
         * Below it the wrap test is a tautology, not evidence. The block's far
         * edge is derived from its own lines, so in a two-line block the longer
         * line *defines* the measure and therefore always appears to reach it —
         * which would join every two-line shop sign into one run and invent
         * words across the break.
         *
         * Three lines is the point where a consistent measure becomes real
         * evidence: a paragraph has every line but the last ending at the same
         * edge, while a stack of separate items ends raggedly. Below that the
         * honest answer is that it cannot be told, and V-28 is explicit about
         * which way to fail — a missed word is degraded but true, an invented
         * one is confidently wrong.
         *
         * The cost is a genuine two-line paragraph losing a word that split
         * across its break. Accepted, and rare next to two-line signage.
         */
        private const val MIN_FLOW_LINES = 3

        val EMPTY = ScanLayout("", emptyList(), emptyList())

        /**
         * Lay out what the recognizer produced.
         *
         * [lines] arrive in the recognizer's own order, which is not trusted for
         * reading order (D-75) but *is* trusted for grouping, because grouping
         * was measured to be sound while only the sequence was wrong.
         */
        fun of(lines: List<ScanLine>): ScanLayout {
            if (lines.isEmpty()) return EMPTY

            val classified = classify(lines)
            val (ruby, body) = separateRuby(classified)
            if (body.isEmpty()) return EMPTY

            return emit(orderBlocks(blockify(body)), ruby)
        }

        // ---- 1. Direction ------------------------------------------------

        private fun classify(lines: List<ScanLine>): List<Placed> {
            val verdicts = lines.map { classifyDirection(it.box, it.charCount) }

            // Unconfident lines inherit the prevailing direction rather than
            // guessing. A single character is the common case and scores ~0.00:
            // a lone square glyph genuinely carries no direction.
            val prevailing = verdicts
                .filter { it.isConfident }
                .groupingBy { it.direction }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?: WritingDirection.Horizontal

            return lines.zip(verdicts) { line, verdict ->
                Placed(
                    line = line,
                    direction = if (verdict.isConfident) verdict.direction else prevailing,
                    inherited = !verdict.isConfident,
                )
            }
        }

        // ---- 2. Ruby -----------------------------------------------------

        /**
         * Split annotation from body text (V-26).
         *
         * **Two signals must agree**, and this is not belt-and-braces — it is
         * the finding that a size-only rule deletes real words. Shop lanterns
         * and shrine donor plaques set company names and prefectures markedly
         * smaller than the main name, *inline in the same column*. Those are
         * words. What makes ruby different is not that it is small but that it
         * is **displaced to the side**, so the positional test is the one doing
         * the real work and the size test merely narrows the field.
         */
        private fun separateRuby(lines: List<Placed>): Pair<Map<Placed, Placed>, List<Placed>> {
            val ruby = mutableMapOf<Placed, Placed>()
            for (candidate in lines) {
                val base = lines.firstOrNull { it !== candidate && candidate.isRubyOf(it) }
                if (base != null) ruby[candidate] = base
            }
            // A line annotating something cannot itself be annotated: without
            // this, two similarly-sized neighbours can each claim the other.
            ruby.keys.removeAll { it in ruby.values }
            return ruby to lines.filter { it !in ruby }
        }

        private fun Placed.isRubyOf(base: Placed): Boolean {
            if (direction != base.direction) return false

            val mySize = line.glyphSize(direction)
            val baseSize = base.line.glyphSize(base.direction)
            if (baseSize == 0) return false
            if (mySize > baseSize * RUBY_MAX_SIZE_RATIO) return false

            // Allow a little encroachment; printed ruby often just touches.
            val slack = baseSize / 4
            return when (direction) {
                // Above the base, sharing its horizontal extent.
                WritingDirection.Horizontal ->
                    line.box.bottom <= base.line.box.top + slack &&
                        base.line.box.top - line.box.bottom < baseSize &&
                        line.box.overlapX(base.line.box) > line.box.width / 2

                // To the right of the base, sharing its vertical extent. This is
                // the test that rejects small text sitting *inside* a column.
                WritingDirection.Vertical ->
                    line.box.left >= base.line.box.right - slack &&
                        line.box.left - base.line.box.right < baseSize &&
                        line.box.overlapY(base.line.box) > line.box.height / 2
            }
        }

        // ---- 3. Blocks ---------------------------------------------------

        /**
         * Cluster lines into blocks of adjacent, parallel text.
         *
         * Blocks matter for two reasons the measurements forced: ordering must
         * be per-block, because a horizontal heading above vertical body cannot
         * be sequenced by any single global sort; and the line-break question is
         * only meaningful *within* a block, since two separate signs are never
         * one flow.
         */
        private fun blockify(lines: List<Placed>): List<Block> {
            val remaining = lines.toMutableList()
            val blocks = mutableListOf<Block>()

            while (remaining.isNotEmpty()) {
                val seed = remaining.removeAt(0)
                val group = mutableListOf(seed)
                var grew = true
                while (grew) {
                    grew = false
                    val it = remaining.iterator()
                    while (it.hasNext()) {
                        val candidate = it.next()
                        if (group.any { member -> member.adjoins(candidate) }) {
                            group += candidate
                            it.remove()
                            grew = true
                        }
                    }
                }
                blocks += Block(group.first().direction, group)
            }
            return blocks
        }

        private fun Placed.adjoins(other: Placed): Boolean {
            if (direction != other.direction) return false
            val reach = maxOf(
                line.glyphSize(direction),
                other.line.glyphSize(other.direction),
            ) * BLOCK_GAP_GLYPHS

            return when (direction) {
                // Columns stand side by side: they share vertical extent, and
                // the gap between them is horizontal.
                WritingDirection.Vertical ->
                    line.box.overlapY(other.line.box) > 0 &&
                        gap(line.box.overlapX(other.line.box)) <= reach

                WritingDirection.Horizontal ->
                    line.box.overlapX(other.line.box) > 0 &&
                        gap(line.box.overlapY(other.line.box)) <= reach
            }
        }

        private fun gap(overlap: Int): Double = if (overlap >= 0) 0.0 else -overlap.toDouble()

        // ---- 4. Order ----------------------------------------------------

        /**
         * Order blocks into bands, top to bottom, then across each band.
         *
         * Banding by **vertical overlap** rather than by a pixel tolerance keeps
         * this scale-free: a heading that clears the body entirely forms its own
         * band, while a row of separate signs at the same height shares one and
         * is read across. Within a band, vertical text runs right to left.
         */
        private fun orderBlocks(blocks: List<Block>): List<Block> {
            // Bands are built explicitly rather than expressed as a comparator.
            // "Shares vertical extent with" is not transitive, so using it to
            // compare pairs is not a total order — which Java's sort is entitled
            // to notice and throw over once the list is long enough. Grouping
            // first and sorting within each group avoids the question.
            val bands = mutableListOf<MutableList<Block>>()
            for (block in blocks.sortedBy { it.box.top }) {
                val current = bands.lastOrNull()
                if (current != null && current.any { it.box.overlapY(block.box) > 0 }) {
                    current += block
                } else {
                    bands += mutableListOf(block)
                }
            }

            return bands.flatMap { band ->
                // A band of vertical text reads right to left. Mixed bands are
                // rare; the majority direction decides, and either answer is
                // defensible when a band genuinely mixes both.
                val vertical = band.count { it.direction == WritingDirection.Vertical } * 2 > band.size
                if (vertical) band.sortedByDescending { it.box.right }
                else band.sortedBy { it.box.left }
            }
        }

        private fun Block.orderedLines(): List<Placed> = when (direction) {
            WritingDirection.Vertical -> lines.sortedByDescending { it.line.box.right }
            WritingDirection.Horizontal -> lines.sortedBy { it.line.box.top }
        }

        // ---- 5. Emit -----------------------------------------------------

        private fun emit(blocks: List<Block>, ruby: Map<Placed, Placed>): ScanLayout {
            val builder = StringBuilder()
            val placements = mutableListOf<CharacterPlacement>()
            val baseOffsets = mutableMapOf<Placed, MutableList<CharacterPlacement>>()

            for ((blockIndex, block) in blocks.withIndex()) {
                val ordered = block.orderedLines()
                for ((lineIndex, placed) in ordered.withIndex()) {
                    val needsSeparator = when {
                        blockIndex == 0 && lineIndex == 0 -> false
                        // Different blocks are never one flow.
                        lineIndex == 0 -> true
                        // Too few lines to establish a measure — see the
                        // constant; the wrap test cannot be trusted here.
                        ordered.size < MIN_FLOW_LINES -> true
                        else -> !block.wrapped(ordered[lineIndex - 1])
                    }
                    if (needsSeparator) builder.append(SEPARATOR)

                    val mine = mutableListOf<CharacterPlacement>()
                    for (fragment in placed.line.fragments) {
                        val start = builder.length
                        builder.append(fragment.text)
                        mine += interpolate(fragment, start, placed.direction)
                    }
                    placements += mine
                    baseOffsets[placed] = mine
                }
            }

            val slots = ruby.mapNotNull { (annotation, base) ->
                val under = baseOffsets[base] ?: return@mapNotNull null
                RubySlot(
                    boxes = annotation.line.fragments.map { it.box },
                    base = under,
                    direction = base.direction,
                )
            }

            return ScanLayout(builder.toString(), placements, slots)
        }

        /**
         * Did [previous] wrap, or did it end?
         *
         * **Japanese does not hyphenate** (V-28), so nothing in the text says
         * which. The geometry does: a line that runs to the block's far edge was
         * broken by the measure and its words continue on the next line, while
         * one that stops short ended because the writer stopped. That reads the
         * evidence of how the text was actually set rather than guessing.
         *
         * Where it is unclear, the answer is **separate**. Inventing a word is
         * worse than missing one: a missed word means the learner taps 生 and
         * gets 生, degraded but true, while an invented one is a confident,
         * plausible, wrong answer in an app whose whole claim is meaning in
         * context (D-44).
         */
        private fun Block.wrapped(previous: Placed): Boolean {
            val glyph = previous.line.glyphSize(direction)
            if (glyph == 0) return false
            val slack = glyph * FULL_MEASURE_GLYPHS
            return when (direction) {
                WritingDirection.Horizontal -> box.right - previous.line.box.right <= slack
                WritingDirection.Vertical -> box.bottom - previous.line.box.bottom <= slack
            }
        }

        /**
         * Rectangles for each character of one fragment, by linear interpolation.
         *
         * Unusually accurate for Japanese, because CJK glyphs are **uniformly
         * wide by design** — unlike Latin, where an `i` and a `W` differ
         * enormously. `architecture.md` calls that property worth relying on.
         */
        private fun interpolate(
            fragment: ScanFragment,
            startOffset: Int,
            direction: WritingDirection,
        ): List<CharacterPlacement> {
            val n = fragment.text.length
            val box = fragment.box
            return fragment.text.mapIndexed { index, char ->
                val from = index.toDouble() / n
                val to = (index + 1).toDouble() / n
                val slice = when (direction) {
                    WritingDirection.Horizontal -> TextBox(
                        left = box.left + (box.width * from).roundToInt(),
                        top = box.top,
                        right = box.left + (box.width * to).roundToInt(),
                        bottom = box.bottom,
                    )
                    WritingDirection.Vertical -> TextBox(
                        left = box.left,
                        top = box.top + (box.height * from).roundToInt(),
                        right = box.right,
                        bottom = box.top + (box.height * to).roundToInt(),
                    )
                }
                CharacterPlacement(startOffset + index, char, slice, direction)
            }
        }

        // ---- internals ---------------------------------------------------

        private class Placed(
            val line: ScanLine,
            val direction: WritingDirection,
            /** True when the direction came from neighbours, not from shape. */
            val inherited: Boolean,
        )

        private class Block(val direction: WritingDirection, val lines: List<Placed>) {
            val box: TextBox = lines.map { it.line.box }.reduce { a, b -> a.union(b) }
        }
    }
}
