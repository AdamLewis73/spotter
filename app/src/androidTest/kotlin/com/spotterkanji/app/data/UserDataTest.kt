package com.spotterkanji.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spotterkanji.data.user.RoomSavedItemsRepository
import com.spotterkanji.data.user.RoomSavedListRepository
import com.spotterkanji.data.user.UserDatabase
import com.spotterkanji.domain.user.SavedList
import com.spotterkanji.domain.user.StudyItem
import com.spotterkanji.domain.user.StudyItemKey
import com.spotterkanji.domain.user.StudyItemType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The user database, against real SQLite.
 *
 * Instrumented rather than JVM because the things worth proving here are things
 * only the real engine does: the unique index that makes saving idempotent, the
 * transaction that makes it safe, and the membership and tombstone filtering
 * that lives in the DAO's SQL rather than in Kotlin.
 *
 * Every case below corresponds to a way user data gets destroyed or misreported
 * silently — merged words (D-12), duplicated rows, a deletion a restore undoes
 * (D-16, D-80), or a word that claims to be saved while appearing nowhere
 * (D-89). None of them produce an error at the time; they produce a learner
 * months later wondering why their reviews are wrong.
 *
 * **Saved means filed** (D-88, D-89). Creating the row is only half of it, which
 * is why almost every case here goes through [fileInto] rather than calling
 * `save` alone.
 */
@RunWith(AndroidJUnit4::class)
class UserDataTest {

    private lateinit var db: UserDatabase
    private lateinit var items: RoomSavedItemsRepository
    private lateinit var lists: RoomSavedListRepository

    private val sensei = StudyItemKey("先生", "せんせい")
    private val seisan = StudyItemKey("生産", "せいさん")

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // In-memory, so each test starts empty and nothing touches the real
        // saved words on the device. Note there is still no destructive
        // migration fallback here (D-17) — the ban has no test-only exception,
        // because that is exactly how the habit gets in.
        db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java).build()
        items = RoomSavedItemsRepository(db)
        lists = RoomSavedListRepository(db)
    }

    @After
    fun tearDown() = db.close()

    /**
     * What the list picker will do: create the word, then file it (D-88, D-91).
     *
     * Saving alone leaves a row nothing displays, so a test that called `save`
     * and then asserted the word was saved would be asserting the bug this file
     * exists to prevent.
     */
    private suspend fun fileInto(
        list: SavedList,
        key: StudyItemKey,
        gloss: String = "teacher; instructor; master",
    ): StudyItem = items.save(key, gloss).also { lists.addToList(list.id, it.id) }

    @Test
    fun filing_a_word_makes_it_saved() = runBlocking {
        val signs = lists.createList("Street Signs")
        assertFalse(items.observeIsSaved(sensei).first())

        fileInto(signs, sensei)

        assertTrue(items.observeIsSaved(sensei).first())
        assertEquals(1, items.observeSaved().first().size)
        assertEquals("teacher; instructor; master", items.find(sensei)?.snapshotGloss)
    }

    /**
     * The defect this change exists to remove (D-89).
     *
     * A row with no list membership is real, keeps its history, and is invisible
     * — so it must not report itself saved. Answering "does a row exist" would
     * show a filled button for a word the user cannot find anywhere.
     */
    @Test
    fun a_word_that_is_never_filed_is_not_saved() = runBlocking {
        items.save(sensei, "teacher")

        assertFalse(items.observeIsSaved(sensei).first())
        assertTrue(items.observeSaved().first().isEmpty())
        assertEquals(null, items.find(sensei))
    }

    /**
     * The identity checkpoint, at the database rather than in the type (D-12).
     *
     * The unique index is on (text, reading, type). If it were on text alone,
     * the second save here would collide and the third would too, and the user
     * would end up with one 上手 whose reading depends on which was saved first.
     */
    @Test
    fun the_three_readings_of_jouzu_save_separately() = runBlocking {
        val signs = lists.createList("Street Signs")
        fileInto(signs, StudyItemKey("上手", "じょうず"), "skillful; skilled")
        fileInto(signs, StudyItemKey("上手", "うわて"), "upper part; upper hand")
        fileInto(signs, StudyItemKey("上手", "かみて"), "stage left")

        val saved = items.observeSaved().first()
        assertEquals(3, saved.size)
        assertEquals(3, saved.map { it.key.reading }.toSet().size)
    }

    /**
     * V-14, at the database: the `type` discriminator is written, and it is part
     * of the identity (D-27, D-92).
     *
     * 生 is both a word — なま, "raw" — and a kanji the learner can save on its
     * own since D-92. They are two study items, and the only thing separating
     * them in the unique index is `type`, because the kanji row's reading is
     * deliberately empty. If `type` were ever defaulted or ignored, these two
     * would collide: saving one would silently become an update of the other,
     * and the learner's kanji card would acquire a word's gloss.
     */
    @Test
    fun a_kanji_and_a_word_written_the_same_way_are_two_items() = runBlocking {
        val signs = lists.createList("Street Signs")
        val word = fileInto(signs, StudyItemKey("生", "なま", StudyItemType.WORD), "raw; unprocessed")
        val kanji = fileInto(signs, StudyItemKey("生", "", StudyItemType.KANJI), "life; birth")

        assertNotEquals(word.id, kanji.id)
        assertEquals(2, items.observeSaved().first().size)
        assertEquals(StudyItemType.WORD, items.find(StudyItemKey("生", "なま"))?.key?.type)
        assertEquals(StudyItemType.KANJI, items.find(StudyItemKey("生", "", StudyItemType.KANJI))?.key?.type)

        // Explicitly stored, never null and never left to a default — the whole
        // point of V-14, and invisible from the model layer above.
        db.openHelper.readableDatabase
            .query("SELECT type FROM study_item WHERE text = '生' ORDER BY type")
            .use { row ->
                val types = buildList { while (row.moveToNext()) add(row.getString(0)) }
                assertEquals(listOf("KANJI", "WORD"), types)
            }
    }

    /** Save is idempotent: the button can be tapped twice, or a sync can race it. */
    @Test
    fun saving_twice_does_not_duplicate() = runBlocking {
        val signs = lists.createList("Street Signs")
        val first = fileInto(signs, sensei, "teacher")
        val second = items.save(sensei, "teacher; instructor; master")

        assertEquals(first.id, second.id)
        assertEquals(1, items.observeSaved().first().size)
        // The snapshot refreshes — a live lookup just succeeded, so its result
        // is newer than the stored one (D-43).
        assertEquals("teacher; instructor; master", second.snapshotGloss)
        // Saving a word that is ALREADY saved leaves created_at alone: tapping a
        // button that was already on must not shuffle the Saved list (D-82).
        assertEquals(first.createdAt, second.createdAt)
    }

    /**
     * A word in several lists appears **once** in the flat saved list.
     *
     * This is why the query uses `EXISTS` rather than a join. A join against
     * `list_membership` returns one row per membership, so 先生 filed in three
     * places would be drawn three times on the Saved screen — a bug that looks
     * like duplicated data rather than a duplicated row.
     */
    @Test
    fun a_word_in_two_lists_appears_once_in_the_saved_list() = runBlocking {
        val signs = lists.createList("Street Signs")
        val menu = lists.createList("Food Menu")
        val word = fileInto(signs, sensei)
        lists.addToList(menu.id, word.id)

        assertEquals(1, items.observeSaved().first().size)
    }

    /**
     * Taking a word out of its **only** list hides it (D-89).
     *
     * It is not deleted — the next case proves the row and its history survive —
     * but it must stop reporting itself saved, or the peek sheet shows a filled
     * button for a word that is in no list and appears in no count.
     */
    @Test
    fun removing_from_its_only_list_hides_the_word() = runBlocking {
        val signs = lists.createList("Street Signs")
        val word = fileInto(signs, sensei)

        lists.removeFromList(signs.id, word.id)

        assertTrue(lists.observeItemsIn(signs.id).first().isEmpty())
        assertFalse(items.observeIsSaved(sensei).first())
        assertTrue(items.observeSaved().first().isEmpty())
    }

    /**
     * ...and filing it again brings back **the same row**, history included.
     *
     * The id is what Phase 7's `review_log` will hang off, so an unfile-and-refile
     * that minted a new id would silently orphan every review of that word. The
     * proof is that the id matches and `created_at` never moved — unlike a
     * delete-and-resave, which resets it deliberately (D-82).
     */
    @Test
    fun refiling_an_unfiled_word_restores_the_same_row() = runBlocking {
        val signs = lists.createList("Street Signs")
        val original = fileInto(signs, sensei)
        lists.removeFromList(signs.id, original.id)

        lists.addToList(signs.id, original.id)

        val restored = items.find(sensei)
        assertEquals(original.id, restored?.id)
        assertEquals(original.createdAt, restored?.createdAt)
        assertEquals(1, items.observeSaved().first().size)
    }

    /**
     * The D-80 case, stated correctly: removing a word from **one of two** lists
     * removes the membership, not the word.
     */
    @Test
    fun removing_from_one_of_two_lists_keeps_the_word_saved() = runBlocking {
        val signs = lists.createList("Street Signs")
        val menu = lists.createList("Food Menu")
        val word = fileInto(signs, sensei)
        lists.addToList(menu.id, word.id)

        lists.removeFromList(signs.id, word.id)

        assertTrue(lists.observeItemsIn(signs.id).first().isEmpty())
        assertTrue(items.observeIsSaved(sensei).first())
    }

    @Test
    fun a_word_can_be_in_two_lists_at_once() = runBlocking {
        val signs = lists.createList("Street Signs")
        val menu = lists.createList("Food Menu")
        val word = fileInto(signs, sensei)
        lists.addToList(menu.id, word.id)

        assertEquals(1, lists.observeItemsIn(signs.id).first().size)
        assertEquals(1, lists.observeItemsIn(menu.id).first().size)
        assertEquals(
            setOf("Street Signs", "Food Menu"),
            lists.observeListsContaining(word.id).first().map { it.name }.toSet(),
        )
    }

    /** Adding twice is a no-op, not a second row — the unique index would reject it. */
    @Test
    fun adding_to_a_list_twice_does_not_duplicate() = runBlocking {
        val signs = lists.createList("Street Signs")
        val word = fileInto(signs, sensei)

        lists.addToList(signs.id, word.id)

        assertEquals(1, lists.observeItemsIn(signs.id).first().size)
    }

    /** Re-adding after a removal revives the membership rather than colliding with its tombstone. */
    @Test
    fun re_adding_after_removal_works() = runBlocking {
        val signs = lists.createList("Street Signs")
        val word = fileInto(signs, sensei)

        lists.removeFromList(signs.id, word.id)
        lists.addToList(signs.id, word.id)

        assertEquals(1, lists.observeItemsIn(signs.id).first().size)
    }

    /** Deleting a list must not delete the words in it — they have lives elsewhere (D-29). */
    @Test
    fun deleting_a_list_keeps_its_words() = runBlocking {
        val signs = lists.createList("Street Signs")
        val menu = lists.createList("Food Menu")
        val word = fileInto(signs, sensei)
        lists.addToList(menu.id, word.id)

        lists.deleteList(signs.id)

        assertTrue(lists.observeLists().first().none { it.name == "Street Signs" })
        assertTrue(items.observeIsSaved(sensei).first())
        assertEquals(1, lists.observeItemsIn(menu.id).first().size)
    }

    /**
     * Deleting a word's **only** list unfiles it, on the same terms as removing
     * it by hand: kept, hidden, and restored intact when filed again (D-89).
     */
    @Test
    fun deleting_the_only_list_hides_its_words_without_losing_them() = runBlocking {
        val signs = lists.createList("Street Signs")
        val word = fileInto(signs, sensei)

        lists.deleteList(signs.id)

        assertFalse(items.observeIsSaved(sensei).first())

        val elsewhere = lists.createList("Food Menu")
        lists.addToList(elsewhere.id, word.id)
        assertEquals(word.id, items.find(sensei)?.id)
    }

    /**
     * Unsaving a word takes it out of every list it was in.
     *
     * A live membership pointing at a dead word is the pair a restore can revive
     * into a list the user had already emptied it from, which is the failure
     * D-80 describes seen from the other end.
     */
    @Test
    fun unsaving_a_word_removes_it_from_its_lists() = runBlocking {
        val signs = lists.createList("Street Signs")
        val word = fileInto(signs, sensei)

        items.unsave(sensei)

        assertTrue(lists.observeItemsIn(signs.id).first().isEmpty())
        assertTrue(lists.observeListsContaining(word.id).first().isEmpty())
    }

    /**
     * An **unfiled** word can still be deleted outright.
     *
     * It is invisible, not absent, so `unsave` looks it up without the
     * membership requirement. Without that, unfiled rows would be undeletable
     * and would accumulate forever where nothing could reach them.
     */
    @Test
    fun an_unfiled_word_can_still_be_deleted() = runBlocking {
        val original = items.save(sensei, "teacher")

        items.unsave(sensei)

        // Proof it was really tombstoned rather than merely still invisible:
        // saving again revives the same row and D-82 moves created_at forward.
        val revived = items.save(sensei, "teacher")
        assertEquals(original.id, revived.id)
        assertTrue(revived.createdAt.isAfter(original.createdAt))
    }

    /**
     * Re-saving a **deleted** word resets `created_at` (D-82).
     *
     * The Saved list is ordered newest-first, so keeping the original date would
     * drop a word the user had just re-saved into the middle of the list — they
     * would go looking for it at the top and not find it. The row id is still
     * the original, which is the part Phase 7's review history depends on.
     */
    @Test
    fun resaving_a_deleted_word_moves_it_to_the_top() = runBlocking {
        val signs = lists.createList("Street Signs")
        val original = fileInto(signs, sensei)
        items.unsave(sensei)

        val revived = items.save(sensei, "teacher")

        assertEquals(original.id, revived.id)
        assertTrue(
            "created_at should move forward on revive, was ${original.createdAt} " +
                "and is ${revived.createdAt}",
            revived.createdAt.isAfter(original.createdAt),
        )
    }

    /**
     * And the newest-first ordering that reset exists to serve.
     *
     * Asserted through `observeSaved` rather than on the timestamp alone,
     * because the ordering is the thing the user actually sees.
     */
    @Test
    fun a_resaved_word_leads_the_saved_list() = runBlocking {
        val signs = lists.createList("Street Signs")
        val word = fileInto(signs, sensei)
        fileInto(signs, seisan, "production")
        // 生産 is newest, so it leads.
        assertEquals("生産", items.observeSaved().first().first().key.text)

        items.unsave(sensei)
        items.save(sensei, "teacher")
        lists.addToList(signs.id, word.id)

        assertEquals("先生", items.observeSaved().first().first().key.text)
    }

    /**
     * The Saved screen's per-list counts.
     *
     * A word in two lists counts in **both** — it really is in both — which is
     * what makes the header total a separate question rather than a sum.
     */
    @Test
    fun list_summaries_count_the_words_in_each_list() = runBlocking {
        val signs = lists.createList("Street Signs")
        val menu = lists.createList("Food Menu")
        val word = fileInto(signs, sensei)
        lists.addToList(menu.id, word.id)
        fileInto(menu, seisan, "production")

        val summaries = lists.observeListSummaries().first().associateBy { it.list.name }
        assertEquals(1, summaries.getValue("Street Signs").wordCount)
        assertEquals(2, summaries.getValue("Food Menu").wordCount)
    }

    /**
     * ...and the header total counts each word **once**, however many lists hold
     * it.
     *
     * Summing the per-list counts would report 3 for the two words filed here,
     * because 先生 is in both lists. That is wrong in a way that looks plausible
     * and grows with how carefully the user organises — which is why the total
     * is its own query rather than a sum.
     */
    @Test
    fun the_saved_total_counts_a_word_once_however_many_lists_hold_it() = runBlocking {
        val signs = lists.createList("Street Signs")
        val menu = lists.createList("Food Menu")
        val word = fileInto(signs, sensei)
        lists.addToList(menu.id, word.id)
        fileInto(menu, seisan, "production")

        val perList = lists.observeListSummaries().first().sumOf { it.wordCount }
        assertEquals("the sum over lists double-counts on purpose", 3, perList)
        assertEquals(2, items.observeSavedCount().first())
    }

    /** An empty list still appears, counting zero rather than vanishing. */
    @Test
    fun an_empty_list_appears_with_a_zero_count() = runBlocking {
        lists.createList("Street Signs")

        val summaries = lists.observeListSummaries().first()
        assertEquals(1, summaries.size)
        assertEquals(0, summaries.first().wordCount)
    }

    /** Unfiled words are outside the total, on the same terms as everywhere else (D-89). */
    @Test
    fun the_saved_total_ignores_unfiled_words() = runBlocking {
        val signs = lists.createList("Street Signs")
        val word = fileInto(signs, sensei)
        assertEquals(1, items.observeSavedCount().first())

        lists.removeFromList(signs.id, word.id)

        assertEquals(0, items.observeSavedCount().first())
        assertEquals(0, lists.observeListSummaries().first().first().wordCount)
    }

    /**
     * Re-filing a word through the picker puts it at the **top** of the list (D-93).
     *
     * Asserted through the order the list screen shows, not through `added_at`,
     * because the order is what the user sees. The clock steps a second per
     * reading: with the real clock, adds made in the same millisecond tie, and an
     * ordering test would pass or fail by luck.
     */
    @Test
    fun refiling_through_the_picker_moves_the_word_to_the_top() = runBlocking {
        val clocked = RoomSavedListRepository(db, clock = SteppingClock())
        val signs = clocked.createList("Street Signs")
        val sensei = items.save(StudyItemKey("先生", "せんせい"), "teacher")
        val deguchi = items.save(StudyItemKey("出口", "でぐち"), "exit")
        clocked.addToList(signs.id, sensei.id)
        clocked.addToList(signs.id, deguchi.id)
        // Newest first: 出口 was filed last.
        assertEquals(listOf("出口", "先生"), clocked.observeItemsIn(signs.id).first().map { it.key.text })

        clocked.removeFromList(signs.id, sensei.id)
        clocked.addToList(signs.id, sensei.id)

        assertEquals(listOf("先生", "出口"), clocked.observeItemsIn(signs.id).first().map { it.key.text })
    }

    /**
     * ...whereas Undo puts it back **exactly where it was** (D-93).
     *
     * Undo cancels a removal rather than making a new addition, so a word
     * restored this way must not jump to the top. This is the whole reason
     * `restoreToList` exists beside `addToList`.
     */
    @Test
    fun undo_restores_the_word_to_its_original_place() = runBlocking {
        val clocked = RoomSavedListRepository(db, clock = SteppingClock())
        val signs = clocked.createList("Street Signs")
        val sensei = items.save(StudyItemKey("先生", "せんせい"), "teacher")
        val deguchi = items.save(StudyItemKey("出口", "でぐち"), "exit")
        clocked.addToList(signs.id, sensei.id)
        clocked.addToList(signs.id, deguchi.id)
        val before = clocked.observeItemsIn(signs.id).first().map { it.key.text }

        clocked.removeFromList(signs.id, sensei.id)
        clocked.restoreToList(signs.id, sensei.id)

        assertEquals(before, clocked.observeItemsIn(signs.id).first().map { it.key.text })
    }

    /**
     * Filing a word already live in a list leaves it where it is, rather than
     * bumping it.
     *
     * **Not reachable through the app**: the picker locks lists that already hold
     * the word (D-91), so no user action files into one. This pins the
     * repository's contract for other callers — chiefly Phase 8's import (D-20),
     * which replays memberships that may already exist and must not reorder or
     * duplicate them.
     */
    @Test
    fun filing_a_word_already_in_the_list_does_not_move_it() = runBlocking {
        val clocked = RoomSavedListRepository(db, clock = SteppingClock())
        val signs = clocked.createList("Street Signs")
        val sensei = items.save(StudyItemKey("先生", "せんせい"), "teacher")
        val deguchi = items.save(StudyItemKey("出口", "でぐち"), "exit")
        clocked.addToList(signs.id, sensei.id)
        clocked.addToList(signs.id, deguchi.id)
        val before = clocked.observeItemsIn(signs.id).first().map { it.key.text }

        clocked.addToList(signs.id, sensei.id)

        assertEquals(before, clocked.observeItemsIn(signs.id).first().map { it.key.text })
    }

    /** Two lists may share a name; identity is the UUID (D-15). */
    @Test
    fun two_lists_may_share_a_name() = runBlocking {
        val a = lists.createList("Food")
        val b = lists.createList("Food")

        assertNotEquals(a.id, b.id)
        assertEquals(2, lists.observeLists().first().size)
    }
}

/**
 * A clock that moves forward one second every time it is read.
 *
 * Ordering tests need distinct timestamps, and real operations inside one test
 * routinely land in the same millisecond — which makes a newest-first
 * assertion depend on scheduling rather than on the code under test.
 * Shared with [ScanPhotoTest].
 */
internal class SteppingClock(
    private var now: Instant = Instant.parse("2026-01-01T00:00:00Z"),
) : Clock() {
    override fun instant(): Instant = now.also { now = now.plusSeconds(1) }
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
}
