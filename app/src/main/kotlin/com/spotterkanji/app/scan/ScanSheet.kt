package com.spotterkanji.app.scan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotterkanji.app.R
import com.spotterkanji.app.ui.theme.SpotterJapanese
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.app.word.WordStrip
import com.spotterkanji.domain.tokenize.Token

/**
 * How far up the screen the sheet reaches.
 *
 * D-30 makes the peek sheet and the word screen **one expanding component**
 * rather than two destinations, so this is a height rather than a navigation
 * state. The kanji screen is a third stage only in the sense that it swaps its
 * *contents* in place at full height (D-32) — it is not taller.
 */
internal enum class SheetStage(val fraction: Float) {
    /** The word and its meanings, over a photograph that stays visible. */
    Peek(0.30f),

    /** The word screen, or the kanji screen swapped in place inside it. */
    Full(0.92f),
}

/**
 * The expanding sheet over a frozen frame — artboard 1a expanding into 2a.
 *
 * Deliberately not `ModalBottomSheet`: it has no back stack, and it dims what is
 * behind it, which would fight the overlay's own scrim. D-32 accepts the cost of
 * custom plumbing for exactly this, and what that cost actually buys is that the
 * photograph stays visible and tappable behind the peek.
 */
@Composable
internal fun ScanSheet(
    stage: SheetStage,
    onStageChanged: (SheetStage) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (SheetStage) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullHeight = maxHeight

        // The peek is 30% of the height, but never less than its contents
        // need. On a short phone 30% is about 160 dp, and the word, its
        // meaning, the word strip and the two buttons do not fit in that — the
        // bottom of a sheet is clipped, and the bottom is where Save is.
        val peekFraction = maxOf(SheetStage.Peek.fraction, PEEK_MIN_HEIGHT / fullHeight)
            .coerceAtMost(SheetStage.Full.fraction)
        fun SheetStage.resolvedFraction() = if (this == SheetStage.Peek) peekFraction else fraction
        val midpoint = (peekFraction + SheetStage.Full.fraction) / 2f

        // Drag moves this away from the settled stage; letting go snaps it to
        // whichever stage it ended up nearer. Tracked as a fraction so it means
        // the same thing on any screen.
        var dragged by remember { mutableFloatStateOf(0f) }
        val settled = stage.resolvedFraction()
        val target = (settled + dragged).coerceIn(0f, SheetStage.Full.fraction)
        val fraction by animateFloatAsState(targetValue = target, label = "sheet")

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(fullHeight * fraction),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                DragHandle(
                    modifier = Modifier.draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            // Upward drag is negative in screen coordinates and
                            // makes the sheet taller, hence the sign flip.
                            dragged -= delta / fullHeight.value
                        },
                        onDragStopped = {
                            val ended = settled + dragged
                            dragged = 0f
                            when {
                                // Dragged below the peek height: let it go.
                                ended < peekFraction * 0.6f -> onDismiss()
                                ended > midpoint -> onStageChanged(SheetStage.Full)
                                else -> onStageChanged(SheetStage.Peek)
                            }
                        },
                    ),
                )
                content(stage)
            }
        }
    }
}

/**
 * The least height the peek may have: handle, word strip, word, two lines of
 * meaning and the buttons, with their spacing. Measured from the layout below,
 * not from a drawing — change one and change the other.
 */
private val PEEK_MIN_HEIGHT = 256.dp

@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // The grabbable area is the whole strip, not the 34dp bar drawn in
            // it. A 4dp-tall touch target would be a design that only works for
            // whoever tested it.
            .height(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                    RoundedCornerShape(2.dp),
                ),
        )
    }
}

/**
 * The peek contents: the word, what it means, and the two things to do next
 * (D-30, D-31).
 *
 * **No reading** (D-47) — the app would be guessing which one applies, and a
 * learner who already knew it would not have scanned the word.
 */
@Composable
internal fun PeekContents(
    word: String,
    glosses: String?,
    loading: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
    onFullDetails: () -> Unit,
    tokens: List<Token>,
    selected: Token?,
    onTokenSelected: (Token) -> Unit,
    modifier: Modifier = Modifier,
) {
    // No navigation-bar padding: the sheet sits above the app's own bar, which
    // already clears the system one.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SpotterTheme.tokens.spaceMd,
                end = SpotterTheme.tokens.spaceMd,
                bottom = SpotterTheme.tokens.spaceMd,
            ),
    ) {
        // The words of the sign, one swipeable row, so the next word is a tap
        // away without finding it on the photograph (D-99). Only here: the
        // full-height word screen drops it, and Back to the peek brings it back.
        if (tokens.size > 1) {
            WordStrip(
                tokens = tokens,
                selected = selected,
                onTokenSelected = onTokenSelected,
                showLabel = false,
                modifier = Modifier.padding(bottom = SpotterTheme.tokens.spaceXs),
            )
        }

        Text(
            text = word,
            fontFamily = SpotterJapanese,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = when {
                loading -> stringResource(R.string.scan_peek_looking_up)
                glosses != null -> glosses
                else -> stringResource(R.string.scan_peek_not_found)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Two lines at most, so the buttons below always fit the peek's
            // height (PEEK_MIN_HEIGHT). The rest is one tap away, on Full details.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = SpotterTheme.tokens.spaceXs),
        )

        Row(
            modifier = Modifier.padding(top = SpotterTheme.tokens.spaceMd),
            horizontalArrangement = Arrangement.spacedBy(SpotterTheme.tokens.spaceSm),
        ) {
            // Live as of Phase 6, once the schema its checkpoints gate was
            // settled (D-79, D-80) rather than improvised for the button.
            //
            // It saves the top-ranked entry — the one whose glosses are printed
            // above it (D-81). The peek shows no reading (D-47) but identity
            // needs one (D-12), so the button saves what the user was actually
            // looking at rather than asking them to choose a reading they were
            // deliberately not shown.
            //
            // Always "add", never a toggle (D-91). A word can be filed in some
            // lists and not others (D-89), so there is no single saved/unsaved
            // truth for this button to report — it opens the picker, and the
            // picker says which lists already hold the word.
            //
            // Disabled only when there is nothing to save — while the lookup is
            // still running, or when it found nothing. Saving a word with no
            // gloss and no reading creates exactly the unresolvable row D-40
            // then has to render forever.
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Text(stringResource(R.string.scan_peek_save)) }

            OutlinedButton(
                onClick = onFullDetails,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text(stringResource(R.string.scan_peek_full_details)) }
        }
    }
}
