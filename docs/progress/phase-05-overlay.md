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

1. **Classification** — writing direction per group. The discriminator that the
   measured boxes validate: divide the long-axis ratio by the character count.
   It comes out **1.05–1.22** on the correct axis and **0.02** on the wrong one,
   so it is not a delicate threshold. Single-character groups are genuinely
   ambiguous and must fall back to how their siblings stack.
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
