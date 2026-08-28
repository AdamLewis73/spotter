package com.spotterkanji.data.user

import androidx.room.withTransaction
import com.spotterkanji.domain.user.SavedList
import com.spotterkanji.domain.user.SavedListId
import com.spotterkanji.domain.user.SavedListRepository
import com.spotterkanji.domain.user.StudyItem
import com.spotterkanji.domain.user.StudyItemId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** [SavedListRepository] over Room. [clock] and [newId] are injected for the same reason as in [RoomSavedItemsRepository]. */
class RoomSavedListRepository(
    private val db: UserDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : SavedListRepository {

    private val dao get() = db.savedListDao()

    private fun now(): Long = Instant.now(clock).toEpochMilli()

    override fun observeLists(): Flow<List<SavedList>> =
        dao.observeLists().map { rows -> rows.map { it.toModel() } }

    override fun observeItemsIn(listId: SavedListId): Flow<List<StudyItem>> =
        dao.observeItemsIn(listId.value).map { rows -> rows.map { it.toModel() } }

    override fun observeListsContaining(itemId: StudyItemId): Flow<List<SavedList>> =
        dao.observeListsContaining(itemId.value).map { rows -> rows.map { it.toModel() } }

    override suspend fun createList(name: String): SavedList {
        require(name.isNotBlank()) { "a list needs a name" }
        val now = now()
        val row = SavedListRow(
            id = newId(),
            name = name.trim(),
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        dao.insertList(row)
        return row.toModel()
    }

    override suspend fun renameList(id: SavedListId, name: String) {
        require(name.isNotBlank()) { "a list needs a name" }
        dao.renameList(id.value, name.trim(), now())
    }

    override suspend fun deleteList(id: SavedListId) {
        db.withTransaction {
            val now = now()
            // The memberships go, the words stay. Deleting "Street Signs" must
            // not delete 先生 — it may also be in "Food Menu", and it carries
            // review history of its own that has nothing to do with either list
            // (D-29).
            dao.softDeleteMembershipsOfList(id.value, now)
            dao.softDeleteList(id.value, now)
        }
    }

    /** Idempotent, and revives a tombstoned membership — the unique index leaves nowhere to put a second one. */
    override suspend fun addToList(listId: SavedListId, itemId: StudyItemId) {
        db.withTransaction {
            val now = now()
            val existing = dao.findMembershipIncludingDeleted(listId.value, itemId.value)
            if (existing == null) {
                dao.insertMembership(
                    ListMembershipRow(
                        id = newId(),
                        listId = listId.value,
                        studyItemId = itemId.value,
                        addedAt = now,
                        updatedAt = now,
                        deletedAt = null,
                    )
                )
            } else if (existing.deletedAt != null) {
                dao.reviveMembership(existing.id, now)
            }
        }
    }

    override suspend fun removeFromList(listId: SavedListId, itemId: StudyItemId) {
        val existing = dao.findMembershipIncludingDeleted(listId.value, itemId.value) ?: return
        if (existing.deletedAt == null) dao.softDeleteMembership(existing.id, now())
    }
}
