#!/usr/bin/env python3
"""Download the dictionary source datasets listed in sources.json (D-41).

Stdlib only — no install step. Writes files to data/raw/ and records what was
actually fetched in sources.lock.json.

The lock file is the point of this script. Three of the sources are published at
fixed URLs and regenerated continuously, so the URL alone does not identify a
version. The generation date inside each file's header does.

Usage:
    python fetch.py                 # fetch everything missing
    python fetch.py --force         # re-download even if present
    python fetch.py --only jmdict   # one source (repeatable)
    python fetch.py --list          # show the manifest without downloading
"""

from __future__ import annotations

import argparse
import bz2
import gzip
import hashlib
import json
import re
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).parent
MANIFEST = HERE / "sources.json"
RAW_DIR = HERE / "data" / "raw"
LOCK = HERE / "sources.lock.json"

# Windows consoles default to cp1252 and mangle anything outside it. Every
# script in this project prints either Japanese or typographic punctuation.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# Some servers reject urllib's default agent.
USER_AGENT = "spotter-dictbuild/0.1 (+https://github.com/AdamLewis73/spotter)"
TIMEOUT = 120
CHUNK = 1 << 16


def human(n: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n:.1f} {unit}" if unit != "B" else f"{n} B"
        n /= 1024
    return f"{n:.1f} GB"


def download(url: str, dest: Path) -> None:
    """Stream url to dest, printing progress. Writes to .part then renames."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    part = dest.with_suffix(dest.suffix + ".part")
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})

    # Carriage-return progress is unreadable when piped to a file or a log.
    live = sys.stdout.isatty()

    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
        total = int(resp.headers.get("Content-Length") or 0)
        got = 0
        with part.open("wb") as fh:
            while chunk := resp.read(CHUNK):
                fh.write(chunk)
                got += len(chunk)
                if live:
                    pct = f"  ({100 * got / total:.0f}%)" if total else ""
                    print(f"\r    {human(got)}{pct}", end="")
    if live:
        print()
    part.replace(dest)


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        while chunk := fh.read(CHUNK):
            h.update(chunk)
    return h.hexdigest()


def read_head(path: Path, nbytes: int = 65536) -> str:
    """Decompressed first nbytes of a file, as text. Empty string if unreadable."""
    try:
        if path.suffix == ".gz":
            opener = gzip.open
        elif path.suffix == ".bz2":
            opener = bz2.open
        else:
            opener = open
        with opener(path, "rb") as fh:
            return fh.read(nbytes).decode("utf-8", errors="replace")
    except OSError as exc:
        print(f"    ! could not read header: {exc}")
        return ""


def header_date(path: Path, pattern: str | None) -> str | None:
    if not pattern:
        return None
    match = re.search(pattern, read_head(path))
    return match.group(1) if match else None


def fetch_one(src: dict, force: bool) -> dict:
    name = src["name"]
    dest = RAW_DIR / src["filename"]
    print(f"\n{name}")
    print(f"    {src['url']}")

    if dest.exists() and not force:
        print(f"    already present ({human(dest.stat().st_size)}) — skipping")
    else:
        try:
            download(src["url"], dest)
        except (urllib.error.URLError, urllib.error.HTTPError, OSError) as exc:
            level = "optional" if src.get("optional") else "FAILED"
            print(f"    {level}: {exc}")
            return {**base_record(src), "error": str(exc)}

    size = dest.stat().st_size
    digest = sha256_of(dest)
    expected = src.get("expected_sha256")
    if expected and digest != expected:
        print(f"    ! CHECKSUM MISMATCH\n      expected {expected}\n      got      {digest}")
    elif expected:
        print("    checksum verified against publisher")

    created = header_date(dest, src.get("header_date_pattern"))
    if created:
        print(f"    generated {created}  ({human(size)})")
    else:
        print(f"    {human(size)}")

    return {
        **base_record(src),
        "bytes": size,
        "sha256": digest,
        "header_date": created,
        "fetched_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    }


def base_record(src: dict) -> dict:
    return {
        "name": src["name"],
        "url": src["url"],
        "filename": src["filename"],
        "license": src["license"],
        "versioning": src["versioning"],
        "version": src.get("version"),
    }


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--force", action="store_true", help="re-download even if present")
    ap.add_argument("--only", action="append", metavar="NAME", help="fetch only this source")
    ap.add_argument("--list", action="store_true", help="print the manifest and exit")
    args = ap.parse_args()

    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    sources = manifest["sources"]

    if args.only:
        wanted = set(args.only)
        unknown = wanted - {s["name"] for s in sources}
        if unknown:
            print(f"unknown source(s): {', '.join(sorted(unknown))}", file=sys.stderr)
            return 2
        sources = [s for s in sources if s["name"] in wanted]

    if args.list:
        for s in sources:
            flag = " [optional]" if s.get("optional") else ""
            print(f"{s['name']:24} {s['versioning']:8} {s['url']}{flag}")
        return 0

    records = [fetch_one(s, args.force) for s in sources]

    LOCK.write_text(
        json.dumps({"generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                    "sources": records}, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    print("\n" + "=" * 62)
    total = 0
    for r in records:
        if "error" in r:
            print(f"  {r['name']:26} FAILED")
            continue
        total += r["bytes"]
        date = r["header_date"] or r["version"] or "-"
        print(f"  {r['name']:26} {human(r['bytes']):>10}   {date}")
    print("=" * 62)
    print(f"  {'total downloaded':26} {human(total):>10}")
    print(f"\nlock file: {LOCK.relative_to(HERE.parent.parent)}")

    return 1 if any("error" in r and not r.get("optional") for r in records) else 0


if __name__ == "__main__":
    raise SystemExit(main())
