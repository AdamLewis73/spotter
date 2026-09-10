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
(D-18). Thirteen instrumented cases pass against real SQLite, and the flow was
driven on the emulator end to end: saving 先生 writes one row with a UUID key,
the full (text, reading) identity, the gloss that was on screen and the
`ent_seq` hint; unsaving leaves a tombstone with the row intact; re-saving
revives **that same id**, still one row. *(That paragraph described the
behaviour on the day it was written; **D-82** later made a revive reset
`created_at`, so a re-saved word returns to the top of a newest-first list.)*

**D-81 came out of wiring the button**, and it is worth knowing about before
touching that screen. The peek deliberately shows no reading (D-47) while
identity requires one (D-12), so Save stores the top-ranked entry — the one
whose glosses are printed above the button. That part still stands.

*Its toggle behaviour does not* — **D-91** replaced it. The button no longer
reports saved state at all: it always offers to add, and opens the list picker.
The code still implements the toggle and has not caught up yet.

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

~~Make saved-ness mean what D-89 says it means.~~ **Done — 18 instrumented
cases, including an unfiled word reporting itself unsaved, a word in two lists
appearing once, and a refiled word coming back with the same id and
`created_at`.**

~~The Saved screen~~ **Done.** The app shell is built: Navigation Compose,
three destinations, and the app's own bar on all of them including the camera
(D-90). Saved lists the user's lists with word counts and creates, renames and
deletes them; tapping one opens its words. Review is an honest placeholder
saying it is not built yet, because an empty queue and an unbuilt one look
identical and mean opposite things.

**The list picker is next**, and it is what makes any of the above reachable —
until it exists nothing can be filed, so every screen shows its empty state.
D-88 and D-91 specify it: a centred multi-select overlay that stages its
choices, writes only on *Add*, and offers *create a new list* at the top. Its
empty state is a first-run screen in disguise — on a new install it is the only
way to save anything at all.

**Then D-92 turns on the kanji screen's Save**, through the same picker.
`StudyItemKey` already accepts `(character, "", KANJI)`, so that is wiring
rather than schema work.

Still owed after those: **swipe-to-remove** on the list screen, which is the
only path that unfiles a word (D-89) and belongs beside the picker that files
them; and **D-86**, moving typing a word off the camera and into Saved.

## Done

- [x] Checkpoint: study-item identity `(text, reading)` plus the `type`
      discriminator (D-12, D-27) — confirmed 2026-08-28, unchanged
- [x] Checkpoint: built-in SRS or Anki export — **both** (D-79)
- [x] Checkpoint: which tables carry tombstones (D-80)
- [x] `:domain` models and repository interfaces
- [x] `UserDatabase` v1 in `:data`, schema JSON committed
- [x] Save writes a row — from the peek sheet and the word screen, over the
      top-ranked entry (D-81); verified on the emulator. *Built as a toggle;
      D-91 has since replaced that with an always-add button and a picker, and
      the code has not caught up*
- [x] CI enforces the `fallbackToDestructiveMigration()` ban (D-17)
- [x] Saved means **filed** — `observeIsSaved`, `observeSaved` and `find`
      require a live list membership (D-88, D-89); 18 instrumented cases
- [x] The app shell — Navigation Compose, three destinations, the bar on all
      of them including the camera (D-36, D-90); back exits from Scan
- [x] Multiple user-named lists in the UI — create, rename, delete, and open
      one to see its words (D-28)
- [x] Review placeholder, so the third tab says something true until Phase 7
- [ ] The list picker (D-88, D-91) — **nothing can be filed until this exists**
- [ ] Swipe to remove a word from a list — the only path that unfiles (D-89)
- [ ] The kanji screen's Save, through the same picker (D-92)
- [ ] Typing a word moves off the camera into Saved (D-86)
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

~~**D-88 and D-89 change code that is already written and merged.**~~ **Done.**
`observeIsSaved`, `observeSaved` and `find` now require a live
`list_membership`, so a word that has been taken out of its last list keeps its
row and its whole review history and simply stops appearing. `unsave` is the one
query that still ignores filing, deliberately — an unfiled word is invisible,
not absent, and must stay deletable.

The saved-list query uses `EXISTS` rather than a join: a join returns one row per
membership, so a word filed in three lists would be drawn three times on the
Saved screen, looking like duplicated data rather than a duplicated row.

**Interim consequence, and it is correct rather than broken:** `save` alone
creates the word without filing it, so until the picker exists the save control
does nothing visible. The button used to flip to a tick while the word appeared
in no list anywhere; it now honestly reports that the word is unfiled.

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
