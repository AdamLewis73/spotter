package com.spotterkanji.app.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

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
    NavigationBar(
        modifier = modifier,
        // The bar sits over a photograph on the Scan destination, so it needs an
        // opaque ground of its own — the default container colour is derived
        // from the surface and would let the viewfinder show through behind the
        // labels.
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        SpotterDestination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = { NavGlyphIcon(glyph = destination.glyph, selected = selected) },
                label = { Text(stringResource(destination.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    // The jade pill behind a selected item would fight the dot,
                    // which is already carrying selection. Colour does the work.
                    indicatorColor = MaterialTheme.colorScheme.surface,
                ),
            )
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
