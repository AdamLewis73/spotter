#!/usr/bin/env python3
"""Build spotter.db from the raw sources.

The output is read-only, disposable, and rebuilt from scratch every run (D-09,
D-38). There is no migration path and none is wanted: if the schema changes,
edit schema.sql and run this again.

Usage:
    python build.py                    # full build
    python build.py --only kanjidic    # one stage (repeatable)
    python build.py --out path/to.db
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import changes
import ingest_furigana
import ingest_jmdict
import ingest_kanjidic
import ingest_kanjivg

HERE = Path(__file__).parent
SCHEMA = HERE / "schema.sql"
LOCK = HERE / "sources.lock.json"
RAW = HERE / "data" / "raw"
DEFAULT_OUT = HERE / "data" / "build" / "spotter.db"
# The last SHIPPED build's key list, committed. See changes.py.
BASELINE_KEYS = HERE / "baseline" / "keys.tsv.gz"

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def load_lock() -> dict:
    if not LOCK.exists():
        sys.exit("sources.lock.json missing — run fetch.py first")
    lock = json.loads(LOCK.read_text(encoding="utf-8"))
    return {s["name"]: s for s in lock["sources"]}


def build_id(sources: dict) -> str:
    """A pure function of the source checksums, so identical sources produce an
    identical id and a rebuild that changed nothing is visibly a rebuild that
    changed nothing (D-58).

    This carried a `%Y%m%d-` prefix until 2026-08-11, which meant two builds
    from byte-identical sources got different ids on different days — exactly
    the "the label is a lie" failure D-58 exists to prevent, and it silently
    broke the `changes` diff's notion of which build an entry disappeared in
    (D-39). Do not reintroduce a date here; wall-clock belongs in
    build-info.json (D-64).
    """
    return hashlib.sha256(
        "".join(s["sha256"] for s in sorted(sources.values(), key=lambda x: x["name"]))
        .encode()
    ).hexdigest()[:12]


def create(out: Path) -> sqlite3.Connection:
    out.parent.mkdir(parents=True, exist_ok=True)
    out.unlink(missing_ok=True)          # disposable — never migrated
    db = sqlite3.connect(out)
    db.executescript(SCHEMA.read_text(encoding="utf-8"))
    return db


def write_meta(db: sqlite3.Connection, sources: dict, bid: str) -> None:
    """Everything written here must be a function of the sources (D-58).

    `built_at` used to live in this table. A wall-clock timestamp inside the
    artefact guarantees the bytes differ on every build, which made D-58's
    byte-identical rule unachievable rather than merely unverified. It now goes
    to build-info.json instead (D-64) — the provenance is still recorded, just
    not inside the thing whose bytes are the checksum.
    """
    versions = {
        name: {"header_date": s.get("header_date"), "version": s.get("version"),
               "sha256": s["sha256"]}
        for name, s in sources.items()
    }
    db.execute(
        "INSERT OR REPLACE INTO meta (build_id, source_versions) VALUES (?, ?)",
        (bid, json.dumps(versions, ensure_ascii=False, sort_keys=True)),
    )


def write_build_info(out: Path, sources: dict, bid: str) -> Path:
    """Wall-clock provenance, deliberately OUTSIDE the database (D-64).

    Answers "when was this built, and from what?" without putting a
    non-reproducible byte into the shipped artefact. Not an app asset — the app
    reads `meta`; this is for humans and for debugging a build.
    """
    info = out.parent / "build-info.json"
    info.write_text(
        json.dumps(
            {
                "build_id": bid,
                "built_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                "database": out.name,
                "sources": {
                    name: {"header_date": s.get("header_date"),
                           "version": s.get("version"), "sha256": s["sha256"]}
                    for name, s in sources.items()
                },
            },
            ensure_ascii=False, indent=2, sort_keys=True,
        ) + "\n",
        encoding="utf-8",
    )
    return info


STAGES = {
    "kanjidic": (ingest_kanjidic.ingest, "kanjidic2"),
    "jmdict": (ingest_jmdict.ingest, "jmdict"),
    "furigana": (ingest_furigana.ingest, "jmdictfurigana"),
    "kanjivg": (ingest_kanjivg.ingest, "kanjivg"),
}

# Counters that mean something is wrong, with the bound at which to say so.
# A stat with a known non-zero expected value belongs here with its ceiling,
# not in the code as a silent tolerance.
BOUNDS = {
    "on_not_katakana": 0,            # D-37: on'yomi are katakana, no exceptions
    "kun_katakana_loanword": 100,    # ~60 expected — loanword ateji, see V-24
    "unmatched_pct": 4,              # D-52 measured 2.25%; V-22 fails above ~4%
    "stroke_count_mismatch": 150,    # ~109 expected — KanjiVG and KANJIDIC2 genuinely
                                     #   disagree on 辶 forms etc. Bound, not equality (V-09)
}


# Measured 99.7 MB at build 20260807 (D-56). The band is wide enough for
# ordinary dictionary drift and narrow enough to catch a layout mistake.
EXPECTED_SIZE_MB = (90, 115)


def _flag(key: str, value: int) -> str:
    limit = BOUNDS.get(key)
    return "   <-- OVER LIMIT" if limit is not None and value > limit else ""


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--only", action="append", metavar="STAGE",
                    help=f"run one stage: {', '.join(STAGES)}")
    args = ap.parse_args()

    stages = args.only or list(STAGES)
    unknown = set(stages) - set(STAGES)
    if unknown:
        sys.exit(f"unknown stage(s): {', '.join(sorted(unknown))}")

    sources = load_lock()
    bid = build_id(sources)
    print(f"build {bid}\noutput {args.out}\n")

    db = create(args.out)
    write_meta(db, sources, bid)
    info = write_build_info(args.out, sources, bid)

    for name in stages:
        fn, source_name = STAGES[name]
        path = RAW / sources[source_name]["filename"]
        started = time.monotonic()
        print(f"{name}")
        stats = fn(db, path)
        db.commit()
        for k, v in stats.items():
            print(f"    {k:<26} {v:>9,}{_flag(k, v)}")
        print(f"    {'elapsed':<24} {time.monotonic() - started:>9.1f}s")

    # Finalize: emit this build's key list, then diff it against the last
    # shipped one to populate `changes` (D-39). Both need the completed word
    # table, so they run after every ingest stage rather than as one of them.
    if not args.only:
        print("changes")
        keys_out = args.out.parent / "keys.tsv.gz"
        n = changes.write_keys(db, keys_out)
        stats = changes.diff(db, BASELINE_KEYS, bid)
        db.commit()
        print(f"    {'keys written':<26} {n:>9,}")
        for k, v in stats.items():
            print(f"    {k:<26} {v:>9,}")
        if stats.get("baseline_absent"):
            print(f"    -> no baseline yet. To ship this build as the reference:")
            print(f"       cp {keys_out.name} baseline/keys.tsv.gz")

    # Bulk inserts leave free pages scattered through the file. The dictionary
    # ships in an APK, so a few megabytes for one command is worth taking.
    before = args.out.stat().st_size
    db.execute("VACUUM")
    db.close()
    after = args.out.stat().st_size
    mb = after / 1024 / 1024
    print(f"\n{args.out.name}  {mb:.1f} MB"
          f"  (VACUUM reclaimed {(before - after) / 1024 / 1024:.1f} MB)")
    print(f"{info.name}  build {bid}")

    # A layout regression is invisible in a passing build otherwise. D-56 got the
    # `strokes` table wrong by 3.4 MB and the total still looked like a win — so
    # the size carries a band, and stepping outside it asks for the per-object
    # measurement to be repeated rather than the number to be edited.
    if args.only:
        return 0
    lo, hi = EXPECTED_SIZE_MB
    if not lo <= mb <= hi:
        direction = "SMALLER" if mb < lo else "LARGER"
        print(f"\n  !! {direction} than the expected {lo}-{hi} MB band (D-56).")
        print(f"     Re-measure per object before accepting: drop each table and")
        print(f"     index in turn, VACUUM, record the delta. The total hides a")
        print(f"     single table moving the wrong way.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
