package com.spotterkanji.app.nav

import androidx.annotation.StringRes
import com.spotterkanji.app.R

/**
 * The app's three top-level destinations (D-36, D-90).
 *
 * **Three, and resist a fourth.** Settings, storage and attribution do not
 * belong here — they land on a Profile screen reached from Saved, which is not
 * built yet. D-36 has said three since before the camera existed, and D-90
 * settled the question the camera raised: the bar is drawn on **all three**,
 * including the viewfinder.
 *
 * [Scan] is the start destination (D-61). The app opens on the camera, and the
 * bar is how you leave it rather than something you pass through to reach it.
 *
 * `route` is a plain string rather than one of Navigation's type-safe route
 * classes: these three carry no arguments, so a serializable route type would
 * add a plugin and a class per destination to express what an enum already
 * says. Routes that *do* take arguments — a list id — are declared where they
 * are used.
 */
enum class SpotterDestination(
    val route: String,
    @StringRes val label: Int,
    val glyph: NavGlyph,
) {
    Scan("scan", R.string.destination_scan, NavGlyph.Circle),
    Saved("saved", R.string.destination_saved, NavGlyph.RoundedSquare),
    Review("review", R.string.destination_review, NavGlyph.Diamond),
    ;

    companion object {
        val start = Scan

        /** The destination owning [route], or null for a route not in the bar. */
        fun forRoute(route: String?): SpotterDestination? =
            entries.firstOrNull { it.route == route }
    }
}

/**
 * The shape a destination is drawn as in the bar, taken from the wireflow.
 *
 * The design uses **geometry rather than pictograms** — a 19px circle, rounded
 * square and diamond — which is why there is no icon set here and no dependency
 * on one. Selection is carried by fill against outline, not by swapping shapes,
 * so each destination keeps the same silhouette whether or not it is current.
 */
enum class NavGlyph { Circle, RoundedSquare, Diamond }
