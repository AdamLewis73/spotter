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

**Met 2026-08-23.** Also check **東京都**, which is the case that shaped the presentation rule (D-70): Kuromoji parses it as 東京 / 都, so both the full place name and 京都 — which straddles the token boundary — are unreachable from the strip. Both must appear as alternates. The failure mode is silence in the purest form: with `existingWords` returning nothing, longest-match still runs, still returns a list, and the list is simply always empty.

Note the **display** filters single-character alternates (D-70), because each is already a component box below and routes to the same kanji screen (D-06, D-49). The mechanism must still report 先 — assert that against `matchesIn`, not against the screen.

### V-07 · Conjugated verbs resolve to dictionary form (D-07)

Input: `生きた`

Expect resolution to the dictionary entry 生きる. Plain longest-match cannot do this; it's the specific reason Kuromoji is in the stack alongside it. Failure looks like "word not found" on perfectly ordinary text.

### V-08 · Reading labels vs. furigana use different scripts (D-14, D-37)

Two conventions that are easy to conflate, on screen at the same time:

- Furigana rendered above 先生 → **せんせい** (hiragana, always)
- On'yomi group header on the 生 kanji screen → **セイ** (katakana)

If both render in the same script, one convention has been applied globally.

### V-21 · Obsolete readings are visibly distinguished (D-53, D-48, D-66)

Scan or paste **上手**. Under D-48 the word screen lists every reading as a section, so all five appear:

| Reading | Tag | Expected treatment |
|---|---|---|
| じょうず | — | normal, badged *common*, **and first** |
| うわて | — | normal |
| かみて | — | normal |
| じょうて | `ok` | **marked archaic**, muted, no *common* badge |
| じょうしゅ | `ok` | **marked archaic**, muted, no *common* badge |

The failure this catches: じょうて rendering identically to じょうず. Nothing errors, the screen looks complete, and the app has quietly taught a reading that has not been current for centuries — to a learner with no way to know the difference.

**It is an ordering failure as much as a labelling one.** じょうしゅ and じょうず both carry 上手's frequency rank of 12 — inherited from the *writing* under V-04's rule — so the query's tiebreak falls to alphabetical kana and the obsolete reading wins. Before this case was met the screen **opened on じょうしゅ**, badged *common*. Marking alone would have left the archaic reading at the top of the screen.

Note the app never knows *which* reading was scanned (D-44, D-53), so this cannot be solved by only showing archaic readings when they were the one photographed. Every reading is always shown; the marking is what carries the information.

Also confirm the reverse: a word with no `ok` readings shows no archaic marking anywhere. A marker applied globally is as wrong as one never applied, and looks just as plausible.

#### The rest of the column (D-66)

`reading_info` carries five `re_inf` codes, and V-21 is not met by handling only `ok`. Three further checks, each catching the same class of silence from a different direction:

| Paste | Expect | The failure |
|---|---|---|
| **中国** | **ちゅうごく alone** | ちゅうこく is a search-only misreading and used to render *first*, badged common. `sk` lands on far commoner words than `ok` does |
| **明日** | あした, あす, みょうにち — **all unmarked** | All three are tagged or flanked by `gikun`, which is not a defect marker. A rule of "tagged means suspect" labels the ordinary reading of an everyday word archaic |
| **あっかんべえ** | resolves, marked *non-standard* | Its only reading is `sk`. An unconditional filter reports a word the dictionary plainly holds as missing — D-40's failure, reached from a different direction. 3,143 written forms are in this position |

**The vocabulary itself is checked by `verify.py`**, not by eye: the app maps four codes and treats an unrecognised one as an ordinary reading, so the case asserts the built dictionary contains exactly `ok`, `ik`, `rk`, `sk` and `gikun`. A sixth code arriving in a source refresh then fails the build rather than silently rendering as normal — which would reintroduce this very bug through the back door.

### V-27 · A sentence appears under one reading only (D-69, D-51)

Paste **明日**. It has three readings — あした, あす, みょうにち — and one example sentence, `あしたは一日中ひまです。`

Expect the sentence under **あした and nowhere else**.

The trap: a sentence attaches to a JMdict *entry*, and V-18 expands one entry into a word per reading, so every reading inherits it. Rendered naively, the app prints `あしたは…` under みょうにち — asserting that a sentence contains a reading it does not. Nothing errors; the screen looks richer than it should. **11,622 entries** carry a sentence shared across more than one reading, so this is not an edge case.

Only 777 of those involve a marked reading, so **filtering by V-21 status does not fix it** — it fixes about 5%. The rule is one sentence per entry, under the best-ranked *current* reading.

Then paste **上手** and confirm the sentence sits under **じょうず**, not under じょうしゅ.

That pair is the specific regression worth re-checking, because it fails in the quietest possible way. じょうしゅ, じょうず and じょうて tie on frequency, so the query's kana tiebreak leads with じょうしゅ. Choosing the primary before applying V-21's status order hands the sentence to the archaic reading, which then correctly suppresses it — and じょうず ends up with no example and no indication that anything was dropped.

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

### V-10 · Vertical text (縦書き) (D-33, D-75, `architecture.md`)

Collect a **vertical** Japanese text image as a permanent test fixture before overlay work starts, not after.

Expected: correct reading order (top to bottom, then right column to left column), and tap targets aligned to characters. A horizontal-only coordinate implementation typically still returns *something* on vertical text — usually the wrong character, or the right characters in scrambled order.

**Measured 2026-08-26, and the answer is that ML Kit gets it wrong (D-75).** Fixtures are generated by `tools/fixtures/make_fixtures.py` and pinned by `VerticalTextOrderTest`; they write the same two lines as `sign-horizontal.png`, one per column, so a correct recognition is the *same string*.

| Fixture | Recognized | Elements |
|---|---|---|
| `sign-horizontal.png` | `先生と生産` / `東京都の学生` | 2 |
| `sign-vertical.png` | `東京者の学生` / `先生と生産` | 2 |
| `sign-vertical-staggered.png` | `東京郡の学生` / `先生と生産` | 4 |

Columns arrive **left-to-right**, which is backwards, and staggering them in y does not change it — so it is an unconditional horizontal-text assumption, not a position sort. Within each column the order is correct, and each column is grouped as one line, so the fix is a sort in stage 2 rather than a reconstruction in stage 4.

Two further findings the case did not anticipate, both of which constrain stage 4:

- **Elements split mid-column.** Two columns came back as four elements on the staggered fixture. Nothing may assume one element per line.
- **Vertical recognition is less accurate.** 都 was misread on both vertical fixtures and differently each time (者, 郡), while reading correctly on the horizontal one. Not a coordinate fault — do not go looking for one.

*Still open, and what this case now protects:* tap targets aligned to characters, which needs stage 4 and real photographs. The fixtures are generated, so they prove ordering and wiring, not accuracy on real signage.

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

**Confirmed on real typesetting 2026-08-26, with three corrections to the above.**

- **The interleaving has no stable order.** This case's table implies ruby lands adjacent to its base. It does not — ruby elements come back scattered before, between and after the lines they annotate, with no consistent relationship. Any rule of the form "ruby precedes its base" is unavailable; separation must be purely geometric.
- **Ruby fragments the column it annotates.** One vertical column returned as four elements, split at each ruby interruption. This is the strongest form of the "never assume one element per line" constraint that V-10 also records.
- **Small text is frequently not ruby**, and this is the trap that makes a size-only rule actively harmful. Shop lanterns and shrine donor plaques set company names, prefectures and job titles markedly smaller than the main name, *inline in the same column*. A size-only test reads them as annotation and drops them from the token stream — deleting real words, silently. **Both signals must agree**: markedly smaller *and* positioned in the ruby slot (above and horizontally overlapping, or right and vertically overlapping).

Measured ruby-to-body height ratio is cleanly separated at capture scale — ruby 32–46 px against body 64–68 — but narrows to 9–16 against 20–21 on a low-resolution source, where it would misfire. The size signal is resolution-dependent; the positional signal is not.

*Why this is worth a case rather than a backlog item:* it is one of the few OCR failures competitors visibly have, and "works on furigana'd text" is demonstrable in a store listing (D-61). It is also cheapest to handle while stage 4 is being designed, for exactly the reason V-10 gives — retrofitting the coordinate layer is the most error-prone work in the project.

### V-28 · A line break must neither invent a word nor hide one (`architecture.md` stage 2 and 4)

**Japanese has no hyphenation.** English breaking `pro-` / `duction` leaves a hyphen saying "rejoin these". Japanese leaves nothing: 禁則処理 (*kinsoku shori*) constrains only *which characters may not begin or end a line* — 、。） cannot start one, （ cannot end one — and beyond that a line may break **between any two characters, mid-word, with no marker at all**.

So flattening ML Kit's lines into the single string the tokenizer wants (stage 2) fails silently in *both* directions, and no choice of separator escapes both:

| Input | Joining lines | Separating lines |
|---|---|---|
| A sign: 先生 above 産業 | invents 生産 — a word nobody wrote | correct |
| A paragraph: 生 ending a line, 産 starting the next | correct — finds 生産 | hides 生産; 生 and 産 resolve alone |

Nothing errors either way. The recognizer succeeds, the tokenizer segments happily, and the words are simply wrong — which reads downstream as "the OCR is a bit flaky", exactly like V-11 and V-26.

**As built in Phase 4: lines were joined with a newline unconditionally**, so the app sat in the right-hand column — hiding words rather than inventing them. **Settled geometrically in Phase 5** (`domain/scan/ScanLayout.kt`); see below.

*That is a deliberate default, not the answer.* **Inventing is worse than missing.** A missed word means the learner taps 生 and gets 生 — degraded, but true. An invented word means they tap and get a confident, plausible, wrong answer, which in an app whose entire claim is teaching meaning in context is the failure that actively does harm (D-44, D-04).

**Expected, once stage 4 exists: the decision is geometric, not fixed.** Whether two lines are one flow or two separate things is answerable from the boxes — block membership, line spacing, alignment of the leading edge — which is the same *kind* of signal V-26 uses to separate ruby from body text and V-10 uses to find columns. All three are one question wearing three hats: *what does this geometry mean?* None of them can be settled from the text alone, and the third cannot even be posed before the first, because "the line above" is undefined until the writing direction is known.

*One consequence stage 4 must expect either way:* wherever a separator sits in the string, that character offset belongs to **no element** and maps to no rectangle. A tap can never land there, but a lookup table assuming every offset has a box is wrong at exactly those positions.

**As decided, 2026-08-26.** The rule is *a line that runs to the block's far edge wrapped; one that stops short ended.* That reads the evidence of how the text was actually set rather than guessing, and it works: run over the real notice's eight columns it breaks the text into exactly its three sentences, purely from where the ink stops, with nothing in the code knowing what a sentence is.

Two guards sit on top of it, both paid for by a failing test:

- **A block of fewer than three lines is never joined.** The wrap test is circular below that: the block's far edge is derived from its own lines, so in a two-line block the longer line *defines* the measure and always appears to reach it — which would join every two-line shop sign. Three lines is where a consistent measure becomes evidence rather than a tautology. The cost is a genuine two-line paragraph losing a word split across its break; accepted, and rare beside two-line signage.
- **Blocks are clustered at 1.5 glyphs of separation**, the top of the typographic range for 行間 and below the range at which independent objects sit apart.

**A known limit, recorded rather than tuned away.** A row of equal-length independent texts — shop lanterns, donor plaques — is *geometrically identical* to a justified paragraph: same starts, same ends, same spacing. Where such objects sit closer than the clustering threshold they merge and join, which is the inventing failure. Separation distance is the only available signal and it is not always sufficient. Observed only on photographs where recognition had already failed badly, so the joined text was unusable regardless; revisit with better fixtures rather than by tightening the constant, which risks splitting real paragraphs.

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


---

## Corrections found after a phase closed

Cases that postdate the phase whose code they touch.

They live here rather than in that phase's section for a reason worth stating,
because the instinct is to file them "where they belong". A closed phase's
section is a record of what was verified when it shipped, and editing it makes
work look like it was done then, or makes the phase look like it is still open.
Neither is true. The fix is scheduled as its own work, and **the case belongs to
whoever does the fix**, not to the phase that introduced the fault.

The same applies to the progress files: a finished phase's `docs/progress/`
entry stays untouched. Where a later decision genuinely supersedes something
recorded in a living reference document — `data-model.md`, `architecture.md` —
that document gets a short pointer to the newer decision, which is the supersede
procedure at the top of `decisions.md`.

### V-29 · Frequency ranking separates a word's own readings (D-04, D-84, V-04)

**Met 2026-08-29.** V-04 checks that ranking is applied *between* words. This checks it is applied *within* one.

**Paste 一人. The screen must lead with ひとり, not いちにん.**

The trap is that both are perfectly ordinary readings. Neither carries an `re_inf` tag, so V-21's status ordering — which is what rescues 上手 from leading with じょうしゅ — has nothing to act on. Only frequency separates them, and the stored frequency said they were equal.

The cause was in the builder. `ingest_jmdict.py` computed a word's priority as `r_pri | writings.get(keb)`, unioning the *writing's* markers into every reading it pairs with. That is right for ranking a word and wrong for ordering readings inside one, because a strongly-marked writing floods every reading and erases the signal JMdict carries:

| Word | Writing `ke_pri` | Reading `re_pri` | Led with | Now |
|---|---|---|---|---|
| **一人** | ichi1 news1 nf02 | ひとり: nf02 nf16 spec1 · いちにん: **none** | いちにん | **ひとり** |
| **その他** | spec1 | そのほか: ichi1 spec1 · そのた: **none** | そのた | **そのほか** |

#### The half that catches every wrong fix

**米, 先, 明日 and 日本 must not move** — こめ, さき, あした, にっぽん. Each carries a reading-level marker on more than one reading, so nothing in the data separates them and kana order is the honest answer. Every alternative D-84 rejects passes the table above and fails this:

| Rejected tiebreak | Also does |
|---|---|
| dictionary row id | 米 → メートル, 先 → さっき |
| `ent_seq` then row id | identical, to the character |
| comparing reading-rank **magnitudes** | 明日 → みょうにち, 日本 → にほん |

The third is the instructive one, and it was caught by **V-27's** test rather than by this case: 明日's みょうにち bands at 5 against あした's 49, because the newspaper corpus is register-biased — announcements say みょうにち, people say あした. Attaching the entry's example sentence to the "best-ranked" reading then handed it to みょうにち. So the query tests `reading_freq_rank IS NULL` and never compares the numbers.

**Why this is a verification case rather than a bug report:** it rendered perfectly. 一人 showed a real reading, correctly spelled, with correct meanings, badged common — and it was the wrong one, for a word in the first hundred a learner meets.
