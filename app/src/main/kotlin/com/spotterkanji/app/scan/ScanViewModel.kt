package com.spotterkanji.app.scan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    /** Set when a capture fails, cleared when the message has been shown. */
    val error: ScanError? = null,
)

internal enum class ScanError { CaptureFailed, CameraUnavailable }

internal class ScanViewModel : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun onShutterPressed() {
        _state.update { it.copy(capturing = true, error = null) }
    }

    fun onFrameCaptured(frame: Bitmap) {
        _state.update { it.copy(frame = frame, capturing = false, error = null) }
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
        _state.update { it.copy(frame = null, capturing = false, error = null) }
    }

    fun onErrorShown() {
        _state.update { it.copy(error = null) }
    }
}
