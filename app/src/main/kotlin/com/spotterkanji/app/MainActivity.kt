package com.spotterkanji.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.app.word.KanjiScreen
import com.spotterkanji.app.word.WordLookupViewModel
import com.spotterkanji.app.word.WordScreen

/**
 * Phase 2's screen: type a word, see what it means.
 *
 * Not the app's real entry point. From Phase 4 the app opens directly on the
 * camera (D-61) and this becomes a path *into* the word screen rather than the
 * whole of it. It exists now because the value of the app can be proven with a
 * text field and no camera at all, which is the point of building inside-out.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // From targetSdk 35 Android draws apps edge to edge and stops insetting
        // them automatically, so without this the first line of content sits
        // *under* the status bar and the last sits under the navigation bar.
        // It is not opt-in behaviour that can be declined at targetSdk 37.
        //
        // The surface deliberately fills the whole window — the background
        // should reach the screen edges — while `safeDrawingPadding` keeps the
        // content clear of the system bars. This matters more than usual for a
        // camera app whose overlay will want the full frame (D-33).
        enableEdgeToEdge()
        setContent {
            SpotterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: WordLookupViewModel = viewModel()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val openKanji = state.openKanji

                    // The kanji screen replaces the word screen rather than
                    // stacking beside it (D-32). System back closes it before
                    // leaving the app, which is the behaviour the eventual
                    // bottom sheet will need to reproduce by hand — a
                    // ModalBottomSheet has no back stack of its own.
                    BackHandler(enabled = openKanji != null, onBack = viewModel::onKanjiClosed)

                    if (openKanji != null) {
                        KanjiScreen(
                            detail = openKanji,
                            onBack = viewModel::onKanjiClosed,
                            modifier = Modifier.safeDrawingPadding(),
                        )
                    } else {
                        WordScreen(
                            state = state,
                            onQueryChanged = viewModel::onQueryChanged,
                            onTokenSelected = viewModel::onTokenSelected,
                            onKanjiSelected = viewModel::onKanjiSelected,
                            modifier = Modifier.safeDrawingPadding(),
                        )
                    }
                }
            }
        }
    }
}
