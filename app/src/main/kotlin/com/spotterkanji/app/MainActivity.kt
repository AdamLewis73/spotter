package com.spotterkanji.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * Scaffold only. This exists to prove the toolchain builds and launches; it is
 * not the app's first screen.
 *
 * Two things are deliberately absent:
 *  - the Material 3 design-token layer (D-35), a checkpoint due before the first
 *    real UI commit, hence bare [MaterialTheme] defaults here;
 *  - anything resembling the scan screen, which arrives in Phase 4 when the app
 *    starts opening directly on the camera (D-61).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Placeholder()
                }
            }
        }
    }
}

@Composable
private fun Placeholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Japanese text on purpose: if the toolchain or font handling is wrong,
        // this renders as tofu boxes and the problem surfaces immediately rather
        // than at the first real screen. Bundling Noto Sans JP is D-34.
        Text(text = "先生", style = MaterialTheme.typography.displayLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderPreview() {
    MaterialTheme {
        Placeholder()
    }
}
