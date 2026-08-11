# Phase 6 — Saved lists

**Status:** not started
**Updated:** 2026-08-09

## Current state

Not started.

## Next action

Nothing yet. Note that Phase 2 hits the user-data checkpoints first, when the
Save button writes its first row — this phase builds on that schema rather than
introducing it.

## Done

- [ ] Checkpoint: study-item identity `(text, reading)` plus the `type`
      discriminator (D-12, D-27)
- [ ] Multiple user-named lists, many-to-many via a join table (D-28)
- [ ] Scan image saved alongside the word (D-21, D-24, D-25)
- [ ] Saved tab in the bottom nav (D-36)
- [ ] Relevant `V-##` cases from `verification.md` added to this list

## Open questions

None recorded yet.

## Notes

- Identity is **(text, reading)**, never text alone — 上手 is three different
  words (D-12). All review history is keyed to this, which is why it is a
  checkpoint.
- Never store dictionary row IDs in user data (D-11). Rebuilds reassign them and
  corrupt saved words with no error.
- Images are files on disk with **relative** paths in the DB (D-24), never BLOBs.
- Scan history and saved-word images have separate lifecycles (D-25).
- Smart/auto lists (by JLPT level, shared kanji, scan date) are deferred but
  fall out of D-28's join table cheaply.
