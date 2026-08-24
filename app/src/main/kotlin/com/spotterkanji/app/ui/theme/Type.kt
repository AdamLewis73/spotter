// Font variation settings are still marked experimental in Compose, but they are
// the only way to select weights from a variable font — and both Noto Sans JP
// and IBM Plex Sans ship as variable fonts. The alternative is synthetic bold,
// which looks bad on CJK glyphs (D-34).
@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.spotterkanji.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
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
 *
 * **Use this for any text that can contain Japanese** — see [SpotterLatin] for
 * why that matters.
 */
internal val SpotterJapanese = FontFamily(
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
 * IBM Plex Sans — the Latin voice (D-67).
 *
 * Noto Sans JP carries perfectly good Latin glyphs, and the app used them for
 * everything until now. Plex is a deliberate change: it gives English meanings a
 * different texture from the Japanese they explain, so a gloss reads as
 * commentary rather than as more of the same sentence. On a screen whose whole
 * job is "here is the Japanese, here is what it means", that separation is doing
 * real work.
 *
 * Variable, one file, 537 KB — the roman only. Nothing in the design uses
 * italic, so the 599 KB italic file is not bundled.
 *
 * **The trap: this font contains no Japanese.** Give it Japanese text and
 * Android falls back to whatever CJK font the *system* provides, which is the
 * exact failure D-34 bundles Noto to prevent — and it fails quietly, as a subtly
 * Chinese-shaped 直 rather than as tofu or a crash.
 *
 * So the families are assigned per role rather than left to font fallback:
 * anything that can hold Japanese takes [SpotterJapanese] explicitly, at the
 * call site. Compose's `FontFamily(a, b)` does **not** give per-glyph fallback —
 * it matches one font per weight and style — so listing Noto after Plex would
 * look like a safety net while providing none.
 */
internal val SpotterLatin = FontFamily(
    Font(
        R.font.ibm_plex_sans,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.ibm_plex_sans,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.ibm_plex_sans,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.ibm_plex_sans,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/**
 * IBM Plex Mono — counts, and the small tracked-out labels (D-67).
 *
 * `5 READINGS · 2 ARCHAIC`, `ON'YOMI`, `34 WORDS · 8 DUE`. These are metadata
 * about the content rather than content, and setting them in a monospace at
 * small size with wide letter-spacing is what keeps them from competing with the
 * word itself. Digits also line up in a column, which matters once Saved and
 * Review start showing counts in lists.
 *
 * No variable build exists, so the two weights the design uses are two static
 * files, 136 KB each. Do not add a third weight without adding a file — Compose
 * will synthesise it silently.
 */
internal val SpotterMono = FontFamily(
    Font(R.font.ibm_plex_mono, weight = FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, weight = FontWeight.Medium),
)

/**
 * The Material scale, set in Latin by default.
 *
 * Default rather than neutral on purpose: most text in the app is English, and
 * the Japanese-bearing composables are few, identifiable, and already separate
 * `Text` calls — a reading, a headword, an example word. Those name
 * [SpotterJapanese] explicitly. Defaulting the other way would mean every label,
 * button and gloss in the app opting out of the default.
 *
 * Japanese text needs more line height than Latin at the same size: the glyphs
 * fill their em box far more completely, so lines set at Latin defaults look
 * cramped and stacked. The ratios below stay generous, because the styles are
 * shared with the Japanese composables that override only the family.
 */
internal val SpotterTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SpotterJapanese,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 68.sp,
    ),
    // The kanji screen's headword. Japanese by definition.
    headlineMedium = TextStyle(
        fontFamily = SpotterJapanese,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 40.sp,
    ),
    // Reading headings — じょうず, せんせい. Japanese by definition (D-48).
    titleLarge = TextStyle(
        fontFamily = SpotterJapanese,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 32.sp,
    ),
    // The workhorse: word meanings and glosses. "teacher; instructor; master" is
    // one sense rendered on one line (D-47), so it wraps often. Latin.
    bodyLarge = TextStyle(
        fontFamily = SpotterLatin,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SpotterLatin,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SpotterLatin,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // The tracked-out metadata line. Letter-spacing is part of the style, not a
    // per-call decision — it is what makes 12sp read as a label rather than as
    // small body text.
    labelSmall = TextStyle(
        fontFamily = SpotterMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.08.em,
    ),
)
