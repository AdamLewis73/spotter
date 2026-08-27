package com.spotterkanji.app.scan

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import com.spotterkanji.app.data.DictionaryProvider
import com.spotterkanji.data.tokenize.KuromojiTokenizer
import com.spotterkanji.domain.scan.ScanLayout
import com.spotterkanji.domain.tokenize.Token
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the scan screen is showing: the live viewfinder, or a frozen frame.
 *
 * These are the two halves of D-02. Everything after the shutter happens on a
 * still image, so the frozen frame is a *state of the scan screen* rather than a
 * separate destination — there is no navigation between them and no back stack
 * entry, only this flag.
 */
internal data class ScanUiState(
    /**
     * The captured frame, or null while the viewfinder is live.
     *
     * Held in a ViewModel rather than in `remember` so that rotating the phone
     * does not throw the photo away. A `rememberSaveable` cannot serve here — a
     * multi-megabyte bitmap in the saved-state `Bundle` overruns the binder
     * transaction limit and takes the whole activity down with it.
     */
    val frame: Bitmap? = null,

    /** True between the shutter press and the frame arriving. */
    val capturing: Boolean = false,

    /** Stage 2's progress on [frame]. Meaningless while [frame] is null. */
    val recognition: RecognitionState = RecognitionState.Idle,

    /** Set when a capture fails, cleared when the message has been shown. */
    val error: ScanError? = null,

    /**
     * The recognized text, segmented. Empty until recognition finishes.
     *
     * Held for the whole frame rather than recomputed per tap: Kuromoji loads a
     * ~12 MB dictionary and the answer does not change while the photograph does
     * not.
     */
    val tokens: List<Token> = emptyList(),

    /** The word under the last tap, if any (D-30, D-31). */
    val peek: PeekState? = null,
)

/**
 * The peek sheet's contents — artboard 1a.
 *
 * **No reading** (D-47). The app would be guessing which one applies, and a
 * learner who already knew the reading would not be scanning the word. The
 * reading lives on the word screen, one drag further in.
 */
internal data class PeekState(
    val token: Token,
    /** Offsets to highlight on the photograph. */
    val selection: IntRange,
    /** Glosses of the first sense, joined — "teacher; instructor; master". */
    val glosses: String? = null,
    val loading: Boolean = true,
) {
    val text: String get() = token.text
    /** True once the lookup finished and found nothing. */
    val notFound: Boolean get() = !loading && glosses == null
}

/**
 * Where text recognition has got to on the frozen frame.
 *
 * [Done] carrying an empty [ScanLayout] is a real and common outcome, not a
 * failure — it is what happens when someone photographs a wall. It is kept
 * distinct from [Failed], which means the recognizer itself errored, because the
 * two want different words on screen and only one of them is worth retrying.
 */
internal sealed interface RecognitionState {
    data object Idle : RecognitionState
    data object Running : RecognitionState
    data class Done(val layout: ScanLayout) : RecognitionState
    data object Failed : RecognitionState
}

internal enum class ScanError { CaptureFailed, CameraUnavailable }

internal class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DictionaryProvider.repository(application)
    private val tokenizer = KuromojiTokenizer()
    private var peekJob: Job? = null

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    // Owned here rather than built per capture: it wraps a native client, and
    // creating one is not free. Living in the ViewModel also means it survives a
    // rotation, which a `remember` in the composable would not.
    private val recognizer = JapaneseTextRecognizer()
    private var recognitionJob: Job? = null

    fun onShutterPressed() {
        _state.update { it.copy(capturing = true, error = null) }
    }

    fun onFrameCaptured(frame: Bitmap) {
        _state.update {
            it.copy(
                frame = frame,
                capturing = false,
                error = null,
                recognition = RecognitionState.Running,
            )
        }
        recognize(frame)
    }

    private fun recognize(frame: Bitmap) {
        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            val next = try {
                // Off the main thread. ML Kit's own callback is asynchronous, but
                // the bitmap handling around it is not, and this runs on an image
                // several megapixels wide.
                withContext(Dispatchers.Default) { recognizer.recognize(frame) }
                    .let(RecognitionState::Done)
            } catch (e: CancellationException) {
                // A retake cancelled this. Rethrow rather than reporting a
                // recognizer failure — the same trap that made the camera claim
                // it did not exist (D-73).
                throw e
            } catch (_: Exception) {
                RecognitionState.Failed
            }
            _state.update { it.copy(recognition = next) }

            // Segment as soon as there is something to segment, so the first tap
            // does not pay for Kuromoji's dictionary load.
            if (next is RecognitionState.Done && !next.layout.isEmpty) {
                val tokens = withContext(Dispatchers.Default) {
                    tokenizer.tokenize(next.layout.text)
                }
                _state.update { it.copy(tokens = tokens) }
            }
        }
    }

    /**
     * A tap on the photograph, already resolved to a character offset.
     *
     * Null means the tap landed on bare image, which dismisses the peek — the
     * only way out of it other than Retake, and the reason a tap that resolves to
     * nothing must be reported rather than swallowed.
     */
    fun onOffsetTapped(offset: Int?) {
        peekJob?.cancel()

        val token = offset?.let { at ->
            _state.value.tokens.firstOrNull { at >= it.start && at < it.endExclusive }
        }
        if (token == null) {
            _state.update { it.copy(peek = null) }
            return
        }

        _state.update {
            it.copy(
                peek = PeekState(
                    token = token,
                    selection = token.start until token.endExclusive,
                ),
            )
        }

        peekJob = viewModelScope.launch {
            val glosses = withContext(Dispatchers.Default) {
                repository.lookup(token.text)
                    .firstOrNull()
                    ?.senses
                    ?.firstOrNull()
                    ?.glosses
                    ?.joinToString("; ")
            }
            _state.update { state ->
                // Only apply to the peek that asked for it. Tapping quickly along
                // a line otherwise lets a slow answer for one word overwrite a
                // newer answer for the next.
                if (state.peek?.token !== token) state
                else state.copy(peek = state.peek.copy(glosses = glosses, loading = false))
            }
        }
    }

    fun onPeekDismissed() {
        peekJob?.cancel()
        _state.update { it.copy(peek = null) }
    }

    fun onCaptureFailed() {
        _state.update { it.copy(capturing = false, error = ScanError.CaptureFailed) }
    }

    fun onCameraUnavailable() {
        _state.update { it.copy(capturing = false, error = ScanError.CameraUnavailable) }
    }

    /**
     * The camera bound successfully, so any earlier failure no longer describes
     * reality.
     *
     * Worth its own method rather than folding into some general "clear the
     * error": a [ScanError.CaptureFailed] is about one photograph and should
     * survive a rebind, while [ScanError.CameraUnavailable] is about the camera
     * itself and must not.
     */
    fun onCameraBound() {
        _state.update {
            if (it.error == ScanError.CameraUnavailable) it.copy(error = null) else it
        }
    }

    /** Back to the viewfinder. The frame is dropped, not stored (D-21 is Phase 6). */
    fun onRetake() {
        recognitionJob?.cancel()
        recognitionJob = null
        _state.update {
            it.copy(
                frame = null,
                capturing = false,
                error = null,
                recognition = RecognitionState.Idle,
                tokens = emptyList(),
                peek = null,
            )
        }
    }

    fun onErrorShown() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        recognizer.close()
    }
}
