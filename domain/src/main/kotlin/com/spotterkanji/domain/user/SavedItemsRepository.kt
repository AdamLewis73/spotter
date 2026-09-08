package com.spotterkanji.domain.user

import kotlinx.coroutines.flow.Flow

/**
 * The user's saved words.
 *
 * Declared here and implemented in `:data`, so nothing above this layer knows
 * the storage is Room — the same arrangement as `DictionaryRepository`, and for
 * the same two reasons: the storage can change without touching callers, and an
 * eventual iOS port reimplements this interface rather than rewriting the app.
 *
 * **Every method here reads live rows only.** Tombstones (D-16, D-80) are an
 * implementation concern and are filtered inside the DAO queries, not by
 * callers — a `deleted_at IS NULL` that each caller must remember is a
 * deleted-word-reappears bug waiting for the one query that forgets.
 */
interface SavedItemsRepository {

    /**
     * Everything currently **filed**, newest first. Empty before the user files
     * anything, and empty again if they empty every list.
     *
     * A word appears once however many lists hold it.
     */
    fun observeSaved(): Flow<List<StudyItem>>

    /**
     * Whether [key] is saved right now — meaning **filed in at least one list**
     * (D-88, D-89), not merely present in the database.
     *
     * The distinction is the whole point. A word taken out of its last list
     * keeps its row and its entire review history, and disappears from the app
     * until it is filed again. Answering *does a row exist* would report that
     * word as saved while it appears nowhere the user can reach.
     *
     * A `Flow` rather than a `suspend` call because this drives the save
     * control, which must change the moment the write lands — and must equally
     * change back when the word is taken out of its last list on the Saved tab
     * while the sheet is still open over the photograph.
     */
    fun observeIsSaved(key: StudyItemKey): Flow<Boolean>

    /**
     * The saved item for [key], or null if it is not **filed**.
     *
     * Null covers three cases the caller does not need to separate: never
     * saved, deleted, and saved-then-unfiled. All three mean *offer to file
     * it*.
     */
    suspend fun find(key: StudyItemKey): StudyItem?

    /**
     * Save [key], and return the row — whether it was created or already there.
     *
     * **Idempotent on the natural key, and it revives a tombstone in place**
     * rather than inserting a second row. That is partly forced — the unique
     * constraint on (text, reading, type) leaves nowhere to put a duplicate —
     * and partly correct: re-saving a word the user once deleted is the same
     * word, and reusing the row keeps its review history, which FSRS needs kept
     * so a schedule can be recomputed if the algorithm is ever retuned.
     *
     * [snapshotGloss] is captured on first save and refreshed on re-save, since
     * a live lookup succeeded either way and its result is the best available
     * fallback (D-43). [entSeq] is stored as a hint only and must never be used
     * to resolve this item (D-11).
     */
    suspend fun save(
        key: StudyItemKey,
        snapshotGloss: String,
        entSeq: Long? = null,
    ): StudyItem

    // NOTE: this creates the word without filing it, so on its own it does not
    // make [observeIsSaved] true (D-88). Pair it with
    // `SavedListRepository.addToList` — that pairing is what the list picker
    // does, and it is why saving is two steps rather than one.

    /**
     * Delete [key] outright, leaving a tombstone (D-16). A no-op if there is no
     * live row.
     *
     * **Not the same as taking a word out of a list.** This ends the word: its
     * memberships are tombstoned with it, so it cannot linger in "Street Signs"
     * as a dangling row a later restore could revive on its own. Removing a
     * word from a list is `SavedListRepository.removeFromList`, which leaves
     * the word and its review history alone (D-89).
     *
     * It works on an unfiled word too — those still exist, they are just
     * invisible, and they must remain deletable.
     */
    suspend fun unsave(key: StudyItemKey)
}
