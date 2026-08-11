# Overview

**Start here if you're new to this project.** This document explains what the app does, why, and defines the Japanese-language terms used throughout the other docs.

## The problem

A learner standing in front of Japanese text wants one thing: *what does this say, and what can I learn from it?* The tools that answer the first half destroy the second.

**Translation apps** replace the Japanese with English, which erases exactly the information a learner needs. **Dictionary apps** answer properly but make you already know how to type what you're looking at — which is the problem you had.

**Camera dictionaries** are the right shape, and they exist. The complaint is not that they're missing; it's that they're cluttered, the scanner is buried several taps deep in a general-purpose dictionary, and the camera is usually the paywalled feature (D-61).

Underneath all of that sits the fact that makes the app worth building at all: kanji don't have fixed meanings in isolation — they have meanings *in context*.

- 生 alone means "life / birth". In 先生 it's "teacher". In 生産 it's "production".
- 手 alone means "hand". In 上手 it's "skilled". In 歌手 it's "singer".

So the app keeps the Japanese and explains it, rather than replacing it.

## The product

**A scanner, first and above all.** Open the app and the camera is already there — no home screen, no menu, no shortcut grid. Point at a sign, a menu, a package. Freeze the frame, tap a word, and see what it means, how it's read, and which kanji compose it. Save words to lists and review them.

Everything else in this document is what happens *after* the tap. The scan is the product; the study loop is what makes it worth keeping (D-61).

The learning claim: **contextual exposure to real text beats memorizing isolated characters.**

**Free, with no paywall on scanning, and no ads** (D-62). This is a positioning choice and also a structural one — the offline-first architecture (D-46) means there is no per-scan cost to recover.

### What "context" means here (D-44)

Precisely: **the word is the context for the kanji.** 生 means "teacher" *inside 先生*. The app delivers that completely — the tokenizer finds the word boundary, the dictionary supplies the word's meaning, and the kanji screen shows every other word using that kanji grouped by reading.

It does **not** attempt to work out which of a word's several meanings applies in a particular photograph. 甘い on a sweet wrapper means "sweet"; in 採点が甘い it means "lenient". The app shows every sense and lets the learner choose — which the **usage completeness** principle below argues is the better teaching anyway.

Worth stating up front because "contextual meaning" reads, on a first pass, as the larger claim. Sentence-level comprehension is deliberately post-v1 (D-45).

## Worked example: what actually happens

A user photographs a sign reading `先生と生産`. This walks the whole system end to end.

**1. Scan.** The camera preview shows a "Japanese text detected" indicator. The user presses the shutter. The frame freezes (D-02) and everything afterward happens on that still image.

**2. Recognition.** ML Kit reads the image and returns the text `先生と生産` along with pixel rectangles for each chunk it found.

**3. Tokenization.** Japanese has no spaces, so the app must decide where words begin and end. Kuromoji splits it into 先生 / と / 生産. Separately, longest-match against the bundled dictionary notes that position 0 also matches the shorter word 先, and keeps it as an alternate (D-07).

**4. Overlay.** The image dims, leaving the recognized text bright (D-33). Each detected word is now tappable.

**5. Peek.** The user taps 先生. A bottom sheet rises partway:

> **先生**
> teacher; instructor; master
> `[Save]` `[Full Details]`

**No reading here** (D-47). The app would be guessing, and a learner who already knew the reading wouldn't be scanning the word.

**6. Word screen.** Dragging the sheet up expands it (D-30) into the full word screen — one section per reading, chips last (D-48):

> **先生**
> **せんせい**
>  1. teacher; instructor; master
>  2. sensei — title for a teacher, doctor, lawyer or artist
>
> Composed of: `先 before, ahead, previous` `生 life, genuine, birth`

Note the component chips show meanings but **not** readings (D-06). Example sentences are ingested but not shown in v1 (D-51) — only 41% of common senses have one, and whether that reads as useful or as a gap is a judgement best made against real screens in Phase 2.

**7. Kanji screen.** The user taps the 生 chip. The sheet swaps in place, with a back arrow (D-32), to a three-tab kanji screen. The **Examples** tab is where the app's core idea lands:

> **セイ** — 先生 teacher · 学生 student · 生活 daily life
> **ショウ** — 一生 a lifetime · 誕生日 birthday
> **なま** — 生ビール draft beer
> **い(きる)** — 生きる to live

The user sees, without reading any authored explanation, that 生 carries different sounds and senses depending on the company it keeps. That's the entire product thesis in one screen (D-04).

*Had they scanned 生 on its own* — on a beer tap, say — they would land on **this same kanji screen directly**, skipping the word screen (D-49). Its Overview tab would additionally list 生's senses as a standalone word (なま "raw", せい "life", き "pure"), each with example sentences. One kanji, one screen, whichever direction you arrive from.

**8. Save and review.** Tapping Save stores 先生 as a study item, optionally into a named list like "Street Signs". It enters the FSRS schedule and reappears for review at the right time. The scan image is saved with it, so the word stays attached to the place it was found.

## Product principles

1. **One obvious action.** The app opens on the camera. Every screen has a single clear next move. Where a feature and a clean first screen conflict, the feature loses or moves behind the tap (D-61).
2. **Word-first, kanji-second.** The unit of study is the word. Kanji screens are reference material reached by drilling down.
3. **Offline always.** No network required for any core function. No runtime LLM calls.
4. **Show, don't assert.** Rather than authoring an explanation of why 生 means teacher in 先生, show every common word using 生 grouped by reading and let the pattern teach.
5. **Usage completeness.** A learner should be able to look at any word or kanji and answer: *"Do I know how to use this in all the ways it's used?"* A word with only one reading isn't a thin screen — it's useful information: *this one is easy.* The UI should say so rather than looking empty.
6. **Real-world capture.** The scan image is saved with the word. "That sign outside the ramen shop" is a stronger memory hook than a bare flashcard.
7. **Free where it counts.** Scanning is never metered, gated, or interrupted by an ad (D-62).

**Principle 1 outranks the rest.** It is the one that is hard to hold, because every other principle argues for putting something on screen. When they conflict, simplicity wins — that is the entire reason this app has a reason to exist alongside the incumbents.

## Core features

| Feature | Summary |
|---|---|
| **Scan** | Camera → freeze frame → tap detected words → detail sheet |
| **Word screen** | Reading, meanings, component kanji, example sentences |
| **Kanji screen** | Overview / Examples / Stroke Order tabs |
| **Saved lists** | Multiple user-named lists ("Street Signs", "Food Menu") |
| **SRS review** | FSRS-scheduled quizzes over saved words |

## Platform

Android first, targeting recent API levels with a low `minSdk` for reach. iOS is a later possibility; the codebase is layered so business logic and data can migrate via Kotlin Multiplatform rather than being rewritten. See `architecture.md`.

## Glossary

Terms used throughout these docs.

| Term | Meaning |
|---|---|
| **Kanji** | Chinese-derived logographic character. ~2,000 in common use. |
| **Kana** | The two phonetic scripts — hiragana (ひらがな) and katakana (カタカナ). |
| **Furigana** | Small kana printed above kanji to show pronunciation. |
| **On'yomi** | A kanji's Chinese-derived reading. Typically used in multi-kanji compounds. |
| **Kun'yomi** | A kanji's native Japanese reading. Typically used standalone or with kana endings. |
| **Jukujikun** | A word whose reading attaches to the word as a whole and cannot be split per character. 明日 = あした. Important because it invalidates per-character reading display (D-06). |
| **Compound (熟語)** | A word made of two or more kanji. 先生, 生産. |
| **Radical (部首)** | The classical indexing component of a kanji. Exactly one per kanji, drawn from a set of 214 established in a 1716 dictionary. Distinct from the full set of visual components a kanji contains. |
| **Sense** | One distinct meaning of a word — a lexicography term, nothing to do with the five senses. English "bank" has three: financial institution, side of a river, to tilt an aircraft. 上手 (じょうず) has two: *skillful* and *flattery*. JMdict stores each as a `<sense>` element. |
| **Gloss** | One English phrase expressing a sense. A single sense usually has several near-synonymous glosses — "teacher; instructor; master" is **one** sense with three glosses, which is why the peek sheet renders them on one line. |
| **Tokenization** | Splitting Japanese text into words. Necessary because Japanese is written without spaces, and technically the central problem of this app. |
| **Morphological analyzer** | A tool that performs tokenization, also returning part of speech and dictionary base forms. Kuromoji is one. |
| **SRS** | Spaced Repetition System. Schedules reviews at growing intervals timed to just before predicted forgetting. |
| **FSRS** | Free Spaced Repetition Scheduler. The modern open-source SRS algorithm, successor to SM-2, adopted by Anki. |
| **縦書き (tategaki)** | Vertical writing — top to bottom, right to left. Common on signage and book spines, and a real constraint on the scan overlay. |
| **Peek sheet** | This project's term for the partially-expanded bottom sheet shown when a word is tapped on a scan. See D-30. |
| **Component chips** | This project's term for the tappable per-kanji elements on the word screen. See D-06. |
