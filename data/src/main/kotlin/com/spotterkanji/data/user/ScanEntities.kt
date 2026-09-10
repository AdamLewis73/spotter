package com.spotterkanji.data.user

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A photograph a word was filed from (D-94).
 *
 * Written only when a word from it is filed — there is no scan history, so a
 * row here always has at least one [ScanWordRow] pointing at it. One row per
 * shutter press, shared by every word filed from that frame.
 *
 * Tombstoned rather than hard-deleted (D-16, D-80): deleting a photo is
 * something a user will eventually do from the storage screen, and a sync or a
 * restore must learn it was deliberate.
 *
 * **What is deliberately not here:**
 * - No image bytes. The photo is a file on disk; this holds its **relative**
 *   path (D-24), never a BLOB.
 * - No width or height. Photos are stored at the size they were taken (D-94),
 *   so the file records its own dimensions and the word boxes need no scaling to
 *   match it.
 * - No `image_type`. D-23 planned one to tell full frames from saved crops, but
 *   crops are only ever drawn, never saved (D-95), and the file's extension
 *   already records its format.
 */
@Entity(
    tableName = "scan",
    indices = [Index(value = ["deleted_at", "created_at"])],
)
data class ScanRow(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    /** Relative to the app's storage root, e.g. `scans/<uuid>.webp` (D-24). */
    @ColumnInfo(name = "image_path") val imagePath: String,
    /**
     * Everything that was read off the photo, laid out (D-22's "capture cheap
     * metadata now"). [ScanWordRow.charOffset] points into this.
     */
    @ColumnInfo(name = "raw_ocr_text") val rawOcrText: String,
    /** Which build wrote the row — worth having the day a format changes. */
    @ColumnInfo(name = "app_version") val appVersion: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
)

/**
 * "This word was found on this photo, here" (D-22, D-83).
 *
 * The box is in **the saved photo's own pixels** — the same pixels it was
 * measured on, because the photo is never resized (D-94). A list thumbnail draws
 * this rectangle of the photo and nothing is converted (D-95).
 *
 * Cascades with its parents rather than carrying a tombstone (D-80): a user
 * never deletes one of these directly; it goes when its photo goes. Since the
 * parents are only ever *soft*-deleted the cascade does not fire in practice,
 * which is what lets a word's photos outlive unsaving (D-83). The foreign keys
 * are the backstop for a genuine hard delete, such as a future storage clean-up.
 */
@Entity(
    tableName = "scan_word",
    foreignKeys = [
        ForeignKey(
            entity = ScanRow::class,
            parentColumns = ["id"],
            childColumns = ["scan_id"],
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
        // One row per (photo, word). Filing the same word from the same photo
        // into three lists in one Add must record the sighting once, not thrice.
        Index(value = ["scan_id", "study_item_id"], unique = true),
        // Serves "the photos of this word" — every list thumbnail.
        Index(value = ["study_item_id"]),
    ],
)
data class ScanWordRow(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "scan_id") val scanId: String,
    @ColumnInfo(name = "study_item_id") val studyItemId: String,
    @ColumnInfo(name = "bbox_x") val bboxX: Int,
    @ColumnInfo(name = "bbox_y") val bboxY: Int,
    @ColumnInfo(name = "bbox_w") val bboxW: Int,
    @ColumnInfo(name = "bbox_h") val bboxH: Int,
    @ColumnInfo(name = "char_offset") val charOffset: Int,
    @ColumnInfo(name = "char_length") val charLength: Int,
    /** Universal, including on cascading rows (D-80). */
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** A word's most recent photo and where it sits on it — a query result, not a table. */
data class ThumbnailRow(
    @ColumnInfo(name = "study_item_id") val studyItemId: String,
    @ColumnInfo(name = "image_path") val imagePath: String,
    @ColumnInfo(name = "bbox_x") val bboxX: Int,
    @ColumnInfo(name = "bbox_y") val bboxY: Int,
    @ColumnInfo(name = "bbox_w") val bboxW: Int,
    @ColumnInfo(name = "bbox_h") val bboxH: Int,
)
