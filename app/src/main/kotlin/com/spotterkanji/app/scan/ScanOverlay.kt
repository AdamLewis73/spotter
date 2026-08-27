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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotterkanji.app.R
import com.spotterkanji.app.ui.theme.SpotterJapanese
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.domain.scan.ScanLayout
import com.spotterkanji.domain.scan.ScanProjection
import com.spotterkanji.domain.scan.ScreenRect
import kotlin.math.roundToInt

/**
 * The tappable overlay — artboard **1a**, "dim frame, bright text, solid
 * selection" (D-33).
 *
 * **The recognized text is redrawn, not revealed.** Un-dimming the photograph's
 * own pixels would leave dim text dim, and a scan of a shadowed sign would be no
 * more legible than the photograph. Drawing each character into its own measured
 * rectangle instead gives an evenly bright, crisply set line — which is what the
 * artboard shows and what makes "the legible text itself is the affordance"
 * (D-33) actually true rather than aspirational.
 *
 * Two things fall out of using `ScanLayout`'s per-character boxes for this:
 * alignment is exact by construction, since every glyph is drawn where it was
 * measured; and 縦書き needs no special case at all, because a vertical
 * character's box is simply lower than the one before it.
 *
 * What it costs is honesty about recognition: a misread comes back as confident,
 * well-set text. That is already true of everything downstream — the tokenizer
 * and dictionary see the same string — so the overlay is not introducing the
 * problem, only rendering it legibly.
 */
@Composable
internal fun ScanOverlay(
    layout: ScanLayout,
    frameWidth: Int,
    frameHeight: Int,
    selection: IntRange?,
    onOffsetTapped: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme

    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    val projection = remember(frameWidth, frameHeight, viewSize) {
        ScanProjection.crop(
            imageWidth = frameWidth,
            imageHeight = frameHeight,
            viewWidth = viewSize.width.toDouble(),
            viewHeight = viewSize.height.toDouble(),
        )
    }

    // Measured once per photograph and view size, never per tap. Selection is a
    // colour decision made at draw time, so tapping along a line does not
    // re-measure a hundred glyphs each time.
    val glyphs = remember(layout, projection) {
        if (viewSize == IntSize.Zero) {
            emptyList()
        } else {
            layout.placements.map { placement ->
                val rect = projection.project(placement.box)
                // A CJK glyph is square, so either extent of its box describes
                // it; the smaller one keeps a glyph inside its rectangle when
                // the box is slightly off square.
                val sizePx = minOf(rect.width, rect.height).coerceAtLeast(1.0)
                val measured = measurer.measure(
                    text = placement.char.toString(),
                    style = TextStyle(
                        fontFamily = SpotterJapanese,
                        fontSize = with(density) { sizePx.toFloat().toSp() },
                        fontWeight = FontWeight.Medium,
                        // Lifts the text off a busy photograph. The scrim does
                        // most of the work; this covers the bright patches it
                        // cannot darken enough without hiding the picture.
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.9f),
                            offset = Offset(0f, 2f),
                            blurRadius = 12f,
                        ),
                    ),
                )
                Glyph(offset = placement.offset, measured = measured, rect = rect)
            }
        }
    }

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

        val selected = glyphs.filter { selection != null && it.offset in selection }
        if (selected.isNotEmpty()) {
            // One block behind the whole word, not a box per character. A token
            // cannot span a separator — Kuromoji breaks at one — so the union is
            // always a single run on a single line.
            val union = selected.map { it.rect }.reduce(ScreenRect::union)
            // Padding comes from the *glyph*, not from the union. A vertical run
            // is as tall as its whole column, so scaling the inset by the union's
            // height turns a five-character selection into a lozenge. The
            // cross-axis extent is the glyph in both directions.
            val glyph = minOf(union.width, union.height)
            val pad = (glyph * 0.08).toFloat()
            drawRoundRect(
                color = colors.primary,
                topLeft = Offset(union.left.toFloat() - pad, union.top.toFloat() - pad),
                size = Size(
                    width = union.width.toFloat() + pad * 2,
                    height = union.height.toFloat() + pad * 2,
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(pad * 2.5f),
            )
        }

        for (glyph in glyphs) {
            val isSelected = selection != null && glyph.offset in selection
            drawText(
                textLayoutResult = glyph.measured,
                color = if (isSelected) colors.onPrimary else colors.onSurface,
                topLeft = glyph.centredIn(),
            )
        }
    }
}

/** One measured character and where it goes, in view coordinates. */
private data class Glyph(
    val offset: Int,
    val measured: TextLayoutResult,
    val rect: ScreenRect,
) {
    /**
     * Centres the measured glyph inside its rectangle.
     *
     * A measured line box is taller than the ink it contains — ascent and
     * descent are reserved whether or not the glyph uses them — so drawing at
     * the rectangle's corner sits every character slightly high and slightly
     * left of where it was photographed.
     */
    fun centredIn() = Offset(
        x = (rect.left + (rect.width - measured.size.width) / 2).toFloat(),
        y = (rect.top + (rect.height - measured.size.height) / 2).toFloat(),
    )
}

private fun ScreenRect.union(other: ScreenRect) = ScreenRect(
    left = minOf(left, other.left),
    top = minOf(top, other.top),
    right = maxOf(right, other.right),
    bottom = maxOf(bottom, other.bottom),
)

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
