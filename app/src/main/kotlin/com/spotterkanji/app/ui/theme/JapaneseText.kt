package com.spotterkanji.app.ui.theme

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.spotterkanji.domain.text.japaneseRuns

/**
 * This text with its Japanese parts set in [SpotterJapanese] and the rest left in
 * whatever the style says (D-34, D-98).
 *
 * For text the **user** wrote, where nothing says in advance which part is
 * Japanese: a list called "Food 食べ物" keeps Plex for *Food* and gets Noto Sans
 * JP for 食べ物. Text the app knows is Japanese — a headword, a reading — asks
 * for [SpotterJapanese] directly instead.
 *
 * Without it the Japanese part falls back to a system font. The locale on
 * [SpotterTypography] makes that the Japanese system font rather than the
 * Chinese one, but this puts it in the bundled face, like every other piece of
 * Japanese in the app.
 */
internal fun AnnotatedString.withJapaneseFont(): AnnotatedString = buildAnnotatedString {
    append(this@withJapaneseFont)
    for (run in this@withJapaneseFont.text.japaneseRuns()) {
        addStyle(SpanStyle(fontFamily = SpotterJapanese), run.first, run.last + 1)
    }
}

internal fun String.withJapaneseFont(): AnnotatedString = AnnotatedString(this).withJapaneseFont()

/**
 * [withJapaneseFont] for a text field, so a list name is drawn in the right
 * face while it is being typed. Only the drawing changes — the characters, and
 * so every cursor position, are the same, which is what `Identity` says.
 */
internal object JapaneseFontTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(text.withJapaneseFont(), OffsetMapping.Identity)
}
