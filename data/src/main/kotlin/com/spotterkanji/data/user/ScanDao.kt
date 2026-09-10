package com.spotterkanji.data.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Reads and writes over saved photographs and the words found on them (D-94). */
@Dao
interface ScanDao {

    @Insert
    suspend fun insertScan(row: ScanRow)

    /**
     * IGNORE rather than failing on the unique (scan, word) index: filing one
     * word into several lists from the same photo calls this once per list, and
     * the sighting should be recorded once.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertScanWord(row: ScanWordRow)

    /**
     * Every live photo of each word in [itemIds], newest first.
     *
     * The caller keeps the first per word. Returning all of them is one query
     * for a whole list screen; a query per row would be N round trips on a
     * scrolling list. Tombstoned photos are filtered here, in SQL, like every
     * other deleted row in this database.
     */
    @Query(
        """
        SELECT sw.study_item_id, s.image_path,
               sw.bbox_x, sw.bbox_y, sw.bbox_w, sw.bbox_h
        FROM scan_word AS sw
        JOIN scan AS s ON s.id = sw.scan_id
        WHERE s.deleted_at IS NULL
          AND sw.study_item_id IN (:itemIds)
        ORDER BY s.created_at DESC
        """
    )
    fun observeThumbnailRows(itemIds: List<String>): Flow<List<ThumbnailRow>>
}
