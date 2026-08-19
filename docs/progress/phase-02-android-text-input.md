# Phase 2 — Android app, text input only

**Status:** in progress
**Updated:** 2026-08-11

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

**Decide D-51**, whether example sentences get rendered. The roadmap is
explicit that this is judged against real screens rather than on paper: look at
先生, 上手 and 生 with them off, turn them on, look again.

**No UI tests yet.** The screen is verified by eye and the data path by
instrumented tests; the ViewModel's stale-result guard — each keystroke cancels
the previous lookup so a slow query for 先 cannot overwrite a newer one for 先生
— is exactly the kind of logic that deserves a test and does not have one.

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
- [x] Text-input screen: paste `先生と生産`, get tokens, tap one to read it
- [x] Word screen — one section per reading, component chips last (D-48, D-06)
- [x] Kanji screen — Overview / Examples tabs; Stroke Order is Phase 3 (D-05)
- [ ] Single kanji routes straight to the kanji screen (D-49)
- [ ] Checkpoint: UUID keys, `updated_at`, soft delete, schema export on,
      destructive migration off (D-15 – D-18) before any user-data write
- [ ] Checkpoint: `snapshot_gloss` on `study_item` (D-43), same commit
- [ ] Relevant `V-##` cases from `verification.md` added to this list
- [ ] `/launch` skill at `.claude/skills/launch/SKILL.md` (roadmap housekeeping)

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
