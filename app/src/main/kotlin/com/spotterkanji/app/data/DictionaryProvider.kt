package com.spotterkanji.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Room
import com.spotterkanji.data.dictionary.DictionaryDatabase
import com.spotterkanji.data.dictionary.RoomDictionaryRepository
import com.spotterkanji.domain.dictionary.DictionaryRepository
import java.io.File

/**
 * Builds the dictionary database, and keeps the extracted copy in step with the
 * one shipped in the APK.
 *
 * This lives in `:app` for one reason: `Room.databaseBuilder` needs a `Context`,
 * and `android.*` is not permitted in `:data` (D-60). Everything else about the
 * dictionary — entities, queries, mapping — stays down there.
 *
 * Deliberately no dependency-injection framework yet. `architecture.md` says to
 * add Hilt once the app works, not before.
 */
object DictionaryProvider {

    private const val TAG = "DictionaryProvider"

    /**
     * The extracted copy's filename, not the asset's.
     *
     * Room copies the asset out to internal storage on first open, so the device
     * holds both: ~30 MB compressed inside the APK and ~100 MB extracted.
     */
    private const val DATABASE_FILE = "dictionary.db"

    @Volatile
    private var database: DictionaryDatabase? = null

    fun database(context: Context): DictionaryDatabase =
        database ?: synchronized(this) {
            database ?: build(context.applicationContext).also { database = it }
        }

    fun repository(context: Context): DictionaryRepository =
        RoomDictionaryRepository(database(context).dictionaryDao())

    private fun build(context: Context): DictionaryDatabase {
        discardExtractedCopyIfStale(context, shippedBuildId(context))
        return Room.databaseBuilder(context, DictionaryDatabase::class.java, DATABASE_FILE)
            .createFromAsset(DictionaryDatabase.ASSET_NAME)
            // Queries stay off the main thread. The dictionary is read-only, but
            // these are still disk reads of a ~100 MB file.
            .build()
    }

    /** The `build_id` of the dictionary inside this APK (D-65). */
    private fun shippedBuildId(context: Context): String =
        context.assets.open(DictionaryDatabase.BUILD_ID_ASSET_NAME)
            .bufferedReader()
            .use { it.readText() }
            .trim()

    /**
     * Deletes the extracted database when it came from a different build.
     *
     * **Room copies an asset out exactly once and never looks at it again.** It
     * compares schema versions, not contents, so shipping a dictionary rebuilt
     * from newer JMdict changes no version and Room keeps serving the old copy
     * indefinitely — an app that looks perfectly healthy and answers with stale
     * data. During development this surfaced as instrumented tests that only
     * passed after `adb uninstall`.
     *
     * Deleting is the whole fix: Room re-extracts on the next open. That is safe
     * precisely because this database is read-only and disposable (D-38) — there
     * is no user data in it to lose. The user database, when it arrives, gets
     * the opposite treatment (D-16, D-17).
     *
     * Visible for testing.
     */
    internal fun discardExtractedCopyIfStale(context: Context, shippedBuildId: String): Boolean {
        val extracted = context.getDatabasePath(DATABASE_FILE)
        if (!extracted.exists()) return false

        val installedBuildId = readBuildId(extracted)
        if (installedBuildId == shippedBuildId) return false

        Log.i(
            TAG,
            "Dictionary changed (installed=${installedBuildId ?: "unreadable"}, " +
                "shipped=$shippedBuildId) — discarding the extracted copy.",
        )
        // Close before deleting. Unlinking an open SQLite file succeeds on
        // Linux, but the existing handle keeps pointing at the old inode, so a
        // still-open Room instance would go on serving the dictionary that was
        // just "deleted" — with no error anywhere.
        synchronized(this) {
            database?.close()
            database = null
        }

        // -wal and -shm must go too. Leaving them beside a fresh copy is how a
        // database ends up half-old.
        listOf(extracted, File("${extracted.path}-wal"), File("${extracted.path}-shm"))
            .filter { it.exists() }
            .forEach { if (!it.delete()) Log.w(TAG, "Could not delete ${it.name}") }

        return true
    }

    /**
     * The `build_id` recorded inside an extracted database, or null if it cannot
     * be read.
     *
     * Null is treated as stale by the caller, which is the safe direction: a copy
     * whose `meta` table is missing, empty or from an older schema is exactly the
     * copy that should be replaced.
     */
    private fun readBuildId(file: File): String? = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT build_id FROM meta LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read build_id from the extracted dictionary", e)
        null
    }
}
