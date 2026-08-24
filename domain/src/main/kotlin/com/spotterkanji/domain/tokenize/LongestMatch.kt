package com.spotterkanji.domain.tokenize

/**
 * A dictionary word found inside a run of text, at a known position.
 *
 * Offsets follow [Token]'s convention — character offsets into the source, not
 * pixels — so a match and a token can be compared directly.
 */
data class WordMatch(
    val text: String,
    val start: Int,
    val endExclusive: Int,
) {
    val length: Int get() = endExclusive - start
}

/**
 * The second half of D-07: every dictionary word inside a run of text, not just
 * the one parse Kuromoji chose.
 *
 * Kuromoji returns exactly one segmentation. That is the right default and it is
 * grammatically informed, but the app's entire pedagogical premise is that a run
 * of characters *contains overlapping words* — that 先生 contains 先, and that a
 * learner should be able to ask about either (V-06). One parse cannot express
 * that, so this runs a second pass over the same text against the dictionary the
 * app already ships. It is the technique Yomichan, 10ten and Rikaichan use.
 *
 * **Split in two on purpose.** [candidates] and [matchesIn] are pure functions
 * over strings and a set, so they live in `:domain` and unit-test in
 * milliseconds with no database and no emulator (D-60). The only part that needs
 * the dictionary is "which of these strings exist", which is one batched query
 * in `:data`.
 *
 * **Why not a text search.** Every lookup is an exact equality on `word.text`,
 * which the `UNIQUE (text, reading)` index already serves — as `schema.sql`
 * notes, that settles the FTS5 question: longest-match needs N indexed equality
 * lookups, and full-text search buys nothing.
 */
object LongestMatch {

    /**
     * The longest substring worth asking the dictionary about.
     *
     * JMdict holds entries far longer than this — whole proverbs — but they are
     * not what a scanner meets, and every extra character multiplies the
     * candidate count by the length of the input. Twelve covers ordinary
     * compounds with room to spare (選挙管理委員会 is seven).
     */
    const val MAX_WORD_LENGTH = 12

    /**
     * Every substring that could be a word: `s[i..i+1]` through `s[i..i+n]` at
     * each position.
     *
     * Returned as a **set** because the caller turns it straight into one
     * `WHERE text IN (…)` query, and 日々 would otherwise ask about 日 twice.
     * Single characters are included — 先 inside 先生 is exactly the case V-06
     * exists to protect.
     */
    fun candidates(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        val out = LinkedHashSet<String>()
        for (start in text.indices) {
            val last = minOf(start + MAX_WORD_LENGTH, text.length)
            for (end in (start + 1)..last) {
                out.add(text.substring(start, end))
            }
        }
        return out
    }

    /**
     * Locate [known] words inside [text], longest first at each position.
     *
     * [known] is the subset of [candidates] the dictionary actually holds. The
     * ordering is the contract the UI depends on: for any start position the
     * longest match is the primary candidate and the shorter ones are its
     * alternates (D-07).
     */
    fun matchesIn(text: String, known: Set<String>): List<WordMatch> {
        if (text.isBlank() || known.isEmpty()) return emptyList()
        val out = mutableListOf<WordMatch>()
        for (start in text.indices) {
            val last = minOf(start + MAX_WORD_LENGTH, text.length)
            // Longest first, so the primary candidate leads its position without
            // the caller having to sort.
            for (end in last downTo (start + 1)) {
                val candidate = text.substring(start, end)
                if (candidate in known) {
                    out.add(WordMatch(candidate, start, end))
                }
            }
        }
        return out
    }

    /**
     * Words a learner cannot otherwise reach from [token].
     *
     * This is the whole point of running a second pass (D-07), and the shape of
     * it only became clear on a device. Kuromoji commits to one parse, and the
     * words it hides fall into three relations to the token it chose:
     *
     * | Input | Kuromoji | Unreachable without this |
     * |---|---|---|
     * | 東京都 | 東京 / 都 | **東京都** (contains the token) and **京都** (crosses the boundary) |
     * | 選挙管理委員会 | 選挙 / 管理 / 委員 / 会 | **選挙管理委員会** (the compound itself) |
     * | 立入禁止 | 立入禁止 | 立入, 禁止 (inside the token) |
     *
     * So the rule is **overlap**, not containment: any dictionary word sharing a
     * character with the token, that is not the token and is not already a token
     * of its own. 都 is on the strip in its own right and is not repeated here.
     *
     * Note this subsumes the multi-granularity split `roadmap.md` expected to
     * need Sudachi for, in both directions — the compound from its parts and the
     * parts from the compound — whichever way Kuromoji happened to cut.
     *
     * **Single characters are filtered out by default, and the mechanism still
     * finds them.** [matchesIn] returns 先 inside 先生 exactly as V-06 requires;
     * this is a presentation rule on top. Every single-character alternate is
     * already a component box below (D-06), and a lone kanji routes to the same
     * screen from either (D-49), so listing them here would repeat that row with
     * no new destination. 先生 therefore offers no alternates, which is correct:
     * no *word* hides inside it, only its two characters.
     */
    fun alternatesFor(
        token: Token,
        tokens: List<Token>,
        matches: List<WordMatch>,
        minLength: Int = 2,
    ): List<WordMatch> {
        val onStrip = tokens.map { it.start to it.endExclusive }.toSet()
        return matches.filter { match ->
            val overlaps = match.start < token.endExclusive &&
                match.endExclusive > token.start
            val isTheToken = match.start == token.start &&
                match.endExclusive == token.endExclusive
            overlaps &&
                !isTheToken &&
                match.length >= minLength &&
                (match.start to match.endExclusive) !in onStrip
        }
    }
}
