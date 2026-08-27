package com.spotterkanji.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotterkanji.app.scan.ScanScreen
import com.spotterkanji.app.scan.ScanViewModel
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.app.word.KanjiScreen
import com.spotterkanji.app.word.WordLookupViewModel
import com.spotterkanji.app.word.WordScreen

/**
 * The single activity.
 *
 * **The camera is the start destination (D-61)** — no home screen, no dashboard,
 * no shortcut grid in front of it. That is the whole positioning against the
 * incumbents, and it is a Phase 4 change because it shapes navigation rather
 * than being a coat of paint applied later.
 *
 * Phase 2's text-input screen survives as a *debug* path rather than being
 * deleted. It is how every `V-##` case so far has been driven, so removing it
 * would cost the project its test harness — but it is also a second front door,
 * which is exactly what D-61 rules out. Two mechanisms keep both facts true:
 *
 *  - The `query` **intent extra opens the lookup screen directly**, bypassing
 *    the camera entirely. `/inspect` therefore works unchanged, in any build
 *    type, without a single tap.
 *  - A small **search affordance on the camera screen**, present only in debug
 *    builds, reaches the same screen by hand.
 *
 * A real user-facing search is wanted eventually and is a deliberate open
 * question — see `progress/phase-04-camera.md`. It is not this: promoting the
 * debug path to a feature is a product decision about what the second screen of
 * a scanner-first app should be, and it is not made by leaving a button on.
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
        // should reach the screen edges — while `safeDrawingPadding` keeps
        // content clear of the system bars. The scan screen is the exception
        // that motivated it: the viewfinder wants the full frame, and insets
        // are applied to its controls rather than to the image (D-33).
        enableEdgeToEdge()

        // A word to open on, supplied by the launch intent:
        //
        //     adb shell am start -n com.spotterkanji.app/.MainActivity --es query 上手
        //
        // This exists because `adb shell input text` is ASCII-only and the
        // emulator has no clipboard command, so there was no way to get Japanese
        // into the text field from a script — which made "run it and look"
        // impossible for any *particular* word, on a screen whose failures are
        // silent rather than loud. Every bug Phase 2 produced was found by
        // looking at a specific word.
        //
        // Read unconditionally rather than behind a debug flag: it pre-fills a
        // dictionary search box and grants nothing, and a hook that only works
        // in debug builds is a hook that cannot check a release build.
        val seed = intent?.getStringExtra(EXTRA_QUERY)?.takeIf { it.isNotBlank() }

        setContent {
            SpotterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Not a navigation library. There are two destinations and
                    // a back stack of depth one, which is served better by a
                    // nullable String than by a dependency. The real navigation
                    // decision is Phase 5's bottom nav (D-36), and it should be
                    // made against three real destinations rather than pre-empted
                    // here.
                    //
                    // null means the camera. Non-null means the lookup screen,
                    // seeded with that string — which is empty when the debug
                    // affordance opened it by hand, and is the recognized text
                    // when a scan did.
                    var lookup by rememberSaveable { mutableStateOf(seed) }

                    val current = lookup
                    if (current != null) {
                        // A launch seeded by intent has nowhere to go back TO, so
                        // back leaves the app as it always did. A scan does, and
                        // back returns to the frozen frame it came from.
                        BackHandler(enabled = seed == null) { lookup = null }
                        LookupRoute(seed = current.takeIf { it.isNotBlank() })
                    } else {
                        ScanRoute(
                            onLookUp = { recognized -> lookup = recognized },
                            onOpenLookup = if (BuildConfig.DEBUG) {
                                { lookup = "" }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val EXTRA_QUERY = "query"
    }
}

@Composable
private fun ScanRoute(
    onLookUp: (String) -> Unit,
    onOpenLookup: (() -> Unit)?,
) {
    val viewModel: ScanViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Back on a frozen frame returns to the viewfinder rather than leaving the
    // app. The freeze is a state of the scan screen, not a destination (D-02),
    // but the system back button has no way to know that.
    BackHandler(enabled = state.frame != null) {
        // The peek sheet is a state within the frozen frame, just as the frozen
        // frame is a state within the scan screen (D-02, D-31). Back unwinds one
        // level at a time rather than jumping straight to the viewfinder.
        if (state.peek != null) viewModel.onPeekDismissed() else viewModel.onRetake()
    }

    ScanScreen(
        state = state,
        onShutterPressed = viewModel::onShutterPressed,
        onFrameCaptured = viewModel::onFrameCaptured,
        onCaptureFailed = viewModel::onCaptureFailed,
        onCameraUnavailable = viewModel::onCameraUnavailable,
        onCameraBound = viewModel::onCameraBound,
        onRetake = viewModel::onRetake,
        onLookUp = onLookUp,
        onOffsetTapped = viewModel::onOffsetTapped,
        onOpenLookup = onOpenLookup,
    )
}

/** Phase 2's screen, unchanged: type a word, see what it means. */
@Composable
private fun LookupRoute(seed: String?) {
    val viewModel: WordLookupViewModel = viewModel()
    // Once per composition, not once per recomposition — without the key the
    // seed would fight every keystroke the user makes.
    LaunchedEffect(seed) {
        seed?.let(viewModel::onQueryChanged)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openKanji = state.openKanji

    // The kanji screen replaces the word screen rather than stacking beside it
    // (D-32). System back closes it before leaving the app, which is the
    // behaviour the eventual bottom sheet will need to reproduce by hand — a
    // ModalBottomSheet has no back stack of its own.
    BackHandler(enabled = openKanji != null, onBack = viewModel::onKanjiClosed)

    if (openKanji != null) {
        KanjiScreen(
            detail = openKanji,
            onBack = viewModel::onKanjiClosed,
            onSave = {},
            modifier = Modifier.safeDrawingPadding(),
        )
    } else {
        WordScreen(
            state = state,
            onQueryChanged = viewModel::onQueryChanged,
            onTokenSelected = viewModel::onTokenSelected,
            onKanjiSelected = viewModel::onKanjiSelected,
            onAlternateSelected = viewModel::onAlternateSelected,
            // Saving arrives in Phase 6 with the user-data checkpoint
            // (D-15–D-18, D-43). The control is built now because it is part of
            // the screen's structure, not because it works.
            onSave = {},
            onDismiss = viewModel::onResultDismissed,
            modifier = Modifier.safeDrawingPadding(),
        )
    }
}
