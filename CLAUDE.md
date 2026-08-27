# Spotter — Agent Context

**Spotter: Kanji Scanner** (D-63). Android app: point the camera at Japanese text, tap a word on the frozen photo, see what it means and which kanji compose it. Save words to lists and review them.

**The scanner is the product** (D-61) — the app opens on the camera, and simplicity outranks every other principle. Free, no ads, no paywall on scanning (D-62). It teaches kanji **in context** rather than translating: 生 alone is "life", but 先生 is "teacher" and 生産 is "production".

**This file is loaded into every session. Keep it short** — it's a router, not documentation. Details live in `docs/`, read on demand.

**If you are new to this project, read `docs/overview.md` first.** It has a worked end-to-end example of the main flow and a glossary of the Japanese-language terms the other docs assume.

---

## Hard rules

Violating any of these destroys user data or forces a rewrite. Each links to fuller reasoning.

1. **Never** call `fallbackToDestructiveMigration()` — it silently deletes the entire user database. Banned in all build types, including debug. (D-17)
2. **Never** store dictionary row IDs in user data. Dictionary rebuilds reassign them, corrupting saved words with no error. Use the natural key: word text + reading. (D-11)
3. User-data primary keys are **UUIDs**, never auto-increment — offline devices generate colliding integers, and it's unfixable afterward. (D-15)
4. Every user-data row has `updated_at`, and deletions are **soft** (`deleted_at`), never hard `DELETE`. (D-16)
5. Saved-item identity is **(text, reading)** — never text alone. 上手 is three different words. (D-12)
6. `:domain` and `:data` modules **must not import `android.*`**. This is the iOS-portability line and cannot be retrofitted cheaply. (`architecture.md`)
7. Room schema export stays **on**; generated schema JSON is committed to git. (D-18)
8. Images are files on disk with **relative** paths in the DB — never BLOBs. (D-24)

## Where things are

| Need | Read |
|---|---|
| What the app is, worked example, glossary | `docs/overview.md` |
| Why something was decided (numbered `D-##`) | `docs/decisions.md` |
| Stack, modules, scan pipeline, navigation | `docs/architecture.md` |
| Datasets, both schemas, migrations, backup | `docs/data-model.md` |
| Screens, overlay, typography, interaction | `docs/ux.md` |
| Expected values for silent-failure bugs (`V-##`) | `docs/verification.md` |
| Required licence text for the in-app screen | `docs/attribution.md` |
| Phases, decision checkpoints, deferred backlog | `docs/roadmap.md` |
| Current state of work in flight | `docs/progress/` |

## Do not read these paths

Committed on purpose (D-55), but they are third-party data and generated artifacts, never project code. Opening one wastes a large amount of context for nothing:

| Path | What |
|---|---|
| `tools/dictbuild/data/` | ~29 MB of JMdict, KANJIDIC2, KanjiVG, JmdictFurigana sources |
| `tools/dictbuild/baseline/` | 3.5 MB generated key list for the `changes` diff |
| `*.db`, `*.gz`, `*.tsv` | build output and compressed data |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/` | Gradle-generated wrapper, ~15k tokens. Never hand-edited — regenerate with `gradle wrapper` rather than reading or patching it |

To inspect their *structure*, use `tools/dictbuild/inspect_sources.py`, which prints small representative samples. `.gitattributes` marks them binary so diffs stay collapsed.

## Conventions

- **Reference decisions by ID** in commits and code comments: `per D-07`.
- **Decision IDs are permanent; decisions are not.** To change one: mark the old entry `SUPERSEDED by D-##`, leave its text intact, and append a new entry with the next unused ID. Never delete or renumber — the reasoning behind an abandoned path stops it being re-proposed later.
- **Changing a decision means grepping `docs/` for its ID** — other docs and `verification.md` cases cite decisions, and a superseded decision leaves stale assertions behind. Full procedure at the top of `docs/decisions.md`.
- **New decision** → append to `docs/decisions.md` with the next ID.
- **Resolved open question** → promote it into `docs/decisions.md`; don't leave the answer only in a progress file.
- **Finished phase** → update `docs/progress/`, the table in `docs/roadmap.md`, and the Status line below.
- **Never continue work on a branch that has already been merged.** Its pull request is closed, so further commits go unreviewed and are easily stranded — they look merged in the branch list while being absent from `master`. Check with `git branch --merged master` before starting, and branch fresh from the current base. If a branch necessarily stacks on an unmerged one, say so in the PR description.

## Communication

Explain Android, Kotlin, and Compose concepts rather than assuming familiarity — these are new to the project owner. Analogies to SQL and Python land well; analogies to Android idiom do not.

The project owner has asked to be **stopped before decisions that are expensive to reverse** — see the checkpoint table in `docs/roadmap.md`. Raise those before writing code rather than picking a default and proceeding.

**Assume no code exists outside this repository.** Everything is written in-session and committed; there is no work-in-progress held elsewhere. If something appears to be missing, it has not been built yet.

## Status

**Phase 1 — Dictionary Builder: complete.** `tools/dictbuild/` builds a 99.7 MB `spotter.db` (30.3 MB gzipped) from four pinned sources in ~45 seconds, byte-reproducible from identical sources (D-58, D-64). `verify.py` passes 11 of 11 verification cases.

**Phase 2 — Android app, text input only: feature-complete.** Type Japanese → Kuromoji segments it → tap a word → readings, meanings, example sentences and component boxes → tap one → the kanji screen, whose Examples tab is D-04. Longest-match offers the words the parse hides (東京都 → 京都). The dictionary ships in the APK and refreshes itself when it changes. Every `V-##` case this phase owns is met; the **user-data checkpoint** (D-15–D-18, D-43) is the one item left on its list and lands with Save in Phase 6 by design.

**Phase 3 — Stroke order: complete.** The kanji screen's third tab draws KanjiVG's real outlines — stage with a centre crosshair, stroke-by-stroke animation, play/pause, speed control, and a tappable per-stroke grid — following design artboard **3b**. Its **Trace** mode makes the same stage writable so the learner draws the character over the ghost — no scoring, no scheduler, independent of review (D-72, which superseded D-71).

**Phase 4 — CameraX + ML Kit: complete.** The app **opens on a live viewfinder**, the shutter freezes a still (D-02), ML Kit reads the Japanese off it, and the text goes into the Phase 2 tokenize-and-look-up path — photograph a sign, tap a word, get its readings and senses. The camera is the launcher destination and the Phase 2 text box is a **debug path**, not a second front door (D-73); `/inspect` is unaffected. The ML Kit model is **bundled** at a measured ~14.8 MB per device (D-74).

**Current phase:** Phase 5 — the tappable overlay, in progress. **This is the core of v1** and the highest-risk work in the project: stage 4's offset-to-pixel bridge, plus vertical text (V-10), tap resolution (V-11) and furigana separation (V-26). `RecognizedText` already carries a box and character offset per element, which is what stage 4 consumes.

**The geometry module is built** (`domain/scan/ScanLayout`, D-76): reading order, writing direction, ruby separation and the line-break policy, on a portable box type in `:domain` so a case costs seconds rather than an emulator round trip. Stage 2 now emits a laid-out `ScanLayout` rather than a raw string, so 縦書き columns read right to left (D-75), ruby stays out of the token stream (V-26) and lines join only on evidence (V-28).

**The overlay is built to artboard 1a**: the frame dims and the **photograph's own pixels** are repainted at full brightness over the rectangles `ScanLayout` measured (D-78), the tapped word takes a jade band, and a peek sheet gives the word, its glosses and two actions — with no reading, per D-47. Retyping the text was built first and rejected on the evidence (D-77): it ghosts, and it renders a misread as authoritative type — the reveal cannot lie about what the sign says, because it *is* the sign. `ScanProjection` handles the `ContentScale.Crop` transform and is tested in `:domain` apart from the interpolation, because on screen a wrong transform and a wrong interpolation look identical.

**The sheet expands** (D-30): one component at two heights — a 30% peek and a 92% word screen — dragged by its handle or opened by *Full details*, with the kanji screen swapping its contents in place at full height (D-32). Back unwinds one level at a time: kanji → word → peek → frozen frame → viewfinder. The scan drives the same `WordLookupViewModel` the text route uses, so alternates (D-70) and the lone-kanji rule (D-49) come for free.

**Phase 5 is essentially done.** What remains is Save, which is drawn and disabled until the Phase 6 user-data checkpoint settles the schema, plus the optional artboards 1b (ambiguity chips) and 1c (loupe).

**Read `docs/progress/phase-05-overlay.md` first**, then `phase-04-camera.md` — which carries the camera and ML Kit knowledge, including two measurement traps that produce confident wrong answers — then `phase-02-android-text-input.md` for the build gotchas and device knowledge, several of which present as misleading errors. Both `phase-03-stroke-order.md` and `phase-04-camera.md` carry how to read the Claude Design project without burning a context window on a 106 KB file.

Run it with **`/launch`** (starts it for you to drive) or **`/inspect`** (drives it against specific words and reports). Build with `./gradlew :app:assembleDebug`, which needs `JAVA_HOME` pointing at Android Studio's JBR — there is no other JDK on this machine. AGP 9 removed the Kotlin Android plugin, so most published advice is now a build error.

**Design is settled** (D-67: warm near-black, one jade accent, IBM Plex beside Noto Sans JP), and the Claude Design project has artboards for more screens than the docs used to claim — check it before designing anything in place. Every composable reads colour and type through `Color.kt` and `Type.kt` — anything new must do the same, and anything rendering Japanese must ask for `SpotterJapanese` explicitly, because IBM Plex contains no CJK and falls back silently to the system font (the exact failure D-34 exists to prevent).
