package com.spotterkanji.data.dictionary

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The read-only bundled dictionary (D-09).
 *
 * **`version` tracks the table STRUCTURE, not the data.** Rebuilding the
 * dictionary from newer JMdict sources changes every row and no columns, so it
 * does not bump this number and needs no migration — the dictionary is
 * disposable and is never migrated (D-38). Refreshing it is a matter of
 * replacing the extracted file, which `:app` handles.
 *
 * That distinction is what keeps `fallbackToDestructiveMigration()` out of this
 * project entirely (D-17). The usual prepopulated-database recipe reaches for
 * it because it conflates "the data changed" with "the schema changed". Here
 * only a real change to `tools/dictbuild/schema.sql` bumps `version`, and that
 * is a code change arriving with matching entity edits.
 *
 * Note this class takes no `Context` and imports nothing from `android.*`. The
 * construction that does needs one lives in `:app`, which is the only module
 * allowed to touch the framework (D-60).
 */
@Database(
    entities = [
        WordRow::class,
        WordSenseRow::class,
        ExampleRow::class,
        KanjiRow::class,
        KanjiInWordRow::class,
        MetaRow::class,
    ],
    version = DictionaryDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        /**
         * Bumped whenever the set of tables or columns **Room knows about**
         * changes — including simply adding an entity for a table the file
         * always had, which is what took this from 1 to 2 when `KanjiRow`
         * arrived.
         *
         * There are no `Migration` objects, and there never will be. Room's own
         * error suggests `fallbackToDestructiveMigration*`, which is banned
         * (D-17), and a migration chain would be equally wrong: this database is
         * read-only and disposable (D-38), so the correct response to any schema
         * change is to throw the extracted copy away and re-extract from the
         * asset. `:app` does exactly that by comparing this number against the
         * copy's `PRAGMA user_version`.
         */
        const val SCHEMA_VERSION: Int = 4
        /** Matches the file staged into assets by `:app:stageDictionaryAsset`. */
        const val ASSET_NAME: String = "spotter.db"

        /**
         * Sidecar asset holding the shipped dictionary's `build_id` (D-65).
         *
         * Kept beside the database rather than read from inside it, because the
         * point is to decide whether to open the extracted copy at all — reading
         * the answer out of that copy would be circular.
         */
        const val BUILD_ID_ASSET_NAME: String = "spotter.db.build-id"
    }
}
