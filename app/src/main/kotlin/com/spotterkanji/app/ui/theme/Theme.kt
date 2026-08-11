package com.spotterkanji.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/**
 * The app's theme. **Every composable goes through this** — none reaches for a
 * raw `Color`, `sp` or `dp` of its own (D-35).
 *
 * Ten minutes of setup now against a refactor touching every composable later.
 * This is the checkpoint the roadmap flags before the first real UI, and the
 * reason it is a checkpoint is that a token layer is nearly free to add before
 * there are screens and expensive afterwards.
 */
@Composable
fun SpotterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) SpotterDarkColors else SpotterLightColors
    val tokens = remember { SpotterTokens() }

    CompositionLocalProvider(LocalSpotterTokens provides tokens) {
        MaterialTheme(
            colorScheme = colors,
            typography = SpotterTypography,
            content = content,
        )
    }
}

/**
 * Accessor for the app-specific tokens, mirroring how `MaterialTheme.colorScheme`
 * reads. `SpotterTheme.tokens.spaceMd` rather than a bare `16.dp`.
 */
object SpotterTheme {
    val tokens: SpotterTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalSpotterTokens.current
}
