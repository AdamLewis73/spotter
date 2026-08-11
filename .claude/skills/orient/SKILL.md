---
name: orient
description: Load Spotter project context — current phase, progress, decisions, and repo state. Use at the start of a session or when resuming after a break.
disable-model-invocation: true
allowed-tools: Read Glob Grep Bash(git status:*) Bash(git log:*) Bash(git branch:*)
---

Orient yourself in this project, then report to the user.

## Repo state

- Branch: !`git branch --show-current`
- Uncommitted: !`git status --short`
- Recent commits: !`git log --oneline -5`

## Read, in this order

1. `docs/overview.md` — product thesis, worked example, glossary. **Skip only if already read this session.**
2. `docs/decisions.md` — read the **index table at the top only**. Do not read all 37 entries; read individual ones when a task touches them.
3. `docs/roadmap.md` — phase table, decision checkpoints, deferred backlog.
4. The progress file for the current phase in `docs/progress/`. Find it via the Status line in `CLAUDE.md`.

Do not read `architecture.md`, `data-model.md`, `ux.md`, or `verification.md` now. Load those when a task needs them.

## Report

Under 15 lines:

- **Current phase** and its status
- **Next action** from the progress file
- **Open questions** blocking work, if any
- **Repo state** — flag uncommitted changes or an unpushed branch
- **Checkpoints ahead** — any decision checkpoint gating the next action

Then ask what to work on. Do not start work or write code.

## Notes

- If the Status line in `CLAUDE.md` disagrees with the progress files, say so — it means a phase transition was recorded incompletely.
- Assume no code exists outside this repository.
