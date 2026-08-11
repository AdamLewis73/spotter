# Phase 2 — Android app, text input only

**Status:** not started
**Updated:** 2026-08-09

## Current state

No Kotlin exists. No Gradle project, no app module, no `spotter.db` built in
this working copy. Phase 1 left the dictionary builder complete and verified;
everything downstream of it is unbuilt.

## Next action

Hold at the **first-commit checkpoint** — module structure, and the rule that
`:domain` and `:data` import nothing from `android.*` (`roadmap.md` checkpoint
table, `architecture.md`). Settle that with the project owner, then scaffold the
Gradle project.

## Done

- [x] Checkpoint: module structure agreed — `:app` / `:data` are Android
      libraries, `:domain` is a plain Kotlin/JVM module (D-60)
- [ ] Gradle project scaffolded; empty app builds and launches
      (application ID `com.spotterkanji.app`, D-63)
- [ ] Layering rule enforced automatically (lint rule or CI grep), not by convention
- [ ] `spotter.db` built and copied into app assets
- [ ] Asset copy automated as a Gradle task — a stale asset serves old data silently
- [ ] Room read-only dictionary DAOs over the Phase 1 schema
- [ ] Checkpoint: Material 3 + design-token layer before the first UI commit (D-35)
- [ ] Kuromoji tokenization behind the `Tokenizer` interface in `:domain` (D-08)
- [ ] JMdict longest-match alternates (D-07)
- [ ] Text-input screen: paste `先生と生産`, get tokens
- [ ] Word screen — one section per reading, component chips last (D-48, D-06)
- [ ] Kanji screen — Overview / Examples tabs; Stroke Order is Phase 3 (D-05)
- [ ] Single kanji routes straight to the kanji screen (D-49)
- [ ] Checkpoint: UUID keys, `updated_at`, soft delete, schema export on,
      destructive migration off (D-15 – D-18) before any user-data write
- [ ] Checkpoint: `snapshot_gloss` on `study_item` (D-43), same commit
- [ ] Relevant `V-##` cases from `verification.md` added to this list
- [ ] `/launch` skill at `.claude/skills/launch/SKILL.md` (roadmap housekeeping)

## Open questions


- **Do example sentences get rendered? (D-51)** Already in the dictionary,
  shown nowhere. 41.4% coverage of common senses, ceiling ~43%. Deliberately
  not being judged on paper — build the word screen without them, look at 先生,
  上手 and 生 on a device, then turn them on and look again.
- If they stay: sense-attached only, or word-level examples too where no
  sense-attached one exists?
- Hilt now or after the app works? `architecture.md` says after.

## Notes

- `spotter.db` is gitignored, so a fresh clone has none. Build it with
  `python fetch.py` then `python build.py` in `tools/dictbuild/` (~45 s).
- Kuromoji is JVM-only and cannot run on iOS. It is the one part of the
  tokenizer that will not port — which is why D-07 leans on JMdict longest-match
  as well.
- Room has Android dependencies, so `:data` is not strictly platform-free. That
  is an accepted compromise (`architecture.md`); it is not a reason to relax the
  `android.*` rule anywhere else.
