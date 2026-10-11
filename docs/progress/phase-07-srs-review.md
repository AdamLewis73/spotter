# Phase 7 — SRS review

**Status:** not started — **unblocked**, Phase 6 closed 2026-09-22
**Updated:** 2026-10-11

## Current state

No code yet. The first design question is settled: a word card's back leads
with the main sense and is graded on it, with the other senses smaller beneath
(D-102).

## Next action

**Settle the card's front, then the schema.** The front decides what is being
recalled — meaning, reading, or both — and so what a grade means. Then
`srs_state` and `review_log`: the schema is the phase's checkpoint, because once
learners have review history its shape can only change by migrating it. Bring a
draft to the owner before writing the entities.

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

- **What is on the front of a card?** The written form alone, or with the
  scan photo — principle 6 calls the photo a memory hook, but it may give the
  answer away. And is the reading recalled, shown, or part of the back?

- **What does a kanji card's back show?** D-92 makes kanji study items, and
  KANJIDIC2 gives a kanji a list of meanings rather than senses, so D-102 does
  not carry over directly.

- **The `srs_state` / `review_log` schema** — a checkpoint (see Next action).

## Notes

- FSRS, not SM-2 or anything hand-rolled (D-26).
- Lists only filter; they never own scheduling (D-29). A word in three lists is
  still one schedule.
- Kanji are study items in v1 (D-92, superseding D-01), so review has to handle
  `KANJI` cards as well as `WORD` ones. The kanji screen's Save already files
  them, keyed `(character, "", KANJI)`.
- The Review tab itself already exists (D-36, D-90) as a placeholder screen;
  what is unbuilt is everything behind it.
