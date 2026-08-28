# Data Model

Read `overview.md` first if you're new — it defines the Japanese-language terms used here.

## Datasets

All free. All require attribution — see the bottom of this file.

| Dataset | Provides | Format | License |
|---|---|---|---|
| **JMdict** (`_examp` variant) | Word entries: writings, readings, senses, part of speech, frequency tags — **plus example sentences inside each `<sense>`** (D-51) | Large XML, gzipped | CC BY-SA (EDRDG) |
| **KANJIDIC2** | Per-kanji data: meanings, on/kun readings, stroke count, frequency rank. *(Also carries grade, radical and a pre-2010 JLPT level — none ingested; D-42, D-50)* | XML, gzipped — 13,108 kanji | CC BY-SA (EDRDG) |
| **KanjiVG** | Stroke order paths | **One combined XML**, gzipped | CC BY-SA |
| **JmdictFurigana** | Per-character reading alignment — **internal index only**, D-13 | Text, one entry per line | Derived, CC BY-SA |
| **KRADFILE** | Kanji → its visual components | Text | CC BY-SA (EDRDG) — **deferred**, see `roadmap.md` |

Example sentences are **not** a separate source: D-51 folded them into the JMdict variant above, where they nest inside each `<sense>`.

### Where they come from

Confirmed August 2026. Acquisition and refresh policy is D-41.

| Dataset | Source | Versioning |
|---|---|---|
| JMdict | `ftp.edrdg.org/pub/Nihongo/JMdict_e_examp.gz` | **None** — regenerated daily at a fixed URL |
| KANJIDIC2 | `edrdg.org/kanjidic/kanjidic2.xml.gz` | **None** — fixed URL |
| KanjiVG | GitHub `KanjiVG/kanjivg` releases | Tagged, immutable, SHA-256 published |
| JmdictFurigana | GitHub `Doublevil/JmdictFurigana` releases | Tagged, immutable, SHA-256 published |

**Four sources, 28.8 MB compressed.** There is no separate example-sentence source — D-51 folded that into the JMdict variant above.

Two practical consequences, both feeding D-41:

- **Three of the four sources have no version history.** A past version of JMdict cannot be requested. Reproducing an old build requires having kept the file — which is why D-55 commits them.
- **The generation date is written into each EDRDG file's header**, so the checksum changes daily whether or not any content did. Pin by header date; a checksum detects difference, not meaningful change.

**All four have been parsed.** Everything below describes structure confirmed against the real files rather than documentation, and the findings that contradicted the documentation are recorded in `progress/phase-01-dictionary-builder.md`.

### Example sentences — settled by D-51

An earlier draft of this file named Tatoeba as a separate source. Three candidates were downloaded and measured on 2026-08-06; **`JMdict_e_examp` won and the others were dropped from the manifest.** Full comparison and the rejected alternatives are in D-51.

Its examples nest inside each `<sense>`, carrying the Tatoeba sentence id, the word's surface form, and a jpn/eng pair:

```xml
<sense>
  <gloss>CD player</gloss>
  <example>
    <ex_srce exsrc_type="tat">162365</ex_srce>
    <ex_text>ＣＤプレイヤー</ex_text>
    <ex_sent xml:lang="jpn">私は、このＣＤプレイヤーをただで得ました。</ex_sent>
    <ex_sent xml:lang="eng">I got this CD player for free.</ex_sent>
  </example>
</sense>
```

**Coverage is 41.4% of common senses** — those belonging to entries with a frequency tag. The ceiling across all sources is about 43%, because the corpus simply doesn't attest the rest. **These are rendered** (D-69, resolving D-51), one per entry under its best-ranked current reading — a sentence belongs to an entry, and an entry expands into a word per reading, so showing it under all of them claims readings the sentence does not contain (V-27).

### Notes on specific datasets

**A JMdict entry is not a word.** One `<entry>` holds several kanji writings *and* several readings, plus explicit restrictions between them — `re_restr` limits a reading to particular writings, and `stagk` / `stagr` limit an individual *sense* to particular writings or readings.

Expanding an entry into `(text, reading)` rows by naive cross-product therefore **invents words that do not exist and attaches meanings to the wrong reading.** Senses must hang off the expanded row, not off the entry. V-02 (上手) is the case that catches this.

**JMdict frequency tags.** Entries carry priority markers (`nf01`–`nf48`, `news1`, `ichi1`, `spec1`) indicating how common a word is. These are **not optional** — they're what makes example lists useful. An unranked list of words containing 生 surfaces obscure vocabulary first and makes the app feel broken.

Note these live on **writing and reading elements separately** (`ke_pri`, `re_pri`), not on the entry, so the rule combines both. As implemented in `ingest_jmdict.freq_rank()`:

| Signal | Stored rank |
|---|---|
| Best `nf01`–`nf48` band across the writing and reading | 1–48 |
| Otherwise `ichi1` / `news1` / `spec1` / `gai1` | 49 |
| Otherwise `ichi2` / `news2` / `spec2` / `gai2` | 50 |
| No priority marker at all | NULL — **sorts last** |

Confirmed against the data: 学校 / 生活 / 生産 land at nf01, 先生 at nf02, 誕生日 at 49. Only **44,311 of 322,323 words** carry any marker, so the NULL case is the common one and must sort last, not first (V-04).

> **Altered by D-84.** Taking the union of `ke_pri` and `re_pri` ranks *words* correctly and orders the readings *within* one word wrongly: a strongly-marked writing floods every reading it pairs with, so readings JMdict separates clearly arrive equal. 一人 leads with いちにん rather than ひとり because of it. D-84 adds a second, reading-level rank from `re_pri` alone as the tiebreak; the combined rank above is unchanged. **Not yet implemented** — V-29 is the case that confirms it.

**KANJIDIC2 details that affect the schema.** Confirmed by inspection 2026-08-05.

- **`radical` is a number, not a character.** 生 yields `<rad_value rad_type="classical">100</rad_value>`. Displaying it would need a 214-entry number→glyph mapping KANJIDIC2 does not contain. **Moot for v1** — D-50 drops the radical entirely, which retires this task. Recorded because the finding outlives the decision: anyone reinstating radicals inherits the mapping problem.
- **`<meaning>` carries several languages.** English glosses are the elements with *no* `m_lang` attribute; French, Spanish and Portuguese sit alongside them. Ingesting indiscriminately fills the app with French.
- **Kun readings carry positional markers** — `.` separates okurigana (`い.きる`), a trailing `-` marks a prefix (`なま-`), a leading `-` marks a suffix (`-う`). These must be stripped before matching against JmdictFurigana's surface readings.
- **Stroke count may have several values** — the first is the accepted count, later ones are common miscounts. V-09 compares this against KanjiVG's path count and must name *which*.
- **`nanori`** are name-only readings. They must not be mixed into kun'yomi display, or the app will teach readings that never appear in ordinary text.
- **A frequency ranking exists** for the 2,501 most common kanji (by occurrence in Mainichi Shimbun). Useful for ordering; worth ingesting.
- **The `jlpt` field is the pre-2010 test** and is deliberately not ingested — see D-42.

**KanjiVG structure.** Distributed as a **single combined XML file** (~3.6 MB gzipped), not as eleven thousand individual SVGs — earlier drafts of this document said otherwise. Each character contains one `<path>` element per stroke, in correct drawing order. Stroke-order animation is therefore rendering those paths sequentially with an animated stroke-dash offset — not a video, not a sprite sheet. Roughly 200 lines of Compose once the data is loaded.

**JmdictFurigana purpose.** It records that in 先生, 先 carries せん and 生 carries せい. This is never shown to the user (D-06), but it is what allows example words to be grouped by which reading a kanji carries (D-04). See D-13.

Format is `text|reading|index:kana;index:kana`, confirmed by inspection:

```
先生|せんせい|0:せん;1:せい
明日|あした|0-1:あした        ← RANGE — jukujikun, do not split
学校|がっこう|0:がっ;1:こう    ← gemination: 学 is がく
花火|はなび|0:はな;1:び        ← rendaku: 火 is ひ
```

Two things fall out of this. **Range notation marks jukujikun explicitly**, so V-03 is a matter of honouring the format rather than detecting the case. And **surface kana routinely differ from dictionary readings**, which is the fuzzy-matching problem in V-17.

Measured over 574,721 spans, the matcher's residue (D-52):

| Matching | Unmatched |
|---|---:|
| Exact comparison only | 8.00% |
| Plus rendaku, gemination, okurigana | **2.09%** |

The 8% is not a random sample — sound changes cluster in *frequent* compounds, because common words erode phonetically. Dropping them would cost 仕事, 出口, 学校 and 一生. The 2.09% that remains is verb stem forms (引き, 言い) and readings KANJIDIC2 simply doesn't record (文 → も in 文字); those are stored with `canonical_reading` NULL, so they join no reading group and never surface.

**Kana script normalization (D-37).** The sources disagree on script, and the ingest must reconcile them deliberately:

| Source | Stores readings as |
|---|---|
| KANJIDIC2 | on'yomi in katakana, kun'yomi in hiragana — matches the target convention |
| JmdictFurigana | hiragana throughout, since furigana is conventionally hiragana |

Reading group labels must display on'yomi in **katakana** and kun'yomi in **hiragana**, so JmdictFurigana's hiragana readings need converting to katakana wherever the reading is an on'yomi — determined by cross-referencing KANJIDIC2's reading lists for that kanji. Furigana rendered over words remains hiragana regardless (D-14); the two conventions are separate and must not be conflated.

## Two databases (D-09)

### Dictionary DB — read-only, shipped as an asset

Built by a desktop Python script (D-10) and loaded via Room's `createFromAsset`. Replaced wholesale on app upgrade, so it **never needs a migration** — if the schema changes, regenerate the file and swap it. Do not build migration machinery for this database.

**99.7 MB on disk, 30.3 MB gzipped** — the gzipped figure is what the APK carries, and the device holds both once Room extracts the asset. Physical layout is D-56 (`WITHOUT ROWID` for narrow rows, plain tables for wide ones) and indexing is D-57 (demand-driven, column order load-bearing). Both carry the per-object measurements; re-measure per object before accepting any layout change, because the total hides a single table moving the wrong way.

Draft schema. Expect revision once the real source files have been inspected:

```
kanji
  char              PK, the character itself: 生
  meanings          English glosses
  on_readings       katakana: セイ, ショウ
  kun_readings      hiragana: い(きる), う(まれる), なま — excludes nanori
  stroke_count      the FIRST KANJIDIC2 value; later ones are miscounts
  freq_rank         Mainichi Shimbun rank, top 2,501 only; null otherwise
                    no jlpt column    (D-42)
                    no grade column   (D-50)
                    no radical column (D-50)

word
  id                internal only — NEVER referenced from user data (D-11)
  text              先生
  reading           せんせい
  ent_seq           JMdict's own entry id; a lookup hint, not an identity

word_sense
  word_id, gloss, part_of_speech, sense_order

word_frequency
  word_id, rank     derived from JMdict priority tags

kanji_in_word       ← from JmdictFurigana; powers D-04 and the Examples tab
  kanji_char        生
  word_id
  position          character index within the word
  surface_reading   がっ  — the kana as it appears in THIS word
  canonical_reading カク  — the dictionary reading it matched; NULL = unmatched
  reading_group     カク / い  — what D-04 groups by (okurigana stripped)
  reading_type      'on' | 'kun' | NULL
  word_freq         word.freq_rank denormalized, NULL stored as 9999

example             ← ingested but NOT rendered in v1 (D-51)
  word_id, sense_order       attaches to a SENSE, not just a word
  japanese, english
  tatoeba_id                 ex_srce; lets a sentence be traced upstream

strokes
  kanji_char, svg_paths

changes             ← D-39; merged and removed entries
  old_text, old_reading      the key that no longer resolves
  new_text, new_reading      where it went, if anywhere
  build_id                   which build it disappeared in

meta                ← one row; lets the app detect an asset upgrade
  build_id                   a pure hash of the source checksums (D-58)
  source_versions            header date + checksum per dataset (D-41)
```

Every column in `meta` must be a function of the sources. A wall-clock
`built_at` lived here until 2026-08-11 and made the database's bytes differ on
every build, so it moved to a `build-info.json` sidecar beside the database
(D-64). That file is build provenance for humans, not an app asset — the app
reads `meta`.

`kanji_in_word` is the table that answers *"show me every common word where 生 is read セイ."* It is queried constantly and rendered never.

**Group by `reading_group`, not `canonical_reading`.** The canonical reading is stored verbatim, so 生 is `い.きる` in 生きる but `い` in 生き残り — the same reading, two values. Grouping on it splits 生's kun readings into 13 groups, several holding one word, which demonstrates no pattern at all. Grouping on the stem gives 8, with 136 words under `い`. Measured on the built database:

| | `canonical_reading` | `reading_group` |
|---|---:|---:|
| 生, kun groups | 13 | 8 |
| words under い | 4 | 136 |

**Sort by frequency with unranked last; never filter on it.** About 86% of words are unranked, and a reading group whose words happen to all be unranked would render as an empty panel — 手's ズ group is exactly that case, with one unranked word. Filtering makes a group that exists in the data show nothing.

**The Examples-tab query must be an ordered index scan, not an aggregate.** `kanji_in_word` carries `word_freq` denormalized and the index is `(kanji_char, reading_group, word_freq)`, so:

```sql
SELECT word_id FROM kanji_in_word
WHERE kanji_char = ? AND reading_group = ?
ORDER BY word_freq LIMIT 12          -- fetch a few extra, dedupe by reading
```

stops after twelve rows. Ordering by the word's own `freq_rank` instead requires joining every row in the group, sorting in a temp b-tree, and only then applying `LIMIT` — so the cost is the size of the whole group. Measured: **10.94 ms → 0.079 ms** for 生/セイ, which holds 1,462 rows. The largest groups belong to the commonest kanji, so this is the screen users open most.

Unranked words are stored as `9999` rather than NULL precisely so a plain ascending scan orders correctly without a `NULLS LAST` clause the index cannot use.

`changes` is **derived** — recomputed each build by comparing this build's `(text, reading)` key set against the previous shipped build's. It accumulates nothing, so the dictionary stays disposable (D-38). The only artifact carried between builds is the previous key list.

### User DB — writable, irreplaceable

Every table follows D-15 (UUID keys), D-16 (`updated_at` + soft delete) as scoped by **D-80**, and D-24 (image paths, not blobs).

**D-80 splits the tables in two.** Rows the user deletes by tapping something — `study_item`, `saved_list`, `list_membership`, `scan` — are soft-deleted and keep a tombstone, because that is how a restored backup or a second device learns the removal was deliberate. Rows that exist only to serve a parent — `srs_state`, `scan_word` — cascade with it. `updated_at` is on every table without exception, cascading ones included, because it serves conflict resolution rather than deletion.

```
study_item
  id            UUID PK                          (D-15)
  type          WORD | KANJI                     (D-27 — v1 always writes WORD)
  text          先生
  reading       せんせい  — part of the identity  (D-12)
  ent_seq       hint only, never the identity    (D-11)
  snapshot_gloss  the gloss line as displayed at save time, ~80 chars.
                  READ ONLY WHEN LIVE LOOKUP FAILS               (D-43)
  created_at, updated_at, deleted_at             (D-16)
  UNIQUE(text, reading, type)

srs_state                       PHASE 7. one row per study_item (D-29)
  study_item_id      FK, ON DELETE CASCADE — no tombstone (D-80)
  due_at             when this item should next be reviewed
  stability          FSRS: days until recall probability falls to ~90%
  difficulty         FSRS: intrinsic difficulty of this item, ~1–10
  review_count
  last_reviewed_at   FSRS needs elapsed time since this to compute recall
  updated_at                                     (D-80 — universal)

review_log                      PHASE 7.
  id UUID, study_item_id        ON DELETE CASCADE — no tombstone (D-80)
  rating             the user's self-assessment: Again | Hard | Good | Easy
  reviewed_at, elapsed_ms
                     kept as history so the schedule can be recomputed if
                     the algorithm is ever retuned or replaced
  updated_at                                     (D-80 — universal)

saved_list
  id UUID, name, created_at, updated_at, deleted_at

list_membership                                  join table (D-28)
  id            UUID PK                          (D-15)
  list_id, study_item_id
  added_at
  updated_at, deleted_at   TOMBSTONED — "remove from list" is a user
                           deletion and must survive a restore (D-80)
  UNIQUE(list_id, study_item_id)

scan
  id UUID, created_at
  raw_ocr_text       kept per D-22's "capture cheap metadata now"
  image_path         RELATIVE path (D-24)
  image_type         FULL_FRAME | WORD_CROP      (D-23)
  app_version        which build created this record

scan_word            links a scanned word to where it appeared
  id            UUID PK                          (D-15)
  scan_id, study_item_id    ON DELETE CASCADE — no tombstone (D-80)
  updated_at                                     (D-80 — universal)
  bbox_x, bbox_y, bbox_w, bbox_h                 (D-22)
  char_offset, char_length
```

**`srs_state` hangs off `study_item`, not off `list_membership`.** That is D-29, and it is the difference between a correct scheduler and one that reviews the same word twice because it lives in two lists.

## The two identity rules

These are the failure modes most likely to corrupt data silently — no crash, no error message, just wrong content appearing months later.

**1. Never reference dictionary rows from user data (D-11).**
A saved item pointing at `dictionary_word_id = 48123` breaks the moment the dictionary is regenerated, because that row number may now hold a different word. Store the text and reading; re-resolve against the dictionary at read time.

**2. Identity is (text, reading), never text alone (D-12).**
上手 is じょうず (skilled), うわて (upper hand), and かみて (stage left) — three distinct vocabulary items a learner must be able to study separately.

### When the key stops resolving

The natural key is far more stable than a row number, but it is not immutable — a corrected reading or a merged entry can retire one. Three mechanisms handle that, and they are deliberately independent:

| | |
|---|---|
| **D-40** | The card renders regardless. An unresolvable item never silently disappears from a list. |
| **D-43** | `snapshot_gloss` gives that card a meaning to show, and keeps it reviewable. |
| **D-39** | The `changes` table upgrades *"no longer in the dictionary"* to *"merged into 上手 (じょうず)"*. |

Only the third depends on the dictionary. The first two hold even if `changes` is empty or missing.

Note the risk profile shifts over time: before release nothing can break, because nobody has saved anything. Afterwards a refresh can orphan real saved words — which is why D-41 refreshes at defined events rather than casually.

## Migrations

Android preserves internal storage and databases across app updates automatically. Uninstalling wipes them. So the risk to user data is **schema changes**, not updates in themselves.

- **`fallbackToDestructiveMigration()` is banned in all build types (D-17).** It resolves missing-migration crashes by deleting the entire user database. It appears throughout online tutorials because it makes the development crash disappear, and it is the most common way Android apps destroy production data.
- **Schema export on, JSON committed to git (D-18).** Room can emit a description of each schema version; committing them lets migrations be written against ground truth rather than memory.
- `AutoMigration` handles simple cases (added column, added table) via annotation. Hand-write anything else.
- **Test with `MigrationTestHelper`, including multi-version chains.** A user on v1.0 installing v1.4 runs 1→2→3→4 in sequence. Never assume the prior installed version was the immediately preceding release.
- Migrations are forward-only.

## Backup

**Android Auto Backup** — a platform feature that backs an app's data up to the user's Google Drive and restores it when they set up a new device. Free, but two constraints matter:

- **The per-app quota is 25 MB.** The dictionary DB and saved images must be excluded via `data_extraction_rules`, or backups silently fail. Only the user DB should be included.
- It can restore an **old** database into a **newer** app version, so migrations must handle arriving from any prior schema version, not just the most recent one.

**Manual export/import (D-20)** — an in-app action producing a versioned JSON or zip through Android's share sheet or file picker, importable on a fresh install or another device. Include a format version field from the very first release, so future importers can recognize and upgrade older files.

Beyond user value, this is the recovery path if a production migration ever fails, and its serialization format is effectively the payload a future sync service would send (D-19).

## Attribution

The CC BY-SA licenses on JMdict, KANJIDIC2, KanjiVG, and JmdictFurigana **require attribution in the shipped app.** This is a license obligation, not a courtesy.

Build the attribution screen early — it's easy to forget until release, and collecting the correct notices is a task best done while the datasets are being ingested in Phase 1.
