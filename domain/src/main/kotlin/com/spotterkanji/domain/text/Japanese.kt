package com.spotterkanji.domain.text

/**
 * Whether this character is a kanji.
 *
 * Deliberately not `Character.isIdeographic`, which matches a far wider range
 * than JMdict and KANJIDIC2 actually cover — including characters the dictionary
 * has no entry for, which would produce empty component chips and a kanji screen
 * with nothing on it.
 *
 * The ranges are CJK Unified Ideographs, Extension A, and the compatibility
 * block. Kana are excluded on purpose: 生きる contributes one kanji, not four
 * characters' worth of chips.
 *
 * Lives in `:domain` because two things need it and both would otherwise keep a
 * private copy — the repository, deciding which characters to look up, and the
 * lookup screen, deciding whether a token is a lone kanji and so routes straight
 * to the kanji screen (D-49). Two copies of a character-range check is exactly
 * the kind of duplication that drifts silently.
 */
fun Char.isKanji(): Boolean =
    this in '一'..'鿿' ||
        this in '㐀'..'䶿' ||
        this in '豈'..'﫿'

/**
 * Whether this text contains anything a **Japanese font** is needed to draw.
 *
 * Not the same question as [isKanji], and deliberately not built on it alone.
 * [isKanji] answers *which characters get component chips*, so it excludes kana
 * on purpose. This answers *which font does this run need*, and kana need
 * Noto Sans JP exactly as much as kanji do: こんにちは set in IBM Plex falls back
 * to the system font just as silently as 先生 does (D-34).
 *
 * Covers kanji, hiragana, katakana, the ideographic punctuation block (「」、。)
 * and half-width katakana. Used where an English sentence has a Japanese word
 * substituted into it, so only the Japanese run is switched to the Japanese font.
 */
fun String.containsJapanese(): Boolean = any { c ->
    c.isKanji() ||
        c in '\u3040'..'\u30FF' || // hiragana and katakana
        c in '\u3000'..'\u303F' || // ideographic spaces and punctuation
        c in '\uFF65'..'\uFF9F'    // half-width katakana
}
