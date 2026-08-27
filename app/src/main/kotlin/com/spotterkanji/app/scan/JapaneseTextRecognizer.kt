package com.spotterkanji.app.scan

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.spotterkanji.domain.scan.ScanFragment
import com.spotterkanji.domain.scan.ScanLayout
import com.spotterkanji.domain.scan.ScanLine
import com.spotterkanji.domain.scan.TextBox
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Stage 2 of the scan pipeline: a photograph in, a laid-out [ScanLayout] out.
 *
 * **The model is bundled into the APK, not fetched through Play Services**
 * (D-74). Both variants exist at the same version and expose this same API; the
 * bundled artifact simply depends on the unbundled one and adds the model, which
 * is why nothing in this file mentions the choice beyond this comment.
 *
 * Holds a native client, so it must be closed — see [close]. It is owned by
 * `ScanViewModel`, which outlives configuration changes, so the recognizer is
 * not rebuilt every time the phone rotates.
 */
internal class JapaneseTextRecognizer : Closeable {

    private val client = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

    /**
     * Reads [bitmap] and lays out what came back.
     *
     * The bitmap is already upright — `ScanScreen` rotates it by the camera's
     * own `rotationDegrees` at capture — so the rotation passed here is 0. Handing
     * ML Kit a sideways image and a rotation of 0 is a silent accuracy failure
     * rather than an error: it still returns text, just much worse text.
     */
    suspend fun recognize(bitmap: Bitmap): ScanLayout =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            client.process(image)
                .addOnSuccessListener { result ->
                    continuation.resume(ScanLayout.of(result.toScanLines()))
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }

    override fun close() {
        client.close()
    }
}

/**
 * Converts ML Kit's `Text` → `TextBlock` → `Line` → `Element` tree into the
 * portable input `:domain` lays out. **Nothing here decides anything.**
 *
 * That division is deliberate and is what D-76 buys. Reading order, writing
 * direction, ruby and line breaks are all judgement calls with subtle failure
 * modes, and they live in `:domain` where a case costs 19 seconds to test
 * instead of an emulator round trip. This file only changes types.
 *
 * In particular ML Kit's **order is not preserved as meaningful** — it walks
 * 縦書き columns left to right, which is backwards (D-75) — but its **grouping
 * is**, because that was measured to be sound. Each ML Kit `Line` becomes one
 * [ScanLine], and `ScanLayout` sorts them.
 */
private fun com.google.mlkit.vision.text.Text.toScanLines(): List<ScanLine> =
    textBlocks.flatMap { block ->
        block.lines.mapNotNull { line ->
            val fragments = line.elements.mapNotNull { element ->
                // An element with no bounding box cannot be mapped back to the
                // photograph, so it is dropped rather than silently occupying
                // offsets that could never resolve to a rectangle. ML Kit
                // documents the box as nullable; in practice it is always
                // present, which is exactly why the null branch needs to be
                // deliberate rather than a `!!`.
                val box = element.boundingBox ?: return@mapNotNull null
                if (element.text.isEmpty()) return@mapNotNull null
                ScanFragment(element.text, box.toTextBox())
            }
            if (fragments.isEmpty()) null else ScanLine(fragments)
        }
    }

/**
 * Normalised so that a box arriving inside-out cannot crash the scan screen.
 *
 * `TextBox` requires its edges to be the right way round, and a recognizer
 * returning otherwise would take the app down at exactly the moment someone is
 * standing in front of a sign. Straightening it costs two comparisons.
 */
private fun Rect.toTextBox() = TextBox(
    left = minOf(left, right),
    top = minOf(top, bottom),
    right = maxOf(left, right),
    bottom = maxOf(top, bottom),
)
