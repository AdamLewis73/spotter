# Phase 6 — Saved lists

**Status:** done. Schema v2, Save through the list picker, the Saved and list
screens, remove-with-undo, scan photos, and search from inside a list.
**Updated:** 2026-09-22

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

**Phase 7 — FSRS review.** Nothing here blocks it: `srs_state` hangs off
`study_item` rather than off `list_membership` (V-13), so one word in three
lists has one schedule, and `study_item` is the table the scheduler needs.
Phase 7 opens with a question already recorded in `roadmap.md` — what goes on
the **back of a review card** for a word with several senses.

**Two things this phase leaves owed, both named on purpose rather than closed
by assertion:**

- **V-30's one real sign, on a real phone.** The instrumented cases use a
  synthetic photo, which catches a systematic shift but cannot catch a camera
  that hands over frames in an orientation the emulator never produces. It
  needs the project owner and a camera; nothing in a repository can discharge
  it.
- **D-86's recovery path** — *Type a word* when camera permission is denied.
  Deferred by the owner on 2026-09-16, and it is one entry point into a screen
  that now exists (D-96), so it stays cheap.

### The work, in the order it landed


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

~~The list picker is next~~ **Done, and the loop closes.** Saving now opens a
centred overlay that stages its choices and writes nothing until *Add* (D-91),
with *create a new list* at the top — which on a new install is the only route
to saving anything at all. Lists already holding the word say so and are not
offered as a way to take it back out. Driven end to end on the emulator:
save 先生 → create "Street Signs" inline → Add → one list, one `study_item`
with the right identity and gloss, one membership, and the word visible in
Saved.

**D-92 is live too** — the kanji screen's Save opens the same picker, filing
`(character, "", KANJI)`.

**The picker has no artboard.** The wireflow marks C2 *"NOT DESIGNED — which
list, or a new one. Not on the canvas yet."*, so it follows the project owner's
spec from conversation rather than a drawing. Worth knowing before redesigning
it: nothing was inferred from an artboard, because there is none.

**Removing a word from a list is built (D-93).** Hold a row, a red panel
overlays its right edge, confirm, then three seconds to undo. Removal never
resets progress: FSRS already weights a returning word by the time elapsed, so
the owner's instinct to reset or decay progress is something FSRS does properly
for free. Driven on the emulator both ways — 先生 removed from its only list
stays live with the same id and becomes unfiled; 出口 removed and undone comes
back with its *Street Signs* membership revived and its *Food Menu* one
untouched.

Two off-design defaults surfaced and were fixed, both the same failure: Material
filling a colour role this project never set. The removal red needed a defined
`error` role (the design's #E54C4A was darkened to #D33A3C so its white label
passes AA, following the `JadeLight` precedent), and the undo message was
Material's lavender and purple until the `inverse*` roles were defined.

~~Still owed: **D-86**, moving typing a word off the camera into Saved.~~ **Done, see below (D-96).**
~~The scan image work~~ **Done — see below.**

**Where a returning word goes, settled (D-93):** Undo puts it back exactly where
it was; re-filing through the picker puts it at the top, by the reasoning D-82
applied to the Saved list. Two methods, `restoreToList` and `addToList`, so each
call site says which it means. The ordering tests use a clock that steps a
second per reading — with the real clock, adds in the same millisecond tie and
the test passes or fails by luck — and the re-filing test was confirmed to fail
when the old behaviour was put back.

**Scan photos are built (D-94, D-95), with the first real migration.** Filing a
word from a scan keeps the photo it came from. The owner's rules, which
superseded D-21's resizing and D-25's scan history:

- **A photo is kept only when a word from it is filed** — not at the shutter,
  not temporarily. Nothing touches the disk until *Add*.
- **One photo per shutter press**, shared by every word filed from it. The
  second word from the same frame links to the first word's `scan` row.
- **Stored at the size it was taken, WebP q80, never resized.** The word boxes
  are measured on that photo, so they stay right for the file on disk only if
  nothing changes its pixel grid. Its width and height are not stored; the file
  header has them.
- **The thumbnail is drawn, not stored.** The list decodes only the square
  around the word from the one file. There is never a second image.
- **Photos are never backed up** — only the database is. A restored phone has
  every word and list without their photos, which draws exactly like a typed
  word with none. A word without a photo is always a normal state.

`UserDatabase` is at **v2**: `scan` and `scan_word` arrive by `AutoMigration`,
and the generated migration was read to confirm it only creates — two tables,
three indexes, nothing touching v1's rows. `MigrationTestHelper` proves a v1
database with a filed word, a list and a tombstone comes through intact, and a
second case opens every past version in the real app build without a fallback.
Both fail when the migration is removed.

D-23's `image_type` column was **not built**: `WORD_CROP` never exists under
D-95, and the column can be added later if a second kind of image ever does.

**The camera itself could not be driven on the emulator** — its virtual scene
has no Japanese in it, so recognition has nothing to read. Everything after the
shutter is tested on the device instead, from a synthetic photo: grey, with one
red block where the "word" is, so "did the thumbnail land on the word?" is a
pixel check rather than a judgement. Nine cases, and the two that matter were
confirmed to fail when the save step was made to halve the photo — the size
check directly, and the thumbnail check because the crop then lands on grey.
The decoder's fallback path was run on its own and crops correctly too. **Still
worth one real sign on a real phone** before calling it seen.

**A word already in every list cannot take a new photo.** Photos attach when
*Add* files the word somewhere, and lists already holding it are locked in the
picker, so a second sighting of a word filed everywhere has no route to its
photo except filing it into a new list. Consistent with the picker as decided;
flagged in case a second photo of a known word turns out to matter.

**Search from inside a list is built (D-86, D-96).** There is no artboard; the
owner specified it on 2026-09-16. Saved → a list → *Add a word from search* at
the top → a text field, with nothing below it until something is typed. Results
are dictionary words **beginning with** the text, one row per written form,
exact match first and then commonest first. Each row shows reading, word and
gloss. The reading is the one the word screen leads with, so the row and the
screen behind it agree. When nothing begins with the text, as with a pasted
sentence, the words found inside it are listed instead, and the screen says so.
Tapping a row opens the scan's own word screen with the word **whole**, and
Save opens the picker with the list you came from already ticked. The tick is
staged, never written, until *Add* (D-91).

- **No dictionary rebuild.** The prefix is a range query on the existing
  `UNIQUE (text, reading)` index. `LIKE 'x%'` would not use that index, and
  would scan every row on every keystroke.
- **Written forms only.** Typing せんせい does not find 先生. That needs the
  reading index `schema.sql` dropped (8.3 MB). Recorded in `roadmap.md`'s
  deferred table as *Search by reading*.
- **The keyboard comes up once**, on arrival. Coming back from a word does not
  raise it again, because then the next back press would only close the
  keyboard instead of leaving.
- **The camera's debug search button and the unused Phase 4 recognized-text
  strip are gone.** The `query` intent extra still opens the Phase 2 screen, so
  `/inspect` is unaffected.

**Tests: 12 instrumented cases.** Eleven cover the dictionary queries and both
ViewModels. One drives the whole path in the real activity, because
`adb shell input text` cannot type Japanese: that is the only way to put 先生 in
the field by script. Two cases were confirmed to fail when their behaviour was
removed: the pre-ticked list, and the row's reading. The reading case uses 孝,
whose raw first reading is the obsolete きょう. The obvious example, 上手, stopped
showing the problem once D-84 fixed its query order, so a test on 上手 passed
with the rule deleted.

**Open, for the owner: how does a learner type a kanji they cannot read?** The
phone's Japanese keyboard (Gboard) converts romaji and kana into kanji and has
handwriting input, so a learner can already type a word they can *say*. The
harder case is a word they can see but not say. The owner wants to brainstorm
this separately. Reading search is one piece of it, and is cheap to add.

**Gotcha, emulator:** it can report `sys.boot_completed=1` and still be half
up — every install then fails with `Failed to install split APK(s)`, and logcat
says *"Cannot access system provider: 'settings' before system providers are
installed"*. Nothing is wrong with the build. Kill the emulator and relaunch it
with `-no-snapshot-load -gpu swiftshader_indirect`, then wait for **both**
`sys.boot_completed=1` and `pm path android` to answer before running anything.

**Gotcha for the next migration test:** `room-testing` needs
kotlinx-serialization 1.8, and the app's own libraries pinned the test
classpath to 1.7.3, failing at runtime with an `AbstractMethodError` rather than
at build time. A dependency constraint in `app/build.gradle.kts` holds it at
1.8.1; the comment in `libs.versions.toml` explains why it is a floor.

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
- [x] The list picker (D-88, D-91) — staged, multi-select, creates a list
      inline, writes only on *Add*
- [x] The kanji screen's Save, through the same picker (D-92)
- [x] Remove a word from a list — hold, confirm, three-second undo (D-93);
      never resets progress
- [x] Typing a word moves off the camera into Saved (D-86): *Add a word from
      search* inside a list (D-96); the camera's debug search button is gone
- [ ] D-86's recovery path, *Type a word* when camera permission is denied —
      **deferred by the owner on 2026-09-16**; it will open the same screen
- [x] Scan image saved alongside the word (D-24, D-94) — once per shutter
      press, only when a word is filed, never resized; D-94 superseded D-21's
      resizing and D-25's scan history
- [x] Bounding box stored on the scan record (D-22) — `scan_word.bbox_*`, in
      the saved photo's own pixels, from `ScanLayout.boxFor`
- [x] Thumbnails in the list, drawn from the one photo (D-95)
- [x] `UserDatabase` v2 by `AutoMigration`, with `MigrationTestHelper` cases
- [x] Backup covers the database only (D-94)
- [x] Saved tab in the bottom nav (D-36) — landed with the app shell
- [x] The `V-##` cases this phase owns, named below

## The verification cases this phase owns

Five, and three of them were written or corrected here. Each is a failure that
produces a normal-looking screen with no error — which is the only thing that
belongs in `verification.md`.

| Case | What it protects | State |
|---|---|---|
| **V-14** | every `study_item` carries an explicit `type` (D-27) | **Met** — 先生 saves as `WORD`, 生 from the kanji screen as a separate `KANJI` row, both covered by instrumented cases |
| **V-30** | a saved photo still fits the boxes stored beside it (D-94, D-95, D-22) | **Met in the parts a machine can check**; the real-sign check is still owed |
| **V-13** | one schedule per item across lists (D-29) | **Not this phase's to meet.** `srs_state` is Phase 7. What Phase 6 owes it is the shape that makes it possible, and that holds: `srs_state` hangs off `study_item`, not off `list_membership`, and the Saved query uses `EXISTS` so a word in three lists is one row on screen |
| **V-31** | a search row shows the reading its word screen leads with (D-96, V-21) | **Met** — instrumented on 孝, and confirmed to fail when the rule is removed |
| **V-20** | an orphaned saved word still renders and stays reviewable (D-40, D-43) | **Half met.** `snapshot_gloss` is written at save time and the list renders from the saved row, so the card survives a dictionary that has dropped the word. The *reviewable* half is Phase 7's, and is the half most likely to be missed |

**V-14 was stale and is corrected.** It said every v1 row is `WORD`, which D-92
made false the moment kanji became study items — and read literally it licensed
exactly the assumption D-27 exists to forbid. Corrected in place rather than in a
closed phase's file, because Phase 6 is open and owns it.

**V-30 is new, and it is the one to keep in mind.** A word's rectangle is stored
in its photo's own pixels, so if anything ever resizes or re-encodes a photo on
the way to disk, every thumbnail quietly points at the wrong part of the sign:
no crash, no error, just wrong pictures. The instrumented cases catch a
systematic shift; **one real photograph of a real sign** is what would catch a
phone whose camera hands over frames in an orientation the emulator never
produces.

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

- **How learners type kanji they cannot read** (D-96). The owner wants to
  brainstorm it. It does not block close-out.

None blocking. Both that were open yesterday are now decided: the system back
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
- ~~Scan history and saved-word images have separate lifecycles (D-25).~~ There
  is no scan history: a photo exists only because a word was filed from it
  (D-94), and then stays with that word even if it is later unfiled (D-83).
- Smart/auto lists (by JLPT level, shared kanji, scan date) are deferred but
  fall out of D-28's join table cheaply.
- `srs_state` and `review_log` are **Phase 7 tables**, not Phase 6 ones — an
  added table is a Room `AutoMigration`. But nothing built here may assume they
  will never exist (D-79).
