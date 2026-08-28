package com.spotterkanji.data.user

import com.spotterkanji.domain.user.SavedList
import com.spotterkanji.domain.user.SavedListId
import com.spotterkanji.domain.user.StudyItem
import com.spotterkanji.domain.user.StudyItemId
import com.spotterkanji.domain.user.StudyItemKey
import com.spotterkanji.domain.user.StudyItemType
import java.time.Instant

/**
 * Row ↔ model conversion, kept in one file so the storage representation stops
 * here.
 *
 * Two things cross this boundary and change shape. Timestamps are epoch
 * milliseconds in SQLite and `Instant` above it, so nothing upstream handles a
 * `Long` that happens to mean a time. And [StudyItemType] is stored by **name**
 * rather than by ordinal: an ordinal silently reinterprets every existing row
 * the day a value is inserted into the middle of the enum, turning saved words
 * into saved kanji with no error to notice.
 */

internal fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)

internal fun Instant.toEpochMillisLong(): Long = toEpochMilli()

internal fun StudyItemRow.toModel() = StudyItem(
    id = StudyItemId(id),
    key = StudyItemKey(
        text = text,
        reading = reading,
        type = StudyItemType.valueOf(type),
    ),
    snapshotGloss = snapshotGloss,
    entSeq = entSeq,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
    deletedAt = deletedAt?.toInstant(),
)

internal fun SavedListRow.toModel() = SavedList(
    id = SavedListId(id),
    name = name,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
    deletedAt = deletedAt?.toInstant(),
)
