package com.spotterkanji.app.saved

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.spotterkanji.domain.text.containsJapanese
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotterkanji.app.R
import com.spotterkanji.app.ui.theme.SpotterJapanese
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.domain.user.StudyItem
import com.spotterkanji.domain.user.StudyItemId
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One list's words, following design artboard **2e**.
 *
 * ### Removing a word (D-93)
 *
 * **Hold** a row and it slides left to reveal a red *Remove* panel, as the
 * artboard draws it. Tapping anywhere else — another row, the back gesture, or
 * starting to scroll — hides it again. Tapping *Remove* asks *Are you sure?*;
 * confirming takes the word out of this list and shows a message for three
 * seconds with **Undo**.
 *
 * A long press rather than a swipe, deliberately. A swipe is one fast gesture
 * that a scroll can trigger by accident; holding is something you mean. And the
 * artboard's panel sits *behind* the row, which a hold reveals just as well as a
 * swipe would — only the gesture that uncovers it changes.
 *
 * Both a confirmation *and* an undo is more than most apps use. It is justified
 * here because this is the only destructive action on the study side of the app,
 * and the two catch different mistakes: the confirmation catches a tap you did
 * not mean, and the undo catches a decision you regret a second later.
 *
 * ### What removal does not do
 *
 * It never resets anything. Removing a word from its last list hides it until it
 * is filed again, with its review history and photos intact (D-89, D-83). The
 * message is the same either way — a word leaving its last list is not worded
 * differently, which is the project owner's call and may be revisited once it
 * has been used.
 *
 * Note what the rows show — reading above, word, then glosses. That is the saved
 * [StudyItem]'s own `snapshot_gloss` (D-43), **not** a fresh dictionary lookup:
 * a live lookup would be a query per row on a scrolling list, and the snapshot
 * is also what makes an unresolvable word still render (D-40).
 */
@Composable
internal fun ListDetailScreen(
    listName: String,
    words: List<StudyItem>,
    onBack: () -> Unit,
    onRemove: (StudyItemId) -> Unit,
    onUndoRemove: (StudyItemId) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val tokens = SpotterTheme.tokens
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Which row has its Remove panel showing, if any. One at a time: revealing a
    // second hides the first, so there is never more than one armed.
    var revealed by remember { mutableStateOf<StudyItemId?>(null) }
    // The word awaiting confirmation. Separate from `revealed` so the dialog
    // survives the panel closing behind it.
    var confirming by remember { mutableStateOf<StudyItem?>(null) }

    // "Tapping elsewhere hides it" also means the back gesture, and it means
    // scrolling: a revealed panel scrolled off-screen and back is a surprise.
    BackHandler(enabled = revealed != null) { revealed = null }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) revealed = null
    }

    val removedMessage = stringResource(R.string.list_removed_from)
    val undoLabel = stringResource(R.string.list_undo)

    confirming?.let { word ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.list_remove_title)) },
            text = {
                Text(
                    withJapaneseRuns(
                        stringResource(R.string.list_remove_body, word.key.text, listName),
                        word.key.text,
                        listName,
                    ),
                )
            },
            confirmButton = {
                // Filled, not red text. #D33A3C carries white at 4.73:1 but as
                // text on the dark dialog it is about 3.6:1 — the colour is
                // tuned to be a fill, so it is used as one.
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    onClick = {
                    confirming = null
                    revealed = null
                    onRemove(word.id)
                    scope.launch {
                        // Exactly three seconds, as specified. Material's own
                        // "short" duration is four, so the timing is ours: an
                        // indefinite snackbar that this coroutine cancels, and
                        // cancelling one dismisses it.
                        val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
                            snackbar.showSnackbar(
                                RemovedVisuals(
                                    message = removedMessage.format(word.key.text, listName),
                                    japaneseRuns = listOf(word.key.text, listName),
                                    actionLabel = undoLabel,
                                )
                            )
                        }
                        if (result == SnackbarResult.ActionPerformed) onUndoRemove(word.id)
                    }
                },
                ) { Text(stringResource(R.string.list_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text(stringResource(R.string.saved_cancel))
                }
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
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
                item { EmptyList() }
            } else {
                items(words, key = { it.id.value }) { word ->
                    RemovableWordRow(
                        word = word,
                        revealed = revealed == word.id,
                        onHold = { revealed = word.id },
                        onTap = { revealed = null },
                        onRemoveTapped = { confirming = word },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            // Above the app's bottom bar, not behind it.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding()),
        ) { data ->
            // A custom body rather than the default, for one reason: the default
            // takes a plain String, and this message has a Japanese word in the
            // middle of an English sentence. Set in IBM Plex, that word falls back
            // silently to the system font (D-34). The runs are carried alongside
            // the text so they can be switched to SpotterJapanese.
            val visuals = data.visuals as? RemovedVisuals
            Snackbar(
                action = {
                    TextButton(onClick = { data.performAction() }) {
                        Text(
                            text = data.visuals.actionLabel.orEmpty(),
                            color = MaterialTheme.colorScheme.inversePrimary,
                        )
                    }
                },
            ) {
                Text(
                    if (visuals != null) {
                        withJapaneseRuns(visuals.message, *visuals.japaneseRuns.toTypedArray())
                    } else {
                        AnnotatedString(data.visuals.message)
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyList() {
    val tokens = SpotterTheme.tokens
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

/**
 * A word row with the artboard's Remove panel.
 *
 * **The row does not move; the panel slides in over its right edge.** An
 * earlier version slid the whole row left by the panel's width, which pushed
 * the word and its reading straight off the screen and left only the tail of
 * the gloss showing — so the one thing the user needs to see before removing
 * something, *which word it is*, was the part that disappeared. The artboard
 * keeps the content anchored and overlays the panel, and so does this.
 *
 * The Box is sized to its row's **intrinsic** height so the panel can fill it.
 * Without that, `fillMaxHeight` is measured against a lazy list's unbounded
 * height, which Compose resolves by ignoring the fill — and the red panel would
 * shrink to its own two lines of text instead of spanning the row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemovableWordRow(
    word: StudyItem,
    revealed: Boolean,
    onHold: () -> Unit,
    onTap: () -> Unit,
    onRemoveTapped: () -> Unit,
) {
    val tokens = SpotterTheme.tokens
    val haptics = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = {
                        // A hold is felt as well as seen, so the user knows it
                        // registered before they lift their finger.
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onHold()
                    },
                )
                // Clears the panel when it is showing, so the end of a long gloss
                // wraps before the panel rather than running underneath it.
                .padding(
                    start = tokens.spaceMd,
                    end = if (revealed) PANEL_WIDTH + tokens.spaceSm else tokens.spaceMd,
                    top = tokens.spaceMd,
                    bottom = tokens.spaceMd,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spaceMd),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // The reading sits ABOVE the word here, unlike the peek sheet
                // which shows none at all (D-47). This word was saved with a
                // reading the user chose to keep, so there is nothing to guess.
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

        // Drawn AFTER the row, so it sits on top of it.
        AnimatedVisibility(
            visible = revealed,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .width(PANEL_WIDTH)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.error)
                    .clickable(onClick = onRemoveTapped),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "−",
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onError,
                )
                Text(
                    text = stringResource(R.string.list_remove_panel),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onError,
                )
            }
        }
    }
}

private val PANEL_WIDTH = 92.dp
private const val UNDO_WINDOW_MS = 3_000L

/**
 * The removal message, carrying which parts of it are Japanese.
 *
 * [message] stays a complete plain sentence so accessibility services read it
 * whole; [japaneseRuns] only tells the renderer where to switch fonts.
 */
private class RemovedVisuals(
    override val message: String,
    val japaneseRuns: List<String>,
    override val actionLabel: String,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
    // Indefinite because the three-second window is enforced by the caller: an
    // indefinite snackbar that the showing coroutine cancels (Material's own
    // "short" is four seconds).
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite
}

/**
 * [full] with each of [runs] that contains Japanese set in SpotterJapanese.
 *
 * For sentences where a Japanese word is substituted into English — the rest
 * stays in IBM Plex, the house face, and only the run that needs Noto Sans JP
 * gets it. A run with no Japanese is left alone, so a list called "Street Signs"
 * does not change typeface just because it sits next to 先生; one called 駅 does.
 */
private fun withJapaneseRuns(full: String, vararg runs: String): AnnotatedString =
    buildAnnotatedString {
        append(full)
        runs.filter { it.isNotEmpty() && it.containsJapanese() }.forEach { run ->
            var from = 0
            while (true) {
                val at = full.indexOf(run, from)
                if (at < 0) break
                addStyle(SpanStyle(fontFamily = SpotterJapanese), at, at + run.length)
                from = at + run.length
            }
        }
    }
