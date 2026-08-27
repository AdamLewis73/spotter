package com.spotterkanji.domain.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The image-to-view transform, tested apart from the interpolation.
 *
 * They are separate bugs that look identical on screen — both read as "taps are
 * slightly off" — so testing them together would mean never knowing which one
 * was wrong. These cases use no `ScanLayout` at all.
 */
class ScanProjectionTest {

    private val tolerance = 0.0001

    /**
     * A wide photograph in a tall view: crop scales by **width**, so the image
     * overflows vertically and the vertical offset goes negative.
     *
     * The naive `viewHeight / imageHeight` would give 2.0 here rather than 4.0 —
     * a 2× error in every coordinate, which is the failure this type exists to
     * make testable.
     */
    @Test
    fun a_wide_image_in_a_tall_view_is_cropped_top_and_bottom() {
        val p = ScanProjection.crop(
            imageWidth = 100, imageHeight = 100,
            viewWidth = 400.0, viewHeight = 200.0,
        )
        assertEquals(4.0, p.scale, tolerance)
        assertEquals(0.0, p.offsetX, tolerance)
        assertEquals(-100.0, p.offsetY, tolerance)
    }

    @Test
    fun a_tall_image_in_a_wide_view_is_cropped_left_and_right() {
        val p = ScanProjection.crop(
            imageWidth = 100, imageHeight = 100,
            viewWidth = 200.0, viewHeight = 400.0,
        )
        assertEquals(4.0, p.scale, tolerance)
        assertEquals(-100.0, p.offsetX, tolerance)
        assertEquals(0.0, p.offsetY, tolerance)
    }

    @Test
    fun matching_aspect_ratios_need_no_cropping() {
        val p = ScanProjection.crop(
            imageWidth = 1920, imageHeight = 1080,
            viewWidth = 960.0, viewHeight = 540.0,
        )
        assertEquals(0.5, p.scale, tolerance)
        assertEquals(0.0, p.offsetX, tolerance)
        assertEquals(0.0, p.offsetY, tolerance)
    }

    /**
     * The centre of the photograph lands at the centre of the view, whatever the
     * aspect mismatch. Crop is centre-aligned, and getting that wrong shifts
     * every tap by half the overflow.
     */
    @Test
    fun the_centre_of_the_image_lands_at_the_centre_of_the_view() {
        val p = ScanProjection.crop(4000, 3000, viewWidth = 1080.0, viewHeight = 2400.0)
        assertEquals(540.0, p.toScreenX(2000), tolerance)
        assertEquals(1200.0, p.toScreenY(1500), tolerance)
    }

    /**
     * A tap maps back to the pixel it came from. This is the property the
     * overlay actually depends on, so it is asserted directly rather than
     * inferred from the scale.
     */
    @Test
    fun screen_coordinates_round_trip_back_to_image_coordinates() {
        val p = ScanProjection.crop(1920, 1080, viewWidth = 1080.0, viewHeight = 2160.0)
        for (x in listOf(0, 1, 640, 1919)) {
            assertEquals(x.toDouble(), p.toImageX(p.toScreenX(x)), tolerance)
        }
        for (y in listOf(0, 1, 540, 1079)) {
            assertEquals(y.toDouble(), p.toImageY(p.toScreenY(y)), tolerance)
        }
    }

    /**
     * Cropping really does put part of the photograph outside the view — the
     * property that makes the frame a superset of the viewfinder, and the reason
     * a tap can be off the image entirely.
     */
    @Test
    fun cropped_pixels_fall_outside_the_view() {
        // A square photograph in a tall, narrow view. To cover, it scales to the
        // view's *height*, which makes it far too wide — so the crop happens at
        // the left and right, and the full height is visible.
        val p = ScanProjection.crop(1000, 1000, viewWidth = 100.0, viewHeight = 400.0)
        assertTrue("the left of the image should be off-view", p.toScreenX(0) < 0)
        assertTrue("the right of the image should be off-view", p.toScreenX(1000) > 100)
        assertEquals(0.0, p.toScreenY(0), tolerance)
        assertEquals(400.0, p.toScreenY(1000), tolerance)
    }

    @Test
    fun a_box_projects_as_a_whole() {
        val p = ScanProjection.crop(200, 200, viewWidth = 400.0, viewHeight = 400.0)
        val rect = p.project(TextBox(10, 20, 30, 40))
        assertEquals(20.0, rect.left, tolerance)
        assertEquals(40.0, rect.top, tolerance)
        assertEquals(60.0, rect.right, tolerance)
        assertEquals(80.0, rect.bottom, tolerance)
        assertEquals(40.0, rect.width, tolerance)
    }

    /**
     * A view that has not been measured yet is the normal first composition, not
     * an error. It must not divide by zero or scale everything to nothing.
     */
    @Test
    fun an_unmeasured_view_falls_back_to_identity() {
        assertEquals(ScanProjection.IDENTITY, ScanProjection.crop(100, 100, 0.0, 0.0))
        assertEquals(ScanProjection.IDENTITY, ScanProjection.crop(0, 0, 100.0, 100.0))
    }
}
