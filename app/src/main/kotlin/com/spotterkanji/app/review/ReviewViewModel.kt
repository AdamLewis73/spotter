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
import com.spotterkanji.domain.review.buildReviewCard
import com.spotterkanji.domain.review.checkReading
import com.spotterkanji.domain.review.meaningMatches
import com.spotterkanji.domain.srs.CardKind
import com.spotterkanji.domain.srs.SrsState
import com.spotterkanji.domain.user.ScanThumbnail
import com.spotterkanji.domain.user.StudyItem
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
    ) : ReviewUiState

    data class Done(val answered: Int) : ReviewUiState
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
 * A review session over the filed words that are due (D-89, D-107).
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
 */
class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val reviews = UserDataProvider.reviews(application)
    private val scans = UserDataProvider.scans(application)
    private val dictionary = DictionaryProvider.repository(application)

    private val _state = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    private val queue = ArrayDeque<StudyItem>()
    private var position = 0
    private var answered = 0
    private var shownAt = 0L

    init {
        refreshHome()
    }

    /** Re-reads the counts; called whenever the tab comes back into view. */
    fun refreshHome() {
        if (_state.value is ReviewUiState.Card) return
        viewModelScope.launch { _state.value = home() }
    }

    /**
     * The home state. "Today" and the forecast's days are the phone's local
     * days; every stored time is UTC (D-112), so this is the one place the
     * conversion happens.
     */
    private suspend fun home(): ReviewUiState.Home {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val upcoming = reviews.dueTimesBetween(now, now + FORECAST_DAYS * DAY_MS)
            .groupingBy { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .eachCount()
            .map { (day, count) -> DayCount(day, count) }
            .sortedBy { it.day }
            .take(FORECAST_ROWS)
        return ReviewUiState.Home(
            dueCount = reviews.dueCount(now),
            nextDueAt = reviews.nextDueAt(),
            answeredToday = reviews.answeredSince(startOfToday),
            comingUp = upcoming,
        )
    }

    fun start() {
        viewModelScope.launch {
            queue.clear()
            queue.addAll(reviews.dueItems(System.currentTimeMillis(), SESSION_LIMIT))
            position = 0
            answered = 0
            showNext()
        }
    }

    /** Leaves the session; anything already answered is already recorded. */
    fun close() {
        queue.clear()
        _state.value = ReviewUiState.Loading
        refreshHome()
    }

    fun submit(meaning: String, reading: String) {
        val current = _state.value as? ReviewUiState.Card ?: return
        if (current.result != null) return
        val card = current.card

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

    /** "I was right" (D-108): the meaning counts, and is accepted from now on. */
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
        viewModelScope.launch {
            val after = reviews.record(
                itemId = current.card.itemId,
                kind = CardKind.REVIEW,
                answer = ReviewAnswer(
                    passed = result.passed,
                    meaningAnswer = result.meaningAnswer,
                    meaningCorrect = result.meaningCorrect,
                    meaningOverridden = result.overridden,
                    readingAnswer = result.readingAnswer,
                    readingCorrect = result.readingCorrect,
                ),
                atMs = now,
                durationMs = (now - shownAt).coerceAtLeast(0),
            )
            answered++
            // Still learning — missed, or new and not yet graduated: it comes back
            // before this session ends.
            if (after.state != SrsState.REVIEW) {
                currentItem?.let { queue.addLast(it) }
            }
            showNext()
        }
    }

    private var currentItem: StudyItem? = null

    private suspend fun showNext() {
        val item = queue.removeFirstOrNull()
        currentItem = item
        if (item == null) {
            _state.value = if (answered > 0) ReviewUiState.Done(answered) else home()
            return
        }
        position++
        val card = buildReviewCard(item, dictionary.lookup(item.key.text), reviews.synonyms(item.id))
        val thumbnail = scans.observeThumbnails(listOf(item.id)).first()[item.id]
        shownAt = System.currentTimeMillis()
        _state.value = ReviewUiState.Card(
            card = card,
            thumbnail = thumbnail,
            position = position,
            total = position + queue.size,
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
