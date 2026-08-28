# Phase 6 — Saved lists

**Status:** in progress. Both entry checkpoints are settled; the schema is next.
**Updated:** 2026-08-28

## Current state

**The two checkpoints that gate this phase are discharged.**

**D-79 — the app schedules reviews itself *and* exports to Anki, scheduler
first.** This had to be answered here rather than in Phase 7, because an
export-only app needs no `srs_state` and no `review_log` and this phase writes
the table they hang off. Phase 7 is therefore unchanged, and Phase 8 gains a
second export format beside D-20's JSON/zip.

**D-80 — which tables carry tombstones.** The draft schema in `data-model.md`
gave `list_membership` neither `updated_at` nor `deleted_at`, which is a real
fault rather than an omission: *remove this word from Street Signs* is the
deletion users perform most often, and a hard-deleted join row means a restored
backup or a second device silently puts the word back. Soft delete now applies
to the four tables the user deletes from; `srs_state` and `scan_word` cascade
with their parents; `updated_at` is universal.

The standing user-data decisions were reviewed with the owner rather than
assumed: UUID keys (D-15), `updated_at` and soft delete (D-16), no
`fallbackToDestructiveMigration()` (D-17), schema export on and committed
(D-18), `snapshot_gloss` on `study_item` (D-43). All stand as written.

**Nothing is built yet.** There is exactly one Room database in the tree today,
`DictionaryDatabase`, and it is read-only and never migrated (D-38) — so none
of the migration machinery this phase needs exists or has been exercised. The
Save button is drawn and disabled at `app/.../scan/ScanSheet.kt:199`.

## Next action

Build the user database, in this order:

1. **`:domain`** — the models and repository interfaces, free of `android.*`
   (D-60), so identity logic is testable in milliseconds rather than on an
   emulator. `(text, reading, type)` is the identity (D-12, D-27).
2. **`:data`** — Room entities, DAOs and `UserDatabase` at version 1, with
   `exportSchema` on and the JSON committed (D-18). Tombstone filtering
   (`deleted_at IS NULL`) belongs in the DAO queries, not in callers.
3. **`:app`** — enable Save on the peek sheet, then the Saved tab (D-36).

Write the first `MigrationTestHelper` chain test as soon as there is a version 2
to migrate to, not before — but keep the schema JSON committed from version 1 or
there is no ground truth to migrate *from*.

## Done

- [x] Checkpoint: study-item identity `(text, reading)` plus the `type`
      discriminator (D-12, D-27) — confirmed 2026-08-28, unchanged
- [x] Checkpoint: built-in SRS or Anki export — **both** (D-79)
- [x] Checkpoint: which tables carry tombstones (D-80)
- [ ] `:domain` models and repository interfaces
- [ ] `UserDatabase` v1 in `:data`, schema JSON committed
- [ ] Save writes a row from the peek sheet
- [ ] Multiple user-named lists, many-to-many via a join table (D-28)
- [ ] Scan image saved alongside the word (D-21, D-24, D-25)
- [ ] Bounding box stored on the scan record — D-22's obligation lands here,
      as a schema field; `ScanLayout.boxFor` already supplies the rectangle
- [ ] Saved tab in the bottom nav (D-36)
- [ ] Relevant `V-##` cases from `verification.md` added to this list

## Open questions

None. Both that gated this phase are now decisions (D-79, D-80).

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
- `srs_state` and `review_log` are **Phase 7 tables**, not Phase 6 ones — an
  added table is a Room `AutoMigration`. But nothing built here may assume they
  will never exist (D-79).
