---
name: phase
description: Start or resume work on a Spotter roadmap phase. Loads that phase's docs, verification cases, and decision checkpoints, then plans the work before coding. Use with a phase number or topic, e.g. "phase 1" or "phase overlay".
argument-hint: [phase-number-or-topic]
arguments: target
disable-model-invocation: true
allowed-tools: Read Glob Grep Bash(git status:*) Bash(git branch:*)
---

Target: **$target**

Plan and begin work on this phase. Do not write code until the checkpoint step below is cleared.

## 1. Load context

Read in this order, stopping when you have what the phase needs:

1. `docs/roadmap.md` — locate the phase, its output, and any checkpoints gating it.
2. `docs/progress/phase-NN-*.md` — current state, next action, open questions, notes. **If no progress file exists for this phase, create one** using the format in `docs/progress/README.md`.
3. `docs/verification.md` — the `V-##` cases for this phase.
4. Phase-specific docs, only what applies:

| Phase | Read |
|---|---|
| 1 Dictionary builder | `data-model.md` |
| 2 Text-input app | `architecture.md`, `data-model.md`, `ux.md` |
| 3 Stroke order | `data-model.md` (KanjiVG), `ux.md` |
| 4 Camera | `architecture.md` (scan pipeline) |
| 5 Overlay | `architecture.md` (stages 3–4), `ux.md` |
| 6 Saved lists | `data-model.md`, `decisions.md` D-27–D-29 |
| 7 SRS | `data-model.md`, `decisions.md` D-26–D-29 |
| 8 Export/import | `data-model.md`, `decisions.md` D-20 |

5. Individual `D-##` entries from `docs/decisions.md` that the phase touches. Read the index first, then only the relevant entries.

If `$target` is a topic rather than a number, grep `docs/` for it and work out which phase owns it.

## 2. Checkpoints — stop here

Check the checkpoint table in `docs/roadmap.md` for anything gating this phase.

**If a checkpoint applies and is unresolved, raise it before writing any code.** State the options and a recommendation, then wait. The project owner has explicitly asked to be stopped at these rather than having a default chosen silently.

## 3. Plan

Report before implementing:

- What this phase produces
- Ordered steps
- Which `D-##` constrain the work, and how
- Which `V-##` must pass
- Assumptions or gaps in the docs

## 4. Work

While implementing:

- Cite decisions in code comments and commits: `per D-11`.
- Verify against the `V-##` cases — they cover bugs that produce plausible output without erroring.
- New decision → append to `docs/decisions.md` with the next unused ID and add it to the index table.
- Resolved open question → promote it into `docs/decisions.md`; don't leave the answer only in a progress file.
- New silent-failure mode found → add a `V-##`, don't just fix it.
- Keep the progress file's **Current state** and **Next action** current as you go. Record dead ends in **Notes**.

## 5. On completion

Update the progress file status, the phase table in `docs/roadmap.md`, and the Status line in `CLAUDE.md`.

## Constraints

- The hard rules in `CLAUDE.md` are non-negotiable.
- Explain Android, Kotlin, and Compose concepts rather than assuming familiarity. SQL and Python analogies land well.
- Assume no code exists outside this repository.
