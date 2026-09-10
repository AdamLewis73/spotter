package com.spotterkanji.domain.user

import kotlinx.coroutines.flow.Flow

/**
 * The user's named lists, and which words are in them (D-28).
 *
 * Lists never own scheduling (D-29) — there is nothing here that touches review
 * state, and that absence is the design rather than an omission.
 *
 * As with [SavedItemsRepository], every read here is of live rows; tombstones
 * are filtered in the DAO.
 */
interface SavedListRepository {

    /** Every live list, in creation order. */
    fun observeLists(): Flow<List<SavedList>>

    /**
     * Every live list with its filed-word count — what the Saved screen shows.
     *
     * Counted in SQL rather than by loading each list's words and taking
     * `size`, which would be one query per list and would pull every saved word
     * into memory to display a number.
     */
    fun observeListSummaries(): Flow<List<SavedListSummary>>

    /** The words in [listId], newest addition first. Empty for an unknown or deleted list. */
    fun observeItemsIn(listId: SavedListId): Flow<List<StudyItem>>

    /**
     * Which lists hold [itemId] — what the word screen needs to show the user
     * where a word is already filed.
     */
    fun observeListsContaining(itemId: StudyItemId): Flow<List<SavedList>>

    /**
     * Which lists hold the word [key], by natural key rather than by row id.
     *
     * The picker needs this before the word necessarily exists: a word being
     * saved for the first time has no id to ask about, and one saved earlier may
     * be **unfiled**, which `find` deliberately reports as not saved (D-89). Both
     * cases have to answer "which lists already hold this", and the natural key
     * is the only handle that works in both — which is D-12's point.
     */
    fun observeListsHolding(key: StudyItemKey): Flow<List<SavedList>>

    /**
     * Create a list called [name].
     *
     * Names are **not** unique. Two lists called "Food" are the user's business:
     * a uniqueness constraint here would either reject a rename the user meant
     * or resurrect a deleted list's name conflict, and the identity that matters
     * is the UUID (D-15).
     */
    suspend fun createList(name: String): SavedList

    suspend fun renameList(id: SavedListId, name: String)

    /**
     * Soft-delete the list and tombstone its memberships (D-16, D-80).
     *
     * The words themselves are untouched — deleting "Street Signs" must not
     * delete 先生, which may also be in "Food Menu" and in any case carries its
     * own review history.
     */
    suspend fun deleteList(id: SavedListId)

    /**
     * File [itemId] into [listId] — what the picker does (D-88, D-91).
     *
     * Idempotent: a word already live in the list is left exactly where it is.
     * A word that was **once** in the list and removed comes back as a fresh
     * addition, at the top, because re-filing is a new decision to keep it here
     * (D-93, following D-82's reasoning for the Saved list).
     *
     * Not for Undo — see [restoreToList], which puts a word back where it was.
     */
    suspend fun addToList(listId: SavedListId, itemId: StudyItemId)

    /**
     * Put [itemId] back into [listId] exactly as it was before it was removed —
     * what Undo does (D-93).
     *
     * The difference from [addToList] is the word's position. Undo cancels a
     * removal, so the word returns to its original place in the list; re-filing
     * later is a new addition and goes to the top. Two methods rather than a flag,
     * so each call site says which of the two it means.
     */
    suspend fun restoreToList(listId: SavedListId, itemId: StudyItemId)

    /** Soft-delete the membership, leaving the word saved (D-80). A no-op if it is not a member. */
    suspend fun removeFromList(listId: SavedListId, itemId: StudyItemId)
}
