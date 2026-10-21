# Phase 8 — Export / import

**Status:** not started — next, now that Phase 7 is closed
**Updated:** 2026-10-21

## Current state

Not started.

## Next action

**Agree the backup file's shape with the owner before writing it.** The schema
it serializes is settled (`UserDatabase` v3, Phases 6 and 7), so nothing blocks
the phase. But the file format is a checkpoint: once learners hold backup files,
every later version of the app has to read them.

## Done

- [ ] Versioned JSON/zip export of user data (photos: see *Open questions*)
- [ ] Import, including the round trip on a different device
- [ ] Anki export: one note per word per list, front and back, no review
      history (D-79, D-111)
- [ ] Relevant `V-##` cases from `verification.md` added to this list

## Open questions

- **Do photos go in the backup file?** This stub's original line said "plus
  images". D-94 keeps photos out of Android's *automatic* backup because of its
  25 MB limit, which is a different mechanism with a different constraint. A
  file the learner exports by hand has no such limit, but full-size photos make
  it large. Not decided.

## Notes

- Manual export/import ships **before** any sync (D-20). It is the escape hatch
  that makes deferring accounts safe.
- Export is only cheap because D-15 (UUID keys), D-16 (`updated_at`, soft
  deletes) and D-19 (design for sync now) are followed from the first user-data
  write in Phase 2. Nothing here retrofits them.
- Export must not contain dictionary row IDs (D-11) — the natural key travels,
  the row ID does not.
