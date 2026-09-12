package com.spotterkanji.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spotterkanji.data.user.RoomSavedItemsRepository
import com.spotterkanji.data.user.RoomSavedListRepository
import com.spotterkanji.data.user.UserDatabase
import com.spotterkanji.domain.user.StudyItemKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The user database's upgrades, against real old databases (D-17, D-18).
 *
 * This is the only proof that an upgrade keeps a user's data rather than an
 * assumption that it does. Every step is built from the schema JSON committed
 * for that version — which is why D-18 requires committing them — so the old
 * database here is the one a real user would have, not a guess at it.
 *
 * **Why the upgrade runs from every old version, not only the previous one.** A
 * user can arrive from any earlier version: a phone left un-updated for a year,
 * or an old Auto Backup restored into a new install. Room walks the chain one
 * step at a time, so a broken middle step fails exactly those users and nobody
 * who updates promptly. [OLDEST] upward is covered, and a new version extends
 * this automatically.
 */
@RunWith(AndroidJUnit4::class)
class UserMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        UserDatabase::class.java,
    )

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * v1 → v2 (D-94): saved words, lists and memberships all survive, and the two
     * new photo tables exist and are empty.
     *
     * `runMigrationsAndValidate` also checks the result against the committed
     * `2.json` exactly — every column, index and foreign key — so an upgrade that
     * produced a subtly different schema from a fresh install fails here.
     */
    @Test
    fun v1_to_v2_keeps_every_saved_word_and_list() {
        helper.createDatabase(DB, 1).use { v1 -> seedVersion1(v1) }

        helper.runMigrationsAndValidate(DB, 2, true).use { v2 ->
            v2.query("SELECT text, reading, type, snapshot_gloss FROM study_item ORDER BY text").use {
                assertEquals(2, it.count)
                it.moveToFirst()
                assertEquals("先生", it.getString(0))
                assertEquals("せんせい", it.getString(1))
                assertEquals("WORD", it.getString(2))
                assertEquals("teacher; instructor; master", it.getString(3))
            }
            v2.query("SELECT name FROM saved_list").use {
                it.moveToFirst()
                assertEquals("Street Signs", it.getString(0))
            }
            v2.query("SELECT COUNT(*) FROM list_membership WHERE deleted_at IS NULL").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
            // The tombstone survives too — a deleted word must stay deleted.
            v2.query("SELECT deleted_at FROM study_item WHERE text = '生産'").use {
                it.moveToFirst()
                assertEquals(DELETED_AT, it.getLong(0))
            }
            for (table in listOf("scan", "scan_word")) {
                v2.query("SELECT COUNT(*) FROM $table").use {
                    it.moveToFirst()
                    assertEquals("$table should arrive empty", 0, it.getInt(0))
                }
            }
        }
    }

    /**
     * Every historical version upgrades to the current one, through Room's real
     * open path — and the app's own repositories then read the old data.
     *
     * The step above proves the SQL. This proves the thing a user experiences:
     * install the new version over the old, open the app, and their words are
     * there. It uses a plain `Room.databaseBuilder` with no destructive fallback
     * (D-17), so a missing migration fails the test instead of emptying the
     * database and passing.
     */
    @Test
    fun every_old_version_opens_in_the_current_app_with_its_words() = runBlocking {
        for (from in OLDEST until UserDatabase.SCHEMA_VERSION) {
            val name = "$DB-from-$from"
            helper.createDatabase(name, from).use { seedVersion1(it) }

            val db = Room.databaseBuilder(context, UserDatabase::class.java, name)
                .build()
            try {
                val items = RoomSavedItemsRepository(db)
                val lists = RoomSavedListRepository(db)
                assertTrue(
                    "a word filed before the upgrade must still be saved after it (from v$from)",
                    items.observeIsSaved(StudyItemKey("先生", "せんせい")).first(),
                )
                assertEquals(1, lists.observeListSummaries().first().single().wordCount)
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }
    }

    /**
     * Writes a representative v1 database with raw SQL, as v1's schema defined it.
     *
     * Raw SQL rather than the current entities on purpose: the current code
     * describes the NEWEST schema, and seeding an old database through it would
     * test nothing. This is what a v1 install actually wrote.
     *
     * Includes a filed word, a list, the membership joining them, and a
     * tombstoned word — the rows most likely to be lost or resurrected by a bad
     * upgrade.
     */
    private fun seedVersion1(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            """INSERT INTO study_item
               (id, type, text, reading, ent_seq, snapshot_gloss, created_at, updated_at, deleted_at)
               VALUES ('item-1', 'WORD', '先生', 'せんせい', 1387990,
                       'teacher; instructor; master', 1000, 1000, NULL)"""
        )
        db.execSQL(
            """INSERT INTO study_item
               (id, type, text, reading, ent_seq, snapshot_gloss, created_at, updated_at, deleted_at)
               VALUES ('item-2', 'WORD', '生産', 'せいさん', NULL,
                       'production', 1000, 2000, $DELETED_AT)"""
        )
        db.execSQL(
            """INSERT INTO saved_list (id, name, created_at, updated_at, deleted_at)
               VALUES ('list-1', 'Street Signs', 1000, 1000, NULL)"""
        )
        db.execSQL(
            """INSERT INTO list_membership
               (id, list_id, study_item_id, added_at, updated_at, deleted_at)
               VALUES ('m-1', 'list-1', 'item-1', 1000, 1000, NULL)"""
        )
    }

    private companion object {
        const val DB = "migration-test"
        const val OLDEST = 1
        const val DELETED_AT = 2000L
    }
}
