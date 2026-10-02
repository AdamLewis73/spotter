package com.spotterkanji.app.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.spotterkanji.app.ui.theme.SpotterJapanese
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.domain.tokenize.Token

/**
 * The words of the text, one chip each, in a single row that swipes sideways
 * (D-99).
 *
 * Shown in two places: the **peek sheet** over a scan, where it is a way to step
 * to a neighbouring word without hunting for it on the photograph, and the
 * typed-lookup harness, where it is the only way to pick a word at all. **Not**
 * on the full-height word screen, where it would compete with the word.
 *
 * One row rather than a wrapping block because its height must not depend on
 * the text. The block it replaced grew to 11 rows on a real notice and pushed
 * the word it was meant to lead to out of the sheet.
 *
 * Particles are shown but muted. They have to keep their place — leaving them
 * out would misrepresent how the sentence divides — while "case marking
 * particle" is not what someone photographing a sign wants explained.
 * Punctuation and bare digits are not here at all: they are not tokens (D-99).
 *
 * @param showLabel the "TAP A WORD" caption. The harness shows it; the peek has
 *   no room, and over a photograph the chips need no explaining.
 */
@Composable
internal fun WordStrip(
    tokens: List<Token>,
    selected: Token?,
    onTokenSelected: (Token) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val spacing = SpotterTheme.tokens
    Column(modifier = modifier) {
        if (showLabel) {
            Text(
                text = "TAP A WORD",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = spacing.spaceXs),
            )
        }
        val rowState = rememberLazyListState()
        val selectedIndex = selected?.let { tokens.indexOf(it) } ?: -1
        // Keep the open word in view when it was chosen some other way — a tap
        // on the photograph, or the first content word on arrival — and so
        // sitting off the edge of the row.
        LaunchedEffect(selectedIndex) {
            if (selectedIndex >= 0) rowState.animateScrollToItem(selectedIndex)
        }
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(spacing.spaceSm),
        ) {
            items(tokens) { token ->
                FilterChip(
                    selected = token == selected,
                    onClick = { onTokenSelected(token) },
                    label = {
                        Text(
                            text = token.text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = SpotterJapanese,
                            color = if (token.isContentWord) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                )
            }
        }
    }
}
