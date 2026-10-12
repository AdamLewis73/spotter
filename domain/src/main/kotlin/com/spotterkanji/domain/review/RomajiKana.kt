package com.spotterkanji.domain.review

/**
 * Romaji typed into the reading field, turned into hiragana as the learner
 * types (D-108) — "jouzu" becomes じょうず — so no Japanese keyboard is needed.
 * Kana typed on a Japanese keyboard passes through untouched.
 *
 * The rules a learner used to WaniKani or a phone IME expects:
 *
 * - **Doubled consonants** become っ: gakkou → がっこう.
 * - **n** becomes ん before another consonant (kanji → かんじ), and **nn** is ん —
 *   using up only the first n when a vowel or y follows, so konnichiha →
 *   こんにちは rather than こんいちは. `n'` forces ん.
 * - A **lone n at the end** stays a letter while typing, because the next key
 *   may make it な or に; [final] turns it into ん when the answer is submitted.
 * - **-** is the long-vowel mark ー.
 *
 * Anything not romaji or kana is left as it is, so an answer that cannot be
 * read as Japanese is simply wrong rather than silently altered.
 */
fun romajiToKana(input: String, final: Boolean = false): String {
    val s = input.lowercase()
    val out = StringBuilder()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c !in 'a'..'z' && c != '\'' && c != '-') {
            out.append(c); i++; continue
        }
        val next = s.getOrNull(i + 1)
        // Doubled consonant: the first becomes っ.
        if (next == c && c in 'a'..'z' && c !in VOWELS && c != 'n') {
            out.append('っ'); i++; continue
        }
        if (c == 'n' && next == 'n') {
            val after = s.getOrNull(i + 2)
            out.append('ん')
            i += if (after != null && (after in VOWELS || after == 'y')) 1 else 2
            continue
        }
        // n before a consonant other than y.
        if (c == 'n' && next != null && next in 'a'..'z' && next !in VOWELS && next != 'y') {
            out.append('ん'); i++; continue
        }
        var matched = false
        for (length in 4 downTo 1) {
            if (i + length > s.length) continue
            val kana = TABLE[s.substring(i, i + length)] ?: continue
            out.append(kana); i += length; matched = true
            break
        }
        if (matched) continue
        if (c == 'n' && i == s.length - 1 && final) {
            out.append('ん'); i++; continue
        }
        out.append(c); i++
    }
    return out.toString()
}

/** Katakana to hiragana, so either script answers a reading (D-108). Other characters pass through. */
fun katakanaToHiragana(text: String): String = buildString(text.length) {
    for (c in text) append(if (c in 'ァ'..'ヶ') (c - 0x60) else c)
}

private const val VOWELS = "aiueo"

private val TABLE: Map<String, String> = mapOf(
    "a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お",
    "ka" to "か", "ki" to "き", "ku" to "く", "ke" to "け", "ko" to "こ",
    "ga" to "が", "gi" to "ぎ", "gu" to "ぐ", "ge" to "げ", "go" to "ご",
    "sa" to "さ", "si" to "し", "shi" to "し", "su" to "す", "se" to "せ", "so" to "そ",
    "za" to "ざ", "zi" to "じ", "ji" to "じ", "zu" to "ず", "ze" to "ぜ", "zo" to "ぞ",
    "ta" to "た", "ti" to "ち", "chi" to "ち", "tu" to "つ", "tsu" to "つ", "te" to "て", "to" to "と",
    "da" to "だ", "di" to "ぢ", "du" to "づ", "de" to "で", "do" to "ど",
    "na" to "な", "ni" to "に", "nu" to "ぬ", "ne" to "ね", "no" to "の",
    "ha" to "は", "hi" to "ひ", "hu" to "ふ", "fu" to "ふ", "he" to "へ", "ho" to "ほ",
    "ba" to "ば", "bi" to "び", "bu" to "ぶ", "be" to "べ", "bo" to "ぼ",
    "pa" to "ぱ", "pi" to "ぴ", "pu" to "ぷ", "pe" to "ぺ", "po" to "ぽ",
    "ma" to "ま", "mi" to "み", "mu" to "む", "me" to "め", "mo" to "も",
    "ya" to "や", "yu" to "ゆ", "yo" to "よ",
    "ra" to "ら", "ri" to "り", "ru" to "る", "re" to "れ", "ro" to "ろ",
    "wa" to "わ", "wo" to "を", "n'" to "ん",
    "kya" to "きゃ", "kyu" to "きゅ", "kyo" to "きょ", "gya" to "ぎゃ", "gyu" to "ぎゅ", "gyo" to "ぎょ",
    "sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ", "she" to "しぇ",
    "sya" to "しゃ", "syu" to "しゅ", "syo" to "しょ",
    "ja" to "じゃ", "ju" to "じゅ", "jo" to "じょ", "je" to "じぇ",
    "jya" to "じゃ", "jyu" to "じゅ", "jyo" to "じょ",
    "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ", "che" to "ちぇ",
    "tya" to "ちゃ", "tyu" to "ちゅ", "tyo" to "ちょ",
    "nya" to "にゃ", "nyu" to "にゅ", "nyo" to "にょ", "hya" to "ひゃ", "hyu" to "ひゅ", "hyo" to "ひょ",
    "bya" to "びゃ", "byu" to "びゅ", "byo" to "びょ", "pya" to "ぴゃ", "pyu" to "ぴゅ", "pyo" to "ぴょ",
    "mya" to "みゃ", "myu" to "みゅ", "myo" to "みょ", "rya" to "りゃ", "ryu" to "りゅ", "ryo" to "りょ",
    "fa" to "ふぁ", "fi" to "ふぃ", "fe" to "ふぇ", "fo" to "ふぉ",
    "xa" to "ぁ", "xi" to "ぃ", "xu" to "ぅ", "xe" to "ぇ", "xo" to "ぉ",
    "la" to "ぁ", "li" to "ぃ", "lu" to "ぅ", "le" to "ぇ", "lo" to "ぉ",
    "xya" to "ゃ", "xyu" to "ゅ", "xyo" to "ょ", "lya" to "ゃ", "lyu" to "ゅ", "lyo" to "ょ",
    "xtu" to "っ", "ltu" to "っ", "xtsu" to "っ",
    "-" to "ー",
)
