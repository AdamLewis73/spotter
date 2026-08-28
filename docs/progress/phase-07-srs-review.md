# Phase 7 — SRS review

**Status:** not started
**Updated:** 2026-08-28

## Current state

Not started.

## Next action

Nothing yet — needs Phase 6's saved items to schedule.

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

- **What goes on the back of a review card for a word with several senses?**
  甘い is "sweet; sugary; mild; naive; lenient" — all of it, the primary sense
  only, or something the user chooses? This is a flashcard design question, not
  a data or scanning one (D-44), and it is the last unresolved part of the
  sense-disambiguation discussion.

## Notes

- FSRS, not SM-2 or anything hand-rolled (D-26).
- Lists only filter; they never own scheduling (D-29). A word in three lists is
  still one schedule.
- Kanji-only study items are deferred, but D-27's `type` discriminator is in the
  schema from day one so adding them costs near zero.
