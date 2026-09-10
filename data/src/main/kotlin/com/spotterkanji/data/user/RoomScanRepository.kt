package com.spotterkanji.data.user

import com.spotterkanji.domain.scan.TextBox
import com.spotterkanji.domain.user.ScanId
import com.spotterkanji.domain.user.ScanRepository
import com.spotterkanji.domain.user.ScanThumbnail
import com.spotterkanji.domain.user.StudyItemId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** [ScanRepository] over Room. [clock] and [newId] are injected for tests, as elsewhere. */
class RoomScanRepository(
    private val db: UserDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ScanRepository {

    private val dao get() = db.scanDao()

    private fun now(): Long = Instant.now(clock).toEpochMilli()

    override suspend fun createScan(
        imagePath: String,
        rawText: String,
        appVersion: String,
    ): ScanId {
        val now = now()
        val row = ScanRow(
            id = newId(),
            imagePath = imagePath,
            rawOcrText = rawText,
            appVersion = appVersion,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        dao.insertScan(row)
        return ScanId(row.id)
    }

    override suspend fun linkWord(
        scanId: ScanId,
        itemId: StudyItemId,
        box: TextBox,
        charOffset: Int,
        charLength: Int,
    ) {
        dao.insertScanWord(
            ScanWordRow(
                id = newId(),
                scanId = scanId.value,
                studyItemId = itemId.value,
                bboxX = box.left,
                bboxY = box.top,
                bboxW = box.width,
                bboxH = box.height,
                charOffset = charOffset,
                charLength = charLength,
                updatedAt = now(),
            )
        )
    }

    override fun observeThumbnails(
        itemIds: List<StudyItemId>,
    ): Flow<Map<StudyItemId, ScanThumbnail>> =
        dao.observeThumbnailRows(itemIds.map { it.value }).map { rows ->
            // Rows arrive newest first, so the first seen for each word wins.
            rows.groupBy { it.studyItemId }.mapValues { (_, forWord) ->
                val newest = forWord.first()
                ScanThumbnail(
                    itemId = StudyItemId(newest.studyItemId),
                    imagePath = newest.imagePath,
                    box = TextBox(
                        left = newest.bboxX,
                        top = newest.bboxY,
                        right = newest.bboxX + newest.bboxW,
                        bottom = newest.bboxY + newest.bboxH,
                    ),
                )
            }.mapKeys { (id, _) -> StudyItemId(id) }
        }
}
