package com.spotterkanji.app.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp

/**
 * The bar's own row. Material's item centres its icon and label in it, so every
 * 2dp here is 1dp above the icons and 1dp below the labels. 70dp gives 12dp
 * above, measured on the emulator — the owner asked for about 12 each side
 * (D-101). Material's default is 80dp (17dp above); 64dp was tried and read as
 * scrunched.
 */
private val BarHeight = 70.dp

/**
 * How far the bar sinks into a gesture strip. Chosen with [BarHeight] so the
 * labels clear the gesture handle by about the same 12dp as the icons clear the
 * top edge — measured on a Pixel 9 emulator, whose strip is 23dp (D-101).
 */
private val GestureOverlap = 16.dp

/**
 * The app's own bottom navigation (D-90) — not Android's system navigation bar,
 * which is the strip below this one carrying back, home and recents.
 *
 * Drawn on all three destinations, the viewfinder included. That costs a strip
 * of chrome across a screen D-61 wants uncluttered, and it was accepted
 * knowingly: the alternative was a corner control that competes with the system
 * back gesture and an exit swipe that loses to the OS, both of which need
 * teaching. This does not.
 *
 * ### The icons are the design's, and they are geometry
 *
 * A 19dp circle for Scan, rounded square for Saved, diamond for Review — taken
 * from the wireflow rather than invented, and the reason this file needs no icon
 * set and no dependency on one. Filled in the accent when current, a 2dp outline
 * otherwise; the silhouette never changes, so a destination is recognisable
 * whether or not it is the one you are on.
 *
 * They were briefly built as three identical dots, which lost the distinction
 * between destinations entirely. Worth remembering: the shapes were specified,
 * and a stale copy of the design said otherwise.
 */
@Composable
internal fun SpotterBottomBar(
    current: SpotterDestination?,
    onSelect: (SpotterDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hairline = MaterialTheme.colorScheme.outline
    // Raised chrome, one step off the sheets and cards (D-101). It was `surface`,
    // which is the scan sheet's colour, so the peek ran straight into the bar
    // with no edge between them. Opaque either way: on Scan it sits over the
    // viewfinder.
    val ground = MaterialTheme.colorScheme.surfaceVariant
    // Below the labels there were two empty margins stacked: Material's own,
    // inside the bar, and Android's gesture strip under it — about three times
    // the space above the icons. On gesture navigation the two overlap instead: the bar's
    // bottom margin sits inside the strip, where taps still reach the app and
    // only the upward swipe is the system's. With three-button navigation the
    // strip is buttons, so nothing may overlap it. `tappableElement` is the
    // measure that tells them apart — the full button strip there, zero for a
    // gesture handle — so the overlap is only ever the non-tappable part.
    val density = LocalDensity.current
    val navigationBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val tappableBottom = with(density) { WindowInsets.tappableElement.getBottom(this).toDp() }
    val overlap = minOf((navigationBottom - tappableBottom).coerceAtLeast(0.dp), GestureOverlap)
    // The ground is painted here rather than by the NavigationBar so it can run
    // down behind Android's gesture strip while the bar itself stays [BarHeight].
    Box(
        modifier = modifier
            .background(ground)
            // A hairline along the top edge, so the bar stays distinct from
            // anything above it drawn in its own colour — the Saved cards are
            // surfaceVariant too (D-101).
            .drawWithContent {
                drawContent()
                val y = 0.5.dp.toPx()
                drawLine(hairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            .windowInsetsPadding(NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal))
            .padding(bottom = navigationBottom - overlap),
    ) {
        NavigationBar(
            modifier = Modifier.height(BarHeight),
            containerColor = ground,
            // Material tints a bar by its elevation; the colour above is the one
            // chosen, so nothing is added to it.
            tonalElevation = 0.dp,
            // Insets are applied by the Box above.
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            SpotterDestination.entries.forEach { destination ->
                val selected = destination == current
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(destination) },
                    icon = { NavGlyphIcon(glyph = destination.glyph, selected = selected) },
                    label = {
                        // The phone's own UI font (Roboto on a Pixel), by the project
                        // owner's choice over IBM Plex: it reads better at this size
                        // (D-97). Named rather than left to the style, so giving
                        // labelMedium a typeface later cannot change it by accident.
                        Text(stringResource(destination.label), fontFamily = FontFamily.Default)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        // The jade pill behind a selected item would fight the dot,
                        // which is already carrying selection. Colour does the work.
                        // Same colour as the bar, so the pill is not drawn at all.
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }
        }
    }
}

/**
 * One destination's shape, at the wireflow's 19dp with its 2dp outline.
 *
 * The diamond is the rounded square turned 45°, exactly as the design expresses
 * it — `rotate` rather than a separate path, so the two stay the same size and
 * weight as each other by construction.
 */
@Composable
private fun NavGlyphIcon(glyph: NavGlyph, selected: Boolean) {
    val shape = when (glyph) {
        NavGlyph.Circle -> CircleShape
        NavGlyph.RoundedSquare -> RoundedCornerShape(4.dp)
        NavGlyph.Diamond -> RoundedCornerShape(3.dp)
    }
    Box(
        modifier = Modifier
            .size(19.dp)
            .then(if (glyph == NavGlyph.Diamond) Modifier.rotate(45f) else Modifier)
            // A rotated square needs to shrink to keep the same optical weight as
            // the others: its corners reach further than its edges, so drawn at
            // 19dp it reads noticeably larger than the circle beside it.
            .then(if (glyph == NavGlyph.Diamond) Modifier.scale(0.78f) else Modifier)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, shape)
                }
            ),
    )
}
