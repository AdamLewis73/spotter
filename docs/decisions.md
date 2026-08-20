# Decisions

Every significant design decision for this project, with the reasoning behind it.

## How to use this file

**IDs are stable and permanent. Decisions are not.**

`D-01` will always refer to the same decision — the number is never reused or renumbered, so it stays a reliable reference in commit messages, code comments, and conversation ("implemented per D-12").

Decisions themselves are expected to change as the project learns things. When one does:

1. Edit the old entry to begin with `**SUPERSEDED by D-##**`, and **leave the rest of the text intact**.
2. Append the replacement with the next unused ID.
3. **Grep `verification.md` for the old decision's ID.** Verification cases cite the decisions they protect, so a grep for `D-12` finds every case that assumes it. Update or retire those cases in the same change.
4. Grep the rest of `docs/` for the ID too — decisions are referenced from `architecture.md`, `data-model.md`, `ux.md`, `roadmap.md`, and the progress files.

Never delete a decision. The reasoning behind a path that was later abandoned is often the most valuable thing here — it stops a future session from re-proposing something already ruled out, and it records *why* the situation changed.

Note that references are deliberately **one-directional**: other docs cite `D-##`, but decisions don't link back. Back-links would need maintaining in two places and would drift. Grep is the mechanism, and it stays correct for free.

**A decision recorded here reflects the current plan, which may be v1-only.** Several decisions deliberately scope to v1 while a later phase expands them. Where that's the case the entry says so and links the related decision. See `roadmap.md` for the deferred backlog.

**New to this project?** Read `overview.md` first — it has a worked end-to-end example and a glossary of the Japanese-language terms used throughout these docs.

## Index

Scan for the relevant entry rather than reading the whole file.

| ID | Decision | Area |
|---|---|---|
| D-01 | v1 study items are words; kanji-only items come later | Product |
| D-02 | Freeze-frame, not live overlay | Product |
| D-03 | ~~Fully offline; no runtime LLM calls~~ — SUPERSEDED by D-46 | Product |
| D-04 | Teach by example (words grouped by reading), not authored prose | Product |
| D-05 | Two detail screens: word → kanji drill-down | Product |
| D-06 | Component chips show meanings only, never readings | Product |
| D-07 | Kuromoji for segmentation + JMdict longest-match for alternates | Tokenization |
| D-08 | Tokenization behind a `Tokenizer` interface in `:domain` | Tokenization |
| D-09 | Two databases: read-only dictionary, writable user data | Data |
| D-10 | Dictionary built by a desktop Python script, not on-device | Data |
| D-11 | **Never store dictionary row IDs in user data** | Data |
| D-12 | **Identity is (text, reading), never text alone** | Data |
| D-13 | JmdictFurigana ingested as an index, never rendered | Data |
| D-14 | Furigana display is whole-word ruby only | Data |
| D-15 | **UUID primary keys on all user data** | Migrations |
| D-16 | **`updated_at` on every row; soft deletes only** | Migrations |
| D-17 | **`fallbackToDestructiveMigration()` banned in all build types** | Migrations |
| D-18 | Room schema export on; JSON committed to git | Migrations |
| D-19 | Design for sync now; build accounts later | Migrations |
| D-20 | Manual export/import ships before any sync | Migrations |
| D-21 | v1 saves downscaled full frames; word crops deferred | Images |
| D-22 | Store the word's bounding box from v1 regardless | Images |
| D-23 | Image format migrations never reprocess old records | Images |
| D-24 | Images are files on disk; DB stores relative paths | Images |
| D-25 | Scan history and saved-word images have separate lifecycles | Images |
| D-26 | FSRS, not SM-2 or a custom algorithm | SRS |
| D-27 | Study items polymorphic (`type`) from day one | SRS |
| D-28 | Saved lists are many-to-many via a join table | SRS |
| D-29 | **Scheduling belongs to the item; lists only filter** | SRS |
| D-30 | Peek sheet and word screen are one expanding component | UI |
| D-31 | Peek state only on the scan screen | UI |
| D-32 | Kanji screen swaps in place inside the sheet | UI |
| D-33 | Overlay dims the image; detected text stays bright | UI |
| D-34 | Bundle Noto Sans JP explicitly | UI |
| D-35 | Material 3 design tokens from the first UI commit | UI |
| D-36 | Three bottom-nav destinations: Scan · Saved · Review | UI |
| D-37 | Reading labels follow dictionary kana convention | UI / Data |
| D-38 | Dictionary stays disposable; stable dictionary IDs rejected | Data |
| D-39 | Dictionary ships a `changes` table for merged and removed entries | Data |
| D-40 | An unresolvable saved item always renders; it never vanishes | UI |
| D-41 | Sources fetched from a pinned manifest; refreshed at defined events | Data |
| D-42 | No JLPT data in v1; a labelled estimate may follow | Data |
| D-43 | `snapshot_gloss` on `study_item`, read only when lookup fails | Data |
| D-44 | "Context" means kanji-in-word, not word-in-sentence | Product |
| D-45 | Sentence-level comprehension is post-v1 | Product |
| D-46 | No *persistent* network; one-time downloads permitted (supersedes D-03) | Product |
| D-47 | Peek sheet shows the word and its meanings — never a reading | UI |
| D-48 | One word screen per written form; readings are sections within it | UI |
| D-49 | A single-character token opens the kanji screen directly | UI |
| D-50 | Kanji screen carries only learner-usable reference; grade and radical dropped | UI |
| D-51 | Example sentences are ingested in v1 but not rendered; decide in Phase 2 | Data |
| D-52 | Reading alignment normalizes sound changes; unmatched spans kept as NULL | Data |
| D-53 | Obsolete readings are ingested and displayed, marked as archaic | UI / Data |
| D-54 | Two sense filters with opposite defaults; obscurity is ranking, not a setting | UI |
| D-55 | The compressed source files are committed to git | Data |
| D-56 | Dictionary storage layout: `WITHOUT ROWID` for narrow rows only | Data |
| D-57 | Indexes are demand-driven, and column order is load-bearing | Data |
| D-58 | The build must be byte-reproducible from identical sources | Data |
| D-59 | CI review is manual-only; committed datasets are undiffable | Process |
| D-60 | `:domain` is a plain Kotlin/JVM module, so the layering rule is a compile error | Architecture |
| D-61 | The scanner is the product; simplicity outranks features | Product |
| D-62 | Free, no ads, and no paywall on scanning | Product |
| D-63 | The app is **Spotter**; "Kanji Scanner" is the store subtitle, not the name | Product |
| D-64 | Wall-clock time lives outside the dictionary, in a `build-info.json` sidecar | Data |
| D-65 | `build_id` identifies the artefact: sources **and** builder, normalised | Data |
| D-66 | Search-only readings are hidden, but never to nothing; `gikun` is not a defect | UI / Data |

**Bold** entries are the ones whose violation causes silent data corruption or a forced rewrite. They are also listed in `CLAUDE.md`.

---

## Product

**D-01 — v1 study items are words; kanji-only items come later.**
An SRS card is a word (先生), tagged with its component kanji. Reviewing bare kanji in isolation would contradict the app's core claim that meaning is contextual — a flashcard reading "生 = life, birth" teaches exactly the thing the app exists to argue against.

This is a **v1 scope decision, not a permanent restriction.** Studying individual kanji is a wanted future feature; the user should be able to opt into it. **D-27** requires the data model to support kanji items from day one so that adding them later is a feature, not a migration. In other words: v1 *ships* words only, but v1 *stores data* as though both exist.

**D-02 — Freeze-frame, not live overlay.**
The live camera preview shows only a lightweight "Japanese text detected" indicator. The user presses the shutter, and every subsequent interaction — text highlighting, tapping, detail sheets — happens on the frozen still image rather than a moving feed.
*Why:* OCR bounding boxes recompute every frame on a live feed, so highlight rectangles jitter and tap targets move under the user's finger. A person also cannot hold a phone steady while reading a detail panel. A still image additionally permits a slower, more accurate OCR pass and pinch-to-zoom (which matters a great deal — see the tap-target problem in `ux.md`).
Google Translate does use a live overlay, but its use case is different: glance-and-replace, not sustained study of one image. A Translate-style approach (background freeze-frames with change detection) is deferred, not rejected — see `roadmap.md`.

**D-03 — Fully offline. No runtime LLM calls.**
**SUPERSEDED by D-46.** The no-runtime-LLM half survives unchanged; the "fully offline" half was too strict and is restated in D-46.

Every core function works with no network connection. This rules out generating kanji explanations on demand from a language model. Two reasons: per-call cost the project doesn't want to carry, and the risk of confidently incorrect etymology being presented as fact in an app whose entire purpose is teaching.

**D-04 — v1 teaches by example, not by authored prose.**
The obvious way to explain why 生 means "teacher" inside 先生 is a written explanation. But no free dataset contains such explanations, authoring them for 2,000+ kanji is a content project rather than a code project, and generating them is ruled out by D-46.

Instead, the kanji screen shows **other common words containing that kanji, grouped by reading, sorted by frequency.** For 生:

> **セイ** — 先生 teacher · 学生 student · 生活 daily life
> **ショウ** — 一生 a lifetime · 誕生日 birthday
> **なま** — 生ビール draft beer
> **い(きる)** — 生きる to live

This teaches through pattern recognition rather than assertion, which is arguably *more* aligned with the product thesis than a paragraph of etymology would be. It generates automatically from JMdict (words and frequency) plus JmdictFurigana (which reading each kanji carries in each word — see D-13). Curated prose remains a possible later enhancement layered on top; see `roadmap.md`.

**D-05 — Two detail screen types, with drill-down.**
Tapping a word opens the **word screen**; tapping a component kanji on that screen opens the **kanji screen**, which has three tabs (Overview / Examples / Stroke Order).

*Why the split:* radicals, stroke order, and on'yomi/kun'yomi readings are all properties of a *single character*, not of a word. 先生 has no radical — 先 has one and 生 has one. An "on'yomi of 先生" is not a meaningful concept. Trying to present per-character data on a word screen produces either nonsense or awkward compromises.

*(Radicals are no longer displayed at all — D-50 — but the argument stands on stroke order and on/kun readings, which remain per-character. D-49 later carved out the one case where the split was redundant: a token that is a single character.)*

Splitting them also mirrors the actual learning motion: *"what does this say?"* → *"why does it say that?"*

**D-06 — Component kanji chips show meanings only, never readings.**
On the word screen, 先生 displays chips for 先 (previous, ahead) and 生 (life, birth) — meanings only.

*Why:* showing a reading on each chip implies the word's reading splits cleanly per character. Often it does (先生 = せん + せい), but frequently it does not. 明日 is read あした as a whole word, with no part of that reading belonging to 明 or to 日 — this is called *jukujikun*. Displaying per-character readings would teach a false rule. Whole-word furigana (D-14) is the correct presentation.

Note this is a *display* decision only. Per-character reading data is still ingested and used internally — see D-13.

**D-44 — "Context" in this project means kanji-in-word, not word-in-sentence.**

The thesis in `overview.md` — 生 is "life" alone, "teacher" in 先生, "production" in 生産 — locates meaning in **the word**. The app delivers that completely: the tokenizer finds the word boundary, the dictionary supplies the word's meaning, and D-04 shows every other word using that kanji grouped by reading.

It does **not** attempt *word sense disambiguation* — choosing which of a word's several meanings applies in a particular photograph. 甘い on a candy wrapper means "sweet"; in 採点が甘い it means "lenient". Nothing in the pipeline can tell those apart, and nothing in the pipeline needs to.

*Why this is written down:* "contextual meaning" appears throughout these docs and reads, to a fresh reader, as the larger claim. It is not. Two things make the smaller claim sufficient:

- **Reading disambiguation is already solved.** Kuromoji builds a lattice over possible segmentations and picks a path using learned connection costs, and its tokens carry readings (D-07). Choosing うわて over じょうず is contextual, offline, and needs no language model. Ambiguity chips (`ux.md`) surface the candidates when it isn't confident, rather than guessing.
- **Showing every sense is the stated goal, not a fallback.** The **usage completeness** principle in `overview.md` asks that a learner be able to answer *"do I know how to use this in all the ways it's used?"* Listing all senses serves that directly.

What remains open is narrow and belongs to Phase 7: what goes on the **back of a review card** for a multi-sense word. That is a flashcard design question, not a data or scanning question, and it must not be mistaken for one.

**D-45 — Sentence-level comprehension is post-v1.**

Two mechanisms were considered for helping a user understand a whole sign rather than one word, and both are deferred:

- **An interlinear gloss strip** — the recognized line with each word's primary meaning beneath it. Rejected for v1: particles gloss badly (*[subject]*, *[adj]*) and confuse beginners more than they help; it duplicates the peek card with strictly less information; and it short-circuits the tap interaction, which is where the learning actually happens.
- **On-device translation** — ML Kit's Translation API, ~30 MB per language model. Genuinely useful, and the honest way to answer "what does this sign say."

*Why deferred rather than rejected:* both are **purely additive** — a new surface calling existing data or one API, with no change to the data model. Deferring costs almost nothing, which is exactly the profile of a feature that should wait.

*Why this decision exists at all:* the pull toward becoming a translation app is constant and it comes from good intentions. `overview.md` rejects translation as the *product shape*, but "not a translation app" and "no sentence-level help whatsoever" are different positions, and only the first was previously written down. This records the second.

Note that `scan.raw_ocr_text` already captures the full recognized line (D-22), so nothing is lost by not rendering it yet.

**D-46 — No *persistent* network dependency. One-time downloads are permitted. No runtime LLM calls.**

*Supersedes D-03*, whose "fully offline" phrasing was stricter than intended.

The requirement is that **after setup, the app works indefinitely with no network.** No per-lookup web calls, no fetching card details on demand, no degraded experience on a train with no signal.

One-time downloads are acceptable, subject to two constraints:

1. **Where a bundled option exists, prefer it.** Eliminating the download is better than handling it well.
2. **No egregious sizes**, and any download is visible and cancellable rather than silent.

The no-runtime-LLM half of D-03 is unchanged and still holds, for the reasons given there.

*Consequences already known:*

- **ML Kit OCR — bundled remains the choice**, but now as a product preference rather than as a consequence of an offline rule. `architecture.md` previously derived it from D-03; a future session must not read the superseded D-03 and conclude the opposite.
- **ML Kit Translation cannot be bundled.** Its API is built around runtime model download; there is no bundled variant. If D-45's translation feature is ever built, the download is structural, not negotiable.
- **Future account sync (D-19)** obviously requires network, and was never in tension with this — sync is not a core function.

**D-61 — The scanner is the product. Where a feature and a clean first screen conflict, the feature loses.**

This decision followed a competitive review in August 2026 that the project should have run before Phase 1. The finding: the planned feature set already exists. Yomiwa (~840k downloads, on Google Play since 2015) does camera OCR, word segmentation, word and kanji detail, saved images with bounding boxes, stroke order and readings. On iOS, "Kanji Lens — Japanese Scanner" ships close to this project's entire roadmap including SRS. Several smaller Android apps cover parts of it.

**D-04's Examples tab — words grouped by reading — was the project's stated differentiator, and it does not survive contact with this.** Nothing else offers it, but nobody installs an app for a screen they cannot see in a store listing. D-04 is **not superseded**: it remains the right design and the best teaching in the app. It is demoted from *the reason the app exists* to *a good feature*.

What replaces it is a positioning claim rather than a feature claim: **the incumbents are cluttered, and their scanners are buried.** Yomiwa's own reviews describe a cluttered home screen with immovable shortcut buttons, freezing, and — most usefully — a camera that is paywalled without clear disclosure. The opening is not a missing capability. It is the same capabilities, reachable in one tap, by an app that opens on the camera.

Concretely, this decision means:

- The app **opens on the camera**. Not a home screen, not a dashboard, not a shortcut grid.
- Every screen has **one obvious next action**.
- A new feature must justify itself against the first screen staying clean. The default answer is to put it behind the tap, or not to build it.
- **Simplicity outranks every other product principle in `overview.md`.** It is the hardest to hold, because every other principle argues for adding something.

*Cost, accepted:* features this project has already specified will have to be hidden, deferred, or cut on these grounds, including ones that are individually good. That is the decision, not a side effect of it.

*Risk being taken knowingly:* discovery, not capability, is the binding constraint on a new entrant here. Being cleaner than Yomiwa does not by itself put the app in front of anyone. Nothing in the build plan solves that, and it should not be rediscovered as a surprise later.

*Watch item:* [Dokuen](https://play.google.com/store/apps/details?id=io.github.dokuendev.dokuenreader) launched September 2025 on the same instinct — camera on real-world text, tap a word, one-tap Anki export — and is already ranked in the top 100 education apps with no ratings yet. It is a closer competitor to this project than Yomiwa is.

**D-62 — Free. No ads, and no paywall on scanning.**

Not "free tier with the camera behind a subscription", which is the incumbent's model and the thing its users complain about most specifically. **Scanning is never metered, gated, timed, or interrupted.**

*Why this is credible rather than merely generous:* the offline-first architecture (D-46) means there is no marginal cost per user. No server, no OCR API bill, no per-scan charge to recover. A competitor built on cloud OCR *must* meter the camera; this project does not have to, and that asymmetry is structural rather than a matter of willpower.

*Why it matters commercially:* the beloved apps in this category are the free, ad-free ones — Takoboto (~1.7M downloads, 4.79) and Akebi (4.70) — while the ad-supported camera apps draw complaints about intrusive ads on top of broken recognition. Android Japanese learners reward this and punish the alternative.

*Scope:* this constrains **scanning and core lookup only**. It does not commit the project to never charging for anything ever — a paid tier over some *later* convenience feature is not ruled out. It does rule out the camera, the dictionary, and the study loop being that feature.

*Consequence for the build:* no billing integration, no paywall plumbing, no ad SDK, and no Play Store financial-data declarations for v1. That is a real simplification, not just a stance.

**D-63 — The app is called Spotter. The Play Store title is `Spotter: Kanji Scanner`.**

The project was called *KanjiLens* until August 2026. It was renamed because the name collided with two live products in the same category — a Chrome extension at kanjilens.com and an iOS app, "Kanji Lens — Japanese Scanner", which ships most of this project's roadmap.

**The brand word is deliberately not descriptive, and the description is deliberately not the brand.** Every app in this category uses a two-part store title — `Takoboto: Japanese Dictionary`, `Yomiwa - Japanese Dictionary`, `Dokuen Japanese Reader` — because Play indexes the whole title field. So the keyword lives in the subtitle, where it earns search traffic, and the brand stays a word that can be owned, defended and said out loud.

`Spotter: Kanji Scanner` is 22 characters, within Play's 30-character title limit.

*Why "Spotter":* a spotter identifies things in the wild — bird spotting, plane spotting. It names what the user does, extends naturally to the saved-word-with-photo feature (D-21), and is an ordinary English word, which matters because **the audience by definition cannot read Japanese**. That last point ruled out the otherwise-strong Japanese candidates (Yomitori, Kotoba, Jukugo) even though the category's own leaders — Anki, WaniKani, Bunpro — all use Japanese words.

*Names rejected, and why, so they don't get re-proposed:*

| Candidate | Why not |
|---|---|
| KanjiLens | The original. Collides with two live products (above) |
| Kanji Scanner | A generic descriptor: unownable, untrademarkable, unrecommendable out loud. Also the ninth `Kanji ___` app |
| NihonGo | The most crowded name in the category — six-plus apps on Play, plus **Nihongo — Japanese Dictionary**, an established product with OCR and SRS whose pitch is nearly identical to this one. Japanese, too |
| KanjiSpot | One letter's difference from **KanjiSnap**, a live iOS Japanese-OCR app. Near-identical mark, same category |

*The pattern behind those rejections:* the obvious names are taken **because** they are obvious. Every entrant reaches for the same three stems — *kanji*, *nihongo*, *lens/camera* — so that namespace is a pile-up and the unclaimed space is exactly the non-obvious names. A future session proposing another `Kanji ___` should read this row first.

*Naming the wrong unit, separately:* `Kanji ___` names the character, but this app's unit of study is the **word** (D-01), and single characters are routed differently on purpose (D-49). A kanji-prefixed name promises the character-by-character behaviour D-49 rejects.

*Done as part of this decision:* the build artifact was renamed `kanjilens.db` → **`spotter.db`** across `build.py`, `verify.py`, `README.md`, `ci.yml` and the docs, and the `fetch.py` user-agent token likewise. `.gitignore` needed no change — it matches `*.db`.

*Application ID:* **`com.spotterkanji.app`**. Deliberately not `com.spotter.*` — "Spotter" is a common app name in aviation and fitness, so a bare `spotter` namespace risks collision. It carries no personal name, and `spotterkanji.com` is unregistered, so the reverse-DNS form claims nothing anyone else holds. Checked against app stores and the web in August 2026 with no conflict found; note that Play package IDs are not publicly searchable in bulk, so **the authoritative check is the first Play Console upload**, which rejects a taken ID. The ID is freely changeable until then and permanent after.

*Repository:* renamed `kanji_lens` → `spotter` (August 2026). The local directory may still be `kanji_lens`; git does not care.

---

## Tokenization

> **Background for fresh sessions:** Japanese text has no spaces. `先生と生産について話した` is one unbroken run of characters. Before anything can be looked up in a dictionary, the text must be split into words — this is called **tokenization** or morphological analysis, and it is the technically central problem of this app. OCR is the comparatively easy part.

**D-07 — Kuromoji for primary segmentation, plus JMdict longest-match for alternates.**

Two complementary mechanisms, both required:

**Kuromoji** is a Japanese morphological analyzer (pure Java, Apache-2.0, ships its own IPADIC dictionary inside the JAR). Given a string it returns a single best-guess sequence of tokens with part-of-speech tags and readings. It correctly handles conjugated verbs (食べた → base form 食べる) and grammatical particles, which raw dictionary lookup cannot.

**JMdict longest-match** is a second pass over the same text using the dictionary the app already ships. For each starting character position, it queries the dictionary for every entry that matches from that position forward. At position 0 of `先生と生産`, it finds both 先 and 先生; the longest is the primary candidate, and the shorter ones are kept as alternates. This is the technique used by Japanese reader tools such as Yomichan, 10ten, and Rikaichan.

*Why both:* Kuromoji produces exactly one parse. But the entire pedagogical point of this app is that a run of characters contains overlapping words — that 先生 contains 先, that the user should be able to ask about either. Longest-match surfaces every candidate; Kuromoji supplies the grammatical correctness that pure lookup lacks. Together they also implement the compound-vs-word view without needing a multi-granularity tokenizer.

*Why not Sudachi* (the more modern alternative, which offers native multi-granularity splitting): it memory-maps its dictionary file, requiring an uncompressed file on disk — which on Android means extracting from assets on first launch, with progress UI, failure handling, and version migration. Its dictionary is roughly 3× larger, and Android usage is poorly documented, meaning early-adopter debugging. Deferred, not rejected — see `roadmap.md`.

**D-08 — Tokenization sits behind a `Tokenizer` interface in the domain layer.**
Keeps D-07 reversible, and keeps the JVM-only Kuromoji dependency out of portable code. Kuromoji cannot run on iOS; see the portability table in `architecture.md`.

---

## Data

**D-09 — Two separate databases: a read-only dictionary and a writable user database.**

The **dictionary DB** ships as a prebuilt file in the app's assets. It contains JMdict, KANJIDIC2, KanjiVG, and the rest. It is never written to at runtime.

The **user DB** contains saved words, lists, SRS state, and scan records. It is small and precious.

*Why separate:* the dictionary is disposable. When a new JMdict release comes out, the app replaces the whole file — no migration, no risk. User data is irreplaceable and evolves slowly under carefully written migrations. If they shared one database, every dictionary refresh would put the user's study history inside the blast radius of a schema change. Keeping them apart means dictionary updates can never harm user data.

**D-10 — The dictionary DB is built by a desktop Python script, not on-device.**
JMdict is a large XML file. Parsing it on first launch would take a long time and drain battery. Instead a script run on a development machine produces `spotter.db`, which is committed as an app asset.

Secondary benefit: this script and its output are the most portable assets in the project. A plain SQLite file works identically on Android, iOS, or desktop.

**D-11 — Never store dictionary row IDs in user data.**

If a saved word row contains `dictionary_word_id = 48123`, and the dictionary is later regenerated from a newer JMdict release, row 48123 may now be an entirely different word. Nothing crashes and no error appears — the user's saved list simply begins showing wrong words, potentially a year later.

Store the **natural key** instead: the word text plus its reading (先生 / せんせい), and re-resolve against the dictionary at read time. JMdict's `ent_seq` identifier may be stored as a *hint* to speed lookup, but it is not the identity — JMdict entries do occasionally get merged or split between releases.

**D-12 — Saved-item identity is (text, reading), never text alone.**

上手 has three readings with genuinely different meanings:

| Reading | Meaning |
|---|---|
| じょうず | skilled, good at |
| うわて | the upper hand, superior position |
| かみて | stage left, upstream |

These are separate vocabulary items and a learner must be able to save, study, and schedule them independently.

*Second benefit:* this makes an open UI question free to answer later. Whether the app shows one word screen with a reading selector, or presents three separate tappable entries, becomes a pure presentation choice changeable at any time — because the underlying data already distinguishes them. Had identity been text alone, splitting later would be impossible: existing saved rows would be ambiguous about which reading the user meant.

*That question was settled by D-48* — one screen per written form, readings as sections inside it. The data model here is unchanged, which is the point: the presentation was decided later and cost nothing.

**D-13 — JmdictFurigana is ingested as an internal index, never rendered.**

[JmdictFurigana](https://github.com/Doublevil/JmdictFurigana) is a dataset providing per-character reading alignment — it records that in 先生, 先 carries せん and 生 carries せい.

D-06 says this is never *displayed*. But it is what makes D-04 possible: to group example words by which reading a kanji carries, something must know that 生 is セイ in 先生 and ショウ in 一生. That mapping cannot be computed; it must come from data.

So: ingested, indexed, queried constantly, shown to the user never.

**D-14 — Furigana display is whole-word ruby only.**
Reading kana render above the entire word as a unit — せんせい positioned over 先生, not せん over 先 and せい over 生. Correctly handles jukujikun (D-06) and keeps rendering simple.

**D-38 — The dictionary is rebuilt from scratch every time. Stable dictionary-owned IDs were considered and rejected.**

Each build regenerates `spotter.db` from the source datasets, so **every row number changes**. That is fine, and D-11 is what makes it fine.

*The rejected alternative, recorded so it isn't re-proposed:* assign our own permanent ID to each word on first build, then maintain the database incrementally forever, updating rows in place as JMdict changes. It is a natural idea and it fails for three reasons:

1. **It makes the dictionary stateful.** Every future schema change becomes a migration against accumulated state, rather than an edit to the build script. That is precisely the machinery D-09 exists to avoid needing.
2. **It destroys reproducibility.** Today, sources + script = database. Under the alternative, sources + script + *every prior build* = database. Lose the file and it cannot be reconstructed, because the ID assignments were arbitrary and order-dependent. The dictionary becomes a large irreplaceable binary requiring backup and version control.
3. **It buys nothing.** The natural key (text, reading) already identifies a word across rebuilds, and does so better — it is readable, reproducible from nothing, identical on every device, and joins directly against JmdictFurigana.

Critically, it also does **not** solve the problem it appears to solve. A stable ID pointing at a merged-away entry is just as stale as a failed natural-key lookup; the merge still has to be handled explicitly. That is D-39, and it works with natural keys.

*This decision is scoped to a dictionary derived entirely from public sources.* If the dictionary ever contains authored content — curated kanji explanations, hand-tuned rankings, original example sentences — it becomes irreplaceable and this must be revisited.

**D-39 — The dictionary ships a `changes` table recording merged and removed entries.**

JMdict entries are occasionally merged, split, or removed, which D-11 already noted without saying what to do about it. This is what to do about it.

Each build compares its full set of `(text, reading)` keys against the previous **shipped** build's key set. Keys that disappeared are written into a `changes` table inside the new dictionary asset: the old key, its replacement where there is one, and the build in which it happened.

At read time, a saved item whose lookup fails is checked against `changes`, letting the app say *"merged into 上手 (じょうず)"* with a link, rather than *"not found"*. See D-40 for the rendering rule this feeds.

The table is **derived**, not accumulated — it is recomputed on each build from two key sets, so D-38 is unaffected. The only artifact retained between builds is the previous key list, roughly 200,000 lines of text, about a megabyte compressed.

**D-41 — Source datasets are fetched from a pinned manifest and refreshed at defined events.**

A `fetch` script downloads all sources from a manifest recording, per dataset: URL, download date, SHA-256 checksum, and **the generation date from inside the file's own header**. The header date is the real version identifier; the download date only records when we happened to ask.

*Why the header date matters:* JMdict, KANJIDIC2, and the Tanaka Corpus are published at unchanging URLs and regenerated continuously — JMdict daily. **There is no way to request a past version.** Worse, the generation date is written into each file, so the checksum changes daily even when no content did; a checksum can prove two files differ but cannot tell you whether anything meaningful changed.

*Refresh at events, not intervals:* at the start of Phase 1, before the first release, and once per release thereafter. Never mid-phase. "Every so often when I think of it" degrades either to never, or to a random moment in the middle of debugging something else — which is exactly when a new variable is least welcome. EDRDG's own guidance to downstream users is every few months.

The cost of refreshing rises sharply once real users exist: before release nothing can break, afterwards a refresh can orphan saved words (D-39, D-40). Refresh freely now; refresh deliberately later.

**D-42 — v1 ships no JLPT data. A clearly-labelled estimate may be added later.**

KANJIDIC2 carries a `jlpt` field, but it encodes the **pre-2010 four-level test**, which no longer exists. Displaying it would be actively misleading.

There is no official replacement. The JLPT administrators deliberately stopped publishing kanji and vocabulary lists with the 2010 revision, to discourage rote list-learning. Every N5–N1 list in circulation is a community reconstruction assembled from published past papers — broadly consistent with each other, but estimates, differing at the margins, and usually of unstated licensing.

So v1 ships nothing rather than something wrong. **Not even a placeholder column**, because the dictionary is disposable (D-09) — adding a column later costs a script edit and a rebuild, with no migration. There is nothing to reserve.

*When it is added:* settle the encoding first — `"N5"` versus an integer, and if an integer, whether 5 is the easiest or the hardest. Name the column `jlpt_estimate`, never `jlpt`, so no future reader mistakes a reconstruction for an official figure.

**D-43 — `study_item` carries a `snapshot_gloss`, read only when live lookup fails.**

The default remains D-11's: store only the natural key and re-resolve everything against the dictionary at read time, so improved glosses reach saved words for free.

But that leaves nothing to show when resolution fails. An orphaned card could render only its text and reading — and worse, an SRS card needs a **back**, which comes from the dictionary. Without a fallback, orphaned items become unreviewable and drop silently out of the review queue, which is the vanishing-item problem (D-40) reappearing somewhere the user cannot even see it.

So: at save time, store **the gloss line exactly as the card displayed it** — capped at roughly 80 characters. Not the first sense alone; the line the user was looking at when they chose to save, which by construction is closest to what they meant.

**The rule that keeps this honest: the snapshot is read only when live resolution fails.** When the dictionary resolves the word, live data wins, always. The snapshot therefore cannot drift into showing stale meanings — it is a parachute, not a cache.

Secondary benefit: it makes export files self-describing (D-20). Importing onto a device with a different dictionary build produces usable cards rather than a list of unexplained words.

*Why this cannot drift:* unlike a dictionary column, this lives in the **user** database. Adding it later is a real migration, and every word saved before that release would have a permanently empty snapshot — the gloss cannot be recovered for a word the dictionary has since removed. It must be present at the first user-data write; see the checkpoint table in `roadmap.md`.

**D-51 — Example sentences are ingested in v1 but not rendered. Whether to show them is a Phase 2 decision.**

The build parses **`JMdict_e_examp`** (which is `JMdict_e` *plus* examples, so it replaces rather than supplements it), populates the `example` table, and ships it. The UI renders nothing from it in v1.

*Why not just render them:* coverage is thin and the number is hard to judge in the abstract.

| Measured against JMdict 2026-08-06 | |
|---|---|
| Common senses with a sense-attached example | **41.4%** (19,357 of 46,713) |
| Common entries with at least one example | 55.9% |
| All senses, including rare vocabulary | 12.5% |

"Four in ten senses" is not something anyone can evaluate on paper. Looking at 先生's actual screen and deciding whether it feels complete or embarrassing is — and that requires Phase 2.

*Why ingest anyway rather than defer entirely:* the `<example>` elements sit **inside the `<sense>` elements the parser already walks**, so extracting them is a few extra lines rather than a separate pass. Ingesting now makes the Phase 2 question purely a UI one, with no return trip to the Python builder mid-Android-work. If the answer turns out to be no, the table is dropped on the next rebuild and nothing is lost — the dictionary is disposable (D-38).

### Alternatives measured and rejected

Recorded so this is not re-litigated. All figures are for **common** entries — those carrying a JMdict frequency tag, i.e. the words anyone would actually photograph.

| Option | Result |
|---|---|
| **Tanaka Corpus** (`examples.utf`) | Better *word-level* coverage (57.3% vs 55.9%) but only **7,537** sense-tagged (word, sense) pairs against `JMdict_e_examp`'s 31,642. Most of its `B:` line tokens carry no sense number at all |
| **Raw Tatoeba** | No English pairing in the export, no word index. Rebuilding, worse, what the other two already provide |
| **Both combined** | **+1.7 percentage points** (41.4% → 43.2%). They are the same corpus — `JMdict_e_examp`'s examples cite Tatoeba ids, and Tanaka is Tatoeba's curated ja-en subset. `JMdict_e_examp`'s covered words are a strict *subset* of Tanaka's |
| **JParaCrawl** (21M pairs) | Released for research purposes — a licensing problem for a shipped app |
| **JESC** (3.2M pairs) | Freely licensed but subtitle dialogue; uneven register for learners |
| **Japanese WordNet** | Genuinely sense-annotated, but against WordNet's sense inventory, not JMdict's. Mapping the two is a research problem |
| **Build-time LLM sense tagging** | Technically possible — D-46 binds the app, not the desktop build. Rejected because D-46's *reasoning* is about confidently-wrong content presented as fact in a teaching app, and wrong sense assignments would be baked into the shipped dictionary, unverifiable at scale and invisible to the user |

**The ceiling is roughly 43%, and it is a corpus limitation, not a sourcing mistake.** Around 57% of common senses have no attested example sentence anywhere in this data. More sentences do not help: the bottleneck is *sense annotation*, not sentence supply, and attaching an unlabelled sentence to a specific sense is word sense disambiguation (D-44).

*Not affected by any of this:* the kanji Examples tab, which shows example **words** grouped by reading (D-04) and is built from JMdict plus JmdictFurigana. Its coverage is essentially complete. The product thesis does not rest on example sentences.

*Phase 2 revisit:* decide whether to render, and if so whether sense-attached only or word-level too. See the checkpoint table in `roadmap.md`.

**D-52 — Reading alignment normalizes sound changes. Spans that still don't match are kept with a NULL reading, never dropped and never guessed.**

JmdictFurigana gives the kana a kanji carries **as it appears** in a word, which routinely differs from its dictionary reading. Matching the two is the hardest correctness problem in Phase 1 (V-17).

Measured over 574,721 spans:

| Matcher | Unmatched |
|---|---:|
| Exact comparison only | **8.00%** |
| Plus rendaku, gemination and okurigana | **2.09%** |

Three normalizations, roughly twenty lines between them:

- **Rendaku** — unvoice the first mora. 花火 gives 火 → び; び unvoiced is ひ, which is 火's kun reading.
- **Gemination** — restore the mora a trailing っ replaced. 学校 gives 学 → がっ; がっ expands to がく = カク.
- **Okurigana** — compare against KANJIDIC2's full kun form as well as its stem, since `い.きる` may surface as either い or いきる.

*Why not simply drop unmatched spans*, which was proposed and is superficially attractive at 8%: that 8% is **not a random sample**. Rendaku and gemination happen in established, frequent compounds — words erode phonetically *because* they are common. Dropping them removes 仕事 from 事's こと group, 出口 from 口's くち group, and 学校 from 学's カク group, leaving the Examples tab showing rarer words in their place. That is precisely the failure V-04 exists to prevent.

*What remains at 2.09%* is two categories. **Verb stem forms** — 引き, 言い, 売り, 買い — which a conjugation rule would mostly catch, taking the residue to roughly 1.5%. And **genuinely irregular readings** KANJIDIC2 does not record at all (文 → も in 文字, 其 → そ), which no rule can derive.

That irreducible remainder is handled exactly as the drop-them proposal suggested: `canonical_reading` and `reading_type` are **NULL**, so the span joins no reading group and never appears on the Examples tab. The idea was right; it was only wrong applied to the whole 8% rather than the 2% that is actually irreducible.

*Why NULL rather than deletion:* it makes a silent failure countable. `SELECT count(*) FROM kanji_in_word WHERE reading_type IS NULL` is the build health check (V-22). A future build whose residue jumps from 2% to 20% says so, instead of quietly shipping a thinner Examples tab. Deleting the rows destroys that evidence; guessing a reading manufactures wrong data.

**D-53 — Obsolete readings are ingested and displayed, marked as archaic.**

JMdict tags out-dated kana with `&ok;` on the reading. 上手 carries じょうて and じょうしゅ, real historical readings nobody uses today.

They are kept, and shown on the word screen alongside current readings — visually distinguished, not hidden.

*Why:* this is a **scanning** app. Someone photographing an inscription at a temple, an old shopfront, or a period text is exactly the person who needs じょうしゅ, and that is a genuine use of the product rather than a hypothetical. Hiding the reading would leave them with no explanation for what is in front of them.

*And we could not act on the distinction anyway.* D-48 shows every reading of a written form, because the app cannot know which one applies (D-44). There is no point at which the app knows a user "scanned the archaic reading" — it only knows they scanned 上手.

Presentation — how strongly to mark it, what wording — is a Phase 2 design question. V-21 covers the failure mode: an obsolete reading rendered identically to a current one teaches kana nobody uses.

**D-54 — Two sense filters with opposite defaults. Obscurity is a ranking rule, not a user setting.**

JMdict is a general-purpose dictionary and records how words are actually used. 生 (なま) carries a sense referring to unprotected sex, directly below "raw; uncooked; fresh."

Two user-facing toggles, and the defaults deliberately differ because the risks are not symmetric:

| Toggle | Tags | Senses | Default | Reasoning |
|---|---|---:|---|---|
| Show explicit content | `vulg` `sens` `derog` `X` | ~900 | **off** | Showing by default risks an unpleasant surprise; hiding costs almost nothing |
| Show slang & colloquial | `sl` `col` | ~3,900 | **on** | Signage, menus and manga are full of casual language. Hiding it by default means failing to explain text the user is looking at |

**Obscurity is a separate concern and not a setting.** `arch` (3,787), `obs` (736) and `rare` (3,144) look like the same category but describe *usefulness*, not offence. They get a ranking rule instead:

- **On a word's own screen — show every sense**, archaic included. The user opened that word specifically and deserves the complete picture.
- **In example lists — never lead with them.** The kanji Examples tab must not surface obscure vocabulary (V-04).

Note this is about *word* selection, not reading identification. The app never knows which reading was scanned (D-53), and none of these rules require it to.

**The rule that stops the filter backfiring: never filter a word to zero senses.** If every sense of a scanned word is tagged, show them regardless. Filtering to nothing turns a real word into "not found", which reads as a broken app rather than a discreet one — the same failure D-40 prevents for saved items. Covered by V-23.

All these tags live on `word_sense.misc`, so every one of these policies is a query-time decision. Changing any of them never requires a rebuild.

**D-55 — The compressed source files are committed to git.**

About 29 MB across four files, in `tools/dictbuild/data/raw/`.

*Why:* three of the four sources are published at fixed URLs and regenerated continuously — **a past version cannot be re-fetched** (D-41). Committing them is therefore the only mechanism that makes a shipped build reproducible, and it makes a fresh clone self-contained with no download step.

*Why the cost is acceptable:* refreshes happen at defined events, once or twice a year (D-41), so history growth is bounded. Every file is well under GitHub's 50 MB per-file warning.

*One non-obvious requirement.* `.gitattributes` marks the directory `-text`. Without it git rewrites LF to CRLF on Windows checkouts, changing the bytes of `JmdictFurigana.txt` and therefore its SHA-256 — so the file would fail the verification in `sources.lock.json` on a fresh clone, defeating the entire purpose. Also `-diff`, since a binary diff of a 13 MB gzip helps nobody.

*If this is ever reversed:* removing large files from git means rewriting history. Adding them was the cheap direction; that asymmetry is why the decision waited until real sizes were known.

**D-56 — Dictionary storage layout: `WITHOUT ROWID` for narrow rows, plain tables for wide ones. Measured per object, never by the total.**

The dictionary went from **126.2 MB to 99.7 MB on disk** (45.4 → 30.3 MB gzipped) with **no row removed** — identical counts in every table before and after. All of it is physical layout.

| Object | Before | After | Change | Cause |
|---|---:|---:|---:|---|
| `kanji_in_word` | 32.9 MB | 21.7 MB | **−11.2** | `WITHOUT ROWID` |
| `idx_word_reading` | 8.3 MB | — | **−8.3** | dropped, D-57 |
| `word_sense` | 29.0 MB | 23.0 MB | **−6.0** | `WITHOUT ROWID` |
| `strokes` | 7.5 MB | 6.3 MB | −1.2 | coordinate rounding |
| `kanji` | 1.1 MB | 0.9 MB | −0.2 | `WITHOUT ROWID` |
| `idx_kiw_group` | 11.7 MB | 12.5 MB | +0.8 | cost of the above |
| `word`, `example` | | unchanged | | integer primary keys |

### Why `WITHOUT ROWID` helps here

SQLite gives every table a hidden auto-numbered `rowid` and stores rows in a b-tree keyed by it. Declaring a `PRIMARY KEY` over *real* columns then builds a **second** b-tree so rows can be found by that key — and that second tree holds another copy of the key columns.

`kanji_in_word` is keyed on `(kanji_char, word_id, position)` across 574,721 rows, so those columns existed twice. `WITHOUT ROWID` stores the rows directly in the key tree: one tree instead of two. In relational terms, a clustered index rather than a heap plus a secondary index.

Applied to `kanji`, `word_sense`, `kanji_in_word`, `changes` and `meta`.

**Not applied to `word` or `example`** — their primary key *is* `INTEGER PRIMARY KEY`, which already means the rowid, so there is nothing to collapse.

### The exception, and how it was found

**`strokes` is deliberately a plain table.** Making it `WITHOUT ROWID` cost **3.4 MB**, swamping the 1.2 MB the coordinate rounding saved.

A `WITHOUT ROWID` table stores row content in the primary-key b-tree, so wide rows land on *interior* pages and inflate the tree. `svg_paths` averages ~1 KB per row — by far the widest column in the schema. SQLite's own guidance is that the layout suits small rows; this is the one table here that is not.

**This was invisible in the total.** The aggregate said "down 21.5 MB, job done" while one table quietly moved 3.4 MB the wrong way. The measurement that found it — drop each object, `VACUUM`, record the delta — is the method to repeat before trusting any future layout change.

### Coordinate rounding

KanjiVG stores stroke paths to two decimals on a 109-unit canvas. The second decimal is 0.009% of the canvas, under a tenth of a pixel at any size a phone renders. Rounding to one decimal removes ~19% of the path text.

**This is the only lossy change in the build.** Everything else is exact. If stroke rendering ever looks wrong at very large sizes, this is the first thing to suspect and a one-line revert in `ingest_kanjivg.py`.

### Reverting any of this

All of it is contained in `schema.sql` plus `ingest_kanjivg.py`, and the dictionary is disposable (D-38) — change the file, rebuild, ship. No migration, no user-data risk. Re-measure per object afterwards rather than trusting the total.

**D-57 — Indexes are added when a feature needs them, and column order is load-bearing.**

Two lessons from the same table, both expensive, both recorded so they are not repeated.

### No speculative indexes

`idx_word_reading` indexed `word.reading` — a sorted copy of that column across 322,323 rows, **8.3 MB**, about 7% of the database. It would serve looking a kanji word up *by* its reading (typing せんせい to find 先生).

**No v1 feature does that.** The scan pipeline always arrives with the written form from OCR, and for kana-only words `text` equals `reading` so the existing `UNIQUE (text, reading)` already covers them.

Dropped. One line to restore if a kana search box appears.

### Column order decides whether an index is used at all

`idx_kiw_group` was originally `(kanji_char, reading_type, reading_group)`, and the Examples-tab query filtered `reading_type IS NOT NULL`. **SQLite compiles `IS NOT NULL` into a RANGE condition**, and a range on the second column stops the third being usable for equality — so the query scanned every row for the kanji and filtered in memory.

The predicate was redundant anyway: `reading_group` is NULL exactly when `reading_type` is.

Fixing the plan alone was not enough. Ordering by the word's own `freq_rank` still meant joining every row in the group and sorting in a temp b-tree before `LIMIT` could apply, so cost scaled with group size — and the largest groups belong to the commonest kanji, which are the screens users open most. 生/セイ holds 1,462 rows, 手/て holds 1,835.

So `word_freq` is denormalized into `kanji_in_word` and the index carries it third, making the query an ordered index scan that stops after N rows:

> **10.94 ms → 0.079 ms**, identical results. A 138× difference on the app's core screen.

Unranked words are stored as `9999` rather than NULL so a plain ascending scan orders them last (V-04) without a `NULLS LAST` clause the index cannot use.

*The general rule:* check `EXPLAIN QUERY PLAN` for every query the app actually runs, and confirm the plan contains no `USE TEMP B-TREE`. All six current lookup patterns were verified this way.

**D-58 — The build must produce a byte-identical database from identical sources.**

`build_id` is a hash of the source checksums (D-41), so a build from unchanged inputs is *labelled* unchanged. That label is a lie if the output actually varies, and the `changes` diff (D-39) would then report churn between builds that changed nothing.

**The rule: never let unordered iteration decide a stored value.**

The bug that produced this decision: a surface reading can match several readings of the same kanji — 一 is both イチ and イツ, and いっ geminates from either. The matcher iterated a Python `set` of candidates, and **string hashing is randomised per process**, so the winner varied between runs. 一生 resolved to イチ on one build and イツ on the next, from byte-identical inputs.

Nothing errors. Both are real readings of 一. The word simply lands in a different reading group depending on which process built the dictionary.

The fix is also more correct: iterate KANJIDIC2's reading list, which is ordered with the primary reading first, and test membership in the candidate set rather than the reverse. Verified identical across three `PYTHONHASHSEED` values. Covered by V-25.

*Applies to any future ingest stage.* Sets and dicts keyed on strings are fine as lookups; they must not decide which of several candidates gets written.

**D-64 — Wall-clock time lives outside the dictionary, in a `build-info.json` sidecar.**

**D-58 is not superseded by this — it was never implemented.** It required a byte-identical database from identical sources, and required `build_id` to be a hash of those sources. The code did neither, and said it did in three places (D-58 itself, V-25, and `build_id`'s own docstring). Found on 2026-08-11 when two builds minutes apart produced different checksums.

Two wall-clock values were responsible, and nothing else:

- **`meta.built_at`** — a timestamp inside the artefact makes its bytes differ on every build *by construction*. D-58's rule was therefore not merely unverified, it was unachievable.
- **`build_id`'s `%Y%m%d-` prefix** — two builds from byte-identical sources got different ids on different days. This is precisely the "the label is a lie" failure D-58 was written to prevent, and the `changes` diff (D-39) records entries against that identity.

**The rule: nothing inside the shipped artefact may be a function of when the build ran.** Provenance is not the problem; provenance *inside the thing being checksummed* is.

So `built_at` moved to `build-info.json`, written beside the database. `build_id` is now a plain hash of the source checksums. With those two changed, two consecutive builds are byte-identical — verified before the change was designed, which is what made this the cheap option rather than a speculative one.

*What this buys:* CI can checksum the **shipped 100 MB artefact** instead of the derived `keys.tsv.gz`. That is a far stronger determinism test — `keys.tsv.gz` covers the word key list, while the database also holds senses, readings, examples, stroke paths, and every index.

*Why a sidecar rather than deleting the timestamp:* "when was this built, and from what?" is a real question when debugging a bad dictionary. The file is build output, not an app asset — the app reads `meta`, and D-38 keeps the dictionary disposable, so dropping a column cost nothing.

*Consequence:* `build_id` changed shape, from `20260811-1103feb9` to `1103feb952bd`. Nothing persisted depends on it yet, which is why this was cheap now and would not have been after Phase 8 ships export files carrying build ids.

**D-65 — `build_id` identifies the artefact, not just its inputs: it hashes the sources *and* the builder, with line endings normalised.**

D-64 made `build_id` a pure hash of the source checksums. That fixed the date-prefix bug but left the mirror-image one: **a change to the builder was invisible.** Adding `NOT NULL` to `word.id` in `schema.sql` produced a materially different database carrying an identical `build_id`.

D-58's rule survived in one direction — a rebuild that changed nothing was labelled unchanged — while the question that actually matters went unanswered: *did this artefact change?* An on-device dictionary refresh has to answer exactly that, and keyed on the old id it would have silently done nothing.

So `build_id` now hashes the source checksums **plus** a digest of every builder file — `build.py`, `changes.py`, `kana.py`, `ingest_*.py`, `schema.sql`. Deliberately excluded: `verify.py` and `test_dictbuild.py` read the output, `inspect_sources.py` reads the raw sources, and `fetch.py`'s effect shows up as a changed `sources.lock.json`, which the source checksums already cover.

**Line endings are normalised to LF before hashing, and that is load-bearing.** Git checks these files out CRLF on Windows and LF on Linux, so hashing raw bytes would make `build_id` a function of the developer's platform — the same commit yielding a different id on a laptop than in CI. Caught while testing this change, not in production. The Gradle task normalises identically; the two implementations must stay in step.

*Second thing this buys — one definition of "the builder".* `build.py` publishes the file list with each hash in `build-info.json`, and `:app:stageDictionaryAsset` consumes it rather than keeping its own glob. Previously both maintained a list and could drift.

*Third — the staleness check stops lying.* It compared mtimes, so `git checkout` or a branch switch made a current database look stale; it fired on a `build.py` byte-identical to git. Comparing published hashes removes the false positive, and with it the `-PallowStaleDictionary` escape hatch that existed only to work around it. A guard that cannot cry wolf does not need one.

*Cost:* `build_id` changes whenever the builder changes, so a refactor that alters no output still produces a new id. That is the correct trade — the id answers "is this the same artefact?", and a conservative answer is safe where a false "unchanged" is not.

**D-59 — GitHub review runs only on manual trigger, and committed datasets must be undiffable.**

`claude-code-review.yml` originally triggered on `pull_request: [opened, synchronize, ready_for_review, reopened]`, which starts a full review on **every push to a PR branch**. PR #7 accumulated **eleven runs**. The last two, immediately after ~29 MB of dictionary sources were committed (D-55), ran **18m20s and 9m18s** — the reviewer was reading through the data files. That consumed a large share of a token budget in minutes.

Three layers, all required:

1. **`workflow_dispatch` only**, with a `pr_number` input, plus `--max-turns 40` as a ceiling that holds even if the exclusions fail. Do not restore a `pull_request` trigger; if automatic review is ever wanted, scope it with `paths` to source code only.
2. **The prompt names the excluded paths** and instructs the reviewer not to open them.
3. **`.gitattributes` marks them `-diff linguist-generated`.** This is the layer that matters most, because it protects *any* tool reading the repository rather than one workflow. Verified: the 12 MB `JmdictFurigana.txt` reports "Binary files differ" instead of emitting its contents.

`CLAUDE.md` carries a do-not-read table so a local session does not repeat it either. `inspect_sources.py` is the supported way to examine those files' structure.

*Note the asymmetry that made this expensive:* committing the datasets (D-55) was a sound decision, but it combined with an automatic reviewer to produce a cost neither change implied on its own. Any future decision to commit large files should check what reads them automatically.

---

## User data and migrations

> **Background:** Android preserves an app's internal storage and databases across app updates automatically. Uninstalling wipes them. So the danger to user data is not updates in themselves — it is **schema changes** made during an update, and the tooling's default behavior when a migration is missing.

**D-15 — UUID primary keys on all user data, never auto-increment integers.**
Two devices creating records while offline will both generate `id = 5`, and there is no way to reconcile them afterward. This is unfixable once real user data exists. UUIDs cost nothing now and are a precondition for the sync described in D-19.

**D-16 — Every user row carries `updated_at`; deletions are soft (`deleted_at`), never hard `DELETE`.**
`updated_at` is required for sync conflict resolution. Soft delete is required for deletion *propagation*: if phone A hard-deletes a record, tablet B has no way to learn that it was deleted — B simply observes that A is missing a record it has, and helpfully re-adds it. A tombstone row communicates the deletion.

**D-17 — `fallbackToDestructiveMigration()` is banned in every build type.**

Room versions the database schema. Increment the version without supplying a migration and the app crashes on launch. `fallbackToDestructiveMigration()` resolves that crash by **deleting the entire user database and recreating it empty.**

It appears in a large fraction of online tutorials because it makes the development-time crash go away, and it is the most common way Android apps silently destroy user data in production. It must not enter this codebase, including debug builds — the habit is the hazard.

**D-18 — Room schema export is on; the generated schema JSON is committed to git.**
Room can emit a JSON description of each schema version (`room.schemaLocation`). Committing these allows diffing versions against ground truth rather than memory when writing a migration, and enables `MigrationTestHelper` to test migrations against genuine historical schemas.

Migrations must be tested as **chains**, not single hops: a user on v1.0 who installs v1.4 runs 1→2→3→4 in sequence. Never assume the previous installed version was the immediately preceding release.

**D-19 — Design for sync now; build accounts later.**
A server-backed account with cross-device sync is a plausible future direction. Building it now would add authentication, privacy obligations, a Play Store data-safety declaration, and hosting cost — none of which help v1.

D-15, D-16, and the repository pattern (`architecture.md`) are the parts that are painful to retrofit. With them in place, adding sync later means writing a sync service. Without them it means rewriting the data layer and migrating every existing user.

**D-20 — Manual export/import ships before any sync.**
An in-app action that writes a versioned JSON or zip file and hands it to Android's share sheet or file picker, importable on a fresh install or a different device.

Beyond its direct user value, it earns its place early for two reasons: it is the recovery path if a production migration ever fails, and defining a clean serializable representation of all user data is precisely the payload a future sync API would send. Building export first is therefore a head start on D-19, not a detour from it.

---

## Images

**D-21 — v1 saves the downscaled full camera frame; word crops are deferred.**
Roughly 1600px on the long edge, WebP lossy quality 80, giving about 250–400 KB per image. Cropping to the specific word would be smaller and a better memory hook, but getting crop geometry right (coordinates, padding, edge cases at image boundaries) is fiddly work that shouldn't block v1.

**D-22 — Store the word's bounding box from v1 regardless.**
Four integers per scanned word. They are already available at scan time — the same data drives the tap overlay — so recording them is a schema field, not new work.

The payoff: moving to word crops later requires **no image reprocessing at all**. The location of the word inside each stored image is already known, so cropping can happen at display time, or lazily replace the file on next access. Without this, the upgrade would mean re-running OCR across every saved image, which is slow, battery-hungry, and awkward to present to the user.

*Generalized principle:* **capture cheap metadata now even when unused.** Bounding boxes, raw OCR text, token character offsets, and the app version that created each record all cost bytes and buy future options. Deriving them later means reprocessing; recording them now is a schema field.

**D-23 — Image format migrations never reprocess old records.**
Each image row carries an `image_type` discriminator (`FULL_FRAME` | `WORD_CROP`). Old records keep their original type forever; new records use the current one. The UI renders both. Mixed-format data is the normal, expected steady state — not a problem to be cleaned up.

**D-24 — Images are files on disk; the database stores relative paths.**
Filenames are UUIDs — never sequential, never derived from content — so collisions across migrations and imports are impossible. Paths are stored **relative** to the app's storage root, because the absolute path can change (across OS versions, backup restores, and device transfers).

Storing images as SQLite BLOBs would bloat the database file and slow every query that touches those rows, including queries that don't need the image.

**D-25 — Scan history and saved-word images have separate lifecycles.**
Images attached to saved study items persist indefinitely. Images from casual scans that were never saved auto-purge after N days. Without this split, ordinary browsing quietly fills the device.

Ship a storage screen showing usage, a clear action, and a "save scan images" toggle.

---

## SRS and organization

> **Background:** a Spaced Repetition System schedules review of each item at growing intervals, timed to just before the learner is predicted to forget it. **FSRS** (Free Spaced Repetition Scheduler) is the modern open-source algorithm, adopted by Anki; it is better calibrated than the older SM-2.

**D-26 — FSRS, not SM-2 and not a custom algorithm.**
Well-researched, actively maintained, and open implementations exist to port. Scheduling algorithms are easy to get subtly wrong and the failure mode (wasting the user's study time for months) is invisible.

**D-27 — Study items are polymorphic from day one, even though v1 ships words only.**

The `study_item` table carries a `type` discriminator (`WORD` | `KANJI`) from the first schema version, and identity is `(text, reading, type)`.

*Why now:* kanji-only study is a wanted future feature (see D-01). Adding the discriminator later would mean restructuring the table that `srs_state` and every row of `review_log` point at — a migration touching the user's entire study history. Adding a column now that v1 always populates with `WORD` costs one field.

**D-28 — Saved lists are many-to-many, via a join table.**
Users can create named lists ("Street Signs", "Food Menu"). A `list_id` column on the study item would restrict each word to a single list — but the same word genuinely appears on both a restaurant menu and a street sign, and the user will want it in both.

**D-29 — Scheduling belongs to the study item; lists only filter review sessions.**

There is exactly one `srs_state` row per study item. It hangs off `study_item`, **not** off list membership.

*Why this matters:* if scheduling attached to list membership, a word saved to two lists would carry two independent schedules. The user would review 先生 today because it appeared in "Street Signs" and again tomorrow via "Food Menu" — doubling their workload and corrupting the algorithm's model of their memory, since FSRS infers retention from the interval since the last review.

Lists are organizational tags. Review sessions may *filter* by list ("review only my Food Menu words"), which gives the same flexibility with correct behavior.

---

## UI

> **Terms used below.** *Peek sheet:* a bottom sheet partially raised over the current screen, showing a summary. *Component chips:* small tappable elements on the word screen, one per constituent kanji. Full screen descriptions are in `ux.md`.

**D-30 — The peek sheet and the word screen are a single component.**
Material 3's `ModalBottomSheet` supports partial and full expansion. The partial (peek) state shows word, reading, meaning, a Save action, and a "Full Details" button. Dragging it up — or tapping Full Details — expands the same sheet into the complete word screen.

*(The reading was later removed from the peek state — see D-47. The single-expanding-component claim below is unaffected.)*

*Why:* the original design described a popup plus a separate full-screen detail view. Making them one expanding sheet means the user never loses their place in the scanned image, the gesture is natural and reversible, and the project builds one component instead of two.

**D-31 — Peek only on the scan screen.**
The peek state exists because the scan screen is where a user triages many words quickly and wants to stay in the image. Once inside a detail context, navigation is direct. No nested peeks.

**D-32 — The kanji screen swaps in place inside the sheet, with a back arrow.**
Rather than pushing a separate full screen onto the navigation stack. This keeps the frozen scan visible behind the sheet, preserves the sense of still studying *this* image, and makes one back gesture return to the word.

Cost: `ModalBottomSheet` has no built-in back stack, so this needs a small amount of custom Compose plumbing to manage the two-level word→kanji stack.

**D-33 — Overlay style: dim the image, render detected text at full brightness.**
Drawing a rectangle around every detected word turns a photograph into unreadable clutter. Dimming everything *except* the text makes the legible text itself the affordance — it reads as deliberate design rather than a debug view. A solid highlight marks the currently selected word only.

**D-34 — Bundle Noto Sans JP explicitly rather than relying on system fonts.**
Unicode unifies Chinese and Japanese characters onto shared codepoints, but the correct *glyph shapes* differ by region — 直, 骨, 令, and 化 all render visibly differently in Chinese versus Japanese typefaces. Android's default font stack may select Chinese forms depending on locale and device.

In an app whose purpose is teaching people to read and write kanji, displaying the wrong glyph form is a correctness bug, not a polish issue.

**D-35 — Material 3 with a design-token layer from the first UI commit.**
Centralized colors, type scale, and spacing. Roughly ten minutes of setup at the start; a refactor touching every composable if retrofitted later.

**D-36 — Three bottom-navigation destinations: Scan · Saved · Review.**
Resist a fourth. Settings, storage, and attribution live inside Saved or a menu, not in the primary navigation.

**D-37 — Reading labels follow the standard dictionary kana convention: on'yomi in katakana, kun'yomi in hiragana.**

On the kanji screen's Overview and Examples tabs, reading group headers render as:

> **セイ** — 先生 · 学生 · 生活          ← on'yomi, katakana
> **ショウ** — 一生 · 誕生日             ← on'yomi, katakana
> **なま** — 生ビール                    ← kun'yomi, hiragana
> **い(きる)** — 生きる                  ← kun'yomi, hiragana

*Why:* this is the convention used by every Japanese dictionary and learning resource, so it matches what learners will encounter elsewhere. It also carries information for free — the script alone tells the user whether a reading is on'yomi or kun'yomi, without a label, which matters because on'yomi typically appear in multi-kanji compounds and kun'yomi typically stand alone.

**This requires deliberate normalization during Phase 1 ingest, because the source datasets disagree:**

| Source | Stores readings as |
|---|---|
| KANJIDIC2 | on'yomi in katakana, kun'yomi in hiragana — already correct |
| JmdictFurigana | hiragana throughout, since furigana is conventionally hiragana |

Since JmdictFurigana is what powers the grouping (D-13), its hiragana readings must be converted to katakana when the reading is an on'yomi. Determining which is which means cross-referencing KANJIDIC2's reading lists for that kanji. Get this wrong and every on'yomi group header renders in the wrong script.

*Scope note:* this governs **reading labels only.** Furigana displayed over words stays hiragana in all cases (D-14) — that is a separate convention and the two must not be conflated.

**Exception found during ingest: ~60 kun'yomi are legitimately katakana.** Japanese writes loanwords in katakana, and KANJIDIC2 preserves that for kanji whose word-level reading is a loanword — Meiji-era unit ateji (粁 キロメートル, 吋 インチ, 瓩 キログラム) and chemical elements (鋁 アルミニウム, 鉑 プラチナ).

This is not confined to obscure characters. 志 (frequency rank 823) reads both こころざし *ambition* and シリング *shilling*; 粉 (1,484) reads こな *powder* and デシメートル.

So the rule is **preserve KANJIDIC2's script for kun readings**, not force them to hiragana. Converting would render 志's reading as しりんぐ, which nobody writes. The conversion this decision does require runs the other way — JmdictFurigana's hiragana to katakana where a reading is an on'yomi. See V-24.

**D-40 — A saved item that cannot be resolved is always rendered. It never disappears.**

If the dictionary cannot resolve a saved item's `(text, reading)`, the app shows the card anyway — with the text, the reading, the review history, and an explanation — rather than omitting it from the list.

*Why this is a decision and not an implementation detail:* the alternative is not a visible bug. A list that quietly contains one fewer item than the user remembers saving looks like nothing at all. The user knows they saved something, cannot find it, and has no way to tell whether the app lost it or they misremembered. That is a trust failure, and trust failures in a study app end with the app being deleted.

The rule holds **independently of D-39.** Even with an empty or missing `changes` table, an unresolvable item renders as *"this entry is no longer in the dictionary"* rather than as absence. D-39 upgrades the message from that to *"merged into 上手 (じょうず)"*; it is not what prevents the disappearance.

Worth stating plainly: a dictionary update **cannot** delete a user's saved word. The two databases are separate (D-09) and the dictionary has no write access to user data. Vanishing is only ever something the app chooses to display — which is why it is a rendering rule.

Paired with D-43, which ensures such a card still has a meaning to show.

**D-47 — The peek sheet shows the word and its meanings. It never shows a reading.**

*Refines D-30*, which described the peek state as showing "word, reading, meaning" — the reading is removed.

> **上手**
> skillful; proficient · upper part · stage left
> `[Save]` `[Full Details]`

*Why:* the reading shown would be the tokenizer's guess, and 上手 has five. A learner who knew which one applied would not be scanning it. Presenting a guessed reading as fact teaches something possibly false to precisely the person who cannot detect the error.

**This holds even when the word has only one reading.** Showing せんせい for 先生 would be safe and useful in isolation, but a peek sheet that sometimes carries a reading and sometimes doesn't is unpredictable, and the user has no way to know which case they are looking at. Consistency is worth more than the information.

Readings appear on the word screen (D-48), where every one is shown together and none is asserted as *the* answer.

**D-48 — One word screen per written form. Readings are sections inside it, not separate screens.**

D-12 deliberately left this open: *"whether the app shows one word screen with a reading selector, or presents three separate tappable entries, becomes a pure presentation choice."* This settles it. The data model is unchanged — identity remains `(text, reading)` — only the presentation is decided.

Layout, in order:

```
上手
  じょうず  skillful; proficient; good (at); adept
            彼は文章を書くのが上手であるとわかった。
            He proved to be a good writer.
  うわて    upper part
  かみて    stage left
Composed of:  上 above, up    手 hand
```

Each reading is a heading; its meanings sit under it; its example sentences sit under those. **Component chips come last**, below every reading.

*Why chips last:* the examples belong to the meanings and must sit next to them. The chips are reference material — the answer to "what is this made of?", which is a follow-up question to "what does this say?"

*Cost, accepted:* the chips are the only route to the kanji screen (D-05), so burying them adds scrolling to a core drill-down. Judged worth it, because a user who wants the kanji breakdown is already engaged and will scroll; a user who just wants the meaning should not have to scroll past the breakdown to reach it.

*Why one screen rather than three:* the app cannot tell which reading applies (D-44), so presenting three tappable entries asks the user a question they came here to have answered. One screen shows the alternatives side by side and lets the sentence context decide.

**D-49 — A single-character token opens the kanji screen directly, skipping the word screen.**

生 scanned alone is a word — several, in fact — *and* a kanji. Routing it through a word screen produced two screens headed 生, both listing readings, connected by a lone component chip pointing at a screen that looked like the one you were already on.

So: **a single-character token opens the kanji screen**, with the character's word senses shown in the Overview tab under an "As a word" heading, each with its own example sentences. Multi-character words are unaffected — 先生 still opens a word screen and still drills into 生 via a chip, arriving at *the same* kanji screen.

One kanji, one screen, reached from either direction.

*Why not merge the two screen types entirely:* 先生 has no stroke order of its own and no single set of on/kun readings — 先 has one set, 生 has another. A merged screen would need nested per-character tabs inside a bottom sheet, which is exactly the "nonsense or awkward compromises" D-05 exists to avoid.

*Cost:* one branch in navigation — single-character tokens route differently. A few lines of code, and a rule statable in one sentence.

*Note on scope:* the "As a word" section carries example sentences, but the **Examples tab remains words-grouped-by-reading (D-04, unchanged)**. Sentences attach to *words* and appear wherever word data appears. They never attach to a kanji as a character, because no dataset records which sense a kanji contributes inside a compound (D-44).

**D-50 — The kanji screen carries only reference a learner can use. Grade and radical are dropped.**

Removed from the Overview tab:

- **School grade** — the Japanese school year in which the kanji is taught. Real information, but the label means nothing to a non-Japanese learner, and it would need explaining to earn its space.
- **Classical radical** — the index component used to look kanji up in *paper* dictionaries. Near-zero utility for someone who will never use one. KANJIDIC2 also stores it as a bare number (`100`), so displaying it at all would require sourcing a 214-entry number→glyph table; dropping it removes that task entirely.
- **JLPT level** — already removed by D-42, but `ux.md` still listed it.

**Stroke count moves to the Stroke Order tab**, where it is self-explanatory and sits beside the thing it describes.

*Why:* "5 strokes · Grade 1 · Radical 100" is three facts, two of which are unreadable to the audience. A reference screen that requires its own key is not reference, it is clutter.

The visual-component question — *what pieces is this kanji built from?* — is the genuinely useful version of "radical", and it is deferred separately in `roadmap.md` (KRADFILE), where the obstacle is component *naming* rather than data.

*Consequence:* Overview would be left holding only meanings and readings, which is thin. D-49 refills it with the "As a word" section for kanji that are also standalone words.

**D-66 — Search-only readings are hidden — but never to nothing. `gikun` is not a defect marker.**

D-53 settled that obsolete kana is shown and marked. Building V-21 turned up the rest of the column it lives in: `word.reading_info` carries **five** JMdict `re_inf` codes, not one, and the app was reading none of them.

| Tag | Meaning | Rows | Treatment |
|---|---|---|---|
| `sk` | search-only kana form | 6,647 | **hidden**, unless it is all the word has |
| `ok` | out-dated kana | 1,301 | shown, marked *archaic* (D-53) |
| `ik` | irregular kana usage | 512 | shown, marked *irregular* |
| `gikun` | gikun / jukujikun | 506 | **not marked at all** |
| `rk` | rarely used kana form | 285 | shown, marked *rare* |

**`sk` is hidden because it is not a reading.** JMdict carries these so a search matches, not so anyone reads them: katakana renderings (私 ワタシ), stretched colloquial spellings (綺麗 きれーい), and outright common misreadings (中国 ちゅうこく, 七 ひち). Displayed as ordinary readings they do more damage than the archaic ones, because they land on far commoner words — 中国 opened on ちゅうこく, badged *common*, above ちゅうごく.

**But hiding them can never empty a word.** 3,143 written forms have no other reading — almost all kana-only variants such as あっかんべえ and ゼイゼイ言う. An unconditional filter would report a word the dictionary plainly holds as missing, which is exactly the failure D-40 exists to prevent, reached from a different direction. So the rule is *drop them where anything else can be shown*, and where nothing else can, show them marked *non-standard*.

*Why hide rather than mark, when D-53 argued so firmly for marking:* D-53's reasoning is that someone photographing a temple inscription genuinely needs じょうしゅ — the archaic reading is real, attested, and the answer to what is in front of them. No one is ever looking at ちゅうこく. It is not a reading of 中国 in any period; it is an index entry.

**`gikun` is not a defect marker, and treating it as one is the trap in the obvious implementation.** 明日 あした, 大人 おとな and 海豚 いるか are all tagged `gikun`, and all three are the ordinary current reading. The tag says the reading attaches to the word as a whole rather than character by character — the jukujikun fact `overview.md` flags as invalidating per-character reading display (D-06), and the thing whole-word furigana will need (D-14). It is orthogonal to currency: 15 readings carry `gikun` *and* `ok` together. So it is modelled as a separate flag, carried and not rendered in v1.

**Two consequences beyond hiding and marking**, both needed before V-21 reads as fixed:

- **A marked reading never leads a word.** Readings sort by status first, frequency second. 上手's じょうしゅ ties じょうず on frequency and beat it on kana order, so the screen *opened* on the obsolete reading.
- **A marked reading is never badged *common*.** `is_common` and `freq_rank` union the **writing's** priority tags into the reading's (V-04), so じょうしゅ inherits 上手's rank of 12. That is the right rule for ranking words against each other and the wrong thing to print beside a dead reading, so the badge is suppressed in the UI rather than the ingest changed.

*On the unknown-tag case:* the mapping returns "current" for a code it does not recognise, because a dictionary refresh must not break the app over a tag it has not met. That leniency is only safe because it is loud somewhere else — `verify.py`'s V-21 case asserts the built dictionary contains exactly these five codes, so a sixth fails the build instead of quietly rendering as ordinary. Failing there means deciding what the new code means, not widening the set.

*Cost:* the app now hides 6,647 rows of real dictionary data. Cheap to reverse — one filter, in one function — and the tags are still ingested either way, so nothing is lost from the database.

---

## Architecture

**D-60 — `:domain` is a plain Kotlin/JVM module, not an Android library. The no-`android.*` rule is enforced by the compiler, not by review.**

`architecture.md` has always required that `:domain` and `:data` import nothing from `android.*` — it is the line that makes a future iOS port a matter of moving files rather than a rewrite. It previously proposed enforcing that with "a lint rule or a CI grep", noting that a convention nobody checks will not survive.

There is a stronger option for `:domain`, and it costs nothing. A Gradle module's flavour determines its compile classpath: an Android library module has `android.jar` on it, and a plain Kotlin/JVM module does not. Declaring `:domain` as the latter means `import android.os.Bundle` is not a style violation to be caught later — it does not compile. The constraint stops being policy and becomes structure.

**`:data` does not get this**, because Room genuinely pulls in Android. That compromise is already accepted in `architecture.md`, and it is the reason a CI grep for `android.` is still required — just for one module instead of two.

*Second benefit, felt daily:* tests in an Android library module need an emulator or Robolectric; tests in a Kotlin/JVM module are ordinary JUnit and run in milliseconds. `:domain` holds FSRS, the use cases, and the tokenizer logic — most of the test suite worth having — so this moves the fast-feedback path to where the interesting code lives.

*Cost, accepted:* `:domain` can never use an Android type. Should one ever seem necessary, that is the signal the code belongs in `:data` or `:app`, not a reason to change the module flavour. Nothing in the planned domain layer — models, use cases, FSRS, repository interfaces, the `Tokenizer` interface (D-08) — needs one.

*On Kotlin Multiplatform:* still not being set up now, per `architecture.md`. A Kotlin/JVM module is, however, the shortest distance from here to a KMP module if it is ever wanted, because the dependency-hygiene work is already done.
