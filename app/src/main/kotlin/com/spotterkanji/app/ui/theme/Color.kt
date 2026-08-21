package com.spotterkanji.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A **fixed** palette, defined here rather than derived from the user's
 * wallpaper (D-35, superseded by D-67).
 *
 * Dynamic colour (Material You) was considered and rejected for a reason
 * specific to this app: the scan overlay dims the photograph and keeps detected
 * text bright (D-33), and that contrast has to hold over *arbitrary
 * photographs*. A palette derived from whatever wallpaper someone happens to
 * have makes that unpredictable — and it also means no two users' screenshots
 * look alike, which is no basis for a store listing. That reasoning is unchanged
 * and is the reason this file exists at all.
 *
 * **What changed (D-67): warm near-black and a single jade accent**, replacing
 * the indigo-and-teal Material scheme. The ground is warm rather than the blue-
 * grey Material defaults to, which matters for a camera app — a neutral-warm
 * chrome sits behind photographs without tinting the user's sense of what they
 * photographed.
 *
 * **On the accent being jade rather than the amber the design proposed:** amber
 * on near-black is a very specific and very well-known brand pairing, and not
 * one a language-learning app wants to evoke. The hue moved; the lightness and
 * chroma did not, so every contrast relationship the design was drawn against
 * still holds. It stays deliberately clear of the red-and-white every
 * Japan-themed app reaches for.
 *
 * *One rule the accent imposes, worth honouring before Phase 7 makes it
 * awkward:* green means **correct**. When the FSRS grading buttons arrive, they
 * must be neutral outlines with the accent on the primary action only — or the
 * palette starts making a claim about whether the answer was right.
 *
 * Measured, not eyeballed. Against the dark ground `#14120F`:
 *
 * | Role | Colour | Contrast |
 * |---|---|---|
 * | `onSurface` | `#F4F0EA` | 16.5:1 |
 * | `onSurfaceVariant` | `#A8A099` | 7.3:1 |
 * | `primary` | `#2DC08E` | 8.1:1 |
 *
 * The accent carries dark text *on* it (`onPrimary` is the ground itself), so
 * that 8.1:1 reads both directions.
 *
 * Every composable reads colour from here, so changing the accent later is one
 * line rather than a sweep. That was D-35's whole point and it is why D-67 cost
 * almost nothing.
 */

// Dark. Not an afterthought and listed first — `ux.md` calls dark mode "not
// optional", because evenings and dim interiors are prime usage time for a
// camera app, and the design was drawn dark-first for the same reason.
private val GroundDark = Color(0xFF14120F)          // page
private val SurfaceDark = Color(0xFF1E1B17)         // cards, sheets
private val SurfaceVariantDark = Color(0xFF272320)  // menus, raised chrome
private val OnSurfaceDark = Color(0xFFF4F0EA)       // warm off-white, never pure white
private val OnSurfaceVariantDark = Color(0xFFA8A099)
private val OutlineDark = Color(0xFF4C4A46)         // rgba(244,240,234,.25) resolved to a solid
private val JadeDark = Color(0xFF2DC08E)            // oklch(0.72 0.14 165)
private val JadeContainerDark = Color(0xFF00513A)
private val OnJadeContainerDark = Color(0xFFA8F2D3)

// Light. Warm paper rather than white — the same warmth as the dark ground,
// seen from the other side. Pure white against Noto Sans JP's dense kanji glares
// in a way #FAF7F0 does not.
private val GroundLight = Color(0xFFF2EEE5)
private val SurfaceLight = Color(0xFFFBF8F2)
private val SurfaceVariantLight = Color(0xFFE8E3D8)
private val OnSurfaceLight = Color(0xFF1A1714)
private val OnSurfaceVariantLight = Color(0xFF57514A)
private val OutlineLight = Color(0xFFB5AC9F)
// The same hue held at a lower lightness so it passes on paper: 4.8:1 as text,
// 5.1:1 carrying white. The dark scheme's #2DC08E would be 2.2:1 here — legible
// as a fill, not as text, which is exactly the trap a single shared accent sets.
private val JadeLight = Color(0xFF007E57)
private val JadeContainerLight = Color(0xFFB8EBD4)
private val OnJadeContainerLight = Color(0xFF00291B)

internal val SpotterLightColors = lightColorScheme(
    primary = JadeLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = JadeContainerLight,
    onPrimaryContainer = OnJadeContainerLight,
    // No second accent hue. The design carries one, and every role Material
    // would fill with a secondary is served by the neutral ramp instead — which
    // is what keeps the accent meaning something when it does appear.
    secondary = JadeLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = JadeContainerLight,
    onSecondaryContainer = OnJadeContainerLight,
    background = GroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
)

internal val SpotterDarkColors = darkColorScheme(
    primary = JadeDark,
    // The ground, not black. Text on the accent should belong to the same warm
    // family as everything else.
    onPrimary = GroundDark,
    primaryContainer = JadeContainerDark,
    onPrimaryContainer = OnJadeContainerDark,
    secondary = JadeDark,
    onSecondary = GroundDark,
    secondaryContainer = JadeContainerDark,
    onSecondaryContainer = OnJadeContainerDark,
    background = GroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
)
