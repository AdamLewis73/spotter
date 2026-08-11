# Phase 2 — Android app, text input only

**Status:** in progress
**Updated:** 2026-08-10

## Current state

**The Gradle scaffold builds.** Three modules — `:app`, `:data`, `:domain` —
with `:domain` as a plain Kotlin/JVM module per D-60. `:app:assembleDebug`
produces a 12 MB debug APK; `:domain:test` runs 3 JUnit tests in milliseconds
with no emulator. The app itself is a placeholder that renders 先生 and nothing
else.

The layering rule is verified rather than asserted: adding `import
android.os.Bundle` to `:domain` fails with `Unresolved reference 'android'`,
and a CI job greps `:data` (where the compiler cannot help) for the same.

No dictionary yet — `spotter.db` is not built in this working copy, and nothing
reads it.

## Next action

Build `spotter.db` and get it into `:app` assets, with the copy automated as a
Gradle task. A stale asset yields an app that looks fine and serves old data.

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

### Versions

Verified against Google Maven and Maven Central on 2026-08-10, per
`architecture.md`'s instruction not to trust remembered version numbers:
AGP 9.2.1, Gradle 9.4.1, Kotlin 2.4.10, Compose BOM 2026.06.01,
compileSdk/targetSdk 37, minSdk 26.

AGP 9.3.1 is published but has **no release notes**, so its minimum Gradle is
unverified — hence pinning the 9.2 line, whose requirements are documented
(Gradle 9.4.1, JDK 17, max API 37.0). Revisit when 9.3 is documented.

### General

- `spotter.db` is gitignored, so a fresh clone has none. Build it with
  `python fetch.py` then `python build.py` in `tools/dictbuild/` (~45 s).
- Kuromoji is JVM-only and cannot run on iOS. It is the one part of the
  tokenizer that will not port — which is why D-07 leans on JMdict longest-match
  as well.
- Room has Android dependencies, so `:data` is not strictly platform-free. That
  is an accepted compromise (`architecture.md`); it is not a reason to relax the
  `android.*` rule anywhere else.
