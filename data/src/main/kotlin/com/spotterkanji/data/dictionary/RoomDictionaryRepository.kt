package com.spotterkanji.data.dictionary

import com.spotterkanji.domain.dictionary.DictionaryEntry
import com.spotterkanji.domain.dictionary.DictionaryRepository
import com.spotterkanji.domain.dictionary.ExampleSentence
import com.spotterkanji.domain.dictionary.KanjiDetail
import com.spotterkanji.domain.dictionary.KanjiExample
import com.spotterkanji.domain.dictionary.KanjiReadingGroup
import com.spotterkanji.domain.dictionary.KanjiSummary
import com.spotterkanji.domain.dictionary.ReadingStatus
import com.spotterkanji.domain.dictionary.Sense
import com.spotterkanji.domain.dictionary.forDisplay
import com.spotterkanji.domain.text.isKanji
import org.json.JSONArray

/**
 * The Room-backed [DictionaryRepository].
 *
 * Note what does *not* cross this boundary: [WordRow.id]. Dictionary row ids are
 * reassigned on every rebuild, so they stay inside this class and the domain
 * gets the natural key instead (D-11).
 */
class RoomDictionaryRepository(
    private val dao: DictionaryDao,
) : DictionaryRepository {

    override suspend fun lookup(text: String): List<DictionaryEntry> {
        val words = dao.wordsByText(text)
        if (words.isEmpty()) return emptyList()

        // One query for every sense, then group in memory. The alternative — a
        // query per word — is N+1 round trips for a word like 上手 that has
        // three readings.
        val sensesByWord = dao.sensesFor(words.map { it.id }).groupBy { it.wordId }
        // Keyed by (word, sense) so a sentence lands under the meaning it
        // actually attests rather than under the first one (D-51).
        val examplesBySense = dao.examplesFor(words.map { it.id })
            .groupBy { it.wordId to it.senseOrder }

        // D-51: a sentence belongs to the ENTRY, and V-18 expands one entry into
        // a word per reading — so every reading inherits it. 明日's sentence uses
        // あした and would otherwise appear under みょうにち too, asserting a
        // reading the sentence does not contain. 11,622 entries are affected.
        //
        // The entry's best-ranked CURRENT reading is the one the corpus almost
        // always used, so sentences show there and nowhere else.
        //
        // Sorting by status first is load-bearing, not tidiness. The query orders
        // by frequency then kana, and 上手's three readings tie on frequency — so
        // the raw order leads with じょうしゅ, an archaic reading. Picking that as
        // primary handed it the sentence and then suppressed it for being
        // archaic, and じょうず simply lost its example with nothing to show for
        // it. Silent, as ever.
        val sentenceBearing = words
            .sortedBy { ReadingStatus.of(it.readingInfo.toStringList()).ordinal }
            .groupBy { it.entSeq }
            .values
            .map { it.first().id }
            .toSet()

        return words.map { word ->
            // `reading_info` is a JSON array of JMdict re_inf codes. The codes
            // are decoded in :domain, not here — what "ok" means to a reader is
            // a display rule, and this class only knows how to unwrap the JSON.
            val readingTags = word.readingInfo.toStringList()
            DictionaryEntry(
                text = word.text,
                reading = word.reading,
                senses = sensesByWord[word.id].orEmpty().map { row ->
                    Sense(
                        glosses = row.glosses.toStringList(),
                        partsOfSpeech = row.partOfSpeech.toStringList(),
                        misc = row.misc.toStringList(),
                        examples = if (word.id in sentenceBearing) {
                            examplesBySense[word.id to row.senseOrder]
                                .orEmpty()
                                .map { ExampleSentence(it.japanese, it.english) }
                        } else {
                            emptyList()
                        },
                    )
                },
                frequencyRank = word.freqRank,
                isCommon = word.isCommon != 0,
                readingStatus = ReadingStatus.of(readingTags),
                isGikun = ReadingStatus.isGikun(readingTags),
            )
            // Current readings first, search-only ones dropped where the word has
            // anything else to show (V-21, D-66). The query cannot do either: it
            // orders by frequency, and an archaic reading inherits the writing's
            // frequency, so 上手 arrives here headed by じょうしゅ.
        }.forDisplay()
    }

    override suspend fun existingWords(texts: Set<String>): Set<String> {
        if (texts.isEmpty()) return emptySet()
        return dao.existingWords(texts).toSet()
    }

    override suspend fun kanjiIn(text: String): List<KanjiSummary> {
        // Kana contribute no chip — 生きる is one kanji plus okurigana. Distinct,
        // because 日々 would otherwise query and render 日 twice.
        val characters = text.filter { it.isKanji() }.map(Char::toString).distinct()
        if (characters.isEmpty()) return emptyList()

        val byCharacter = dao.kanji(characters).associateBy { it.character }
        // Ordered by appearance in the word, not by whatever the query returned:
        // the chips under 先生 must read 先 then 生. A character the dictionary
        // does not know is dropped rather than shown as an empty chip.
        return characters.mapNotNull { character ->
            byCharacter[character]?.let {
                KanjiSummary(character = character, meanings = it.meanings.toStringList())
            }
        }
    }

    override suspend fun kanjiDetail(character: String): KanjiDetail? {
        val row = dao.kanji(listOf(character)).firstOrNull() ?: return null

        val groups = dao.readingExamples(character)
            .groupBy { it.readingGroup }
            .map { (reading, rows) ->
                // Deduplicate on READING, not written form. JMdict records
                // 一生けんめい, 一生けん命 and 一生懸命 as separate entries — three
                // ways of writing one word, all いっしょうけんめい — and 学生 has
                // the pre-reform 學生 beside it. Listing each is accurate and
                // useless: it fills the group with what looks like repetition
                // and buries the pattern D-04 is trying to show. The query
                // orders by frequency, so the survivor is the common spelling.
                val examples = rows.distinctBy { it.reading }
                    .take(EXAMPLES_PER_READING)
                    .map { example ->
                        KanjiExample(
                            text = example.text,
                            reading = example.reading,
                            meaning = example.glosses.toStringList().firstOrNull(),
                        )
                    }
                // Lower is commoner; 9999 stands in for unranked (V-04).
                val bestFrequency = rows.minOf { it.wordFreq }
                Triple(reading, rows.first().readingType, examples) to bestFrequency
            }
            // On'yomi before kun'yomi, the order every dictionary uses. Within a
            // type the reading carrying the commonest word leads, so 生's セイ
            // (先生, 学生) precedes ショウ (一生) rather than trailing it.
            .sortedWith(
                compareBy(
                    { (group, _) -> READING_TYPE_ORDER.indexOf(group.second).takeIf { it >= 0 } ?: 99 },
                    { (_, bestFrequency) -> bestFrequency },
                ),
            )
            .map { (group, _) ->
                KanjiReadingGroup(reading = group.first, type = group.second, examples = group.third)
            }

        return KanjiDetail(
            character = character,
            meanings = row.meanings.toStringList(),
            onReadings = row.onReadings.toStringList(),
            kunReadings = row.kunReadings.toStringList(),
            strokeCount = row.strokeCount,
            readingGroups = groups,
            // D-49: a single character scanned on its own lands here directly,
            // so its own word senses have to be reachable from this screen.
            asWord = lookup(character),
            // Null where the dictionary has no KanjiVG data for this character;
            // toStringList() turns that into the empty list the tab renders as
            // "no stroke data" rather than as a blank canvas.
            strokePaths = dao.strokePaths(character).toStringList(),
        )
    }

    /** The dictionary build currently on the device (D-65). */
    suspend fun buildId(): String? = dao.buildId()

    private companion object {
        /**
         * Enough to show a pattern, few enough to scan. 生's い group holds 136
         * words; listing them all would bury the point D-04 is making rather
         * than make it.
         */
        const val EXAMPLES_PER_READING = 8
        val READING_TYPE_ORDER = listOf("on", "kun")
    }
}

/**
 * The builder stores these columns as JSON arrays of strings.
 *
 * `org.json` is part of the Android platform rather than `android.*`, so it does
 * not breach the layering rule (D-60) — but it is also the reason this parsing
 * lives in `:data` and not in `:domain`.
 */
private fun String?.toStringList(): List<String> {
    if (this.isNullOrBlank()) return emptyList()
    val array = JSONArray(this)
    return List(array.length()) { array.getString(it) }
}
