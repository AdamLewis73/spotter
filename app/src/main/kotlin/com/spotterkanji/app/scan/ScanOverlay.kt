package com.spotterkanji.app.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotterkanji.app.R
import com.spotterkanji.app.ui.theme.SpotterJapanese
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.domain.scan.CharacterPlacement
import com.spotterkanji.domain.scan.ScanLayout
import com.spotterkanji.domain.scan.ScanProjection
import com.spotterkanji.domain.scan.TextBox
import com.spotterkanji.domain.scan.WritingDirection
import kotlin.math.roundToInt

/**
 * The tappable overlay — artboard **1a**, "dim frame, bright text, solid
 * selection" (D-33).
 *
 * **The photograph's own pixels are what stays bright** (D-78). The scrim covers
 * the whole frame and the recognized text is painted back over it, unmodified,
 * at full brightness. Nothing is retyped.
 *
 * The alternative — redrawing each character as type into its measured
 * rectangle — was built first and rejected on the evidence (D-77, superseded).
 * It ghosts, because the drawn glyphs never sit exactly on the photographed
 * ones; and worse, it renders a misrecognition as clean, authoritative type. On
 * a real notice it confidently displayed 合風 for 台風 and 休館 as 体館. Painting
 * the photograph back cannot lie about what the sign says, because it *is* the
 * sign — a misread then costs a wrong lookup rather than a wrong sign.
 *
 * 縦書き needs no special case either way: a vertical character's box is simply
 * lower than the one before it.
 */
@Composable
internal fun ScanOverlay(
    frame: ImageBitmap,
    layout: ScanLayout,
    selection: IntRange?,
    onOffsetTapped: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    val projection = remember(frame, viewSize) {
        ScanProjection.crop(
            imageWidth = frame.width,
            imageHeight = frame.height,
            viewWidth = viewSize.width.toDouble(),
            viewHeight = viewSize.height.toDouble(),
        )
    }

    // Contiguous characters are repainted as one patch rather than one per
    // glyph, so the seams between them do not show.
    val runs = remember(layout) { layout.runs() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it }
            .pointerInput(projection, layout) {
                detectTapGestures { position ->
                    // A tap on bare image reports null rather than being
                    // swallowed: that is what dismisses the peek sheet.
                    onOffsetTapped(
                        layout.offsetAt(
                            x = projection.toImageX(position.x).roundToInt(),
                            y = projection.toImageY(position.y).roundToInt(),
                        ),
                    )
                }
            },
    ) {
        drawRect(color = SCRIM)

        for (run in runs) {
            val selected = selection != null && run.offsets.first in selection
            // ML Kit's boxes sit tight against the ink and clip the odd stroke,
            // so each patch is repainted very slightly proud of its box.
            val bleed = (minOf(run.box.width, run.box.height) * PATCH_BLEED).toInt()
            val source = run.box.expandedBy(bleed, frame.width, frame.height)
            val target = projection.project(source)

            if (selected) {
                // The artboard's solid selection, showing as a band around the
                // word. It cannot recolour the photograph's glyphs, so it frames
                // them instead — which reads the same and stays honest.
                val pad = (target.height.coerceAtMost(target.width) * 0.12).toFloat()
                drawRoundRect(
                    color = colors.primary,
                    topLeft = Offset(target.left.toFloat() - pad, target.top.toFloat() - pad),
                    size = Size(
                        width = target.width.toFloat() + pad * 2,
                        height = target.height.toFloat() + pad * 2,
                    ),
                    cornerRadius = CornerRadius(pad),
                )
            }

            drawImage(
                image = frame,
                srcOffset = IntOffset(source.left, source.top),
                srcSize = IntSize(
                    source.width.coerceAtLeast(1),
                    source.height.coerceAtLeast(1),
                ),
                dstOffset = IntOffset(target.left.toInt(), target.top.toInt()),
                dstSize = IntSize(
                    target.width.toInt().coerceAtLeast(1),
                    target.height.toInt().coerceAtLeast(1),
                ),
            )
        }
    }
}

/** One unbroken stretch of text on a single line, and the box covering it. */
private data class TextRun(val offsets: IntRange, val box: TextBox)

/**
 * Groups placements into stretches that can be repainted as one patch.
 *
 * A run breaks where the offsets stop being consecutive — a separator owns an
 * offset and no rectangle — and **also** where the next character does not share
 * a line with the last. The second test is the one that is easy to miss: when
 * V-28 joins two columns into one flow their offsets *are* consecutive, and
 * unioning across them would repaint a rectangle covering everything between.
 */
private fun ScanLayout.runs(): List<TextRun> {
    val runs = mutableListOf<TextRun>()
    var start: CharacterPlacement? = null
    var previous: CharacterPlacement? = null
    var box: TextBox? = null

    fun flush() {
        val s = start ?: return
        val p = previous ?: return
        runs += TextRun(s.offset..p.offset, box!!)
        start = null
    }

    for (placement in placements) {
        val last = previous
        val continues = last != null &&
            start != null &&
            placement.offset == last.offset + 1 &&
            placement.direction == last.direction &&
            placement.sharesLineWith(last)

        if (!continues) {
            flush()
            start = placement
            box = placement.box
        } else {
            box = box!!.union(placement.box)
        }
        previous = placement
    }
    flush()
    return runs
}

private fun CharacterPlacement.sharesLineWith(other: CharacterPlacement): Boolean =
    when (direction) {
        WritingDirection.Horizontal -> box.overlapY(other.box) > 0
        WritingDirection.Vertical -> box.overlapX(other.box) > 0
    }

private fun TextBox.expandedBy(amount: Int, maxWidth: Int, maxHeight: Int) = TextBox(
    left = (left - amount).coerceAtLeast(0),
    top = (top - amount).coerceAtLeast(0),
    right = (right + amount).coerceAtMost(maxWidth),
    bottom = (bottom + amount).coerceAtMost(maxHeight),
)

/** How far proud of its measured box each patch is repainted, in glyphs. */
private const val PATCH_BLEED = 0.10

/**
 * `rgba(10, 9, 8, .68)` from artboard 1a — warm near-black, not grey.
 *
 * Not a theme token because it is not a surface: it is a filter over somebody's
 * photograph, and it has to hold at that exact weight in both themes. The
 * overlay is dark regardless of the system setting, because the thing underneath
 * it is a photograph rather than a page.
 */
private val SCRIM = Color(0xAD0A0908)

/**
 * The peek sheet: the word, what it means, and the two things to do next
 * (D-30, D-31).
 *
 * **No reading** (D-47) — the app would be guessing which one applies, and a
 * learner who already knew it would not have scanned the word.
 */
@Composable
internal fun PeekSheet(
    peek: PeekState,
    onFullDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(
                    start = SpotterTheme.tokens.spaceMd,
                    end = SpotterTheme.tokens.spaceMd,
                    top = SpotterTheme.tokens.spaceSm,
                    bottom = SpotterTheme.tokens.spaceLg,
                ),
        ) {
            // The drag handle from artboard 1a. Drawn because the sheet is
            // eventually the word screen expanded (D-30) and the affordance
            // should not appear later as if bolted on; the drag itself is not
            // wired up yet.
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = SpotterTheme.tokens.spaceMd)
                    .size(width = 34.dp, height = 4.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                shape = RoundedCornerShape(2.dp),
                content = {},
            )

            Text(
                text = peek.text,
                fontFamily = SpotterJapanese,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = when {
                    peek.loading -> stringResource(R.string.scan_peek_looking_up)
                    peek.glosses != null -> peek.glosses
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
                // Save is drawn and disabled rather than omitted. Saving writes
                // user data, which is gated behind the Phase 6 checkpoint
                // (D-15–D-18, D-43) — getting those wrong deletes real data in
                // production, so the button waits for the schema rather than the
                // schema being improvised for the button.
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(stringResource(R.string.scan_peek_save))
                }

                OutlinedButton(
                    onClick = onFullDetails,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.scan_peek_full_details))
                }
            }
        }
    }
}
