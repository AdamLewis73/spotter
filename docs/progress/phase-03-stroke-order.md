# Phase 3 — Stroke order tab

**Status:** in progress — the data is read; the drawing is not written
**Updated:** 2026-08-24

## Current state

**The paths are out of the dictionary and on the screen as a number; nothing is
drawn yet.** `strokes` has a Room entity, `KanjiDetail.strokePaths` carries the
per-stroke SVG `d` strings, and `StrokeOrderTab` reports the count it would
animate. Verified on the emulator against 辻 and 㐂, and by two instrumented
cases in `KanjiDetailTest`.

## Next action

**Draw one static kanji.** Compose has no SVG path parser; `androidx.graphics.path`
/ `PathParser` turns a `d` string into a `Path` that `Canvas` can draw. Then
animate the strokes in sequence — the visually rewarding part, and the reason
`roadmap.md` places this before the camera.

Three measurements taken while plumbing the data, all worth having before
drawing:

- **The canvas is 109×109.** Confirmed against the data, not just the KanjiVG
  docs: the largest absolute coordinate in the whole table is 108.0. Negative
  numbers appear and are not out-of-range — they are relative deltas in lowercase
  commands.
- **Only six path commands occur in the entire table:** `c` (166,031), `M`
  (78,905), `C` (8,962), `s` (1,095), `m` (135), `S` (91). No arcs, no
  quadratics, no `Z`. So the geometry is moveto plus cubic Béziers and nothing
  else, which matters because animating a stroke means walking its length —
  `PathMeasure` handles all of it, but a hand-rolled parser would only need
  these.
- **Paths are small:** ~820 bytes per kanji on average, under 2 KB at worst,
  5.3 MB for the table. That is why they load with the rest of the kanji screen
  rather than when the tab is opened — a second loading state would cost more
  than the bytes.

**The design still has no stroke-order frame.** The Claude Design project (D-67)
does not cover this tab. Either draw one or design it in place; do not assume a
frame exists.

## What the two traps actually did

Both fired, and **one of them fired for the opposite reason than predicted** —
worth reading before touching another dictionary table.

- **The nullable primary key was real, and the earlier note here had it
  backwards.** This file predicted `strokes` would be safe because "SQLite
  forbids null in a TEXT primary key". It does not. SQLite permits NULL in a
  PRIMARY KEY column unless the column is `INTEGER PRIMARY KEY` or the table is
  `WITHOUT ROWID` — a documented deviation from the SQL standard, kept for
  backward compatibility. `strokes` is neither (D-56 made it a rowid table
  deliberately, because `svg_paths` is the widest column in the schema), so
  `PRAGMA table_info` genuinely reported `notnull=0`.

  Room compares `notNull` exactly and derives `true` from a non-null Kotlin
  property, so the open would have failed. There is no entity shape that reads
  the old file either: Room rejects a nullable primary key at compile time. **So
  the fix belonged in the builder** — `schema.sql` now spells out `NOT NULL`,
  which every other table here had already, either explicitly or by being
  `WITHOUT ROWID` or integer-keyed. `strokes` was the only one missing it.

  Cost: one dictionary rebuild (~45 s). `schema.sql` is in `BUILDER_GLOBS`, so
  `build_id` rolled to `31d9084cc633` and the device re-extracted, which is
  exactly what D-65 is for.

- **`PRAGMA table_info` first was the right call and paid for itself.** It is how
  the above was caught before reading a Room validation dump. The foreign key
  `REFERENCES kanji(char)` is mirrored on the entity, per the note that a real
  constraint the entity omits fails the open with a message that never mentions
  foreign keys.

- **The schema export behaved.** `SCHEMA_VERSION` went 4 → 5 before the first
  build, so `4.json` was left untouched and `5.json` is new. Bumping first is
  what avoids the in-place rewrite this file warned about.

## V-09's display rule was being broken, and is fixed

`verification.md` V-09 requires the tab to show **the number of paths being
animated, not KANJIDIC2's figure.** The placeholder tab showed
`detail.strokeCount`, which is KANJIDIC2's — so it was already wrong for the 109
kanji where the two disagree, before any animation existed to contradict.

Now fixed and checked on device: 辻 reports **6 STROKES** (paths) rather than 5
(KANJIDIC2). Where KanjiVG has no data at all the tab falls back to KANJIDIC2's
count and says there is no diagram — checked with 㐂 — rather than rendering a
blank canvas or "0 STROKES".

## Done

- [x] Stroke order tab exists on the kanji screen, empty and saying so (D-67)
- [x] Room entity over the `strokes` table (needed a `NOT NULL` in `schema.sql`)
- [x] Read per-stroke SVG paths out of the dictionary — `KanjiDetail.strokePaths`
- [x] **V-09** claimed: the tab shows the path count, not KANJIDIC2's, and says
      so when there is no diagram
- [ ] Render one kanji statically in Compose
- [ ] Animate strokes sequentially

## Open questions

None blocking. The one design question is what the tab looks like, since
D-67 never drew it — see **Next action**.

## Notes

- **V-09 already passes** for this data: stroke path count agrees with
  KANJIDIC2's stroke count for 6,416 kanji, including all 2,501 ranked ones, with
  109 bounded mismatches that are genuine 辶 form differences. So the paths can be
  trusted; what is unproven is only the drawing.
- **The design has no stroke-order frame.** The Claude Design project (D-67)
  covers the word screen, kanji Examples, review, saved lists and profile — not
  this tab. Either draw one or design it in place; do not assume a frame exists.
- KanjiVG ships as **one combined XML** (~3.6 MB gzipped), not ~11,000
  individual SVG files — a Phase 1 finding.
- Self-contained and visually rewarding; `roadmap.md` picks it as the phase to
  get comfortable with Compose drawing before the camera work.
- Word-level stroke order (playing 先 then 生 in sequence) is deferred, not
  planned — see the deferred table in `roadmap.md`.
