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

**Phase 1 — Dictionary Builder: complete.** `tools/dictbuild/` builds a 99.7 MB `spotter.db` (30.3 MB gzipped) from four pinned sources in ~45 seconds. `verify.py` passes 10 of 10 verification cases.

**Current phase:** Phase 2 — Android app, text input only. **In progress.** The Gradle scaffold builds: `:app` / `:data` / `:domain`, with `:domain` a plain Kotlin/JVM module so `android.*` is a compile error there (D-60). App is a placeholder; no dictionary wired up yet.

Build with `./gradlew :app:assembleDebug` (needs `JAVA_HOME` pointing at Android Studio's JBR). Scaffold gotchas and pinned versions are in `docs/progress/phase-02-android-text-input.md` — **read its Notes before touching build files**, especially that AGP 9 removed the Kotlin Android plugin.

Two checkpoints remain in this phase: **Material 3 design tokens** before the first real UI (D-35), and the **user-data rules** before the first write (D-15–D-18, D-43).
