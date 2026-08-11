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

`data/build/spotter.db` is **gitignored** — it is a build output, and committing a 100 MB binary that changes on every rebuild is exactly what D-55 avoided for the sources. So a fresh clone has the sources but not the database.

Phase 2 needs it as an app asset:

```bash
python fetch.py                      # only if data/raw/ is empty
python build.py
python verify.py                     # 10 of 10 must pass
cp data/build/spotter.db ../../app/src/main/assets/
```

Room loads it with `createFromAsset`, which copies it out to internal storage on first launch — so the device holds both the compressed copy inside the APK and the extracted one, roughly 130 MB total.

**This step is not automated yet.** When the Gradle project exists, wiring it as a build task is the obvious next move; until then it is a manual copy and worth remembering, because a stale asset produces an app that looks fine and serves old data.

## Refresh policy (D-41)

At defined events only: phase start, before first release, and once per release. Never mid-phase.

The cost of refreshing rises sharply after launch — before release nothing can break, afterwards a refresh can orphan a user's saved words (D-39, D-40).

## Open

`sources.json` lists **three candidates** for example sentences. Which one wins should be settled by looking at the real files, not from documentation. See `docs/data-model.md`.
