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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotterkanji.app.nav.SpotterApp
import com.spotterkanji.app.scan.ScanScreen
import com.spotterkanji.app.scan.PeekContents
import com.spotterkanji.app.scan.RecognitionState
import com.spotterkanji.app.scan.ScanSheet
import com.spotterkanji.app.scan.ScanViewModel
import com.spotterkanji.app.scan.SheetStage
import com.spotterkanji.app.ui.theme.SpotterTheme
import com.spotterkanji.app.word.KanjiScreen
import com.spotterkanji.app.word.SaveToListSheet
import com.spotterkanji.app.word.ScanContext
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
 * Phase 2's text-input screen survives as a *test harness*, reached only by the
 * `query` **intent extra**, which opens it directly and bypasses the camera.
 * It is how every `V-##` case has been driven, so `/inspect` depends on it.
 *
 * It is no longer reachable by hand. The debug search button that used to sit
 * on the camera is gone: typing a word left the camera for Saved (D-86), and
 * the real search screen lives inside a list (D-96).
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
                    // The bottom nav now hosts the three real destinations
                    // (D-36, D-90), which is what the note that used to sit here
                    // was waiting for — it said the navigation decision should be
                    // made against three real destinations rather than pre-empted.
                    //
                    // The lookup harness is deliberately NOT one of them. It sits
                    // outside the shell and only an intent reaches it; back leaves
                    // the app, since a launch straight into it has nowhere to go
                    // back to.
                    if (seed != null) {
                        LookupRoute(seed = seed)
                    } else {
                        SpotterApp(
                            scanContent = { bottomBarHeight ->
                                ScanRoute(bottomBarHeight = bottomBarHeight)
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
    bottomBarHeight: Dp,
) {
    val scan: ScanViewModel = viewModel()
    val state by scan.state.collectAsStateWithLifecycle()

    // The dictionary half of the scan screen is the same ViewModel the Phase 2
    // route uses. Reusing it rather than duplicating a lookup inside
    // ScanViewModel is what makes the sheet's expansion nearly free: tokens,
    // alternates (D-70), the lone-kanji rule (D-49) and the in-place kanji swap
    // (D-32) are all already here, and the peek is simply the same selection
    // shown smaller.
    val words: WordLookupViewModel = viewModel()
    val wordState by words.state.collectAsStateWithLifecycle()
    val picker by words.picker.collectAsStateWithLifecycle()

    var stage by rememberSaveable { mutableStateOf(SheetStage.Peek) }

    // Segment the photograph as soon as it is read, so the first tap does not
    // pay for Kuromoji's dictionary load.
    val recognition = state.recognition
    LaunchedEffect(recognition) {
        if (recognition is RecognitionState.Done && !recognition.layout.isEmpty) {
            // autoSelect = false: the photograph is the query, and the word is
            // whichever one the user taps. Calling onSelectionCleared() after a
            // selecting call would not do — it cancels the lookup job, which is
            // the same coroutine still doing the tokenizing.
            words.onQueryChanged(recognition.layout.text, autoSelect = false)
            stage = SheetStage.Peek
        }
    }

    // The photo a filed word will keep (D-94). Handed over only once it has been
    // read, and withdrawn the moment the frame goes — a retake, or back to the
    // live viewfinder — so a word can never be filed against a photo that is no
    // longer the one on screen.
    val frame = state.frame
    LaunchedEffect(frame, recognition) {
        words.onScanContext(
            if (frame != null && recognition is RecognitionState.Done) {
                ScanContext(frame, recognition.layout)
            } else {
                null
            }
        )
    }

    val selected = wordState.selected
    val openKanji = wordState.openKanji

    /** One level of unwinding, in the order the user built the state up. */
    fun back() {
        when {
            openKanji != null -> words.onKanjiClosed()
            stage == SheetStage.Full -> stage = SheetStage.Peek
            selected != null -> words.onSelectionCleared()
            else -> scan.onRetake()
        }
    }

    // Back unwinds one level at a time rather than jumping to the viewfinder:
    // kanji screen → word screen → peek → frozen frame → camera. The freeze and
    // the sheet are states of this screen, not destinations (D-02, D-31), so
    // the system back button has no way to know any of this on its own.
    BackHandler(enabled = state.frame != null) { back() }

    ScanScreen(
        state = state,
        onShutterPressed = scan::onShutterPressed,
        onFrameCaptured = scan::onFrameCaptured,
        onCaptureFailed = scan::onCaptureFailed,
        onCameraUnavailable = scan::onCameraUnavailable,
        onCameraBound = scan::onCameraBound,
        onRetake = scan::onRetake,
        onOffsetTapped = { offset ->
            val token = offset?.let { at ->
                wordState.tokens.firstOrNull { at >= it.start && at < it.endExclusive }
            }
            if (token == null) {
                words.onSelectionCleared()
            } else {
                stage = SheetStage.Peek
                words.onTokenSelected(token)
            }
        },
        selection = selected?.let { it.start until it.endExclusive },
        bottomBarHeight = bottomBarHeight,
        sheet = {
            if (selected != null) {
                ScanSheet(
                    stage = stage,
                    onStageChanged = { stage = it },
                    onDismiss = words::onSelectionCleared,
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) { shown ->
                    when {
                        shown == SheetStage.Peek -> PeekContents(
                            word = selected.text,
                            glosses = wordState.saveTarget
                                ?.senses?.firstOrNull()?.glosses?.joinToString("; "),
                            loading = wordState.searching,
                            // Nothing to save while the lookup runs or when it
                            // found nothing (D-81).
                            canSave = wordState.saveTarget != null,
                            onSave = words::onSaveRequested,
                            onFullDetails = { stage = SheetStage.Full },
                        )

                        openKanji != null -> KanjiScreen(
                            detail = openKanji,
                            onBack = words::onKanjiClosed,
                            // Live as of D-92: kanji are study items in v1, and
                            // a lone scanned character lands here (D-49).
                            onSave = words::onSaveKanjiRequested,
                        )

                        else -> WordScreen(
                            state = wordState,
                            onQueryChanged = {},
                            onTokenSelected = words::onTokenSelected,
                            onKanjiSelected = words::onKanjiSelected,
                            onAlternateSelected = words::onAlternateSelected,
                            onSave = words::onSaveRequested,
                            onDismiss = { stage = SheetStage.Peek },
                            standalone = false,
                        )
                    }
                }
            }
        },
    )

    // Rendered outside the sheet on purpose. A Dialog draws in its own window,
    // so the picker sits above the peek sheet AND the frozen photograph rather
    // than being clipped to whichever one is on top.
    SaveToListSheet(
        word = picker.target?.key?.text.orEmpty(),
        state = picker,
        onDismiss = words::onPickerDismissed,
        onToggle = words::onPickerListToggled,
        onNewListStaged = words::onPickerNewListStaged,
        onNewListRemoved = words::onPickerNewListRemoved,
        onConfirm = words::onPickerConfirmed,
    )
}

/** Phase 2's screen, kept as the `/inspect` harness: type a word, see what it means. */
@Composable
private fun LookupRoute(seed: String?) {
    val viewModel: WordLookupViewModel = viewModel()
    // Once per composition, not once per recomposition — without the key the
    // seed would fight every keystroke the user makes.
    LaunchedEffect(seed) {
        seed?.let(viewModel::onQueryChanged)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
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
            onSave = viewModel::onSaveKanjiRequested,
            modifier = Modifier.safeDrawingPadding(),
        )
    } else {
        WordScreen(
            state = state,
            onQueryChanged = viewModel::onQueryChanged,
            onTokenSelected = viewModel::onTokenSelected,
            onKanjiSelected = viewModel::onKanjiSelected,
            onAlternateSelected = viewModel::onAlternateSelected,
            onSave = viewModel::onSaveRequested,
            onDismiss = viewModel::onResultDismissed,
            modifier = Modifier.safeDrawingPadding(),
        )
    }

    SaveToListSheet(
        word = picker.target?.key?.text.orEmpty(),
        state = picker,
        onDismiss = viewModel::onPickerDismissed,
        onToggle = viewModel::onPickerListToggled,
        onNewListStaged = viewModel::onPickerNewListStaged,
        onNewListRemoved = viewModel::onPickerNewListRemoved,
        onConfirm = viewModel::onPickerConfirmed,
    )
}
