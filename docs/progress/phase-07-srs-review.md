# Phase 7 — SRS review

**Status:** not started — **unblocked**, Phase 6 closed 2026-09-22
**Updated:** 2026-10-12

## Current state

No code yet. The card is settled (D-107), after a clickable prototype of two
candidates (the *Spotter Review Cards* artifact): **one card per word** — title,
the word, the scan photo, a meaning field and a reading field — with one
schedule, passing only when both answers are right. Answers are typed and
checked by the app, right or wrong, never Again/Hard/Good/Easy. Meanings match
loosely against any listed sense, and "I was right" saves the learner's own
synonym; readings are typed as romaji and converted to kana (D-108).
Handwriting is a separate, opt-in queue (D-109). Kana-only words cannot be
saved in v1 (D-110).

## Next action

**The owner tries the review screen on a device**, and its learning steps
(1 and 10 minutes, D-113) are judged in use. Then: compare the card with the
Claude Design project's review artboards, which were not consulted (D-114);
decide what a kanji card's reading accepts so kanji join the queue (D-106);
and the opt-in handwriting queue (D-109).

What Phase 6 leaves ready: `study_item` exists with `(text, reading, type)`
identity, saved words are the filed ones (D-88, D-89), and `srs_state` hangs off
the item rather than off a list, which is what V-13 protects. `srs_state` and
`review_log` are new tables, so they arrive as a Room `AutoMigration` from
`UserDatabase` v2 — `phase-06-saved-lists.md` records how the first one was
written and tested.

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
- [ ] **Handwriting review — design artboard 2c, "Review — write it"** (D-72): a
      canvas the learner writes the word on, a *Clear* control, a **Check**
      button that reveals the answer beside their attempt, then the four grades.
      This is the **blind** version, and it is what distinguishes it from the
      trace practice already built on the stroke order tab: there the ghost is
      visible the whole time and nothing is graded, because practice is a
      reference capability. Here the answer is hidden until Check, which is what
      makes a grade mean anything.
      Most of the drawing machinery exists — `StrokeOrder.kt` already parses the
      paths, scales them, captures gestures and hit-tests strokes.
- [ ] Relevant `V-##` cases from `verification.md` added to this list

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
  button. Still open: *how* the learner opts in. A write card is drawn by hand on a canvas and self-graded after
  *Check* (artboard 2c, D-72) — there is no handwriting recognition. Its prompt
  needs the reading as well as the meaning, since "teacher" alone is 先生 or
  教師; confirm against the artboard.

- ~~**What does a kanji card's back show?**~~ — **resolved 2026-10-11
  (D-106):** its meanings in KANJIDIC2's order, cut to fit with a count of the
  rest. Kanji use D-107's card; still open: what a kanji's **reading** field
  accepts (生 has せい, しょう, い.きる, なま…). Deferred by the owner.

- ~~**The `srs_state` / `review_log` schema**~~ — **approved 2026-10-12
  (D-112):** an append-only log is the truth, `srs_state` a cache keyed by
  (item, kind), times in UTC, and no undo in v1.

## Notes

- FSRS, not SM-2 or anything hand-rolled (D-26).
- Lists only filter; they never own scheduling (D-29). A word in three lists is
  still one schedule per card kind (D-105).
- Kanji are study items in v1 (D-92, superseding D-01), so review has to handle
  `KANJI` cards as well as `WORD` ones. The kanji screen's Save already files
  them, keyed `(character, "", KANJI)`.
- The Review tab itself already exists (D-36, D-90) as a placeholder screen;
  what is unbuilt is everything behind it.
