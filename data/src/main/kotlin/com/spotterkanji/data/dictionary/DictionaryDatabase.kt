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
    entities = [WordRow::class, WordSenseRow::class, MetaRow::class],
    version = 1,
    exportSchema = true,
)
abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        /** Matches the file staged into assets by `:app:stageDictionaryAsset`. */
        const val ASSET_NAME: String = "spotter.db"
    }
}
