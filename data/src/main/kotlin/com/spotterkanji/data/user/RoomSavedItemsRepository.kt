package com.spotterkanji.data.user

import androidx.room.withTransaction
import com.spotterkanji.domain.user.SavedItemsRepository
import com.spotterkanji.domain.user.StudyItem
import com.spotterkanji.domain.user.StudyItemKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * [SavedItemsRepository] over Room.
 *
 * [clock] and [newId] are injected rather than called directly so the behaviour
 * that matters here — that re-saving revives a row instead of duplicating it,
 * and that `updated_at` actually moves — can be asserted against known values
 * rather than against whatever the machine's clock said during the test.
 */
class RoomSavedItemsRepository(
    private val db: UserDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : SavedItemsRepository {

    private val dao get() = db.studyItemDao()

    override fun observeSaved(): Flow<List<StudyItem>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observeIsSaved(key: StudyItemKey): Flow<Boolean> =
        dao.observeByKey(key.text, key.reading, key.type.name).map { it != null }

    override suspend fun find(key: StudyItemKey): StudyItem? =
        dao.findLive(key.text, key.reading, key.type.name)?.toModel()

    /**
     * Idempotent save (see the interface for why it revives rather than
     * inserts).
     *
     * The lookup and the write are one transaction because they are otherwise a
     * check-then-act race: two rapid taps on Save, or a save arriving while a
     * sync writes, both find no row and both insert, and the second loses to
     * the unique index with an exception the user sees as the button failing.
     */
    override suspend fun save(
        key: StudyItemKey,
        snapshotGloss: String,
        entSeq: Long?,
    ): StudyItem = db.withTransaction {
        val now = Instant.now(clock).toEpochMillisLong()
        val existing = dao.findIncludingDeleted(key.text, key.reading, key.type.name)

        if (existing == null) {
            val row = StudyItemRow(
                id = newId(),
                type = key.type.name,
                text = key.text,
                reading = key.reading,
                entSeq = entSeq,
                snapshotGloss = snapshotGloss,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
            dao.insert(row)
            row.toModel()
        } else {
            // Live or tombstoned, the same call is right: it clears deleted_at
            // (a no-op when already live) and refreshes the snapshot from the
            // lookup that just succeeded, which is newer than the stored one.
            // createdAt is deliberately NOT touched — when the user first saved
            // this word is a fact, and a delete-and-resave should not rewrite it.
            dao.reviveAndRefresh(existing.id, snapshotGloss, entSeq, now)
            existing.copy(
                snapshotGloss = snapshotGloss,
                entSeq = entSeq,
                updatedAt = now,
                deletedAt = null,
            ).toModel()
        }
    }

    override suspend fun unsave(key: StudyItemKey) {
        db.withTransaction {
            val row = dao.findLive(key.text, key.reading, key.type.name) ?: return@withTransaction
            val now = Instant.now(clock).toEpochMillisLong()
            // Memberships first, then the word — order is irrelevant inside a
            // transaction, but tombstoning both is not: a live membership of a
            // dead word is the pair a restore can revive into a list the user
            // had already emptied it from.
            db.savedListDao().softDeleteMembershipsOfItem(row.id, now)
            dao.softDelete(row.id, now)
        }
    }
}
