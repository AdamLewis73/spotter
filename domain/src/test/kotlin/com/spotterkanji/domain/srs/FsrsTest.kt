package com.spotterkanji.domain.srs

import com.spotterkanji.domain.srs.Fsrs.Companion.DAY_MS
import com.spotterkanji.domain.srs.Fsrs.Companion.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The port is checked against py-fsrs's own test suite (v6.3.2,
 * tests/test_basic.py): where that suite states a number, the same number is
 * asserted here, so a mistranscribed formula fails rather than scheduling
 * plausibly and wrongly. A scheduler bug costs a learner months of misplaced
 * study and never shows as an error (D-26).
 */
class FsrsTest {

    /** 2022-11-29 12:30 UTC, py-fsrs's reference start. */
    private val start = 1_669_725_000_000L

    private val ratings1 = listOf(
        Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD,
        Rating.AGAIN, Rating.AGAIN,
        Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD,
    )

    /** py-fsrs test_review_card: the canonical interval history. */
    @Test
    fun `interval history matches the reference`() {
        val fsrs = Fsrs(enableFuzzing = false)
        var card = SrsCard.new(start)
        var at = start
        val intervals = ratings1.map { rating ->
            card = fsrs.review(card, rating, at)
            at = card.dueMs
            (card.dueMs - card.lastReviewMs!!).floorDiv(DAY_MS)
        }
        assertEquals(listOf(0L, 2, 11, 46, 163, 498, 0, 0, 2, 4, 7, 12, 21), intervals)
    }

    /** py-fsrs test_repeated_correct_reviews: Easy, ten times, bottoms difficulty out. */
    @Test
    fun `repeated easy answers take difficulty to its floor`() {
        val fsrs = Fsrs(enableFuzzing = false)
        var card = SrsCard.new(start)
        for (i in 0 until 10) card = fsrs.review(card, Rating.EASY, start + i)
        assertEquals(1.0, card.difficulty!!, 0.0)
    }

    /**
     * py-fsrs test_memo_state: the memory model itself, to four decimal places.
     * Fuzz is on, as in the reference — it moves due dates, never stability or
     * difficulty.
     */
    @Test
    fun `stability and difficulty match the reference`() {
        val fsrs = Fsrs()
        val ratings = listOf(Rating.AGAIN, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD)
        val gaps = listOf(0, 0, 1, 3, 8, 21)
        var card = SrsCard.new(start)
        var at = start
        ratings.zip(gaps).forEachIndexed { i, (rating, gap) ->
            at += gap * DAY_MS
            card = fsrs.review(card, rating, at, fuzzSeed = i.toLong())
        }
        assertEquals(53.62691, card.stability!!, 1e-4)
        assertEquals(6.3574867, card.difficulty!!, 1e-4)
    }

    // ---- Learning steps (1 and 10 minutes by default)

    @Test
    fun `good on a new card moves to the second learning step`() {
        val card = Fsrs().review(SrsCard.new(start), Rating.GOOD, start)
        assertEquals(SrsState.LEARNING, card.state)
        assertEquals(1, card.step)
        assertEquals(10 * MINUTE_MS, card.dueMs - start)
    }

    @Test
    fun `good on the last learning step graduates to review`() {
        val fsrs = Fsrs()
        var card = fsrs.review(SrsCard.new(start), Rating.GOOD, start)
        card = fsrs.review(card, Rating.GOOD, card.dueMs)
        assertEquals(SrsState.REVIEW, card.state)
        assertNull(card.step)
        assertTrue(card.dueMs - start >= DAY_MS)
    }

    @Test
    fun `again on a new card repeats the first step`() {
        val card = Fsrs().review(SrsCard.new(start), Rating.AGAIN, start)
        assertEquals(SrsState.LEARNING, card.state)
        assertEquals(0, card.step)
        assertEquals(1 * MINUTE_MS, card.dueMs - start)
    }

    /** py-fsrs test_hard_learning_steps: halfway between the first two steps, 5.5 minutes. */
    @Test
    fun `hard on a new card waits halfway between the first two steps`() {
        val card = Fsrs().review(SrsCard.new(start), Rating.HARD, start)
        assertEquals(0, card.step)
        assertEquals(330_000L, card.dueMs - start)
    }

    @Test
    fun `easy on a new card skips learning`() {
        val card = Fsrs().review(SrsCard.new(start), Rating.EASY, start)
        assertEquals(SrsState.REVIEW, card.state)
        assertNull(card.step)
        assertTrue(card.dueMs - start >= DAY_MS)
    }

    @Test
    fun `hard with one learning step waits one and a half steps`() {
        val fsrs = Fsrs(learningSteps = listOf(10 * MINUTE_MS))
        val card = fsrs.review(SrsCard.new(start), Rating.HARD, start)
        assertEquals(SrsState.LEARNING, card.state)
        assertEquals(15 * MINUTE_MS, card.dueMs - start)
    }

    @Test
    fun `hard on the second learning step repeats that step`() {
        val fsrs = Fsrs(learningSteps = listOf(1 * MINUTE_MS, 10 * MINUTE_MS))
        var card = fsrs.review(SrsCard.new(start), Rating.GOOD, start)
        val before = card.dueMs
        card = fsrs.review(card, Rating.HARD, before)
        assertEquals(1, card.step)
        assertEquals(10 * MINUTE_MS, card.dueMs - before)
    }

    // ---- Review and relearning

    @Test
    fun `again on a review card sends it to relearning for ten minutes`() {
        val fsrs = Fsrs(enableFuzzing = false)
        var card = fsrs.review(SrsCard.new(start), Rating.GOOD, start)
        card = fsrs.review(card, Rating.GOOD, card.dueMs)
        card = fsrs.review(card, Rating.GOOD, card.dueMs)
        assertEquals(SrsState.REVIEW, card.state)
        val before = card.dueMs
        card = fsrs.review(card, Rating.AGAIN, before)
        assertEquals(SrsState.RELEARNING, card.state)
        assertEquals(0, card.step)
        assertEquals(10 * MINUTE_MS, card.dueMs - before)

        card = fsrs.review(card, Rating.AGAIN, card.dueMs)
        assertEquals(SrsState.RELEARNING, card.state)

        val relearnDue = card.dueMs
        card = fsrs.review(card, Rating.GOOD, relearnDue)
        assertEquals(SrsState.REVIEW, card.state)
        assertTrue(card.dueMs - relearnDue >= DAY_MS)
    }

    @Test
    fun `hard on relearning with two steps behaves like learning`() {
        val fsrs = Fsrs(relearningSteps = listOf(1 * MINUTE_MS, 10 * MINUTE_MS))
        var card = fsrs.review(SrsCard.new(start), Rating.EASY, start)
        card = fsrs.review(card, Rating.AGAIN, card.dueMs)
        var before = card.dueMs
        card = fsrs.review(card, Rating.HARD, before)
        assertEquals(0, card.step)
        assertEquals(330_000L, card.dueMs - before)

        card = fsrs.review(card, Rating.GOOD, card.dueMs)
        assertEquals(1, card.step)
        before = card.dueMs
        card = fsrs.review(card, Rating.HARD, before)
        assertEquals(1, card.step)
        assertEquals(10 * MINUTE_MS, card.dueMs - before)

        card = fsrs.review(card, Rating.EASY, before)
        assertEquals(SrsState.REVIEW, card.state)
    }

    /** py-fsrs test_long_term_stability_learning_state: a relearning answer a day late. */
    @Test
    fun `a late relearning answer uses the long-term model`() {
        val fsrs = Fsrs()
        var card = fsrs.review(SrsCard.new(start), Rating.EASY, start)
        card = fsrs.review(card, Rating.AGAIN, card.dueMs)
        assertEquals(SrsState.RELEARNING, card.state)
        card = fsrs.review(card, Rating.GOOD, card.dueMs + DAY_MS)
        assertEquals(SrsState.REVIEW, card.state)
    }

    @Test
    fun `with no learning steps a new card goes straight to review`() {
        val card = Fsrs(learningSteps = emptyList()).review(SrsCard.new(start), Rating.AGAIN, start)
        assertEquals(SrsState.REVIEW, card.state)
        assertTrue(card.dueMs - start >= DAY_MS)
    }

    @Test
    fun `with no relearning steps a lapse stays in review`() {
        val fsrs = Fsrs(relearningSteps = emptyList())
        var card = fsrs.review(SrsCard.new(start), Rating.GOOD, start)
        card = fsrs.review(card, Rating.GOOD, card.dueMs)
        card = fsrs.review(card, Rating.AGAIN, card.dueMs)
        assertEquals(SrsState.REVIEW, card.state)
        assertTrue(card.dueMs - card.lastReviewMs!! >= DAY_MS)
    }

    // ---- Bounds

    @Test
    fun `no interval exceeds the maximum`() {
        val fsrs = Fsrs(maximumInterval = 100)
        var card = SrsCard.new(start)
        for (rating in listOf(Rating.EASY, Rating.GOOD, Rating.EASY, Rating.GOOD)) {
            card = fsrs.review(card, rating, card.dueMs)
            assertTrue((card.dueMs - card.lastReviewMs!!).floorDiv(DAY_MS) <= 100)
        }
    }

    @Test
    fun `stability never falls below its floor`() {
        val fsrs = Fsrs()
        var card = SrsCard.new(start)
        repeat(1000) { i ->
            card = fsrs.review(card, Rating.AGAIN, card.dueMs + DAY_MS, fuzzSeed = i.toLong())
            assertTrue(card.stability!! >= Fsrs.STABILITY_MIN)
        }
    }

    @Test
    fun `a same-day hard answer does not lower stability`() {
        val fsrs = Fsrs(learningSteps = emptyList())
        val at = 1_767_225_600_000L // 2026-01-01 UTC
        val first = fsrs.review(SrsCard.new(at), Rating.GOOD, at)
        val second = fsrs.review(first, Rating.HARD, at + MINUTE_MS)
        assertEquals(first.stability!!, second.stability!!, 0.0)
    }

    @Test
    fun `parameters are validated`() {
        assertThrows(IllegalArgumentException::class.java) { Fsrs(parameters = List(20) { 1.0 }) }
        val bad = Fsrs.DEFAULT_PARAMETERS.toMutableList().also { it[4] = 11.0 }
        assertThrows(IllegalArgumentException::class.java) { Fsrs(parameters = bad) }
    }

    // ---- Retrievability

    @Test
    fun `retrievability is zero for a new card and a probability after`() {
        val fsrs = Fsrs()
        assertEquals(0.0, fsrs.retrievability(SrsCard.new(start), start), 0.0)
        var card = fsrs.review(SrsCard.new(start), Rating.GOOD, start)
        card = fsrs.review(card, Rating.GOOD, card.dueMs)
        val r = fsrs.retrievability(card, card.dueMs)
        assertTrue(r in 0.0..1.0)
        // Due date is set for the desired retention, 0.9.
        assertEquals(0.9, r, 0.05)
    }

    // ---- Spotter's own requirements

    /** D-107: the app's two outcomes map onto FSRS's failed and successful recall. */
    @Test
    fun `right is good and wrong is again`() {
        assertEquals(Rating.GOOD, Rating.of(passed = true))
        assertEquals(Rating.AGAIN, Rating.of(passed = false))
    }

    /** D-112: the same review with the same seed always lands on the same day. */
    @Test
    fun `fuzz is deterministic for a given seed`() {
        val fsrs = Fsrs()
        val graduated = graduate(fsrs)
        val a = fsrs.review(graduated, Rating.GOOD, graduated.dueMs, fuzzSeed = 42)
        val b = fsrs.review(graduated, Rating.GOOD, graduated.dueMs, fuzzSeed = 42)
        assertEquals(a, b)
    }

    /** Fuzz spreads due dates, and only within FSRS's range around the true interval. */
    @Test
    fun `fuzz stays near the unfuzzed interval and does vary`() {
        val plain = Fsrs(enableFuzzing = false)
        val fuzzy = Fsrs()
        val graduated = graduate(plain)
        val exact = plain.review(graduated, Rating.GOOD, graduated.dueMs)
        val exactDays = (exact.dueMs - exact.lastReviewMs!!).floorDiv(DAY_MS)
        assertTrue("needs a long enough interval to fuzz", exactDays >= 3)

        // FSRS's spread, worked out independently of the code under test: one
        // day, plus 15% of the interval between 2.5 and 7 days, 10% between 7
        // and 20, and 5% beyond. At 46 days that is ±4.275.
        val d = exactDays.toDouble()
        val spread = 1.0 +
            0.15 * (minOf(d, 7.0) - 2.5).coerceAtLeast(0.0) +
            0.10 * (minOf(d, 20.0) - 7.0).coerceAtLeast(0.0) +
            0.05 * (d - 20.0).coerceAtLeast(0.0)
        val range = kotlin.math.round(d - spread).toLong()..kotlin.math.round(d + spread).toLong()

        val fuzzed = (0L until 50L).map { seed ->
            val c = fuzzy.review(graduated, Rating.GOOD, graduated.dueMs, fuzzSeed = seed)
            (c.dueMs - c.lastReviewMs!!).floorDiv(DAY_MS)
        }
        fuzzed.forEach { assertTrue("$it days is outside $range around $exactDays", it in range) }
        assertTrue("fuzz never varied", fuzzed.toSet().size > 1)
    }

    /** D-112: the schedule is a cache — replaying the log rebuilds it exactly. */
    @Test
    fun `replaying the answers rebuilds the same card`() {
        val fsrs = Fsrs()
        val answers = mutableListOf<Answer>()
        var card = SrsCard.new(start)
        ratings1.forEachIndexed { i, rating ->
            val answer = Answer(rating, card.dueMs, fuzzSeed = 1000L + i)
            answers += answer
            card = fsrs.review(card, answer.rating, answer.atMs, answer.fuzzSeed)
        }
        assertEquals(card, fsrs.replay(start, answers))
        assertEquals(card, fsrs.replay(start, answers.shuffled(kotlin.random.Random(7))))
    }

    @Test
    fun `replaying nothing gives a new card`() {
        val card = Fsrs().replay(start, emptyList())
        assertEquals(SrsCard.new(start), card)
        assertFalse(card.lastReviewMs != null)
    }

    /** A card answered Good twice from new, so it sits in REVIEW. */
    private fun graduate(fsrs: Fsrs): SrsCard {
        var card = fsrs.review(SrsCard.new(start), Rating.GOOD, start)
        card = fsrs.review(card, Rating.GOOD, card.dueMs)
        card = fsrs.review(card, Rating.GOOD, card.dueMs, fuzzSeed = 1)
        check(card.state == SrsState.REVIEW)
        return card
    }
}
