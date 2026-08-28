package com.spotterkanji.domain.user

import java.time.Instant

/**
 * What kind of thing is being studied (D-27).
 *
 * v1 only ever writes [WORD]. [KANJI] exists in the type from the first schema
 * version because adding the discriminator later would mean restructuring the
 * table that every row of review history points at — a migration across the
 * user's entire study record, to add a column v1 could have carried for free.
 */
enum class StudyItemType { WORD, KANJI }

/**
 * The identity of a saved item: **(text, reading, type)** — never text alone
 * (D-12, D-27).
 *
 * 上手 is じょうず (skilled), うわて (upper hand) and かみて (stage left): three
 * distinct vocabulary items a learner must be able to study, schedule and
 * forget separately. Keying on 上手 alone silently merges them, and because all
 * three render plausibly there is no error to notice — the learner simply finds
 * their reviews teaching them the wrong reading months later.
 *
 * Note what is *not* here. No dictionary row id (D-11): row ids are reassigned
 * on every dictionary rebuild, so one stored in user data eventually points at a
 * different word, with no error at any point. The natural key survives rebuilds
 * because it is made of the same text the user saw.
 *
 * [reading] is empty for a [StudyItemType.KANJI] item, whose written form is its
 * whole identity. It is never empty for a word.
 */
data class StudyItemKey(
    val text: String,
    val reading: String,
    val type: StudyItemType = StudyItemType.WORD,
) {
    init {
        require(text.isNotBlank()) { "a study item must have text" }
        require(type != StudyItemType.WORD || reading.isNotBlank()) {
            "a WORD needs a reading — identity is (text, reading), not text alone (D-12)"
        }
    }
}

/** Opaque UUID identity (D-15). Typed so a list id can never be passed as one. */
@JvmInline
value class StudyItemId(val value: String)

/**
 * A word (or, later, a kanji) the user has saved.
 *
 * The primary key is a UUID rather than an auto-increment integer (D-15),
 * because two devices saving while offline would both mint `id = 5` and there
 * is no way to reconcile that afterwards. [key] is the *natural* identity used
 * to recognise the same word saved twice; [id] is what other tables reference.
 */
data class StudyItem(
    val id: StudyItemId,
    val key: StudyItemKey,
    /**
     * The gloss line as it was displayed at the moment of saving, **read only
     * when live lookup fails** (D-43).
     *
     * Not a cache and not a denormalisation — the dictionary is always
     * consulted first, so a corrected or expanded entry is what the user sees.
     * This exists so that a saved item whose key stops resolving still has a
     * meaning to show and stays reviewable (D-40), and it must be captured at
     * save time because a word the dictionary has since dropped cannot have its
     * gloss recovered from anywhere.
     */
    val snapshotGloss: String,
    /**
     * The JMdict `ent_seq` this came from — **a hint, never the identity**
     * (D-11).
     *
     * Usable for diagnostics and for the D-39 `changes` lookup that upgrades
     * "no longer in the dictionary" into "merged into 上手 (じょうず)". Nothing
     * may resolve a saved item by it, because JMdict retires sequence numbers.
     */
    val entSeq: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
    /**
     * When the user deleted this, or null while it is live (D-16).
     *
     * The row stays. A hard delete is invisible to a second device or a restored
     * backup — both observe only that a record is *missing*, which is
     * indistinguishable from never having had it, and the natural repair is to
     * re-add. A tombstone says the removal was deliberate and when.
     */
    val deletedAt: Instant?,
) {
    val isDeleted: Boolean get() = deletedAt != null
}
