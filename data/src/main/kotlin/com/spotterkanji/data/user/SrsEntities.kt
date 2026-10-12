package com.spotterkanji.data.user

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One card's current schedule (D-105, D-112). Schema v3.
 *
 * **A cache, not a record.** Everything here can be rebuilt by replaying the
 * card's [ReviewLogRow]s through FSRS (`domain/srs/Fsrs.replay`), which is what
 * makes merging two devices, retuning FSRS and fixing a scheduler bug cheap.
 * Nothing should ever be written here that the log could not reproduce.
 *
 * **Keyed by (study item, card kind), not by a UUID** — the one table that
 * departs from D-15's letter, for D-15's own reason (D-112). The key is built
 * from the item's UUID and a fixed name, so it cannot collide, and two offline
 * devices that both create 先生's REVIEW schedule produce the same key and merge
 * rather than leaving two schedules for one card.
 *
 * Cascades with its study item and has no tombstone: it is derived data, not
 * something the user deletes (D-80). It never hangs off a list (D-29, V-13).
 */
@Entity(
    tableName = "srs_state",
    primaryKeys = ["study_item_id", "card_kind"],
    foreignKeys = [
        ForeignKey(
            entity = StudyItemRow::class,
            parentColumns = ["id"],
            childColumns = ["study_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // Serves the review queue: "what of this kind is due by now?"
    indices = [Index(value = ["card_kind", "due_at"])],
)
data class SrsStateRow(
    @ColumnInfo(name = "study_item_id") val studyItemId: String,
    /** `REVIEW` — the two-field card (D-107) — or `WRITING`, opt-in (D-109). */
    @ColumnInfo(name = "card_kind") val cardKind: String,
    /** `LEARNING`, `REVIEW` or `RELEARNING` — FSRS's own states. */
    @ColumnInfo(name = "fsrs_state") val fsrsState: String,
    /** Position in the short learning steps; null once in `REVIEW`. */
    @ColumnInfo(name = "step") val step: Int?,
    /** FSRS: days until recall probability falls to ~90%. Null until the first answer. */
    @ColumnInfo(name = "stability") val stability: Double?,
    /** FSRS: intrinsic difficulty, 1–10. Null until the first answer. */
    @ColumnInfo(name = "difficulty") val difficulty: Double?,
    /** Epoch ms, UTC. "Due today" is local and worked out at query time (D-112). */
    @ColumnInfo(name = "due_at") val dueAt: Long,
    /** Epoch ms, UTC; null means never reviewed — a new card. */
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * Every answer ever given (D-112). Schema v3. **Append-only, and the source of
 * truth**: [SrsStateRow] is rebuilt from it, never the other way round.
 *
 * No `deleted_at`, because nothing removes an answer — there is no undo in v1
 * (D-112). A retry after a different-word nudge (D-108) is not graded and so
 * not logged.
 *
 * The meaning and reading outcomes are kept apart although one pass decides the
 * schedule (D-107): if one card per word is ever split into two, each half's
 * history can be replayed into its own schedule rather than invented.
 */
@Entity(
    tableName = "review_log",
    foreignKeys = [
        ForeignKey(
            entity = StudyItemRow::class,
            parentColumns = ["id"],
            childColumns = ["study_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // One card's answers in order — exactly what a replay reads.
    indices = [Index(value = ["study_item_id", "card_kind", "reviewed_at"])],
)
data class ReviewLogRow(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "study_item_id") val studyItemId: String,
    @ColumnInfo(name = "card_kind") val cardKind: String,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    /** Right or wrong: what FSRS is fed (D-107). The mapping to FSRS's grades lives in code. */
    @ColumnInfo(name = "passed") val passed: Boolean,
    /** What was typed. Null on a writing card, which has no fields. */
    @ColumnInfo(name = "meaning_answer") val meaningAnswer: String?,
    @ColumnInfo(name = "meaning_correct") val meaningCorrect: Boolean?,
    /** True when "I was right" turned the meaning right (D-108). */
    @ColumnInfo(name = "meaning_overridden") val meaningOverridden: Boolean?,
    /** The kana, after romaji conversion (D-108). */
    @ColumnInfo(name = "reading_answer") val readingAnswer: String?,
    @ColumnInfo(name = "reading_correct") val readingCorrect: Boolean?,
    /** Universal (D-80), though an answer never changes once written. */
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * A meaning the learner taught the app for one word, by pressing "I was right"
 * (D-108). Schema v3. User data: UUID key, tombstoned, backed up (D-80).
 */
@Entity(
    tableName = "user_synonym",
    foreignKeys = [
        ForeignKey(
            entity = StudyItemRow::class,
            parentColumns = ["id"],
            childColumns = ["study_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // One row per (word, synonym); re-adding a removed one revives it.
    indices = [Index(value = ["study_item_id", "text"], unique = true)],
)
data class UserSynonymRow(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "study_item_id") val studyItemId: String,
    /** As typed when "I was right" was pressed. */
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
)
