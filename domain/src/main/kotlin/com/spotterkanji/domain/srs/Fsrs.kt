/*
 * Ported from py-fsrs v6.3.2 (commit 9446cb06605c), fsrs/scheduler.py:
 * https://github.com/open-spaced-repetition/py-fsrs
 *
 * MIT License
 *
 * Copyright (c) 2022 Open Spaced Repetition
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.spotterkanji.domain.srs

import kotlin.math.E
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.random.Random

/**
 * The FSRS scheduler (D-26): given a card and an answer, when is it due next?
 *
 * A line-for-line port of py-fsrs, the reference implementation, so its own
 * test values hold here (`FsrsTest`). Pure — no clock, no storage, no Android —
 * which is what lets the schedule be rebuilt by replaying the review log
 * (D-112): the same answers at the same times always produce the same card.
 *
 * Four deliberate differences from the Python. Only the last moves a due date,
 * and only within the spread fuzz already allows:
 *
 * - **Times are epoch milliseconds, UTC**, not `datetime` (D-112). A day is
 *   86,400,000 ms, and "whole days since" floors, as Python's
 *   `timedelta.days` does.
 * - **Fuzz is seeded per review** ([review]'s `fuzzSeed`) rather than drawn
 *   from a global random source. py-fsrs re-randomises on every replay; that
 *   would make the schedule a learner sees depend on when it was last rebuilt.
 * - **Rounding is half-to-even**, `kotlin.math.round`, matching Python's
 *   `round()`. `roundToInt()` rounds halves up and would put some intervals a
 *   day out.
 * - **A fuzzed interval is floored, as in ts-fsrs**, the TypeScript reference.
 *   py-fsrs rounds it, which lets one draw in a range land a day past the
 *   range's top — contrary to its own comment. Found by `FsrsTest`.
 *
 * Spotter's answers are right or wrong (D-107), so the app only ever passes
 * [Rating.AGAIN] or [Rating.GOOD] — see [Rating.of]. The other two are kept
 * because the algorithm is defined over four, and a port that dropped them
 * could not be checked against the reference.
 */
class Fsrs(
    val parameters: List<Double> = DEFAULT_PARAMETERS,
    val desiredRetention: Double = 0.9,
    val learningSteps: List<Long> = listOf(1 * MINUTE_MS, 10 * MINUTE_MS),
    val relearningSteps: List<Long> = listOf(10 * MINUTE_MS),
    val maximumInterval: Int = 36_500,
    val enableFuzzing: Boolean = true,
) {
    private val decay: Double
    private val factor: Double

    init {
        require(parameters.size == LOWER_BOUNDS.size) {
            "Expected ${LOWER_BOUNDS.size} parameters, got ${parameters.size}."
        }
        val outOfBounds = parameters.indices.filterNot { i ->
            parameters[i] in LOWER_BOUNDS[i]..UPPER_BOUNDS[i]
        }
        require(outOfBounds.isEmpty()) {
            "Parameters out of bounds: " + outOfBounds.joinToString { i ->
                "parameters[$i] = ${parameters[i]} not in (${LOWER_BOUNDS[i]}, ${UPPER_BOUNDS[i]})"
            }
        }
        decay = -parameters[20]
        factor = 0.9.pow(1 / decay) - 1
    }

    /**
     * The predicted probability of recalling [card] at [atMs]. Zero for a card
     * never reviewed.
     */
    fun retrievability(card: SrsCard, atMs: Long): Double {
        val last = card.lastReviewMs ?: return 0.0
        val stability = card.stability ?: return 0.0
        val elapsedDays = max(0L, (atMs - last).floorDiv(DAY_MS))
        return (1 + factor * elapsedDays / stability).pow(decay)
    }

    /**
     * The card after answering it with [rating] at [atMs].
     *
     * [fuzzSeed] decides the small random spread FSRS adds to longer intervals,
     * so cards learned together do not all fall due on the same day. Pass the
     * same seed for the same review and the result is the same — the app derives
     * it from the review itself so replaying the log reproduces it (D-112).
     */
    fun review(card: SrsCard, rating: Rating, atMs: Long, fuzzSeed: Long = 0L): SrsCard {
        val daysSinceLastReview = card.lastReviewMs?.let { (atMs - it).floorDiv(DAY_MS) }
        val sameDay = daysSinceLastReview != null && daysSinceLastReview < 1

        var state = card.state
        var step = card.step
        val stability: Double
        val difficulty: Double
        var intervalMs: Long

        when (card.state) {
            SrsState.LEARNING, SrsState.RELEARNING -> {
                val steps = if (card.state == SrsState.LEARNING) learningSteps else relearningSteps
                if (card.stability == null || card.difficulty == null) {
                    // Only a LEARNING card is ever new; py-fsrs asserts the same.
                    stability = initialStability(rating)
                    difficulty = initialDifficulty(rating, clamp = true)
                } else if (sameDay) {
                    stability = shortTermStability(card.stability, rating)
                    difficulty = nextDifficulty(card.difficulty, rating)
                } else {
                    stability = nextStability(
                        card.difficulty, card.stability, retrievability(card, atMs), rating,
                    )
                    difficulty = nextDifficulty(card.difficulty, rating)
                }

                val currentStep = checkNotNull(step) { "A ${card.state} card always has a step" }
                // The first branch also covers a card scheduled under more steps
                // than this scheduler has (py-fsrs: "edge case").
                if (steps.isEmpty() || (currentStep >= steps.size && rating != Rating.AGAIN)) {
                    state = SrsState.REVIEW
                    step = null
                    intervalMs = nextIntervalDays(stability) * DAY_MS
                } else {
                    when (rating) {
                        Rating.AGAIN -> {
                            step = 0
                            intervalMs = steps[0]
                        }
                        Rating.HARD -> {
                            // The step stays where it is.
                            intervalMs = when {
                                currentStep == 0 && steps.size == 1 -> steps[0] * 3 / 2
                                currentStep == 0 -> (steps[0] + steps[1]) / 2
                                else -> steps[currentStep]
                            }
                        }
                        Rating.GOOD -> {
                            if (currentStep + 1 == steps.size) {
                                state = SrsState.REVIEW
                                step = null
                                intervalMs = nextIntervalDays(stability) * DAY_MS
                            } else {
                                step = currentStep + 1
                                intervalMs = steps[currentStep + 1]
                            }
                        }
                        Rating.EASY -> {
                            state = SrsState.REVIEW
                            step = null
                            intervalMs = nextIntervalDays(stability) * DAY_MS
                        }
                    }
                }
            }

            SrsState.REVIEW -> {
                val s = checkNotNull(card.stability) { "A REVIEW card always has a stability" }
                val d = checkNotNull(card.difficulty) { "A REVIEW card always has a difficulty" }
                stability = if (sameDay) {
                    shortTermStability(s, rating)
                } else {
                    nextStability(d, s, retrievability(card, atMs), rating)
                }
                difficulty = nextDifficulty(d, rating)

                if (rating == Rating.AGAIN && relearningSteps.isNotEmpty()) {
                    state = SrsState.RELEARNING
                    step = 0
                    intervalMs = relearningSteps[0]
                } else {
                    intervalMs = nextIntervalDays(stability) * DAY_MS
                }
            }
        }

        if (enableFuzzing && state == SrsState.REVIEW) {
            intervalMs = fuzzedIntervalMs(intervalMs, Random(fuzzSeed))
        }

        return SrsCard(
            state = state,
            step = step,
            stability = stability,
            difficulty = difficulty,
            dueMs = atMs + intervalMs,
            lastReviewMs = atMs,
        )
    }

    /**
     * Rebuild a card from its answers, oldest first — py-fsrs's
     * `reschedule_card`. This is how `srs_state` is recomputed from
     * `review_log` (D-112). The answers are sorted here, so their order in
     * the list does not matter.
     */
    fun replay(createdAtMs: Long, answers: List<Answer>): SrsCard =
        answers.sortedBy { it.atMs }.fold(SrsCard.new(createdAtMs)) { card, answer ->
            review(card, answer.rating, answer.atMs, answer.fuzzSeed)
        }

    // ---- The model. Each mirrors the py-fsrs private method of the same name.

    private fun clampDifficulty(d: Double) = min(max(d, MIN_DIFFICULTY), MAX_DIFFICULTY)
    private fun clampStability(s: Double) = max(s, STABILITY_MIN)

    private fun initialStability(rating: Rating) = clampStability(parameters[rating.value - 1])

    private fun initialDifficulty(rating: Rating, clamp: Boolean): Double {
        val d = parameters[4] - E.pow(parameters[5] * (rating.value - 1)) + 1
        return if (clamp) clampDifficulty(d) else d
    }

    private fun nextIntervalDays(stability: Double): Long {
        val interval = (stability / factor) * (desiredRetention.pow(1 / decay) - 1)
        return round(interval).toLong().coerceAtLeast(1).coerceAtMost(maximumInterval.toLong())
    }

    private fun shortTermStability(stability: Double, rating: Rating): Double {
        var increase = E.pow(parameters[17] * (rating.value - 3 + parameters[18])) *
            stability.pow(-parameters[19])
        if (rating != Rating.AGAIN) increase = max(increase, 1.0)
        return clampStability(stability * increase)
    }

    private fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val delta = -(parameters[6] * (rating.value - 3))
        val damped = difficulty + (10.0 - difficulty) * delta / 9.0
        val easy = initialDifficulty(Rating.EASY, clamp = false)
        val reverted = parameters[7] * easy + (1 - parameters[7]) * damped
        return clampDifficulty(reverted)
    }

    private fun nextStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: Rating,
    ): Double = clampStability(
        if (rating == Rating.AGAIN) nextForgetStability(difficulty, stability, retrievability)
        else nextRecallStability(difficulty, stability, retrievability, rating)
    )

    private fun nextForgetStability(difficulty: Double, stability: Double, retrievability: Double): Double {
        val longTerm = parameters[11] *
            difficulty.pow(-parameters[12]) *
            ((stability + 1).pow(parameters[13]) - 1) *
            E.pow((1 - retrievability) * parameters[14])
        val shortTerm = stability / E.pow(parameters[17] * parameters[18])
        return min(longTerm, shortTerm)
    }

    private fun nextRecallStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: Rating,
    ): Double {
        val hardPenalty = if (rating == Rating.HARD) parameters[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) parameters[16] else 1.0
        return stability * (
            1 + E.pow(parameters[8]) *
                (11 - difficulty) *
                stability.pow(-parameters[9]) *
                (E.pow((1 - retrievability) * parameters[10]) - 1) *
                hardPenalty *
                easyBonus
            )
    }

    /** py-fsrs's `_get_fuzzed_interval`, drawing from [random] instead of a global source. */
    private fun fuzzedIntervalMs(intervalMs: Long, random: Random): Long {
        val days = intervalMs.floorDiv(DAY_MS)
        if (days < 2.5) return intervalMs // no fuzz under two and a half days

        var delta = 1.0
        for ((start, end, fuzzFactor) in FUZZ_RANGES) {
            delta += fuzzFactor * max(min(days.toDouble(), end) - start, 0.0)
        }
        var minIvl = round(days - delta).toLong()
        var maxIvl = round(days + delta).toLong()
        minIvl = max(2L, minIvl)
        maxIvl = min(maxIvl, maximumInterval.toLong())
        minIvl = min(minIvl, maxIvl)

        // floor, as ts-fsrs does, not py-fsrs's round: rounding lets the top
        // of the draw reach maxIvl + 1, a day past the range py-fsrs itself
        // describes. Floor makes every day from minIvl to maxIvl equally likely.
        val fuzzed = random.nextDouble() * (maxIvl - minIvl + 1) + minIvl
        return min(floor(fuzzed).toLong(), maximumInterval.toLong()) * DAY_MS
    }

    companion object {
        const val MINUTE_MS = 60_000L
        const val DAY_MS = 86_400_000L

        const val STABILITY_MIN = 0.001
        private const val INITIAL_STABILITY_MAX = 100.0
        const val MIN_DIFFICULTY = 1.0
        const val MAX_DIFFICULTY = 10.0

        /** FSRS-6's 21 default weights, as py-fsrs v6.3.2 ships them. */
        val DEFAULT_PARAMETERS: List<Double> = listOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
            1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
        )

        private val LOWER_BOUNDS = listOf(
            STABILITY_MIN, STABILITY_MIN, STABILITY_MIN, STABILITY_MIN,
            1.0, 0.001, 0.001, 0.001, 0.0, 0.0, 0.001, 0.001, 0.001, 0.001, 0.0, 0.0,
            1.0, 0.0, 0.0, 0.0, 0.1,
        )

        private val UPPER_BOUNDS = listOf(
            INITIAL_STABILITY_MAX, INITIAL_STABILITY_MAX, INITIAL_STABILITY_MAX, INITIAL_STABILITY_MAX,
            10.0, 4.0, 4.0, 0.75, 4.5, 0.8, 3.5, 5.0, 0.25, 0.9, 4.0, 1.0,
            6.0, 2.0, 2.0, 0.8, 0.8,
        )

        /** (start, end, factor) in days, as py-fsrs's FUZZ_RANGES. */
        private val FUZZ_RANGES = listOf(
            Triple(2.5, 7.0, 0.15),
            Triple(7.0, 20.0, 0.1),
            Triple(20.0, Double.POSITIVE_INFINITY, 0.05),
        )
    }
}

/** FSRS's four grades, with the values its formulas use. */
enum class Rating(val value: Int) {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);

    companion object {
        /**
         * Spotter's answers are right or wrong (D-107). Wrong is a failed recall,
         * which FSRS calls AGAIN; right is a successful one, GOOD. HARD and EASY
         * are never produced by the app.
         */
        fun of(passed: Boolean): Rating = if (passed) GOOD else AGAIN
    }
}

/** Where a card is in FSRS's cycle. A new card is LEARNING with no stability. */
enum class SrsState { LEARNING, REVIEW, RELEARNING }

/**
 * One card's schedule — the fields of `srs_state` (D-112), without its key.
 *
 * [stability] and [difficulty] are null until the first answer; [step] is null
 * once the card reaches [SrsState.REVIEW].
 */
data class SrsCard(
    val state: SrsState,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    val dueMs: Long,
    val lastReviewMs: Long?,
) {
    companion object {
        /** A card that has never been answered, due at once. */
        fun new(createdAtMs: Long) = SrsCard(
            state = SrsState.LEARNING,
            step = 0,
            stability = null,
            difficulty = null,
            dueMs = createdAtMs,
            lastReviewMs = null,
        )
    }
}

/** One logged answer, as [Fsrs.replay] needs it. */
data class Answer(val rating: Rating, val atMs: Long, val fuzzSeed: Long = 0L)
