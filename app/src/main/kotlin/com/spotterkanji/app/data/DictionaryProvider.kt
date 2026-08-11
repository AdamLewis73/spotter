package com.spotterkanji.app.data

import android.content.Context
import androidx.room.Room
import com.spotterkanji.data.dictionary.DictionaryDatabase
import com.spotterkanji.data.dictionary.RoomDictionaryRepository
import com.spotterkanji.domain.dictionary.DictionaryRepository

/**
 * Builds the dictionary database.
 *
 * This lives in `:app` for one reason: `Room.databaseBuilder` needs a
 * `Context`, and `android.*` is not permitted in `:data` (D-60). Everything
 * else about the dictionary — entities, queries, mapping — stays down there;
 * only the framework handle is up here.
 *
 * Deliberately no dependency-injection framework yet. `architecture.md` says to
 * add Hilt once the app works, not before.
 */
object DictionaryProvider {

    @Volatile
    private var database: DictionaryDatabase? = null

    fun database(context: Context): DictionaryDatabase =
        database ?: synchronized(this) {
            database ?: build(context.applicationContext).also { database = it }
        }

    fun repository(context: Context): DictionaryRepository =
        RoomDictionaryRepository(database(context).dictionaryDao())

    private fun build(context: Context): DictionaryDatabase =
        Room.databaseBuilder(context, DictionaryDatabase::class.java, DATABASE_FILE)
            .createFromAsset(DictionaryDatabase.ASSET_NAME)
            // The dictionary is read-only and never written by the app, so
            // queries on the main thread would still be wrong — they are disk
            // reads of a ~100 MB file. Left at Room's default (prohibited).
            .build()

    /**
     * The extracted copy's filename, not the asset's.
     *
     * Room copies the asset out to internal storage on first open, so the device
     * holds both: ~30 MB compressed inside the APK and ~100 MB extracted.
     */
    private const val DATABASE_FILE = "dictionary.db"
}
