package com.spotterkanji.app.saved

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotterkanji.app.R
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.domain.user.SavedListId
import com.spotterkanji.domain.user.SavedListSummary

/**
 * The Saved destination: the user's lists, following design artboard **2d**.
 *
 * ### What the design shows that this does not
 *
 * The artboard's list card also carries a due count, a learned count and when
 * the list was last scanned into. None of those can be answered yet — due and
 * learned need `srs_state`, which is Phase 7 (D-79), and "last scan" needs the
 * `scan` table, which arrives with images (D-21).
 *
 * They are **left off rather than shown as zero**. A card reading "0 due · 0
 * learned" is a false statement about the user's progress; a card that does not
 * mention review yet is merely incomplete. This is the same reasoning D-47 uses
 * for keeping a guessed reading off the peek sheet.
 */
@Composable
internal fun SavedScreen(
    state: SavedUiState,
    newListDialogOpen: Boolean,
    onNewListRequested: () -> Unit,
    onNewListDismissed: () -> Unit,
    onNewListConfirmed: (String) -> Unit,
    onRenamed: (SavedListId, String) -> Unit,
    onDeleted: (SavedListId) -> Unit,
    onListOpened: (SavedListId) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val tokens = SpotterTheme.tokens

    if (newListDialogOpen) {
        ListNameDialog(
            title = stringResource(R.string.saved_new_list),
            initial = "",
            confirmLabel = stringResource(R.string.saved_create),
            onDismiss = onNewListDismissed,
            onConfirm = onNewListConfirmed,
        )
    }

    when (state) {
        // Nothing is drawn for the one frame before the query answers, which is
        // deliberate: a spinner that appears and vanishes in 16ms is a flicker,
        // and the alternative — rendering the empty state — would tell a user
        // with fifty lists that they have none.
        SavedUiState.Loading -> Unit

        is SavedUiState.Ready -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(tokens.spaceSm),
        ) {
            item {
                SavedHeader(
                    listCount = state.lists.size,
                    totalWords = state.totalWords,
                )
            }

            if (state.lists.isEmpty()) {
                item { EmptySaved() }
            } else {
                items(state.lists, key = { it.list.id.value }) { summary ->
                    ListCard(
                        summary = summary,
                        onOpen = { onListOpened(summary.list.id) },
                        onRename = { name -> onRenamed(summary.list.id, name) },
                        onDelete = { onDeleted(summary.list.id) },
                        modifier = Modifier.padding(horizontal = tokens.spaceMd),
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = onNewListRequested,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spaceMd, vertical = tokens.spaceSm)
                        .height(48.dp),
                ) { Text(stringResource(R.string.saved_new_list_action)) }
            }
        }
    }
}

@Composable
private fun SavedHeader(listCount: Int, totalWords: Int) {
    val tokens = SpotterTheme.tokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = tokens.spaceMd,
                end = tokens.spaceMd,
                top = tokens.spaceMd,
                bottom = tokens.spaceXs,
            ),
    ) {
        Text(
            text = stringResource(R.string.destination_saved),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Words first, then lists — the words are what the user is keeping; the
        // lists are only how they are arranged.
        Text(
            text = pluralStringResource(R.plurals.saved_word_count, totalWords, totalWords) +
                " · " +
                pluralStringResource(R.plurals.saved_list_count, listCount, listCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = tokens.spaceXs),
        )
    }
}

/**
 * The empty state, which on a new install is the **first** thing the Saved
 * destination ever shows.
 *
 * It says what to do rather than that there is nothing here. A user arrives
 * with no lists because they have not scanned anything yet, so the useful
 * sentence points back at the camera — not at the *New list* button below,
 * which makes an empty list they would then have to fill.
 */
@Composable
private fun EmptySaved() {
    val tokens = SpotterTheme.tokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spaceXl, vertical = tokens.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.saved_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(tokens.spaceSm))
        Text(
            text = stringResource(R.string.saved_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ListCard(
    summary: SavedListSummary,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SpotterTheme.tokens
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    if (renaming) {
        ListNameDialog(
            title = stringResource(R.string.saved_rename_list),
            initial = summary.list.name,
            confirmLabel = stringResource(R.string.saved_rename),
            onDismiss = { renaming = false },
            onConfirm = { name -> renaming = false; onRename(name) },
        )
    }

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(tokens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.list.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.saved_word_count,
                        summary.wordCount,
                        summary.wordCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = tokens.spaceXs),
                )
            }

            // "⋯" rather than an icon, matching the glyph controls the rest of
            // the app uses — the icon set this project has does not carry one.
            TextButton(onClick = { menuOpen = true }) {
                Text(
                    text = "⋯",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.saved_rename_list)) },
                    onClick = { menuOpen = false; renaming = true },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.saved_delete_list)) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

/**
 * One dialog for both creating and renaming, because they differ only in their
 * labels and their starting text.
 */
@Composable
private fun ListNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.saved_list_name)) },
            )
        },
        confirmButton = {
            // Disabled on a blank name: the alternative is a list whose card has
            // nothing on it and which cannot be told apart from its neighbours.
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.saved_cancel))
            }
        },
    )
}
