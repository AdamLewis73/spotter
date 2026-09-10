package com.spotterkanji.app.saved

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spotterkanji.app.data.ScanImageStore
import com.spotterkanji.domain.scan.TextBox
import com.spotterkanji.domain.scan.thumbnailRegion
import com.spotterkanji.domain.user.ScanThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * A word's thumbnail: the part of its photo around the word (D-95).
 *
 * **Nothing is cropped on disk.** The photo is stored whole (D-94); this decodes
 * only the square around the word, in memory, for display. One file per shutter
 * press, however many words and thumbnails point into it.
 *
 * Draws an empty slot when there is no photo to show — no [thumbnail], or a file
 * that did not survive a restore. Both are normal states (D-94), so they look the
 * same as each other and neither looks like an error.
 */
@Composable
internal fun PhotoThumbnail(
    thumbnail: ScanThumbnail?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val context = LocalContext.current
    val targetPx = with(LocalDensity.current) { size.roundToPx() }
    val image by produceState<ImageBitmap?>(null, thumbnail?.imagePath, thumbnail?.box, targetPx) {
        value = thumbnail?.let {
            // Decoding a photo is disk and CPU work, and a list decodes one per
            // visible row as it scrolls; none of it belongs on the main thread.
            withContext(Dispatchers.IO) { decodeRegion(context, it, targetPx) }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * Decodes just the square around the word, no larger than the slot needs.
 *
 * Reads the photo's size from its header first — cheap, and it is how this code
 * learns the dimensions it deliberately does not store (D-94). The square comes
 * from [thumbnailRegion], which keeps it on the photo at the edges.
 *
 * The box needs no conversion: the photo was saved at the size it was taken and
 * the box was measured on that same photo. The only scaling here is decoding at a
 * reduced sample size, and that is computed from the file itself.
 */
internal fun decodeRegion(context: Context, thumbnail: ScanThumbnail, targetPx: Int): ImageBitmap? {
    val file = ScanImageStore.resolve(context, thumbnail.imagePath)
    if (!file.isFile) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val square = thumbnail.box.thumbnailRegion(bounds.outWidth, bounds.outHeight) ?: return null

    // Largest power of two that still leaves the square at least slot-sized, so a
    // word photographed small from far away is not decoded at full resolution.
    var sample = 1
    while (square.width / (sample * 2) >= targetPx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }

    val bitmap = decodeWithRegionDecoder(file.path, square, options)
        ?: decodeWholeAndCut(file.path, square, sample)
    return bitmap?.asImageBitmap()
}

/**
 * The efficient path: decode only the square, never the rest of the photo.
 * Returns null if this device's decoder cannot do regions of this format, and
 * the caller falls back.
 */
private fun decodeWithRegionDecoder(path: String, square: TextBox, options: BitmapFactory.Options): Bitmap? =
    try {
        val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(path)
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(path, false)
        }
        try {
            decoder.decodeRegion(square.toRect(), options)
        } finally {
            decoder.recycle()
        }
    } catch (e: IOException) {
        null
    }

/** The fallback: decode the whole photo at the sample size, then cut the square out in memory. */
private fun decodeWholeAndCut(path: String, square: TextBox, sample: Int): Bitmap? {
    val whole = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val x = (square.left / sample).coerceIn(0, whole.width - 1)
    val y = (square.top / sample).coerceIn(0, whole.height - 1)
    val side = (square.width / sample).coerceAtMost(minOf(whole.width - x, whole.height - y))
    return Bitmap.createBitmap(whole, x, y, side, side)
}

private fun TextBox.toRect() = Rect(left, top, right, bottom)
