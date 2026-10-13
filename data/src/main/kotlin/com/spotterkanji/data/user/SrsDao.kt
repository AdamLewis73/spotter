package com.spotterkanji.data.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.spotterkanji.domain.srs.CardKind
import com.spotterkanji.domain.srs.SrsState

/**
 * Schedules and answers (D-112).
 *
 * **The queue reviews filed words only** (D-89): a live study item with at least
 * one live list membership — the same test `StudyItemDao.observeFiledCount`
 * uses, so a word counts as reviewable exactly when Saved shows it. And WORD
 * items only for now: what a kanji card's reading field accepts is still open
 * (D-106), so a kanji is kept out rather than asked a question with no rule.
 */
@Dao
interface SrsDao {

    @Query(
        """
        SELECT COUNT(*) FROM srs_state AS s
        JOIN study_item AS i ON i.id = s.study_item_id
        WHERE s.card_kind = :cardKind AND s.due_at <= :now
          AND $REVIEWABLE
        """
    )
    suspend fun dueCount(cardKind: String, now: Long): Int

    @Query(
        """
        SELECT MIN(s.due_at) FROM srs_state AS s
        JOIN study_item AS i ON i.id = s.study_item_id
        WHERE s.card_kind = :cardKind AND $REVIEWABLE
        """
    )
    suspend fun nextDueAt(cardKind: String): Long?

    /**
     * Due items, most overdue first. New words share their save time as due
     * time (the v3 migration and `newReviewSchedule` both do that), so they come
     * up in the order they were saved; `created_at` breaks exact ties.
     */
    @Query(
        """
        SELECT i.* FROM srs_state AS s
        JOIN study_item AS i ON i.id = s.study_item_id
        WHERE s.card_kind = :cardKind AND s.due_at <= :now
          AND $REVIEWABLE
        ORDER BY s.due_at, i.created_at
        LIMIT :limit
        """
    )
    suspend fun dueItems(cardKind: String, now: Long, limit: Int): List<StudyItemRow>

    @androidx.room.Upsert
    suspend fun upsert(row: SrsStateRow)

    @Insert
    suspend fun insertLog(row: ReviewLogRow)

    @Query("SELECT * FROM review_log WHERE study_item_id = :studyItemId AND card_kind = :cardKind ORDER BY reviewed_at")
    suspend fun logFor(studyItemId: String, cardKind: String): List<ReviewLogRow>

    @Query("SELECT text FROM user_synonym WHERE study_item_id = :studyItemId AND deleted_at IS NULL ORDER BY created_at")
    suspend fun synonyms(studyItemId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSynonymIfAbsent(row: UserSynonymRow): Long

    /** Brings back a synonym the learner removed and then earned again. */
    @Query("UPDATE user_synonym SET deleted_at = NULL, updated_at = :now WHERE study_item_id = :studyItemId AND text = :text")
    suspend fun reviveSynonym(studyItemId: String, text: String, now: Long)

    /**
     * Creates a schedule unless the card already has one. IGNORE rather than
     * REPLACE: a schedule that exists carries review history, and replacing it
     * would reset a learner's progress on a word they re-save.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: SrsStateRow)

    @Query("SELECT * FROM srs_state WHERE study_item_id = :studyItemId AND card_kind = :cardKind")
    suspend fun find(studyItemId: String, cardKind: String): SrsStateRow?
}

/** The test every review query shares: a live, filed WORD (D-89, D-106). */
private const val REVIEWABLE = """
    i.deleted_at IS NULL
    AND i.type = 'WORD'
    AND EXISTS (
        SELECT 1 FROM list_membership AS m
        WHERE m.study_item_id = i.id AND m.deleted_at IS NULL
    )
"""

/**
 * A REVIEW card that has never been answered, due at [dueAt] — FSRS's new card
 * (`SrsCard.new`). Every study item has exactly one (D-107, D-112): it is
 * created with the item, and the v2 → v3 migration gave one to every item that
 * already existed.
 */
internal fun newReviewSchedule(studyItemId: String, dueAt: Long, now: Long) = SrsStateRow(
    studyItemId = studyItemId,
    cardKind = CardKind.REVIEW.name,
    fsrsState = SrsState.LEARNING.name,
    step = 0,
    stability = null,
    difficulty = null,
    dueAt = dueAt,
    lastReviewedAt = null,
    createdAt = now,
    updatedAt = now,
)

/**
 * v2 → v3 (D-112). Room writes the three new tables by itself; this adds the
 * one thing it cannot know — that **every existing study item gets a REVIEW
 * schedule**, so words saved before the update can be reviewed at once.
 *
 * Due at the item's own `created_at`, which is in the past, so all of them are
 * due immediately and come up in the order they were saved. Tombstoned items
 * get one too: a deleted word that is saved again is revived with its id
 * (D-82), and it must come back with a schedule rather than without one.
 *
 * `INSERT OR IGNORE`, so running it against a database that somehow already has
 * schedules leaves them alone. Values mirror [newReviewSchedule].
 *
 * Overrides the **connection** form of the hook, which is the one Room 2.8's
 * generated migration calls (`UserDatabase_AutoMigration_2_3_Impl`); the older
 * `SupportSQLiteDatabase` form is only reached through a compatibility bridge.
 */
class UserMigration2To3 : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            INSERT OR IGNORE INTO srs_state
                (study_item_id, card_kind, fsrs_state, step, stability, difficulty,
                 due_at, last_reviewed_at, created_at, updated_at)
            SELECT id, '${CardKind.REVIEW.name}', '${SrsState.LEARNING.name}', 0, NULL, NULL,
                   created_at, NULL,
                   CAST(strftime('%s', 'now') AS INTEGER) * 1000,
                   CAST(strftime('%s', 'now') AS INTEGER) * 1000
            FROM study_item
            """.trimIndent()
        )
    }
}
