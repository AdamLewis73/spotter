package com.spotterkanji.domain.tokenize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V-06's second half, on the JVM.
 *
 * The failure this guards is the quietest in the project: if longest-match never
 * runs, the app still segments, still looks up, and still renders — it simply
 * loses the ability to ask about a word inside a word, which is the pedagogical
 * premise the whole app rests on. Nothing errors and no screen looks wrong.
 */
class LongestMatchTest {

    /** Stand-in for the dictionary, so these stay pure (D-60). */
    private val dictionary = setOf(
        "先生", "先", "生", "生産", "産", "と",
        "東京都", "東京", "京都", "東", "京", "都",
        "選挙管理委員会", "選挙", "管理", "委員会", "委員", "会",
    )

    private fun matches(text: String) = LongestMatch.matchesIn(text, dictionary)

    @Test
    fun candidates_cover_every_substring_up_to_the_cap() {
        val out = LongestMatch.candidates("先生と")

        assertEquals(setOf("先", "先生", "先生と", "生", "生と", "と"), out)
    }

    /** A long line must not produce candidates longer than the cap. */
    @Test
    fun candidates_are_capped() {
        val out = LongestMatch.candidates("あ".repeat(40))

        assertEquals(LongestMatch.MAX_WORD_LENGTH, out.maxOf { it.length })
    }

    @Test
    fun blank_input_asks_the_dictionary_nothing() {
        assertTrue(LongestMatch.candidates("   ").isEmpty())
        assertTrue(LongestMatch.matchesIn("   ", dictionary).isEmpty())
    }

    /**
     * V-06 itself: at position 0 of 先生と生産, longest-match finds 先生 **and**
     * 先. Both halves matter — only the Kuromoji parse means this never ran.
     */
    @Test
    fun the_verification_case_finds_the_compound_and_the_word_inside_it() {
        val atZero = matches("先生と生産").filter { it.start == 0 }

        assertEquals(listOf("先生", "先"), atZero.map { it.text })
    }

    /** Longest first at each position — the ordering the UI relies on. */
    @Test
    fun matches_are_longest_first_within_a_position() {
        val atZero = matches("東京都").filter { it.start == 0 }

        assertEquals(listOf("東京都", "東京", "東"), atZero.map { it.text })
    }

    /**
     * The classic ambiguity, and the clearest argument for running this at all:
     * 東京都 contains 京都 starting one character in, which no single parse of
     * the string will ever mention.
     */
    @Test
    fun a_word_starting_mid_token_is_found() {
        val found = matches("東京都")

        assertTrue(
            "expected 京都 at offset 1, got $found",
            found.any { it.text == "京都" && it.start == 1 },
        )
    }

    /**
     * The compound itself, when Kuromoji chose the parts. 選挙管理委員会 is in
     * the dictionary, and Kuromoji segments it as 選挙 / 管理 / 委員 / 会 — so
     * without this the whole term cannot be asked about at all.
     */
    @Test
    fun a_compound_is_offered_from_one_of_its_parts() {
        val tokens = listOf(
            Token("選挙", 0, 2), Token("管理", 2, 4),
            Token("委員", 4, 6), Token("会", 6, 7),
        )
        val alternates = LongestMatch.alternatesFor(
            tokens.first(), tokens, matches("選挙管理委員会"),
        )

        assertEquals(listOf("選挙管理委員会"), alternates.map { it.text })
    }

    /**
     * The classic ambiguity, end to end. Kuromoji reads 東京都 as 東京 / 都, so
     * both the full place name and the word straddling the boundary are
     * invisible without a second pass.
     */
    @Test
    fun a_containing_word_and_a_crossing_word_are_both_offered() {
        val tokens = listOf(Token("東京", 0, 2), Token("都", 2, 3))
        val alternates = LongestMatch.alternatesFor(tokens.first(), tokens, matches("東京都"))

        assertEquals(listOf("東京都", "京都"), alternates.map { it.text })
    }

    /** A word already on the strip is not repeated as an alternate. */
    @Test
    fun a_word_that_is_its_own_token_is_not_repeated() {
        val tokens = listOf(Token("東京", 0, 2), Token("都", 2, 3))
        val alternates = LongestMatch.alternatesFor(
            tokens.first(), tokens, matches("東京都"), minLength = 1,
        )

        assertTrue("都 is a token of its own", alternates.none { it.text == "都" })
    }

    /**
     * 先生 has no *word* inside it, only its two characters — and those are the
     * component boxes already on screen (D-06). Offering them here would repeat
     * that row with no new destination.
     */
    @Test
    fun single_characters_are_not_offered_as_alternates() {
        val tokens = listOf(Token("先生", 0, 2), Token("と", 2, 3), Token("生産", 3, 5))
        val alternates = LongestMatch.alternatesFor(tokens.first(), tokens, matches("先生と生産"))

        assertTrue("expected no alternates for 先生, got $alternates", alternates.isEmpty())
    }

    /** …but the mechanism still reports them, exactly as V-06 requires. */
    @Test
    fun the_mechanism_still_reports_single_character_matches() {
        val tokens = listOf(Token("先生", 0, 2), Token("と", 2, 3), Token("生産", 3, 5))
        val everything = LongestMatch.alternatesFor(
            tokens.first(), tokens, matches("先生と生産"), minLength = 1,
        )

        assertEquals(listOf("先", "生"), everything.map { it.text })
    }

    /** A word sharing no character with the token is not an alternate of it. */
    @Test
    fun a_word_that_does_not_overlap_is_not_an_alternate() {
        val tokens = listOf(Token("先生", 0, 2), Token("と", 2, 3), Token("生産", 3, 5))
        val alternates = LongestMatch.alternatesFor(
            tokens.first(), tokens, matches("先生と生産"), minLength = 1,
        )

        assertTrue(alternates.none { it.text == "生産" })
        assertTrue(alternates.all { it.start < 2 })
    }
}
