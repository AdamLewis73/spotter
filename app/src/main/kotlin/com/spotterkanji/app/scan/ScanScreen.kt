package com.spotterkanji.app.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spotterkanji.app.R
import com.spotterkanji.app.ui.theme.SpotterTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive

/**
 * The app's front door (D-61): a live viewfinder, one large shutter, and the
 * frozen frame the shutter produces (D-02).
 *
 * **This is Phase 4 step one and deliberately does nothing with the photograph.**
 * There is no text recognition here, no tokenizing, and no overlay — capture and
 * freeze are the parts with the device-specific failures in them, and they are
 * worth having working alone before anything downstream can be blamed for them.
 * ML Kit arrives next; the tappable overlay over the frozen frame is Phase 5,
 * drawn to design artboard 1a.
 *
 * @param onOpenLookup route to the Phase 2 text-input screen, or null to hide it.
 *   Non-null only in debug builds — see `MainActivity`.
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
    onOpenLookup: (() -> Unit)?,
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

        // Debug only, and in the top corner on purpose: `ux.md` keeps important
        // controls out of the top corners because the phone is held up one-handed
        // while scanning — which makes a corner exactly the right place for a
        // control that is not important.
        if (onOpenLookup != null) {
            IconButton(
                onClick = onOpenLookup,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(SpotterTheme.tokens.spaceSm),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.scan_open_lookup),
                    tint = Color.White.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun CameraStage(
    state: ScanUiState,
    onShutterPressed: () -> Unit,
    onFrameCaptured: (Bitmap) -> Unit,
    onCaptureFailed: () -> Unit,
    onCameraUnavailable: () -> Unit,
    onCameraBound: () -> Unit,
    onRetake: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

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

    LaunchedEffect(lifecycleOwner) {
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

        val preview = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder().setAspectRatioStrategy(aspectRatio).build(),
            )
            .build()
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
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val frame = state.frame
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = stringResource(R.string.scan_frozen_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            surfaceRequest?.let { request ->
                CameraXViewfinder(
                    surfaceRequest = request,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // The camera stays bound while a frame is frozen rather than being
        // unbound and rebound around it. Rebinding costs a few hundred
        // milliseconds, and right now a frozen frame has nothing on it to read,
        // so every freeze is followed almost immediately by a retake and that
        // latency is the thing a user would notice. Worth revisiting in Phase 5,
        // when the peek sheet gives people a reason to sit on a frozen frame for
        // minutes and the battery cost becomes the larger of the two.

        ScanControls(
            frozen = frame != null,
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
    capturing: Boolean,
    onShutter: () -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = SpotterTheme.tokens.spaceXl),
        contentAlignment = Alignment.Center,
    ) {
        if (frozen) {
            // Not a shutter. Retaking is a step backwards, and giving it the same
            // big round target would make the two states look identical at a
            // glance while doing opposite things.
            OutlinedButton(onClick = onRetake) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text(
                    text = stringResource(R.string.scan_retake),
                    modifier = Modifier.padding(start = SpotterTheme.tokens.spaceSm),
                )
            }
        } else {
            ShutterButton(enabled = !capturing, onClick = onShutter)
        }
    }
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
