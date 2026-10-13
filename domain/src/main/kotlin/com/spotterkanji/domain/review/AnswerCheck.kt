package com.spotterkanji.domain.review

/**
 * Whether a typed meaning is right (D-108). Offline and rule-based: no network
 * and no model (D-46).
 *
 * Accepted if it matches a gloss of **any** listed sense — "lenient" for 甘い is
 * real knowledge though it is not the main sense — or one of the learner's own
 * [synonyms], saved by "I was right". Matching ignores case, a leading "to",
 * "a", "an" or "the", punctuation, and bracketed parts; each gloss is also tried
 * with its brackets kept as words, so "good" and "good at" both match
 * "good (at)". JMdict already lists most synonyms side by side: 上手's first
 * sense has "skilled" beside "skillful".
 *
 * Small typos pass in longer words: none up to 3 letters, one up to 6, two
 * beyond — "skilful" matches "skillful", "cat" does not match "cut".
 */
fun meaningMatches(typed: String, senses: List<List<String>>, synonyms: List<String> = emptyList()): Boolean {
    val answer = normaliseMeaning(typed)
    if (answer.isEmpty()) return false
    val candidates = senses.flatten().flatMap { gloss ->
        listOf(normaliseMeaning(gloss), normaliseMeaning(gloss.replace("(", " ").replace(")", " ")))
    } + synonyms.map(::normaliseMeaning)
    return candidates.any { candidate ->
        candidate.isNotEmpty() &&
            (candidate == answer || editDistance(candidate, answer) <= typoAllowance(candidate.length))
    }
}

/** What the reading field's answer was, after conversion (D-108). */
sealed interface ReadingVerdict {
    /** A reading of the saved word — じょうず, or じょうて, which JMdict records as the same word. */
    data class Right(val kana: String) : ReadingVerdict

    /**
     * A reading of a **different** word written the same way — 上手 read うわて,
     * "upper part". Not graded: the card shows only 上手, so the learner could not
     * have known which word was meant. They are told, and try again.
     */
    data class OtherWord(val word: OtherReading) : ReadingVerdict

    data class Wrong(val kana: String) : ReadingVerdict

    /** Nothing typed. */
    data object Empty : ReadingVerdict
}

/**
 * Checks a typed reading against the saved word's readings and the other words
 * that share its written form. [typed] may be romaji, hiragana or katakana.
 */
fun checkReading(typed: String, accepted: List<String>, otherWords: List<OtherReading>): ReadingVerdict {
    val kana = katakanaToHiragana(romajiToKana(typed.trim(), final = true))
    if (kana.isEmpty()) return ReadingVerdict.Empty
    if (accepted.any { katakanaToHiragana(it) == kana }) return ReadingVerdict.Right(kana)
    otherWords.firstOrNull { katakanaToHiragana(it.reading) == kana }
        ?.let { return ReadingVerdict.OtherWord(it) }
    return ReadingVerdict.Wrong(kana)
}

/** A reading that belongs to another word with the same written form, and that word's meaning. */
data class OtherReading(val reading: String, val meaning: String)

internal fun normaliseMeaning(text: String): String =
    text.lowercase()
        .replace(BRACKETED, " ")
        .replace(NOT_WORD, " ")
        .replace(SPACES, " ")
        .trim()
        .removePrefixWord("to")
        .removePrefixWord("a")
        .removePrefixWord("an")
        .removePrefixWord("the")

private fun String.removePrefixWord(word: String): String =
    if (startsWith("$word ")) substring(word.length + 1).trim() else this

private fun typoAllowance(length: Int): Int = when {
    length <= 3 -> 0
    length <= 6 -> 1
    else -> 2
}

/** Levenshtein distance, stopping early when the lengths alone rule a match out. */
internal fun editDistance(a: String, b: String): Int {
    if (kotlin.math.abs(a.length - b.length) > 2) return Int.MAX_VALUE
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        for (j in 1..b.length) {
            val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
        }
        previous = current
    }
    return previous[b.length]
}

private val BRACKETED = Regex("\\([^)]*\\)")
private val NOT_WORD = Regex("[^a-z0-9' -]")
private val SPACES = Regex("\\s+")
