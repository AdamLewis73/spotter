package com.spotterkanji.domain.tokenize

/**
 * A word located within a run of Japanese text.
 *
 * Positions are **character offsets into the source text**, not pixels. Nothing
 * in `:domain` knows anything about the screen; mapping offsets to on-screen
 * rectangles is the overlay's job (Phase 5, `architecture.md` stage 4).
 *
 * [endExclusive] follows the usual half-open convention, so `先生` in
 * `先生と生産` is `start = 0, endExclusive = 2`.
 */
data class Token(
    val text: String,
    val start: Int,
    val endExclusive: Int,
) {
    init {
        require(start >= 0) { "start must not be negative, was $start" }
        require(endExclusive > start) {
            "endExclusive ($endExclusive) must be greater than start ($start)"
        }
    }

    val length: Int get() = endExclusive - start
}

/**
 * Splits Japanese text into words.
 *
 * Japanese is written without spaces, so this is the technically central problem
 * of the app rather than a detail. The interface lives here, in `:domain`, so
 * that the implementation can be swapped without touching callers (D-08) — v1
 * uses Kuromoji, which is JVM-only and therefore the one part of the pipeline
 * that will not port to iOS.
 */
interface Tokenizer {
    fun tokenize(text: String): List<Token>
}
