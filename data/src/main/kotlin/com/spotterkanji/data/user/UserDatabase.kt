package com.spotterkanji.data.user

import androidx.room.AutoMigration
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
 * - **v1** — saved words and lists.
 * - **v2** — adds `scan` and `scan_word`: the photos words were filed from and
 *   where each word sat on them (D-22, D-94). Added tables only, so it is an
 *   `AutoMigration`, proven against a real v1 database in `UserMigrationTest`.
 *
 * `srs_state` and `review_log` are Phase 7's (D-79) and will arrive the same way.
 *
 * Note this class takes no `Context` and imports nothing from `android.*`. The
 * construction that needs one lives in `:app` (D-60).
 */
@Database(
    entities = [
        StudyItemRow::class,
        SavedListRow::class,
        ListMembershipRow::class,
        ScanRow::class,
        ScanWordRow::class,
    ],
    version = UserDatabase.SCHEMA_VERSION,
    exportSchema = true,
    // Every step must stay listed forever. A user can arrive from ANY earlier
    // version — a phone left un-updated for a year, or an old Auto Backup
    // restored into a new install — and Room walks the chain one step at a
    // time. Deleting an old step breaks exactly those users, and only them.
    autoMigrations = [
        // v1 → v2 adds `scan` and `scan_word` (D-94) and touches nothing that
        // already exists, which is the case Room can write by itself. Anything
        // that changes an existing column is hand-written instead.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun studyItemDao(): StudyItemDao
    abstract fun savedListDao(): SavedListDao
    abstract fun scanDao(): ScanDao

    companion object {
        const val SCHEMA_VERSION: Int = 2

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
