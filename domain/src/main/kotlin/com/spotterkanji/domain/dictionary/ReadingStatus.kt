package com.spotterkanji.domain.dictionary

/**
 * How current a reading is — the thing V-21 exists to make visible.
 *
 * JMdict tags a reading element with `re_inf` codes, and the word screen shows
 * every reading of a written form because it cannot know which one was
 * photographed (D-44, D-48). Rendering an obsolete reading identically to a
 * current one therefore teaches kana nobody has used for centuries, to the one
 * reader with no way to tell the difference (D-53).
 *
 * **Declaration order is display order.** Readings sort by this, so a word lists
 * what a learner should actually use before what it merely also records. Within
 * a status the dictionary's own frequency order is preserved.
 */
enum class ReadingStatus {
    /** No `re_inf` tag. The overwhelming majority, and the only unmarked case. */
    CURRENT,

    /** `rk` — a rarely used kana form. Current, just uncommon. */
    RARE,

    /** `ik` — irregular kana usage: a real spelling, not a standard one. */
    IRREGULAR,

    /** `ok` — out-dated kana. 上手 じょうて, real in 1700 and not since (D-53). */
    ARCHAIC,

    /**
     * `sk` — a **search-only** kana form.
     *
     * JMdict carries these so a search matches, not so anyone reads them: they
     * include katakana renderings (私 ワタシ), stretched colloquial spellings
     * (綺麗 きれーい) and plain common misreadings (中国 ちゅうこく, 七 ひち).
     * Displayed as ordinary readings they are worse than the archaic ones,
     * because they land on far commoner words — so they are hidden wherever the
     * word has anything else to show (D-66).
     */
    SEARCH_ONLY,
    ;

    /** Every status but [CURRENT] gets a marker beside the reading. */
    val isMarked: Boolean get() = this != CURRENT

    companion object {
        /**
         * The `re_inf` codes this maps, and nothing else.
         *
         * An unrecognised code yields [CURRENT] rather than throwing, because a
         * dictionary refresh must never break the app over a tag it has not met.
         * That silence is deliberately guarded elsewhere instead: `verify.py`
         * asserts the built dictionary contains exactly these five codes, so a
         * new one fails the build rather than quietly rendering as ordinary.
         */
        private val BY_CODE = mapOf(
            "rk" to RARE,
            "ik" to IRREGULAR,
            "ok" to ARCHAIC,
            "sk" to SEARCH_ONLY,
        )

        /**
         * The status carried by [tags], taking the **least current** where a
         * reading has several.
         *
         * Note what is absent: `gikun`. It is not a defect marker and must never
         * be treated as one — 明日 あした, 大人 おとな and 海豚 いるか are all
         * tagged `gikun` and are all the ordinary, current reading. It says the
         * reading attaches to the word as a whole rather than character by
         * character, which is [DictionaryEntry.isGikun], an orthogonal fact: 15
         * readings are tagged `gikun` *and* `ok` together.
         *
         * Marking those readings archaic would be right; marking あした archaic
         * because it shares a column with them would not.
         */
        fun of(tags: List<String>): ReadingStatus =
            tags.mapNotNull(BY_CODE::get).maxByOrNull { it.ordinal } ?: CURRENT

        /** `gikun` covers both gikun and jukujikun; JMdict does not separate them. */
        fun isGikun(tags: List<String>): Boolean = tags.contains("gikun")
    }
}
