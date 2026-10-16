package com.spotterkanji.app.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotterkanji.app.data.DictionaryProvider
import com.spotterkanji.app.data.UserDataProvider
import com.spotterkanji.domain.review.OtherReading
import com.spotterkanji.domain.review.ReadingVerdict
import com.spotterkanji.domain.review.ReviewAnswer
import com.spotterkanji.domain.review.ReviewCard
import com.spotterkanji.domain.review.buildKanjiReviewCard
import com.spotterkanji.domain.review.buildReviewCard
import com.spotterkanji.domain.review.checkReading
import com.spotterkanji.domain.review.meaningMatches
import com.spotterkanji.domain.srs.CardKind
import com.spotterkanji.domain.srs.SrsState
import com.spotterkanji.domain.user.SavedListId
import com.spotterkanji.domain.user.ScanThumbnail
import com.spotterkanji.domain.user.StudyItem
import com.spotterkanji.domain.user.StudyItemId
import com.spotterkanji.domain.user.StudyItemType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the Review tab is showing. */
sealed interface ReviewUiState {
    data object Loading : ReviewUiState

    /**
     * The tab at rest: how much is due, or when the next word is — and, once
     * caught up, what was done today and what is coming (artboard 4h).
     */
    data class Home(
        val dueCount: Int,
        val nextDueAt: Long?,
        val answeredToday: Int = 0,
        val comingUp: List<DayCount> = emptyList(),
        /** Set when reviewing one list (D-117); null on the Review tab itself. */
        val listName: String? = null,
        /** A list's words not due yet, offered as practice once nothing is due (D-117). */
        val practiceCount: Int = 0,
    ) : ReviewUiState

    /** One card of a session (D-107). */
    data class Card(
        val card: ReviewCard,
        val thumbnail: ScanThumbnail?,
        /** 1-based position and the session's current length, which grows when a card comes back. */
        val position: Int,
        val total: Int,
        /** Null while asking; set once Submit has graded the answer. */
        val result: Result? = null,
        /** A reading of a different word was typed (D-108): say so, grade nothing. */
        val nudge: OtherReading? = null,
        /** Practice, not review: nothing is recorded (D-117). */
        val practice: Boolean = false,
        val listName: String? = null,
    ) : ReviewUiState

    /**
     * The end of a session: how many words, how many were right the first time
     * they were asked and how many were missed, and — after a review, not
     * practice — when they come back. A missed word was asked again until it was
     * right (D-114), so "missed" counts words, not wrong answers.
     */
    data class Done(
        val words: Int,
        val rightFirstTime: Int,
        val missed: Int,
        val comingBack: List<DayCount> = emptyList(),
        val practice: Boolean = false,
        val listName: String? = null,
        /** The list's other words, offered as practice after its review (D-117). */
        val practiceCount: Int = 0,
    ) : ReviewUiState
}

/** How many words fall due on one local day. */
data class DayCount(val day: LocalDate, val count: Int)

/** The outcome of one Submit, shown until Next. */
data class Result(
    val meaningAnswer: String,
    val meaningCorrect: Boolean,
    /** "I was right" was pressed (D-108). */
    val overridden: Boolean,
    val readingAnswer: String,
    val readingCorrect: Boolean,
) {
    val meaningRight: Boolean get() = meaningCorrect || overridden

    /** One card, one schedule: both halves must be right (D-107). */
    val passed: Boolean get() = meaningRight && readingCorrect
}

/**
 * A review session over the filed words that are due (D-89, D-107) — all of
 * them, or one list's (D-117).
 *
 * **An answer is recorded at Next, not at Submit.** Between the two the learner
 * can press "I was right", which changes the outcome, and the log is
 * append-only (D-112) — so the one row written is the final one. Leaving between
 * Submit and Next records nothing, which is the same as never having answered.
 *
 * **A card answered wrong comes back in the same session.** FSRS puts a missed
 * or new card on a short learning step — minutes, not days — so it goes to the
 * back of this session's queue rather than waiting for the next visit. Shown
 * early, it is simply an early review, which FSRS's same-day model handles.
 *
 * **Practice records nothing** (D-117). Once a list has nothing due, its other
 * words can be asked on the same card, checked the same way, with no log row and
 * no schedule change — so practising ahead never shortens an interval.
 */
class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val reviews = UserDataProvider.reviews(application)
    private val lists = UserDataProvider.savedLists(application)
    private val scans = UserDataProvider.scans(application)
    private val dictionary = DictionaryProvider.repository(application)

    private val _state = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    /** The list being reviewed (D-117), or null for every filed word. */
    private var listId: SavedListId? = null
    private var listName: String? = null
    private var opened = false

    private val queue = ArrayDeque<StudyItem>()
    private var currentItem: StudyItem? = null
    private var position = 0
    private var shownAt = 0L
    private var practice = false

    /** Each word's first answer this session, in the order asked — what the summary counts. */
    private val firstAnswer = LinkedHashMap<StudyItemId, Boolean>()

    /** Each word's due time after its last recorded answer this session. */
    private val dueAfter = HashMap<StudyItemId, Long>()

    /**
     * Which words this screen reviews: [list]'s, or every filed word when null.
     * The route supplies it, so this is called from composition and is
     * idempotent for the same list — as `ListDetailViewModel.open` is, and for
     * the same reason.
     */
    fun open(list: SavedListId?) {
        if (opened && list == listId) return
        opened = true
        listId = list
        refreshHome()
    }

    /** Re-reads the counts; called whenever the tab comes back into view. */
    fun refreshHome() {
        if (!opened) return
        if (_state.value is ReviewUiState.Card || _state.value is ReviewUiState.Done) return
        viewModelScope.launch { _state.value = home() }
    }

    /**
     * The home state. "Today" and the forecast's days are the phone's local
     * days; every stored time is UTC (D-112), so [forecast] and [startOfToday]
     * are where the conversion happens.
     */
    private suspend fun home(): ReviewUiState.Home {
        val now = System.currentTimeMillis()
        val list = listId
        listName = list?.let { id -> lists.observeLists().first().firstOrNull { it.id == id }?.name }
        val dueCount = reviews.dueCount(now, list)
        return ReviewUiState.Home(
            dueCount = dueCount,
            nextDueAt = reviews.nextDueAt(list),
            // Today's answers are counted across every list, so they are said
            // only on the Review tab, where "today" means all of it.
            answeredToday = if (list == null) reviews.answeredSince(startOfToday()) else 0,
            comingUp = forecast(reviews.dueTimesBetween(now, now + FORECAST_DAYS * DAY_MS, list)),
            listName = listName,
            practiceCount = if (list != null && dueCount == 0) {
                reviews.notDueItems(now, SESSION_LIMIT, list).size
            } else 0,
        )
    }

    /** The first [FORECAST_ROWS] local days with anything due, and how much on each. */
    private fun forecast(dueTimes: List<Long>): List<DayCount> {
        val zone = ZoneId.systemDefault()
        return dueTimes
            .groupingBy { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .eachCount()
            .map { (day, count) -> DayCount(day, count) }
            .sortedBy { it.day }
            .take(FORECAST_ROWS)
    }

    private fun startOfToday(): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** A review of what is due — in the list, when there is one. */
    fun start() {
        viewModelScope.launch {
            begin(practice = false, reviews.dueItems(System.currentTimeMillis(), SESSION_LIMIT, listId))
        }
    }

    /**
     * Practice over the list's words that are not due (D-117). Words reviewed
     * earlier in this sitting are left out: they have just been asked.
     */
    fun startPractice() {
        val list = listId ?: return
        val justReviewed = firstAnswer.keys.toSet()
        viewModelScope.launch {
            val items = reviews.notDueItems(System.currentTimeMillis(), SESSION_LIMIT, list)
                .filterNot { it.id in justReviewed }
            begin(practice = true, items)
        }
    }

    private suspend fun begin(practice: Boolean, items: List<StudyItem>) {
        this.practice = practice
        queue.clear()
        queue.addAll(items)
        position = 0
        firstAnswer.clear()
        dueAfter.clear()
        showNext()
    }

    /** Leaves the session or its summary; anything answered is already recorded. */
    fun close() {
        queue.clear()
        firstAnswer.clear()
        dueAfter.clear()
        _state.value = ReviewUiState.Loading
        viewModelScope.launch { _state.value = home() }
    }

    fun submit(meaning: String, reading: String) {
        val current = _state.value as? ReviewUiState.Card ?: return
        if (current.result != null) return
        val card = current.card

        // A kanji the dictionary lost asks its meaning only (D-40, D-118).
        if (!card.asksReading) {
            if (meaning.isBlank()) return
            _state.value = current.copy(
                result = Result(
                    meaningAnswer = meaning.trim(),
                    meaningCorrect = meaningMatches(meaning, card.senses, card.synonyms),
                    overridden = false,
                    readingAnswer = "",
                    readingCorrect = true,
                ),
            )
            return
        }

        when (val verdict = checkReading(reading, card.acceptedReadings, card.otherWords)) {
            is ReadingVerdict.OtherWord -> {
                // Not graded: the card shows only the written form, so the learner
                // could not have known which word was meant (D-108).
                _state.value = current.copy(nudge = verdict.word)
                return
            }
            ReadingVerdict.Empty -> return
            else -> Unit
        }
        if (meaning.isBlank()) return

        val readingVerdict = checkReading(reading, card.acceptedReadings, card.otherWords)
        _state.value = current.copy(
            nudge = null,
            result = Result(
                meaningAnswer = meaning.trim(),
                meaningCorrect = meaningMatches(meaning, card.senses, card.synonyms),
                overridden = false,
                readingAnswer = when (readingVerdict) {
                    is ReadingVerdict.Right -> readingVerdict.kana
                    is ReadingVerdict.Wrong -> readingVerdict.kana
                    else -> reading.trim()
                },
                readingCorrect = readingVerdict is ReadingVerdict.Right,
            ),
        )
    }

    /**
     * "I was right" (D-108): the meaning counts, and is accepted from now on. In
     * practice too — a synonym is what the learner knows the word means, not a
     * grade, so it is kept even though the answer is not.
     */
    fun iWasRight() {
        val current = _state.value as? ReviewUiState.Card ?: return
        val result = current.result ?: return
        if (result.meaningRight) return
        _state.value = current.copy(result = result.copy(overridden = true))
        viewModelScope.launch { reviews.addSynonym(current.card.itemId, result.meaningAnswer) }
    }

    fun next() {
        val current = _state.value as? ReviewUiState.Card ?: return
        val result = current.result ?: return
        val now = System.currentTimeMillis()
        val itemId = current.card.itemId
        firstAnswer.putIfAbsent(itemId, result.passed)
        viewModelScope.launch {
            if (current.practice) {
                // Nothing recorded (D-117). A miss still comes back, so practice
                // ends the way review does: every word answered right once.
                if (!result.passed) currentItem?.let { queue.addLast(it) }
                showNext()
                return@launch
            }
            val after = reviews.record(
                itemId = itemId,
                kind = CardKind.REVIEW,
                answer = ReviewAnswer(
                    passed = result.passed,
                    meaningAnswer = result.meaningAnswer,
                    meaningCorrect = result.meaningCorrect,
                    meaningOverridden = result.overridden,
                    // Null when the card did not ask (D-112: the log says what was asked).
                    readingAnswer = result.readingAnswer.takeIf { current.card.asksReading },
                    readingCorrect = result.readingCorrect.takeIf { current.card.asksReading },
                ),
                atMs = now,
                durationMs = (now - shownAt).coerceAtLeast(0),
            )
            dueAfter[itemId] = after.dueMs
            // Still learning — missed, or new and not yet graduated: it comes back
            // before this session ends.
            if (after.state != SrsState.REVIEW) {
                currentItem?.let { queue.addLast(it) }
            }
            showNext()
        }
    }

    private suspend fun showNext() {
        val item = queue.removeFirstOrNull()
        currentItem = item
        if (item == null) {
            _state.value = if (firstAnswer.isNotEmpty()) summary() else home()
            return
        }
        position++
        val synonyms = reviews.synonyms(item.id)
        val card = when (item.key.type) {
            StudyItemType.WORD -> buildReviewCard(item, dictionary.lookup(item.key.text), synonyms)
            // From the kanji screen's own data, so the card agrees with what was saved (D-118).
            StudyItemType.KANJI -> buildKanjiReviewCard(item, dictionary.kanjiDetail(item.key.text), synonyms)
        }
        val thumbnail = scans.observeThumbnails(listOf(item.id)).first()[item.id]
        shownAt = System.currentTimeMillis()
        _state.value = ReviewUiState.Card(
            card = card,
            thumbnail = thumbnail,
            position = position,
            total = position + queue.size,
            practice = practice,
            listName = listName,
        )
    }

    private suspend fun summary(): ReviewUiState.Done {
        val list = listId
        val practiceLeft = if (list != null && !practice) {
            reviews.notDueItems(System.currentTimeMillis(), SESSION_LIMIT, list).count { it.id !in firstAnswer }
        } else 0
        return ReviewUiState.Done(
            words = firstAnswer.size,
            rightFirstTime = firstAnswer.values.count { it },
            missed = firstAnswer.values.count { !it },
            // The same week-ahead window as the home screen's forecast.
            comingBack = forecast(dueAfter.values.filter { it <= System.currentTimeMillis() + FORECAST_DAYS * DAY_MS }),
            practice = practice,
            listName = listName,
            practiceCount = practiceLeft,
        )
    }

    private companion object {
        /** Enough for a sitting; the rest stay due for the next one. */
        const val SESSION_LIMIT = 50

        /** The forecast looks a week ahead and shows its first three days with anything due. */
        const val FORECAST_DAYS = 7L
        const val FORECAST_ROWS = 3
        const val DAY_MS = 86_400_000L
    }
}
