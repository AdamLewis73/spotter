package com.spotterkanji.domain.user

import com.spotterkanji.domain.scan.TextBox
import kotlinx.coroutines.flow.Flow

/** Opaque UUID identity of one saved photograph (D-15). */
@JvmInline
value class ScanId(val value: String)

/**
 * What a list row needs to draw a word's thumbnail (D-95): which photo, and
 * where on it the word sat.
 *
 * [box] is in **the saved photo's own pixels**. Photos are stored at the size
 * they were taken and never resized (D-94), and the box was measured on that
 * same photo, so it needs no conversion to be correct for the file on disk.
 */
data class ScanThumbnail(
    val itemId: StudyItemId,
    val imagePath: String,
    val box: TextBox,
)

/**
 * The photographs words were found in (D-83, D-94).
 *
 * A photo is recorded only when a word is filed from it — there is no scan
 * history — and one photo serves every word filed from the same shutter press:
 * [createScan] once, then [linkWord] for each word.
 *
 * This layer never touches image bytes. Encoding and writing the file is
 * Android work and happens in `:app`; this stores the **relative** path it was
 * written to (D-24), which is why the interface takes a path rather than a
 * picture.
 */
interface ScanRepository {

    /** Record a saved photo. [imagePath] is relative to the app's storage root (D-24). */
    suspend fun createScan(
        imagePath: String,
        rawText: String,
        appVersion: String,
    ): ScanId

    /**
     * Record that [itemId] appears on [scanId], and where (D-22).
     *
     * [charOffset] and [charLength] locate the word in the photo's recognised
     * text, so it can be highlighted again later without re-running OCR.
     *
     * Idempotent per (photo, word): filing the same word from the same photo
     * twice — into two lists in one go, say — records it once.
     */
    suspend fun linkWord(
        scanId: ScanId,
        itemId: StudyItemId,
        box: TextBox,
        charOffset: Int,
        charLength: Int,
    )

    /**
     * The most recent photo of each of [itemIds], for a list's thumbnails.
     *
     * One query for the whole list rather than one per row. A word with no photo
     * is simply absent from the map — a normal state (D-94), not an error.
     */
    fun observeThumbnails(itemIds: List<StudyItemId>): Flow<Map<StudyItemId, ScanThumbnail>>
}
