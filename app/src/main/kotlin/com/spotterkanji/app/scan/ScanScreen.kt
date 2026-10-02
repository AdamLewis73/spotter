package com.spotterkanji.app.scan

import android.graphics.Bitmap
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.graphics.Matrix
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spotterkanji.app.R
import com.spotterkanji.app.ui.theme.SpotterTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The app's front door (D-61): a live viewfinder, one large shutter, and the
 * frozen frame the shutter produces (D-02).
 *
 * This file owns capture and freeze — the parts with the device-specific
 * failures in them. Everything done with the photograph lives elsewhere and is
 * handed in: recognition in `ScanViewModel`, the tappable overlay in
 * [ScanOverlay] (artboard 1a), and the peek sheet as [sheet].
 */
@Composable
internal fun ScanScreen(
    state: ScanUiState,
    onShutterPressed: () -> Unit,
    onFrameCaptured: (Bitmap) -> Unit,
    onCaptureFailed: () -> Unit,
    onCameraUnavailable: () -> Unit,
    onCameraBound: () -> Unit,
    onRetake: () -> Unit,
    onOffsetTapped: (Int?) -> Unit,
    selection: IntRange?,
    sheet: @Composable BoxScope.() -> Unit,
    /**
     * How far the app's own bottom navigation reaches up the screen (D-90).
     *
     * The viewfinder still fills the whole frame — the photograph should not be
     * letterboxed by chrome — but the shutter has to clear the bar, or the two
     * overlap at the bottom of the screen. So the image ignores this and the
     * controls do not, which is the same split D-33 already makes for the system
     * bars.
     */
    bottomBarHeight: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val permission = rememberCameraPermissionState()

    Box(
        modifier = modifier
            .fillMaxSize()
            // Black, not the theme ground. The chrome around a photograph should
            // not tint the user's sense of what they photographed, which is the
            // same reasoning D-67 gives for a warm-neutral palette elsewhere.
            .background(Color.Black),
    ) {
        when (permission.state) {
            CameraPermissionState.Granted -> CameraStage(
                state = state,
                onShutterPressed = onShutterPressed,
                onFrameCaptured = onFrameCaptured,
                onCaptureFailed = onCaptureFailed,
                onCameraUnavailable = onCameraUnavailable,
                onCameraBound = onCameraBound,
                onRetake = onRetake,
                onOffsetTapped = onOffsetTapped,
                selection = selection,
                sheet = sheet,
                bottomBarHeight = bottomBarHeight,
            )

            CameraPermissionState.Askable -> PermissionPanel(
                body = stringResource(R.string.scan_permission_body),
                actionLabel = stringResource(R.string.scan_permission_grant),
                onAction = permission.onRequest,
            )

            CameraPermissionState.Blocked -> PermissionPanel(
                body = stringResource(R.string.scan_permission_denied_body),
                actionLabel = stringResource(R.string.scan_permission_settings),
                onAction = permission.onOpenSettings,
            )
        }
    }
}

@OptIn(ExperimentalCamera2Interop::class) // the first-frame signal, below
@Composable
private fun CameraStage(
    state: ScanUiState,
    onShutterPressed: () -> Unit,
    onFrameCaptured: (Bitmap) -> Unit,
    onCaptureFailed: () -> Unit,
    onCameraUnavailable: () -> Unit,
    onCameraBound: () -> Unit,
    onRetake: () -> Unit,
    onOffsetTapped: (Int?) -> Unit,
    selection: IntRange?,
    sheet: @Composable BoxScope.() -> Unit,
    bottomBarHeight: Dp,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    // ---- Releasing the camera behind a frozen frame --------------------------
    //
    // A frozen frame keeps the camera running for [FROZEN_IDLE_MS], so the
    // common case — freeze, glance, retake — goes straight back to a live
    // picture. Past that the user is reading, not about to retake, and a
    // streaming camera nobody can see is the larger cost; the camera is
    // released entirely. The price is a rebind on the next Retake, a few
    // hundred milliseconds, and [heldFrame] covers it: the last photo stays up
    // until the first live frame arrives, then fades into it. Never black.

    // True once the frozen frame has sat long enough to release the camera.
    var idle by remember { mutableStateOf(false) }

    // True from the first frame after a bind until the unbind. Set by the
    // camera's own capture callback rather than by bindToLifecycle returning,
    // which happens well before any picture does.
    var streaming by remember { mutableStateOf(false) }

    // The last frozen frame, kept past a Retake to cover the camera's warm-up.
    var heldFrame by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(state.frame) {
        val frozen = state.frame
        idle = false
        if (frozen != null) {
            heldFrame = frozen
            delay(FROZEN_IDLE_MS)
            idle = true
        }
    }

    // Let go of the held photo once the live picture has faded in over it.
    LaunchedEffect(state.frame, streaming) {
        if (state.frame == null && streaming) {
            delay(HANDOVER_FADE_MS.toLong())
            heldFrame = null
        }
    }

    // Preview and ImageCapture are given the SAME aspect-ratio strategy, and both
    // are displayed with ContentScale.Crop. That pairing is what makes the
    // photograph a superset of what the user framed: crop-to-fill can hide part
    // of the captured image off-screen, but it can never capture *less* than was
    // shown. The other direction — framing text at the edge of the viewfinder and
    // finding it missing from the photo — is a bug the user cannot diagnose, and
    // it becomes a coordinate-mapping bug in Phase 5 rather than a visible one.
    val aspectRatio = remember { AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY }

    val imageCapture = remember {
        ImageCapture.Builder()
            // Quality over latency: this photograph exists to be read by OCR
            // rather than looked at, and the shutter is pressed once per scan.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspectRatio)
                    // 1920x1080 is a starting point, not a settled number. Small
                    // kanji on a sign photographed from across a street are
                    // exactly the case where more pixels help, and the ceiling is
                    // memory: a full-sensor 4000x3000 frame is ~48 MB as
                    // ARGB_8888 and will not survive a low-end device. Revisit
                    // once ML Kit is reading real signage and the accuracy cost
                    // of this cap can be measured rather than guessed at.
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1920, 1080),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .build()
    }

    LaunchedEffect(lifecycleOwner, idle) {
        // Idle: the previous run's `finally` has already unbound the camera,
        // and there is nothing to bind until Retake clears the flag.
        if (idle) return@LaunchedEffect

        val provider = try {
            ProcessCameraProvider.awaitInstance(context)
        } catch (e: Exception) {
            // Cancellation is not a camera fault — see the note on the bind
            // below, which is where this bit us.
            ensureActive()
            // Thrown on a device with no usable camera, and on an emulator
            // configured without one. Nothing here is recoverable.
            onCameraUnavailable()
            return@LaunchedEffect
        }

        // Cleared in `finally`, so a frame arriving from a camera already being
        // released cannot report the new binding as live.
        val bound = AtomicBoolean(true)
        val firstFrame = AtomicBoolean(false)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val previewBuilder = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder().setAspectRatioStrategy(aspectRatio).build(),
            )
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    // Every preview frame lands here, on the camera's thread.
                    // Only the first matters, and state is set on the main one.
                    if (firstFrame.compareAndSet(false, true)) {
                        mainExecutor.execute { if (bound.get()) streaming = true }
                    }
                }
            },
        )
        val preview = previewBuilder.build()
            .apply { setSurfaceProvider { request -> surfaceRequest = request } }

        try {
            try {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
                // Clears a failure left over from a previous attempt. Without
                // this a transient bind failure is permanent for the life of the
                // process, because the error lives in the ViewModel and nothing
                // else ever retracts it.
                onCameraBound()
            } catch (e: Exception) {
                // No live picture is coming, so the held photo must not stay up
                // pretending to be one.
                heldFrame = null
                onCameraUnavailable()
            }

            // `awaitCancellation` is deliberately OUTSIDE the catch above, and
            // this is not stylistic. Cancellation in Kotlin *is* an exception:
            // leaving the scan screen cancels this coroutine, `awaitCancellation`
            // throws `CancellationException`, and a `catch (Exception)` around it
            // swallows the very signal that means "we are shutting down
            // normally". The first version of this code did exactly that, and the
            // symptom was absurd enough to be worth recording — navigating away
            // and back showed "This device has no camera" printed on top of a
            // working live preview.
            awaitCancellation()
        } finally {
            // The binding is owned by this coroutine, so it lives exactly as long
            // as the composable does. This is what releases the camera when the
            // user navigates away — without it the hardware stays held and the
            // next bind fails on some devices with a bare "camera in use".
            provider.unbindAll()
            bound.set(false)
            streaming = false
            // The request belonged to the binding just released. The next bind
            // issues its own.
            surfaceRequest = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The viewfinder stays composed under a frozen frame rather than
        // leaving with it, so its surface survives and a Retake inside the idle
        // window is instant. The photo drawn over it is opaque.
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val frame = state.frame
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = stringResource(R.string.scan_frozen_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            heldFrame?.let { held ->
                // Fully opaque until the camera delivers, then a short fade.
                // After a quick Retake the camera never stopped, so this starts
                // at zero and nothing is shown at all.
                val heldAlpha by animateFloatAsState(
                    targetValue = if (streaming) 0f else 1f,
                    animationSpec = tween(HANDOVER_FADE_MS),
                    label = "handover",
                )
                Image(
                    bitmap = held.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(heldAlpha),
                )
            }
        }

        // The overlay sits on the photograph itself (artboard 1a). It is drawn
        // inside this Box, above the Image and below the controls, so the scrim
        // dims the picture without dimming the chrome.
        val recognition = state.recognition
        if (frame != null && recognition is RecognitionState.Done && !recognition.layout.isEmpty) {
            ScanOverlay(
                frame = frame.asImageBitmap(),
                layout = recognition.layout,
                selection = selection,
                onOffsetTapped = onOffsetTapped,
            )
        }

        // The camera stays bound behind a frozen frame only for
        // FROZEN_IDLE_MS, then is released — see the note at the top of this
        // function.

        sheet()

        ScanControls(
            frozen = frame != null,
            recognition = state.recognition,
            selectionActive = selection != null,
            capturing = state.capturing,
            onShutter = {
                onShutterPressed()
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            // `use` matters more than it looks: an ImageProxy
                            // holds a buffer from a small fixed pool, and leaking
                            // one means the *next* capture never fires and never
                            // errors either — it simply does nothing.
                            image.use { onFrameCaptured(it.toUprightBitmap()) }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            onCaptureFailed()
                        }
                    },
                )
            },
            onRetake = onRetake,
            bottomBarHeight = bottomBarHeight,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        val error = state.error
        if (error != null) {
            Text(
                text = when (error) {
                    ScanError.CaptureFailed -> stringResource(R.string.scan_capture_failed)
                    ScanError.CameraUnavailable -> stringResource(R.string.scan_no_camera)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(SpotterTheme.tokens.spaceLg)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(SpotterTheme.tokens.spaceMd),
            )
        }
    }
}

@Composable
private fun ScanControls(
    frozen: Boolean,
    recognition: RecognitionState,
    selectionActive: Boolean,
    capturing: Boolean,
    onShutter: () -> Unit,
    onRetake: () -> Unit,
    bottomBarHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // System bars first, then the app's own bar on top of them (D-90),
            // then the breathing room the shutter wants. Both insets are needed:
            // navigationBarsPadding alone puts the shutter behind the nav bar,
            // and the app bar alone puts it behind the gesture pill.
            .navigationBarsPadding()
            .padding(bottom = bottomBarHeight)
            .padding(bottom = SpotterTheme.tokens.spaceXl),
        contentAlignment = Alignment.Center,
    ) {
        if (frozen) {
            FrozenFrameControls(
                recognition = recognition,
                selectionActive = selectionActive,
                onRetake = onRetake,
            )
        } else {
            ShutterButton(enabled = !capturing, onClick = onShutter)
        }
    }
}

/**
 * What sits under a frozen frame while nothing is selected.
 *
 * Once a word is tapped the peek sheet takes the bottom of the screen and this
 * disappears: the words are on the photograph, so a strip repeating them would
 * be the clutter D-33 exists to avoid, and D-61 puts simplicity above showing
 * more. What remains here is the states the overlay cannot express — still
 * reading, unreadable, or nothing found.
 */
@Composable
private fun FrozenFrameControls(
    recognition: RecognitionState,
    selectionActive: Boolean,
    onRetake: () -> Unit,
) {
    // The sheet owns the bottom of the screen while a word is selected.
    if (selectionActive) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpotterTheme.tokens.spaceMd),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (recognition) {
            RecognitionState.Idle, RecognitionState.Running ->
                ReadingStrip(stringResource(R.string.scan_reading))

            RecognitionState.Failed ->
                ReadingStrip(stringResource(R.string.scan_recognize_failed))

            is RecognitionState.Done -> if (recognition.layout.isEmpty) {
                ReadingStrip(stringResource(R.string.scan_no_text))
            } else {
                // Text was found and is drawn on the photograph. The only thing
                // left to say is how to use it, once.
                ReadingStrip(stringResource(R.string.scan_tap_a_word))
            }
        }

        // Not a shutter. Retaking is a step backwards, and giving it the same big
        // round target would make the two states look identical at a glance while
        // doing opposite things.
        OutlinedButton(
            onClick = onRetake,
            modifier = Modifier.padding(top = SpotterTheme.tokens.spaceMd),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text(
                text = stringResource(R.string.scan_retake),
                modifier = Modifier.padding(start = SpotterTheme.tokens.spaceSm),
            )
        }
    }
}

@Composable
private fun ReadingStrip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.85f),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(
                horizontal = SpotterTheme.tokens.spaceMd,
                vertical = SpotterTheme.tokens.spaceSm,
            ),
    )
}

/**
 * Drawn rather than iconified.
 *
 * `ux.md` asks for a large shutter target reachable by thumb while the phone is
 * raised, and the ring-and-disc shutter is a shape every phone owner already
 * knows. An icon glyph would need a fixed size to stay crisp; a Canvas scales
 * with the token and costs no dependency.
 */
@Composable
private fun ShutterButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.scan_shutter)
    val alpha = if (enabled) 1f else 0.5f
    Canvas(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        val radius = size.minDimension / 2f
        drawCircle(
            color = Color.White.copy(alpha = 0.9f * alpha),
            radius = radius,
            style = Stroke(width = 4.dp.toPx()),
        )
        drawCircle(color = Color.White.copy(alpha = alpha), radius = radius - 8.dp.toPx())
    }
}

@Composable
private fun PermissionPanel(
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(SpotterTheme.tokens.spaceLg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.scan_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = SpotterTheme.tokens.spaceLg),
        )
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

/**
 * Turns a captured frame into a bitmap the right way up.
 *
 * `ImageProxy.toBitmap()` decodes the buffer exactly as the sensor produced it
 * and applies no rotation, so on a phone held upright the result is a landscape
 * image lying on its side. `rotationDegrees` is the camera's own account of how
 * far to turn it, which is why this reads that value rather than consulting the
 * display: it stays correct on a device whose sensor is mounted at an unusual
 * angle, and it needs no plumbing from the window.
 */
private fun ImageProxy.toUprightBitmap(): Bitmap {
    val raw = toBitmap()
    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return raw
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}

/**
 * How long a frozen frame keeps the camera running before it is released.
 *
 * Long enough for freeze, glance, retake — the case where a rebind would be
 * felt — and short against the minutes someone spends reading the peek sheet,
 * which is where a streaming camera nobody can see costs battery. Not measured
 * on a device yet; tune it there.
 */
private const val FROZEN_IDLE_MS = 10_000L

/** The crossfade from the held photo to the live picture after a rebind. */
private const val HANDOVER_FADE_MS = 200
