# Phase 6 — Saved lists

**Status:** in progress. Checkpoints settled, schema built, Save works.
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

**The user database exists and Save works.** `UserDatabase` v1 holds
`study_item`, `saved_list` and `list_membership`; its schema JSON is committed
(D-18). Eleven instrumented cases pass against real SQLite, and the flow was
driven on the emulator end to end: saving 先生 writes one row with a UUID key,
the full (text, reading) identity, the gloss that was on screen and the
`ent_seq` hint; unsaving leaves a tombstone with the row intact; re-saving
revives **that same id** with `created_at` untouched, still one row.

**D-81 came out of wiring the button**, and it is worth knowing about before
touching that screen. The peek deliberately shows no reading (D-47) while
identity requires one (D-12), so Save stores the top-ranked entry — the one
whose glosses are printed above the button. It is a toggle, and it is disabled
while the lookup is running or after it fails.

`ent_seq` had to be plumbed through `DictionaryEntry` to get there: the column
existed on the dictionary row but was never exposed to `:domain`, so nothing
could pass it. Captured per D-22's rule that cheap metadata is recorded now,
because a word whose dictionary entry has since moved cannot have it derived
afterwards.

**Also closed a standing gap:** D-17 bans `fallbackToDestructiveMigration()` in
every build type and nothing checked. CI greps for it now. The pattern requires
the leading dot so it matches calls rather than the four comments explaining why
it is banned — a check that fails on its own documentation gets deleted rather
than obeyed.

## Next action

**The Saved tab (D-36) — and it needs asking about before it is built.** Words
can now be saved and there is nowhere to see them, which is the gap to close
next. But D-36 specifies three bottom-nav destinations (Scan · Saved · Review)
and the app currently has none: D-73 made the camera the launcher destination
with no chrome around it, and `roadmap.md`'s deferred entry on user-facing
search says in as many words that *what the second screen of a scanner-first app
should be* is a D-61 question to ask rather than answer. Adding a nav bar is the
same question. Raise it before drawing one.

After that, in order:

1. Lists in the UI — create, rename, add a word to one. The repository and the
   join table already do all of it (D-28); nothing calls them yet.
2. The scan image saved alongside the word (D-21, D-24, D-25), which brings the
   `scan` and `scan_word` tables and D-22's bounding box with it. Added tables
   are a Room `AutoMigration`, and that bump is the moment to write the first
   `MigrationTestHelper` **chain** test — not before, because there is nothing
   yet to migrate from.
3. Whether the kanji screen's Save should work. It is drawn, and a lone kanji
   scanned on a beer tap goes straight there (D-49), so it is reachable — but
   D-01 scopes v1 study items to words. Decided, not overlooked; worth
   re-confirming rather than silently leaving a live-looking button inert.

## Done

- [x] Checkpoint: study-item identity `(text, reading)` plus the `type`
      discriminator (D-12, D-27) — confirmed 2026-08-28, unchanged
- [x] Checkpoint: built-in SRS or Anki export — **both** (D-79)
- [x] Checkpoint: which tables carry tombstones (D-80)
- [x] `:domain` models and repository interfaces
- [x] `UserDatabase` v1 in `:data`, schema JSON committed
- [x] Save writes a row — from the peek sheet and the word screen, as a
      toggle over the top-ranked entry (D-81); verified on the emulator
- [x] CI enforces the `fallbackToDestructiveMigration()` ban (D-17)
- [ ] Multiple user-named lists in the UI — the schema and repository are done
      (D-28), nothing calls them
- [ ] Scan image saved alongside the word (D-21, D-24, D-25)
- [ ] Bounding box stored on the scan record — D-22's obligation lands here,
      as a schema field; `ScanLayout.boxFor` already supplies the rectangle
- [ ] Saved tab in the bottom nav (D-36)
- [ ] Relevant `V-##` cases from `verification.md` added to this list

## Open questions

- **Does the app get a bottom nav bar, and is Saved the second destination?**
  D-36 says three destinations; D-73 gave the camera the screen with no chrome;
  D-61 says simplicity outranks features. Saved words are now unreachable, so
  this needs answering — see Next action.
- **Should the kanji screen's Save work?** D-01 scopes v1 to words, and a lone
  scanned kanji lands there (D-49). The button is drawn and inert.

The two that *gated* this phase are settled (D-79, D-80).

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
