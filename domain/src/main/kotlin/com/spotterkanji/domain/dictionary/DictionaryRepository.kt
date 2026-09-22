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

    /**
     * Everything the kanji screen needs for [character], or null if the
     * dictionary has no entry for it.
     *
     * Null is a real case rather than an error: KANJIDIC2 does not cover every
     * character that can appear in Japanese text, and a saved item must still
     * render when its data goes missing (D-40).
     */
    suspend fun kanjiDetail(character: String): KanjiDetail?

    /**
     * Which of [texts] the dictionary actually holds — the one database step
     * longest-match needs (D-07).
     *
     * Takes the whole candidate set at once rather than a string at a time. A
     * ten-character line produces on the order of a hundred candidates, and a
     * query each would be a hundred round trips for something the user is
     * waiting on.
     */
    suspend fun existingWords(texts: Set<String>): Set<String>

    /**
     * Written forms beginning with [prefix], for the search screen (D-96).
     *
     * An exact match leads, then commonest first with unranked last (V-04),
     * then shorter before longer — so typing 生 offers 生 itself, then common
     * words beginning with it such as 生活 and 生徒, rather than whichever rare
     * compound happens to sort first.
     *
     * Matches the **written form only**. Typing せんせい finds kana-written words
     * and not 先生: the index that would serve a reading search was removed from
     * the dictionary for its size (see `schema.sql`), and putting it back is a
     * rebuild. Converting kana to kanji is left to the keyboard for now.
     */
    suspend fun search(prefix: String, limit: Int): List<WordHit>

    /**
     * A [WordHit] for each of [texts] the dictionary holds, in the given order.
     * Texts it does not hold are skipped.
     */
    suspend fun hits(texts: List<String>): List<WordHit>
}
