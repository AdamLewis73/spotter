package com.spotterkanji.app.word

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.spotterkanji.app.R
import com.spotterkanji.app.ui.theme.SpotterJapanese
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.domain.user.SavedListId

/**
 * The list picker: where a word gets filed (D-88, D-91).
 *
 * **This screen has no artboard.** The wireflow marks it *"NOT DESIGNED — which
 * list, or a new one. Not on the canvas yet."*, so it is built to the project
 * owner's spec rather than to a drawing: a centred overlay, every list listed
 * and scrollable, multi-select, *create a new list* at the top, and an **Add**
 * button that commits.
 *
 * ### Nothing here writes anything
 *
 * Tapping lists selects and deselects them, and creating a list only *stages* a
 * name. The database is untouched until **Add**. That is D-91, and it is what
 * makes the overlay somewhere to think rather than a row of switches with
 * consequences — dismissing costs nothing, including any list you typed.
 *
 * ### It only ever adds
 *
 * Lists that already hold this word are shown saying so and cannot be
 * unselected here. Taking a word out of a list is a destructive, organisational
 * act and it lives on the list screen, where you can see the list you are
 * emptying. One place to remove things is easier to explain than two, and much
 * harder to do by accident.
 */
@Composable
internal fun SaveToListSheet(
    word: String,
    state: PickerState,
    onDismiss: () -> Unit,
    onToggle: (SavedListId) -> Unit,
    onNewListStaged: (String) -> Unit,
    onNewListRemoved: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    if (!state.open) return
    val tokens = SpotterTheme.tokens
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(tokens.spaceMd)) {
                Text(
                    text = stringResource(R.string.picker_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = word,
                    fontFamily = SpotterJapanese,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = tokens.spaceXs),
                )

                Spacer(Modifier.height(tokens.spaceMd))

                // Create sits at the TOP, not the bottom. On a new install the
                // list below is empty, and this is then the only way to save
                // anything at all — it has to be the first thing read, not
                // something found after scrolling past nothing.
                if (creating) {
                    NewListField(
                        value = newName,
                        onValueChange = { newName = it },
                        onCommit = {
                            onNewListStaged(newName)
                            newName = ""
                            creating = false
                        },
                        onCancel = { newName = ""; creating = false },
                    )
                } else {
                    TextButton(
                        onClick = { creating = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.picker_create_list),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (state.noListsYet && !creating) {
                    Text(
                        text = stringResource(R.string.picker_no_lists),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = tokens.spaceMd),
                    )
                }

                // Bounded rather than unbounded: a user with thirty lists should
                // scroll inside the overlay, not push Add off the screen.
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(tokens.spaceXs),
                ) {
                    items(state.stagedNewLists, key = { "new:$it" }) { name ->
                        PickerRow(
                            name = name,
                            checked = true,
                            subtitle = stringResource(R.string.picker_will_be_created),
                            onClick = { onNewListRemoved(name) },
                        )
                    }
                    items(state.lists, key = { it.id.value }) { list ->
                        val holding = list.id in state.alreadyHolding
                        PickerRow(
                            name = list.name,
                            checked = holding || list.id in state.staged,
                            subtitle = if (holding) {
                                stringResource(R.string.picker_already_in)
                            } else {
                                null
                            },
                            // A list that already holds the word is not a way to
                            // remove it. Tapping does nothing rather than
                            // pretending to unfile (D-91).
                            onClick = if (holding) null else ({ onToggle(list.id) }),
                        )
                    }
                }

                Spacer(Modifier.height(tokens.spaceMd))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.saved_cancel))
                    }
                    Spacer(Modifier.size(tokens.spaceSm))
                    Button(
                        onClick = onConfirm,
                        // Disabled rather than a silent no-op: Add with nothing
                        // chosen would dismiss and appear to have saved.
                        enabled = !state.nothingChosen,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text(stringResource(R.string.picker_add)) }
                }
            }
        }
    }
}

@Composable
private fun NewListField(
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    val tokens = SpotterTheme.tokens
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = { Text(stringResource(R.string.saved_list_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = tokens.spaceXs),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.saved_cancel))
            }
            TextButton(onClick = onCommit, enabled = value.isNotBlank()) {
                Text(stringResource(R.string.picker_stage_list))
            }
        }
    }
}

/**
 * One row: a checkbox drawn as the app's own square rather than Material's, so
 * the overlay matches the nav bar instead of importing a second visual
 * language.
 *
 * **Fill carries the state, with no tick inside it.** A "✓" was tried and drawn
 * by whatever font had it, which rendered as a lowercase v — the silent
 * fallback D-34 exists to prevent, in miniature. A filled jade square already
 * means "selected" in the bottom bar, so it means the same here and needs no
 * glyph at all.
 */
@Composable
private fun PickerRow(
    name: String,
    checked: Boolean,
    subtitle: String?,
    onClick: (() -> Unit)?,
) {
    val tokens = SpotterTheme.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = tokens.spaceSm, horizontal = tokens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .then(
                    if (checked) {
                        Modifier.background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(5.dp),
                        )
                    } else {
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            RoundedCornerShape(5.dp),
                        )
                    }
                ),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
