package com.spotterkanji.app.scan

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Stage 2 of the scan pipeline: a photograph in, [RecognizedText] out.
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
     * Reads [bitmap] and flattens ML Kit's tree into text plus offsets.
     *
     * The bitmap is already upright — `ScanScreen` rotates it by the camera's
     * own `rotationDegrees` at capture — so the rotation passed here is 0. Handing
     * ML Kit a sideways image and a rotation of 0 is a silent accuracy failure
     * rather than an error: it still returns text, just much worse text.
     */
    suspend fun recognize(bitmap: Bitmap): RecognizedText =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            client.process(image)
                .addOnSuccessListener { result ->
                    continuation.resume(result.flatten())
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
 * Walks the `Text` → `TextBlock` → `Line` → `Element` tree in reading order,
 * building the single string stage 3 wants and recording where each element
 * landed in it.
 *
 * Reading order here is **ML Kit's own** block-then-line order, taken as given.
 * That is correct for ordinary horizontal text and is not yet correct for
 * vertical writing (縦書き), where columns run right to left — V-10, and Phase
 * 5's problem. Nothing is reordered here, so nothing has to be un-reordered
 * there.
 */
private fun com.google.mlkit.vision.text.Text.flatten(): RecognizedText {
    val builder = StringBuilder()
    val elements = mutableListOf<RecognizedElement>()

    for (block in textBlocks) {
        for (line in block.lines) {
            // Separator before each line except the very first, so the string
            // never begins or ends with one.
            if (builder.isNotEmpty()) builder.append(RecognizedText.LINE_SEPARATOR)
            for (element in line.elements) {
                val box = element.boundingBox
                // An element with no bounding box cannot be mapped back to the
                // photograph, so it is dropped rather than silently occupying
                // offsets that stage 4 could never resolve to a rectangle. ML
                // Kit documents the box as nullable; in practice it is always
                // present, which is exactly why the null branch needs to be
                // deliberate rather than a `!!`.
                if (box == null) continue
                elements += RecognizedElement(
                    text = element.text,
                    box = box,
                    startOffset = builder.length,
                )
                builder.append(element.text)
            }
        }
    }

    return RecognizedText(text = builder.toString(), elements = elements)
}
