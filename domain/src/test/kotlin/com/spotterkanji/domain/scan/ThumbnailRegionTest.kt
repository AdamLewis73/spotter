package com.spotterkanji.domain.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a word's thumbnail is cut from its photo (D-95).
 *
 * The failures here are silent in the usual way: a region that runs off the
 * photo does not crash, it decodes a strip of nothing or a shifted patch, and
 * the thumbnail simply looks wrong. The edges are where that happens, so most
 * cases are at the edges.
 */
class ThumbnailRegionTest {

    // A portrait photo, as a phone held normally produces once turned upright.
    private val w = 1080
    private val h = 1920

    private fun TextBox.inside(other: TextBox) =
        left >= other.left && top >= other.top && right <= other.right && bottom <= other.bottom

    @Test
    fun `a word in the middle gets a square with the word centred and a margin round it`() {
        val word = TextBox(500, 900, 600, 960) // 100 x 60
        val region = word.thumbnailRegion(w, h)!!

        assertEquals(region.width, region.height)
        assertEquals(150, region.width) // longer side 100, plus 25% each side
        assertTrue("the word must be inside its own thumbnail", word.inside(region))
        assertEquals(550, region.left + region.width / 2)
    }

    /** The case a naive centred crop gets wrong: it would start at a negative x. */
    @Test
    fun `a word at the left edge shifts the square inward rather than running off`() {
        val word = TextBox(0, 900, 80, 960)
        val region = word.thumbnailRegion(w, h)!!

        assertEquals(0, region.left)
        assertTrue(word.inside(region))
    }

    @Test
    fun `a word in the bottom right corner stays on the photo`() {
        val word = TextBox(1000, 1860, 1080, 1920)
        val region = word.thumbnailRegion(w, h)!!

        assertEquals(w, region.right)
        assertEquals(h, region.bottom)
        assertTrue(word.inside(region))
    }

    /**
     * A phrase wider than the photo is short — here, on a landscape photo — cannot
     * fit a square. The square is capped at the photo's short side and keeps the
     * phrase's middle, rather than stretching off the photo.
     */
    @Test
    fun `a word too wide for a square keeps its middle and never leaves the photo`() {
        val landscape = 1920 to 1080
        val phrase = TextBox(100, 500, 1800, 560)
        val region = phrase.thumbnailRegion(landscape.first, landscape.second)!!

        assertEquals(1080, region.width)
        assertTrue(region.left >= 0 && region.right <= landscape.first)
        assertTrue(region.top >= 0 && region.bottom <= landscape.second)
    }

    /** A vertical column (縦書き) is tall and thin; its longer side sets the square. */
    @Test
    fun `a tall vertical word is sized by its height`() {
        val column = TextBox(500, 600, 540, 800) // 40 x 200
        val region = column.thumbnailRegion(w, h)!!

        assertEquals(300, region.height)
        assertTrue(column.inside(region))
    }

    @Test
    fun `an unreadable photo gives no region`() {
        assertNull(TextBox(0, 0, 10, 10).thumbnailRegion(0, 0))
    }
}
