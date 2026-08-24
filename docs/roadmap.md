# Roadmap

Read `overview.md` first if you're new to this project.

## Sequencing principle

**Build inside-out, not outside-in.** The instinct is to start with the camera because it's the exciting part. Don't — it's the hardest component to debug, and it sits on top of everything else, so a bug anywhere in a camera-first build looks like a camera bug.

By the end of Phase 3, roughly 70% of the app exists and is fully testable without ever pointing a phone at anything.

**Build order is not importance order.** D-61 makes the scanner the product, which means **Phases 4 and 5 are what v1 lives or dies on** — they are built last for debuggability, not because they matter least. Two consequences worth holding onto through the earlier phases:

- Phases 2 and 3 are *infrastructure for the scan*, not the deliverable. Resist polishing them past the point where they serve the tap.
- The schedule risk sits at the end. The hardest and most important work is the last work, so slippage in Phases 2–3 eats directly into the only part users will judge.

## Phases

| # | Phase | Status | Output |
|---|---|---|---|
| 1 | Dictionary builder (desktop Python) | **Complete** | `spotter.db` — 99.7 MB, 30.3 MB gzipped |
| 2 | Android app, text input only | **Feature-complete** — every `V-##` case this phase owns is met; the user-data checkpoint lands with Save in Phase 6 | Paste 先生 → word + kanji screens |
| 3 | Stroke order tab | **Complete** | KanjiVG animation, design artboard 3b |
| 4 | CameraX + ML Kit | Not started | Raw recognized text into the Phase 2 pipeline |
| 5 | Tappable overlay | Not started | The real scan experience |
| 6 | Saved lists | Not started | Multiple lists, many-to-many |
| 7 | SRS review | Not started | FSRS scheduling and quizzes |
| 8 | Export / import | Not started | Versioned JSON/zip |

### Phase 1 — Dictionary builder

A desktop Python script that parses JMdict, KANJIDIC2, KanjiVG, JmdictFurigana, and an example-sentence corpus into a single SQLite file shipped as an Android asset (D-10). Contains no Android code and requires no emulator, so it de-risks the entire data layer before any Android work begins.

Lives at `tools/dictbuild/` in this repository, fetched from a pinned manifest (D-41).

Schema draft is in `data-model.md`. **First task is inspecting the real source files** — URLs, formats and licensing are now confirmed, but the internal element shapes are not, and the draft schema was written from documentation rather than from the data.

Three things are known to be harder than they look:

- **Reading normalization (D-37, V-17).** JmdictFurigana supplies hiragana; on'yomi must be katakana. Worse, surface readings drift from dictionary readings through rendaku and gemination (学校 = がっこう, not がくこう), so the match is fuzzy rather than exact. This is the hardest correctness problem in the phase.
- **Entry expansion (V-18).** A JMdict entry is not a word; `re_restr` and `stagk`/`stagr` must be honoured or the ingest invents words and misattributes meanings.
- **Frequency derivation (V-04).** Priority tags live on writing and reading elements separately, so `word_frequency` needs a stated rule.

**Settled:** the example-sentence source is `JMdict_e_examp`, which replaces plain `JMdict_e` rather than adding to it (D-51). Sentences are rendered as of Phase 2 (D-69) — see the note below.

### Phase 2 — Android app with a text box

**No camera at all.** Paste `先生と生産` into a text field, tokenize with Kuromoji, look up in Room, and render the word screen and kanji screen. This is where the app's actual value gets proven, and it's fully testable without any of the camera complexity.

**First job: get the dictionary in. — Done.** `spotter.db` is a build output and gitignored, so a fresh clone doesn't have one; `:app:stageDictionaryAsset` builds it into the APK and fails loudly if it is missing or older than the code that generates it. CI rebuilds it from the committed sources every push and asserts it reached the APK, which is what actually prevents a stale asset serving old data.

~~**Decide here: do example sentences get rendered? (D-51)**~~ — **settled 2026-08-23: they ship (D-69).**

Built behind a switch and looked at, as planned. The coverage worry turned out to be the wrong worry: 先生 shows sentences on two of four senses and reads like a dictionary, not like something broken. 生産 is the case that settled it.

The fault that *did* matter was invisible on paper. A sentence attaches to a JMdict entry, and V-18 expands one entry into a word per reading, so all of them inherited it — 明日's あした sentence also appeared under みょうにち. 11,622 entries affected. Sentences now show under the entry's best-ranked **current** reading only (V-27).

Word-level examples where no sense-attached one exists stay deferred; nothing in the data made the case for them.

**Housekeeping — `/launch` and `/inspect` exist** (`.claude/skills/launch/SKILL.md`, `.claude/skills/inspect/SKILL.md`), added 2026-08-19 alongside `orient` and `phase`, split in two on 2026-08-20. `/launch` builds, installs and starts the app, then leaves it up for the owner to drive by hand; `/inspect` does the same but drives it against specific words, screenshots and reports. Between them they carry the device knowledge this phase cost: the emulator must be woken before a screenshot or it captures black, the first launch after install needs ~15 seconds while Room extracts 100 MB, and `connectedAndroidTest` uninstalls the app when it finishes — so anything testing an upgrade must drive `adb install -r` plus `am instrument` or it silently tests a fresh install.

### Phase 3 — Stroke order

Self-contained, visually rewarding, and good Compose practice. Renders KanjiVG's per-stroke SVG paths sequentially.

**Done.** Built to design artboard **3b**: a stage with a centre crosshair, the character drawn stroke by stroke, play/pause, a speed control, and an **Every stroke** grid of cumulative frames that scrubs the animation when tapped. V-09's display rule is met — the counter reports the number of paths being animated, never KANJIDIC2's figure.

The artboard's **Trace** button switches the stage into a writable trace mode (D-72): the ghost stays up, the expected stroke is lifted out of it, and the learner draws over it. No scoring and no scheduler, which is what keeps practice independent of review — artboard 2c's blind write-then-grade remains a separate Phase 7 screen.

### Phase 4 — Camera

CameraX plus ML Kit's Japanese model, feeding recognized text into a pipeline that already works and is already trusted.

**This is where the product starts existing** (D-61). The app opens on the camera — no home screen, no dashboard, no shortcut grid. That is the whole positioning against the incumbents, and it is a Phase 4 decision because it shapes navigation, not a coat of paint applied later.

### Phase 5 — Overlay

The coordinate-mapping work described in `architecture.md` — connecting ML Kit's pixel rectangles to the tokenizers' character offsets so a tap resolves to a word. Highest risk of subtle bugs in the project, **and the core of v1**.

Stage 4 does three geometric jobs at once, and they must be designed together rather than retrofitted onto each other:

1. **Vertical text (縦書き)** — interpolate on y, columns right-to-left (V-10). In test images from day one.
2. **Character interpolation** — the tap-to-word resolution itself (V-11).
3. **Furigana separation** — ruby is smaller and offset, and must be kept out of the token stream (V-26). This is one of the few OCR failures competitors visibly have, which makes it demonstrable in a store listing rather than merely correct.

### Phases 6–8 — The study loop

Saved lists, then FSRS review, then export/import.

**Open for Phase 7:** what goes on the **back of a review card** for a word with several senses. 甘い is "sweet; sugary; mild; naive; lenient" — all of it, or the primary sense only, or something the user chooses? This is a flashcard design question, not a data or scanning one (D-44), and it is the only part of the sense-disambiguation discussion that remains unresolved.

**A shippable v1 is Phases 1–5.** Phases 6–8 turn it from a lookup tool into a study app. The staging matters: the full spec is a large build, and stalling at 60% is the common failure mode for solo projects of this size.

---

## Decision checkpoints

Stop and decide before proceeding past each of these. Every one is cheap now and expensive or impossible to retrofit.

The project owner has asked to be consulted at these points rather than having a default chosen silently.

| Before | Decision | Why it's hard to undo |
|---|---|---|
| Phase 1 | Dictionary schema and natural-key strategy | Regenerating the dictionary is trivial; migrating user data that references it is not (D-11) |
| Phase 1 | Which datasets to ingest — JmdictFurigana is in (D-13) | Adding one later means a full rebuild plus a schema change |
| Phase 2, first commit | Module structure; `:domain` and `:data` free of `android.*` | This is the iOS-portability line — retrofitting is a rewrite |
| ~~Phase 2, first UI commit~~ | ~~Material 3 plus a design-token layer (D-35)~~ — **done 2026-08-11**: fixed palette, light and dark, plus bundled Noto Sans JP (D-34) | Touches every composable if done later |
| Phase 2, first user-data write | UUID keys, `updated_at`, soft delete, schema export on, destructive migration off (D-15 – D-18) | Getting this wrong deletes user data in production |
| Phase 2, first user-data write | `snapshot_gloss` on `study_item` (D-43) | Adding it later is a migration, **and** every word saved before it has a permanently empty snapshot — the gloss cannot be recovered for a word the dictionary has since dropped |
| Phase 5 | Bounding box stored in the scan record (D-22) | Cheap now; later requires re-running OCR over every saved image |
| Phase 6 | Study-item identity `(text, reading)` plus the `type` discriminator (D-12, D-27) | All review history is keyed to it |
| Phase 6 | **Built-in SRS, or export to Anki?** (D-26, D-29) | The schema Phase 6 builds assumes the answer. Serious learners already live in Anki; beginners don't have it. See the open question in `progress/phase-07-srs-review.md` |

---

## Deferred

Pinned deliberately, each with the reason and the cost of adding it later. **None of these are rejected** — several are wanted, just not in v1.

| Item | What it is | Why deferred | Cost to add later |
|---|---|---|---|
| **Sudachi** | An alternative tokenizer with native multi-granularity splitting — it can return both 選挙管理委員会 and its parts 選挙 / 管理 / 委員会 from one pass | Requires extracting a memory-mapped dictionary from assets on first launch; ~3× larger dictionary; poorly documented on Android. May prove unnecessary since JMdict longest-match already surfaces overlapping candidates (D-07) | **Low** — the `Tokenizer` interface isolates it (D-08) |
| **Live camera overlay** | Google Translate-style continuous recognition instead of freeze-frame | Doing it well means background freeze-frames with change detection — genuinely complex. D-02's reasoning holds for v1 | **Medium** — new capture path, but everything downstream is reused |
| **Object recognition** | Point the camera at an object, get its Japanese name | ML Kit image labelling returns generic English labels ("Food", "Building") that then need translating into Japanese with no context. Weak payoff next to the text path, and effectively a separate app mode | **Medium** |
| **Curated kanji explanations** | Authored prose explaining why a kanji means what it does in a compound | Writing these for 2,000+ kanji is a content project, not a code project. D-04 delivers most of the value automatically and free | **Low** — purely additive content layer |
| **Word crops instead of full frames** | Save just the tapped word's region rather than the whole photo | Crop geometry is fiddly and shouldn't block v1 (D-21) | **Near zero** — D-22 stores the bounding box now, so no image reprocessing is ever needed |
| **KRADFILE radical components** | Show which visual pieces a kanji is built from | The component *data* is free, but standard English *names* for components are not, and inventing them risks looking derivative of WaniKani, whose names are their own authored content. ~214 names is a bounded task if ever wanted | **Low** — additive |
| **Word-level stroke order** | Play 先 then 生 in sequence on the word screen | Redundant and visually busy for long words; D-05 places stroke order on the kanji screen | **Low** — composes existing per-kanji data |
| **Accounts + server sync** | User profiles with cross-device sync | Authentication brings privacy obligations, a Play Store data-safety declaration, and hosting cost. None of it helps v1 | **Low — but only because** D-15, D-16, and D-19 are being followed from the start |
| **Ads** | ~~Post-quiz placement, Duolingo-style~~ | **Ruled out, not deferred (D-62).** Free with no ads is now a positioning commitment, and intrusive ads are among the loudest complaints against the free competitors | n/a |
| **Kanji-only study items** | Let users add individual kanji to their SRS, not just words | v1 studies words (D-01) | **Near zero** — D-27 puts the `type` discriminator in the schema from day one |
| **Smart / auto lists** | Lists generated by rule — by JLPT level, shared kanji, or scan date | Nice-to-have | **Low** — falls out of D-28's join table |
| **On-device sentence translation** | ML Kit Translation showing an English rendering of the scanned line, alongside — never replacing — the word breakdown | The honest way to answer "what does this sign say", but it is a translation surface on a learning app's main screen, and the thing shown first is the thing people use (D-45) | **Low** — a new panel calling one API; no data-model change. Note the ~30 MB model **cannot** be bundled (D-46) |
| **Interlinear gloss strip** | The recognized line with each word's primary meaning beneath it | Considered for v1 and cut. Particles gloss badly (*[subject]*, *[adj]*) and confuse beginners; it duplicates the peek card with less information; and it short-circuits the tap, which is where the learning happens (D-45) | **Near zero** — `scan.raw_ocr_text` already stores the line |
| **JLPT level** | An estimated N5–N1 level per kanji | No official list exists post-2010; only community reconstructions of unstated licensing (D-42) | **Near zero** — a dictionary column, no migration. Settle the encoding first |
