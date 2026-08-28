package com.spotterkanji.data.user

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The writable user database (D-09) — saved words and the lists they are filed
 * under. Entirely separate from `DictionaryDatabase`, and the separation is the
 * point: the dictionary is disposable and thrown away whenever it changes
 * (D-38), while **nothing in this file is ever recoverable once lost**.
 *
 * That asymmetry is why the two are different databases rather than different
 * tables. A single database would make every dictionary refresh a migration of
 * the user's saved words, and the shortcut out of a migration error is the one
 * call this project bans.
 *
 * ### The rule this class exists to hold
 *
 * **`fallbackToDestructiveMigration()` is banned in every build type (D-17),
 * debug included.** Room requires a `Migration` for each version bump and
 * crashes on launch without one; that call resolves the crash by deleting the
 * entire database and recreating it empty. It is in a large fraction of Room
 * tutorials because it makes the development crash go away, and it is the most
 * common way Android apps destroy production data. CI greps for it.
 *
 * The correct response to that crash is always to write the migration. A debug
 * database is still somebody's saved words, and the habit is the hazard.
 *
 * ### Versioning
 *
 * [SCHEMA_VERSION] is bumped whenever the tables or columns Room knows about
 * change, and the generated JSON under `data/schemas/` is committed (D-18) so a
 * migration is written against ground truth rather than memory. Migrations run
 * as **chains** — a user on v1 installing v4 runs 1→2→3→4 — so
 * `MigrationTestHelper` must exercise the chain, never a single hop.
 *
 * Version 1 holds saved words and lists. `srs_state` and `review_log` are Phase
 * 7's (D-79) and arrive as added tables, which Room can express as an
 * `AutoMigration`; `scan` and `scan_word` follow with the image work, carrying
 * D-22's bounding box.
 *
 * Note this class takes no `Context` and imports nothing from `android.*`. The
 * construction that needs one lives in `:app` (D-60).
 */
@Database(
    entities = [
        StudyItemRow::class,
        SavedListRow::class,
        ListMembershipRow::class,
    ],
    version = UserDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun studyItemDao(): StudyItemDao
    abstract fun savedListDao(): SavedListDao

    companion object {
        const val SCHEMA_VERSION: Int = 1

        /**
         * The file name under the app's database directory.
         *
         * Distinct from the dictionary's, and deliberately not "spotter.db" —
         * that name belongs to the disposable asset, and a collision would mean
         * the dictionary-refresh path deleting user data.
         */
        const val FILE_NAME: String = "user-data.db"
    }
}
