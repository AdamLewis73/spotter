package com.spotterkanji.data.dictionary

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
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
 * `WITHOUT ROWID` on some of these tables (D-56) is invisible to Room: it
 * validates via `PRAGMA table_info`, which reports the same shape either way.
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
    // `defaultValue` must mirror schema.sql's `DEFAULT 0`. Room compares
    // defaults during validation, so omitting it here fails the open with an
    // "invalid schema" error rather than anything about defaults.
    @ColumnInfo(name = "is_common", defaultValue = "0") val isCommon: Int,
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
