# Phase 4 — CameraX + ML Kit

**Status:** in progress — camera and ML Kit both done. Overlay is Phase 5.
**Updated:** 2026-08-24

## Current state

**CameraX is in and the freeze-frame works** (D-02). The app opens on a live
viewfinder, the shutter captures a still, the still replaces the preview, and
Retake — or system back — returns to the viewfinder. Verified on the Pixel_9
emulator against its `virtualscene` back camera: permission flow, first capture,
retake, second capture, and the round trip out to the lookup screen and back.

**The camera is now the launcher destination** (D-61, recorded as D-73). The
Phase 2 text-input screen survives as a debug path and is reached two ways — the
`query` intent extra in any build type, and a debug-only search affordance in the
scan screen's top corner. `/inspect` therefore works unchanged; confirmed by
driving `--es query 上手` and getting the word screen with no tapping.

**ML Kit is in, and the pipeline runs end to end.** Photograph Japanese text →
stage 2 reads it → the existing Phase 2 tokenize-and-look-up path segments it →
tap a word → readings, senses and example sentences. Verified on the emulator
against a generated sign reading 先生と生産 / 東京都の学生 / 上手, which came back
verbatim and tokenized to 先生 · と · 生産 · 東京 · 都 · の · 学生 · 上手.

**The model is bundled (D-74), measured rather than assumed: ~14.8 MB on a real
device**, not the 43 MB a universal debug APK reports. The measurement notes are
in D-74 and are worth reading before anyone re-measures anything in this project.

**What is deliberately still missing is the overlay.** Artboard 1a puts the words
on the photograph with a peek sheet under the tap; that needs stage 4's
offset-to-pixel bridge and is Phase 5. Until then the recognized text is shown as
a strip under the frozen frame and handed whole to the lookup screen. The
pipeline is real; only the tap target is interim.

Files: `app/src/main/kotlin/com/spotterkanji/app/scan/` — `ScanScreen.kt`,
`ScanViewModel.kt`, `CameraPermission.kt`, `JapaneseTextRecognizer.kt`,
`RecognizedText.kt`.

## What the first pass got wrong, because it will recur

**A `catch (Exception)` around `awaitCancellation()` reported a camera failure on
every normal teardown.** Cancellation in Kotlin *is* an exception. Leaving the
scan screen cancels the binding coroutine, `awaitCancellation` throws
`CancellationException`, and the broad catch treated it as a bind failure. The
error was stored in the ViewModel and nothing retracted it, so returning to the
camera showed **"This device has no camera"** printed on top of a working live
preview.

Two fixes, both needed: `awaitCancellation()` now sits outside the catch, and a
successful bind clears a `CameraUnavailable` error. Worth recording because the
symptom is absurd enough to send you looking in the wrong place — the camera was
never at fault, and no exception was ever logged.

**It was only found by looking at a screenshot.** The build was clean, the tests
passed, and the app worked perfectly on any path that did not leave the scan
screen and come back.

## Next action

**Phase 5 — the overlay.** Stage 4's offset-to-pixel bridge, then taps on the
photograph itself (artboard 1a). `RecognizedText` already carries a box and a
character offset per element, which is the raw material stage 4 needs; nothing
interpolates *within* an element yet, and nothing handles vertical text or
furigana (V-10, V-11, V-26).

Still open inside Phase 4, and small:

- **The "Japanese text detected" indicator on the live preview** (D-02). It needs
  a cheap recognition pass on preview frames rather than on the captured still,
  which is a different use case (`ImageAnalysis`, not `ImageCapture`) — worth
  doing deliberately rather than by reusing the capture path.
- **Real photographs as fixtures.** The one committed fixture is generated: clean
  text on a plain ground, the easy case. It proves the wiring, not the accuracy.

## Design

**The live camera screen is not in the design project — checked, not assumed.**
Artboards `1a`–`1c` are *overlay* treatments and all draw the **frozen** frame
with text highlighted and a peek sheet; `1a` is the chosen one and belongs to
Phase 5. None of them draw viewfinder chrome — no shutter, no permission state,
no detection indicator. So the preview screen was designed in place, deliberately
and knowingly, against `ux.md`'s "large shutter target" and "nothing important in
the top corners".

How to read the design project: MCP tool `DesignSync`, project id
`f7fc2ff0-e30a-4985-8043-606ceed347c6`, `list_files` / `get_file` read-only.
`Kanji Lens.dc.html` is ~106 KB — `get_file` persists a result that large to a
file rather than to context, so slice the artboard out of that file by
`id="1a"` rather than reading it whole. Artboard ids run 1a–1o, 2a–2f, 3a–3b.

## Choices made here that are worth revisiting, not settling

- **Capture resolution is capped at 1920×1080.** A starting point, not a settled
  number. Small kanji photographed from across a street are exactly where more
  pixels help; the ceiling is memory, since a full-sensor 4000×3000 frame is
  ~48 MB as ARGB_8888. Revisit once ML Kit is reading real signage and the
  accuracy cost can be measured instead of guessed at.
- **The camera stays bound while a frame is frozen.** Rebinding costs a few
  hundred milliseconds, and today a frozen frame has nothing on it to read, so
  every freeze is followed by an immediate retake and that latency is what a user
  would notice. Revisit in Phase 5, when the peek sheet gives people a reason to
  sit on a frozen frame for minutes and battery becomes the larger cost.
- **Preview and ImageCapture share one aspect-ratio strategy, both drawn with
  `ContentScale.Crop`.** This makes the photograph a superset of what was framed.
  The failure it prevents — text visible at the edge of the viewfinder and absent
  from the photo — is invisible to the user and becomes a coordinate-mapping bug
  in Phase 5 rather than a visible one. Confirm it precisely there.

## Done

- [x] CameraX preview and capture
- [x] Freeze-frame on shutter (D-02) — not a live overlay
- [x] Camera permission flow, including the denied-permanently state
- [x] **App opens directly on the camera** — no home screen, no dashboard (D-61, D-73)
- [x] Text-input screen kept as a debug path without becoming a second front door (D-73)
- [x] ML Kit Japanese text recognition, **bundled** model variant (D-74)
- [x] Confirm the APK size cost of bundling — ~14.8 MB per device (D-74)
- [x] Recognized text fed into the existing Phase 2 tokenize-and-look-up path
- [ ] "Japanese text detected" indicator on the live preview
- [ ] Collect a **furigana'd** test image alongside the vertical-text one (V-26)
- [x] Relevant `V-##` cases from `verification.md` reviewed — **Phase 4 owns none.**
      `verification.md`'s "Phase 4–5" section holds V-10 (vertical text), V-11
      (character-level tap resolution) and V-26 (furigana separation); all three
      are stage-4 coordinate work and belong to **Phase 5**. V-12 (Japanese glyph
      forms) sits in that section too but was met in Phase 2 by bundling Noto
      Sans JP. Capture and freeze have no silent-failure case of their own —
      a camera that does not work is loud.

## What the second pass got wrong, also worth keeping

**Measuring APK size on an incremental build is measuring nothing.** AGP's
packager leaves freed space as zero-filled dead bytes instead of compacting, so
the unbundled APK — repackaged inside the bundled one's layout — inherited 39 MB
of zeros and came out 5 KB from the bundled figure. The conclusion "bundling is
free" was one screenshot away from reaching the docs. Clean-build every variant.

**Line separators became empty chips in the token strip.** Joining OCR lines with
a newline is right — it stops the tokenizer inventing a word across a line break
— but Kuromoji returns whitespace as a token, and the strip rendered each one as
a blank tappable box. Fixed in `WordLookupViewModel` by dropping blank tokens,
not at the scan boundary, because the separators have to stay in the string.
Typing `先生 と` by hand always did this; the scan just made it visible.

## Open questions

- **What is the real user-facing search?** The project owner wants one
  eventually — asked for explicitly on 2026-08-24 — but deliberately not now:
  the question is what the second screen of a scanner-first app should be, and it
  is better asked once the camera path works. Today's text box is a debug tool
  wearing a search icon, and promoting it is a product decision (D-73), not a
  matter of leaving the button switched on.
- **Onboarding.** `ux.md` notes the app's premise is not self-evident from a
  viewfinder — a new user sees a camera and assumes it is a translator, and it
  suggests explaining before requesting the permission. The permission panel
  currently carries a one-line version of that ("Spotter reads the words in a
  photo…"). Whether that is enough, or whether the bundled-sample-image idea is
  wanted, is unresolved and belongs with the store listing.

## Notes

- **Read `phase-02-android-text-input.md` for the device and build knowledge** —
  the emulator must be woken before a screenshot or it captures black, the first
  launch after install needs ~15 s while Room extracts 100 MB, and
  `connectedAndroidTest` uninstalls the app when it finishes. Several present as
  misleading errors.
- **The Pixel_9 AVD has a usable back camera** — `hw.camera.back=virtualscene` in
  its `config.ini`. It renders a room with a checkerboard TV, which is enough to
  verify capture, rotation and framing, and useless for verifying OCR. Real
  signage photographs are needed for that.
- **CameraX artifacts version in lockstep.** `camera-compose`'s POM pins
  `camera-core` to an exact `[1.6.1]`, so a mismatched version is a resolution
  failure rather than a subtle bug. `camera-camera2` is **not** transitive and
  must be declared — without it there is no camera backend at runtime, and it
  fails at bind time rather than at compile time.
- `CameraXViewfinder` from `camera-compose` is the Compose viewfinder proper.
  Most tutorials wrap `camera-view`'s `PreviewView` in an `AndroidView`; that
  works, but it puts a View-system surface inside the composition, and Phase 5's
  overlay is easier to reason about with nothing in the tree that is a View.
- `ImageProxy.toBitmap()` applies **no** rotation, so a phone held upright yields
  a landscape image on its side. `imageInfo.rotationDegrees` is the correction,
  and reading it beats consulting the display — it stays right on a device whose
  sensor is mounted at an odd angle.
- **`ImageProxy` must be closed.** It holds a buffer from a small fixed pool, and
  leaking one means the *next* capture never fires and never errors either. It
  simply does nothing, which is the worst possible failure mode to debug.
- **The emulator can be pointed at Japanese text**, which is not obvious and
  cost an hour. Boot with
  `-virtualscene-poster wall=<png>` to hang an image in the virtual scene, then
  walk the camera to it with
  `adb emu automation play "$ANDROID_HOME/emulator/resources/macros/Walk_to_image_room"`
  — the macro must be given by **absolute path**; the bare name is rejected with
  "Could not open file". The default view faces a TV showing a checkerboard, so
  without the walk the app correctly reports no Japanese text and it looks like a
  failure.
- **`connectedAndroidTest` uninstalls the app**, so an on-device run interleaved
  with a test run silently starts poking at a device with no app on it. Already
  in the Phase 2 notes; it caught this phase too.
- This phase produces recognized text and pixel bounding boxes. Connecting those
  boxes to word boundaries is **Phase 5**, deliberately kept separate.
- ML Kit `Element` boundaries do **not** match Japanese word boundaries. Elements
  are useful for their positions, not their segmentation.
