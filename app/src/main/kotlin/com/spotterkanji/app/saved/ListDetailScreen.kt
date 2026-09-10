package com.spotterkanji.app.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotterkanji.app.R
import com.spotterkanji.app.ui.theme.SpotterJapanese
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.domain.user.StudyItem

/**
 * One list's words, following design artboard **2e**.
 *
 * Read-only for now. The artboard also offers *swipe a word left to remove it*
 * and shows the scans a word was found in, and neither is here yet: removal is
 * the only way a word becomes unfiled (D-89) and is worth building beside the
 * picker that files them, and the scans need the `scan` table (D-21).
 *
 * Note what the rows show — reading above, word, then glosses. That is the
 * saved [StudyItem]'s own `snapshot_gloss` (D-43), **not** a fresh dictionary
 * lookup. A live lookup here would be a query per row on a scrolling list; the
 * snapshot is what it was when the user chose to keep it, which is also what
 * makes an unresolvable word still render (D-40).
 */
@Composable
internal fun ListDetailScreen(
    listName: String,
    words: List<StudyItem>,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val tokens = SpotterTheme.tokens

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spaceMd, vertical = tokens.spaceSm),
            ) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        text = "‹  " + stringResource(R.string.destination_saved),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = listName,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.saved_word_count,
                        words.size,
                        words.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = tokens.spaceXs),
                )
            }
        }

        if (words.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spaceXl, vertical = tokens.spaceXl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.list_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(tokens.spaceSm))
                    Text(
                        text = stringResource(R.string.list_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(words, key = { it.id.value }) { word ->
                WordRow(word)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun WordRow(word: StudyItem) {
    val tokens = SpotterTheme.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spaceMd, vertical = tokens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // The reading sits ABOVE the word here, unlike the peek sheet which
            // shows none at all (D-47). The difference is that this word was
            // saved with a reading the user chose to keep, so there is nothing
            // to guess.
            if (word.key.reading.isNotEmpty()) {
                Text(
                    text = word.key.reading,
                    fontFamily = SpotterJapanese,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = word.key.text,
                fontFamily = SpotterJapanese,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = word.snapshotGloss,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = tokens.spaceXs),
            )
        }
    }
}
