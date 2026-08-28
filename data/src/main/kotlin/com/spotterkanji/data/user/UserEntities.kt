package com.spotterkanji.data.user

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The writable user database's tables (D-09) — the ones holding data the user
 * created, as opposed to the read-only dictionary shipped in the APK.
 *
 * Four rules apply to every row here, and each is a checkpoint in `roadmap.md`
 * because getting it wrong destroys data that cannot be recovered:
 *
 * - **UUID primary keys, never auto-increment** (D-15). Two devices saving
 *   offline both mint `id = 5`, and there is no reconciling that afterwards.
 * - **`updated_at` on every row, no exceptions** (D-16, D-80). It is what a
 *   future sync compares to decide which of two versions of a row is newer.
 * - **Soft delete where the user deletes** (D-80): `study_item`, `saved_list`
 *   and `list_membership` keep tombstones. Absence alone is ambiguous — a
 *   second device cannot tell "deliberately removed" from "never had it", and
 *   the repair it reaches for is to re-add.
 * - **No dictionary row ids** (D-11). Rebuilds reassign them, so one stored
 *   here eventually points at a different word with no error anywhere.
 *
 * Timestamps are **epoch milliseconds**, not ISO strings: they sort and compare
 * correctly in SQL without a collation, and Phase 8's export (D-20) serialises
 * an integer rather than committing to a text format it would then be stuck
 * with. Conversion to `java.time.Instant` happens at the mapper, so the domain
 * layer never sees a `Long` pretending to be a time.
 */
@Entity(
    tableName = "study_item",
    indices = [
        // Identity is (text, reading, type) — never text alone (D-12, D-27).
        // UNIQUE because this is what makes saving idempotent: re-saving a word
        // has nowhere to put a duplicate, so it must find the existing row.
        //
        // Note the constraint covers tombstoned rows too, which is deliberate.
        // A deleted word's row still occupies its natural key, so re-saving it
        // revives that row and keeps its review history rather than orphaning
        // it behind an identical-looking new one.
        Index(value = ["text", "reading", "type"], unique = true),
        // The Saved tab lists live rows newest-first; without this it is a full
        // scan and a sort on every emission of the Flow.
        Index(value = ["deleted_at", "created_at"]),
    ],
)
data class StudyItemRow(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** `WORD` | `KANJI` (D-27). v1 only ever writes `WORD`. */
    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "text")
    val text: String,

    /** Half the identity (D-12). Empty only for a `KANJI` row. */
    @ColumnInfo(name = "reading")
    val reading: String,

    /**
     * JMdict's `ent_seq` — **a hint, never the identity** (D-11).
     *
     * Nullable because a word saved when the dictionary could not supply one
     * must still save. Nothing may resolve a row by this column.
     */
    @ColumnInfo(name = "ent_seq")
    val entSeq: Long?,

    /**
     * The gloss as displayed at save time, read **only when live lookup fails**
     * (D-43).
     *
     * Captured now because it cannot be captured later: a word the dictionary
     * has since dropped has no gloss left to recover, and D-40 requires that
     * its card still render rather than silently vanish from the user's list.
     */
    @ColumnInfo(name = "snapshot_gloss")
    val snapshotGloss: String,

    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
)

/** A user-named list. Names are not unique — two lists called "Food" are the user's business. */
@Entity(
    tableName = "saved_list",
    indices = [Index(value = ["deleted_at", "created_at"])],
)
data class SavedListRow(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
)

/**
 * A word's membership of a list — the join row that makes lists many-to-many
 * (D-28), since the same word genuinely belongs on both a menu and a street
 * sign.
 *
 * **This is the row D-80 was written for.** "Remove from Street Signs" deletes
 * *this*, not the word, and it is the deletion users perform most often. Hard
 * deleting it means a restored backup or a second device sees the membership on
 * one side and missing on the other, with nothing saying the absence was
 * intended — so it helpfully puts the word back in the list the user just
 * tidied. Hence `deleted_at`.
 *
 * The foreign keys cascade, which under soft deletion never fires — a
 * tombstoned parent is still a present row. They are declared anyway as a
 * backstop for any genuine hard delete (a future purge of long-dead rows, or an
 * import repairing itself), so that path cannot leave memberships pointing at
 * nothing.
 */
@Entity(
    tableName = "list_membership",
    foreignKeys = [
        ForeignKey(
            entity = SavedListRow::class,
            parentColumns = ["id"],
            childColumns = ["list_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = StudyItemRow::class,
            parentColumns = ["id"],
            childColumns = ["study_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // One membership per (list, word) — what makes "add to list" idempotent.
        Index(value = ["list_id", "study_item_id"], unique = true),
        // Room requires an index on a foreign key's child column, and this one
        // also serves "which lists is this word in" on the word screen.
        Index(value = ["study_item_id"]),
    ],
)
data class ListMembershipRow(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "list_id") val listId: String,
    @ColumnInfo(name = "study_item_id") val studyItemId: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
)
