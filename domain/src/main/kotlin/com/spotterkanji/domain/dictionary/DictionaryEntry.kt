package com.spotterkanji.domain.dictionary

/**
 * One dictionary sense: a single meaning, carrying several near-synonymous
 * glosses.
 *
 * "teacher; instructor; master" is **one sense with three glosses**, not three
 * senses — which is why the peek sheet renders them on a single line (D-47).
 */
data class Sense(
    val glosses: List<String>,
    val partsOfSpeech: List<String> = emptyList(),
    val misc: List<String> = emptyList(),
)

/**
 * A word as the dictionary knows it, identified by **(text, reading)** and never
 * by text alone — 上手 is three different words (D-12).
 *
 * Note there is deliberately no dictionary row id here. Row ids are reassigned
 * on every rebuild, so letting one escape into anything durable corrupts saved
 * words with no error (D-11). The natural key is the only identity that crosses
 * this boundary.
 */
/**
 * A single kanji, as shown on a component chip beneath a word.
 *
 * **Meanings only — never readings** (D-06). A kanji's reading inside a compound
 * is not the sum of its parts: 明日 is あした, which cannot be split across 明 and
 * 日 at all. Printing per-character readings under a word teaches something
 * false, so the chips carry meanings and the reading lives on the word.
 */
data class KanjiSummary(
    val character: String,
    val meanings: List<String>,
)

data class DictionaryEntry(
    val text: String,
    val reading: String,
    val senses: List<Sense>,
    /** Lower is more common; null means unranked and **must sort last** (V-04). */
    val frequencyRank: Int? = null,
    val isCommon: Boolean = false,
    /** How current this reading is (D-53, V-21). Defaults to the unmarked case. */
    val readingStatus: ReadingStatus = ReadingStatus.CURRENT,
    /**
     * The reading attaches to the word as a whole and cannot be split across its
     * characters — 明日 is あした, which is neither 明 nor 日.
     *
     * Carried but **not rendered in v1**: it is the reason [readingStatus] has
     * to ignore the `gikun` tag rather than treat it as one more defect, and
     * whole-word furigana (D-14) will need it. Adding a fourth label to a screen
     * already too busy is a question for the UI pass, not for V-21.
     */
    val isGikun: Boolean = false,
) {
    /**
     * Whether the *dictionary* calls this word common — suppressed for anything
     * but a current reading.
     *
     * [isCommon] and [frequencyRank] are derived by unioning the priority tags
     * of the **writing** into those of the reading (V-04), which is the right
     * rule for ranking words against each other and the wrong thing to print
     * beside a dead reading: 上手 じょうしゅ inherits 上手's rank of 12 and would
     * otherwise be badged *common*, one line above じょうず.
     */
    val showsCommonBadge: Boolean get() = isCommon && !readingStatus.isMarked
}

/**
 * The readings of one written form, ordered and filtered for display.
 *
 * Two rules, both of which V-21 needs and neither of which the SQL can do —
 * `reading_info` is a JSON array, so ordering by it in SQLite would mean parsing
 * JSON there:
 *
 * 1. **Current readings first**, then rare, irregular and archaic. The database
 *    orders by frequency alone, and because an archaic reading inherits the
 *    writing's rank it ties with the current one and falls back to alphabetical
 *    kana — which is why 上手 today opens on じょうしゅ rather than じょうず. The
 *    sort is stable, so frequency order survives inside each status.
 * 2. **Search-only readings are dropped — unless dropping them empties the
 *    word** (D-66). 3,143 written forms, almost all kana-only variants like
 *    あっかんべえ, have no other reading at all; hiding theirs unconditionally
 *    would report a word the dictionary plainly holds as missing, which is the
 *    failure D-40 exists to prevent, reached from a different direction.
 */
fun List<DictionaryEntry>.forDisplay(): List<DictionaryEntry> {
    val displayable = filter { it.readingStatus != ReadingStatus.SEARCH_ONLY }
    return (if (displayable.isEmpty()) this else displayable)
        .sortedBy { it.readingStatus.ordinal }
}
