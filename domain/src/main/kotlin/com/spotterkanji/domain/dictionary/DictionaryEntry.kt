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
    /**
     * Sentences attesting this sense, ingested in Phase 1 (D-51).
     *
     * Attached to the **sense**, not the word: the corpus records which meaning
     * a sentence demonstrates, and 甘い meaning "lenient" is not illustrated by a
     * sentence about sweets.
     */
    val examples: List<ExampleSentence> = emptyList(),
)

/**
 * One attested sentence and its translation.
 *
 * From `JMdict_e_examp`, which cites Tatoeba sentence ids — so the sentences are
 * Tatoeba's and are credited as such (`attribution.md`, D-51).
 */
data class ExampleSentence(
    val japanese: String,
    val english: String,
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
/**
 * Readings that mean exactly the same thing, shown once (D-68).
 *
 * 先生 has five readings and three of them — せんしょう, せんじょう, ぜんじょう —
 * carry an identical pair of senses. Rendering three blocks that each say
 * "teacher; instructor; master / previous existence" states the same fact three
 * times and buries the two readings that differ. The readings are still all
 * present; only the repetition goes.
 *
 * [entries] keeps its display order, so the best-ranked reading leads and takes
 * the *common* badge — which is why a group may legitimately mix a current
 * reading with archaic ones. 上手 じょうず, じょうしゅ and じょうて share their
 * senses exactly; the line reads じょうず COMMON · じょうしゅ ARCHAIC ·
 * じょうて ARCHAIC, and every reading keeps its own mark (V-21).
 */
data class MergedReading(
    /** At least one, all sharing an identical sense list. Best-ranked first. */
    val entries: List<DictionaryEntry>,
) {
    val primary: DictionaryEntry get() = entries.first()

    /** Identical across [entries] by construction, so the first is definitive. */
    val senses: List<Sense> get() = primary.senses

    /**
     * True only when *every* reading here is marked.
     *
     * A group led by a current reading is part of the main sequence even when it
     * carries archaic alternates — it is じょうず's line, and じょうず is what a
     * learner wants. Dimming it because of its company would hide a current
     * reading (D-68).
     */
    val allMarked: Boolean get() = entries.all { it.readingStatus.isMarked }
}

/**
 * Collapse readings whose meanings are identical.
 *
 * Grouped on the **glosses**, not the whole [Sense]: two readings recorded with
 * the same meanings but a differing part-of-speech tag are still saying the same
 * thing to a reader, and splitting on that would leave the repetition on screen
 * for a distinction the screen does not show.
 *
 * Each group lands at the position of its first member, so the ordering
 * `forDisplay` established survives.
 */
fun List<DictionaryEntry>.mergedByMeaning(): List<MergedReading> {
    val groups = LinkedHashMap<List<List<String>>, MutableList<DictionaryEntry>>()
    forEach { entry ->
        val key = entry.senses.map { it.glosses }
        groups.getOrPut(key) { mutableListOf() }.add(entry)
    }
    return groups.values.map(::MergedReading)
}

fun List<DictionaryEntry>.forDisplay(): List<DictionaryEntry> {
    val displayable = filter { it.readingStatus != ReadingStatus.SEARCH_ONLY }
    return (if (displayable.isEmpty()) this else displayable)
        .sortedBy { it.readingStatus.ordinal }
}
