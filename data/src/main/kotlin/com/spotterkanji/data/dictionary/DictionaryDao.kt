package com.spotterkanji.data.dictionary

import androidx.room.Dao
import androidx.room.Query

@Dao
interface DictionaryDao {

    /**
     * Every entry written as [text], most common first.
     *
     * `freq_rank IS NULL` sorts first in SQLite, and ~74% of entries are
     * unranked — so without the explicit null-last clause the common words this
     * app exists to explain would appear at the bottom of the list (V-04).
     *
     * **`reading_freq_rank` breaks the ties `freq_rank` cannot** (D-84, V-29).
     * `freq_rank` unions the writing's priority markers into every reading, so
     * the readings of one written form routinely arrive equal — 一人's ひとり and
     * いちにん both land at rank 2 — and the sort then fell through to kana order,
     * which put いちにん first.
     *
     * **Only `IS NULL` is consulted, never the number**, and that restraint is
     * the decision rather than an oversight. *Having* a marker of its own is
     * JMdict stating this reading is standard for this writing. The band it sits
     * in is newspaper-corpus frequency, which is register-biased: 明日's
     * みょうにち bands at 5 against あした's 49 because announcements say
     * みょうにち and people say あした. Comparing the numbers reverses 明日 and
     * breaks V-27; comparing only presence leaves it alone.
     *
     * Kana order remains the final tiebreak and still decides real cases: 米
     * (こめ / メートル), 先 (さき / さっき) and 日本 (にっぽん / にほん) all carry
     * markers on both readings, so nothing here separates them — which is
     * correct, because nothing should. Half of V-29 is asserting they do not
     * move, and it is the half every rejected alternative in D-84 fails.
     */
    @Query(
        """
        SELECT * FROM word
        WHERE text = :text
        ORDER BY freq_rank IS NULL, freq_rank,
                 reading_freq_rank IS NULL,
                 reading
        """
    )
    suspend fun wordsByText(text: String): List<WordRow>

    @Query(
        """
        SELECT * FROM word_sense
        WHERE word_id IN (:wordIds)
        ORDER BY word_id, sense_order
        """
    )
    suspend fun sensesFor(wordIds: List<Long>): List<WordSenseRow>

    /**
     * Every example sentence for these words (D-51).
     *
     * One query for the whole lookup rather than one per sense, for the same
     * reason [sensesFor] batches: 上手 would otherwise be a dozen round trips.
     */
    @Query(
        """
        SELECT * FROM example
        WHERE word_id IN (:wordIds)
        ORDER BY word_id, sense_order, id
        """
    )
    suspend fun examplesFor(wordIds: List<Long>): List<ExampleRow>

    /**
     * The written forms among [texts] that exist as words (D-07).
     *
     * `DISTINCT` because a written form has a row per reading — 上手 is five —
     * and longest-match only asks whether the string is a word at all.
     *
     * Served by the `UNIQUE (text, reading)` index on its leftmost column, so
     * this is N indexed equality lookups rather than a scan.
     */
    @Query("SELECT DISTINCT text FROM word WHERE text IN (:texts)")
    suspend fun existingWords(texts: Set<String>): List<String>

    @Query("SELECT * FROM kanji WHERE char IN (:characters)")
    suspend fun kanji(characters: List<String>): List<KanjiRow>

    /**
     * The query D-04 is built on: every common word where [character] takes a
     * given reading.
     *
     * `idx_kiw_group (kanji_char, reading_group, word_freq)` serves this exactly
     * — group, then commonest first. `word_freq` stores 9999 rather than NULL
     * for unranked words precisely so this ordering puts them last without a
     * null-handling clause (V-04).
     *
     * Rows with no `reading_group` are excluded: those are spans the reading
     * alignment could not resolve (D-52), and a group headed by nothing teaches
     * nothing.
     */
    @Query(
        """
        SELECT k.reading_group AS readingGroup,
               k.reading_type  AS readingType,
               w.text          AS text,
               w.reading       AS reading,
               k.word_freq     AS wordFreq,
               (SELECT s.glosses FROM word_sense s
                 WHERE s.word_id = w.id ORDER BY s.sense_order LIMIT 1) AS glosses
        FROM kanji_in_word k
        JOIN word w ON w.id = k.word_id
        WHERE k.kanji_char = :character AND k.reading_group IS NOT NULL
        ORDER BY k.reading_group, k.word_freq, w.text
        """
    )
    suspend fun readingExamples(character: String): List<KanjiExampleRow>

    /**
     * KanjiVG's stroke outlines for [character] as the raw JSON array, or null
     * where the dictionary has no stroke data.
     *
     * Null is a real case, not an error: `strokes` covers 6,416 of the kanji
     * KANJIDIC2 describes, so a `kanji` row can exist without one here. It does
     * cover all 2,501 ranked characters (V-09), so the gap is confined to the
     * rare tail.
     *
     * Selects the one column rather than the row because `kanji_char` is already
     * the argument — nothing downstream needs it echoed back.
     */
    @Query("SELECT svg_paths FROM strokes WHERE kanji_char = :character")
    suspend fun strokePaths(character: String): String?

    /** The dictionary's own version — a hash of the sources and builder (D-65). */
    @Query("SELECT build_id FROM meta LIMIT 1")
    suspend fun buildId(): String?
}
