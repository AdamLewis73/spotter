package com.spotterkanji.domain.scan

/**
 * A rectangle in **view** coordinates, in whatever unit the caller measured the
 * view in. Fractional, because a projection rarely lands on whole pixels.
 */
data class ScreenRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
}

/**
 * Maps image pixels to the view the photograph is drawn in, and back.
 *
 * **This is a separate bug from the interpolation, and on screen they look
 * identical.** Both present as "taps are slightly off", so both
 * `phase-04-camera.md` and `architecture.md` insist this be derived from the
 * *measured* view size and tested apart from `ScanLayout`. That is why it is a
 * value type in `:domain` with its own cases rather than arithmetic inlined into
 * a composable, where checking it would cost an emulator round trip.
 *
 * [crop] models `ContentScale.Crop`, which is what the frozen frame is drawn
 * with. Crop is **not** a plain scale: the image is enlarged until it covers the
 * view in both axes and the overflow is clipped, so part of the photograph is
 * off-screen and the offsets are negative. A naive `viewWidth / imageWidth`
 * scale is right only in the accidental case where the aspect ratios already
 * agree — which is exactly how this bug survives casual testing.
 */
data class ScanProjection(
    val scale: Double,
    val offsetX: Double,
    val offsetY: Double,
) {
    fun toScreenX(imageX: Number): Double = imageX.toDouble() * scale + offsetX
    fun toScreenY(imageY: Number): Double = imageY.toDouble() * scale + offsetY

    fun toImageX(screenX: Number): Double = (screenX.toDouble() - offsetX) / scale
    fun toImageY(screenY: Number): Double = (screenY.toDouble() - offsetY) / scale

    fun project(box: TextBox) = ScreenRect(
        left = toScreenX(box.left),
        top = toScreenY(box.top),
        right = toScreenX(box.right),
        bottom = toScreenY(box.bottom),
    )

    companion object {
        /** A projection that changes nothing, for empty or degenerate input. */
        val IDENTITY = ScanProjection(scale = 1.0, offsetX = 0.0, offsetY = 0.0)

        /**
         * The transform `ContentScale.Crop` applies, with centre alignment.
         *
         * Scale is the **larger** of the two ratios so the image covers the view
         * — the defining property of crop, and the opposite of `Fit`, which
         * takes the smaller and letterboxes. The result is then centred, which
         * is where the negative offsets come from.
         */
        fun crop(
            imageWidth: Int,
            imageHeight: Int,
            viewWidth: Double,
            viewHeight: Double,
        ): ScanProjection {
            if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
                return IDENTITY
            }
            val scale = maxOf(viewWidth / imageWidth, viewHeight / imageHeight)
            return ScanProjection(
                scale = scale,
                offsetX = (viewWidth - imageWidth * scale) / 2.0,
                offsetY = (viewHeight - imageHeight * scale) / 2.0,
            )
        }
    }
}
