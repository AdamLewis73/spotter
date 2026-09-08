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

    /** Two lists may share a name; identity is the UUID (D-15). */
    @Test
    fun two_lists_may_share_a_name() = runBlocking {
        val a = lists.createList("Food")
        val b = lists.createList("Food")

        assertNotEquals(a.id, b.id)
        assertEquals(2, lists.observeLists().first().size)
    }
}
