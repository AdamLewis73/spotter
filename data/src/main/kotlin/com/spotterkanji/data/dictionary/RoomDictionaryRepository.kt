package com.spotterkanji.data.dictionary

import com.spotterkanji.domain.dictionary.DictionaryEntry
import com.spotterkanji.domain.dictionary.DictionaryRepository
import com.spotterkanji.domain.dictionary.KanjiDetail
import com.spotterkanji.domain.dictionary.KanjiExample
import com.spotterkanji.domain.dictionary.KanjiReadingGroup
import com.spotterkanji.domain.dictionary.KanjiSummary
import com.spotterkanji.domain.dictionary.Sense
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

        return words.map { word ->
            DictionaryEntry(
                text = word.text,
                reading = word.reading,
                senses = sensesByWord[word.id].orEmpty().map { row ->
                    Sense(
                        glosses = row.glosses.toStringList(),
                        partsOfSpeech = row.partOfSpeech.toStringList(),
                        misc = row.misc.toStringList(),
                    )
                },
                frequencyRank = word.freqRank,
                isCommon = word.isCommon != 0,
            )
        }
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
/**
 * CJK Unified Ideographs, plus the extension A and compatibility blocks that
 * JMdict actually uses. Deliberately not `Character.isIdeographic`, which also
 * matches characters this dictionary has no entries for.
 */
private fun Char.isKanji(): Boolean =
    this in '一'..'鿿' ||
        this in '㐀'..'䶿' ||
        this in '豈'..'﫿'

private fun String?.toStringList(): List<String> {
    if (this.isNullOrBlank()) return emptyList()
    val array = JSONArray(this)
    return List(array.length()) { array.getString(it) }
}
