package com.spotterkanji.domain.user

import java.time.Instant

/** Opaque UUID identity (D-15). Typed so it cannot be confused with a [StudyItemId]. */
@JvmInline
value class SavedListId(val value: String)

/**
 * A user-named collection of saved words — "Street Signs", "Food Menu".
 *
 * Lists are **organisational tags and nothing more** (D-29). They do not own
 * scheduling: a word in three lists still has exactly one review schedule, and
 * a review session may *filter* by list rather than drawing from it. Attaching
 * a schedule to membership instead would review 先生 today because it is in
 * Street Signs and again tomorrow via Food Menu, doubling the user's workload
 * and corrupting FSRS's model of their memory, which infers retention from the
 * interval since the last review.
 */
data class SavedList(
    val id: SavedListId,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Set when the user deletes the list; the row remains (D-16, D-80). */
    val deletedAt: Instant?,
) {
    val isDeleted: Boolean get() = deletedAt != null
}

/**
 * One word's membership of one list — the join row that makes lists
 * many-to-many (D-28).
 *
 * A `list_id` column on the study item would confine each word to a single
 * list, but the same word genuinely appears on a restaurant menu *and* a street
 * sign, and the user will want it filed under both.
 *
 * **This row is tombstoned, and it is the case that forced D-80.** Removing a
 * word from a list is the deletion users perform most often, and it is a
 * deletion of *this row* rather than of the word. Hard-deleting it means a
 * restored backup or a second device finds the membership present on one side
 * and absent on the other, with nothing recording that the absence was
 * intentional — so it puts the word back into the list the user just tidied.
 */
data class ListMembership(
    val id: String,
    val listId: SavedListId,
    val studyItemId: StudyItemId,
    val addedAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
