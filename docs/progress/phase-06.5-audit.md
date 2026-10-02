# Phase 6.5 — Audit

**Status:** done — three checks owed on a device
**Updated:** 2026-10-01

## Current state

An aside between Phases 6 and 7, at the project owner's request: read the whole
codebase for anything broken, messy or done wrong, fix what is small, and bring
anything that changes design or flow to the owner first. Eight bugs are fixed,
four decisions were taken with the owner (D-97 to D-100), and three verification
cases came out of it (V-32 to V-34).

**None of the Kotlin was compiled in the session that did this.** It ran in a
cloud container where Google's Maven repository is blocked, so `:app` and
`:data` could not build. `:domain` was tested through a scratch Gradle project
(107 of 107 pass), the dictionary builder ran in full (12 of 12 `verify.py`
cases), and the trickier Kotlin patterns were checked in isolation. CI is the
first real build of `:app` and `:data`.

## Next action

**Run it on the emulator with `/launch` and look at the three things only a
screen shows** — listed under *Owed on a device* below. Then Phase 7.

## Owed on a device

- [ ] **The camera handover (D-100).** Freeze a frame, wait more than 10 s,
      press Retake: the photo should hold, then fade into the live picture —
      never black. `adb shell dumpsys media.camera` while idle should show the
      camera closed. Tune the 10 s there.
- [ ] **The selection band (V-33).** Tap a word in the middle of a line: it gets
      the band, and only it. The band reaches about a fifth of a glyph past the
      word and may nick the neighbours' edges — judge whether that reads.
- [ ] **Japanese forms in user text (V-32)**, on an emulator set to English.
- [ ] **The peek (D-99).** Tap a word on a scan: the peek sits on top of the
      bottom bar with Save and Full details visible, and the word strip across
      its top. Swipe the strip; tap a chip and the band moves on the photo. Drag
      up — the strip is gone; Back — it returns. Try a short emulator too
      (640 dp): the peek grows to fit rather than clipping its buttons.

## Done

### Bugs fixed

- [x] **A scan of a long sign crashed the app on Android 8–11.** Longest-match
      sends every candidate substring to SQLite as one `IN (...)` query, one
      bound variable each, and SQLite before 3.32 refuses more than 999. The
      99-character notice in `RealNoticeLayoutTest` makes 1,069. Uncaught in
      `viewModelScope`, so the app died. Batched at 500, with an instrumented
      case using that notice. A list's thumbnails had the same limit at 1,000
      words and are batched the same way.
- [x] **The selection band framed the whole line, or nothing** (V-33). A
      regression from `9c45bea`.
- [x] **Staging the same new list name twice crashed the picker** — two rows
      with one `LazyColumn` key. Duplicates are ignored.
- [x] **A failed photo write after *Add* crashed the app**, with the word already
      filed. It now keeps no photo, the normal state D-94 describes.
- [x] **Kanji past the basic plane were not kanji** (V-34). 𠮟る had no component
      box; a lone 𩸽 skipped the kanji screen. `:domain` now counts by code point.
- [x] **User-typed Japanese could render in Chinese forms** (V-32, D-98).
- [x] **On a long sign the full-height sheet's word strip pushed the word out of
      reach** (D-99). 63 chips, 528 dp; 0 dp left for the word on a short phone.
- [x] **The scan sheet ran under the bottom bar.** Since D-90 put the bar on
      the camera, the scan screen runs under it and only the shutter was lifted
      clear. Measured at Pixel 9 size: the peek's buttons cleared the bar with
      gesture navigation but were half hidden with three-button navigation or a
      three-line meaning, and on a word screen long enough to scroll, the
      component boxes stayed behind the bar even scrolled to the end. First
      reported as "the buttons are always hidden", which was wrong — the peek's
      contents stack from its top, so short content cleared the bar.

### Decided with the owner

- [x] D-97 — the bottom bar keeps the system font (Roboto), by preference.
- [x] D-98 — no Chinese letterforms anywhere: `ja` locale on every style, Noto
      Sans JP on user text.
- [x] D-99 — only words are chips; the strip is one swipeable row, at the top
      of the peek and gone from the full word screen.
- [x] D-100 — the camera is released 10 s into a frozen frame.

### Housekeeping

- [x] Stale comments from earlier phases corrected — notably "v1 only ever
      writes WORD", false since D-92, which Phase 7 would have read.
- [x] Dead code removed: `WordLookupState.saved` and its watcher (unread since
      D-91 retired the toggle, and a live query per word), four unused strings.
- [x] `build.py --only` marks its output partial, and the Gradle staging task
      refuses it; a one-stage database had been shippable as complete.
- [x] `app/proguard-rules.pro` created — the build named a file that did not
      exist.
- [x] The on-demand Claude review now covers the Kotlin.
- [x] README status brought up to date (it still said Phase 2); dictionary size
      corrected to 100.1 MB.
- [x] The attribution screen the data licences require is now on the roadmap, as
      Phase 9.

## Open questions

- **The rest of the Roboto.** Besides the bottom bar, list names on Saved, the
  kanji screen's tabs, dialog titles and the camera permission title all use
  Material styles `Type.kt` never defined, so they render in the system font
  rather than IBM Plex. The owner preferred Roboto for the bar but does "not
  necessarily love either font". Left exactly as it was until that is decided.

## Notes

- **A finished phase's progress file is not edited**, even to record that
  something it deferred has since been done — `verification.md`'s *Corrections
  found after a phase closed* explains why. This audit briefly broke that by
  noting D-100 in `phase-04-camera.md`, and put it back.
- **Not done, on purpose:** moving the ~50 English labels still written in code
  (`WordScreen`, `KanjiScreen`, `StrokeOrder`) into `strings.xml`. Nothing is
  translated, instrumented tests find nodes by that exact text, and one piece of
  logic branches on a label (`label.startsWith("ON")`). Worth doing when
  translation is wanted, with a compiler to hand.
- **Bundling Roboto.** "The system font" is Roboto on Pixels but not on, say,
  Samsung phones. If the owner wants Roboto itself, it must be bundled.
- **The dictionary is 100.1 MB**, up from 99.7 when D-56 measured it.
