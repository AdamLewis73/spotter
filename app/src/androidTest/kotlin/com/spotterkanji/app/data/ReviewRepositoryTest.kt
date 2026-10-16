package com.spotterkanji.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spotterkanji.data.user.RoomReviewRepository
import com.spotterkanji.data.user.RoomSavedItemsRepository
import com.spotterkanji.data.user.RoomSavedListRepository
import com.spotterkanji.data.user.UserDatabase
import com.spotterkanji.domain.review.ListReviewState
import com.spotterkanji.domain.review.ReviewAnswer
import com.spotterkanji.domain.review.fuzzSeed
import com.spotterkanji.domain.srs.Answer
import com.spotterkanji.domain.srs.CardKind
import com.spotterkanji.domain.srs.Fsrs
import com.spotterkanji.domain.srs.Rating
import com.spotterkanji.domain.srs.SrsState
import com.spotterkanji.domain.user.StudyItem
import com.spotterkanji.domain.user.StudyItemKey
import com.spotterkanji.domain.user.StudyItemType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The review queue and recording (D-89, D-107, D-112), against a real Room
 * database. Every failure here is silent in the app: a word that never comes
 * due, a word reviewed that should not be, or a schedule that no longer matches
 * the answers that produced it.
 */
@RunWith(AndroidJUnit4::class)
class ReviewRepositoryTest {

    private lateinit var db: UserDatabase
    private lateinit var items: RoomSavedItemsRepository
    private lateinit var lists: RoomSavedListRepository
    private lateinit var reviews: RoomReviewRepository

    /** Far in the future, so every word saved during the test is due by it. */
    private val later = 4_000_000_000_000L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java).build()
        items = RoomSavedItemsRepository(db)
        lists = RoomSavedListRepository(db)
        reviews = RoomReviewRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun filed(text: String, reading: String, type: StudyItemType = StudyItemType.WORD): StudyItem {
        val list = lists.observeListsOnce().firstOrNull() ?: lists.createList("Signs")
        return items.save(StudyItemKey(text, reading, type), "gloss").also { lists.addToList(list.id, it.id) }
    }

    private suspend fun RoomSavedListRepository.observeListsOnce() = observeLists().first()

    private val right = ReviewAnswer(true, "teacher", true, false, "せんせい", true)
    private val wrong = ReviewAnswer(false, "teacher", true, false, "せんせ", false)

    // ---- The queue

    @Test
    fun a_filed_word_is_due_at_once() = runBlocking {
        val sensei = filed("先生", "せんせい")
        assertEquals(1, reviews.dueCount(later))
        assertEquals(listOf(sensei.id), reviews.dueItems(later, 10).map { it.id })
    }

    /** D-89: unfiled keeps the schedule but leaves the queue, exactly as it leaves Saved. */
    @Test
    fun an_unfiled_or_unsaved_word_is_not_reviewed() = runBlocking {
        items.save(StudyItemKey("生産", "せいさん"), "production") // never filed
        val sensei = filed("先生", "せんせい")
        val list = lists.observeListsOnce().single()
        lists.removeFromList(list.id, sensei.id)

        assertEquals(0, reviews.dueCount(later))
        assertTrue(reviews.dueItems(later, 10).isEmpty())
        assertNull(reviews.nextDueAt())
    }

    /** D-106: what a kanji card's reading accepts is open, so kanji stay out for now. */
    @Test
    fun kanji_items_are_not_in_the_queue_yet() = runBlocking {
        filed("生", "", StudyItemType.KANJI)
        assertEquals(0, reviews.dueCount(later))
    }

    // ---- Recording

    /** D-107: right is FSRS's successful recall — a new card moves to its next step. */
    @Test
    fun a_right_answer_is_logged_and_moves_the_card_on() = runBlocking {
        val sensei = filed("先生", "せんせい")
        val at = later

        val after = reviews.record(sensei.id, CardKind.REVIEW, right, at, durationMs = 4200)

        assertEquals(SrsState.LEARNING, after.state)
        assertEquals(1, after.step)
        assertEquals(at + 10 * Fsrs.MINUTE_MS, after.dueMs)
        val log = db.srsDao().logFor(sensei.id.value, "REVIEW").single()
        assertTrue(log.passed)
        assertEquals("せんせい", log.readingAnswer)
        assertEquals(4200L, log.durationMs)
        assertEquals(after.dueMs, db.srsDao().find(sensei.id.value, "REVIEW")!!.dueAt)
    }

    /** Wrong is a failed recall: back to the first step, a minute away. */
    @Test
    fun a_wrong_answer_sends_the_card_back_to_the_first_step() = runBlocking {
        val sensei = filed("先生", "せんせい")
        val after = reviews.record(sensei.id, CardKind.REVIEW, wrong, later, null)
        assertEquals(0, after.step)
        assertEquals(later + Fsrs.MINUTE_MS, after.dueMs)
        // The halves are kept apart (D-107): the meaning was right, the reading not.
        val log = db.srsDao().logFor(sensei.id.value, "REVIEW").single()
        assertEquals(true, log.meaningCorrect)
        assertEquals(false, log.readingCorrect)
    }

    /**
     * D-112's promise, end to end: the stored schedule is exactly what replaying
     * the log through FSRS produces. If this fails, `srs_state` has drifted from
     * the answers that produced it, and nothing could ever rebuild it.
     */
    @Test
    fun the_stored_schedule_is_a_replay_of_the_log() = runBlocking {
        val sensei = filed("先生", "せんせい")
        var at = later
        for (answer in listOf(right, right, wrong, right, right)) {
            val after = reviews.record(sensei.id, CardKind.REVIEW, answer, at, null)
            at = maxOf(after.dueMs, at + 1)
        }

        val stored = db.srsDao().find(sensei.id.value, "REVIEW")!!
        val log = db.srsDao().logFor(sensei.id.value, "REVIEW")
        val replayed = Fsrs().replay(
            createdAtMs = stored.createdAt,
            answers = log.map {
                Answer(Rating.of(it.passed), it.reviewedAt, fuzzSeed(sensei.id, CardKind.REVIEW, it.reviewedAt))
            },
        )
        assertEquals(replayed.state.name, stored.fsrsState)
        assertEquals(replayed.step, stored.step)
        assertEquals(replayed.stability!!, stored.stability!!, 0.0)
        assertEquals(replayed.difficulty!!, stored.difficulty!!, 0.0)
        assertEquals(replayed.dueMs, stored.dueAt)
    }

    // ---- Review state for Saved and the Review tab (artboards 2d, 4h)

    /**
     * A live count is started with one "now" and keeps it. A word filed after
     * that is due (its due time is its save time), so it must count at once —
     * not a minute later on the next tick.
     */
    @Test
    fun a_word_filed_after_the_count_started_is_due_at_once() = runBlocking {
        val countFrom = 0L // long before anything was saved
        assertEquals(0, reviews.observeDueCount(countFrom).first())
        filed("先生", "せんせい")
        assertEquals(1, reviews.observeDueCount(countFrom).first())
    }

    /** Per list: due counts what is due; learned counts what has left learning. */
    @Test
    fun each_list_reports_its_due_and_learned_words() = runBlocking {
        val sensei = filed("先生", "せんせい")
        filed("生産", "せいさん")
        val list = lists.observeListsOnce().single()

        assertEquals(ListReviewState(due = 2, learned = 0), reviews.observeListReviewState(later).first()[list.id])

        // Two right answers take a new card out of learning (D-113's steps).
        val first = reviews.record(sensei.id, CardKind.REVIEW, right, later, null)
        reviews.record(sensei.id, CardKind.REVIEW, right, first.dueMs, null)

        val state = reviews.observeListReviewState(later).first()[list.id]!!
        assertEquals(1, state.learned)
        assertEquals("先生 is no longer due; 生産 still is", 1, state.due)
    }

    @Test
    fun the_forecast_and_todays_count_come_from_the_log_and_schedules() = runBlocking {
        val sensei = filed("先生", "せんせい")
        val after = reviews.record(sensei.id, CardKind.REVIEW, right, later, null)

        assertEquals(1, reviews.answeredSince(later))
        assertEquals(0, reviews.answeredSince(later + 1))
        assertEquals(listOf(after.dueMs), reviews.dueTimesBetween(later, later + 7 * 86_400_000L))
    }

    // ---- Review this list, and practice (D-117)

    /**
     * A list only filters (D-29): its review asks its own due words and no
     * others, and a word in two lists is due in both — one schedule, so
     * answering it from one list moves it in the other.
     */
    @Test
    fun a_list_review_asks_only_that_lists_due_words() = runBlocking {
        val signs = lists.createList("Signs")
        val menu = lists.createList("Menu")
        val sensei = items.save(StudyItemKey("先生", "せんせい"), "teacher").also { lists.addToList(signs.id, it.id) }
        val seisan = items.save(StudyItemKey("生産", "せいさん"), "production").also { lists.addToList(menu.id, it.id) }
        lists.addToList(menu.id, sensei.id)

        assertEquals(listOf(sensei.id), reviews.dueItems(later, 10, signs.id).map { it.id })
        assertEquals(setOf(sensei.id, seisan.id), reviews.dueItems(later, 10, menu.id).map { it.id }.toSet())
        assertEquals(1, reviews.dueCount(later, signs.id))
        assertEquals(2, reviews.dueCount(later))

        val first = reviews.record(sensei.id, CardKind.REVIEW, right, later, null)
        reviews.record(sensei.id, CardKind.REVIEW, right, first.dueMs, null)
        assertEquals(0, reviews.dueCount(later, signs.id))
        assertEquals("先生 left Menu's queue too", listOf(seisan.id), reviews.dueItems(later, 10, menu.id).map { it.id })
        assertTrue(reviews.nextDueAt(signs.id)!! > later)
    }

    /**
     * Practice is drawn from the list's words that are not due — never a due
     * word, which belongs to review, and never a word never yet reviewed, which
     * is always due.
     */
    @Test
    fun practice_draws_the_lists_words_that_are_not_due() = runBlocking {
        val sensei = filed("先生", "せんせい")
        filed("生産", "せいさん")
        val list = lists.observeListsOnce().single()
        assertTrue(reviews.notDueItems(later, 10, list.id).isEmpty())

        val first = reviews.record(sensei.id, CardKind.REVIEW, right, later, null)
        val graduated = reviews.record(sensei.id, CardKind.REVIEW, right, first.dueMs, null)
        val now = first.dueMs + 1

        assertEquals(listOf(sensei.id), reviews.notDueItems(now, 10, list.id).map { it.id })
        assertTrue("due again, so review's, not practice's", reviews.notDueItems(graduated.dueMs, 10, list.id).isEmpty())
    }

    // ---- Synonyms

    @Test
    fun i_was_right_saves_a_synonym_once() = runBlocking {
        val jouzu = filed("上手", "じょうず")
        reviews.addSynonym(jouzu.id, "talented")
        reviews.addSynonym(jouzu.id, " talented ")
        reviews.addSynonym(jouzu.id, "")
        assertEquals(listOf("talented"), reviews.synonyms(jouzu.id))
    }
}
