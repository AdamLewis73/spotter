package com.spotterkanji.domain.text

/**
 * Whether this **code point** is a kanji.
 *
 * Deliberately not `Character.isIdeographic`, which matches a far wider range
 * than JMdict and KANJIDIC2 actually cover — including characters the dictionary
 * has no entry for, which would produce empty component chips and a kanji screen
 * with nothing on it. (A character in these ranges that the dictionary still
 * does not know is dropped by the lookup, not shown empty.)
 *
 * The ranges are CJK Unified Ideographs, Extension A, the compatibility block,
 * and **everything beyond the basic plane**: Extensions B to H and the
 * compatibility supplement. That last group is why this takes a code point
 * rather than a `Char`. A Kotlin `Char` is a 16-bit unit, and a character past
 * U+FFFF — 𠮟, the standard form of 叱 since 2010, or 𩸽 on an izakaya menu —
 * is stored as *two* of them, neither of which is a kanji on its own. Testing
 * `Char`s silently treated 303 of KANJIDIC2's kanji as not-kanji.
 *
 * Kana are excluded on purpose: 生きる contributes one kanji, not four
 * characters' worth of chips.
 *
 * Lives in `:domain` because two things need it and both would otherwise keep a
 * private copy — the repository, deciding which characters to look up, and the
 * lookup screen, deciding whether a token is a lone kanji and so routes straight
 * to the kanji screen (D-49). Two copies of a character-range check is exactly
 * the kind of duplication that drifts silently.
 */
fun isKanji(codePoint: Int): Boolean =
    codePoint in 0x4E00..0x9FFF ||   // CJK Unified Ideographs
        codePoint in 0x3400..0x4DBF ||   // Extension A
        codePoint in 0xF900..0xFAFF ||   // Compatibility Ideographs
        codePoint in 0x20000..0x323AF    // Extensions B-H, Compatibility Supplement

/** As [isKanji] for a `Char` — true only for kanji in the basic plane. Prefer the code-point overload or [kanjiCharacters]. */
fun Char.isKanji(): Boolean = isKanji(code)

/**
 * The code points of this string, each paired with its start index in the
 * string (in 16-bit units, which is what every offset in this app counts).
 *
 * Written out rather than using `String.codePoints()` because that is a JVM
 * API, and `:domain` stays portable (D-60). A lone surrogate — which only a
 * malformed string contains — is yielded as itself.
 */
fun String.codePointsWithIndex(): Sequence<Pair<Int, Int>> = sequence {
    var i = 0
    while (i < length) {
        val high = this@codePointsWithIndex[i]
        val low = getOrNull(i + 1)
        if (high.isHighSurrogate() && low != null && low.isLowSurrogate()) {
            val codePoint = 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
            yield(codePoint to i)
            i += 2
        } else {
            yield(high.code to i)
            i += 1
        }
    }
}

/**
 * The kanji in [text], in order, each as a whole character — 𠮟 comes back as
 * the one-character string "𠮟", not as two halves. Repeats are kept; callers
 * that want each once call `distinct()`.
 */
fun kanjiCharacters(text: String): List<String> =
    text.codePointsWithIndex()
        .filter { (codePoint, _) -> isKanji(codePoint) }
        .map { (codePoint, at) -> text.substring(at, at + if (codePoint > 0xFFFF) 2 else 1) }
        .toList()

/**
 * Whether this string is exactly one kanji — the test D-49 routes on.
 *
 * `length == 1` is not that test: 𩸽 has length 2.
 */
fun String.isSingleKanji(): Boolean {
    val points = codePointsWithIndex().take(2).toList()
    return points.size == 1 && isKanji(points[0].first)
}

/** Whether this string contains any kana or kanji — Japanese *writing*, as opposed to punctuation. */
fun String.hasKanaOrKanji(): Boolean = codePointsWithIndex().any { (codePoint, _) ->
    isKanji(codePoint) || isKana(codePoint)
}

private fun isKana(codePoint: Int): Boolean =
    codePoint in 0x3040..0x30FF ||   // hiragana and katakana
        codePoint in 0xFF65..0xFF9F      // half-width katakana

/**
 * Whether this code point needs a **Japanese font** to draw.
 *
 * Wider than writing: the ideographic punctuation block (「」、。) and the
 * full-width forms (ＪＲ, ６) are drawn by the CJK font too, and IBM Plex has
 * none of them.
 */
private fun needsJapaneseFont(codePoint: Int): Boolean =
    isKanji(codePoint) ||
        isKana(codePoint) ||
        codePoint in 0x3000..0x303F ||   // ideographic spaces and punctuation
        codePoint in 0xFF00..0xFF64 ||   // full-width forms
        codePoint in 0xFFA0..0xFFEF      // the rest of the half/full-width block

/**
 * Whether this text contains anything a **Japanese font** is needed to draw.
 *
 * Not the same question as [isKanji], and deliberately not built on it alone.
 * [isKanji] answers *which characters get component chips*, so it excludes kana
 * on purpose. This answers *which font does this run need*, and kana need
 * Noto Sans JP exactly as much as kanji do: こんにちは set in IBM Plex falls back
 * to the system font just as silently as 先生 does (D-34).
 */
fun String.containsJapanese(): Boolean = codePointsWithIndex().any { (codePoint, _) ->
    needsJapaneseFont(codePoint)
}

/**
 * The stretches of this text that need the Japanese font, as index ranges.
 *
 * For text the **user** typed, where nothing says in advance which part is
 * Japanese — a list called "Food 食べ物" wants Plex for the first word and Noto
 * Sans JP for the second (D-98). A space between two Japanese runs is folded
 * into one run, so 駅 の看板 is one span rather than three.
 */
fun String.japaneseRuns(): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = -1
    var end = -1
    for ((codePoint, at) in codePointsWithIndex()) {
        val width = if (codePoint > 0xFFFF) 2 else 1
        when {
            needsJapaneseFont(codePoint) -> {
                if (start < 0) start = at
                end = at + width
            }
            // A space inside a Japanese run does not end it.
            codePoint == ' '.code && start >= 0 -> Unit
            start >= 0 -> {
                runs += start until end
                start = -1
            }
        }
    }
    if (start >= 0) runs += start until end
    return runs
}
