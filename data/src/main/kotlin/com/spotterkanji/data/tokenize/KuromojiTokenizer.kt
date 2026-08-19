package com.spotterkanji.data.tokenize

import com.atilika.kuromoji.ipadic.Tokenizer as Kuromoji
import com.spotterkanji.domain.tokenize.Token
import com.spotterkanji.domain.tokenize.Tokenizer

/**
 * Kuromoji-backed segmentation (D-07, D-08).
 *
 * Japanese is written without spaces, so deciding where words begin and end is
 * the technically central problem of this app rather than a detail — 先生と生産
 * is one unbroken run of characters that has to become 先生 / と / 生産.
 *
 * **Construction is expensive.** Kuromoji reads a ~12 MB IPADIC dictionary out
 * of its jar on first use, which takes long enough to drop frames if it happens
 * on the main thread. The instance is created lazily and then reused; callers
 * should keep one around rather than making them per lookup.
 *
 * This lives in `:data` and not `:domain` because Kuromoji is JVM-only and
 * cannot run on iOS. That is precisely why D-08 puts the interface in the domain
 * layer — replacing this with something portable later means writing one class,
 * not rewriting callers.
 */
class KuromojiTokenizer : Tokenizer {

    private val kuromoji: Kuromoji by lazy { Kuromoji() }

    override fun tokenize(text: String): List<Token> {
        if (text.isBlank()) return emptyList()

        return kuromoji.tokenize(text).map { token ->
            val surface = token.surface
            Token(
                text = surface,
                // Kuromoji reports the start as a character offset into the
                // input, which is exactly what the overlay will need later to
                // map a tap back to a word (architecture.md stage 4).
                start = token.position,
                endExclusive = token.position + surface.length,
                baseForm = token.baseForm.takeIf { it.isUsable() && it != surface },
                partOfSpeech = token.partOfSpeechLevel1.takeIf { it.isUsable() },
            )
        }
    }
}

/**
 * Kuromoji writes the literal string `*` for fields it has no value for, rather
 * than null or empty. Passing that through would produce tokens whose base form
 * is "*", and a lookup for "*" quietly returns nothing — a silent wrong answer
 * rather than an error.
 */
private fun String?.isUsable(): Boolean = !isNullOrBlank() && this != "*"
