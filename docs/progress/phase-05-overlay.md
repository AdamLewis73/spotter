# Phase 5 — Tappable overlay

**Status:** feature-complete. Every `V-##` case this phase owns is met and the
D-22 checkpoint is discharged. The one thing outstanding is a photographed
fixture we own; Save belongs to Phase 6.
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

**Phase 5 is essentially done.** The scan reads, lays out, draws, resolves a tap,
peeks, expands into the word screen and swaps the kanji screen in place. What is
left is either owned by another phase or optional:

1. **Save** — drawn and disabled, on both the peek and the word screen. It writes
   user data, gated behind the Phase 6 checkpoint (D-15–D-18, D-43).
2. **Ambiguity chips (1b) and the loupe (1c)** — additions to 1a in the design
   project rather than alternatives to it. The *data* behind 1b already exists
   and is rendered: `AlternateStrip` on the word screen shows every overlapping
   dictionary word (D-07, D-70). 1b is that same list moved to chips at the touch
   point on the photograph. Neither is on a `V-##`; both are optional for v1.
3. **The camera's own states** — artboard `4k` "lens covered" and the rest of
   section 4. Camera chrome rather than overlay, so it belongs with whatever
   phase revisits the camera screen.

**The sheet, as built.** `ScanSheet` is a height, not a destination (D-30): one
component that animates between a 30% peek and a 92% full stage, dragged by its
handle or moved by *Full details*. The kanji screen swaps its **contents** at
full height rather than being a third stage (D-32).

Back unwinds exactly one level at a time, which is the flow the project owner
asked for on 2026-08-26:

```
kanji screen → word screen → peek → frozen frame → viewfinder
```

**One trap worth keeping.** The scan seeds the lookup with
`onQueryChanged(text, autoSelect = false)`. Selecting a word for the user would
open a sheet over a photograph they have not looked at yet, and the obvious
alternative — seed, then clear the selection — silently breaks the screen, because
clearing cancels the lookup job and that is the same coroutine still doing the
tokenizing. Taps then resolve to nothing, with no error anywhere.

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
- [x] The expand-to-word-screen gesture (D-30, D-31) — one component, two
      heights, dragged by the handle or opened by *Full details*
- [x] Kanji screen swaps in place inside the sheet, with a back arrow (D-32)
- [x] **Checkpoint: bounding box stored in the scan record (D-22)** — discharged
      2026-08-26. Nothing stores it yet because the user-data schema is Phase 6's;
      what this phase owed was that the box still be *knowable* at save time, and
      `ScanLayout.boxFor(offsets)` provides it, tested. The schema field carries
      into Phase 6 as an obligation.
- [x] **Relevant `V-##` cases swept** — see below.

## Verification cases this phase owns

`verification.md`'s "Phase 4–5" section holds five. Phase 4 owns none of them
(`phase-04-camera.md` records why), so all five land here.

| Case | Status |
|---|---|
| **V-10** vertical text (縦書き) | **Met.** Reading order was measured, found backwards, and fixed in stage 2 (D-75); tap targets are asserted per character. Caveat below. |
| **V-11** character-level tap resolution | **Met.** Asserted at every character of a run, in `:domain` and again through real Compose layout in `ScanOverlayTest` — the second one covering the `ContentScale.Crop` transform that the first cannot see. |
| **V-12** Japanese glyph forms | **Met, and now moot for the overlay.** It was met in Phase 2 by bundling Noto Sans JP. Since D-78 the overlay draws **no glyphs of its own** — the photograph supplies them — so the case now applies only to the sheet and the screens inside it, which take `SpotterJapanese` explicitly. |
| **V-26** furigana excluded from the token stream | **Met.** Ruby is separated by two agreeing signals, size *and* displacement. Caveat below. |
| **V-28** a line break neither invents nor hides a word | **Met.** Decided geometrically, and checked against a real notice whose eight columns break into exactly its three sentences. |

**The caveat on V-10 and V-26 is the same one**, and it is the last open item in
the phase: every *committed* fixture is generated. The real photographs used to
calibrate the rules are third-party and local-only (see `.gitignore`), so what
survives of them is measured rectangles in `RealNoticeLayoutTest` and numbers in
these docs. Both cases ask for a permanent photographed fixture, and neither has
one we own. V-26 in particular has no photographed fixture at all.

**Cases from other phases that the scan path now inherits.** The sheet drives the
same `WordLookupViewModel` and the same screens as the Phase 2 text route, so
V-06 (alternates), V-07 (conjugated verbs), V-08 (reading scripts), V-21
(obsolete readings), V-23 (sense filtering) and V-27 (one sentence per reading)
hold on a scanned word for the same reason they hold on a typed one — it is
literally the same code. That is a consequence of reusing the ViewModel rather
than duplicating it, and it is worth stating because the alternative would have
quietly forked six verification cases.

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
