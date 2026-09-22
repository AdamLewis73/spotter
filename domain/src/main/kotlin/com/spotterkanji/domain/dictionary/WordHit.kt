package com.spotterkanji.domain.dictionary

/**
 * One row of search results: a written form, its leading reading, and the
 * meaning of its first sense (D-96).
 *
 * **One per written form, not one per reading.** 上手 is three words (D-12) but
 * one row here, because tapping it opens one word screen with the readings as
 * sections inside it (D-48). The reading shown is the one that screen leads
 * with, so the row never promises something the next screen contradicts.
 *
 * Showing a reading at all is a deliberate difference from the peek sheet
 * (D-47). The peek would be guessing what a photograph said; here the user
 * typed the word, and the reading is what tells two similar rows apart.
 */
data class WordHit(
    val text: String,
    val reading: String,
    /** The first sense's glosses, joined — the same line the peek sheet shows. */
    val gloss: String,
)
