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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotterkanji.app.R
import com.spotterkanji.app.ui.theme.SpotterJapanese
import com.spotterkanji.app.ui.theme.SpotterTheme

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

        // Drag moves this away from the settled stage; letting go snaps it to
        // whichever stage it ended up nearer. Tracked as a fraction so it means
        // the same thing on any screen.
        var dragged by remember { mutableFloatStateOf(0f) }
        val settled = stage.fraction
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
                                ended < SheetStage.Peek.fraction * 0.6f -> onDismiss()
                                ended > MIDPOINT -> onStageChanged(SheetStage.Full)
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

/** Halfway between the two stages, in screen fractions. */
private val MIDPOINT = (SheetStage.Peek.fraction + SheetStage.Full.fraction) / 2f

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
    onFullDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = SpotterTheme.tokens.spaceMd,
                end = SpotterTheme.tokens.spaceMd,
                bottom = SpotterTheme.tokens.spaceMd,
            ),
    ) {
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
            modifier = Modifier.padding(top = SpotterTheme.tokens.spaceXs),
        )

        Row(
            modifier = Modifier.padding(top = SpotterTheme.tokens.spaceMd),
            horizontalArrangement = Arrangement.spacedBy(SpotterTheme.tokens.spaceSm),
        ) {
            // Save is drawn and disabled. It writes user data, gated behind the
            // Phase 6 checkpoint (D-15–D-18, D-43) — getting those wrong deletes
            // real data in production, so the button waits for the schema rather
            // than the schema being improvised for the button.
            Button(
                onClick = {},
                enabled = false,
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
