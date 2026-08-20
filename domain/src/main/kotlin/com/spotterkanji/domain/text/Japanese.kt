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
