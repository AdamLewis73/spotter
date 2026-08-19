package com.spotterkanji.domain.dictionary

/** One word demonstrating a kanji reading — 先生 / せんせい / "teacher". */
data class KanjiExample(
    val text: String,
    val reading: String,
    val meaning: String?,
)

/**
 * Every common word where a kanji takes one particular reading.
 *
 * This is the shape D-04 is built on. Rather than authoring prose explaining why
 * 生 means "teacher" inside 先生, the app shows セイ → 先生 · 学生 · 生活 and lets
 * the pattern do the teaching.
 *
 * Grouped on the dictionary's `reading_group`, **not** the canonical reading:
 * 生 is `い.きる` in 生きる but `い` in 生き残り — the same reading written two
 * ways. Grouping on the raw value splits 生's kun readings into 13 groups,
 * several holding a single word, which demonstrates no pattern at all.
 */
data class KanjiReadingGroup(
    /** セイ for on'yomi, い for kun — katakana and hiragana respectively (D-37). */
    val reading: String,
    /** `"on"`, `"kun"`, or null where the alignment could not be resolved. */
    val type: String?,
    val examples: List<KanjiExample>,
)

/**
 * Everything the kanji screen shows, minus stroke order (Phase 3).
 *
 * Deliberately absent, per D-50: **school grade** and **classical radical**.
 * Both are real data and neither is usable by someone who does not already read
 * Japanese — the radical is stored as a bare number, and the grade names a
 * Japanese school year. A reference screen that needs its own key is clutter.
 * Stroke count belongs with the stroke order animation, not here.
 */
data class KanjiDetail(
    val character: String,
    val meanings: List<String>,
    val onReadings: List<String>,
    val kunReadings: List<String>,
    val strokeCount: Int,
    /** The Examples tab. Ordered on'yomi first, commonest reading first. */
    val readingGroups: List<KanjiReadingGroup>,
    /**
     * The character's own dictionary entries, when it is also a word by itself.
     *
     * 生 alone is なま "raw", せい "life", き "pure". D-49 routes a single-character
     * token straight here rather than through a word screen, so without this the
     * Overview tab would silently drop what the user actually scanned.
     */
    val asWord: List<DictionaryEntry>,
)
