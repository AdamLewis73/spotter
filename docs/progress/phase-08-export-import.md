# Phase 8 — Export / import

**Status:** not started
**Updated:** 2026-08-09

## Current state

Not started.

## Next action

Nothing yet — needs the full user-data schema settled through Phases 6 and 7.

## Done

- [ ] Versioned JSON/zip export of user data plus images
- [ ] Import, including the round trip on a different device
- [ ] Relevant `V-##` cases from `verification.md` added to this list

## Open questions

None recorded yet.

## Notes

- Manual export/import ships **before** any sync (D-20). It is the escape hatch
  that makes deferring accounts safe.
- Export is only cheap because D-15 (UUID keys), D-16 (`updated_at`, soft
  deletes) and D-19 (design for sync now) are followed from the first user-data
  write in Phase 2. Nothing here retrofits them.
- Export must not contain dictionary row IDs (D-11) — the natural key travels,
  the row ID does not.
