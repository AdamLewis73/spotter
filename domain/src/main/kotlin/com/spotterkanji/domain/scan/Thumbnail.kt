package com.spotterkanji.domain.scan

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The square of a photo to draw as a word's thumbnail (D-95): this word, with a
 * margin of the photo around it, never running off the photo's edge.
 *
 * Nothing is cut out of the file — the photo is kept whole (D-94) and this only
 * says which part of it to *show*.
 *
 * Square because the thumbnail slot is square. Centred on the word, then shifted
 * inward if that would leave the photo: a word at the very edge of a sign is
 * exactly where a naive crop runs off the image, and the shift keeps the word in
 * view instead of shrinking the square around it.
 *
 * [margin] is the share of the word's longer side added on **each** side, so the
 * word is recognisably a word on something rather than a patch of pixels.
 *
 * A word wider than the whole photo is tall — a long horizontal phrase on a
 * portrait photo — cannot fit a square, and keeps its middle. For a thumbnail
 * that is the right trade; the whole photo is still there to open.
 *
 * Returns null for a photo with no size, which is a corrupt or unreadable file.
 */
fun TextBox.thumbnailRegion(
    imageWidth: Int,
    imageHeight: Int,
    margin: Float = 0.25f,
): TextBox? {
    if (imageWidth <= 0 || imageHeight <= 0) return null

    val wanted = (max(width, height) * (1 + 2 * margin)).roundToInt()
    val side = wanted.coerceIn(1, min(imageWidth, imageHeight))

    val centreX = left + width / 2
    val centreY = top + height / 2
    val x = (centreX - side / 2).coerceIn(0, imageWidth - side)
    val y = (centreY - side / 2).coerceIn(0, imageHeight - side)

    return TextBox(left = x, top = y, right = x + side, bottom = y + side)
}
