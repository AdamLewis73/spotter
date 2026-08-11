# Spotter

**Spotter: Kanji Scanner** — by Adam Lewis

An Android app for reading Japanese in the wild. Point the camera at a sign, a menu, or a package; freeze the frame; tap a word to see what it means, how it's read, and which kanji compose it. Save words and review them.

**The scanner is the product.** The app opens on the camera — no home screen, no dashboard. Free, with no ads and no paywall on scanning.

Where it differs from a translation app: it keeps the Japanese and explains it, rather than replacing it. Kanji don't have fixed meanings in isolation — 生 alone is "life", but 先生 is "teacher" and 生産 is "production"; 手 alone is "hand", but 上手 is "skilled" and 歌手 is "singer". Replacing the text destroys exactly the information a learner needs.

## Status

**Phase 1 complete.** `tools/dictbuild/` builds the bundled dictionary from four pinned open datasets — 99.7 MB, byte-reproducible from identical sources, 10 of 10 verification cases passing.

**Phase 2 (Android app) in progress.** Three Gradle modules build, and the dictionary ships inside a 46 MB debug APK. The app itself is still a placeholder: it launches and renders 先生, and nothing reads the dictionary yet.

```bash
python tools/dictbuild/build.py     # ~45 s; sources are committed, no network needed
./gradlew :app:assembleDebug        # stages the dictionary into the APK
```

See `docs/roadmap.md` for the phase plan and `docs/overview.md` to start reading.

## Documentation

| Doc | Contents |
|---|---|
| [Overview](docs/overview.md) | Product vision, principles, glossary |
| [Decisions](docs/decisions.md) | Numbered decisions with rationale |
| [Architecture](docs/architecture.md) | Stack, modules, layering, navigation |
| [Data model](docs/data-model.md) | Datasets, schema, migrations, backup |
| [UX](docs/ux.md) | Screens, interaction, visual rules |
| [Roadmap](docs/roadmap.md) | Phases, checkpoints, deferred backlog |
| [Progress](docs/progress/) | Living state of work in flight |

## Working on this project

Two project slash commands, defined in `.claude/skills/`:

- **`/orient`** — load current context at the start of a session: phase, progress, open questions, repo state.
- **`/phase <n>`** — plan and begin a roadmap phase, e.g. `/phase 1`. Loads that phase's docs and verification cases, and stops at any decision checkpoint before writing code.

## Attribution

Dictionary data comes from [JMdict/KANJIDIC2](https://www.edrdg.org/) (EDRDG, CC BY-SA), [KanjiVG](https://kanjivg.tagaini.net/) (CC BY-SA), [JmdictFurigana](https://github.com/Doublevil/JmdictFurigana), and [Tatoeba](https://tatoeba.org/) (CC-BY). Full attribution ships in-app as required by these licenses.
