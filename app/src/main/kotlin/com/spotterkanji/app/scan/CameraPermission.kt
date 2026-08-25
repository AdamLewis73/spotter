package com.spotterkanji.app.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The three states a runtime permission can actually be in, as opposed to the
 * two the API appears to offer.
 *
 * `checkSelfPermission` answers granted or not granted. The third state —
 * *denied and no longer askable* — is not reported anywhere directly: it is
 * inferred from `shouldShowRequestPermissionRationale` returning false while
 * the permission is still not granted. Without that distinction the app either
 * shows an "Allow camera" button that does nothing when tapped, or sends every
 * first-time user to Settings for a permission it has not asked for yet.
 *
 * The inference has one wrinkle worth recording: before the *first* request,
 * `shouldShowRequestPermissionRationale` is also false, which is
 * indistinguishable from permanent denial. [hasAsked] resolves it — the app
 * knows whether it has asked, so it need not deduce it.
 */
internal enum class CameraPermissionState {
    Granted,

    /** Not granted, and the system dialog will still appear if we ask. */
    Askable,

    /**
     * Not granted, and asking is now a no-op. Only Settings can change it, so
     * this is the one state where sending the user out of the app is correct
     * rather than lazy.
     */
    Blocked,
}

/**
 * Tracks the camera permission across the app leaving and returning.
 *
 * The re-check on `ON_START` is the load-bearing part. When the user is
 * [CameraPermissionState.Blocked] the only route to granting is the system
 * Settings screen, which means leaving the app entirely — and on return nothing
 * recomposes by itself. Without this observer the user grants the permission,
 * comes back, and still sees "Camera access is turned off", which reads as the
 * app being broken rather than stale.
 */
@Composable
internal fun rememberCameraPermissionState(): CameraPermissionController {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var hasAsked by rememberSaveableAskedFlag()
    var state by remember { mutableStateOf(context.readCameraPermission(activity, hasAsked)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // The result boolean is deliberately ignored in favour of re-reading:
        // a denial has to be re-classified as Askable or Blocked, and only
        // `shouldShowRequestPermissionRationale` can tell those apart.
        hasAsked = true
        state = context.readCameraPermission(activity, hasAsked = true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                state = context.readCameraPermission(activity, hasAsked)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(state, activity) {
        CameraPermissionController(
            state = state,
            onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { activity?.openAppSettings() },
        )
    }
}

internal class CameraPermissionController(
    val state: CameraPermissionState,
    val onRequest: () -> Unit,
    val onOpenSettings: () -> Unit,
)

@Composable
private fun rememberSaveableAskedFlag() =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

private fun Context.readCameraPermission(
    activity: Activity?,
    hasAsked: Boolean,
): CameraPermissionState {
    val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) return CameraPermissionState.Granted

    // No Activity means no way to ask — treat it as askable rather than blocked
    // so the UI offers the button instead of sending the user to Settings for
    // something that may be fine.
    if (activity == null) return CameraPermissionState.Askable

    val canAskAgain = activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
    return when {
        canAskAgain -> CameraPermissionState.Askable
        // Never asked, and no rationale wanted: this is a first run, not a
        // permanent denial. The two look identical to the API.
        !hasAsked -> CameraPermissionState.Askable
        else -> CameraPermissionState.Blocked
    }
}

private fun Activity.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ),
    )
}

/**
 * Compose hands out a `Context`, not an `Activity`, and under a theme wrapper it
 * is a `ContextWrapper` several layers deep — so a cast straight to `Activity`
 * throws on exactly the setups that use a themed context.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
