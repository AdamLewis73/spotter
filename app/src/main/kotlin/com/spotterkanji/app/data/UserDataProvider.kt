package com.spotterkanji.app.data

import android.content.Context
import androidx.room.Room
import com.spotterkanji.data.user.RoomSavedItemsRepository
import com.spotterkanji.data.user.RoomSavedListRepository
import com.spotterkanji.data.user.UserDatabase
import com.spotterkanji.domain.user.SavedItemsRepository
import com.spotterkanji.domain.user.SavedListRepository

/**
 * Builds the writable user database — the counterpart to [DictionaryProvider],
 * and here for the same reason: `Room.databaseBuilder` needs a `Context`, which
 * `:data` may not import (D-60).
 *
 * **What is absent from [build] is the point of this file.** There is no
 * `createFromAsset`, because nothing seeds user data; there is no staleness
 * check, because this database is never thrown away; and above all there is no
 * `fallbackToDestructiveMigration()`, which is banned in every build type
 * including debug (D-17) and checked by CI.
 *
 * That last one is not hypothetical. The first schema change will crash the app
 * on launch with a message whose top search result recommends exactly that
 * call, and it *works* — by deleting every word the user has ever saved. The
 * correct response is always to write the migration, against the schema JSON
 * committed under `data/schemas/` (D-18).
 *
 * Deliberately no dependency-injection framework yet, matching
 * [DictionaryProvider]: `architecture.md` says to add Hilt once the app works.
 */
object UserDataProvider {

    @Volatile
    private var database: UserDatabase? = null

    fun database(context: Context): UserDatabase =
        database ?: synchronized(this) {
            database ?: build(context.applicationContext).also { database = it }
        }

    fun savedItems(context: Context): SavedItemsRepository =
        RoomSavedItemsRepository(database(context))

    fun savedLists(context: Context): SavedListRepository =
        RoomSavedListRepository(database(context))

    private fun build(context: Context): UserDatabase =
        Room.databaseBuilder(context, UserDatabase::class.java, UserDatabase.FILE_NAME)
            // Migrations are added here as the schema grows, and must be tested
            // as chains — a user on v1 installing v4 runs 1→2→3→4 (D-18).
            .build()
}
