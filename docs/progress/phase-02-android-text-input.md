# Phase 2 — Android app, text input only

**Status:** in progress
**Updated:** 2026-08-11

## Current state

**The Gradle scaffold builds and the dictionary ships inside the APK.** Three
modules — `:app`, `:data`, `:domain` — with `:domain` as a plain Kotlin/JVM
module per D-60. The debug APK is **46 MB**, holding a 99.7 MB `spotter.db` at
`assets/spotter.db` (the APK's own compression does the rest). `:domain:test`
runs 3 JUnit tests in milliseconds with no emulator.

The app itself is still a placeholder that renders 先生. **Nothing reads the
dictionary yet** — there is no Room layer, so the asset is proven to be present
and byte-correct, not proven to be usable.

Rules are verified rather than asserted: `import android.os.Bundle` in
`:domain` fails to compile; a CI grep covers `:data`, where the compiler cannot
help; and both dictionary guards were tested by deliberately breaking them.

The dictionary is now byte-reproducible (D-58, D-64), which immediately exposed
a pre-existing bug: `verify.py` was modifying the database it verified, because
opening SQLite read-write changes a file's bytes. That class of fault is
invisible until something checksums the artefact.

## Next action

Room, reading the dictionary out of the asset. That is what turns "the file is
in the APK" into "the app can look up 先生", and it is the first thing that will
exercise the schema from Kotlin.

Note the **D-35 checkpoint** (Material 3 plus a design-token layer) falls due
before any real UI, and the placeholder screen deliberately does not pre-empt
it — it uses bare `MaterialTheme` defaults.

## Done

- [x] Checkpoint: module structure agreed — `:app` / `:data` are Android
      libraries, `:domain` is a plain Kotlin/JVM module (D-60)
- [x] Gradle project scaffolded; app builds
      (application ID `com.spotterkanji.app`, D-63)
- [x] Layering rule enforced automatically — compiler for `:domain`, CI grep
      for `:data`; both verified against a deliberate violation
- [ ] Confirm the APK actually launches on a device or emulator
- [x] `spotter.db` built and copied into app assets
- [x] Asset copy automated as a Gradle task (`:app:stageDictionaryAsset`),
      failing loudly when the dictionary is missing or older than its builder
- [x] CI builds the dictionary from committed sources and asserts it reached
      the APK — the real guarantee that master never ships a stale asset
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
- **The staleness check is a timestamp comparison, and timestamps lie.**
  `git checkout` and branch switches reset mtime without changing content, which
  makes a current database look stale — this fired during development on a
  `build.py` that was byte-identical to git. Hence
  `-PallowStaleDictionary=true`. A guard with no escape hatch gets deleted.
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
