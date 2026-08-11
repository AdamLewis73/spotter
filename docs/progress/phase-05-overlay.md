# Phase 5 — Tappable overlay

**Status:** not started
**Updated:** 2026-08-09

## Current state

Not started.

## Next action

Nothing yet — needs Phase 4's pixel boxes and Phase 2's character offsets both
in place, since this phase is the bridge between them.

## Done

- [ ] Offset↔pixel lookup table built by walking ML Kit elements in reading order
- [ ] Per-character rectangles by interpolation within an element
- [ ] **Vertical text (縦書き) in test images from day one** — interpolate on y,
      columns right-to-left
- [ ] **Furigana excluded from the token stream** (V-26) — ruby is separated
      geometrically, by glyph size and baseline offset, in this same stage
- [ ] Tap resolves: pixel → element → character index → global offset → token
- [ ] Overlay dims the image, detected text stays bright (D-33)
- [ ] Peek sheet, and the expand-to-word-screen gesture (D-30, D-31)
- [ ] Kanji screen swaps in place inside the sheet, with a back arrow (D-32)
- [ ] Checkpoint: bounding box stored in the scan record (D-22)
- [ ] Relevant `V-##` cases from `verification.md` added to this list

## Open questions

None recorded yet.

## Notes

- **Highest risk of subtle bugs in the project, and now also the core of v1**
  (D-61). `architecture.md` stage 4 has the interpolation maths and the reasoning.
- Stage 4 carries three jobs, all geometric and all cheapest to design together:
  horizontal vs vertical (V-10), character interpolation (V-11), and separating
  ruby from base text (V-26). Building one and retrofitting the others is the
  failure mode.
- Linear interpolation is unusually accurate here because CJK glyphs are
  uniformly wide by design. That property is worth relying on.
- Vertical text is not an edge case to add later. Discovering it after a
  horizontal-only implementation means redoing the whole coordinate layer.
- D-22 is a checkpoint because storing the box is cheap now and later requires
  re-running OCR over every saved image.
- The two-level in-sheet stack (word → kanji) needs custom plumbing;
  `ModalBottomSheet` has no back stack. D-32 accepts that cost.
