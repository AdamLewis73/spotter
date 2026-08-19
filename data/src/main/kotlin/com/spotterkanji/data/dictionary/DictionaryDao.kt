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
     */
    @Query(
        """
        SELECT * FROM word
        WHERE text = :text
        ORDER BY freq_rank IS NULL, freq_rank, reading
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

    /** The dictionary's own version — a hash of the sources and builder (D-65). */
    @Query("SELECT build_id FROM meta LIMIT 1")
    suspend fun buildId(): String?
}
