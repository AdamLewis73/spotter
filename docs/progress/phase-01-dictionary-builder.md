# Phase 1 — Dictionary Builder

**Status:** Phase 1 complete — build, verification, attribution and size review all done
**Updated:** 2026-08-07

## Current state

`tools/dictbuild/` builds a 99.7 MB `spotter.db` in about 45 seconds: `kanji`, `word`, `word_sense`, `example`, `kanji_in_word`, `meta`. All four parsers are in, plus the `changes` diff and a verification harness. Nothing outstanding.

```
python fetch.py     # sources from the pinned manifest (D-41)
python build.py     # spotter.db from schema.sql + the ingest stages
```

Design decisions from this phase are D-38 through D-59, with verification cases V-17 through V-25. Nothing below should be re-litigated without reading those.

### Findings that changed the plan

- **KanjiVG ships one combined XML** (~3.6 MB gzipped), not ~11,000 individual SVGs.
- **Three of the four sources have no version history** — JMdict is regenerated daily at a fixed URL, and a past version cannot be requested. That is why D-55 commits them.

## Inspection findings — 2026-08-05

All seven candidates downloaded (51.4 MB) and examined. The shipped set is
four sources, 28.8 MB — the example-sentence candidates were narrowed to one
by D-51, which folded it into the JMdict variant. Both and examined as raw text. Both GitHub checksums verified. JMdict and
KANJIDIC2 both generated 2026-08-06.

### JMdict — 218,329 entries

| Element | Count | Why it matters |
|---|---|---|
| `re_restr` | 6,208 | Reading↔writing restrictions are common, not an edge case |
| `stagk` / `stagr` | 746 / 1,183 | Sense restrictions are rare but real |
| `ke_pri` / `re_pri` | 56,933 / 62,678 | **Only ~26% of entries carry any priority tag** |

- **Multiple entries share one writing.** 上手 is *two* entries: `1353320` (じょうず, じょうて`ok`, じょうしゅ`ok` — "skillful") and `1580400` (うわて, かみて — "upper part"). Longest-match lookup must expect several entries per key.
- **`&ok;` marks out-dated kana.** じょうて and じょうしゅ are real JMdict readings nobody uses. Kept and marked archaic rather than hidden (D-53) — a temple inscription is exactly where they are needed.
- **`&gikun;` flags irregular readings.** 明日's あした and あす both carry it; みょうにち does not. JMdict marks jukujikun for us — V-03 doesn't have to infer it.
- 明日 also carries `<stagr>あす</stagr>` on its "near future" sense, so both restriction mechanisms appear in one familiar entry.
- Part-of-speech and info values are **XML entities** (`&n;`, `&adj-na;`, `&ok;`) declared in the internal DTD subset. The parser must resolve them.
- **Frequency covers a minority of entries.** Unranked words need a defined sort position (last), or V-04's ordering will be arbitrary for three-quarters of the dictionary.

### KANJIDIC2 — three traps not previously known

- **`radical` is a number, not a character.** 生 gives `<rad_value rad_type="classical">100</rad_value>`. Rendering it would need a 214-entry number→glyph mapping KANJIDIC2 does not contain. Moot — D-50 drops radicals, which retired the task.
- **`<meaning>` includes other languages.** 生 carries English, French, Spanish and Portuguese glosses. English ones are the elements *without* an `m_lang` attribute. Ingesting all of them would silently fill the app with French.
- **~60 kun'yomi are legitimately katakana** — found by an assertion during ingest, not by reading. Japanese writes loanwords in katakana, and KANJIDIC2 preserves that for unit ateji (粁 キロメートル, 吋 インチ) and chemical elements (鋁 アルミニウム). Two are common kanji: 志 (freq 823) reads both こころざし and シリング; 粉 (1,484) reads こな and デシメートル. Forcing kun'yomi to hiragana would corrupt these. See D-37's exception note and V-24.
- Confirmed as expected: `<freq>` present (生 = 29), on'yomi in katakana, kun'yomi with `.` okurigana markers plus `-` for prefix/suffix position (`なま-`, `-う`), `<nanori>` in separate elements, `<jlpt>4</jlpt>` present but pre-2010 (excluded, D-42).

### JmdictFurigana — format confirmed, and V-17 confirmed by data

Format is `text|reading|index:kana;index:kana`.

```
先生|せんせい|0:せん;1:せい
明日|あした|0-1:あした        ← RANGE, not per-character
学校|がっこう|0:がっ;1:こう    ← gemination
花火|はなび|0:はな;1:び        ← rendaku
```

- **Range notation `0-1:` marks jukujikun explicitly.** The dataset already refuses to split あした across characters, so V-03 is a matter of honouring the format rather than detecting the case.
- **The sound-change problem is real and confirmed.** 学 carries がっ (not がく) and 火 carries び (not ひ). Matching these back to KANJIDIC2 readings needs the fuzzy normalization V-17 describes.

### Example sentences — the question is now a real choice

| Source | Structure | Verdict |
|---|---|---|
| **`JMdict_e_examp`** | `<example>` nested **inside `<sense>`**, with Tatoeba id, surface form, and jpn/eng pair | Sense-level linkage, zero matching work. **13.2% entry coverage**, 32,035 examples, ~1.1 per covered entry |
| **Tanaka** (`examples.utf`) | `A:` line is the jpn/eng pair; `B:` line indexes each word with reading, `[sense#]`, `(#ent_seq)`, and `{surface}` | Richer — more sentences per word, and it also carries sense indices. Costs a custom parser and the join |
| **Tatoeba** (raw) | `id \t lang \t text` only | **Ruled out.** No English pairing (separate links file) and no word index. We would rebuild, worse, what the other two already have |

Both surviving candidates draw on the same underlying corpus — `JMdict_e_examp`'s examples cite Tatoeba ids, and Tanaka is the curated Japanese-English subset of Tatoeba. The difference is who does the joining.

**Resolved by D-51** — `JMdict_e_examp`, ingested here and rendered in Phase 2 (D-69). Tanaka and the Tatoeba export were dropped from the manifest. Measurements that settled it:

| | Common senses attached | Common entries with any example |
|---|---:|---:|
| `JMdict_e_examp` | **41.4%** | 55.9% |
| Tanaka | 7,537 pairs only | 57.3% |
| Both combined | 43.2% | 57.3% |

Combining adds **1.7 points**, because they are the same corpus — `JMdict_e_examp`'s covered words are a strict subset of Tanaka's. The ~43% ceiling is a corpus limitation: roughly 57% of common senses have no attested sentence anywhere. More sentences don't help, because the bottleneck is *sense annotation*, not supply.

## Next action

**Phase 2** — the Android app with a text box. Size settled at 99.7 MB on disk /
30.3 MB gzipped, so the APK contribution is 30 MB and the device footprint about
135 MB once Room extracts the asset.

**One case opened against this phase after it closed: V-29** (found 2026-08-28
from Phase 6). `freq_rank` unions the writing's priority into every reading, so a
word's own readings arrive equal and 一人 leads with いちにん instead of ひとり.
The fix is **D-84** — a reading-level rank as the tiebreak — and both the
measurements and the two rejected alternatives live there and in V-29 rather
than here, because they are an expected value and a decision, not work in
flight.

It needs a rebuild and a `SCHEMA_VERSION` bump, which is a re-extract rather
than a migration (D-38).

## Done

- [x] `tools/dictbuild/` skeleton and fetch script with pinned manifest (D-41)
- [x] Source datasets downloaded and inspected; findings recorded here
- [x] Example-sentence source chosen — `JMdict_e_examp` (D-51)
- [x] Schema finalized — `schema.sql`, applies cleanly
- [x] JMdict parser — 322,323 words, 388,895 senses; `re_restr`, `stagk`/`stagr` honoured (V-18)
- [x] Frequency derivation rule stated and applied — nf band, else 49/50, else NULL (V-04)
- [x] KANJIDIC2 parser — 13,108 kanji, 2,501 ranked (no grade, radical or JLPT: D-50, D-42)
- [x] JmdictFurigana ingest → `kanji_in_word` — 574,721 rows, 7 unmatched keys
- [x] Kana script normalization tolerant of rendaku and gemination — **2.09% residue** (D-37, V-17, V-22)
- [x] KanjiVG ingest → stroke paths — 6,416 kanji, **100% of the ranked 2,501**
- [x] Example sentences ingested from `<sense>` — 58,839 rows — **not rendered** (D-51)
- [x] `meta` table with build id and per-source header dates (D-41)
- [x] `changes` table + key-set diff against previous build (D-39, V-19)
- [x] Indexes for lookup patterns — all six verified by EXPLAIN QUERY PLAN
- [x] Output size measured — **99.7 MB on disk, 30.3 MB gzipped**
- [x] Attribution text collected — `docs/attribution.md`
- [x] Verification harness — `verify.py`, **11 of 11 cases pass**
      *(V-05 is a review check with no user DB yet; V-23 is a Phase 2 UI case.
      V-21 gained a data half in Phase 2 — it asserts the `re_inf` vocabulary is
      exactly the five codes the app decodes, so a sixth arriving in a source
      refresh fails the build instead of rendering as an ordinary reading)*

## Open questions

- **Two guards will bite on the next source refresh (D-41). Fix them when
  dictbuild is next touched — not urgent, since a refresh happens once or twice
  a year.**
  - **V-09 asserts `ranked == 2501`, an exact equality against KANJIDIC2.**
    It fails if upstream gains or loses a single ranked kanji, which it
    eventually will. That is a spurious failure, not a caught regression — a
    lower bound (`>= 2400`) protects against the real fault, which is coverage
    collapsing, without breaking on ordinary upstream drift.
  - **The D-56 size band disagrees with itself.** `build.py` prints a warning
    and returns 0; the CI step hard-fails. A refresh landing at 120 MB therefore
    passes locally and fails in CI, which is a confusing way to find out. Make
    them agree — the CI behaviour is the right one.
  - Neither affects a build from unchanged sources, which is why they have not
    fired yet.
- **Verb-stem conjugation in the reading matcher.** Optional. D-52's normalizer leaves 2.25% unmatched; roughly half of that is stem forms (引き, 言い, 売り, 買い) that a conjugation rule would catch, taking the residue to about 1.5%. Diminishing returns — decide after seeing whether the Examples tab looks thin anywhere.
- **Trimming vocabulary, if the device footprint ever matters more than coverage.** 86% of words carry no frequency tag; keeping only tagged ones would drop 278,012 words, 315,281 senses and 503,512 alignments. Rejected for now — it is one-way once users have saved words (D-11) and risks "not found" on legitimate text. Revisit only with a real reason.

*Closed by the size review and the build:*

- **Final DB size** — 99.7 MB on disk, 30.3 MB gzipped (D-56).
- **FTS5 or plain indexes** — plain. Longest-match is N indexed equality lookups on substrings, not a text search; the `UNIQUE (text, reading)` constraint already serves lookups by text alone. FTS5 buys nothing (D-57, and the note in `schema.sql`).
- **Does the frequency rule survive the data** — yes. 学校/生活/生産 land at nf01, 先生 at nf02, 誕生日 at 49 (common but unbanded). V-04 passes.
- **Example volume** — moot. `JMdict_e_examp` carries roughly one sentence per sense; only 380 senses have more than one, so a cap would never bind.

*Resolved since last update:* dictionary versioning (now the `meta` table); JLPT handling (D-42); refresh cadence (D-41); merge/removal handling (D-39, D-40, D-43); **the radical number→glyph mapping, retired by D-50 dropping radicals entirely** — a UX simplification that removed a data-sourcing task.

*Also resolved:* whether to commit the compressed sources (D-55 — yes, ~29 MB, with `.gitattributes -text` so checksums survive a Windows checkout); the `&ok;` display policy (D-53 — kept and marked archaic); sensitive senses (D-54 — two toggles with opposite defaults, obscurity handled as ranking rather than a setting); and the reading-alignment approach (D-52 — normalize, keep the residue as NULL).

*Parked, not blocking Phase 1:* ambiguity chips (D-07 records the current position; the alternative is always taking the longest match) and word-screen formatting under D-48.

## Notes

- The dictionary DB is **read-only and disposable** (D-09, D-38). It never needs a migration — regenerate and replace the file. Do not add migration machinery here. Stable dictionary-owned IDs were considered and rejected; D-38 records why, so it doesn't get re-proposed.
- **Never expose row IDs to user data** (D-11). User data references words by text + reading.
- `kanji_in_word` (from JmdictFurigana) is an **internal index only** (D-13). It powers "words grouped by reading" and the Examples tab; per-character readings are never rendered (D-06).
- Frequency ranking is not optional — unranked example lists surface obscure words and feel broken.
- **Kana script matters (D-37).** The hard part isn't the script conversion, it's deciding *which* readings are on'yomi when the surface kana has been altered by rendaku or gemination. 学校 = がっこう, not がくこう. See V-17 for the two silent failure modes.
- **A JMdict entry is not a word.** Expanding one into `(text, reading)` rows by cross-product invents words that don't exist. See V-18.
- This script is the most portable asset in the project. It produces a plain SQLite file usable on Android, iOS, or anywhere else.

## Size breakdown

**Recorded as decisions: D-56 (storage layout) and D-57 (indexing).** Those carry the reasoning and the revert paths; what follows is the measurement they came from.

Measured by dropping each object and re-VACUUMing, on the 126 MB build before
trimming:

| Object | Size | Share |
|---|---:|---:|
| `kanji_in_word` | 32.9 MB | 26% |
| `word_sense` | 29.0 MB | 23% |
| `word` | 27.6 MB | 22% |
| `idx_kiw_group` | 11.7 MB | 9% |
| `idx_word_reading` | 8.3 MB | 7% |
| `strokes` | 7.5 MB | 6% |
| `example` | 7.4 MB | 6% |

`kanji_in_word` held only 3.1 MB of actual reading text against 32.9 MB stored,
and `word` 3.2 MB against 27.6 MB — a 10x and 8.6x ratio. Tables with a
composite `PRIMARY KEY` or `UNIQUE` constraint store every row twice, once in
the hidden rowid b-tree and once in the auto-index.

What was taken, none of it costing v1 functionality:

| Object | Before | After | Change |
|---|---:|---:|---:|
| `kanji_in_word` | 32.9 MB | 21.7 MB | **−11.2** |
| `word_sense` | 29.0 MB | 23.0 MB | **−6.0** |
| `idx_word_reading` | 8.3 MB | — | **−8.3** |
| `strokes` | 7.5 MB | 6.3 MB | −1.2 |
| `kanji` | 1.1 MB | 0.9 MB | −0.2 |
| `idx_kiw_group` | 11.7 MB | 12.5 MB | +0.8 |
| `word`, `example` | | unchanged | |

Three mechanisms, none of which drops a single row:

- **`WITHOUT ROWID`** on `kanji`, `word_sense`, `kanji_in_word`, `changes` and
  `meta`. SQLite normally gives a table a hidden `rowid` and stores rows in a
  tree keyed by it; declaring a `PRIMARY KEY` over real columns then builds a
  *second* tree holding those columns again. `WITHOUT ROWID` stores the rows
  directly in the key tree — one tree instead of two.
- **Dropping `idx_word_reading`.** An index is a whole sorted copy of a column
  plus pointers, 322,323 entries deep. Nothing in v1 looks a word up by its
  reading; OCR always supplies the written form.
- **SVG coordinates to one decimal.** 0.009% of a 109-unit canvas — under a
  tenth of a pixel at any size a phone renders.

`idx_kiw_group` grew 0.8 MB because a secondary index on a `WITHOUT ROWID`
table must store the full primary key instead of a compact rowid. That is the
trade, and the 11.2 MB the table itself gave back dwarfs it.

**`strokes` is deliberately NOT `WITHOUT ROWID`.** Making it so cost 3.4 MB and
swamped the rounding saving, because that layout puts row content on interior
b-tree pages — fine for narrow rows, bad for the ~1 KB of path data per row
here. Caught only by measuring per object rather than trusting the total.

**126.2 MB -> 99.7 MB on disk, 45.4 MB -> 30.3 MB gzipped.**

Not taken, and both remain available:

- **Excluding `example` from the shipped asset** would save 8.1 MB. It renders
  nowhere in v1 (D-51), but shipping it keeps the Phase 2 decision a pure UI
  question, which was D-51's whole point.
- **Trimming vocabulary.** 86% of words carry no frequency tag. Keeping only
  tagged words would drop 278,012 words, 315,281 senses and 503,512 alignments —
  a very large saving. Rejected for now: it is one-way once users have saved
  words (D-11), and it risks "not found" on legitimate text. The frequency tags
  do cover real signage well — 立入禁止, 消火器 and 非常口 are all tagged — but
  the tail is where scanning surprises come from.
