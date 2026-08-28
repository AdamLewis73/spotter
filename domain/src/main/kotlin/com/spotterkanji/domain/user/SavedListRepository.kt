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

    /** The words in [listId], newest addition first. Empty for an unknown or deleted list. */
    fun observeItemsIn(listId: SavedListId): Flow<List<StudyItem>>

    /**
     * Which lists hold [itemId] — what the word screen needs to show the user
     * where a word is already filed.
     */
    fun observeListsContaining(itemId: StudyItemId): Flow<List<SavedList>>

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

    /** Add [itemId] to [listId]. Idempotent, and revives a tombstoned membership in place. */
    suspend fun addToList(listId: SavedListId, itemId: StudyItemId)

    /** Soft-delete the membership, leaving the word saved (D-80). A no-op if it is not a member. */
    suspend fun removeFromList(listId: SavedListId, itemId: StudyItemId)
}
