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

**Make saved-ness mean what D-89 says it means**, before anything is drawn on top
of the old meaning: join `list_membership` in `observeIsSaved` and
`observeSaved`, and add instrumented cases for an unfiled word — it must report
itself unsaved, stay out of the Saved list, and come back with its history when
re-filed.

**Then the Saved screen and the list picker**, which D-88, D-90 and D-91 now
specify well enough to build: the nav bar on all three destinations, and a
centred multi-select picker that stages its choices, writes only on *Add*, and
offers *create a new list* at the top. Note the picker's empty state is a
first-run screen in disguise — on a new install it is the only way to save
anything at all.

**And D-92 turns on the kanji screen's Save**, which routes through the same
picker. `StudyItemKey` already accepts `(character, "", KANJI)`, so this is
wiring rather than schema work.

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

## Settled while wiring Save, 2026-08-28

- **D-82** — re-saving a *deleted* word resets `created_at`, so it returns to the
  top of a newest-first Saved list where the user will look for it; re-saving one
  that is already saved changes nothing. The row **id** survives either way,
  which is the part Phase 7's review history depends on.
- **D-83** — a word's scan history outlives unsaving. Photos belong to the word,
  not to the save, so saving 上手 again a year later still shows every place it
  has been photographed. This also makes deliberate something `scan_word` would
  otherwise have done by accident, since it cascades from a parent that is only
  ever soft-deleted.

**Found here, owned elsewhere:** the leading reading of a word can be wrong —
一人 displays as いちにん rather than ひとり — because `freq_rank` unions the
writing's priority into every reading. Recorded as **V-29** with the expected
values and **D-84** with the fix and two rejected alternatives. Not fixed on this
branch: it needs a dictionary rebuild and a `SCHEMA_VERSION` bump, which do not
belong beside a user-data schema.

## Settled from the wireflow, 2026-09-01

The project owner built a six-lane wireflow in Claude Design and it was read
against the record. Five decisions came out of it, and one piece of built code
now needs changing.

- ~~**D-85**~~ — **superseded within the day by D-90**: the bottom nav is drawn on
  **all three** destinations, camera included, with no back control on the
  camera and system back exiting the app. D-85 had kept the bar off the
  viewfinder, and it fell to two things: a left-edge swipe *is* the system back
  gesture on gesture navigation, so its exit gesture would have lost to the OS;
  and its central argument was a sentence in `ux.md` that no decision had ever
  ratified. Worth remembering — **prose in a reference doc is not a decision.**
- **D-86** — typing a word moves off the camera and over to Saved, which is also
  where `roadmap.md`'s deferred *user-facing search* entry expected it to land.
  It stays as recovery in the camera-blocked path.
- **D-87** — a first-run sequence: explain, then ask for the camera. Placeholder
  design. The wireflow's dictionary-download node is dropped; the dictionary
  ships inside the app and there is no network on first run.
- **D-88** — **every saved word must be filed in at least one list.** Save opens
  a centred multi-select picker that can create a list inline.
- **D-89** — unfiling a word keeps it and its review history, and hides it from
  lists and review until it is filed again.

**D-88 and D-89 change code that is already written and merged.** `observeIsSaved`
and `observeSaved` currently mean *the `study_item` row exists*; they must come
to mean *the row exists and has at least one live `list_membership`*. Left as is,
an unfiled word reports itself as saved and shows a filled button with nothing
behind it. This is the first thing to do on the Saved screen, before any of it is
drawn against the old meaning.

Three more followed once the wireflow had been read twice:

- **D-90** — the nav bar is on all three destinations; system back exits the app.
- **D-91** — the list picker **stages** its choices: nothing is written until
  *Add*, it only ever adds, and re-adding to a list that already holds the word
  attaches the current scan's photo. This supersedes D-81's toggle — the button
  no longer reports saved state at all, because a word can be in some lists and
  not others and there is no single truth to show.
- **D-92** — **kanji are study items in v1**, superseding D-01. D-49 sends a
  scanned lone kanji straight to the kanji screen, so deferring this meant a
  Save button that could never work. Near-free: D-27's `type` discriminator has
  been in the schema since version 1 for exactly this.

Handwriting-in-review is left to Phase 7 (D-72 untouched), and a Profile screen
will eventually host storage, attribution and export — neither is settled here.

## Open questions

None outstanding. Both that were open yesterday are now decided: the system back
gesture exits the app (D-90), and the kanji screen's Save works, because kanji
are study items in v1 (D-92).

The two that *gated* this phase remain settled (D-79, D-80).

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
