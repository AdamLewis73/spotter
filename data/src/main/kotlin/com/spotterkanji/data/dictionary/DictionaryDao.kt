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

    /** The dictionary's own version — a hash of the sources and builder (D-65). */
    @Query("SELECT build_id FROM meta LIMIT 1")
    suspend fun buildId(): String?
}
