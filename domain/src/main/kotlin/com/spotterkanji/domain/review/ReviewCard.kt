package com.spotterkanji.domain.review

import com.spotterkanji.domain.dictionary.DictionaryEntry
import com.spotterkanji.domain.srs.CardKind
import com.spotterkanji.domain.srs.SrsCard
import com.spotterkanji.domain.user.StudyItem
import com.spotterkanji.domain.user.SavedListId
import com.spotterkanji.domain.user.StudyItemId
import kotlinx.coroutines.flow.Flow

/**
 * Everything one review card needs to ask and to check its answers (D-107,
 * D-108): the word, its senses, which readings count, and which readings belong
 * to other words written the same way.
 */
data class ReviewCard(
    val itemId: StudyItemId,
    val text: String,
    /** The saved reading — the `reading` half of the item's identity (D-12). */
    val reading: String,
    /** Each sense's glosses, main sense first. Checked against any of them (D-108). */
    val senses: List<List<String>>,
    /** Readings that answer the card: the saved one first, then the rest of its JMdict entry. */
    val acceptedReadings: List<String>,
    /** Readings of other words with the same written form — a retry, not a mark (D-108). */
    val otherWords: List<OtherReading>,
    /** The learner's own accepted meanings for this word (D-108). */
    val synonyms: List<String>,
    /**
     * False when the dictionary no longer has this (text, reading): the card is
     * built from `snapshot_gloss` instead and stays answerable (D-40, D-43, V-20).
     */
    val resolved: Boolean,
) {
    val mainSense: List<String> get() = senses.firstOrNull().orEmpty()
    val otherSenses: List<List<String>> get() = senses.drop(1)
}

/**
 * Builds [item]'s card from a dictionary [lookup][com.spotterkanji.domain.dictionary.DictionaryRepository.lookup]
 * of its written form.
 *
 * **Which readings count is decided by the JMdict entry, not by the meaning
 * text.** Readings that share the saved reading's entry are the same word —
 * 上手's じょうず, じょうしゅ and じょうて are one entry, "skillful" — and every one
 * answers the card. A reading from another entry is another word, うわて "upper
 * part", and earns a retry. The entry number is used only *within* one lookup,
 * never stored or compared across builds, so D-11 is not touched.
 *
 * When the saved reading is no longer in the dictionary the card falls back to
 * `snapshot_gloss` and the saved reading alone, so it never drops out of review
 * (D-40). Its sibling readings are then still offered as other words to retry on.
 */
fun buildReviewCard(item: StudyItem, entries: List<DictionaryEntry>, synonyms: List<String>): ReviewCard {
    val saved = entries.firstOrNull { it.reading == item.key.reading }

    if (saved == null) {
        return ReviewCard(
            itemId = item.id,
            text = item.key.text,
            reading = item.key.reading,
            senses = listOf(item.snapshotGloss.split(";").map { it.trim() }.filter { it.isNotEmpty() }),
            acceptedReadings = listOf(item.key.reading),
            otherWords = entries.toOtherReadings(),
            synonyms = synonyms,
            resolved = false,
        )
    }

    fun sameWord(entry: DictionaryEntry) =
        entry.reading == saved.reading || (saved.entSeq != null && entry.entSeq == saved.entSeq)

    return ReviewCard(
        itemId = item.id,
        text = item.key.text,
        reading = item.key.reading,
        senses = saved.senses.map { it.glosses }.filter { it.isNotEmpty() },
        acceptedReadings = (listOf(saved.reading) + entries.filter(::sameWord).map { it.reading }).distinct(),
        otherWords = entries.filterNot(::sameWord).toOtherReadings(),
        synonyms = synonyms,
        resolved = true,
    )
}

private fun List<DictionaryEntry>.toOtherReadings(): List<OtherReading> =
    distinctBy { it.reading }.map { entry ->
        OtherReading(
            reading = entry.reading,
            meaning = entry.senses.firstOrNull()?.glosses?.take(2)?.joinToString("; ").orEmpty(),
        )
    }

/**
 * A list's review state on Saved (artboard 2d). **Learned** means out of FSRS's
 * learning steps and into its REVIEW state — a word that has been seen once is
 * not yet learned, and one that has lapsed back into relearning is not either.
 */
data class ListReviewState(val due: Int, val learned: Int)

/**
 * What a learner answered, as `review_log` records it (D-112). [passed] is what
 * FSRS sees; the two halves are kept apart so a later change to one card per
 * word can be replayed rather than invented (D-107).
 */
data class ReviewAnswer(
    val passed: Boolean,
    val meaningAnswer: String?,
    val meaningCorrect: Boolean?,
    val meaningOverridden: Boolean?,
    val readingAnswer: String?,
    val readingCorrect: Boolean?,
)

/**
 * The seed for one review's fuzz (D-113), derived from the review itself so a
 * replay of the log reproduces the same due date (D-112).
 *
 * FNV-1a over the item, the card kind and the time — written out rather than
 * `String.hashCode`, so the value is fixed by this code and not by a platform.
 */
fun fuzzSeed(itemId: StudyItemId, kind: CardKind, atMs: Long): Long {
    var hash = -0x340d631b7bdddcdbL // 14695981039346656037, the FNV-1a 64-bit offset basis
    for (c in "${itemId.value}|${kind.name}|$atMs") {
        hash = hash xor c.code.toLong()
        hash *= 0x100000001b3L
    }
    return hash
}

/**
 * Review's view of user data (D-112). Implemented over Room in `:data`.
 *
 * Only **filed** words are reviewed (D-89) — a word taken out of every list
 * keeps its schedule but drops out of the queue until it is filed again — and
 * only WORD items for now: what a kanji card's reading field accepts is still
 * open (D-106).
 */
interface ReviewRepository {
    /**
     * How many REVIEW cards are due at [nowMs] — in [listId] only, when given
     * ("Review this list", D-117). A list only filters; the schedule is the
     * word's (D-29).
     */
    suspend fun dueCount(nowMs: Long, listId: SavedListId? = null): Int

    /** When the next REVIEW card falls due, or null if nothing is scheduled. */
    suspend fun nextDueAt(listId: SavedListId? = null): Long?

    /** Due REVIEW cards' items, most overdue first, new words in the order saved. */
    suspend fun dueItems(nowMs: Long, limit: Int, listId: SavedListId? = null): List<StudyItem>

    /**
     * [listId]'s words that are **not** due at [nowMs], soonest due first — the
     * practice that follows a list's review (D-117). Practice is never recorded,
     * so this is the only place it touches the schedule: to choose an order.
     */
    suspend fun notDueItems(nowMs: Long, limit: Int, listId: SavedListId): List<StudyItem>

    /** [dueCount], live: changes as words are filed, answered or removed. */
    fun observeDueCount(nowMs: Long): Flow<Int>

    /** Due and learned counts per list (artboard 2d). Lists with no reviewable words are absent. */
    fun observeListReviewState(nowMs: Long): Flow<Map<SavedListId, ListReviewState>>

    /** Answers given at or after [sinceMs]. */
    suspend fun answeredSince(sinceMs: Long): Int

    /** Due times of reviewable cards after [fromMs] and up to [untilMs], earliest first. */
    suspend fun dueTimesBetween(fromMs: Long, untilMs: Long, listId: SavedListId? = null): List<Long>

    suspend fun synonyms(itemId: StudyItemId): List<String>

    /** Saves [text] as the learner's own meaning for the word ("I was right", D-108). */
    suspend fun addSynonym(itemId: StudyItemId, text: String)

    /**
     * Appends [answer] to the log and moves the card's schedule on through FSRS,
     * in one transaction. Returns the new schedule.
     */
    suspend fun record(
        itemId: StudyItemId,
        kind: CardKind,
        answer: ReviewAnswer,
        atMs: Long,
        durationMs: Long?,
    ): SrsCard
}
