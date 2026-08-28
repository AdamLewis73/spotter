package com.spotterkanji.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spotterkanji.data.user.RoomSavedItemsRepository
import com.spotterkanji.data.user.RoomSavedListRepository
import com.spotterkanji.data.user.UserDatabase
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
 * transaction that makes it safe, and the tombstone filtering that lives in the
 * DAO's SQL rather than in Kotlin.
 *
 * Every case below corresponds to a way user data gets destroyed silently —
 * merged words (D-12), duplicated rows, or a deletion that a restore undoes
 * (D-16, D-80). None of them produce an error at the time; they produce a
 * learner months later wondering why their reviews are wrong.
 */
@RunWith(AndroidJUnit4::class)
class UserDataTest {

    private lateinit var db: UserDatabase
    private lateinit var items: RoomSavedItemsRepository
    private lateinit var lists: RoomSavedListRepository

    private val sensei = StudyItemKey("先生", "せんせい")

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

    @Test
    fun a_saved_word_is_saved() = runBlocking {
        assertFalse(items.observeIsSaved(sensei).first())

        items.save(sensei, snapshotGloss = "teacher; instructor; master")

        assertTrue(items.observeIsSaved(sensei).first())
        assertEquals(1, items.observeSaved().first().size)
        assertEquals("teacher; instructor; master", items.find(sensei)?.snapshotGloss)
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
        items.save(StudyItemKey("上手", "じょうず"), "skillful; skilled")
        items.save(StudyItemKey("上手", "うわて"), "upper part; upper hand")
        items.save(StudyItemKey("上手", "かみて"), "stage left")

        val saved = items.observeSaved().first()
        assertEquals(3, saved.size)
        assertEquals(3, saved.map { it.key.reading }.toSet().size)
    }

    /** Save is idempotent: the button can be tapped twice, or a sync can race it. */
    @Test
    fun saving_twice_does_not_duplicate() = runBlocking {
        val first = items.save(sensei, "teacher")
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
     * Unsaving leaves a tombstone rather than removing the row (D-16), and
     * re-saving revives that same row rather than minting a new one.
     *
     * The id is what Phase 7's review history will hang off, so a
     * delete-and-resave that changed it would silently orphan every review the
     * user had done of that word.
     */
    @Test
    fun unsaving_then_resaving_revives_the_same_row() = runBlocking {
        val original = items.save(sensei, "teacher")

        items.unsave(sensei)
        assertFalse(items.observeIsSaved(sensei).first())
        assertTrue(items.observeSaved().first().isEmpty())

        val revived = items.save(sensei, "teacher")
        assertEquals(original.id, revived.id)
        assertEquals(1, items.observeSaved().first().size)
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
        val original = items.save(sensei, "teacher")
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
        items.save(sensei, "teacher")
        val later = StudyItemKey("生産", "せいさん")
        items.save(later, "production")
        // 生産 is newest, so it leads.
        assertEquals("生産", items.observeSaved().first().first().key.text)

        items.unsave(sensei)
        items.save(sensei, "teacher")

        assertEquals("先生", items.observeSaved().first().first().key.text)
    }

    @Test
    fun a_word_can_be_in_two_lists_at_once() = runBlocking {
        val word = items.save(sensei, "teacher")
        val signs = lists.createList("Street Signs")
        val menu = lists.createList("Food Menu")

        lists.addToList(signs.id, word.id)
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
        val word = items.save(sensei, "teacher")
        val signs = lists.createList("Street Signs")

        lists.addToList(signs.id, word.id)
        lists.addToList(signs.id, word.id)

        assertEquals(1, lists.observeItemsIn(signs.id).first().size)
    }

    /**
     * The case D-80 exists for: removing a word from a list removes the
     * membership, not the word.
     */
    @Test
    fun removing_from_a_list_keeps_the_word_saved() = runBlocking {
        val word = items.save(sensei, "teacher")
        val signs = lists.createList("Street Signs")
        lists.addToList(signs.id, word.id)

        lists.removeFromList(signs.id, word.id)

        assertTrue(lists.observeItemsIn(signs.id).first().isEmpty())
        assertTrue(items.observeIsSaved(sensei).first())
    }

    /** Re-adding after a removal revives the membership rather than colliding with its tombstone. */
    @Test
    fun re_adding_after_removal_works() = runBlocking {
        val word = items.save(sensei, "teacher")
        val signs = lists.createList("Street Signs")

        lists.addToList(signs.id, word.id)
        lists.removeFromList(signs.id, word.id)
        lists.addToList(signs.id, word.id)

        assertEquals(1, lists.observeItemsIn(signs.id).first().size)
    }

    /** Deleting a list must not delete the words in it — they have lives elsewhere (D-29). */
    @Test
    fun deleting_a_list_keeps_its_words() = runBlocking {
        val word = items.save(sensei, "teacher")
        val signs = lists.createList("Street Signs")
        val menu = lists.createList("Food Menu")
        lists.addToList(signs.id, word.id)
        lists.addToList(menu.id, word.id)

        lists.deleteList(signs.id)

        assertTrue(lists.observeLists().first().none { it.name == "Street Signs" })
        assertTrue(items.observeIsSaved(sensei).first())
        assertEquals(1, lists.observeItemsIn(menu.id).first().size)
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
        val word = items.save(sensei, "teacher")
        val signs = lists.createList("Street Signs")
        lists.addToList(signs.id, word.id)

        items.unsave(sensei)

        assertTrue(lists.observeItemsIn(signs.id).first().isEmpty())
        assertTrue(lists.observeListsContaining(word.id).first().isEmpty())
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
