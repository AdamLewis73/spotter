package com.spotterkanji.app.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.domain.dictionary.DictionaryEntry
import com.spotterkanji.domain.dictionary.ReadingStatus

/**
 * A reading, and how far it can be trusted — the visible half of V-21.
 *
 * Shared because a reading appears on two screens: as the heading of a word
 * screen section (D-48), and under "As a word" on the kanji screen a lone
 * character routes to (D-49). 上手 marked archaic on one and unmarked on the
 * other would be worse than not marking it at all, since the reader would have
 * no way to know which screen was lying.
 *
 * Two signals rather than one, because a single quiet label is easy to skim
 * past: the reading itself drops to the muted colour, **and** it is named. The
 * sort in `forDisplay()` is the third — a marked reading never leads a word.
 *
 * How strongly to mark it is explicitly a Phase 2 design question (D-53) and
 * this is the restrained answer, chosen so the UI pass has something honest to
 * refine rather than something loud to undo.
 */
@Composable
internal fun ReadingHeading(entry: DictionaryEntry, modifier: Modifier = Modifier) {
    val spacing = SpotterTheme.tokens
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.reading,
            style = MaterialTheme.typography.titleLarge,
            color = if (entry.readingStatus.isMarked) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        entry.readingStatus.label()?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * Plain words, not JMdict's codes.
 *
 * Nothing here says "re_inf" or "ok" — a learner reading a shop sign has no
 * reason to know the dictionary's vocabulary, and the label has to carry its
 * whole meaning in one glance.
 *
 * [ReadingStatus.SEARCH_ONLY] is labelled at all only because of the
 * never-empty rule: those readings are normally hidden, and reach a screen just
 * when they are the sole thing a word has (D-66). あっかんべえ is genuinely what
 * was scanned, so it renders — honestly flagged rather than silently absent.
 */
private fun ReadingStatus.label(): String? = when (this) {
    ReadingStatus.CURRENT -> null
    ReadingStatus.RARE -> "rare"
    ReadingStatus.IRREGULAR -> "irregular"
    ReadingStatus.ARCHAIC -> "archaic"
    ReadingStatus.SEARCH_ONLY -> "non-standard"
}
