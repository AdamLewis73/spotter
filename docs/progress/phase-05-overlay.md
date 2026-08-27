# Phase 5 — Tappable overlay

**Status:** in progress — geometry, the transform and the overlay itself are
built to artboard 1a. What is left is the sheet's expansion (D-30, D-32) and Save.
**Updated:** 2026-08-26

## Current state

**The gating experiment is done, and it found a fault (D-75).** ML Kit emits
縦書き columns **left-to-right**, which is backwards, so the string was being
scrambled in stage 2 before stage 4 ever saw a pixel — the outcome this file
warned about. Full measurements are in D-75 and V-10; the short version:

- Within a column, reading order is **correct**, and each column is grouped as
  one line, so the separator lands between columns. The fix is therefore a
  **sort, not a reconstruction** — much the cheaper of the two outcomes.
- Staggering the columns in y does not change the order, so this is an
  unconditional horizontal-text assumption rather than a position sort. It will
  not come right on its own.
- **Elements split mid-column** — two columns came back as four elements.
  Nothing downstream may assume one element per line.
- Vertical recognition is **less accurate** (都 misread on both vertical
  fixtures, differently each time). Not a coordinate fault; do not chase it as
  one.

**Taps are not currently broken by this.** Each element carries its own box and
its own offset and the two agree, so a tap on a vertical column already resolves
to the right word. What is wrong is the **flow**, not the mapping — and the flow
becomes load-bearing the moment V-28 permits two lines to join.

Also settled before writing code: **the geometry lives in `:domain` on a portable
box type (D-76)**, not in `:app` on `android.graphics.Rect`. The reason is test
speed — 19 seconds for the whole JVM suite against minutes per emulator round
trip — which decides how many cases with known answers actually get written, and
that is the only real defence against this phase's failure mode.

## Calibrated against real images, 2026-08-26

The rules below were checked against real typesetting and real photographs
before any module was built around them. **Those images are third-party and
local-only — see `.gitignore`.** What survives here is the numbers.

**The direction classifier works, and it knows when it doesn't.** Details in the
Next action section; the headline is that no fixture produced a confident wrong
answer, and every unreliable case self-reported a small margin.

**Furigana is worse than V-26 assumed, in a way that matters.** Ruby comes back
interleaved with the body text in **no stable order** — scattered before,
between and after the lines it annotates — so there is no shortcut like "ruby
precedes its base". It must be separated geometrically. Ruby also **fragments
the column it annotates**: one vertical column came back as four elements, split
at each ruby interruption.

Measured ruby-to-body height ratio is cleanly separated at capture scale (ruby
32–46 px against body 64–68) and narrows dangerously at low resolution (9–16
against 20–21). So the size signal alone is resolution-dependent and needs the
positional test beside it, not behind it.

**Small text is not necessarily ruby.** Donor plaques and shop lanterns carry
company names, prefectures and titles set markedly smaller than the main name,
inline in the same column. A size-only rule reads them as ruby and drops them
from the token stream. This is the case that makes the two-signal requirement
non-negotiable.

**Grouping must precede ordering, and it is spatial.** The notice fixture has a
*horizontal* header above *vertical* body columns; ML Kit drops it into the
middle of the string, and sorting the whole image by x leaves it there. The
lantern photograph is worse — a 2D grid of independent vertical texts, where any
single global sort is meaningless. Group into spatial clusters, classify each,
order the groups, then order within them.

**Recognition quality on hard signage is well below the clean case, and that is
not a geometry problem.** Measured, roughly:

| Input | Result |
|---|---|
| Clean printed text (the notice, UDHR samples) | near-perfect |
| Modern signage, moderate distance | partial — some elements clean, many garbled |
| Night neon, curved lanterns, weathered wood at an angle | mostly fails |

Worth holding in mind when judging the overlay: on a hard image the overlay will
be sparse because there is little to draw, not because the bridge is broken.

*Weak evidence on capture resolution, recorded so it is not over-read.* A sweep
of one lantern photo and one plaque photo at 1620×1080, 2560×1707 and 3840×2560
gave 3/4/6 and 13/12/16 elements — sub-linear, and **not monotonic**. Raising
the cap is not the fix for hard signage. Two images is not enough to settle
`phase-04-camera.md`'s open question, but it is enough to say resolution is not
the dominant term.

**Both halves this phase bridges now exist.** Phase 4 produces pixel
boxes and Phase 2 produces character offsets, so the condition this file was
waiting on is met.

Specifically, `app/src/main/kotlin/com/spotterkanji/app/scan/RecognizedText.kt`
already carries, per ML Kit element, its text, its bounding box in **image pixel
coordinates**, and its **start offset** into the concatenated string the
tokenizer sees. The concatenation happens exactly once, there, on purpose — if
this phase re-walked ML Kit's tree itself the two walks could disagree and shift
every tap by a character, silently.

Two things that concatenation does **not** do, and that belong here:

- Nothing interpolates *within* an element, so there are no per-character
  rectangles yet.
- Reading order is ML Kit's own block-then-line order, taken as given. That is
  **now confirmed wrong** for vertical text (D-75) and nothing has been
  reordered, so nothing has to be un-reordered — the sort is added here, on
  untouched input.

One wrinkle to expect: lines are joined with a newline separator, so a few
character offsets belong to **no element**. A tap can never land on one, because
no rectangle maps to one — but a lookup table that assumes every offset has a box
will be wrong at exactly those positions.

## Next action

**The sheet's second half.** The overlay, the transform and the peek sheet are
built; `ScanOverlay.kt` draws artboard 1a and `ScanProjection` handles
`ContentScale.Crop`. What artboard 1a implies but this does not yet do:

1. **Drag the peek sheet up into the word screen** (D-30). The handle is drawn,
   deliberately, so the affordance does not appear later as if bolted on — but
   the drag is not wired. The two are meant to be one expanding component, and
   `ModalBottomSheet` has no back stack, so this needs the custom plumbing D-32
   accepts the cost of.
2. **Kanji screen swapping in place inside the sheet** (D-32), with a back arrow.
3. **Save** — drawn and disabled. It writes user data, which is gated behind the
   Phase 6 checkpoint (D-15–D-18, D-43). The button waits for the schema rather
   than the schema being improvised for the button.
4. **Ambiguity chips (1b) and the loupe (1c)**, if wanted. Both are drawn in the
   design project as additions to 1a rather than alternatives to it; neither is
   implemented and neither is on any `V-##`.

**Looking at it.** `OverlayShotTest` renders the overlay to PNG for a design
check. It asserts nothing, so it is gitignored as a local tool. `connectedAndroidTest`
uninstalls the app and takes its files with it, so drive it directly:

```
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.spotterkanji.app.scan.OverlayShotTest   com.spotterkanji.app.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.spotterkanji.app/files/overlay-horizontal.png
```

**Still outstanding: real photographed fixtures we own.** Everything committed is
generated. The third-party images used for calibration are local-only and cannot
be committed (see `.gitignore`), so the measurements they produced live in the
docs and in `RealNoticeLayoutTest`.

**The three geometry problems are one problem.** V-10 (are these columns, and do
they run right-to-left?), V-26 (is this small kana annotation or body text?) and
V-28 (is this line a continuation of the one above, or a separate thing?) all ask
*what does this geometry mean?*, none can be answered from the text alone, and
V-28 cannot even be posed until V-10 is, because "the line above" is undefined
until the writing direction is known. Design them together; retrofitting any one
onto the others is the redo `architecture.md` warns about.

**The design is drawn**: artboard **1a**, "Dim frame, bright text, solid
selection", plus 1b for ambiguity chips and 1c for the loupe. See
`phase-04-camera.md` for how to read the design project without spending a
context window on a 106 KB file.

## Done

- [x] Offset↔pixel lookup table built by walking elements in reading order
- [x] Per-character rectangles by interpolation within an element
- [x] **Vertical text (縦書き) in test images from day one** — interpolate on y,
      columns right-to-left
- [x] **Furigana excluded from the token stream** (V-26) — ruby separated by two
      agreeing signals, size *and* displacement, because size alone deletes the
      small-but-real text on lanterns and donor plaques
- [x] **Line-break policy decided geometrically** (V-28) — a line that runs to
      the measure wrapped; one that stops short ended. Blocks of fewer than three
      lines are never joined, because below that the test is circular
- [x] **Confirm ML Kit's reading order for vertical text** — done 2026-08-26.
      Columns come back left-to-right, which is backwards, and stagger does not
      change it (D-75, V-10). Pinned by `VerticalTextOrderTest`.
- [x] **Decide where the geometry lives** — `:domain`, on a portable box type
      (D-76), for the test-speed reason
- [x] Columns sorted right-to-left in stage 2, before concatenation (D-75)
- [x] Geometry built in `:domain` on a portable box type (D-76) — 68 JVM cases
- [ ] Real photographed fixtures — vertical and furigana'd; all three committed
      fixtures are generated (V-10, V-26)
- [x] Tap resolves: pixel → character → offset (`ScanLayout.offsetAt`), including
      a tap on ruby falling through to the word beneath it
- [x] Screen-pixel to image-pixel transform, against `ContentScale.Crop`
      (`ScanProjection`, tested in `:domain` apart from the interpolation)
- [x] Overlay dims the image, detected text stays bright (D-33) — the
      photograph's **own pixels** are repainted over the scrim (D-78, which
      superseded D-77's retyping after both were compared on real photographs)
- [x] Peek sheet — word, glosses, two actions, and **no reading** (D-47)
- [ ] The expand-to-word-screen gesture (D-30, D-31); the handle is drawn, the
      drag is not wired
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
