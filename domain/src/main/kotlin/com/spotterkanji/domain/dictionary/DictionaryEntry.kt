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
)
