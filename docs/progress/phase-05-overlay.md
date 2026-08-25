# Phase 5 — Tappable overlay

**Status:** not started — unblocked, and now the current phase
**Updated:** 2026-08-24

## Current state

Not started. **Both halves this phase bridges now exist.** Phase 4 produces pixel
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
  wrong for vertical text (V-10) and nothing has been reordered, so nothing has
  to be un-reordered.

One wrinkle to expect: lines are joined with a newline separator, so a few
character offsets belong to **no element**. A tap can never land on one, because
no rectangle maps to one — but a lookup table that assumes every offset has a box
will be wrong at exactly those positions.

## Next action

**First, and it is not the coordinate work: find out what ML Kit does with
vertical text.** Stage 2 takes ML Kit's block-then-line order as given and
concatenates it — so if the recognizer walks 縦書き columns left-to-right, or
interleaves them, **the string is already scrambled before stage 4 ever sees a
pixel**. That would make vertical text a stage 2 bug wearing a stage 4 costume,
and it would change how the bridge is designed.

It is a ten-minute experiment and it gates the design: generate or photograph a
vertical fixture, run `JapaneseTextRecognizer` over it in an instrumented test
like the horizontal one, and read the string that comes back. Do this **before**
building any lookup table.

Then, in order:

1. **Collect real test images** — vertical (V-10) and furigana'd (V-26), per
   `architecture.md`: discovering vertical text after building a horizontal-only
   implementation means redoing this stage, which is the most error-prone work in
   the project. The one committed fixture
   (`app/src/androidTest/assets/sign-horizontal.png`) is generated, horizontal
   and deliberately easy — it proves wiring, not accuracy.
2. **Stage 4, the offset↔pixel bridge.** Walk `RecognizedText.elements`, which
   are already in reading order with offsets recorded, and interpolate within
   each element for per-character rectangles.
3. **Settle the line-break policy (V-28)** in the same pass, because it is the
   same geometric question — see below.

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
- [ ] Confirm ML Kit's reading order for vertical text — a stage 2 question that
      gates the stage 4 design (see Next action)
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
