# Phase 7 — SRS review

**Status:** done. Word and kanji review on FSRS, "Review this list" with
practice, and a summary after every session — everything 1.0 asks of it (D-116).
**Updated:** 2026-10-21

## Current state

**Review works end to end.** Save a word and it is due at once; the Review tab
shows what is due (with a badge on the tab and counts on Saved), and a session
asks **one card per word** (D-107): the word, the photo it was saved from, a
typed **meaning** and a typed **reading**, one Submit. The app checks both —
loosely for meaning (any listed sense, small typos, the learner's own synonyms
via "I was right"), exactly for reading, which is typed as romaji and shown as
kana (D-108). Right or wrong goes to FSRS (D-113) as a successful or failed
recall; an append-only `review_log` is the truth and `srs_state` a cache of it
(D-112). Session rules are D-114; what was taken from the design project is
D-115.

**"Review this list" is built (D-117).** From a list's ⋯ menu on Saved, or
*Review* at the top of the list, the list's due words are reviewed as usual;
once none are due, its other words can be **practised**, checked the same way
but **never recorded**. Every session now ends on a summary: words, right first
time, missed, and when they come back.

**Kanji are reviewed (D-118).** A saved kanji gets a card labelled *Kanji*:
any one meaning and any one reading pass, a kun'yomi's bare stem included, and
the answer lists every reading with a word that uses it, the one typed marked
in jade.

**Not built:** the scan date on the card's photo (D-115 deferred it). The
**handwriting** queue (D-109) is after 1.0 (D-116).

## Next action

None. **Closed by the owner on 2026-10-21.** Work moves to Phase 8
(`phase-08-export-import.md`). What this phase leaves behind is named rather
than open: handwriting review (D-109) and a stricter kanji reading rule (D-118)
are after 1.0 in `roadmap.md`'s Deferred table, and the scan date on a card's
photo stays deferred by D-115.

**Deferred by the owner, 2026-10-14: the phone test.** Review on a real phone
waits for one device pass near the end, alongside V-30 (D-116). Nothing in
Phase 7 has been on a physical device yet.

### Where the code is

| Piece | File |
|---|---|
| FSRS (port of py-fsrs v6.3.2) | `domain/srs/Fsrs.kt` — `FsrsTest` |
| Answer checking, romaji → kana, card building, fuzz seed | `domain/review/` — `ReviewTest` |
| Review tables, queue queries, migration 2 → 3 | `data/user/SrsEntities.kt`, `SrsDao.kt` |
| Recording an answer (log, then FSRS, one transaction) | `data/user/RoomReviewRepository.kt` — `ReviewRepositoryTest` |
| The Review tab and session | `app/review/ReviewScreen.kt`, `ReviewViewModel.kt` |
| The kanji card: accepted readings, the reveal (D-118) | `domain/review/KanjiCard.kt` — `KanjiCardTest` |
| Review this list, practice, summary (D-117) | the same two files; route `list/{id}/review` in `app/nav/SpotterApp.kt`; list filter `IN_LIST` and `notDueItems` in `SrsDao.kt` |
| Due counts on Saved, the tab badge | `app/saved/SavedViewModel.kt`, `app/nav/SpotterApp.kt` (`minuteTicks`) |

## Done

- [x] **No Save for kana-only words (D-110, V-35)** — 2026-10-12. The rule is
      `containsKanji()` in `:domain`; the peek drops its Save button (tested on
      the tapped word, so it never flickers) and the word screen its ✚, with an
      empty square keeping the word centred. The word itself is still looked up
      and shown. `JapaneseTest` and `WordLookupViewModelTest` (9 of 9 on the
      emulator) cover it; seen on screen for トイレ, と and 生産.
- [x] **FSRS ported into `:domain`, free of `android.*` (D-26, D-113)** —
      2026-10-12. `domain/srs/Fsrs.kt`, a line-for-line port of py-fsrs v6.3.2:
      `review(card, rating, at, fuzzSeed)` and `replay(createdAt, answers)`, on
      epoch milliseconds. `FsrsTest` (25 cases) asserts the reference suite's own
      numbers. Two things the port taught: Python's `round()` is half-to-even, so
      `roundToInt()` would put intervals a day out; and py-fsrs's fuzz can land a
      day past its own range, so the port floors as ts-fsrs does. MIT notice in
      the file and in `assets/licenses/`.
- [x] **Schema v3 (D-112, V-36)** — 2026-10-12. `srs_state`, `review_log` and
      `user_synonym` (`data/user/SrsEntities.kt`), by `AutoMigration` 2 → 3 with
      `UserMigration2To3` giving every existing study item its REVIEW schedule;
      saving creates one in the same transaction and never replaces it.
      `3.json` committed; Room's generated migration read and confirmed to only
      create. Room 2.8 calls the **connection** form of `onPostMigrate` — the
      `SupportSQLiteDatabase` form is reached only through a bridge, so the spec
      overrides the former. Tests: `UserMigrationTest` 3/3 (both migration
      cases fail with the backfill removed), `UserDataTest` 28/28. A real v2
      emulator database with 先生 filed upgraded in place, checks clean.
- [x] Scheduling attached to the item, not to any list (D-29) — `srs_state` is
      keyed by (item, kind) and has no list column at all
- [x] **Review tab and the review session (D-107, D-108, D-114)** — 2026-10-12.
      The tab shows words due, all caught up, or nothing yet. The card is
      D-107's: word, photo, meaning and reading fields, one Submit, green and
      red borders, the answer beneath, Next, and "I was right" for a meaning
      marked wrong, which saves a synonym. Romaji becomes kana as it is typed;
      a reading of a different word gets a note and a retry. Answers are
      recorded at Next; a card still learning comes back before the session
      ends. Answer checking lives in `:domain/review` (`ReviewTest`, 18 cases);
      the queue and recording in `RoomReviewRepository`
      (`ReviewRepositoryTest`, 7 cases, including V-37's replay check).
      Driven on the emulator end to end: 先生 graduated to REVIEW due in two
      days, matching FSRS's reference sequence; 生産's override and synonym
      reached the database. Two faults found by looking and fixed: the theme's
      focus border is the same jade as "right", so a field looked graded while
      typing; and a trailing n stayed a letter in the field after Submit.
- [ ] **After 1.0 (D-116).** **Handwriting review — design artboard 2c, "Review — write it"** (D-72,
      D-109): **opt-in**, its own queue and button, its own `WRITING` schedule.
      A canvas the learner writes the word on, a *Clear* control, a **Check**
      button that reveals the answer beside their attempt, then **right or
      wrong** — self-graded, since there is no handwriting recognition (D-107
      retired the four grades the artboard draws). The prompt shows the reading
      and the meaning, as 2c does.
      This is the **blind** version, and it is what distinguishes it from the
      trace practice already built on the stroke order tab: there the ghost is
      visible the whole time and nothing is graded, because practice is a
      reference capability. Here the answer is hidden until Check, which is what
      makes a grade mean anything.
      Most of the drawing machinery exists — `StrokeOrder.kt` already parses the
      paths, scales them, captures gestures and hit-tests strokes.
- [x] **From the design project (D-115)** — 2026-10-12. Saved shows due and
      learned counts per list and in its header; the Review tab carries a due
      badge; "all caught up" gains a check, today's answer count and a
      *Coming up* forecast; the card reveals the reading in jade over the word
      and has a progress bar. "Review ahead" declined. Found while building:
      live due counts hold their start time, so a never-reviewed card counts
      as due regardless; and **rewriting the reading field on each keystroke
      lost letters on Gboard** — "gakusei" became がうせい — so the field now
      only *draws* romaji as kana (V-38). `ReviewRepositoryTest` 10/10.
- [x] Its `V-##` cases: **V-13** (one schedule per card kind, D-105), **V-35**
      (no Save for kana-only words), **V-36** (every study item has one schedule,
      kept on re-save), **V-37** (the stored schedule is a replay of the log),
      **V-38** (romaji through a composing keyboard keeps every letter)
- [x] **Kanji in review (D-118, V-40)** — 2026-10-15. `kanjiAnswers` turns
      KANJIDIC2's readings into what a learner types: markers dropped, on'yomi
      compared in hiragana, a kun'yomi as its stem or in full. The reveal reuses
      the Examples tab's reading groups, first word of each. `REVIEWABLE` no
      longer filters on type. `KanjiCardTest` 8/8, `ReviewRepositoryTest` 12/12.
      Driven on the emulator: 生 saved from its kanji screen, answered
      `life`/`い` then `birth`/`sei`, marked right both times, い and then セイ
      lit in the reveal, and the summary said it comes back Saturday. Seen while
      driving, and left alone because it comes from the kanji screen's own
      grouping: 生's kun'yomi show as う, うま and うまれ separately, and the
      example picked for セイ is 学生 rather than 先生.
- [x] **"Review this list", practice and a session summary (D-117, V-39)** —
      2026-10-15. Every review query takes an optional list (`IN_LIST`); practice
      draws the list's not-due words and writes nothing. Reached from the ⋯ menu
      on Saved (artboard 2d) and *Review* on the list screen (wireflow C5), on a
      route under the list so Saved stays lit. The summary (wireflow D4, not
      designed) serves every session. `ReviewRepositoryTest` 12/12. Driven on
      the emulator end to end: a list review with one miss gave "2 words · 1
      right first time · 1 missed" with a forecast; practice with a miss brought
      the miss back and left `review_log` and `srs_state` exactly as they were.
      Found while driving: せいざん for 生産 is a real word ("live birth"), so
      D-108's retry nudge fired — working as designed.

## Open questions

- ~~**Should this phase build an SRS at all, or export to Anki?**~~ — **resolved
  2026-08-28: both, scheduler first (D-79).** The reasoning is now in
  `decisions.md`; the short version is that the two audiences are different
  people. Serious learners run a stack with Anki at its centre and a new
  scheduler loses to it; the beginner `overview.md` targets does not run Anki
  and would be handed a dead end. So this phase proceeds exactly as D-26 and
  D-29 describe, and Anki export becomes a second format in Phase 8 (D-20).

- ~~**What goes on the back of a review card for a word with several
  senses?**~~ — **resolved 2026-10-11 (D-102):** the main sense leads and is
  what the learner grades; the other senses sit smaller beneath it.

- ~~**What is on the front of a card?**~~ — **resolved 2026-10-11:** the
  written form alone; the reading and main sense are both recalled (D-103);
  the photo is on the back (D-104). *Both superseded 2026-10-12 by D-107: one
  card with typed meaning and reading fields, photo on the question screen.*

- ~~**Does every saved word get a write card, or does the learner opt
  in?**~~ — **resolved 2026-10-12 (D-109):** opt-in, with its own queue and
  button. Still open, and **after 1.0** (D-116): *how* the learner opts in. A write card is drawn by hand on a canvas and self-graded after
  *Check* (artboard 2c, D-72) — there is no handwriting recognition. Its prompt
  needs the reading as well as the meaning, since "teacher" alone is 先生 or
  教師; confirm against the artboard.

- ~~**What does a kanji card's back show?**~~ — **resolved 2026-10-11
  (D-106):** its meanings in KANJIDIC2's order, cut to fit with a count of the
  rest. Kanji use D-107's card; still open: what a kanji's **reading** field
  accepts (生 has せい, しょう, い.きる, なま…). — **resolved 2026-10-15 (D-118):**
  any one reading, a kun'yomi's bare stem included, with every reading shown on
  the answer. To be revisited after 1.0.

- ~~**What does "Review this list" include?**~~ — **resolved 2026-10-15
  (D-117):** the list's due words, then optional practice over the rest that is
  never recorded.

- ~~**Where is the 1.0 cutoff?**~~ — **resolved 2026-10-15 (D-116):** the end
  of Phase 9, with Phases 7 and 8 complete except handwriting review.

- ~~**The `srs_state` / `review_log` schema**~~ — **approved 2026-10-12
  (D-112):** an append-only log is the truth, `srs_state` a cache keyed by
  (item, kind), times in UTC, and no undo in v1.

## Notes

- **Reading the Claude Design project**, as it worked on 2026-10-12: the
  `DesignSync` tool's read methods (`list_files`, `get_file`) need a one-time
  `/design-login` from an interactive `claude` terminal; the browser pane is a
  separate sign-in and does not help. `get_file` on `Kanji Lens.dc.html`
  (~137 KB) and `Kanji Lens Wireflow.dc.html` saves to a file; cut one artboard
  out by its `id` (balanced `<div>`s), wrap it with the Google Fonts link, and
  render it with headless Edge (`msedge --headless=new --screenshot`) — the
  browser pane will not screenshot a local file. Review lives in "E · Review
  card" (1m–1o), 2c, 2d, 4h, and wireflow lane D. **`/design-sync` is not a
  reader** — it uploads a code component library into a new design project.

- FSRS, not SM-2 or anything hand-rolled (D-26).
- Lists only filter; they never own scheduling (D-29). A word in three lists is
  still one schedule per card kind (D-105).
- Kanji are study items in v1 (D-92), get a REVIEW schedule like any word,
  and are in the queue since D-118. The card is built by `buildKanjiReviewCard`
  from `kanjiDetail`, the kanji screen's own data, not from a word lookup.

### Device and test knowledge from this phase

- **A wedged emulator** — installs failing with `Broken pipe (32)` from the
  package service — does not recover by waiting. `adb emu kill`, then boot with
  `-no-snapshot-load`.
- **Right after a cold boot the low-memory killer can kill the test process**
  (`Process crashed`, logcat says `lowmemorykiller`). Wait a minute and rerun;
  it is not the app.
- **Scripted typing through Gboard is unreliable for Enter.** `adb shell input
  keyevent 66` can be swallowed committing Gboard's composing text. Hide the
  keyboard and tap the on-screen Submit instead. Typing letter by letter
  (`input text g`, `input text a`, …) is what exposed V-38 — keep doing it for
  the reading field.
- **Background emulator tasks stop at the 2-hour limit**, which looks like the
  device vanishing. Start it again rather than debugging the app.
- **The scan sign for tests** is `app/src/androidTest/assets/sign-horizontal.png`,
  hung with `-virtualscene-poster wall=<path>` and reached with the
  `Walk_to_image_room` macro (`phase-04-camera.md`).
