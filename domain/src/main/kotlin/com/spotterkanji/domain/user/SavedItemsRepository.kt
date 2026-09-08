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

    /** Everything currently saved, newest first. Empty before the user saves anything. */
    fun observeSaved(): Flow<List<StudyItem>>

    /**
     * Whether [key] is saved right now.
     *
     * **This does not yet mean what D-89 says it must.** It currently answers
     * *does a live row exist*; it has to answer *does a live row exist **and**
     * does it have at least one live list membership*, because D-88 requires
     * every saved word to be filed and D-89 keeps an unfiled word's row and
     * history while hiding it everywhere. Until this is fixed, an unfiled word
     * reports itself saved with nothing behind it.
     *
     * Not reachable by a user today — nothing can add a word to a list or take
     * it out yet — so it is latent until the Saved screen ships. Fix it first.
     *
     * A `Flow` rather than a `suspend` call because this drives the Save button
     * on the peek sheet, which must change the moment the write lands — and
     * must equally change back when the same word is unsaved from the Saved tab
     * while the sheet is still open behind it.
     */
    fun observeIsSaved(key: StudyItemKey): Flow<Boolean>

    /** The saved item for [key], or null if it is not saved. */
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

    /**
     * Soft-delete [key], leaving a tombstone (D-16). A no-op if it is not saved.
     *
     * List memberships are tombstoned with it, so the word does not linger in
     * "Street Signs" as a dangling row that a later restore could revive on its
     * own.
     */
    suspend fun unsave(key: StudyItemKey)
}
