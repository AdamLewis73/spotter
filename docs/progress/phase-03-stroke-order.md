# Phase 3 — Stroke order tab

**Status:** not started
**Updated:** 2026-08-09

## Current state

Not started. KanjiVG is already ingested by the Phase 1 builder, so the stroke
path data ships in `spotter.db` and needs no new source work.

## Next action

Nothing yet — blocked on Phase 2's kanji screen existing to hang the third tab
from (D-05).

## Done

- [ ] Read per-stroke SVG paths out of the dictionary
- [ ] Render them in Compose
- [ ] Animate strokes sequentially
- [ ] Relevant `V-##` cases from `verification.md` added to this list

## Open questions

None recorded yet.

## Notes

- KanjiVG ships as **one combined XML** (~3.6 MB gzipped), not ~11,000
  individual SVG files — a Phase 1 finding.
- Self-contained and visually rewarding; `roadmap.md` picks it as the phase to
  get comfortable with Compose drawing before the camera work.
- Word-level stroke order (playing 先 then 生 in sequence) is deferred, not
  planned — see the deferred table in `roadmap.md`.
