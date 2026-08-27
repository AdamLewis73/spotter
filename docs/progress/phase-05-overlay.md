# Phase 5 — Tappable overlay

**Status:** in progress — the vertical-text question is measured and answered;
the coordinate work has not started
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

**Build the geometry module in `:domain` (D-76), starting with the column sort
that D-75 requires.** The pieces below are one design and one module; splitting
them is the documented failure mode.

1. **Classification** — writing direction per group. For a run of *n*
   characters, compare `width/height` and `height/width` against *n* and take
   whichever is nearer.

   **State it as a comparison, never as a threshold.** An earlier draft of this
   file quoted a band of 1.05–1.22 for the correct axis, measured off the
   generated fixtures. That band is wrong — generated text is too uniform.
   Against real typesetting the correct axis spans **0.69–1.01**, and 【重要】
   sits at the bottom of it, so anything thresholding near 1.0 misclassifies a
   perfectly ordinary heading. The comparison has no such problem.

   **Keep the margin** — the gap between the two candidate scores — because it
   is what makes the rule say when it does not know. Validated across every
   fixture available: no confident-and-wrong classification, and every case that
   *was* unreliable reported a margin below 0.5. Single characters come back at
   **0.00–0.02**, which is correct; a lone square glyph genuinely carries no
   direction and must inherit from its siblings.
2. **Reading order** — sort groups by descending x for vertical, ascending y for
   horizontal. This is D-75's fix and it belongs in stage 2, before
   concatenation, because the concatenation defines the offsets.
3. **Ruby separation (V-26)** — require *two* agreeing signals: markedly smaller
   than the group's modal glyph size **and** sitting in the ruby position (above
   and horizontally overlapping for horizontal text, right and vertically
   overlapping for vertical). Size alone eats legitimately small body text.
   Excluded from the token stream, but the boxes are kept, so a tap on ruby can
   resolve to the base word rather than doing nothing.
4. **Line-break policy (V-28)** — the strong signal is that *a line stopping
   short of the group's trailing edge ended a flow; a line running to the edge
   wrapped*. That reads the evidence of how the text was actually set rather than
   guessing. Ties stay conservative — separate — per V-28.
5. **The offset↔pixel bridge** — `architecture.md`'s interpolation, on y instead
   of x when vertical, in both directions: offset → rect to draw highlights,
   point → offset to resolve taps. Must tolerate offsets owned by no element.

Then the UI: dim frame and bright text (D-33), highlights, hit-testing, the peek
sheet (D-30, D-31) and the in-sheet kanji swap (D-32).

**Still outstanding from the list this replaces: real test images.** The three
committed fixtures are all generated — clean text, plain ground, one font. They
prove ordering and wiring, not accuracy on real signage, and V-26 has no fixture
at all yet. D-75's accuracy finding argues for photographs sooner rather than
more generated images.

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

- [ ] Offset↔pixel lookup table built by walking ML Kit elements in reading order
- [ ] Per-character rectangles by interpolation within an element
- [ ] **Vertical text (縦書き) in test images from day one** — interpolate on y,
      columns right-to-left
- [ ] **Furigana excluded from the token stream** (V-26) — ruby is separated
      geometrically, by glyph size and baseline offset, in this same stage
- [ ] **Line-break policy decided geometrically** (V-28) — Japanese does not
      hyphenate, so a word may split across lines with no marker; Phase 4 ships a
      conservative newline join that hides such words rather than inventing them
- [x] **Confirm ML Kit's reading order for vertical text** — done 2026-08-26.
      Columns come back left-to-right, which is backwards, and stagger does not
      change it (D-75, V-10). Pinned by `VerticalTextOrderTest`.
- [x] **Decide where the geometry lives** — `:domain`, on a portable box type
      (D-76), for the test-speed reason
- [ ] Columns sorted right-to-left in stage 2, before concatenation (D-75)
- [ ] Real photographed fixtures — vertical and furigana'd; all three committed
      fixtures are generated (V-10, V-26)
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
