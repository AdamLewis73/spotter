# Phase 4 — CameraX + ML Kit

**Status:** not started — unblocked, and now the current phase
**Updated:** 2026-08-24

## Current state

Not started. No camera code of any kind exists.

**What it depends on is now in place.** This phase was written as blocked on the
Phase 2 pipeline being built and trusted — the point of the inside-out build
order is that a bug here reads as a camera bug rather than as anything else.
Phase 2 is feature-complete and Phase 3 is done, so that condition is met: text
goes in, tokenizes, looks up, and renders both detail screens, with 42
instrumented tests over it.

## Next action

**Add CameraX and get a frozen frame on screen — nothing else.** No ML Kit, no
text recognition, no pipeline wiring. Preview, shutter, freeze (D-02), and a way
back to the preview. That is the piece with the device-specific failures in it,
and it is worth having working alone before anything downstream can be blamed
for it.

Then ML Kit, then hand its output to the existing tokenize-and-look-up path,
which already works and is already trusted.

**Two things to settle before writing code**, both cheap now:

- **Bundled vs unbundled ML Kit** — bundled is the stated preference, but it is a
  product call about APK size, and the APK already carries a ~100 MB dictionary
  plus a 9.2 MB font. Measure the delta before committing (see the Done list).
- **The app opens directly on the camera** (D-61). This is a navigation change,
  not a screen to bolt on beside the existing text box, and `roadmap.md` calls it
  out as shaping navigation rather than being a coat of paint. Decide what
  happens to the text-input screen — kept as a debug path, moved behind
  something, or removed — rather than leaving two entry points by accident.

The text-input screen and `/inspect` are how every `V-##` case so far has been
driven, so removing it outright would cost the project its test harness. Consider
that before deleting it.

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

- **What happens to the text-input screen when the camera becomes the front
  door (D-61)?** It is currently the only way to drive the app for testing, and
  every verification case so far has gone through it. Keeping it as a debug
  entry point is the obvious answer; the question is how it is reached once the
  app opens on the camera.

## Notes

- **Read `phase-02-android-text-input.md` for the device and build knowledge** —
  the emulator must be woken before a screenshot or it captures black, the first
  launch after install needs ~15 s while Room extracts 100 MB, and
  `connectedAndroidTest` uninstalls the app when it finishes. Several present as
  misleading errors.
- Bundled vs unbundled ML Kit: **bundled** is the choice, but as a product
  preference, not a rule. D-46 supersedes D-03's "fully offline", so unbundled
  is no longer forbidden — don't read superseded D-03 and conclude otherwise.
  See `architecture.md`.
- This phase produces recognized text and pixel bounding boxes. Connecting those
  boxes to word boundaries is **Phase 5**, deliberately kept separate.
- ML Kit `Element` boundaries do **not** match Japanese word boundaries. Elements
  are useful for their positions, not their segmentation.
