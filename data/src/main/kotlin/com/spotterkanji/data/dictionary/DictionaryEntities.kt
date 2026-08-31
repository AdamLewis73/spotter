package com.spotterkanji.data.dictionary

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room's view of the dictionary tables.
 *
 * **These mirror `tools/dictbuild/schema.sql`; they do not define it.** The
 * schema is authored in Python and the database is built on a desktop (D-10),
 * so Room is a reader here, not the owner. Room validates its expectations
 * against the real file when it opens, and a mismatch throws at runtime rather
 * than at compile time — so changing `schema.sql` means changing these in the
 * same commit.
 *
 * `WITHOUT ROWID` (D-56) is invisible to Room's **column** check — `PRAGMA
 * table_info` reports the same shape either way — but **not** to its index
 * check. See [KanjiInWordRow] for what that costs.
 */
@Entity(tableName = "word")
data class WordRow(
    // Internal to the dictionary and reassigned on every rebuild. It must never
    // reach user data or any durable contract (D-11) — it exists only to join
    // to word_sense within a single query.
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "reading") val reading: String,
    @ColumnInfo(name = "ent_seq") val entSeq: Long,
    @ColumnInfo(name = "reading_info") val readingInfo: String?,
    @ColumnInfo(name = "freq_rank") val freqRank: Int?,
    /**
     * Rank from the reading's own `re_pri` alone (D-84).
     *
     * Only ever a **tiebreak between the readings of one written form**, never a
     * way to rank words against each other — that stays [freqRank], which unions
     * the writing's markers in. Doing both with one number is what left 一人
     * leading with いちにん (V-29).
     *
     * **Null-ness is the signal; the magnitude is not.** Queries order by
     * `reading_freq_rank IS NULL` and stop there — comparing bands between two
     * readings promotes the formal register over the spoken one and reverses
     * 明日 (みょうにち bands 5, あした 49). See D-84.
     */
    @ColumnInfo(name = "reading_freq_rank") val readingFreqRank: Int?,
    // `defaultValue` must mirror schema.sql's `DEFAULT 0`. Room compares
    // defaults during validation, so omitting it here fails the open with an
    // "invalid schema" error rather than anything about defaults.
    @ColumnInfo(name = "is_common", defaultValue = "0") val isCommon: Int,
)

/**
 * Checked with `PRAGMA table_info` before writing: no nullable primary key, no
 * column defaults, no foreign keys — so unlike `word`, this one needed nothing
 * mirrored. Doing that check first is faster than reading a Room validation dump.
 */
@Entity(tableName = "kanji")
data class KanjiRow(
    @PrimaryKey @ColumnInfo(name = "char") val character: String,
    @ColumnInfo(name = "meanings") val meanings: String,
    @ColumnInfo(name = "on_readings") val onReadings: String,
    @ColumnInfo(name = "kun_readings") val kunReadings: String,
    @ColumnInfo(name = "stroke_count") val strokeCount: Int,
    @ColumnInfo(name = "freq_rank") val freqRank: Int?,
)

/**
 * The table D-04 is built on, and the reason it has to be declared even though
 * nothing maps rows to it directly: Room validates every `@Query` against the
 * schema it knows, so querying an undeclared table fails the *build* with
 * `no such table: kanji_in_word`.
 *
 * The foreign keys are mirrored because Room compares those — omitting one
 * compiles and then throws at runtime when it validates the real file.
 *
 * **The index column list looks wrong and is right.** `schema.sql` declares
 * `idx_kiw_group` on `(kanji_char, reading_group, word_freq)` — three columns —
 * yet two more are listed below.
 *
 * This table is `WITHOUT ROWID` (D-56), and SQLite **appends the primary key to
 * every secondary index** on such a table, because there is no rowid to locate
 * the row with. `PRAGMA index_info` therefore reports five columns, and Room
 * compares index sets **exactly**: leaving the index undeclared fails just as
 * hard as declaring the three-column form, with `indices = { }` expected against
 * one found.
 *
 * So the earlier note that `WITHOUT ROWID` is invisible to Room holds only for
 * `PRAGMA table_info`. It is emphatically visible through `index_info`.
 *
 * If `schema.sql` ever changes this table's primary key, this list changes with
 * it — the trailing columns are the primary key, in primary-key order.
 */
@Entity(
    tableName = "kanji_in_word",
    primaryKeys = ["kanji_char", "word_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = WordRow::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
        ),
        ForeignKey(
            entity = KanjiRow::class,
            parentColumns = ["char"],
            childColumns = ["kanji_char"],
        ),
    ],
    indices = [
        Index(
            name = "idx_kiw_group",
            value = ["kanji_char", "reading_group", "word_freq", "word_id", "position"],
        ),
    ],
)
data class KanjiInWordRow(
    @ColumnInfo(name = "kanji_char") val kanjiChar: String,
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "surface_reading") val surfaceReading: String,
    @ColumnInfo(name = "canonical_reading") val canonicalReading: String?,
    @ColumnInfo(name = "reading_group") val readingGroup: String?,
    @ColumnInfo(name = "reading_type") val readingType: String?,
    @ColumnInfo(name = "word_freq") val wordFreq: Int,
)

/**
 * A projection, not a table — the result of joining `kanji_in_word` to `word`
 * for the Examples tab. Room maps query columns onto this by name, so the
 * aliases in the query have to match these properties exactly.
 */
data class KanjiExampleRow(
    val readingGroup: String,
    val readingType: String?,
    val text: String,
    val reading: String,
    val wordFreq: Int,
    val glosses: String?,
)

/** One row. Lets the app tell which dictionary build it is holding (D-58). */
@Entity(tableName = "meta")
data class MetaRow(
    @PrimaryKey @ColumnInfo(name = "build_id") val buildId: String,
    @ColumnInfo(name = "source_versions") val sourceVersions: String,
)

/**
 * The `REFERENCES word(id)` in `schema.sql` must be declared here too. Room
 * compares foreign keys during validation, so a real constraint the entity does
 * not know about fails the open with "invalid schema" — saying nothing about
 * foreign keys.
 */
@Entity(
    tableName = "word_sense",
    primaryKeys = ["word_id", "sense_order"],
    foreignKeys = [
        ForeignKey(
            entity = WordRow::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
        ),
    ],
)
data class WordSenseRow(
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "sense_order") val senseOrder: Int,
    // JSON arrays, stored as TEXT by the builder. Parsed in the repository
    // rather than by a Room TypeConverter, so the mapping stays visible.
    @ColumnInfo(name = "glosses") val glosses: String,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String?,
    @ColumnInfo(name = "misc") val misc: String?,
)

/**
 * KanjiVG's per-stroke outlines, one row per kanji (Phase 3).
 *
 * `svg_paths` is a JSON array of SVG path `d` strings in drawing order, parsed
 * in the repository like every other JSON column here rather than by a Room
 * TypeConverter, so the mapping stays visible at the call site.
 *
 * **`schema.sql` gained an explicit `NOT NULL` on `kanji_char` for this entity
 * to be declarable at all.** `PRIMARY KEY` does not imply `NOT NULL` in SQLite
 * outside `INTEGER PRIMARY KEY` and `WITHOUT ROWID` tables, and this table is
 * neither (D-56) — so the column really was nullable, `PRAGMA table_info`
 * reported `notnull=0`, and Room compares that field exactly against the
 * non-null `String` below. The fix belongs in the builder, not here: Room
 * rejects a nullable primary key at compile time, so there is no entity shape
 * that reads the old file.
 *
 * The `REFERENCES kanji(char)` is mirrored for the usual reason — a real
 * foreign key the entity omits fails the open with a message that never
 * mentions foreign keys.
 */
@Entity(
    tableName = "strokes",
    foreignKeys = [
        ForeignKey(
            entity = KanjiRow::class,
            parentColumns = ["char"],
            childColumns = ["kanji_char"],
        ),
    ],
)
data class StrokesRow(
    @PrimaryKey @ColumnInfo(name = "kanji_char") val kanjiChar: String,
    @ColumnInfo(name = "svg_paths") val svgPaths: String,
)

/**
 * Example sentences, keyed to a word AND a sense (D-51).
 *
 * `tatoeba_id` is declared even though nothing reads it: Room validates every
 * column of a table it maps, so omitting it fails the open with an "invalid
 * schema" error rather than being quietly ignored.
 */
@Entity(
    tableName = "example",
    foreignKeys = [
        ForeignKey(
            entity = WordRow::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
        ),
    ],
    indices = [
        Index(name = "idx_example_word", value = ["word_id", "sense_order"]),
    ],
)
data class ExampleRow(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "sense_order") val senseOrder: Int,
    @ColumnInfo(name = "japanese") val japanese: String,
    @ColumnInfo(name = "english") val english: String,
    @ColumnInfo(name = "tatoeba_id") val tatoebaId: Long?,
)
