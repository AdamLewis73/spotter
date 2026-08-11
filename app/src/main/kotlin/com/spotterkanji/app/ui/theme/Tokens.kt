package com.spotterkanji.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The values Material 3 does not model, but this app cannot do without.
 *
 * M3 covers colour and a type scale. It has nothing to say about how far to dim
 * a photograph, how large furigana should sit above a word, or how much a tap
 * target has to grow before a 12dp kanji is reliably tappable — and every one of
 * those appears on multiple screens. Left as literals they end up inconsistent
 * across composables and impossible to tune in one place, which is exactly the
 * retrofit D-35 exists to avoid.
 */
data class SpotterTokens(
    /** 4dp grid. Anything not on it should have a reason. */
    val spaceXs: Dp = 4.dp,
    val spaceSm: Dp = 8.dp,
    val spaceMd: Dp = 16.dp,
    val spaceLg: Dp = 24.dp,
    val spaceXl: Dp = 32.dp,

    /**
     * How far the photograph is dimmed behind the overlay (D-33).
     *
     * The detected text stays at full brightness; this is what makes it read as
     * "lit up". Too low and the text does not stand out over a busy photograph;
     * too high and the user loses the context they took the picture for.
     */
    val scanDimAlpha: Float = 0.55f,

    /**
     * Furigana size as a fraction of the word it sits above (D-14).
     *
     * Real Japanese typesetting puts ruby at roughly half the base size. Below
     * about 0.4 it stops being legible at arm's length, which is the distance
     * this app is used at.
     */
    val furiganaScale: Float = 0.5f,

    /**
     * Material's accessibility minimum for a touch target.
     *
     * `ux.md` spells out the problem: a kanji on a shop sign photographed from
     * three metres away may occupy 12dp, and no amount of care in the overlay
     * makes a 12dp target reliably tappable. Tap regions are grown to this
     * regardless of how small the glyph looks.
     */
    val minTouchTarget: Dp = 48.dp,
)

/**
 * Read tokens with `SpotterTheme.tokens`, never by constructing [SpotterTokens].
 * A local keeps them swappable — for a compact layout, or a test — without
 * threading a parameter through every composable.
 */
internal val LocalSpotterTokens = staticCompositionLocalOf { SpotterTokens() }
