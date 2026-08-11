# Verification Cases

Known-good expected values, tied to the decisions they protect.

## What belongs here

**Only cases where a bug produces plausible-looking output with no error.** That is the selection criterion, and it keeps this file from becoming a generic test plan.

The failure mode this file exists to catch: the script runs, the app builds, the screen renders, nothing throws — and the content is wrong in a way nobody notices for months. Wrong kana script. A word silently collapsed to one of its three readings. Furigana split across characters that don't own it. Examples sorted by database order instead of frequency.

A crash is self-reporting. These aren't.

Each case names the decision it protects, so grepping this file for a decision ID (`D-12`) finds every case that assumes it. Keeping those in sync is part of the supersede procedure documented at the top of `decisions.md` — a stale verification case is worse than none, because it asserts something the project no longer believes.

**These are not unit tests, and both exist.** `tools/dictbuild/test_dictbuild.py` asks whether the *code* is right — pure functions, edge cases, milliseconds. This file asks whether the *data* is right, and `verify.py` runs it against a built database. They catch different things: if a rendaku mapping were wrong, 学校 and 花火 might still resolve while thousands of other words broke, and every case here would pass.

**Readings below should be confirmed against KANJIDIC2 during Phase 1 rather than trusted from this file** — this document records the *shape* of the expected answer and the trap being tested, not an authoritative reading list.

---

## Phase 1 — Dictionary builder

### V-01 · Kana script by reading type (D-37)

The trap: JmdictFurigana supplies all readings in hiragana. On'yomi must be stored and displayed in katakana. Nothing errors if this is skipped — every on'yomi header simply renders in the wrong script.

Query the kanji 生 and check the script of each reading group:

| Reading | Type | Expected script |
|---|---|---|
| セイ | on'yomi | **katakana** |
| ショウ | on'yomi | **katakana** |
| なま | kun'yomi | hiragana |
| い(きる) | kun'yomi | hiragana |

Fails silently as `せい` / `しょう` if normalization is skipped.

**"kun'yomi are hiragana" has ~60 real exceptions** — see V-24. Assert this case on 生, not as a blanket rule over the whole table.

### V-02 · Words with multiple readings survive ingest (D-12)

The trap: a naive "one row per word text" schema keeps whichever reading it encountered last and silently discards the others.

**Confirmed against JMdict 2026-08-06.** 上手 is **two entries carrying five readings** — not one entry, and not the three this case originally assumed:

| ent_seq | Readings | First gloss |
|---|---|---|
| 1353320 | じょうず, じょうて `ok`, じょうしゅ `ok` | skillful |
| 1580400 | うわて, かみて | upper part |

Two things to assert:

1. **Five `(text, reading)` rows survive ingest.** Fewer means identity collapsed to text alone — a D-12 violation that makes the readings unrecoverable later.
2. **The rows come from two different entries.** A parser assuming one entry per writing will drop 1580400 entirely, losing うわて and かみて while still looking correct for じょうず.

じょうて and じょうしゅ carry `&ok;` (out-dated kana). Whatever the display policy for those turns out to be, they must not be *discarded at ingest* — that decision belongs to the UI, not the parser.

### V-03 · Jukujikun alignment (D-06, D-13, D-14)

The trap: assuming every word's reading splits cleanly per character.

明日 is unusually good as a fixture because its readings behave differently from one another:

| Reading | Splits per character? |
|---|---|
| みょうにち | **Yes** — みょう = 明, にち = 日 |
| あした | **No** — jukujikun, the reading belongs to the whole word |
| あす | **No** — also irregular |

Expect `kanji_in_word` to contain rows for みょうにち and **no per-character rows** for あした or あす. If the ingest invents an alignment for あした, it will teach a false reading.

### V-04 · Frequency ranking is applied (D-04)

The trap: examples sorted by insertion order look fine but surface obscure vocabulary, which quietly makes the app's core feature feel broken.

Query example words for 生 grouped by reading. The セイ group should lead with high-frequency words — 先生, 学生, 生活 — not rare compounds. If the ordering looks arbitrary, the JMdict priority tags (`nf01`–`nf48`, `news1`, `ichi1`) were parsed but not applied to sorting.

### V-05 · Dictionary row IDs absent from any user-facing contract (D-11)

Not a data check but a review check, and cheap: grep the export format, the user DB schema, and any serialization for dictionary row IDs. They must not appear. This is verifiable before the user DB exists and gets harder to audit later.

### V-17 · Reading alignment survives sound changes (D-37, D-04, D-13)

**The hardest correctness problem in Phase 1.** JmdictFurigana records the kana a kanji carries *in a specific word*, but that surface kana routinely differs from the kanji's dictionary reading:

| Word | Reading | What happens |
|---|---|---|
| 学校 | がっこう | 学 is がく, surfaces as がっ — **gemination** |
| 花火 | はなび | 火 is ひ, surfaces as び — **rendaku** (voicing) |
| 一生 | いっしょう | 一 is いち, surfaces as いっ |

Classifying a surface reading as on'yomi or kun'yomi means matching it against KANJIDIC2's lists, which requires normalizing for voicing, gemination, and KANJIDIC2's own okurigana markers (`い.きる`, `-がわ`).

Assert that 学 in 学校 is classified **on'yomi** and grouped under カク, and 火 in 花火 **kun'yomi** under ひ.

Two silent failure modes, and they look different: a strict matcher **drops** the word from its reading group, making the Examples tab look thin for no visible reason; a loose matcher **misfiles** it, putting a word under the wrong reading header. Neither errors. V-01 only covers the clean case where surface and dictionary readings already agree.

### V-18 · Entry expansion respects reading and sense restrictions (D-12)

A JMdict entry holds several writings and several readings, with `re_restr` limiting which readings apply to which writings, and `stagk` / `stagr` limiting individual senses. A naive cross-product invents words and misattributes meanings.

V-02 anchors this with 上手. **During inspection, additionally identify one entry using `re_restr` and one using `stagr`**, and assert the expansion honours both — the specific entries aren't named here because they should be found in the real file rather than trusted from this document.

Failure is silent and plausible: a word that reads correctly but carries a meaning belonging to a different reading of the same characters.

### V-19 · The `changes` table catches a retired key (D-39)

Not testable against a single build. Build the dictionary twice from different source snapshots, or synthesize a retirement by removing one entry from a copy of the source.

Expect: the removed `(text, reading)` appears in `changes` with the build id, and with a replacement key where JMdict recorded one. An empty `changes` table on a build where keys genuinely disappeared means the diff never ran — and nothing downstream will complain, because a missing warning looks exactly like no warning being needed.

### V-24 · Loanword kun'yomi keep their katakana (D-37)

The trap is the *opposite* of V-01's. Having established that on'yomi must be katakana, the tempting next step is to force kun'yomi to hiragana. That corrupts about 60 readings, and nothing errors.

Japanese writes loanwords in katakana, and KANJIDIC2 preserves that where a kanji's word-level reading is a loanword:

| Kanji | Frequency | Expected kun readings |
|---|---:|---|
| 志 | 823 | こころざ.す, こころざし, **シリング** |
| 粉 | 1,484 | こ, こな, **デシメートル** |
| 粁 | — | **キロメートル** |
| 吋 | — | **インチ** |

Check 志 specifically: it is a common kanji, so the damage is visible to ordinary users rather than confined to obscure characters. Rendering しりんぐ is wrong — nobody writes it that way.

Expect **about 60** katakana kun readings across the whole table. The build reports the count and flags above 100 (`kun_katakana_loanword`). Zero means something is converting them; a large jump means the source changed.

### V-25 · The build is deterministic (D-41, D-58, D-64)

Identical sources must produce an identical database. `build_id` is a hash of the source checksums, so a build that changes nothing is *labelled* as changing nothing — which is a lie if the output actually varies.

**Check the database itself, not a proxy for it:**

```bash
python build.py && sha256sum data/build/spotter.db
python build.py && sha256sum data/build/spotter.db   # must match
```

This assertion was impossible until 2026-08-11. `meta.built_at` held a wall-clock timestamp, so the bytes differed on every build and the strongest available check was the derived `keys.tsv.gz`. Moving that timestamp to a `build-info.json` sidecar (D-64) made the artefact itself reproducible; CI now checksums it directly.

`build-info.json` is excluded from this check — it is the one output that is *meant* to vary.

Also rebuild the alignment with different `PYTHONHASHSEED` values, which is what catches the specific bug below:

```bash
for seed in 1 2 3; do PYTHONHASHSEED=$seed python build.py --only furigana; done
# then hash: kanji_char, word_id, position, canonical_reading, reading_type
```

All three must agree.

**A second trap, found 2026-08-11.** `build_id` was `%Y%m%d-<hash>`, so two builds from byte-identical sources produced different ids on different days. Nothing errored; the `changes` diff (D-39) simply recorded entries against a build identity that did not mean what it claimed. The date prefix is gone — do not reintroduce one.

**A third, found the moment this check first ran against the database.** `verify.py` opened it read-write, and **simply connecting to a SQLite file read-write changes its bytes** — same size, same content, different checksum. V-19 made it worse by writing to `changes` and deleting the rows afterwards, which looks tidy and is not.

So verifying the dictionary modified it, and this check then compared a *verified* database against a *freshly built* one and reported a reproducibility failure that did not exist. The tooling was wrong, not the build.

`verify.py` now opens read-only, and V-19 does its writing against a throwaway copy. **A tool whose job is to verify an artefact must not write to it** — and note that this class of bug is undetectable until something checksums the artefact, which is the argument for checking the database itself rather than a derived file.

**The trap this caught.** A surface reading can match several readings of the same kanji — 一 is both イチ and イツ, and いっ geminates from either. The matcher originally iterated a Python `set` of candidates, and string hashing is randomised per process, so the winner varied between runs. 一生 resolved to イチ on one build and イツ on the next, from byte-identical inputs.

Nothing errors. Both are real readings of 一. The word simply lands in a different reading group on the Examples tab depending on which process built the dictionary, and the `changes` diff (D-39) would report spurious churn between builds that changed nothing.

The fix — iterate KANJIDIC2's reading list rather than the candidate set — is also more correct, since that list is ordered with the primary reading first.

### V-22 · Reading-alignment residue stays within bounds (D-52, V-17)

A build-health assertion rather than a content check, and the mechanism that makes V-17's silent failures visible.

```sql
SELECT count(*) * 100.0 / (SELECT count(*) FROM kanji_in_word)
FROM kanji_in_word WHERE reading_type IS NULL;
```

| Matcher state | Expected residue |
|---|---|
| Exact comparison only | ~8.0% |
| With rendaku + gemination + okurigana (D-52) | **~2.25%** |
| Plus verb-stem conjugation, if implemented | ~1.5% |

**Fail the build above roughly 4%.** The number is stable across dictionary refreshes because it reflects the matcher, not the data — so a jump means the normalizer broke, not that JMdict changed.

This is the whole reason unmatched spans are stored with NULL rather than dropped. Deleting them destroys the evidence and the Examples tab simply gets thinner, which nobody notices. Spot-check that 仕事, 出口, 学校 and 一生 all resolve — those are the high-frequency compounds that fail under exact matching.

---

## Phase 2 — Tokenization and lookup

### V-06 · Segmentation plus alternates (D-07)

Input: `先生と生産`

| Mechanism | Expected |
|---|---|
| Kuromoji primary parse | 先生 / と / 生産 |
| JMdict longest-match at position 0 | 先生 (longest) **and** 先 (alternate) |

Both halves matter. Only the Kuromoji parse means longest-match isn't running, and the compound-versus-word interaction — the app's whole pedagogical premise — silently won't work. The user could never ask about 先 on its own.

### V-07 · Conjugated verbs resolve to dictionary form (D-07)

Input: `生きた`

Expect resolution to the dictionary entry 生きる. Plain longest-match cannot do this; it's the specific reason Kuromoji is in the stack alongside it. Failure looks like "word not found" on perfectly ordinary text.

### V-08 · Reading labels vs. furigana use different scripts (D-14, D-37)

Two conventions that are easy to conflate, on screen at the same time:

- Furigana rendered above 先生 → **せんせい** (hiragana, always)
- On'yomi group header on the 生 kanji screen → **セイ** (katakana)

If both render in the same script, one convention has been applied globally.

### V-21 · Obsolete readings are visibly distinguished (D-53, D-48)

Scan or paste **上手**. Under D-48 the word screen lists every reading as a section, so all five appear:

| Reading | Tag | Expected treatment |
|---|---|---|
| じょうず | — | normal |
| うわて | — | normal |
| かみて | — | normal |
| じょうて | `ok` | **marked archaic** |
| じょうしゅ | `ok` | **marked archaic** |

The failure this catches: じょうて rendering identically to じょうず. Nothing errors, the screen looks complete, and the app has quietly taught a reading that has not been current for centuries — to a learner with no way to know the difference.

Note the app never knows *which* reading was scanned (D-44, D-53), so this cannot be solved by only showing archaic readings when they were the one photographed. Every reading is always shown; the marking is what carries the information.

Also confirm the reverse: a word with no `ok` readings shows no archaic marking anywhere. A marker applied globally is as wrong as one never applied, and looks just as plausible.

### V-23 · Sense filtering never empties a word (D-54, D-40)

With "show explicit content" **off** — the default — find a word whose senses are *all* tagged `vulg`, `sens`, `derog` or `X`, and open it.

Expect the senses to be **shown anyway**, or an explicit "this word has hidden senses" affordance. What must not happen is the word resolving to nothing.

The trap: filtering is applied per sense, so a word where every sense is filtered silently becomes an empty result. It reads as "the dictionary doesn't have this word" — a broken app rather than a discreet one — and it happens on exactly the words a user is most likely to be puzzled by. Same failure shape as D-40, arriving from a different direction.

Check the counts hold too: roughly 900 senses carry the explicit tags and about 3,900 carry `sl` / `col`. If toggling the explicit filter changes far more than ~900 senses, the wrong tag set is wired to it.

---

## Phase 3 — Stroke order

### V-09 · Stroke count and stroke path count agree (KanjiVG)

The animation still plays and still looks like handwriting whatever the path count is, which is why this needs an assertion rather than an eyeball.

**Compare against the *first* `stroke_count` value only.** KANJIDIC2 may list several; the first is the accepted count and the rest are documented common miscounts.

**But the expected mismatch count is not zero.** Measured on the built database: **109 of 6,416** ingested kanji disagree (1.7%), and **20 of 2,501** ranked ones (0.8%). They are overwhelmingly ±1, and they cluster on characters containing 辶 (shinnyou), which is genuinely drawn with two or three strokes depending on whether the printed or handwritten form is followed — 辻 (5 vs 6), 逗 (10 vs 11), 謎 (16 vs 17), 葛 (11 vs 12).

So the assertion is a **bound, not an equality**: fail above ~150 mismatches. A real parsing fault produces a far higher rate or a systematic offset, not a 1.7% scatter concentrated on one radical. Spot-check that 生 (5), 先 (6), 手 (4), 一 (1) and 鬱 (29) all match exactly — a parser that flattens component groups wrongly fails those immediately.

**Display consequence.** The Stroke Order tab shows the stroke count (D-50). It must show the **number of paths being animated**, not KANJIDIC2's figure — otherwise 辻 says "5 strokes" while the animation visibly draws 6, and the user is watching the contradiction happen.

**Coverage is not universal and that is expected.** KanjiVG holds 6,702 characters against KANJIDIC2's 13,108, so 6,692 kanji have no stroke data at all. But coverage of the **top 2,501 ranked kanji is 100%**, so the gap falls entirely on rare characters. Assert that number rather than total coverage; a drop below it means the ingest is dropping entries.

Spot-check a low-stroke and a high-stroke character.

---

## Phase 4–5 — Camera and overlay

### V-10 · Vertical text (縦書き) (D-33, `architecture.md`)

Collect a **vertical** Japanese text image as a permanent test fixture before overlay work starts, not after.

Expected: correct reading order (top to bottom, then right column to left column), and tap targets aligned to characters. A horizontal-only coordinate implementation typically still returns *something* on vertical text — usually the wrong character, or the right characters in scrambled order.

### V-11 · Character-level tap resolution (`architecture.md` stage 4)

On a frozen scan of `先生と生産`, tapping the 産 glyph must open 生産, not 先生 and not と.

This is the interpolation math connecting ML Kit's pixel rectangles to tokenizer character offsets. Off-by-one errors here are systematically wrong but rarely obviously wrong — taps land on a neighbouring word, which reads as "the OCR is a bit flaky" rather than as a bug.

### V-12 · Japanese glyph forms (D-34)

Render 直, 骨, 令, 化 and confirm they show **Japanese** forms rather than Chinese ones. These four are known divergent characters under Unicode CJK unification.

Both forms are legible and neither errors. In an app teaching people to write kanji, the wrong form is a correctness bug — and it's device- and locale-dependent, so it may look correct on the development device and wrong on a user's.

### V-26 · Furigana must not be interleaved into the body text (D-61, `architecture.md` stage 4)

Collect a test image of Japanese text that carries **ruby** — small kana printed above kanji (or to their right in vertical text) to show pronunciation. Children's books, manga, station signage and NHK captions all use it heavily.

The trap: OCR does not know that ruby is annotation. It recognizes small kana as ordinary characters and returns them **in reading order interleaved with the base text**. 先生 annotated with せんせい can come back as `せんせい先生`, `先せんせい生`, or similar, depending on how the engine orders the boxes.

Nothing errors. The recognizer succeeds, the tokenizer happily segments the garbled string, and the overlay renders tappable words — they are simply the wrong words. Downstream it reads as "the dictionary doesn't have this", not as a text-extraction bug.

Expected: ruby is **excluded from the token stream**, and taps on the base text resolve to the base word.

| Input | Wrong (silent) | Right |
|---|---|---|
| 先生 with せんせい ruby | tokens for `せんせい先生` | tokens for `先生` |

The signal to separate them is geometric, not linguistic: ruby glyphs are markedly smaller than their base text and sit consistently above it (horizontal) or to its right (vertical). ML Kit supplies per-element bounding boxes, so the height ratio and baseline offset are both available in stage 4 — the same stage that already has to distinguish vertical from horizontal (V-10).

*Why this is worth a case rather than a backlog item:* it is one of the few OCR failures competitors visibly have, and "works on furigana'd text" is demonstrable in a store listing (D-61). It is also cheapest to handle while stage 4 is being designed, for exactly the reason V-10 gives — retrofitting the coordinate layer is the most error-prone work in the project.

---

## Phase 6–7 — Study loop

### V-13 · One schedule per item across multiple lists (D-29)

Setup: save 先生, add it to two lists ("Street Signs" and "Food Menu"), review it once.

Expect **one** `srs_state` row and **one** due date. If the word appears twice in a review session, or has two independent schedules, scheduling has been attached to list membership instead of to the study item — which doubles the user's workload and corrupts FSRS's model of their retention.

### V-14 · Study item type discriminator populated (D-27)

Every v1 row must have `type = WORD` explicitly, never null or defaulted. A nullable discriminator that "works" because v1 only writes one kind is the exact retrofit D-27 exists to prevent.

### V-20 · An orphaned saved item renders and stays reviewable (D-40, D-43)

Setup: save a word, then swap in a dictionary build where that `(text, reading)` no longer resolves.

| Check | Expected |
|---|---|
| Saved list | The card is **present**, not filtered out |
| Card content | Text, reading, `snapshot_gloss`, and an explanation |
| Review queue | The item still appears and is answerable |

The review check is the one that matters and the one most likely to be missed. A card with no back is unanswerable, so the natural implementation quietly excludes it from the queue — and unlike a missing list entry, the user has no screen on which to notice. Their review count simply drops by one.

Test with `changes` **empty** as well as populated. D-40 must hold on its own; D-39 only improves the wording.

---

## Phase 8 — Export/import

### V-15 · Round-trip fidelity (D-20)

Export, wipe app data, import. Expect study items, readings, list membership, SRS state, and review history all preserved.

Specifically confirm 上手 (じょうず) and 上手 (うわて) survive as **separate** items with separate schedules — the export format is the most likely place for identity to silently collapse back to text alone (D-12).

### V-16 · Format version present from the first release (D-20)

The export file must carry a format version field from v1.0. Adding it in v1.1 means the first release's exports are unidentifiable to any future importer, and there is no way to fix that retroactively.
