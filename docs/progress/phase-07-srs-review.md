# Phase 7 — SRS review

**Status:** not started — **unblocked**, Phase 6 closed 2026-09-22
**Updated:** 2026-10-11

## Current state

No code yet. The card is settled: the front is the written form alone; the
back gives the reading, then the main sense with the others smaller beneath it,
and the scan photo; a pass means recalling the reading and the main sense
(D-102, D-103, D-104). Reading and writing a word keep separate schedules, so
`srs_state` is keyed by item and card kind (D-105).

## Next action

**Draft the `srs_state` and `review_log` schema and bring it to the owner
before writing any entity.** It is the phase's checkpoint: once learners have
review history its shape can only change by migrating it. D-105 fixes the key —
item and card kind.

What Phase 6 leaves ready: `study_item` exists with `(text, reading, type)`
identity, saved words are the filed ones (D-88, D-89), and `srs_state` hangs off
the item rather than off a list, which is what V-13 protects. `srs_state` and
`review_log` are new tables, so they arrive as a Room `AutoMigration` from
`UserDatabase` v2 — `phase-06-saved-lists.md` records how the first one was
written and tested.

## Done

- [ ] FSRS ported into `:domain`, free of `android.*` (D-26)
- [ ] Scheduling attached to the item, not to any list (D-29)
- [ ] Review tab in the bottom nav (D-36)
- [ ] Quiz flow over due items
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
  the photo is on the back (D-104).

- **Does every saved word get a write card, or does the learner opt in?**
  D-105 gives writing its own schedule; whether it exists for every word is
  open.

- **What does a kanji card's back show?** D-92 makes kanji study items, and
  KANJIDIC2 gives a kanji a list of meanings rather than senses, so D-102 does
  not carry over directly.

- **The `srs_state` / `review_log` schema** — a checkpoint (see Next action).

## Notes

- FSRS, not SM-2 or anything hand-rolled (D-26).
- Lists only filter; they never own scheduling (D-29). A word in three lists is
  still one schedule per card kind (D-105).
- Kanji are study items in v1 (D-92, superseding D-01), so review has to handle
  `KANJI` cards as well as `WORD` ones. The kanji screen's Save already files
  them, keyed `(character, "", KANJI)`.
- The Review tab itself already exists (D-36, D-90) as a placeholder screen;
  what is unbuilt is everything behind it.
