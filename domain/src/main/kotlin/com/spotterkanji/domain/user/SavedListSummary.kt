package com.spotterkanji.domain.user

/**
 * A list plus what the Saved screen needs to describe it without opening it.
 *
 * [wordCount] counts **filed, live** words only — the same definition
 * `observeIsSaved` uses (D-88, D-89), so the number on the card and the words
 * behind it cannot disagree. A word taken out of the list stops being counted
 * immediately; a word deleted outright stops being counted even while its
 * tombstoned membership row is still there.
 *
 * ### What is deliberately absent
 *
 * The design's list card also shows a due count, a learned count, and when the
 * list was last scanned into. None of those exist yet: due and learned need
 * `srs_state`, which is Phase 7 (D-79), and "last scan" needs the `scan` table,
 * which arrives with images (D-21). They are left off rather than defaulted to
 * zero, because a card reading "0 due · 0 learned" states something false about
 * the user's progress instead of admitting the app cannot answer yet.
 */
data class SavedListSummary(
    val list: SavedList,
    val wordCount: Int,
)
