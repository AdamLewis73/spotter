package com.spotterkanji.data.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes over the saved words.
 *
 * **Every read filters `deleted_at IS NULL` here, in the query.** That is not
 * incidental: tombstones exist for sync and backup (D-16, D-80) and are of no
 * interest to anything above this layer, so the filter belongs in one place
 * where it cannot be forgotten. A repository that filtered in Kotlin instead
 * would work until the one query that forgot, and the symptom — a word the user
 * deleted reappearing in their reviews — reads as data corruption rather than
 * as a missing `WHERE`.
 *
 * There are no `@Delete` methods, for the same reason.
 */
@Dao
interface StudyItemDao {

    @Query(
        """
        SELECT * FROM study_item
        WHERE deleted_at IS NULL
        ORDER BY created_at DESC
        """
    )
    fun observeAll(): Flow<List<StudyItemRow>>

    /**
     * The live row for a natural key, or null.
     *
     * A `Flow` because it drives the Save button's saved/unsaved state, which
     * must follow a write made anywhere — including an unsave performed on the
     * Saved tab while the peek sheet is still open beneath it.
     */
    @Query(
        """
        SELECT * FROM study_item
        WHERE text = :text AND reading = :reading AND type = :type
          AND deleted_at IS NULL
        """
    )
    fun observeByKey(text: String, reading: String, type: String): Flow<StudyItemRow?>

    @Query(
        """
        SELECT * FROM study_item
        WHERE text = :text AND reading = :reading AND type = :type
          AND deleted_at IS NULL
        """
    )
    suspend fun findLive(text: String, reading: String, type: String): StudyItemRow?

    /**
     * The row for a natural key **including a tombstoned one**.
     *
     * The only query here that sees dead rows, and it exists for exactly one
     * caller: saving. The unique index covers tombstones, so a word the user
     * once deleted still occupies its key — re-saving it must find that row and
     * revive it rather than fail on the constraint. It also means the word's
     * review history survives a delete-and-resave round trip, which is what
     * FSRS wants, since history is kept so a schedule can be recomputed.
     */
    @Query(
        """
        SELECT * FROM study_item
        WHERE text = :text AND reading = :reading AND type = :type
        """
    )
    suspend fun findIncludingDeleted(text: String, reading: String, type: String): StudyItemRow?

    @Insert
    suspend fun insert(row: StudyItemRow)

    /**
     * Revives a **tombstoned** row, resetting `created_at` to now.
     *
     * The reset is deliberate and is the one place this schema treats
     * `created_at` as anything but immutable. From the user's point of view they
     * saved this word just now, and the Saved list is ordered newest-first — so
     * keeping the original date would drop a word they had just re-saved back
     * into the middle of the list, where they would go looking for it at the top
     * (D-82).
     *
     * The row **id** is still preserved, which is the part that matters for
     * Phase 7: `review_log` will hang off it, and minting a new id here would
     * orphan every review of the word.
     */
    @Query(
        """
        UPDATE study_item
        SET deleted_at = NULL, snapshot_gloss = :snapshotGloss, ent_seq = :entSeq,
            created_at = :now, updated_at = :now
        WHERE id = :id
        """
    )
    suspend fun reviveAndRefresh(id: String, snapshotGloss: String, entSeq: Long?, now: Long)

    /**
     * Refreshes an **already live** row's snapshot without touching
     * `created_at`.
     *
     * Saving a word that is already saved is a no-op the user did not ask for —
     * re-ordering their Saved list because they tapped a button that was already
     * on would be surprising (D-82).
     */
    @Query(
        """
        UPDATE study_item
        SET snapshot_gloss = :snapshotGloss, ent_seq = :entSeq, updated_at = :now
        WHERE id = :id
        """
    )
    suspend fun refreshLive(id: String, snapshotGloss: String, entSeq: Long?, now: Long)

    /** Soft delete (D-16). There is deliberately no hard-delete counterpart. */
    @Query("UPDATE study_item SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

/**
 * Reads and writes over lists and their membership (D-28).
 *
 * Nothing here touches review state, and that absence is the design: lists
 * organise, they never schedule (D-29). A word in three lists has one schedule.
 */
@Dao
interface SavedListDao {

    @Query("SELECT * FROM saved_list WHERE deleted_at IS NULL ORDER BY created_at ASC")
    fun observeLists(): Flow<List<SavedListRow>>

    /**
     * The words in a list.
     *
     * **Both sides are filtered for tombstones.** A membership can be live while
     * its word is deleted — unsaving a word tombstones its memberships too, but
     * a restore or a future sync can land the two halves out of step, and the
     * visible failure would be a deleted word reappearing inside a list. The
     * join therefore trusts neither side on its own.
     */
    @Query(
        """
        SELECT i.* FROM study_item i
        INNER JOIN list_membership m ON m.study_item_id = i.id
        WHERE m.list_id = :listId
          AND m.deleted_at IS NULL
          AND i.deleted_at IS NULL
        ORDER BY m.added_at DESC
        """
    )
    fun observeItemsIn(listId: String): Flow<List<StudyItemRow>>

    @Query(
        """
        SELECT l.* FROM saved_list l
        INNER JOIN list_membership m ON m.list_id = l.id
        WHERE m.study_item_id = :studyItemId
          AND m.deleted_at IS NULL
          AND l.deleted_at IS NULL
        ORDER BY l.created_at ASC
        """
    )
    fun observeListsContaining(studyItemId: String): Flow<List<SavedListRow>>

    @Insert
    suspend fun insertList(row: SavedListRow)

    @Query("UPDATE saved_list SET name = :name, updated_at = :now WHERE id = :id")
    suspend fun renameList(id: String, name: String, now: Long)

    @Query("UPDATE saved_list SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDeleteList(id: String, now: Long)

    /** As [StudyItemDao.findIncludingDeleted], and for the same reason: the unique index covers tombstones. */
    @Query(
        """
        SELECT * FROM list_membership
        WHERE list_id = :listId AND study_item_id = :studyItemId
        """
    )
    suspend fun findMembershipIncludingDeleted(listId: String, studyItemId: String): ListMembershipRow?

    @Insert
    suspend fun insertMembership(row: ListMembershipRow)

    @Query("UPDATE list_membership SET deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun reviveMembership(id: String, now: Long)

    @Query("UPDATE list_membership SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDeleteMembership(id: String, now: Long)

    /**
     * Tombstone every membership of a word, used when the word itself is
     * unsaved.
     *
     * Leaving them live would make the word a member of nothing visible — the
     * join filters it out — right up until a restore revived the word and put
     * it back into lists the user had emptied it from.
     */
    @Query(
        """
        UPDATE list_membership SET deleted_at = :now, updated_at = :now
        WHERE study_item_id = :studyItemId AND deleted_at IS NULL
        """
    )
    suspend fun softDeleteMembershipsOfItem(studyItemId: String, now: Long)

    /** As above, when a list is deleted. The words themselves are untouched. */
    @Query(
        """
        UPDATE list_membership SET deleted_at = :now, updated_at = :now
        WHERE list_id = :listId AND deleted_at IS NULL
        """
    )
    suspend fun softDeleteMembershipsOfList(listId: String, now: Long)
}
