# Phase 3 — Stroke order tab

**Status:** complete
**Updated:** 2026-08-24

## Current state

**Done.** The Stroke order tab draws KanjiVG's real outlines: a 200dp stage with
a centre crosshair, the character written stroke by stroke, play/pause, a working
speed control, and an **Every stroke** grid of cumulative frames that scrubs when
tapped. Verified on the emulator against 生 (5 strokes), 辻 (the V-09
disagreement case), 鬱 (29 strokes, six grid rows) and 㐂 (no KanjiVG data), in
both light and dark.

## The design *does* cover this tab — the earlier note here was wrong

This file and `CLAUDE.md` both said the Claude Design project (D-67) had no
stroke-order frame and that the tab would have to be designed in place. It has
one: **artboard 3b**, "Stroke order tab — frames are placeholders", in
`Kanji Lens.dc.html`. Read it before changing this screen.

The artboard's own caption says what it is: *"the stroke frames are placeholders
— I can't draw accurate kanji strokes, and these should come from real
stroke-order data (KanjiVG) at build time. Sequence, numbering and layout are the
design; the glyphs inside are stand-ins."* So the layout was followed exactly and
the frames filled with real paths.

**How to read the design project from here:** the MCP tool is `DesignSync`, the
project id is `f7fc2ff0-e30a-4985-8043-606ceed347c6`, and `list_files` /
`get_file` are read-only. `Kanji Lens.dc.html` is ~106 KB, which is a large
fraction of a context window — pull it once, write it to a scratch file, and slice
out the artboard by `id="3b"` rather than reading the whole thing. `support.js`
is the generated canvas runtime and contains no design information. The artboard
ids run 1a–1o, 2a–2f and 3a–3b.

## The artboard's Trace button is built — D-72, replacing D-71

**D-71 got this wrong and has been superseded.** It read the Trace button as a
link to the design's writing surface (artboard 2c) and deferred it to Phase 7,
reasoning that handwriting is the review interaction. The mistake was treating
*practice* and *assessment* as one feature because the design draws them on the
same kind of canvas.

They are not. Practice is a **reference** capability — you just looked up a kanji
and want to write it; being told to come back after building a review queue is
absurd. Assessment needs a scheduler, a due date and a grade. D-72 separates
them, and the Trace button now switches the stage between two modes:

- **Watch** plays the character being written.
- **Trace** makes the same stage writable. The ghost stays up, the expected
  stroke is lifted out of it in the accent colour, and the learner draws over it.

**No scoring, and that is what keeps it independent of FSRS** — the ghost *is*
the answer, so there is nothing to grade. Nothing here writes user data or needs
Phase 6 or 7. Artboard 2c's blind write-then-check with the four grades is still
a separate Phase 7 screen.

A stroke counts if it **starts and ends** near the right places, against a
generous tolerance, compared only against the stroke currently expected. Not
handwriting recognition and deliberately not shape matching. The one real
correctness claim: **a stroke drawn backwards is rejected**, because a reversed
stroke puts the start near the target's end — and stroke direction is a common
beginner error rather than a technicality.

**The speed control is also real**, not the artboard's static text: slowing 鬱
down is the reason a learner would want it. It is hidden in trace mode rather
than disabled, since playback is not happening.

One addition the artboard could not express, because it used a font glyph as the
stand-in: **strokes not yet drawn show as a faint ghost.** Without it the stage is
empty before the animation starts and reads as broken, and the ghost is what makes
the motion legible as a character filling in rather than a line moving in the dark.

## What the drawing cost

- **Compose already ships an SVG path parser.** `androidx.compose.ui.graphics.vector.PathParser`
  — the one `ImageVector` uses — turns a `d` string into a Compose `Path`. No new
  dependency, and no hand-rolled parser. The note here previously pointed at
  `androidx.graphics.path`, which is a different thing: that artifact *reads* an
  existing `Path` back out as segments.
- **Scale the canvas, not the paths.** Lengths stay in KanjiVG's 109 units, so
  animation progress is a fraction of a fixed number and the 200dp stage and the
  40dp grid cells render from identical geometry.
- **`PathMeasure.getSegment` is what draws a partial stroke.** One `PathMeasure`
  and one destination `Path` are held across frames rather than allocated in the
  draw pass — 鬱 at 60fps would otherwise churn ~1,700 objects a second.
- **Stroke width is expressed in KanjiVG units, not dp**, which is what keeps the
  thumbnails self-similar to the stage instead of spidery.
- **`onClickLabel` is not a `contentDescription`.** It labels the click *action*.
  A grid cell whose content is a drawing therefore announced nothing, and the
  first UI test found zero nodes. Both the cells and the play button now carry
  real descriptions, decorative glyphs are cleared from the semantics tree with
  `clearAndSetSemantics {}`, and the selected cell and speed chip expose
  `selected` — which is also what the tests assert on, rather than the accent
  colour, per the reasoning in `ReadingHeadingTest`.

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
- [x] Render one kanji statically in Compose
- [x] Animate strokes sequentially
- [x] Artboard 3b built in full — stage, transport, cumulative grid, and the
      Trace button as a two-mode switch (D-72, superseding D-71)
- [x] `StrokeOrderTest` — 10 Compose UI cases: grid, scrub, speed, both empty
      states, and trace mode driven through real gestures including the
      backwards-stroke rejection

## Open questions

None. (The "what should this look like" question was answered by finding
artboard 3b, which existed all along.)

## Notes

- **V-09 already passes** for this data: stroke path count agrees with
  KANJIDIC2's stroke count for 6,416 kanji, including all 2,501 ranked ones, with
  109 bounded mismatches that are genuine 辶 form differences. So the paths can be
  trusted; what is unproven is only the drawing.
- **The design does have a stroke-order frame** — artboard 3b. The earlier claim
  here that it did not was wrong; see the section above.
- KanjiVG ships as **one combined XML** (~3.6 MB gzipped), not ~11,000
  individual SVG files — a Phase 1 finding.
- Self-contained and visually rewarding; `roadmap.md` picks it as the phase to
  get comfortable with Compose drawing before the camera work.
- Word-level stroke order (playing 先 then 生 in sequence) is deferred, not
  planned — see the deferred table in `roadmap.md`.
