// Font variation settings are still marked experimental in Compose, but they are
// the only way to select weights from a variable font — and Google Fonts ships
// Noto Sans JP as a variable font only. The alternative is synthetic bold, which
// looks bad on CJK glyphs (D-34).
@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.spotterkanji.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.spotterkanji.app.R

/**
 * Noto Sans JP, bundled rather than left to the system (D-34).
 *
 * Unicode unifies Chinese and Japanese onto shared codepoints, but the correct
 * *glyph shapes* differ — 直, 骨, 令 and 化 all render visibly differently — and
 * Android's default stack may pick the Chinese forms depending on locale and
 * device. In an app that teaches people to read and write kanji, that is a
 * correctness bug rather than a polish issue. V-12 is the case that checks it.
 *
 * **This is a variable font**: one 9.2 MB file spanning weights 100–900, which
 * is what Google Fonts ships (there are no static instances). Android supports
 * variable fonts from API 26, which is exactly this project's `minSdk`.
 *
 * Each weight below names the same resource with different variation settings.
 * That looks redundant and is not — without an entry, Compose synthesises the
 * weight by smearing the glyphs, and synthetic bold on CJK forms turns dense
 * kanji into mush at the sizes this app uses.
 */
internal val SpotterFontFamily = FontFamily(
    Font(
        R.font.noto_sans_jp,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.noto_sans_jp,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.noto_sans_jp,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/**
 * Japanese text needs more line height than Latin at the same size: the glyphs
 * fill their em box far more completely, so lines set at Latin defaults look
 * cramped and stacked. The ratios below are deliberately generous.
 */
internal val SpotterTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SpotterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 68.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SpotterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 40.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SpotterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 32.sp,
    ),
    // The workhorse: word meanings and glosses. "teacher; instructor; master" is
    // one sense rendered on one line (D-47), so it wraps often.
    bodyLarge = TextStyle(
        fontFamily = SpotterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SpotterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SpotterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)
