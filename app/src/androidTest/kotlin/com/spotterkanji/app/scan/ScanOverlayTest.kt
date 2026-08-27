package com.spotterkanji.app.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.domain.scan.ScanFragment
import com.spotterkanji.domain.scan.ScanLayout
import com.spotterkanji.domain.scan.ScanLine
import com.spotterkanji.domain.scan.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * The overlay's tap resolution, through the real Compose layout.
 *
 * **This is the half that cannot be unit-tested.** `ScanLayout` is checked in
 * `:domain` and `ScanProjection` has its own cases there; what neither can cover
 * is whether the two are composed correctly against a view whose size Compose
 * decided — which is exactly where a `ContentScale.Crop` mismatch would hide.
 *
 * The photograph is deliberately a different shape from the view, so a naive
 * `viewWidth / imageWidth` scale would fail these. With matching aspect ratios
 * the wrong transform passes, which is how this bug survives casual testing.
 */
class ScanOverlayTest {

    @get:Rule
    val compose = createComposeRule()

    private companion object {
        const val OVERLAY = "scan-overlay"
    }

    /**
     * 先生と生産 centred in a 1000×500 photograph, one glyph per 100 px.
     *
     * Centred deliberately. The view below is a different shape from the
     * photograph, so `ContentScale.Crop` throws away the left and right edges —
     * text placed near an edge would be cropped out of the view and untappable,
     * which says nothing about whether the transform is right.
     */
    private val layout = ScanLayout.of(
        listOf(
            ScanLine(
                listOf(ScanFragment("先生と生産", TextBox(250, 200, 750, 300))),
            ),
        ),
    )

    private fun overlay(
        viewWidth: Int,
        viewHeight: Int,
        onTap: (Int?) -> Unit,
    ) {
        compose.setContent {
            SpotterTheme {
                Box(modifier = Modifier.size(viewWidth.dp, viewHeight.dp)) {
                    ScanOverlay(
                        layout = layout,
                        frameWidth = 1000,
                        frameHeight = 500,
                        selection = null,
                        onOffsetTapped = onTap,
                        modifier = Modifier.testTag(OVERLAY),
                    )
                }
            }
        }
    }

    /**
     * V-11 through the UI: tapping 産 must resolve to 産, not to a neighbour.
     *
     * Checked at every character rather than at one, because an off-by-one is
     * systematically wrong while looking merely flaky.
     */
    @Test
    fun every_character_resolves_to_itself_through_the_real_layout() {
        var tapped: Int? = null
        overlay(viewWidth = 400, viewHeight = 300) { tapped = it }

        val overlay = compose.onNodeWithTag(OVERLAY)
        val bounds = overlay.fetchSemanticsNode().size

        // Work in the view's own pixel space: the projection is crop-centred, so
        // recompute where each glyph landed rather than assuming.
        val scale = maxOf(bounds.width / 1000.0, bounds.height / 500.0)
        val offsetX = (bounds.width - 1000 * scale) / 2.0
        val offsetY = (bounds.height - 500 * scale) / 2.0

        for (index in 0 until 5) {
            val centreImageX = 250 + index * 100 + 50
            val x = (centreImageX * scale + offsetX).toFloat()
            val y = (250 * scale + offsetY).toFloat()

            tapped = null
            overlay.performTouchInput { click(Offset(x, y)) }
            compose.waitForIdle()

            assertEquals("character $index", index, tapped)
        }
    }

    @Test
    fun a_tap_away_from_the_text_reports_nothing() {
        var tapped: Int? = -1
        overlay(viewWidth = 400, viewHeight = 300) { tapped = it }

        compose.onNodeWithTag(OVERLAY).performTouchInput { click(Offset(2f, 2f)) }
        compose.waitForIdle()

        assertNull("a tap on bare image must dismiss, not select", tapped)
    }
}
