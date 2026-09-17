package com.spotterkanji.app.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotterkanji.app.R
import com.spotterkanji.app.ui.theme.SpotterJapanese
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.app.word.KanjiScreen
import com.spotterkanji.app.word.SaveToListSheet
import com.spotterkanji.app.word.WordLookupViewModel
import com.spotterkanji.app.word.WordScreen
import com.spotterkanji.domain.dictionary.WordHit
import com.spotterkanji.domain.user.SavedListId

/**
 * Adding a word by typing it, reached from inside a list (D-86, D-96).
 *
 * Three levels, unwound by back one at a time: results → word screen → kanji
 * screen. The word and kanji screens are the same ones a scan opens, driven by
 * the same `WordLookupViewModel`, so alternates (D-70), the lone-kanji rule
 * (D-49) and Save all behave exactly as they do over a photograph.
 *
 * The word screen **replaces** the results rather than sliding over them, the
 * way the kanji screen replaces the word screen (D-32). The results are not lost
 * — they live in their own ViewModel — so back returns to them as they were.
 *
 * @param listId the list the user came from. The picker opens with it ticked.
 */
@Composable
internal fun SearchRoute(
    listId: SavedListId?,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val search: WordSearchViewModel = viewModel()
    val searchState by search.state.collectAsStateWithLifecycle()

    val words: WordLookupViewModel = viewModel()
    val wordState by words.state.collectAsStateWithLifecycle()
    val picker by words.picker.collectAsStateWithLifecycle()
    LaunchedEffect(listId) { words.onDefaultList(listId) }

    // The keyboard comes up on arrival only. Coming back from a word re-enters
    // the results screen, and raising it again then would make the next back
    // press close the keyboard instead of leaving — an extra press for nothing.
    var focusedOnce by rememberSaveable { mutableStateOf(false) }

    val selected = wordState.selected
    val openKanji = wordState.openKanji

    // Back from the results leaves through the NavHost, which needs no help.
    // Inside a word it has to unwind by hand: these are states of one screen,
    // not destinations the back stack knows about.
    BackHandler(enabled = selected != null) {
        if (openKanji != null) words.onKanjiClosed() else words.onSelectionCleared()
    }

    val inset = Modifier.fillMaxSize().padding(contentPadding)
    when {
        selected == null -> SearchScreen(
            state = searchState,
            onQueryChanged = search::onQueryChanged,
            onHitChosen = { hit -> words.onWordChosen(hit.text) },
            onBack = onBack,
            autoFocus = !focusedOnce,
            onAutoFocused = { focusedOnce = true },
            modifier = inset,
        )

        // A lone kanji lands here directly (D-49); its word screen waits behind.
        openKanji != null -> KanjiScreen(
            detail = openKanji,
            onBack = words::onKanjiClosed,
            onSave = words::onSaveKanjiRequested,
            modifier = inset,
        )

        else -> WordScreen(
            state = wordState,
            onQueryChanged = {},
            onTokenSelected = words::onTokenSelected,
            onKanjiSelected = words::onKanjiSelected,
            onAlternateSelected = words::onAlternateSelected,
            onSave = words::onSaveRequested,
            // Back to the results, not to an empty search.
            onDismiss = words::onSelectionCleared,
            standalone = false,
            modifier = inset,
        )
    }

    SaveToListSheet(
        word = picker.target?.key?.text.orEmpty(),
        state = picker,
        onDismiss = words::onPickerDismissed,
        onToggle = words::onPickerListToggled,
        onNewListStaged = words::onPickerNewListStaged,
        onNewListRemoved = words::onPickerNewListRemoved,
        onConfirm = words::onPickerConfirmed,
    )
}

/**
 * A text field, and results beneath it as the user types.
 *
 * **No artboard exists for this screen.** It follows the project owner's
 * description: field at the top, the area below blank until something is
 * typed. The rows match a list's own rows — reading, word, gloss — so a result
 * looks like what it becomes once it is added.
 */
@Composable
internal fun SearchScreen(
    state: WordSearchState,
    onQueryChanged: (String) -> Unit,
    onHitChosen: (WordHit) -> Unit,
    onBack: () -> Unit,
    autoFocus: Boolean,
    onAutoFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SpotterTheme.tokens
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // The user came here to type, so the keyboard is already up.
    LaunchedEffect(Unit) {
        if (autoFocus) {
            focus.requestFocus()
            onAutoFocused()
        }
    }

    Column(modifier = modifier.padding(horizontal = tokens.spaceMd)) {
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.padding(top = tokens.spaceSm),
        ) {
            Text(
                text = "‹  " + stringResource(R.string.search_back),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.search_title),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_placeholder),
                    fontFamily = SpotterJapanese,
                )
            },
            // What the user types is Japanese, and IBM Plex has no CJK: without
            // this the field falls back to the system font (D-34).
            textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = SpotterJapanese),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // Results are live, so Search has nothing left to do but get the
            // keyboard out of their way.
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = tokens.spaceMd)
                .focusRequester(focus),
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.hits.isNotEmpty() -> LazyColumn {
                    if (state.foundInText) {
                        item {
                            Text(
                                text = stringResource(R.string.search_found_inside),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = tokens.spaceSm),
                            )
                        }
                    }
                    items(state.hits, key = { it.text }) { hit ->
                        HitRow(hit, onClick = { onHitChosen(hit) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }

                state.hasSearched -> Text(
                    text = stringResource(R.string.search_nothing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = tokens.spaceMd),
                )

                // Blank until something is typed, as specified.
                else -> Unit
            }
        }
    }
}

@Composable
private fun HitRow(hit: WordHit, onClick: () -> Unit) {
    val tokens = SpotterTheme.tokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = tokens.spaceMd),
    ) {
        // Omitted for a kana-only word, where the reading is the word itself.
        if (hit.reading != hit.text) {
            Text(
                text = hit.reading,
                fontFamily = SpotterJapanese,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = hit.text,
            fontFamily = SpotterJapanese,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (hit.gloss.isNotEmpty()) {
            Text(
                text = hit.gloss,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = tokens.spaceXs),
            )
        }
    }
}
