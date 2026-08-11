# dictbuild

Builds `spotter.db`, the read-only dictionary shipped as an Android asset (D-10).

Desktop Python, stdlib only, no install step. Contains no Android code and needs no emulator — the point is to de-risk the whole data layer before any Android work starts.

## Usage

```bash
python test_dictbuild.py   # 38 unit tests over the pure functions, ~3 ms
python build.py            # spotter.db from schema.sql + the ingest stages
python verify.py           # the verification cases from docs/verification.md
```

Both `build.py` and `verify.py` work from a clean checkout with **no network**, because the sources are committed (D-55). `fetch.py` is only needed when refreshing them:

```bash
python fetch.py            # download per sources.json
python fetch.py --list     # show the manifest without downloading
python fetch.py --force    # re-download even if already present
```

Sources live in `data/raw/` and are committed; what produced the current build is recorded in `sources.lock.json`. Build output goes to `data/build/` and is gitignored.

CI runs all three on every push (`.github/workflows/ci.yml`) — plain Python, no tokens.

## Why the lock file exists

Three of the five sources — JMdict, KANJIDIC2, and the Tanaka Corpus — are published at fixed URLs and regenerated continuously. JMdict is rebuilt daily. **A past version cannot be requested**, so the URL alone identifies nothing.

Each of those files carries its generation date in its own header, and that is the real version identifier. `fetch.py` extracts it and records it alongside the SHA-256.

Note the checksum changes every day whether or not any content did, because the generation date is written into the file. A checksum proves two files differ; it cannot tell you whether anything meaningful changed.

The remaining two sources (KanjiVG, JmdictFurigana) are immutable GitHub release assets, pinned by tag and verified against the publisher's own SHA-256.

## Getting the database into the Android app

`data/build/spotter.db` is **gitignored** — a 100 MB binary does not belong in git, and D-55's argument for committing the *sources* does not extend to a file those sources can regenerate in 45 seconds. So a fresh clone has the sources but not the database.

Build it, and the Android build picks it up:

```bash
python fetch.py                      # only if data/raw/ is empty
python build.py
python verify.py                     # 10 of 10 must pass
```

**The copy into the app is automated** (`:app:stageDictionaryAsset`), so there is no manual `cp` step. That task fails the Android build if the database is missing, or if it is older than the code that generates it — a stale asset otherwise produces an app that looks fine and serves old data. See `app/build.gradle.kts`.

Room loads it with `createFromAsset`, which copies it out to internal storage on first launch — so the device holds both the compressed copy inside the APK and the extracted one, roughly 130 MB total.

Two outputs, and only one of them is reproducible:

| File | Reproducible? |
|---|---|
| `spotter.db` | **Yes** — byte-identical from identical sources (D-58) |
| `build-info.json` | No, and deliberately so — it records the wall-clock build time that used to sit inside the database (D-64) |

## Refresh policy (D-41)

At defined events only: phase start, before first release, and once per release. Never mid-phase.

The cost of refreshing rises sharply after launch — before release nothing can break, afterwards a refresh can orphan a user's saved words (D-39, D-40).

## Open

`sources.json` lists **three candidates** for example sentences. Which one wins should be settled by looking at the real files, not from documentation. See `docs/data-model.md`.
