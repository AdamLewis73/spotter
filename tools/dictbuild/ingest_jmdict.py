"""Parse JMdict into the `word`, `word_sense` and `example` tables.

The big stage: 218,329 entries expanding to 322,323 (text, reading) rows.

A JMdict entry is NOT a word. One <entry> holds several kanji writings and
several readings, with explicit restrictions between them, and expanding it
carelessly invents words that do not exist. Measured against the real file,
a naive cross-product produces 11,547 rows too many (V-18).

Four things this file exists to get right:

  * re_restr / re_nokanji  — which readings go with which writings
  * stagk / stagr          — which SENSES apply to which of the expanded rows
  * ke_pri / re_pri        — frequency, which lives on writings and readings
                             separately rather than on the entry. Both a combined
                             rank (for ranking words) and a reading-only rank (for
                             ordering the readings within one word) are stored;
                             D-84 and V-29 explain why one number cannot do both
  * colliding keys         — 1,659 merges, where a (text, reading) pair is
                             produced by more than one entry
"""

from __future__ import annotations

import gzip
import json
import re
import sqlite3
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

# JMdict tags everything with XML entities declared in its internal DTD subset.
# The parser expands them to their DESCRIPTIONS ("noun (common) (futsuumeishi)"),
# but D-53 and D-54 need the compact codes ("n", "ok", "vulg"), so read the
# declarations and map back.
_ENTITY_DECL = re.compile(r'<!ENTITY\s+(\S+)\s+"([^"]*)">')
_NF_BAND = re.compile(r"^nf(\d+)$")

# ElementTree expands xml:lang to its full namespace URI, and an XPath predicate
# cannot use the `xml:` prefix without a namespace map — so match on the
# attribute directly.
_XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"

# Priority markers that mean "common" without giving a band.
_COMMON_1 = {"ichi1", "news1", "spec1", "gai1"}
_COMMON_2 = {"ichi2", "news2", "spec2", "gai2"}
_RANK_COMMON_1 = 49       # worse than any nf band (1–48), better than nothing
_RANK_COMMON_2 = 50


def load_entity_codes(path: Path) -> tuple[dict[str, str], list[str]]:
    """Build {description: code} from the DTD. Returns the map and any
    descriptions claimed by more than one entity."""
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        head = fh.read(200_000)          # the DTD sits at the top of the file
    pairs = _ENTITY_DECL.findall(head)
    codes: dict[str, str] = {}
    collisions: list[str] = []
    for code, desc in pairs:
        if desc in codes:
            collisions.append(desc)      # first declaration wins, deterministically
        else:
            codes[desc] = code
    return codes, collisions


def freq_rank(priorities: set[str]) -> int | None:
    """Collapse JMdict priority markers into one sortable rank, lower = commoner.

    nf01–nf48 divide the newspaper-frequency corpus into bands of 500 words, so
    the band number is already an ordering. Entries marked common without a band
    sort after every banded entry but before unranked ones.

    Returns None for unranked words — roughly 74% of entries. Those MUST sort
    last rather than first, or V-04's example ordering is arbitrary for most of
    the dictionary.
    """
    bands = [int(m.group(1)) for p in priorities if (m := _NF_BAND.match(p))]
    if bands:
        return min(bands)
    if priorities & _COMMON_1:
        return _RANK_COMMON_1
    if priorities & _COMMON_2:
        return _RANK_COMMON_2
    return None


def _texts(el: ET.Element, tag: str) -> list[str]:
    return [c.text for c in el.findall(tag) if c.text]


def _codes(el: ET.Element, tag: str, codes: dict[str, str]) -> list[str]:
    """Element text is an expanded entity description; map it back to its code."""
    return [codes.get(t, t) for t in _texts(el, tag)]


def _ex_sent(example: ET.Element, lang: str) -> str | None:
    for s in example.findall("ex_sent"):
        if s.get(_XML_LANG) == lang:
            return s.text
    return None


# JMdict's final entry is not a word. It stamps the file's creation date into
# the data itself:
#
#   <ent_seq>9999999</ent_seq>
#   <keb>ＪＭｄｉｃｔ</keb>
#   <gloss>Japanese-Multilingual Dictionary Project - Creation Date: …</gloss>
#
# Ingested naively it becomes a lookup-able word, which is junk in a dictionary.
_METADATA_ENT_SEQ = 9999999


def expand(entry: ET.Element, codes: dict[str, str]) -> list[dict]:
    """One entry -> the (text, reading) rows it legitimately produces."""
    ent_seq = int(entry.findtext("ent_seq"))
    if ent_seq == _METADATA_ENT_SEQ:
        return []

    writings = {}                                     # keb -> priority set
    for k in entry.findall("k_ele"):
        writings[k.findtext("keb")] = set(_texts(k, "ke_pri"))

    words = []
    for r in entry.findall("r_ele"):
        reb = r.findtext("reb")
        r_pri = set(_texts(r, "re_pri"))
        r_inf = _codes(r, "re_inf", codes)

        # A reading with re_nokanji is the kana form of a word normally written
        # with kanji — it stands alone rather than pairing with any writing.
        if not writings or r.find("re_nokanji") is not None:
            words.append({"text": reb, "reading": reb, "keb": None,
                          "ent_seq": ent_seq, "pri": r_pri,
                          "reading_pri": r_pri, "info": r_inf})
            continue

        # re_restr limits this reading to specific writings. Without it the
        # reading applies to all of them.
        restr = _texts(r, "re_restr")
        for keb in (restr or writings):
            # `pri` unions the writing's markers in, which is right for ranking
            # this WORD against other words. `reading_pri` deliberately does not,
            # because a strongly-marked writing floods every reading it pairs
            # with and erases the distinction JMdict draws between them — which
            # is how 一人 ends up leading with いちにん (D-84, V-29).
            words.append({"text": keb, "reading": reb, "keb": keb,
                          "ent_seq": ent_seq,
                          "pri": r_pri | writings.get(keb, set()),
                          "reading_pri": r_pri, "info": r_inf})

    # Senses, each carrying the restrictions that decide which words get it.
    senses = []
    for s in entry.findall("sense"):
        senses.append({
            "stagk": set(_texts(s, "stagk")),
            "stagr": set(_texts(s, "stagr")),
            "glosses": _texts(s, "gloss"),
            "pos": _codes(s, "pos", codes),
            "misc": _codes(s, "misc", codes),
            "examples": [
                {
                    "japanese": _ex_sent(ex, "jpn"),
                    "english": _ex_sent(ex, "eng"),
                    "tatoeba_id": ex.findtext("ex_srce"),
                }
                for ex in s.findall("example")
            ],
        })

    for w in words:
        w["senses"] = [s for s in senses if _applies(s, w)]
    return words


def _applies(sense: dict, word: dict) -> bool:
    """stagk restricts a sense to particular writings, stagr to particular
    readings. Both must be satisfied when both are present.

    A word with no writing (kana-only, or re_nokanji) cannot satisfy a stagk
    restriction, so such senses correctly do not attach to it.
    """
    if sense["stagk"] and word["keb"] not in sense["stagk"]:
        return False
    if sense["stagr"] and word["reading"] not in sense["stagr"]:
        return False
    return True


def parse(path: Path, codes: dict[str, str]):
    with gzip.open(path, "rb") as fh:
        for _, el in ET.iterparse(fh, events=("end",)):
            if el.tag != "entry":
                continue
            yield expand(el, codes)
            el.clear()


def ingest(db: sqlite3.Connection, path: Path) -> Counter:
    stats = Counter()
    codes, collisions = load_entity_codes(path)
    stats["dtd_entities"] = len(codes)
    stats["dtd_desc_collisions"] = len(collisions)

    # (text, reading) -> (word_id, next sense_order). Holding only this keeps the
    # pass streaming; senses and examples are written as they are produced.
    seen: dict[tuple[str, str], list[int]] = {}
    next_id = 1

    for words in parse(path, codes):
        stats["entries"] += 1
        if not words:
            stats["skipped_metadata_entry"] += 1
        for w in words:
            key = (w["text"], w["reading"])
            rank = freq_rank(w["pri"])
            # Same function, same scale — only the input set differs (D-84).
            reading_rank = freq_rank(w["reading_pri"])

            if key in seen:
                stats["merged_into_existing"] += 1
                word_id, sense_no = seen[key]
                # A later entry may be the commoner one; keep the best signal.
                db.execute(
                    "UPDATE word SET freq_rank = CASE"
                    "   WHEN freq_rank IS NULL THEN ?"
                    "   WHEN ? IS NULL THEN freq_rank"
                    "   ELSE MIN(freq_rank, ?) END,"
                    " reading_freq_rank = CASE"
                    "   WHEN reading_freq_rank IS NULL THEN ?"
                    "   WHEN ? IS NULL THEN reading_freq_rank"
                    "   ELSE MIN(reading_freq_rank, ?) END,"
                    " is_common = MAX(is_common, ?) WHERE id = ?",
                    (rank, rank, rank,
                     reading_rank, reading_rank, reading_rank,
                     1 if w["pri"] else 0, word_id),
                )
            else:
                word_id, sense_no = next_id, 1
                next_id += 1
                stats["words"] += 1
                db.execute(
                    "INSERT INTO word (id, text, reading, ent_seq, reading_info,"
                    " freq_rank, reading_freq_rank, is_common)"
                    " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    (word_id, w["text"], w["reading"], w["ent_seq"],
                     json.dumps(w["info"], ensure_ascii=False) if w["info"] else None,
                     rank, reading_rank, 1 if w["pri"] else 0),
                )
                if w["text"] == w["reading"]:
                    stats["kana_only"] += 1
                if rank is not None:
                    stats["ranked"] += 1

            for s in w["senses"]:
                db.execute(
                    "INSERT INTO word_sense (word_id, sense_order, glosses,"
                    " part_of_speech, misc) VALUES (?, ?, ?, ?, ?)",
                    (word_id, sense_no,
                     json.dumps(s["glosses"], ensure_ascii=False),
                     json.dumps(s["pos"], ensure_ascii=False) if s["pos"] else None,
                     json.dumps(s["misc"], ensure_ascii=False) if s["misc"] else None),
                )
                stats["senses"] += 1
                for ex in s["examples"]:
                    if not (ex["japanese"] and ex["english"]):
                        continue
                    db.execute(
                        "INSERT INTO example (word_id, sense_order, japanese,"
                        " english, tatoeba_id) VALUES (?, ?, ?, ?, ?)",
                        (word_id, sense_no, ex["japanese"], ex["english"],
                         int(ex["tatoeba_id"]) if ex["tatoeba_id"] else None),
                    )
                    stats["examples"] += 1
                sense_no += 1

            seen[key] = [word_id, sense_no]

    stats["colliding_keys"] = stats["merged_into_existing"]
    return stats
