# Phase 3 — Stroke order tab

**Status:** not started
**Updated:** 2026-08-23

## Current state

Not started. KanjiVG is already ingested by the Phase 1 builder, so the stroke
path data ships in `spotter.db` and needs no new source work.

## Next action

**Unblocked, and the tab already exists.** `KanjiScreen.kt` in `:app` has three
tabs — Overview, Examples, **Stroke order** — and the third currently renders
`StrokeOrderTab`, which shows the stroke count and the line "Stroke order
animation arrives in Phase 3." Replacing that composable's body is the whole
job; nothing above it needs to move.

The tab was built empty on purpose rather than added later: a tab that appears
later shifts the two beside it, and the count is where D-50 says it belongs.

In order:

1. **Read the paths out of the dictionary.** `strokes.svg_paths` is a JSON array
   of SVG path strings, one per stroke, joined to `kanji` on `kanji_char`.
   Nothing maps that table yet — it needs a Room entity, and Room validates
   every column of a table it maps.
2. **Draw one static kanji** before animating anything. Compose has no SVG path
   parser; `androidx.graphics.path` / `PathParser` turns a `d` string into a
   `Path` that `Canvas` can draw.
3. **Animate the strokes in sequence**, which is the visually rewarding part and
   the reason `roadmap.md` places this before the camera.

**Two traps waiting**, both already paid for elsewhere in the project:

- **Room's pre-packaged schema check.** Declaring a new entity is what fires it,
  and the message names the *table* and not the field — diff Expected against
  Found. `example` cost an hour on exactly this in Phase 2: an
  `INTEGER PRIMARY KEY` without `NOT NULL` reads as nullable to Room, because
  SQLite genuinely permits null there.

  `strokes` should be clear — its key is `kanji_char TEXT PRIMARY KEY`, and
  SQLite forbids null in a TEXT primary key, so it reports as non-null. Check it
  with `PRAGMA table_info`, `foreign_key_list` and `index_list` before writing
  the entity anyway; that is faster than reading a validation dump. Note it does
  carry `REFERENCES kanji(char)`, and a real foreign key the entity fails to
  declare breaks the open with a message that says nothing about foreign keys.
- **Adding an entity changes the exported schema**, and Room rewrites the
  current version's JSON in place rather than complaining. Bump
  `DictionaryDatabase.SCHEMA_VERSION` (now **4**) so the committed record of what
  each version shipped stays true. The bump costs one slow launch while the
  dictionary re-extracts.

## Done

- [x] Stroke order tab exists on the kanji screen, empty and saying so (D-67)
- [ ] Room entity over the `strokes` table
- [ ] Read per-stroke SVG paths out of the dictionary
- [ ] Render one kanji statically in Compose
- [ ] Animate strokes sequentially
- [ ] Relevant `V-##` cases from `verification.md` added to this list

## Open questions

None recorded yet.

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
