package com.spotterkanji.domain.review

import com.spotterkanji.domain.dictionary.KanjiDetail
import com.spotterkanji.domain.dictionary.KanjiExample
import com.spotterkanji.domain.user.StudyItem

/**
 * What a kanji card shows once answered (D-118): each reading with one word that
 * uses it, and the readings no common word demonstrates.
 *
 * The learner needs only one reading to pass, so the breadth lives here, on the
 * reveal, rather than in the test — 生 answered いきる still shows セイ 先生 and
 * ショウ 一生 beneath it. Show, don't assert (D-04).
 */
data class KanjiReveal(
    /** On'yomi first, commonest first — the kanji screen's Examples tab order. */
    val readings: List<KanjiReadingExample>,
    /** Readings with no common word to show them, as plain kana. */
    val rarer: List<String>,
    /**
     * Each accepted answer, in hiragana, to the reading it answers — いきる and い
     * both to い — so the reveal can mark the one the learner typed.
     */
    val keyOf: Map<String, String>,
) {
    /** The reading [typedKana] answered, as a key of [readings] or [rarer]; null if none. */
    fun keyFor(typedKana: String): String? = keyOf[katakanaToHiragana(typedKana)]
}

/** One reading of a kanji and a word where it takes it — セイ 先生 "teacher". */
data class KanjiReadingExample(
    /** As the dictionary writes it: katakana for on'yomi, hiragana for kun (D-37). */
    val reading: String,
    val example: KanjiExample?,
) {
    val key: String get() = katakanaToHiragana(reading)
}

/**
 * Which typed readings answer a kanji card (D-118): **any one of its readings**.
 *
 * KANJIDIC2 writes kun'yomi with markers — `い.きる` for the stem い and the kana
 * ending きる that follows the kanji, `なま-` for a prefix, `-う` for a suffix. The
 * markers are dropped, and a reading with an ending is accepted both as its stem
 * and in full: a card shows the kanji alone, so asking for the ending would be
 * asking for a word the card does not show.
 *
 * Accepting a bare stem means a one-kana guess can pass — い, う, お, は and more
 * all answer 生 — and that was weighed and accepted (D-118), to be revisited after
 * 1.0 with real use behind it.
 *
 * Returned in hiragana, keyed to the reading each one answers.
 */
fun kanjiAnswers(onReadings: List<String>, kunReadings: List<String>): Map<String, String> {
    val answers = LinkedHashMap<String, String>()
    for (on in onReadings) {
        val kana = katakanaToHiragana(on.replace("-", ""))
        if (kana.isNotEmpty()) answers.putIfAbsent(kana, kana)
    }
    for (kun in kunReadings) {
        val bare = katakanaToHiragana(kun.replace("-", ""))
        val stem = bare.substringBefore('.')
        if (stem.isEmpty()) continue
        answers.putIfAbsent(stem, stem)
        if ('.' in bare) answers.putIfAbsent(bare.replace(".", ""), stem)
    }
    return answers
}

/**
 * Builds a saved kanji's card (D-92, D-106, D-118) from the kanji screen's own
 * data, so the card agrees with what the learner saw when they saved it.
 *
 * **Meaning:** any of KANJIDIC2's meanings, checked as loosely as a word's
 * (D-108), with the learner's own synonyms. They are kept as one sense in
 * KANJIDIC2's order, which is D-106's "most common first".
 *
 * **Reading:** any one reading ([kanjiAnswers]).
 *
 * When the dictionary has no entry for the character any more, the card is
 * built from `snapshot_gloss` and asks the meaning alone — it stays reviewable
 * (D-40) rather than asking a reading it cannot check.
 */
fun buildKanjiReviewCard(item: StudyItem, detail: KanjiDetail?, synonyms: List<String>): ReviewCard {
    if (detail == null) {
        return ReviewCard(
            itemId = item.id,
            text = item.key.text,
            reading = "",
            senses = listOf(item.snapshotGloss.split(";", ",").map { it.trim() }.filter { it.isNotEmpty() }),
            acceptedReadings = emptyList(),
            otherWords = emptyList(),
            synonyms = synonyms,
            resolved = false,
            asksReading = false,
        )
    }

    val answers = kanjiAnswers(detail.onReadings, detail.kunReadings)
    val shown = detail.readingGroups.map { KanjiReadingExample(it.reading, it.examples.firstOrNull()) }
    val shownKeys = shown.map { it.key }.toSet()
    val rarer = answers.values.distinct().filterNot { it in shownKeys }
    // An on'yomi without an example is shown as the dictionary writes it, in katakana.
    val onByKey = detail.onReadings.associateBy { katakanaToHiragana(it.replace("-", "")) }

    return ReviewCard(
        itemId = item.id,
        text = item.key.text,
        reading = "",
        senses = listOf(detail.meanings),
        acceptedReadings = answers.keys.toList(),
        otherWords = emptyList(),
        synonyms = synonyms,
        resolved = true,
        asksReading = answers.isNotEmpty(),
        kanji = KanjiReveal(
            readings = shown,
            rarer = rarer.map { onByKey[it] ?: it },
            keyOf = answers,
        ),
    )
}
