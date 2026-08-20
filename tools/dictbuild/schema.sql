-- Spotter dictionary schema
--
-- Read-only, shipped as an Android asset, rebuilt from scratch every build
-- (D-09, D-38). It never needs a migration — regenerate and swap the file.
--
-- NEVER expose these row ids to user data (D-11). User data keys on the
-- natural key (text, reading) and re-resolves at read time.
--
-- Multi-valued fields are stored as JSON arrays rather than child tables where
-- they are only ever read as a unit — meanings, glosses, readings, stroke
-- paths. SQLite ships JSON1 and Room maps them with a TypeConverter. Child
-- tables exist only where rows are queried or filtered individually.

PRAGMA foreign_keys = ON;


-- ------------------------------------------------------------------ kanji

CREATE TABLE kanji (
    char          TEXT    PRIMARY KEY,  -- 生
    meanings      TEXT    NOT NULL,     -- JSON array, ENGLISH ONLY: KANJIDIC2 <meaning>
                                        --   elements WITHOUT an m_lang attribute. The file
                                        --   also carries fr/es/pt.
    on_readings   TEXT    NOT NULL,     -- JSON array, katakana as KANJIDIC2 stores it (D-37)
    kun_readings  TEXT    NOT NULL,     -- JSON array, hiragana, KANJIDIC2 form INCLUDING its
                                        --   markers: "い.きる" (okurigana), "なま-" (prefix),
                                        --   "-う" (suffix). Stored raw and lossless; the UI
                                        --   renders "い.きる" as "い(きる)".
    stroke_count  INTEGER NOT NULL,     -- FIRST value only. Later ones are documented common
                                        --   miscounts, not alternatives (V-09).
    freq_rank     INTEGER               -- Mainichi Shimbun rank, top 2,501 only; NULL otherwise
                                        -- no grade, no radical (D-50); no jlpt (D-42)
) WITHOUT ROWID;


-- ------------------------------------------------------------------ words

-- 322,323 rows expected. One row per (text, reading) pair, expanded from
-- JMdict entries while HONOURING re_restr — a naive cross-product invents
-- 11,547 words that do not exist (V-18).
--
-- Kana-only entries (41,149 of them, ~19%) have no <keb>; their text IS the
-- kana, so text == reading.
--
-- 1,659 merges occur where a key is produced by more than one entry, almost all
-- kana-only homonyms (うん, ギリギリ, カラカラ). Those are MERGED into one row
-- and their senses concatenated, because D-48 renders every sense of a written
-- form on one screen anyway — the entry boundary is not something the UI shows.
-- ent_seq then holds the highest-priority contributing entry, which is fine:
-- D-11 defines it as a lookup hint, never an identity.

CREATE TABLE word (
    -- NOT NULL is redundant to SQLite (an INTEGER PRIMARY KEY is the rowid
    -- alias and cannot be null) but it is NOT redundant to Room: PRAGMA
    -- table_info reports the implicit form as nullable, and Room then rejects
    -- the whole database with "Pre-packaged database has an invalid schema".
    id            INTEGER PRIMARY KEY NOT NULL,  -- internal only, NEVER in user data (D-11)
    text          TEXT    NOT NULL,     -- 先生 — or the kana itself for kana-only words
    reading       TEXT    NOT NULL,     -- せんせい
    ent_seq       INTEGER NOT NULL,     -- JMdict entry id. A HINT, not identity (D-11).
    reading_info  TEXT,                 -- JSON array of re_inf tags: ["ok"], ["gikun"], ...
                                        --   Five codes occur. Display policy is D-66:
                                        --   ok/ik/rk shown and marked, sk hidden unless it
                                        --   is all a word has, gikun never marked. All are
                                        --   ingested regardless — the policy is UI-side.
    freq_rank     INTEGER,              -- derived from ke_pri/re_pri; NULL = unranked.
                                        --   NULL must sort LAST, not first (V-04): only
                                        --   ~26% of entries carry any priority tag.
    is_common     INTEGER NOT NULL DEFAULT 0,   -- 1 if the entry had any priority tag
    UNIQUE (text, reading)
);

-- The UNIQUE constraint above already indexes (text, reading), and a composite
-- index serves queries on its leftmost column — so lookups by text alone are
-- covered and no separate index on text is needed.
--
-- This also settles the FTS5 question: longest-match (D-07) works by taking
-- substrings s[i:i+1] … s[i:i+n] at each position and looking each up exactly.
-- That is N indexed equality lookups, not a text search. FTS5 buys nothing.
-- No index on `reading` alone. It would serve looking a kanji word up BY its
-- reading (typing せんせい to find 先生), and no v1 feature does that — the scan
-- pipeline always arrives with the written form. It cost 8.3 MB, about 7% of the
-- database. One line to add back if a kana search box ever appears.


CREATE TABLE word_sense (
    word_id        INTEGER NOT NULL REFERENCES word(id),
    sense_order    INTEGER NOT NULL,    -- 1-based, JMdict order (editors reorder between
                                        --   releases; do not treat as stable)
    glosses        TEXT    NOT NULL,    -- JSON array: ["skillful","skilled","proficient"]
                                        --   One SENSE, several GLOSSES — rendered as one
                                        --   line, "skillful; skilled; proficient".
    part_of_speech TEXT,                -- JSON array of resolved entities: ["adj-na","n"]
    misc           TEXT,                -- JSON array: ["arch"], ["vulg"], ["col"] …
                                        --   Carries the tags any sensitive-sense filtering
                                        --   policy would need. Ingested, never yet filtered.
    PRIMARY KEY (word_id, sense_order)
) WITHOUT ROWID;

-- Senses restricted by <stagk>/<stagr> attach ONLY to the (text, reading) rows
-- they apply to. 明日 is the worked case: its "near future" sense is stagr-bound
-- to あす and must not appear under みょうにち (V-18).


-- Ingested but NOT rendered in v1 (D-51). 41.4% of common senses have one.
CREATE TABLE example (
    id          INTEGER PRIMARY KEY,
    word_id     INTEGER NOT NULL REFERENCES word(id),
    sense_order INTEGER NOT NULL,       -- attaches to a SENSE, not just a word
    japanese    TEXT    NOT NULL,
    english     TEXT    NOT NULL,
    tatoeba_id  INTEGER                 -- <ex_srce exsrc_type="tat">; traces upstream
);
CREATE INDEX idx_example_word ON example (word_id, sense_order);


-- ------------------------------------------- kanji ↔ word reading alignment

-- From JmdictFurigana. Internal index only, queried constantly, rendered never
-- (D-13, D-06). This is what powers D-04's Examples tab.
--
-- The two-reading design is deliberate. JmdictFurigana gives the kana a kanji
-- carries AS IT APPEARS — 学 is がっ in 学校, 火 is び in 花火 — which differs
-- from its dictionary reading through gemination and rendaku. Matching surface
-- back to canonical is the hardest correctness problem in Phase 1 (V-17), and
-- it has two silent failure modes: dropping a word from its group, or filing it
-- under the wrong reading.
--
-- Storing BOTH, with a NULLABLE canonical, makes those failures countable
-- instead of silent: `SELECT count(*) FROM kanji_in_word WHERE reading_type IS
-- NULL` is the health check (V-22). A build that suddenly cannot match 12% of
-- rows says so, rather than quietly rendering a thinner Examples tab.
--
-- Measured over 574,721 spans (D-52): 8.00% unmatched with exact comparison,
-- 2.09% shipped. Fail the build above ~4%. What remains is verb stem forms
-- (引き, 言い) and readings KANJIDIC2 does not record at all (文 → も in 文字).

CREATE TABLE kanji_in_word (
    kanji_char        TEXT    NOT NULL REFERENCES kanji(char),
    word_id           INTEGER NOT NULL REFERENCES word(id),
    position          INTEGER NOT NULL,  -- character index within word.text
    surface_reading   TEXT    NOT NULL,  -- がっ    — as it appears in this word
    canonical_reading TEXT,              -- カク     — matched KANJIDIC2 reading, verbatim
                                         --   い.きる — kun readings keep their markers
    reading_group     TEXT,              -- カク / い — what D-04 GROUPS BY
    reading_type      TEXT,              -- 'on' | 'kun' | NULL when unmatched
    word_freq         INTEGER NOT NULL,  -- word.freq_rank denormalized, NULL as 9999
    PRIMARY KEY (kanji_char, word_id, position)
) WITHOUT ROWID;

-- Why reading_group exists as a column rather than being derived.
--
-- canonical_reading is stored verbatim, so 生 in 生きる is い.きる and in
-- 生き残り is い — the same reading, two values. Grouping on it fragments 生's
-- kun readings into 13 groups, several holding a single word, which shows no
-- pattern at all. Grouping on the stem collapses them to 8 and puts 28 words
-- under い, which is the pattern D-04 exists to demonstrate.
--
-- It is derivable from canonical_reading, but a derived GROUP BY cannot use an
-- index, and this is the query behind the app's most-used screen. Storing it
-- also keeps the okurigana-stripping rule in the builder rather than duplicated
-- in Kotlin.

-- Jukujikun produce NO rows here. JmdictFurigana marks them with range
-- notation — 明日|あした|0-1:あした — meaning the reading belongs to the whole
-- word and splits across no character. Inventing an alignment would teach a
-- false reading (V-03, D-06).

-- (kanji_char, reading_group) and NOT (kanji_char, reading_type, reading_group).
--
-- reading_group is NULL exactly when reading_type is NULL — both are set only
-- on a successful match — so filtering on the group already excludes unmatched
-- spans, and `reading_type IS NOT NULL` is redundant.
--
-- It is worse than redundant: SQLite compiles IS NOT NULL into a RANGE
-- condition, and a range on the second column stops the third being usable for
-- equality. With reading_type in the middle the Examples-tab query degraded to
-- scanning every row for the kanji and filtering in memory — 5.9ms against
-- 0.1ms for every other indexed lookup in this schema.
-- word_freq is denormalized into this table, and the index carries it as its
-- third column, so the Examples-tab query becomes an ordered index scan that
-- stops after N rows.
--
-- Without it, ordering by the word's frequency requires joining every row in
-- the group to `word`, sorting in a temp b-tree, and only then applying LIMIT —
-- so the query costs the size of the whole group. 生/セイ holds 1,462 rows and
-- 手/て holds 1,835, and those are the most common kanji, i.e. exactly the
-- screens a user opens most. Measured at ~11ms on a desktop before, which would
-- be several times that on a phone, once per reading group on the screen.
--
-- NULL ranks are stored as 9999 rather than NULL so a plain ascending sort puts
-- unranked words last (V-04) without a NULLS LAST clause the index cannot use.
CREATE INDEX idx_kiw_group ON kanji_in_word (kanji_char, reading_group, word_freq);


-- ------------------------------------------------------------- stroke order

-- NOT `WITHOUT ROWID`, unlike every other table here with a non-integer key.
--
-- A WITHOUT ROWID table stores row content in the primary-key b-tree, so wide
-- rows end up on interior pages and inflate the tree. svg_paths averages ~1 KB
-- per row — by far the widest column in this schema. Measured: making this table
-- WITHOUT ROWID cost 3.4 MB, swamping the 1.2 MB the coordinate rounding saved.
-- SQLite's own guidance is that WITHOUT ROWID suits small rows; this is the one
-- table here that isn't.
CREATE TABLE strokes (
    kanji_char TEXT PRIMARY KEY REFERENCES kanji(char),
    svg_paths  TEXT NOT NULL   -- JSON array of path 'd' strings, in drawing order.
                               -- Length must equal kanji.stroke_count (V-09).
);


-- ------------------------------------------------------ build bookkeeping

-- Merged and removed entries, so a saved word that no longer resolves can say
-- where it went instead of "not found" (D-39). DERIVED each build by diffing
-- this build's (text, reading) key set against the previous shipped build's —
-- it accumulates nothing, so the dictionary stays disposable (D-38).
CREATE TABLE changes (
    old_text    TEXT NOT NULL,
    old_reading TEXT NOT NULL,
    new_text    TEXT,               -- NULL when the entry was removed outright
    new_reading TEXT,
    build_id    TEXT NOT NULL,      -- the build in which it disappeared
    PRIMARY KEY (old_text, old_reading)
) WITHOUT ROWID;


-- One row. Lets the app detect an asset upgrade, and records exactly which
-- source files produced this dictionary (D-41).
-- Every column here must be a function of the sources (D-58). A wall-clock
-- `built_at` lived here until 2026-08-11 and made the database's bytes differ
-- on every build; it moved to build-info.json, outside the artefact (D-64).
CREATE TABLE meta (
    build_id        TEXT PRIMARY KEY,
    source_versions TEXT NOT NULL   -- JSON: per source, header date + sha256, straight
                                    --   from sources.lock.json. The header date is the
                                    --   real version identifier — three of the four
                                    --   sources have no version history at all.
) WITHOUT ROWID;
