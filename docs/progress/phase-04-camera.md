# Phase 4 — CameraX + ML Kit

**Status:** not started
**Updated:** 2026-08-09

## Current state

Not started. No camera code of any kind exists.

## Next action

Nothing yet — depends on the Phase 2 pipeline being built and trusted, so that a
bug in this phase is legible as a camera bug rather than as anything else.

## Done

- [ ] CameraX preview and capture
- [ ] Freeze-frame on shutter (D-02) — not a live overlay
- [ ] ML Kit Japanese text recognition, **bundled** model variant
- [ ] Confirm the APK size cost of bundling
- [ ] Recognized text fed into the existing Phase 2 tokenize-and-look-up path
- [ ] Camera permission flow
- [ ] **App opens directly on the camera** — no home screen, no dashboard (D-61)
- [ ] Collect a **furigana'd** test image alongside the vertical-text one (V-26)
- [ ] Relevant `V-##` cases from `verification.md` added to this list

## Open questions

None recorded yet.

## Notes

- Bundled vs unbundled ML Kit: **bundled** is the choice, but as a product
  preference, not a rule. D-46 supersedes D-03's "fully offline", so unbundled
  is no longer forbidden — don't read superseded D-03 and conclude otherwise.
  See `architecture.md`.
- This phase produces recognized text and pixel bounding boxes. Connecting those
  boxes to word boundaries is **Phase 5**, deliberately kept separate.
- ML Kit `Element` boundaries do **not** match Japanese word boundaries. Elements
  are useful for their positions, not their segmentation.
