package com.spotterkanji.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.spotterkanji.app.ui.theme.SpotterTheme

/**
 * Scaffold only. This exists to prove the toolchain builds and launches; it is
 * not the app's first screen.
 *
 * The scan screen arrives in Phase 4, when the app starts opening directly on
 * the camera (D-61).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpotterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Placeholder()
                }
            }
        }
    }
}

@Composable
private fun Placeholder() {
    val tokens = SpotterTheme.tokens
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tokens.spaceSm, Alignment.CenterVertically),
    ) {
        // Japanese on purpose: if font handling is wrong this renders as tofu
        // boxes and the problem surfaces here rather than at the first real
        // screen. Bundling Noto Sans JP is D-34.
        Text(text = "先生", style = MaterialTheme.typography.displayLarge)
        // The four characters whose glyph forms differ between Chinese and
        // Japanese typefaces — the V-12 case for D-34, on screen where it can
        // actually be looked at.
        Text(text = "直 骨 令 化", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "せんせい",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, name = "Light")
@Composable
private fun PlaceholderPreviewLight() {
    SpotterTheme(darkTheme = false) { Surface { Placeholder() } }
}

@Preview(showBackground = true, name = "Dark")
@Composable
private fun PlaceholderPreviewDark() {
    SpotterTheme(darkTheme = true) { Surface { Placeholder() } }
}
