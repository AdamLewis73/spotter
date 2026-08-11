# UI / UX

Read `overview.md` first if you're new — it has a worked end-to-end walkthrough of the main flow and defines the Japanese-language terms used here.

The app's core interaction — tapping one specific word on a photograph — has no established convention to borrow from. It deserves more design attention than a typical app's navigation.

## Vocabulary used in these docs

| Term | Meaning |
|---|---|
| **Peek sheet** | A Material 3 `ModalBottomSheet` raised partway over the frozen scan, showing a one-line summary of the tapped word. Expanding it reveals the full word screen — they are the same component (D-30). |
| **Word screen** | The expanded sheet. Reading, meanings, component chips, examples. No tabs. |
| **Kanji screen** | Reached by tapping a component chip. Three tabs. Swaps in place inside the sheet (D-32). |
| **Component chips** | Small tappable elements on the word screen, one per constituent kanji, showing meanings only (D-06). |
| **Ambiguity chips** | A different thing: a row of candidate words shown when a tap could resolve to more than one token. See below. |

## Screen map

**Scan is the start destination.** The app launches straight into the live camera preview — there is no home screen, dashboard, or shortcut grid in front of it (D-61). The bottom nav is how you leave the scanner, never something you pass through to reach it.

```
Bottom nav: Scan · Saved · Review          (three — resist a fourth, D-36)
             ▲ start destination (D-61)

Scan
 └─ live preview + "text detected" indicator + large shutter
     └─ frozen image + overlay
         └─ tap word → PEEK SHEET
                        word · meanings only — NO reading (D-47)
                        [Save]  [Full Details]
             │
             ├─ expand, MULTI-character → WORD SCREEN      (same sheet, D-30)
             │              per reading:  reading
             │                            meanings + part of speech
             │                            example sentences
             │              component kanji chips LAST      (D-48)
             │    └─ tap chip → KANJI SCREEN  (in place, back arrow, D-32)
             │
             └─ expand, SINGLE character → KANJI SCREEN directly (D-49)
                            Overview | Examples | Stroke Order
```

Both routes reach **the same** kanji screen. One kanji, one screen.

### Word screen — no tabs

**One screen per written form** (D-48). Every reading appears as a section, because the app cannot tell which one applies (D-44) and showing three tappable entries would ask the user the question they came to have answered.

```
上手
  じょうず  skillful; proficient; good (at); adept
            flattery
  うわて    upper part
  かみて    stage left
Composed of:  上 above, up    手 hand
```

Reading → meanings, repeated per reading. **Component chips come last**, below every reading, showing meanings only (D-06).

**Obsolete readings appear too, marked as archaic** (D-53). 上手 has five readings; じょうて and じょうしゅ are historical and shown visually distinguished rather than hidden. Someone photographing a temple inscription or an old shopfront is exactly the person who needs them — and the app never knows which reading was scanned anyway, so hiding them would just leave a gap with no explanation. How strongly to mark them is a Phase 2 design question; V-21 covers the failure of not marking them at all.

**Example sentences are not rendered in v1** (D-51). They are ingested and sit in the dictionary, but only 41.4% of common senses have one, and that number is impossible to judge without seeing real screens. The slot in the layout — beneath each sense, above the next reading — is reserved:

```
  じょうず  skillful; proficient; good (at); adept
            ← examples would go here, per sense
            flattery
```

Two things to settle in Phase 2: whether to show them at all, and how to keep meanings and examples visually distinct at a glance. Both are design questions, not data ones — the data is already there.

### Kanji screen — three tabs

| Tab | Content |
|---|---|
| **Overview** | Meanings, on'yomi / kun'yomi, and — when the kanji is also a standalone word — an **As a word** section listing its senses (D-49). Example sentences there follow the same v1 rule as the word screen: ingested, not rendered (D-51) |
| **Examples** | Other **words** containing this kanji, grouped by reading, frequency-sorted (D-04) |
| **Stroke Order** | KanjiVG animation — paths drawn sequentially — plus the stroke count. **Show the number of paths being animated, not KANJIDIC2's figure**: the two disagree for ~1.7% of kanji, mostly 辶 forms, and 辻 labelled "5 strokes" while visibly drawing 6 is a contradiction the user watches happen (V-09) |

**Grade, classical radical and JLPT level are deliberately absent** (D-50, D-42). "5 strokes · Grade 1 · Radical 100" is three facts, two of which mean nothing to a non-Japanese learner. Stroke count moved to the Stroke Order tab, where it needs no explanation.

Note the **Examples tab shows words, never sentences.** Sentences attach to *words* and appear wherever word data does — including the Overview tab's "As a word" section. They can never attach to a kanji as a character, because no dataset records which sense a kanji contributes inside a compound (D-44).

The Examples tab expresses the same idea at both levels: **show every distinct way this thing is used.** For a kanji that means its different readings; for a word it means its sense variations.

Design this so that a word or kanji with only **one** reading reads as *information*, not as a broken screen. "Only one reading — this one's easy to remember" is genuinely useful to a learner. An empty-looking panel is not.

## The overlay

**Style (D-33):** dim the whole image and render detected text at full brightness, with a solid highlight on the selected word only. Drawing a box around every detected word is unreadable clutter; making the legible text itself the affordance reads as deliberate design rather than a debug view.

### Tap targets are the hard problem

Material's accessibility minimum for a touch target is 48dp. A kanji on a shop sign photographed from three metres away may occupy 12dp. All three mitigations below are needed — none is sufficient alone.

1. **Pinch-zoom and pan on the frozen image.** The primary fix, and wanted regardless. It's also a strong argument for freeze-frame (D-02): a moving feed cannot be zoomed and inspected.
2. **Ambiguity chips.** When a tap could resolve to more than one token, show a small horizontal row of candidates near the touch point rather than silently guessing. This doubles as the compound-versus-word interface: tapping 先 offers both 先 and 先生, which is exactly the pedagogical point (D-07).
3. **Snap to nearest token** within a tolerance radius, so near-misses still land.

### Vertical text

Japanese signage, menus, and book spines are frequently written **縦書き** — top to bottom, right to left. Overlay geometry, reading order, and sheet placement must all tolerate it. See `architecture.md` for the coordinate-mapping implications.

Test against vertical text from the first day of overlay work. Discovering it later means redoing the most error-prone stage of the pipeline.

## Typography

**Furigana rendering is custom work.** Compose has no built-in ruby-text support. A composable that draws small kana above a word — with correct centering, sizing, and line breaking — will appear on nearly every screen in the app. Build it once, early, and reuse it everywhere.

Whole-word ruby only (D-14): せんせい positioned over 先生 as a unit, never split per character.

Include a **global furigana toggle.** Advanced learners find constant furigana distracting, and hiding it during review makes recall genuinely harder in a useful way.

**Bundle Noto Sans JP (D-34).** Without an explicit Japanese font, Android may render kanji using Chinese glyph forms — 直, 骨, 令, and 化 all differ visibly between the two. In an app that teaches people to read and write kanji, that is a correctness bug rather than a polish issue.

## Context of use

This app gets opened while standing in a shop, sitting at a restaurant table, or waiting on a train platform. That drives several things that are easy to miss when designing at a desk:

- **One-handed reachability.** Nothing important in the top corners — the user is holding the phone up with one hand. The bottom-sheet pattern is already correct for this.
- **Dark mode is not optional.** Evenings and dim interiors are prime usage time.
- **Large shutter target**, comfortably thumb-reachable while the phone is raised.
- **Fast cold start.** If reaching a working camera takes four seconds, people give up and open Google Translate instead.

## Easy to skip, shouldn't be

**Onboarding.** The app's premise is not self-evident from a viewfinder — a new user sees a camera and assumes it's a translator. Three screens explaining the 生 → 先生 → 生産 idea, or better, a bundled sample image they can tap around in *before* granting camera permission. Request the permission after they understand why it's needed.

**Empty states.** "No saved words yet" and "Nothing due for review" are the screens a new user sees most often, which makes them the best onboarding surface available. Don't leave them blank.

**Unresolvable saved items (D-40).** A different problem from an empty state, and easier to get wrong. If a dictionary update retires a word's `(text, reading)`, the saved card **still renders** — text, reading, review history, and an explanation. It is never filtered out of the list.

> **上手** ・ うわて
> ⚠️ Merged in the June dictionary update.
> Now listed under **上手 (じょうず)** → [View]
> *Reviewed 11 times · next due in 4 days*

The meaning shown comes from `snapshot_gloss` (D-43); the merge target from the dictionary's `changes` table (D-39). With no `changes` entry, the same card reads "no longer in the dictionary" — the card appears either way.

Silently omitting the card is the failure this prevents, and it is invisible: the list is simply one shorter than the user remembers. They cannot tell whether the app lost their word or they misremembered saving it.

**Attribution screen.** A licence obligation under CC BY-SA, and EDRDG's statement is specific about its shape for mobile apps:

> acknowledgement must be made **on a separate screen accessed from a menu, such as one labelled "About"** — it is not sufficient just to mention it on a start-up/launch page.

So a line in onboarding does not discharge it. Placing the screen inside Saved or a menu (D-36) satisfies the requirement. Exact wording, per-dataset credits and the shipped dataset versions are in `attribution.md`.

**Storage screen (D-25).** Usage breakdown, a clear action, and a "save scan images" toggle. Users who discover an app consuming 500 MB uninstall it.

**Content settings (D-54).** Two toggles, living wherever Settings lands — not in the bottom nav (D-36):

| Toggle | Default |
|---|---|
| Show explicit content — vulgar, sensitive, derogatory | **off** |
| Show slang & colloquial | **on** |

The defaults differ on purpose. Explicit senses are ~900 out of 252,927, so hiding them costs almost nothing and showing them by default risks an unpleasant surprise. Slang and colloquial are ~3,900 and pervade real signage, menus and manga — hiding those by default means failing to explain the text a user is standing in front of.

**Never filter a word to zero senses.** If everything is filtered, show it regardless. An empty result reads as "the dictionary doesn't have this word", which is a broken app rather than a discreet one — the same failure D-40 prevents for saved items. See V-23.

## Process

**Wireframe the frozen-scan screen and the expanded sheet before writing any Compose code** — paper is fine. The overlay/tap/sheet interaction is the app's entire identity, and problems are enormously cheaper to find with a pencil than with a rebuild. Everything else (Saved, Review) follows conventional patterns that can be borrowed wholesale.

Set up Material 3 design tokens — colors, type scale, spacing — in the first UI commit (D-35). Ten minutes then; a refactor touching every composable later.
