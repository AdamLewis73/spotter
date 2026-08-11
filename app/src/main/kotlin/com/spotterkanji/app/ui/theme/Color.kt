package com.spotterkanji.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A **fixed** palette, defined here rather than derived from the user's
 * wallpaper (D-35).
 *
 * Dynamic colour (Material You) was considered and rejected for a reason
 * specific to this app: the scan overlay dims the photograph and keeps detected
 * text bright (D-33), and that contrast has to hold over *arbitrary
 * photographs*. A palette derived from whatever wallpaper someone happens to
 * have makes that unpredictable — and it also means no two users' screenshots
 * look alike, which is no basis for a store listing.
 *
 * Deep indigo reads as calm and legible in both schemes, and stays distinct
 * from the photograph behind it without competing for attention. It is
 * deliberately not the red-and-white palette every Japan-themed app reaches for.
 *
 * These are a working starting point, not a finished identity. What matters at
 * this stage is that every composable reads colour from here, so changing it
 * later is one file rather than a sweep.
 */

// Light
private val IndigoPrimaryLight = Color(0xFF3A4C9B)
private val IndigoOnPrimaryLight = Color(0xFFFFFFFF)
private val IndigoContainerLight = Color(0xFFDDE1FF)
private val IndigoOnContainerLight = Color(0xFF00164E)
private val TealSecondaryLight = Color(0xFF1F6B63)
private val TealOnSecondaryLight = Color(0xFFFFFFFF)
private val TealContainerLight = Color(0xFFB4EFE5)
private val TealOnContainerLight = Color(0xFF00201C)
private val SurfaceLight = Color(0xFFFDFBFF)
private val OnSurfaceLight = Color(0xFF1B1B1F)
private val SurfaceVariantLight = Color(0xFFE2E1EC)
private val OnSurfaceVariantLight = Color(0xFF45464F)
private val OutlineLight = Color(0xFF767680)

// Dark. Not an afterthought — `ux.md` calls dark mode "not optional", because
// evenings and dim interiors are prime usage time for a camera app.
private val IndigoPrimaryDark = Color(0xFFB8C4FF)
private val IndigoOnPrimaryDark = Color(0xFF06176B)
private val IndigoContainerDark = Color(0xFF212F83)
private val IndigoOnContainerDark = Color(0xFFDDE1FF)
private val TealSecondaryDark = Color(0xFF99D3C9)
private val TealOnSecondaryDark = Color(0xFF003733)
private val TealContainerDark = Color(0xFF00504A)
private val TealOnContainerDark = Color(0xFFB4EFE5)
private val SurfaceDark = Color(0xFF1B1B1F)
private val OnSurfaceDark = Color(0xFFE4E1E6)
private val SurfaceVariantDark = Color(0xFF45464F)
private val OnSurfaceVariantDark = Color(0xFFC6C5D0)
private val OutlineDark = Color(0xFF90909A)

internal val SpotterLightColors = lightColorScheme(
    primary = IndigoPrimaryLight,
    onPrimary = IndigoOnPrimaryLight,
    primaryContainer = IndigoContainerLight,
    onPrimaryContainer = IndigoOnContainerLight,
    secondary = TealSecondaryLight,
    onSecondary = TealOnSecondaryLight,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = TealOnContainerLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
)

internal val SpotterDarkColors = darkColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = IndigoOnPrimaryDark,
    primaryContainer = IndigoContainerDark,
    onPrimaryContainer = IndigoOnContainerDark,
    secondary = TealSecondaryDark,
    onSecondary = TealOnSecondaryDark,
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = TealOnContainerDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
)
