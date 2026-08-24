# Phase 2 — Android app, text input only

**Status:** in progress
**Updated:** 2026-08-23

## Current state

**The Gradle scaffold builds and the dictionary ships inside the APK.** Three
modules — `:app`, `:data`, `:domain` — with `:domain` as a plain Kotlin/JVM
module per D-60. The debug APK is **46 MB**, holding a 99.7 MB `spotter.db` at
`assets/spotter.db` (the APK's own compression does the rest). `:domain:test`
runs 24 JUnit tests in milliseconds with no emulator.

**The word screen works**, and now looks like the design (D-67). A centred
headword over a `5 READINGS · 2 ARCHAIC` count, readings as a divided list in the
accent colour, meanings under each with their example sentence (D-69), component
boxes last (D-48, D-06). Confirmed on a device: 先生 shows せんせい *common* with
four senses, then せんしょう · せんじょう · ぜんじょう sharing one block because
their meanings are identical (D-68), then シーサン — the whole word on one screen
with its 先 / 生 boxes visible.

**The kanji screen works**, reached from a component chip: Overview carries
meanings, on/kun readings and the "As a word" senses (D-49); Examples carries
D-04 — every common word grouped by the reading the kanji takes there, セイ
before ショウ because セイ holds 先生 and 学生.

**Tokenization works.** `先生と生産` segments into 先生 / と / 生産 as chips; tapping
one shows its entry. Confirmed on a device — tapping 生産 gives せいさん
"production; manufacture" with 生 *life, genuine* and 産 *products, bear*
beneath, which is the contextual point the whole app exists to make.

Particles are shown but muted: they must keep their place or every offset after
them is wrong, while "case marking particle" is not what anyone wants explained.
Inflected words fall back to the dictionary form, so 生きた finds 生きる.

Rules are verified rather than asserted: `import android.os.Bundle` in
`:domain` fails to compile; a CI grep covers `:data`, where the compiler cannot
help; and both dictionary guards were tested by deliberately breaking them.

The dictionary is now byte-reproducible (D-58, D-64), which immediately exposed
a pre-existing bug: `verify.py` was modifying the database it verified, because
opening SQLite read-write changes a file's bytes. That class of fault is
invisible until something checksums the artefact.

## Next action

Nothing. Every verification case this phase owns is met, and what remains on
the list below is the **user-data checkpoint** (D-15–D-18, D-43), which by design
lands with Save in Phase 6 rather than here.

Two small things are open and neither blocks anything:

- Example meanings on the kanji screen are right-aligned as the design draws
  them; at 411dp the gap between a short meaning and its word is wide.
- Part-of-speech tags render as raw JMdict codes (`adj-na`, `n`). A
  code-to-label table would read better and needs a decision about which codes
  are worth naming.

**Compose UI tests now exist**, four of them, covering V-21's marking on the
word screen — that an archaic reading is labelled, that a current one is
labelled nowhere, that each status says its own word, and that 上手 leads with
じょうず carrying the only *common* badge. Verified by deliberately breaking the
label: three of the four fail, and the one asserting *absence* correctly still
passes.

What remains untested is the rest of the composition: the stale-result guard
(each keystroke cancels the previous lookup, so a slow answer for 先 cannot
overwrite a newer one for 先生), and that a chip tap opens the kanji screen. The
harness is now in place for both.

## What a fresh session should know

Written down because it lives nowhere else in the repo.

**The build works and the app does something real.** Type Japanese, get
segmented words, tap one, read its meanings, tap a kanji, see every common word
grouped by the reading it takes there. That last screen is D-04 and is the
project's only genuine differentiator — a competitive review in August 2026 found
the rest of the feature set already shipping elsewhere (D-61).

**Everything about the data layer is verified on a device, not assumed.** The
dictionary ships in the APK, refreshes itself when either its build id or Room's
schema version changes, and 32 instrumented tests cover it. Where a claim is
unproven this file says so.

**The recurring failure mode in this phase has been silence, not crashes.** A
verifier that mutated the database it verified; a `build_id` that did not change
when the builder did; `BackHandler` doing nothing because of a missing manifest
attribute; a word screen announcing 生 was not in the dictionary. None threw. The
habit that caught them was running the thing and looking at it, and it is worth
keeping — `/inspect` exists to make that cheap.

**Two guards in dictbuild will misfire on the next source refresh** and should be
fixed when that code is next touched; see the phase-01 open questions.

## Done

- [x] Checkpoint: module structure agreed — `:app` / `:data` are Android
      libraries, `:domain` is a plain Kotlin/JVM module (D-60)
- [x] Gradle project scaffolded; app builds
      (application ID `com.spotterkanji.app`, D-63)
- [x] Layering rule enforced automatically — compiler for `:domain`, CI grep
      for `:data`; both verified against a deliberate violation
- [x] Confirm the APK actually launches on a device or emulator
- [x] `spotter.db` built and copied into app assets
- [x] Asset copy automated as a Gradle task (`:app:stageDictionaryAsset`),
      failing loudly when the dictionary is missing or older than its builder
- [x] CI builds the dictionary from committed sources and asserts it reached
      the APK — the real guarantee that master never ships a stale asset
- [x] Room read-only dictionary DAOs over the Phase 1 schema, proven on a device
      by instrumented tests (先生 resolves; 上手 returns all three readings)
- [x] Room schema export on, JSON committed (D-18)
- [x] Refresh the extracted dictionary when the shipped build differs, verified
      end to end by shipping a different dictionary as an in-place APK update
- [x] Checkpoint: Material 3 + design-token layer (D-35) — fixed palette, light
      and dark, plus Noto Sans JP bundled (D-34)
- [x] Kuromoji tokenization behind the `Tokenizer` interface (D-07, D-08)
- [x] JMdict longest-match alternates (D-07, D-70, V-06)
- [x] Example sentences rendered, one reading per entry (D-69, D-51, V-27)
- [x] Readings with identical meanings share a block (D-68)
- [x] Obsolete, irregular and rare readings marked; search-only readings hidden
      but never to nothing (V-21, D-53, D-66)
- [x] UI pass on the word and kanji screens, from the Claude Design import
      (D-67) — new palette and type, plus 2a and 2b
- [x] Text-input screen: paste `先生と生産`, get tokens, tap one to read it
- [x] Word screen — one section per reading, component chips last (D-48, D-06)
- [x] Kanji screen — Overview / Examples tabs; Stroke Order is Phase 3 (D-05)
- [x] Single kanji routes straight to the kanji screen (D-49)
- [ ] Checkpoint: UUID keys, `updated_at`, soft delete, schema export on,
      destructive migration off (D-15 – D-18) before any user-data write
- [ ] Checkpoint: `snapshot_gloss` on `study_item` (D-43), same commit
### Verification cases — assessed 2026-08-19, and three are unmet

Folding these in rather than leaving the line item vague, because the honest
answer is that Phase 2 is **less finished than the rest of this list suggests**.

| Case | State |
|---|---|
| **V-07** conjugated verbs resolve to the dictionary form (D-07) | **Met.** `生きた` → `生きる` via `Token.baseForm`, with the lookup falling back to it. Tokenizer half unit-tested; the lookup fallback is exercised only by hand |
| **V-06** segmentation **plus alternates** (D-07) | **Met**, 2026-08-23. Longest-match runs as a second pass over the same line, one batched query for every candidate substring. 先生 / 先 at position 0, and — the case that mattered more — 東京都 and 京都 from a parse that gives only 東京 / 都 (D-70) |
| **V-08** reading labels vs. furigana use different scripts (D-14, D-37) | **Half met.** Reading labels follow the convention and are tested. There is no furigana rendering at all yet, so the half about ruby is untested |
| **V-21** obsolete readings are visibly distinguished (D-53, D-48, D-66) | **Met**, 2026-08-19. 上手 opens on じょうず; じょうしゅ and じょうて are muted, labelled *archaic* and sorted last, with the inherited *common* badge suppressed. Confirmed on a device for 上手, 中国 and 明日 |
| **V-23** sense filtering never empties a word (D-54, D-40) | **Not applicable yet.** No sense filtering exists to test |

**V-21 was fixed first, and it was larger than the line above suggested.** The
column it depends on was read by nothing: `reading_info` carries five `re_inf`
codes, not one, and the worst offender was not `ok` at all. `sk` — search-only
kana, 6,647 rows — was rendering as ordinary readings on words far commoner than
上手: **中国 opened on ちゅうこく**, a misreading JMdict stores purely so search
matches, badged *common* above ちゅうごく. D-66 records the resulting policy for
all five codes.

Two things that were not obvious going in, both now in D-66:

- **It was an ordering bug as much as a labelling one.** 上手's archaic readings
  inherit the writing's frequency rank (V-04), tie with じょうず, and win the
  alphabetical tiebreak — so the screen *opened* on じょうしゅ. Marking alone
  would have left it at the top.
- **`gikun` is not a defect marker**, and the obvious implementation treats it as
  one. 明日 あした, 大人 おとな and 海豚 いるか all carry the tag and are all the
  ordinary current reading.
- [x] `/launch` skill at `.claude/skills/launch/SKILL.md`, and `/inspect` at
      `.claude/skills/inspect/SKILL.md` (roadmap housekeeping)

## Open questions


- **A 100 MB asset is proven present, not proven readable.** Room's
  `createFromAsset` streams a compressed asset out to internal storage on first
  launch; that path is untested here and will roughly double disk use on device
  while it runs. Test on a real device, not only the emulator.


- ~~**Do example sentences get rendered? (D-51)**~~ — **answered: yes (D-69).**
  Word-level examples where no sense-attached one exists stay deferred; nothing
  in the data made the case for them.
- Hilt now or after the app works? `architecture.md` says after.

## Notes

### Scaffold gotchas — 2026-08-10

Four things cost time. All four look like typos in a log and none are.

- **AGP 9.0+ removed the Kotlin Android plugin.** Kotlin support is built into
  AGP now, and applying `org.jetbrains.kotlin.android` is an *error*. Every
  tutorial and answer written before 2026 says to add it. `:app` and `:data`
  therefore apply no Kotlin plugin at all; `:domain` still applies
  `org.jetbrains.kotlin.jvm` because it is not an Android module. The Compose
  compiler plugin **is** still required per Compose module.
- **`jvmToolchain(17)` needs a JDK 17 actually installed.** This machine has
  only Android Studio's bundled JDK 25, and Gradle will not invent one without
  toolchain download repositories configured. `:domain` instead sets
  `jvmTarget = JVM_17` plus `sourceCompatibility`/`targetCompatibility`, which
  compiles on 25 and emits 17 bytecode — what the Android dexer needs.
- **`local.properties` treats `\` as an escape character.** `sdk.dir=C:\Users\…`
  silently collapses to `C:Users…` and AGP fails with a bare
  `java.io.IOException: Invalid file path` that names neither the file nor the
  property. Use forward slashes.
- **Gradle will not generate a wrapper in a directory with no build.**
  `gradle wrapper` fails until a `settings.gradle.kts` exists, even an empty one.
- **From API 37 the SDK platforms carry minor versions.** The installed packages
  are `android-37.0` and `android-37.1`; there is no bare `android-37`. AGP maps
  `compileSdk = 37` onto the `.0` minor. Asking sdkmanager for
  `platforms;android-37` fails with "Failed to find package", which reads like
  the platform has not been published yet rather than like a naming problem.
  This broke the first CI run. Build-tools version independently — 37.0.0 is not
  what AGP wants merely because compileSdk is 37; AGP 9.2 defaults to 36.0.0.
- **`gradlew` must be committed executable, and Windows git will not do it.**
  It lands as mode `100644`, and Linux CI then fails with
  `./gradlew: Permission denied` and exit code 126 — which looks like a missing
  file rather than a missing permission bit. Fix once with
  `git update-index --chmod=+x gradlew`; the mode is stored in git, so it only
  has to be done on the commit that adds the file.

### Room over a hand-written schema — 2026-08-11

Room validates a pre-packaged database against its entities on open and throws
`IllegalStateException: Pre-packaged database has an invalid schema`. The message
names the table and dumps Expected vs Found, but never says *which field*
differs — diff the two blocks. Three mismatches cost time:

- **`INTEGER PRIMARY KEY` reports as nullable.** SQLite treats it as the rowid
  alias, implicitly non-null, but `PRAGMA table_info` returns `notnull=0`, and
  Room compares that against a non-null Kotlin field. Fixed in `schema.sql` by
  writing `NOT NULL` explicitly — redundant to SQLite, required by Room.
- **`DEFAULT 0` must be mirrored** in `@ColumnInfo(defaultValue = "0")`.
- **`REFERENCES word(id)` must be mirrored** in `@Entity(foreignKeys = …)`. A
  real constraint the entity does not declare fails validation.
- `WITHOUT ROWID` (D-56) is **not** a problem: it exists only in the SQL text,
  and Room compares pragmas, which report the same shape either way.

Check a table with `PRAGMA table_info`, `foreign_key_list` and `index_list`
before writing its entity; it is faster than reading a validation dump.

**Room copies the asset once and never looks again.** It compares schema
versions, not contents, so a dictionary rebuilt from newer sources changes no
version and Room keeps serving the old copy indefinitely. Early instrumented
tests only passed after `adb uninstall`, which looked like a test-harness quirk
and was not.

Fixed by shipping the build id as a sidecar asset and discarding the extracted
copy when it differs (D-65). Two things that are easy to get wrong:

- **Close the Room instance before deleting the file.** Unlinking an open SQLite
  file succeeds on Linux, but the open handle keeps pointing at the old inode,
  so a still-open instance goes on serving the database that was just deleted —
  with no error anywhere.
- **Delete `-wal` and `-shm` too**, or a fresh copy sits beside old journal
  files.

Verifying this needs a genuine in-place APK update. `connectedAndroidTest`
uninstalls afterwards, so a plain re-run silently tests a fresh install and
proves nothing; drive it with `adb install -r` plus `am instrument` and read the
`DictionaryProvider` log line.

`Room.databaseBuilder` needs a `Context`, which `:data` may not import (D-60),
so construction lives in `:app` and `:data` exposes Room types via `api` rather
than `implementation` — `DictionaryDatabase` extends `RoomDatabase`, so Room is
genuinely part of its public surface.

### Theme and font — 2026-08-11

- **Google Fonts ships Noto Sans JP as a variable font only** — one 9.2 MB
  `NotoSansJP[wght].ttf` spanning weights 100–900, no static instances. Android
  supports variable fonts from API 26, which is exactly this project's `minSdk`.
  Each weight needs its own `Font(...)` entry naming the same resource with
  different `FontVariation` settings; without one, Compose synthesises the weight
  and synthetic bold turns dense kanji to mush.
- `variationSettings` is still `@ExperimentalTextApi`, so the file opts in.
- **The XML theme must have a `-night` variant.** It paints the window before
  Compose draws anything, so a hardcoded `Material.Light` parent flashes white on
  launch in dark mode — worst in exactly the conditions this app is used in.
  `Theme.Material.DayNight` is API 29+, above our floor, so the `-night`
  resource qualifier does the job instead. `window_background` is duplicated in
  XML because the platform cannot read a Compose colour.
- **Debug APK size is misleading.** It reached 80 MB, of which ~29 MB was dex
  from `ui-tooling` — a `debugImplementation` that never ships. The release APK
  is **44 MB** including the font. Measure release before worrying.
- A screenshot taken within ~4 s of launch catches the splash screen, and one
  taken right after `cmd uimode night` catches a blank frame mid-recreation.
  Both look like bugs and are not; force-stop, restart, then wait.

### Longest-match: the problem ran the other way — 2026-08-23

D-07 describes longest-match as finding the longest word at a position and
keeping the shorter ones as alternates. Built exactly that way, it surfaced
almost nothing, and the reason was only visible on a device.

**Kuromoji is better at compounds than D-07 assumed.** It already splits
選挙管理委員会 into 選挙 / 管理 / 委員 / 会 — so nothing shorter hides inside any
token, and what is actually unreachable is the **compound itself**. Same for
東京都, which parses as 東京 / 都: the full place name and 京都, which straddles
the boundary, are both invisible from the strip.

So an alternate is any word that **overlaps** the token, not one contained in it
(D-70). That covers inside, containing and crossing — which are one question from
the learner's side.

Two things worth knowing if this is revisited:

- **Single-character alternates are filtered from the display, not the
  mechanism.** V-06 requires 先 inside 先生 be found, and it is; it just is not
  listed, because it is already a component box below and routes to the same
  kanji screen (D-06, D-49). 先生 shows no alternates at all, correctly.
- **The whole thing is one query.** `LongestMatch.candidates` builds every
  substring up to 12 characters and `existingWords` asks about all of them at
  once. A query per substring would be a hundred round trips on a line the user
  is waiting for. The `UNIQUE (text, reading)` index serves it on its leftmost
  column, which is what `schema.sql` means about FTS5 buying nothing.

### Example sentences, and two traps in the data — 2026-08-23

D-51 is settled (D-69): sentences ship. What the exercise actually taught:

- **The question the roadmap asked was not the question that mattered.** It
  worried that 41.4% coverage would read as broken. It does not — 先生 shows
  sentences on two of four senses and looks like a dictionary. The real fault was
  a correctness one that only appears on a device.
- **A sentence belongs to an ENTRY, not to a (text, reading) word.** V-18 expands
  one entry into a word per reading, so every reading inherits the entry's
  sentences. 明日's あした sentence rendered identically under あす and
  みょうにち. **11,622 entries** are affected. Only 777 involve a marked reading,
  so V-21's status filter fixes ~5% and is not the answer.
- **Picking the "primary" reading must happen AFTER status ordering.** The query
  sorts by frequency then kana; 上手's three readings tie on frequency, so the raw
  order leads with じょうしゅ. Taking that as primary gave the sentence to an
  archaic reading, which then correctly suppressed it — and じょうず lost its
  example with nothing on screen to say so. Found by looking, as usual. V-27
  covers it.

**Room's schema check fired again on the first new table.** Declaring `ExampleRow`
threw `Pre-packaged database has an invalid schema: example` on launch, naming
the table and not the field. The cause was the one `word.id` already carries a
comment about: `example.id` was `INTEGER PRIMARY KEY` without `NOT NULL`, SQLite
genuinely permits NULL there (it assigns a rowid), so `PRAGMA table_info` reports
`notnull=0` against a non-null Kotlin field. The other primary keys are `TEXT`,
where SQLite forbids null, which is why they never tripped it.

**Adding an entity changes the exported schema.** Room silently rewrote
`3.json` in place rather than complaining. Bumped to 4 so the committed record of
what version 3 shipped stays true; the bump also forces one re-extract on
upgrade, which is correct and costs a single slow launch.

### The UI pass, and what the design did not cover — 2026-08-20

Implemented from a Claude Design project ("Kanji app mobile design"), imported
with the `DesignSync` tool. Two notes for anyone importing another one:

- **`list_projects` returns nothing useful.** It is filtered to *design-system*
  projects that the user can write to, and an ordinary design project is
  `PROJECT_TYPE_PROJECT`. Go straight to `get_project` / `list_files` /
  `get_file` with the project id out of the share URL.
- **The design canvas is one ~90 KB HTML file** of inline-styled frames. Do not
  read it whole. Split on the frame ids (`id="2a"`), strip tags, and read the
  text — the rationale paragraphs and frame captions carry most of the intent.

**The design covers six screens; four of them are Phases 6–7** (Saved, list
detail, Review, Profile) and are left unbuilt. They all write user data, which
is the D-15–D-18 + D-43 checkpoint. Two things in them are also not in the
roadmap at all and need deciding before they are built: the review card is a
**handwriting** exercise rather than the recall card D-26/D-29 assumed, and
Profile is a fourth destination the design itself flags against D-36.

**What was changed from the design, and why.** A first pass got this wrong in a
way worth recording: each omission below was individually defensible and the sum
of them was not the design. The screens ended up being the *old* layout wearing
the new palette — no back or save buttons, no accent rule, no stroke-order tab,
readings in the body colour rather than the accent. **A mockup is a spec for
intent, not a set of claims to fact-check.** Correcting its placeholder
`4 READINGS` to 5 was the tell.

What genuinely differs now:

- **The accent is jade, not amber.** Amber on near-black is a very well-known
  brand pairing. Hue only — lightness and chroma are the design's (D-67).
- **The text field has no counterpart in the design.** 2a is drawn as the sheet
  a scan opens, and until Phase 4 the field is the only way to put a word on the
  screen.
- **Save is inert** until Phase 6 and the user-data checkpoint; **back** clears
  the result, which is the nearest true equivalent to dismissing a sheet to the
  photograph behind it; the **drag handle** becomes real in Phase 5. All three
  are built, because a control that appears later moves everything around it.
- **Stroke order is a real tab** carrying the stroke count and saying Phase 3
  fills it in. The alternative — adding a third tab later — shifts the two that
  already exist.
- **The Overview tab's reading run was fixed anyway.** Not in the design, but it
  was half the original complaint — 生's twenty kun'yomi were three lines of
  undifferentiated kana.

### Compose UI tests, and the Espresso that blocked them — 2026-08-19

The first Compose test in the project failed before reaching an assertion:

```
NoSuchMethodException: android.hardware.input.InputManager.getInstance
```

**Espresso was resolving to 3.5.0** — from 2022. Nothing here calls Espresso;
it arrives transitively behind Compose's test rule, which routes idle-
synchronisation through it, and a transitive dependency takes whatever version
the graph settles on while every *direct* dependency in this project is pinned
current. Espresso reflects into that hidden platform method, which no longer
exists on an API 37 image, so all four tests died identically.

Pinned to 3.7.0 — verified against Google Maven rather than remembered — purely
to force the version up. `espresso-core` is listed in `app/build.gradle.kts`
despite no code importing it, which looks redundant and is not.

Two smaller traps:

- **`ui-test-manifest` is a `debugImplementation`, not an androidTest one.** It
  contributes the empty activity `createComposeRule()` launches into, which has
  to merge into the manifest of the app under test.
- **`setContent` may be called only once per test.** Looping it over three
  statuses fails with "Cannot call setContent twice per test" — a message about
  the harness, not about the thing under test. Render the cases together in a
  `Column` instead.

`createComposeRule()` is deprecated in favour of a `v2` package that swaps the
coroutine dispatcher. Not migrated: the change alters when effects run, and the
note to make it is worth more than doing it blind alongside a correctness fix.

### Driving the screen from a script — 2026-08-19

`MainActivity` reads an optional `query` string extra and seeds the text field
with it:

```
adb shell am start -n com.spotterkanji.app/.MainActivity --es query 上手
```

Added because there was **no way to type Japanese into the app from a script**.
`adb shell input text` is ASCII-only, and this emulator image answers
`cmd clipboard` with "No shell command implementation" — so verifying a claim
about a particular word meant typing it by hand on the emulator, which is
exactly the friction `/inspect` exists to remove. The extra is read in all build
types on purpose: it pre-fills a search box and grants nothing, and a hook that
only works in debug cannot check a release build.

### Versions

Verified against Google Maven and Maven Central on 2026-08-10, per
`architecture.md`'s instruction not to trust remembered version numbers:
AGP 9.2.1, Gradle 9.4.1, Kotlin 2.4.10, Compose BOM 2026.06.01,
compileSdk/targetSdk 37, minSdk 26.

AGP 9.3.1 is published but has **no release notes**, so its minimum Gradle is
unverified — hence pinning the 9.2 line, whose requirements are documented
(Gradle 9.4.1, JDK 17, max API 37.0). Revisit when 9.3 is documented.

### The dictionary asset — 2026-08-10

- **A plain `Copy` task is the wrong tool.** With a missing source it is skipped
  as `NO-SOURCE`, silently producing an app with no dictionary — the exact
  failure being guarded against. `StageDictionaryAsset` is a custom task so the
  absent case throws instead.
- **AGP 9 refuses a `Provider` in `sourceSets.assets.srcDir`.** It errors and
  points at the Variant API; `androidComponents.onVariants { … addGenerated
  SourceDirectory(...) }` is the supported route and carries the task
  dependency automatically, so no `preBuild.dependsOn` is needed.
- **`BackHandler` does nothing without `android:enableOnBackInvokedCallback`.**
  Without it the system logs `Setting back callback null` and back leaves the
  app instead of closing the kanji screen. Nothing warns at build time, and the
  attribute is documented as defaulting to true at recent target SDKs — it did
  not here.
- **The staleness check compared timestamps, and timestamps lie.** `git checkout`
  and branch switches reset mtime without changing content, so a current
  database looked stale — it fired on a `build.py` byte-identical to git. It now
  compares content hashes published by `build.py` in `build-info.json` (D-65),
  which removes the false positive and the `-PallowStaleDictionary` escape hatch
  that existed only to work around it.
- The real protection is CI: it rebuilds the dictionary from committed sources
  every push and asserts `assets/spotter.db` is in the APK.

### General

- `spotter.db` is gitignored, so a fresh clone has none. Build it with
  `python build.py` in `tools/dictbuild/` (~45 s). No `fetch.py` needed — the
  sources are committed (D-55), which is also why CI can build offline.
- Kuromoji is JVM-only and cannot run on iOS. It is the one part of the
  tokenizer that will not port — which is why D-07 leans on JMdict longest-match
  as well.
- Room has Android dependencies, so `:data` is not strictly platform-free. That
  is an accepted compromise (`architecture.md`); it is not a reason to relax the
  `android.*` rule anywhere else.
