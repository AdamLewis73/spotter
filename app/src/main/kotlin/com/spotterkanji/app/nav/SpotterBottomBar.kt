package com.spotterkanji.app.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * ### The icons are deliberately minimal
 *
 * A dot rather than a pictogram. The design has not supplied nav icons — the
 * wireflow drew placeholder outlines — and inventing three is designing in
 * place, which this project has been bitten by before. A filled dot for the
 * current destination and a hollow one otherwise reads as intentional rather
 * than unfinished, and swapping in real icons later touches only this file.
 *
 * `NavigationBarItem` requires an icon slot, which is why there is a shape here
 * at all rather than labels alone.
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
                icon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .then(
                                if (selected) {
                                    Modifier.background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape,
                                    )
                                } else {
                                    Modifier.border(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                        CircleShape,
                                    )
                                }
                            ),
                    )
                },
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
