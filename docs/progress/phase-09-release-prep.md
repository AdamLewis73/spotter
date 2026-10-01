# Phase 9 — Release prep

**Status:** not started — deliberately last
**Updated:** 2026-10-01

## Current state

Not started. Placed last by the project owner (2026-10-01): the attribution text
can still change until the dataset versions are frozen for release, so building
the screen earlier means building it twice.

## Next action

Blocked on everything before it. When it starts, begin with the attribution
screen.

## Done

- [ ] **In-app attribution screen.** EDRDG's licence for JMdict and KANJIDIC2
      requires acknowledgement on a separate screen reached from a menu, such as
      "About" — a launch-screen mention does not satisfy it. Text and shape are
      in `docs/attribution.md`; versions come from
      `tools/dictbuild/sources.lock.json`. A placeholder is acceptable until then.
      **Nothing is distributed before this exists.**
- [ ] The rest of what a store release needs — to be listed when the phase
      starts.

## Open questions

- Where the screen is reached from. `ux.md` places it inside Saved or a menu
  rather than the bottom bar (D-36), which satisfies EDRDG.
