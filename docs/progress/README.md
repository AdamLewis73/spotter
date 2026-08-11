# Progress files

One file per roadmap phase. Every planned phase has a stub from the outset, so
there is always somewhere to park a finding that belongs to a phase other than
the current one — those are exactly the notes that get lost otherwise.

A stub is thin on purpose: **Current state** says "not started", **Next action**
says what it is blocked on, and **Done** lists the checkpoints and the work the
roadmap already commits to. Everything else waits until the phase starts.

*This rule changed on 2026-08-09.* It previously said files were created when a
phase started, not in advance.

These are the **volatile** half of the docs. Keep them separate from `decisions.md` so that reading current state doesn't drag stable content along, and so decisions stay stable while progress churns.

## Format

Each file has a fixed shape so an agent can read the top and stop:

```markdown
# Phase N — <name>

**Status:** not started | in progress | blocked | done
**Updated:** YYYY-MM-DD

## Current state
Two or three sentences. What works right now, what doesn't.

## Next action
The single next thing to do. Not a list.

## Done
- [x] ...

## Open questions
Things needing a decision. Promote to `decisions.md` once resolved.

## Notes
Gotchas, dead ends, things that cost time. Most valuable section
for a future session — write down what surprised you.
```

## Rules

- **Current state** and **Next action** stay at the top. A session should get oriented from the first 10 lines.
- Resolving an open question means adding a `D-##` to `decisions.md` and linking it here — never leaving the answer only in a progress file.
- Record dead ends. "Tried X, it failed because Y" saves a future session from repeating it and is the most common thing lost between sessions.
- Check `docs/verification.md` when starting a phase — it lists the expected values for bugs in that phase that produce plausible output without erroring. Add the relevant `V-##` cases to the phase's Done checklist.
- Finding a new silent-failure mode means adding a `V-##` case, not just fixing it. The next such bug is usually in the same family.
- Finishing a phase means setting status to `done` and updating the table in `roadmap.md` plus the **Status** line in `CLAUDE.md`.
