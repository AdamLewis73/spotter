package com.spotterkanji.domain.dictionary

/**
 * Read-only access to the bundled dictionary.
 *
 * Declared here and implemented in `:data`, so nothing above this layer knows
 * that the dictionary is SQLite, an asset, or Room — which is what lets the
 * storage change without touching callers, and what makes the eventual iOS port
 * a matter of reimplementing this interface rather than rewriting the app.
 */
interface DictionaryRepository {

    /**
     * Every entry written as [text], across all its readings.
     *
     * 上手 returns three. The caller decides how to present them — the app cannot
     * tell which reading a photograph meant, and does not guess (D-44, D-48).
     *
     * Ordered most common first, with unranked entries last (V-04). Empty if the
     * word is not in the dictionary.
     */
    suspend fun lookup(text: String): List<DictionaryEntry>

    /**
     * The kanji making up [text], in the order they appear, for the component
     * chips (D-06).
     *
     * Kana are skipped — 生きる contributes 生 only. A character the dictionary
     * has no entry for is omitted rather than rendered as an empty chip.
     */
    suspend fun kanjiIn(text: String): List<KanjiSummary>
}
