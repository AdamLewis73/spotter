#!/usr/bin/env python3
"""Run the verification cases from docs/verification.md against a built database.

These are not unit tests. Every case here exists because the bug it catches
produces plausible-looking output with no error — wrong kana script, a word
silently collapsed to one reading, furigana split across characters that do not
own it, examples ordered by insertion instead of frequency. A crash is
self-reporting; these are not.

So the output shows the actual values rather than dots. Seeing that 生's on
readings are セイ and ショウ is the point; "1 passed" is not.

Stdlib only, matching the rest of the builder — no install step.

    python verify.py
    python verify.py --db path/to.db
"""

from __future__ import annotations

import argparse
import gzip
import json
import shutil
import sqlite3
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import changes  # noqa: E402
import kana  # noqa: E402

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

HERE = Path(__file__).parent
CASES = []


def case(cid: str, title: str):
    def deco(fn):
        CASES.append((cid, title, fn))
        return fn
    return deco


def _glosses(db, word_id, sense=1):
    r = db.execute("SELECT glosses FROM word_sense WHERE word_id=? AND sense_order=?",
                   (word_id, sense)).fetchone()
    return json.loads(r[0]) if r else []


# --------------------------------------------------------------- Phase 1

@case("V-01", "Kana script by reading type (D-37)")
def v01(db, out):
    on, kun = db.execute("SELECT on_readings, kun_readings FROM kanji WHERE char='生'").fetchone()
    on, kun = json.loads(on), json.loads(kun)
    out(f"生 on  {', '.join(on)}")
    out(f"生 kun {', '.join(kun[:5])} …")
    ok = on[:2] == ["セイ", "ショウ"] and "なま" in kun
    return ok, "on'yomi katakana, kun'yomi hiragana"


@case("V-02", "Words with multiple readings survive ingest (D-12)")
def v02(db, out):
    rows = db.execute(
        "SELECT reading, ent_seq, reading_info FROM word WHERE text='上手'"
        " ORDER BY ent_seq, reading").fetchall()
    for reading, seq, info in rows:
        tags = ", ".join(json.loads(info)) if info else "-"
        out(f"{reading:<10} ent_seq={seq}  {tags}")
    readings = {r[0] for r in rows}
    entries = {r[1] for r in rows}
    return (len(rows) == 5 and len(entries) == 2
            and {"じょうず", "うわて", "かみて"} <= readings), \
        "five rows across two entries"


@case("V-03", "Jukujikun produces no per-character alignment (D-06, D-13)")
def v03(db, out):
    results = {}
    for reading in ("あした", "あす", "みょうにち"):
        n = db.execute(
            "SELECT count(*) FROM kanji_in_word k JOIN word w ON w.id=k.word_id"
            " WHERE w.text='明日' AND w.reading=?", (reading,)).fetchone()[0]
        results[reading] = n
        out(f"明日 / {reading:<10} {n} rows")
    return results == {"あした": 0, "あす": 0, "みょうにち": 2}, \
        "only the splittable reading aligns"


@case("V-04", "Frequency ranking is applied (D-04)")
def v04(db, out):
    rows = db.execute("""
        SELECT w.text, w.freq_rank FROM kanji_in_word k JOIN word w ON w.id=k.word_id
        WHERE k.kanji_char='生' AND k.reading_group='セイ'
        GROUP BY w.reading ORDER BY w.freq_rank IS NULL, w.freq_rank LIMIT 5""").fetchall()
    out("生 / セイ leads with: " + " · ".join(f"{t}({r})" for t, r in rows))
    unranked = db.execute("SELECT count(*) FROM word WHERE freq_rank IS NULL").fetchone()[0]
    total = db.execute("SELECT count(*) FROM word").fetchone()[0]
    out(f"unranked words {unranked:,} of {total:,} — these must sort LAST")
    return all(r is not None for _, r in rows), "common words lead the group"


@case("V-17", "Reading alignment survives sound changes (D-52)")
def v17(db, out):
    expect = {("学校", "学"): ("ガク", "on"), ("花火", "火"): ("ひ", "kun"),
              ("一生", "一"): ("イチ", "on"), ("仕事", "事"): ("こと", "kun"),
              ("出口", "口"): ("くち", "kun")}
    ok = True
    for (text, ch), (canon, rtype) in expect.items():
        r = db.execute(
            "SELECT k.surface_reading, k.canonical_reading, k.reading_type"
            " FROM kanji_in_word k JOIN word w ON w.id=k.word_id"
            " WHERE w.text=? AND k.kanji_char=? LIMIT 1", (text, ch)).fetchone()
        got = (r[1], r[2]) if r else (None, None)
        mark = "" if got == (canon, rtype) else "   <-- expected " + str((canon, rtype))
        ok &= got == (canon, rtype)
        out(f"{text:<6} {ch}  {r[0] if r else '-':<5} -> {got[0]} ({got[1]}){mark}")
    return ok, "rendaku and gemination resolve"


@case("V-18", "Entry expansion respects restrictions (D-12)")
def v18(db, out):
    senses = {}
    for wid, reading in db.execute("SELECT id, reading FROM word WHERE text='明日'"):
        senses[reading] = db.execute(
            "SELECT count(*) FROM word_sense WHERE word_id=?", (wid,)).fetchone()[0]
        out(f"明日 / {reading:<10} {senses[reading]} senses")
    stagr_ok = senses.get("あす", 0) > senses.get("みょうにち", 0)

    pairs = db.execute("SELECT text, reading FROM word WHERE ent_seq=1000110").fetchall()
    for t, r in pairs:
        out(f"re_restr  {t}  {r}")
    return stagr_ok and len(pairs) == 2, \
        "stagr restricts senses; re_restr prevents a cross-product"


@case("V-19", "The changes table catches a retired key (D-39)")
def v19(db, out):
    # This is the one case that must WRITE — it populates `changes` from a
    # synthetic baseline and reads back what landed. It therefore runs against a
    # throwaway copy, never the real database.
    #
    # It used to write to the artefact directly and delete its rows afterwards.
    # That looked tidy and was not: simply opening SQLite read-write alters the
    # file's bytes, so verifying the dictionary changed it, and the D-58
    # determinism check compared a verified database against a freshly built one
    # and reported a reproducibility failure that did not exist.
    source = Path(db.execute("PRAGMA database_list").fetchone()[2])
    seq = db.execute("SELECT ent_seq FROM word WHERE text='上手'"
                     " AND reading='じょうず'").fetchone()[0]
    gone = db.execute("SELECT max(ent_seq) FROM word").fetchone()[0] + 1
    with tempfile.TemporaryDirectory() as td:
        synthetic = Path(td) / "keys.tsv.gz"
        with gzip.open(synthetic, "wt", encoding="utf-8", newline="\n") as fh:
            for t, r, s in db.execute("SELECT text, reading, ent_seq FROM word"):
                fh.write(f"{t}\t{r}\t{s}\n")
            fh.write(f"上手\tじょうづ\t{seq}\n")        # entry survives
            fh.write(f"架空語\tかくうご\t{gone}\n")     # entry gone

        scratch_path = Path(td) / "scratch.db"
        shutil.copy2(source, scratch_path)
        scratch = sqlite3.connect(scratch_path)
        try:
            scratch.execute("DELETE FROM changes")
            stats = changes.diff(scratch, synthetic, "verify")
            rows = {(r[0], r[1]): (r[2], r[3])
                    for r in scratch.execute("SELECT * FROM changes")}
        finally:
            scratch.close()

    for k, v in rows.items():
        out(f"{k[0]} / {k[1]:<10} -> {v[0] + ' / ' + v[1] if v[0] else '(removed outright)'}")
    return (stats["retired"] == 2 and stats["with_successor"] == 1
            and rows.get(("上手", "じょうづ")) == ("上手", "じょうず")), \
        "successor found where the entry survives, not invented where it does not"


@case("V-22", "Reading-alignment residue stays within bounds (D-52)")
def v22(db, out):
    total, unmatched = db.execute(
        "SELECT count(*), sum(reading_type IS NULL) FROM kanji_in_word").fetchone()
    pct = 100 * unmatched / total
    out(f"{unmatched:,} of {total:,} spans unmatched — {pct:.2f}%  (bound 4%)")
    for text in ("仕事", "出口", "学校", "一生"):
        n = db.execute("SELECT count(*) FROM kanji_in_word k JOIN word w ON w.id=k.word_id"
                       " WHERE w.text=? AND k.reading_type IS NOT NULL", (text,)).fetchone()[0]
        out(f"  {text} resolves: {'yes' if n else 'NO'}")
    return pct < 4.0, "residue below the bound"


@case("V-24", "Loanword kun'yomi keep their katakana (D-37)")
def v24(db, out):
    kun = json.loads(db.execute(
        "SELECT kun_readings FROM kanji WHERE char='志'").fetchone()[0])
    out(f"志 kun {', '.join(kun)}")
    # Count with the same script test the ingest uses, not a LIKE over a few
    # sample characters — a wrong number here is worse than no number.
    n = sum(1 for (j,) in db.execute("SELECT kun_readings FROM kanji")
            for r in json.loads(j) if not kana.is_hiragana(r))
    out(f"katakana kun readings across the table: {n} (expect ~60, bound 100)")
    common = json.loads(db.execute(
        "SELECT kun_readings FROM kanji WHERE char='粉'").fetchone()[0])
    out(f"粉 kun {', '.join(common)}   <- freq 1484, so this is user-visible")
    return "シリング" in kun and 40 < n < 100, "loanword readings not forced to hiragana"


@case("V-09", "Stroke path count agrees with KANJIDIC2 (KanjiVG)")
def v09(db, out):
    for ch in ("生", "先", "手", "一", "鬱"):
        r = db.execute("SELECT k.stroke_count, s.svg_paths FROM kanji k"
                       " JOIN strokes s ON s.kanji_char=k.char WHERE k.char=?",
                       (ch,)).fetchone()
        out(f"{ch}  kanjidic={r[0]:<3} paths={len(json.loads(r[1]))}")
    rows = db.execute("SELECT k.stroke_count, s.svg_paths FROM kanji k"
                      " JOIN strokes s ON s.kanji_char=k.char").fetchall()
    bad = sum(1 for sc, p in rows if len(json.loads(p)) != sc)
    out(f"mismatches {bad} of {len(rows):,} — bound 150; 辶 forms genuinely differ")
    ranked = db.execute("SELECT count(*) FROM kanji k JOIN strokes s"
                        " ON s.kanji_char=k.char WHERE k.freq_rank IS NOT NULL").fetchone()[0]
    out(f"ranked kanji with strokes: {ranked:,} of 2,501")
    # NOTE: `ranked == 2501` is an exact equality against an upstream dataset and
    # will fail the day KANJIDIC2 gains or loses a ranked kanji — a spurious
    # failure rather than a caught regression. The fault worth catching is
    # coverage collapsing, which a lower bound detects just as well. Change it to
    # `ranked >= 2400` next time dictbuild is touched; see the phase-01 progress
    # notes. Left alone for now because it only bites on a source refresh (D-41).
    return bad < 150 and ranked == 2501, "bounded disagreement, full coverage where it matters"


@case("V-21", "The re_inf vocabulary is exactly what the UI decodes (D-53, D-66)")
def v21(db, out):
    """The data half of V-21. The rendering half needs a screen, not a database.

    `ReadingStatus.of()` maps four of these codes and treats anything else as an
    ordinary current reading, because a dictionary refresh must not crash the app
    over a tag it has not met. That leniency is only safe if a NEW tag is loud
    somewhere, and this is where: JMdict adds re_inf codes occasionally, and one
    arriving unnoticed means a reading the app should mark rendering as normal —
    the exact silence V-21 exists to catch, reintroduced through the back door.

    Failing here means deciding what the new code means and adding it to
    ReadingStatus, not widening this set.
    """
    known = {"ok", "ik", "rk", "sk", "gikun"}
    seen = {}
    for (info,) in db.execute("SELECT reading_info FROM word WHERE reading_info IS NOT NULL"):
        for tag in json.loads(info):
            seen[tag] = seen.get(tag, 0) + 1
    for tag, n in sorted(seen.items(), key=lambda kv: -kv[1]):
        out(f"{tag:<8} {n:>6,}" + ("" if tag in known else "   <- UNKNOWN to ReadingStatus"))

    # 上手 is the case verification.md names, and it needs all three shapes
    # present or the UI has nothing to distinguish: a current reading, an
    # obsolete one, and the inherited frequency that makes them tie.
    rows = db.execute("SELECT reading, reading_info, freq_rank FROM word"
                      " WHERE text='上手' ORDER BY reading").fetchall()
    tagged = {r[0]: json.loads(r[1]) if r[1] else [] for r in rows}
    out("上手  " + "  ".join(f"{r}{'(' + ','.join(t) + ')' if t else ''}"
                             for r, t in tagged.items()))
    ranks = {r[0]: r[2] for r in rows}
    out(f"じょうしゅ inherits 上手's rank {ranks.get('じょうしゅ')} from the writing"
        f" — same as じょうず ({ranks.get('じょうず')}), which is why the query alone"
        " cannot order them")

    unknown = set(seen) - known
    return (not unknown
            and tagged.get("じょうて") == ["ok"]
            and tagged.get("じょうしゅ") == ["ok"]
            and tagged.get("じょうず") == []),         "no unrecognised re_inf codes; 上手 carries the ok tags the UI marks"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--db", type=Path, default=HERE / "data" / "build" / "spotter.db")
    args = ap.parse_args()
    if not args.db.exists():
        sys.exit(f"{args.db} not found — run build.py first")

    # READ-ONLY, and not merely as good practice. A plain sqlite3.connect()
    # opens read-write, and simply connecting is enough to change the file's
    # bytes without changing its size or content — SQLite touches the header.
    #
    # That silently broke the D-58 determinism check: CI builds, verifies, then
    # rebuilds and compares checksums, so the verifier was mutating the very
    # artefact under comparison and the rebuild "failed" reproducibility. The
    # bug was invisible until the database became byte-reproducible (D-64) and
    # something finally checksummed it.
    #
    # A tool whose job is to verify an artefact must not write to it.
    db = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    failures = []
    for cid, title, fn in CASES:
        lines: list[str] = []
        try:
            ok, claim = fn(db, lines.append)
        except Exception as exc:                      # a case that cannot run is a failure
            ok, claim = False, f"raised {type(exc).__name__}: {exc}"
        print(f"\n{'PASS' if ok else 'FAIL'}  {cid}  {title}")
        for line in lines:
            print(f"        {line}")
        print(f"        -> {claim}")
        if not ok:
            failures.append(cid)
    db.commit()

    print(f"\n{'=' * 60}")
    print(f"{len(CASES) - len(failures)} of {len(CASES)} cases pass"
          + (f"   FAILED: {', '.join(failures)}" if failures else ""))
    print("V-05 is a review check (no dictionary row ids in any user-facing "
          "contract) and\nhas no user database to check against yet.")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
