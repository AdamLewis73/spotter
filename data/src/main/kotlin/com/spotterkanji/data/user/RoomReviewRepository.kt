package com.spotterkanji.data.user

import androidx.room.withTransaction
import com.spotterkanji.domain.review.ListReviewState
import com.spotterkanji.domain.review.ReviewAnswer
import com.spotterkanji.domain.review.ReviewRepository
import com.spotterkanji.domain.review.fuzzSeed
import com.spotterkanji.domain.srs.CardKind
import com.spotterkanji.domain.srs.Fsrs
import com.spotterkanji.domain.srs.Rating
import com.spotterkanji.domain.srs.SrsCard
import com.spotterkanji.domain.srs.SrsState
import com.spotterkanji.domain.user.SavedListId
import com.spotterkanji.domain.user.StudyItem
import com.spotterkanji.domain.user.StudyItemId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * [ReviewRepository] over Room (D-112).
 *
 * The rule this class holds: **the log is written first and the schedule is
 * derived from it**, in one transaction. A schedule that moved without its
 * answer being logged could never be rebuilt, and an answer logged without the
 * schedule moving would bring the same card straight back.
 */
class RoomReviewRepository(
    private val db: UserDatabase,
    private val fsrs: Fsrs = Fsrs(),
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ReviewRepository {

    private val dao get() = db.srsDao()

    override suspend fun dueCount(nowMs: Long): Int = dao.dueCount(CardKind.REVIEW.name, nowMs)

    override suspend fun nextDueAt(): Long? = dao.nextDueAt(CardKind.REVIEW.name)

    override suspend fun dueItems(nowMs: Long, limit: Int): List<StudyItem> =
        dao.dueItems(CardKind.REVIEW.name, nowMs, limit).map { it.toModel() }

    override fun observeDueCount(nowMs: Long): Flow<Int> = dao.observeDueCount(CardKind.REVIEW.name, nowMs)

    override fun observeListReviewState(nowMs: Long): Flow<Map<SavedListId, ListReviewState>> =
        dao.observeListReviewState(CardKind.REVIEW.name, nowMs).map { rows ->
            rows.associate { SavedListId(it.listId) to ListReviewState(due = it.due, learned = it.learned) }
        }

    override suspend fun answeredSince(sinceMs: Long): Int = dao.answeredSince(CardKind.REVIEW.name, sinceMs)

    override suspend fun dueTimesBetween(fromMs: Long, untilMs: Long): List<Long> =
        dao.dueTimesBetween(CardKind.REVIEW.name, fromMs, untilMs)

    override suspend fun synonyms(itemId: StudyItemId): List<String> = dao.synonyms(itemId.value)

    override suspend fun addSynonym(itemId: StudyItemId, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val now = Instant.now(clock).toEpochMillisLong()
        db.withTransaction {
            val inserted = dao.insertSynonymIfAbsent(
                UserSynonymRow(
                    id = newId(),
                    studyItemId = itemId.value,
                    text = trimmed,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                )
            )
            // -1: the word already has this synonym, perhaps removed. Bring it back.
            if (inserted == -1L) dao.reviveSynonym(itemId.value, trimmed, now)
        }
    }

    override suspend fun record(
        itemId: StudyItemId,
        kind: CardKind,
        answer: ReviewAnswer,
        atMs: Long,
        durationMs: Long?,
    ): SrsCard = db.withTransaction {
        dao.insertLog(
            ReviewLogRow(
                id = newId(),
                studyItemId = itemId.value,
                cardKind = kind.name,
                reviewedAt = atMs,
                durationMs = durationMs,
                passed = answer.passed,
                meaningAnswer = answer.meaningAnswer,
                meaningCorrect = answer.meaningCorrect,
                meaningOverridden = answer.meaningOverridden,
                readingAnswer = answer.readingAnswer,
                readingCorrect = answer.readingCorrect,
                updatedAt = atMs,
            )
        )

        val existing = dao.find(itemId.value, kind.name)
        val before = existing?.toSrsCard() ?: SrsCard.new(atMs)
        val after = fsrs.review(before, Rating.of(answer.passed), atMs, fuzzSeed(itemId, kind, atMs))

        dao.upsert(
            SrsStateRow(
                studyItemId = itemId.value,
                cardKind = kind.name,
                fsrsState = after.state.name,
                step = after.step,
                stability = after.stability,
                difficulty = after.difficulty,
                dueAt = after.dueMs,
                lastReviewedAt = after.lastReviewMs,
                createdAt = existing?.createdAt ?: atMs,
                updatedAt = atMs,
            )
        )
        after
    }
}

internal fun SrsStateRow.toSrsCard() = SrsCard(
    state = SrsState.valueOf(fsrsState),
    step = step,
    stability = stability,
    difficulty = difficulty,
    dueMs = dueAt,
    lastReviewMs = lastReviewedAt,
)
