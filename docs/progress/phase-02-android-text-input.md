# Phase 2 — Android app, text input only

**Status:** in progress
**Updated:** 2026-08-19

## Current state

**The Gradle scaffold builds and the dictionary ships inside the APK.** Three
modules — `:app`, `:data`, `:domain` — with `:domain` as a plain Kotlin/JVM
module per D-60. The debug APK is **46 MB**, holding a 99.7 MB `spotter.db` at
`assets/spotter.db` (the APK's own compression does the rest). `:domain:test`
runs 3 JUnit tests in milliseconds with no emulator.

**The word screen works.** Type a word and it renders one card per reading,
meanings under each, component chips last (D-48, D-06). Confirmed on a device:
先生 shows せんせい marked *common* with four senses, then せんしょう, せんじょう,
ぜんじょう and シーサン, with 先 / 生 chips beneath.

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

In the order they are worth doing, and the reasoning matters more than the list:

1. **The UI pass** the project owner asked for on 2026-08-19. The screens work
   and read as cluttered — the word screen stacks five reading cards for 先生,
   and the kanji screen's Overview prints every kun'yomi in one long run. Not
   yet designed; it is a deliberate, separate pass rather than a tidy-up folded
   into feature work.
2. **Decide D-51**, whether example sentences get rendered. Sequenced *after*
   the UI pass on purpose: the roadmap wants this judged against real screens,
   and judging "do sentences earn their space" against a layout already known to
   be too busy would answer the wrong question. Build them behind a switch, look
   at 先生, 上手 and 生 both ways, then decide.
3. **JMdict longest-match alternates (D-07)** — the missing half of V-06.

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
schema version changes, and 22 instrumented tests cover it. Where a claim is
unproven this file says so.

**The recurring failure mode in this phase has been silence, not crashes.** A
verifier that mutated the database it verified; a `build_id` that did not change
when the builder did; `BackHandler` doing nothing because of a missing manifest
attribute; a word screen announcing 生 was not in the dictionary. None threw. The
habit that caught them was running the thing and looking at it, and it is worth
keeping — `/launch` exists to make that cheap.

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
- [ ] JMdict longest-match alternates (D-07)
- [x] Obsolete, irregular and rare readings marked; search-only readings hidden
      but never to nothing (V-21, D-53, D-66)
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
| **V-06** segmentation **plus alternates** (D-07) | **Half met.** Kuromoji gives 先生 / と / 生産. The JMdict longest-match half is not built, so 先 inside 先生 cannot be asked about — and V-06 says plainly that both halves matter because that interaction is the pedagogical premise |
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
- [x] `/launch` skill at `.claude/skills/launch/SKILL.md` (roadmap housekeeping)

## Open questions


- **A 100 MB asset is proven present, not proven readable.** Room's
  `createFromAsset` streams a compressed asset out to internal storage on first
  launch; that path is untested here and will roughly double disk use on device
  while it runs. Test on a real device, not only the emulator.


- **Do example sentences get rendered? (D-51)** Already in the dictionary,
  shown nowhere. 41.4% coverage of common senses, ceiling ~43%. Deliberately
  not being judged on paper — build the word screen without them, look at 先生,
  上手 and 生 on a device, then turn them on and look again.
- If they stay: sense-attached only, or word-level examples too where no
  sense-attached one exists?
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
exactly the friction `/launch` exists to remove. The extra is read in all build
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
