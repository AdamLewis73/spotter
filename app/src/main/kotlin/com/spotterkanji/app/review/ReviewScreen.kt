package com.spotterkanji.app.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotterkanji.app.R
import com.spotterkanji.app.saved.PhotoThumbnail
import com.spotterkanji.app.ui.theme.SpotterJapanese
import com.spotterkanji.app.ui.theme.SpotterMono
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.app.ui.theme.withJapaneseFont
import com.spotterkanji.domain.review.romajiToKana

/**
 * The Review destination (D-36, D-90): how much is due, and the session itself.
 *
 * The card is D-107's: the word, the photo it was saved from, a meaning field
 * and a reading field, one Submit. After Submit each field's border turns green
 * or red, the answer appears beneath, and Submit becomes Next. Answers are
 * checked by the app (D-108); there are no Again/Hard/Good/Easy buttons.
 */
@Composable
internal fun ReviewScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ReviewViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Counts move with the clock; re-read them whenever the tab is shown.
    LaunchedEffect(Unit) { viewModel.refreshHome() }

    // Back leaves the session for the tab's home rather than leaving the app.
    // Anything answered is already recorded (answers are written at Next).
    BackHandler(enabled = state is ReviewUiState.Card, onBack = viewModel::close)

    Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        when (val s = state) {
            ReviewUiState.Loading -> Unit
            is ReviewUiState.Home -> ReviewHome(s, onStart = viewModel::start)
            is ReviewUiState.Card -> ReviewCardScreen(
                state = s,
                onSubmit = viewModel::submit,
                onIWasRight = viewModel::iWasRight,
                onNext = viewModel::next,
                onClose = viewModel::close,
            )
            is ReviewUiState.Done -> ReviewDone(s, onDone = viewModel::close)
        }
    }
}

@Composable
private fun ReviewHome(state: ReviewUiState.Home, onStart: () -> Unit) {
    val tokens = SpotterTheme.tokens
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = tokens.spaceXl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.dueCount > 0 -> {
                Text(
                    text = pluralStringResource(R.plurals.review_due, state.dueCount, state.dueCount),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(tokens.spaceLg))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(stringResource(R.string.review_start)) }
            }
            // Something is scheduled, just not yet. An empty queue and an empty
            // app look alike and mean opposite things, so they are told apart.
            state.nextDueAt != null -> {
                Title(stringResource(R.string.review_caught_up_title))
                Spacer(Modifier.height(tokens.spaceSm))
                Body(stringResource(R.string.review_caught_up_next, untilText(state.nextDueAt)))
            }
            else -> {
                Title(stringResource(R.string.review_nothing_yet_title))
                Spacer(Modifier.height(tokens.spaceSm))
                Body(stringResource(R.string.review_nothing_yet_body))
            }
        }
    }
}

@Composable
private fun ReviewDone(state: ReviewUiState.Done, onDone: () -> Unit) {
    val tokens = SpotterTheme.tokens
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = tokens.spaceXl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Title(stringResource(R.string.review_done_title))
        Spacer(Modifier.height(tokens.spaceSm))
        Body(pluralStringResource(R.plurals.review_done_body, state.answered, state.answered))
        Spacer(Modifier.height(tokens.spaceLg))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) { Text(stringResource(R.string.review_done_back)) }
    }
}

@Composable
private fun ReviewCardScreen(
    state: ReviewUiState.Card,
    onSubmit: (meaning: String, reading: String) -> Unit,
    onIWasRight: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val tokens = SpotterTheme.tokens
    val card = state.card
    val result = state.result
    // Keyed by position, so each card starts with empty fields — including a
    // card that comes back later in the same session.
    var meaning by rememberSaveable(state.position) { mutableStateOf("") }
    var reading by remember(state.position) { mutableStateOf(TextFieldValue("")) }
    val meaningFocus = remember { FocusRequester() }
    val readingFocus = remember { FocusRequester() }
    LaunchedEffect(state.position) { meaningFocus.requestFocus() }

    val submit = {
        // A trailing n becomes ん only now (D-108), so the field shows the kana
        // that was actually checked rather than "せいさn".
        val final = romajiToKana(reading.text, final = true)
        reading = TextFieldValue(final, selection = TextRange(final.length))
        onSubmit(meaning, final)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tokens.spaceMd, vertical = tokens.spaceMd),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceMd),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.review_card_kind).uppercase(),
                fontFamily = SpotterMono,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${state.position} / ${state.total}",
                fontFamily = SpotterMono,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.review_close)) }
        }

        Text(
            text = card.text,
            fontFamily = SpotterJapanese,
            fontWeight = FontWeight.Bold,
            fontSize = 56.sp,
            lineHeight = 64.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // The sign it was saved from (D-107). A word without a photo — found by
        // search, or its photo lost to a restore — simply has none (D-94).
        if (state.thumbnail != null) {
            PhotoThumbnail(
                thumbnail = state.thumbnail,
                size = 160.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        OutlinedTextField(
            value = meaning,
            onValueChange = { if (result == null) meaning = it },
            readOnly = result != null,
            label = { Text(stringResource(R.string.review_meaning_label)) },
            placeholder = { Text(stringResource(R.string.review_meaning_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { readingFocus.requestFocus() }),
            colors = resultColors(result?.meaningRight),
            modifier = Modifier.fillMaxWidth().focusRequester(meaningFocus),
        )

        OutlinedTextField(
            value = reading,
            onValueChange = { typed ->
                if (result != null) return@OutlinedTextField
                // Romaji becomes kana as it is typed (D-108); the cursor follows
                // the end, which is where the learner is typing.
                val converted = romajiToKana(typed.text)
                reading = if (converted == typed.text) typed
                else TextFieldValue(converted, selection = TextRange(converted.length))
            },
            readOnly = result != null,
            label = { Text(stringResource(R.string.review_reading_label)) },
            placeholder = { Text(stringResource(R.string.review_reading_hint)) },
            singleLine = true,
            textStyle = TextStyle(fontFamily = SpotterJapanese, fontSize = 18.sp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = resultColors(result?.readingCorrect),
            supportingText = state.nudge?.let { other ->
                {
                    Text(
                        stringResource(
                            R.string.review_other_word,
                            card.text, other.reading, other.meaning, card.mainSense.firstOrNull().orEmpty(),
                        ).withJapaneseFont(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(readingFocus),
        )

        if (result != null) {
            Answer(state)
            if (result.overridden) {
                Text(
                    text = stringResource(R.string.review_synonym_saved, result.meaningAnswer, card.text)
                        .withJapaneseFont(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(
            onClick = { if (result == null) submit() else onNext() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) { Text(stringResource(if (result == null) R.string.review_submit else R.string.review_next)) }

        // Only for a meaning marked wrong (D-108): the reading is exact, and a
        // reading marked wrong is wrong.
        if (result != null && !result.meaningRight) {
            OutlinedButton(
                onClick = onIWasRight,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text(stringResource(R.string.review_i_was_right)) }
        }
    }
}

/** The answer under the fields after Submit: D-102's layout, kept by D-107. */
@Composable
private fun Answer(state: ReviewUiState.Card) {
    val tokens = SpotterTheme.tokens
    val card = state.card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(tokens.spaceMd),
        verticalArrangement = Arrangement.spacedBy(tokens.spaceXs),
    ) {
        Label(stringResource(R.string.review_answer_meaning))
        Text(
            text = card.mainSense.joinToString("; "),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (card.otherSenses.isNotEmpty()) {
            val shown = card.otherSenses.take(3).joinToString(" · ") { it.take(2).joinToString("; ") }
            val more = card.otherSenses.size - 3
            Text(
                text = stringResource(R.string.review_also, shown) +
                    if (more > 0) "  " + stringResource(R.string.review_more, more) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(tokens.spaceXs))
        Label(stringResource(R.string.review_answer_reading))
        val others = card.acceptedReadings.drop(1)
        Text(
            text = (card.reading + if (others.isEmpty()) "" else "　（${others.joinToString("、")}）")
                .withJapaneseFont(),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Green for right, red for wrong — and a neutral border while asking. The
 * theme's own focus colour is the same jade as "right", so a focused field would
 * look graded before anything was submitted; green has to mean only one thing.
 */
@Composable
private fun resultColors(right: Boolean?) = when (right) {
    null -> OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor = MaterialTheme.colorScheme.onSurface,
        cursorColor = MaterialTheme.colorScheme.onSurface,
    )
    else -> {
        val color = if (right) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = color,
            unfocusedBorderColor = color,
            focusedLabelColor = color,
            unfocusedLabelColor = color,
        )
    }
}

@Composable
private fun Label(text: String) = Text(
    text = text.uppercase(),
    fontFamily = SpotterMono,
    fontSize = 11.sp,
    letterSpacing = 1.5.sp,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun Title(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onSurface,
    textAlign = TextAlign.Center,
)

@Composable
private fun Body(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

/** "in 3 hours", "in 2 days" — coarse on purpose; a due time is not an appointment. */
@Composable
private fun untilText(atMs: Long): String {
    val minutes = ((atMs - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)
    return when {
        minutes < 60 -> "in $minutes min"
        minutes < 48 * 60 -> "in ${minutes / 60} h"
        else -> "in ${minutes / (24 * 60)} days"
    }
}
