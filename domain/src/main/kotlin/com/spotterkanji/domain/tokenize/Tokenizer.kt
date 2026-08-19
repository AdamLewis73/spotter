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
    /** Exactly as it appeared in the source text — 生きた, not 生きる. */
    val text: String,
    val start: Int,
    val endExclusive: Int,
    /**
     * The dictionary form, when the surface form is inflected.
     *
     * This is what makes conjugated words findable: the dictionary has 生きる and
     * the sign says 生きた, so looking up the surface form alone returns nothing.
     * Null when the analyser has no base form for the token.
     */
    val baseForm: String? = null,
    /** Kuromoji's coarse part of speech — 名詞, 助詞, 動詞 … */
    val partOfSpeech: String? = null,
) {
    init {
        require(start >= 0) { "start must not be negative, was $start" }
        require(endExclusive > start) {
            "endExclusive ($endExclusive) must be greater than start ($start)"
        }
    }

    val length: Int get() = endExclusive - start

    /**
     * Whether this token is worth offering to a learner.
     *
     * Particles and grammatical markers — the と in 先生と生産 — are real tokens and
     * must keep their place in the offsets, but they gloss badly ("case marking
     * particle") and are not what someone photographing a sign wants explained.
     * `ux.md` makes the same point about an interlinear gloss strip: particles
     * are exactly what makes one confusing to a beginner.
     */
    val isContentWord: Boolean
        get() = partOfSpeech == null ||
            partOfSpeech !in setOf("助詞", "助動詞", "記号", "フィラー", "その他")
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
