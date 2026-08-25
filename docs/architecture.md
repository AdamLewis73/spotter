# Architecture

Read `overview.md` first if you're new to this project — it explains what the app does and defines the Japanese-language terms used here.

## Stack

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin | |
| UI | Jetpack Compose + Material 3 | No XML layouts |
| Camera | CameraX | The lower-level Camera2 API is notoriously difficult; CameraX exists to wrap it |
| OCR | ML Kit Text Recognition v2, Japanese model | On-device, offline, free, no API key |
| Tokenizer | Kuromoji (IPADIC) + JMdict longest-match | D-07 |
| Database | Room (SQLite) | Two separate DBs — D-09 |
| DI | Hilt | Add once the app works, not before |
| State | ViewModel + StateFlow | Unidirectional data flow |
| Navigation | Navigation Compose | |
| SRS | FSRS (ported) | D-26 |

**Versions live in `gradle/libs.versions.toml`, which is the single source of truth.** Do not paste version numbers into module build files, and do not trust a remembered one — check Google Maven or Maven Central. That instruction has already earned its keep: AGP 9 removed the Kotlin Android plugin, so every pre-2026 tutorial gives advice that is now a build error. The scaffold's findings are in `progress/phase-02-android-text-input.md`.

**Dependency coordinates still to add, for later phases:**
- `com.google.android.gms:play-services-mlkit-text-recognition-japanese` (Phase 4)
- `com.atilika.kuromoji:kuromoji-ipadic` (Phase 2)

ML Kit offers *bundled* and *unbundled* variants. Bundled ships the model inside the APK (larger download, works immediately); unbundled downloads it via Google Play Services on first use (smaller APK, but the first scan can fail offline). **Bundled is the choice, and the size cost was measured in Phase 4 (D-74): ~14.8 MB per device, not the 43 MB a universal APK suggests.** The gap is entirely per-ABI native libraries, which a Play app bundle splits away.

*Note the reasoning changed.* This previously followed from D-03's "fully offline" rule. D-46 supersedes that: one-time downloads are now permitted, so unbundled is no longer forbidden. Bundled remains correct as a **product preference** — where a bundled option exists, eliminating the download beats handling it well. Don't read superseded D-03 and conclude unbundled is banned; it isn't, it's just not preferred.

### SDK levels

- **`compileSdk` / `targetSdk`: latest stable.** Google Play requires apps to target API 36 (Android 16) or higher as of 2026-08-31. **Currently 37.** Note that from API 37 the SDK platforms carry *minor* versions — the installable packages are `android-37.0` and `android-37.1`, and there is no bare `android-37`.
- **`minSdk`: 26.** This is the *floor* of Android versions that can install the app — a completely different knob from `targetSdk`. Setting it near the newest release would cut the app off from most devices in use for no benefit. ML Kit itself only requires API 21+.
- **JDK 17 bytecode throughout**, including `:domain`, because Android's dexer rejects class files stamped newer than it understands.

## Module structure

The layering rule below is the single most important architectural constraint in the project, because it is what makes a future iOS port feasible and it cannot be retrofitted cheaply.

```
:app          Compose UI, ViewModels, navigation, CameraX, ML Kit
              ↓ depends on
:data         Room, repository implementations, dictionary access,
              Kuromoji tokenizer implementation
              ↓ depends on
:domain       Models, use cases, FSRS, repository interfaces,
              Tokenizer interface
              ← depends on nothing
```

**`:domain` and `:data` must not import anything from `android.*`.** All Android-specific code lives in `:app`.

For `:domain` this is enforced by the compiler: it is declared as a plain Kotlin/JVM module, so `android.jar` is not on its classpath and the import does not resolve (D-60). For `:data` it needs a CI grep, since Room brings Android in — see the caveat below.

One caveat: Room itself has Android dependencies, so `:data` isn't strictly platform-free. That's an accepted compromise for now. If strict purity becomes necessary, SQLDelight is the multiplatform equivalent. Not worth switching preemptively.

## iOS portability

Not a v1 concern. The only goal is to avoid a from-scratch rewrite if an iOS version is ever built.

| Layer | Transfers? |
|---|---|
| Dictionary SQLite file + Python builder | **100%** — platform-agnostic; the project's most portable asset |
| `:domain` (models, FSRS, use cases) | Yes, via Kotlin Multiplatform — *only if* the no-`android.*` rule held |
| Compose UI | Mostly — Compose Multiplatform for iOS reached stable in version 1.8.0, May 2025 |
| ML Kit | Yes — Google ships an iOS SDK with the same Japanese model |
| Room, CameraX | No — replace with SQLDelight and AVFoundation |
| **Kuromoji** | **No — it is JVM-only and cannot run on iOS** |

Kuromoji is the real iOS risk, and a further argument for leaning on JMdict longest-match (D-07), which is pure logic over a database and therefore portable anywhere. `sudachi.rs` — a Rust port of the Sudachi tokenizer — is a viable future escape hatch if a full morphological analyzer is ever needed on iOS.

**Do not set up Kotlin Multiplatform now.** It adds meaningful build complexity to what is already a first Android project. Just hold the layering line; the conversion later is mostly moving files.

## The scan pipeline

This is the technical heart of the app and the part most likely to produce subtle bugs. Each stage below states exactly what it receives and what it produces.

```
1. CameraX          → a captured still image (D-02: frozen, not live)
2. ML Kit           → recognized text + pixel bounding boxes
3. Tokenizers       → words as character offsets into the text
4. Offset↔pixel map → screen rectangle for every character
5. Overlay          → tap coordinates resolve to a word
6. Dictionary       → lookup result populates the peek sheet
```

### Stage 2 — what ML Kit actually returns

A tree, roughly: `Text` → `TextBlock` → `Line` → `Element`. Every node carries a `text` string and a `boundingBox` rectangle in **image pixel coordinates**.

Important caveat: for Japanese, `Element` boundaries do **not** correspond to word boundaries. ML Kit does not know where Japanese words begin and end — that's what stage 3 is for. Elements are useful for their *positions*, not their segmentation.

### Stage 3 — what the tokenizers return

Kuromoji takes the concatenated text and returns tokens described as **character offsets** — "a word starts at character 3 and is 2 characters long." JMdict longest-match likewise returns candidates as offset ranges.

Neither tokenizer knows anything about pixels.

### Stage 4 — the bridge, and the actual hard part

Stage 2 speaks pixels. Stage 3 speaks character offsets. Something must connect them.

Build a lookup table by walking ML Kit's elements in reading order and counting characters cumulatively:

```
element "先生と"  box (100, 50, 90, 30)   chars 0–2
element "生産"    box (200, 50, 60, 30)   chars 3–4
```

To find the rectangle for a single character, interpolate within its element. For a horizontal element containing *n* characters, character *k* occupies approximately:

```
x = box.x + (k / n) * box.width
width = box.width / n
```

This linear interpolation is unusually accurate for Japanese, because CJK glyphs are **uniformly wide by design** — unlike Latin text where an `i` and a `W` differ enormously. That property is worth relying on.

A tap then resolves as: pixel `(x, y)` → containing element → character index within it → global character offset → the token whose offset range spans it → dictionary lookup.

### Vertical text

Japanese signage, menus, and book spines are frequently written **縦書き** (*tategaki*) — top to bottom, right to left. When they are, stage 4 interpolates on the **y** axis instead of x, and reading order between columns runs right-to-left.

This must be handled in the coordinate layer from the start. Discovering it after building a horizontal-only implementation means redoing stage 4 — the most error-prone part of the pipeline. Include vertical text in test images from the first day of Phase 5.

### Why this is stage 5, not stage 1

The pipeline is built in reverse (see `roadmap.md`). Phases 1–3 construct and prove stages 3 and 6 with typed text and no camera at all. Only then do stages 1–2 get added, and the coordinate bridge last. A bug anywhere in a camera-first build looks like a camera bug.

## Navigation

```
Bottom nav: Scan · Saved · Review          (D-36)

Scan → shutter → frozen image + overlay
  → tap word  → PEEK SHEET (ModalBottomSheet, partially expanded)
                  word · reading · meaning · [Save] · [Full Details]
  → expand    → WORD SCREEN — the same sheet, fully expanded (D-30)
  → tap chip  → KANJI SCREEN — swaps in place, back arrow (D-32)
                  tabs: Overview | Examples | Stroke Order
```

A single kanji scanned on its own may route directly to the kanji screen.

The two-level in-sheet stack (word → kanji) requires custom plumbing, since `ModalBottomSheet` has no built-in back stack. See D-32 for why that cost is accepted.

## Repository pattern

All data access goes through repository *interfaces* declared in `:domain` and *implemented* in `:data`. The UI never touches Room, files, or the dictionary directly.

This is what makes D-19 (future server sync) an additive change: a remote data source slots in behind the existing interfaces without any caller knowing. Without it, adding sync means rewriting every call site.
