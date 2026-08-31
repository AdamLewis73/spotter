#!/usr/bin/env python3
"""Unit tests for the dictionary builder's pure functions.

These are a different thing from verify.py, and both are needed.

    verify.py   asks "is the DATA right?" — runs against a built database,
                takes 45 seconds, checks specific known words.
    this file   asks "is the CODE right?" — runs in milliseconds, covers edge
                cases the real data may not happen to contain.

The distinction matters because verify.py samples. If a rendaku mapping were
wrong, 学校 and 花火 might still resolve while thousands of other words broke,
and every verification case would pass. A test on the mapping itself catches it.

    python test_dictbuild.py           # or: python -m unittest
"""

from __future__ import annotations

import sys
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import changes
import ingest_furigana
import ingest_jmdict
import ingest_kanjivg
import kana


class TestKanaScript(unittest.TestCase):
    def test_conversion_round_trips(self):
        self.assertEqual(kana.to_katakana("せんせい"), "センセイ")
        self.assertEqual(kana.to_hiragana("センセイ"), "せんせい")

    def test_conversion_leaves_non_kana_alone(self):
        # ー (prolonged sound mark) sits outside both ranges and must survive,
        # or ラーメン-style readings get mangled.
        self.assertEqual(kana.to_katakana("なま-"), "ナマ-")
        self.assertEqual(kana.to_hiragana("キロメートル"), "きろめーとる")

    def test_script_detection_ignores_punctuation(self):
        self.assertTrue(kana.is_katakana("キロメートル"))   # contains ー
        self.assertTrue(kana.is_hiragana("い.きる"))        # contains .
        self.assertFalse(kana.is_katakana("こころざし"))
        self.assertFalse(kana.is_hiragana("シリング"))

    def test_script_detection_on_empty_input(self):
        # A reading with no kana at all is not "all katakana" — returning True
        # here would make V-24's loanword count meaningless.
        self.assertFalse(kana.is_katakana(""))
        self.assertFalse(kana.is_hiragana("---"))


class TestOkurigana(unittest.TestCase):
    """KANJIDIC2 marks position: '.' splits okurigana, '-' marks prefix/suffix."""

    def test_stem_strips_markers(self):
        self.assertEqual(kana.strip_okurigana("い.きる"), "い")
        self.assertEqual(kana.strip_okurigana("なま-"), "なま")
        self.assertEqual(kana.strip_okurigana("-う"), "う")
        self.assertEqual(kana.strip_okurigana("なま"), "なま")

    def test_full_form_keeps_okurigana(self):
        self.assertEqual(kana.full_reading("い.きる"), "いきる")
        self.assertEqual(kana.full_reading("なま-"), "なま")


class TestVariants(unittest.TestCase):
    """D-52's normalizer. A bug here silently empties reading groups."""

    def test_rendaku_unvoices_the_first_mora_only(self):
        self.assertIn("ひ", kana.variants("び"))        # 花火: 火 is ひ
        self.assertIn("こと", kana.variants("ごと"))     # 仕事: 事 is こと
        self.assertIn("くち", kana.variants("ぐち"))     # 出口: 口 is くち
        # Only the FIRST mora voices in rendaku; a later one must be left alone.
        self.assertNotIn("かき", kana.variants("かぎ"))

    def test_handakuten_maps_back_to_the_unvoiced_row(self):
        # ぱ行 comes from は行, not ば行 — 発表 はっぴょう.
        self.assertIn("ひょう", kana.variants("ぴょう"))

    def test_gemination_restores_the_lost_mora(self):
        v = kana.variants("がっ")
        self.assertIn("がく", v)                        # 学校: 学 is がく
        self.assertIn("がち", v)
        self.assertNotIn("がっ", v - {"がっ"})

    def test_rendaku_and_gemination_compose(self):
        # A voiced, geminated surface must reach the plain dictionary reading.
        self.assertIn("はつ", kana.variants("ばっ"))

    def test_surface_is_always_a_candidate(self):
        self.assertIn("なま", kana.variants("なま"))


class TestFrequencyRule(unittest.TestCase):
    """V-04 depends entirely on this ordering."""

    def test_nf_band_wins_and_takes_the_best(self):
        self.assertEqual(ingest_jmdict.freq_rank({"nf12", "ichi1"}), 12)
        self.assertEqual(ingest_jmdict.freq_rank({"nf40", "nf05"}), 5)

    def test_common_without_a_band_sorts_after_every_band(self):
        rank = ingest_jmdict.freq_rank({"ichi1"})
        self.assertGreater(rank, 48)
        self.assertLess(rank, ingest_jmdict.freq_rank({"ichi2"}))

    def test_unranked_is_none_not_zero(self):
        # Zero would sort FIRST and put obscure vocabulary at the top of every
        # example list — the exact failure V-04 exists to catch.
        self.assertIsNone(ingest_jmdict.freq_rank(set()))
        self.assertIsNone(ingest_jmdict.freq_rank({"something-unknown"}))


class TestReadingLevelRank(unittest.TestCase):
    """V-29 and D-84 — the same function, applied to a smaller input set.

    The bug was never in `freq_rank` itself. It was in what got passed to it: a
    word's rank unions the writing's `ke_pri` with the reading's `re_pri`, and
    that union is what erases the difference between two readings of one written
    form. These cases are 一人's actual tags.
    """

    # ke_pri on the writing 一人, and re_pri on each of its two readings.
    WRITING = {"ichi1", "news1", "nf02"}
    HITORI = {"ichi1", "news1", "nf02", "nf16", "spec1"}
    ICHININ: set[str] = set()

    def test_the_union_makes_both_readings_look_equal(self):
        # The old behaviour, kept as a test so the regression is legible: both
        # readings rank 2, the sort falls through to kana, いちにん wins.
        self.assertEqual(ingest_jmdict.freq_rank(self.HITORI | self.WRITING), 2)
        self.assertEqual(ingest_jmdict.freq_rank(self.ICHININ | self.WRITING), 2)

    def test_the_readings_own_tags_separate_them(self):
        self.assertEqual(ingest_jmdict.freq_rank(self.HITORI), 2)
        self.assertIsNone(ingest_jmdict.freq_rank(self.ICHININ))

    def test_an_unmarked_reading_sorts_last_not_first(self):
        # Same trap as V-04's, one level down: None must sort LAST. A zero here
        # would promote every unmarked reading to the front of its own word.
        self.assertIsNone(ingest_jmdict.freq_rank(self.ICHININ))


class TestEntryExpansion(unittest.TestCase):
    """V-18. A naive cross-product invents 11,547 words that do not exist."""

    CODES = {"out-dated or obsolete kana usage": "ok"}

    def expand(self, xml: str):
        return ingest_jmdict.expand(ET.fromstring(xml), self.CODES)

    def test_re_restr_prevents_a_cross_product(self):
        words = self.expand("""
            <entry><ent_seq>1</ent_seq>
              <k_ele><keb>ＣＤプレーヤー</keb></k_ele>
              <k_ele><keb>ＣＤプレイヤー</keb></k_ele>
              <r_ele><reb>シーディープレーヤー</reb>
                     <re_restr>ＣＤプレーヤー</re_restr></r_ele>
              <r_ele><reb>シーディープレイヤー</reb>
                     <re_restr>ＣＤプレイヤー</re_restr></r_ele>
              <sense><gloss>CD player</gloss></sense>
            </entry>""")
        pairs = {(w["text"], w["reading"]) for w in words}
        self.assertEqual(len(pairs), 2)                       # not 4
        self.assertIn(("ＣＤプレーヤー", "シーディープレーヤー"), pairs)
        self.assertNotIn(("ＣＤプレーヤー", "シーディープレイヤー"), pairs)

    def test_a_reading_without_restriction_pairs_with_every_writing(self):
        words = self.expand("""
            <entry><ent_seq>2</ent_seq>
              <k_ele><keb>会う</keb></k_ele>
              <k_ele><keb>逢う</keb></k_ele>
              <r_ele><reb>あう</reb></r_ele>
              <sense><gloss>to meet</gloss></sense>
            </entry>""")
        self.assertEqual(len(words), 2)

    def test_kana_only_entry_has_text_equal_to_reading(self):
        words = self.expand("""
            <entry><ent_seq>3</ent_seq>
              <r_ele><reb>ください</reb></r_ele>
              <sense><gloss>please</gloss></sense>
            </entry>""")
        self.assertEqual(words[0]["text"], words[0]["reading"])

    def test_re_nokanji_reading_stands_alone(self):
        words = self.expand("""
            <entry><ent_seq>4</ent_seq>
              <k_ele><keb>玉子</keb></k_ele>
              <r_ele><reb>たまご</reb></r_ele>
              <r_ele><reb>タマゴ</reb><re_nokanji/></r_ele>
              <sense><gloss>egg</gloss></sense>
            </entry>""")
        pairs = {(w["text"], w["reading"]) for w in words}
        self.assertIn(("玉子", "たまご"), pairs)
        self.assertIn(("タマゴ", "タマゴ"), pairs)      # not paired with 玉子
        self.assertNotIn(("玉子", "タマゴ"), pairs)

    def test_stagr_restricts_a_sense_to_one_reading(self):
        words = self.expand("""
            <entry><ent_seq>5</ent_seq>
              <k_ele><keb>明日</keb></k_ele>
              <r_ele><reb>あす</reb></r_ele>
              <r_ele><reb>みょうにち</reb></r_ele>
              <sense><gloss>tomorrow</gloss></sense>
              <sense><stagr>あす</stagr><gloss>near future</gloss></sense>
            </entry>""")
        by_reading = {w["reading"]: len(w["senses"]) for w in words}
        self.assertEqual(by_reading["あす"], 2)
        self.assertEqual(by_reading["みょうにち"], 1)

    def test_metadata_entry_is_not_a_word(self):
        # JMdict's last entry stamps the file's creation date into the data.
        words = self.expand("""
            <entry><ent_seq>9999999</ent_seq>
              <k_ele><keb>ＪＭｄｉｃｔ</keb></k_ele>
              <r_ele><reb>ジェイエムディクト</reb></r_ele>
              <sense><gloss>Japanese-Multilingual Dictionary Project</gloss></sense>
            </entry>""")
        self.assertEqual(words, [])

    def test_entity_descriptions_map_back_to_codes(self):
        words = self.expand("""
            <entry><ent_seq>6</ent_seq>
              <k_ele><keb>上手</keb></k_ele>
              <r_ele><reb>じょうて</reb>
                     <re_inf>out-dated or obsolete kana usage</re_inf></r_ele>
              <sense><gloss>skillful</gloss></sense>
            </entry>""")
        self.assertEqual(words[0]["info"], ["ok"])


class TestReadingMatch(unittest.TestCase):
    """The alignment core. Both ways of getting it wrong are silent (V-17)."""

    def readings(self, on=(), kun=()):
        by_stem, by_full = {}, {}
        for r in kun:
            by_stem.setdefault(kana.strip_okurigana(r), []).append(r)
            by_full[kana.full_reading(r)] = r
        return {"on": list(on), "stem": by_stem, "full": by_full}

    def test_exact_on_reading(self):
        r = self.readings(on=["セイ", "ショウ"])
        self.assertEqual(ingest_furigana.match("せい", "", r), ("セイ", "on"))

    def test_gemination_resolves_to_the_dictionary_reading(self):
        r = self.readings(on=["ガク"])                       # 学校
        self.assertEqual(ingest_furigana.match("がっ", "", r), ("ガク", "on"))

    def test_rendaku_resolves_to_the_dictionary_reading(self):
        r = self.readings(kun=["ひ"])                        # 花火
        self.assertEqual(ingest_furigana.match("び", "", r), ("ひ", "kun"))

    def test_okurigana_from_the_word_picks_the_specific_reading(self):
        # 生きる: surface い, followed by きる -> い.きる, not the bare stem い
        # which 生 also shares with い.かす and い.ける.
        r = self.readings(kun=["い.きる", "い.かす", "い.ける"])
        self.assertEqual(ingest_furigana.match("い", "きる", r), ("い.きる", "kun"))

    def test_ambiguous_stem_falls_back_to_the_stem(self):
        r = self.readings(kun=["い.きる", "い.かす"])
        self.assertEqual(ingest_furigana.match("い", "", r), ("い", "kun"))

    def test_unambiguous_stem_returns_the_specific_reading(self):
        r = self.readings(kun=["なま-"])
        self.assertEqual(ingest_furigana.match("なま", "", r), ("なま-", "kun"))

    def test_no_match_returns_none_rather_than_guessing(self):
        # 文 -> も in 文字 is not in KANJIDIC2 at all. NULL keeps it countable
        # (V-22) instead of filing it under a reading it does not have.
        r = self.readings(on=["ブン", "モン"])
        self.assertEqual(ingest_furigana.match("も", "", r), (None, None))

    def test_ambiguity_resolves_by_kanjidic_order_not_hash_order(self):
        # D-58. 一 is both イチ and イツ, and いっ geminates from either. The
        # winner must be the reading KANJIDIC2 lists FIRST, deterministically —
        # iterating a candidate set made this depend on the process hash seed.
        first = self.readings(on=["イチ", "イツ"])
        self.assertEqual(ingest_furigana.match("いっ", "", first), ("イチ", "on"))
        swapped = self.readings(on=["イツ", "イチ"])
        self.assertEqual(ingest_furigana.match("いっ", "", swapped), ("イツ", "on"))

    def test_on_is_preferred_over_kun_when_both_could_match(self):
        r = self.readings(on=["キ"], kun=["き"])
        self.assertEqual(ingest_furigana.match("き", "", r)[1], "on")


class TestFollowingKana(unittest.TestCase):
    def test_takes_hiragana_up_to_the_next_kanji(self):
        self.assertEqual(ingest_furigana._following_kana("生きる", 0), "きる")
        self.assertEqual(ingest_furigana._following_kana("生き物", 0), "き")
        self.assertEqual(ingest_furigana._following_kana("先生", 0), "")

    def test_stops_at_katakana(self):
        # 生ビール — ビール is the word, not okurigana belonging to 生.
        self.assertEqual(ingest_furigana._following_kana("生ビール", 0), "")


class TestSuccessorHeuristic(unittest.TestCase):
    """D-39. JMdict does not record merges, so this is inference."""

    def test_prefers_a_survivor_sharing_the_written_form(self):
        by_seq = {1: [("上手", "じょうず"), ("下手", "へた")]}
        self.assertEqual(
            changes._successor(("上手", "じょうづ"), 1, by_seq), ("上手", "じょうず"))

    def test_falls_back_to_a_shared_reading(self):
        by_seq = {1: [("下手", "へた"), ("旧字", "じょうず")]}
        self.assertEqual(
            changes._successor(("上手", "じょうず"), 1, by_seq), ("旧字", "じょうず"))

    def test_returns_none_when_the_entry_itself_is_gone(self):
        # Must not invent a successor — the card then says "no longer in the
        # dictionary" rather than pointing somewhere wrong (D-40).
        self.assertIsNone(changes._successor(("架空語", "かくうご"), 999, {}))


class TestCoordinateRounding(unittest.TestCase):
    """The only lossy transformation in the whole build (D-56)."""

    def test_rounds_to_one_decimal_and_trims(self):
        self.assertEqual(
            ingest_kanjivg._round_coords("M31.26,25.89c0.36,1.36"),
            "M31.3,25.9c0.4,1.4")

    def test_integers_are_untouched(self):
        self.assertEqual(ingest_kanjivg._round_coords("M31,25c0,1"), "M31,25c0,1")

    def test_trailing_zeros_are_dropped(self):
        self.assertEqual(ingest_kanjivg._round_coords("M31.04,25.00"), "M31,25")


if __name__ == "__main__":
    unittest.main(verbosity=2)
