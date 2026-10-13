package com.audiocast.app.image

import com.audiocast.app.data.FitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameGeometryTest {

    @Test
    fun `fit letterboxes a wide image in a landscape frame`() {
        // 2000x500 image into 1280x720 -> scaled to 1280x320, centered.
        val r = FrameGeometry.compute(2000, 500, 1280, 720, FitMode.FIT)
        assertEquals(IntRect(0, 0, 2000, 500), r.src)
        assertEquals(1280, r.dst.width)
        assertEquals(320, r.dst.height)
        assertEquals(0, r.dst.left)
        assertTrue(r.dst.top > 0)
        // vertically centered
        assertEquals(r.dst.top, 720 - r.dst.bottom)
    }

    @Test
    fun `fit pillarboxes a tall image in a landscape frame`() {
        // 500x2000 image into 1280x720 -> scaled by height.
        val r = FrameGeometry.compute(500, 2000, 1280, 720, FitMode.FIT)
        assertEquals(720, r.dst.height)
        assertEquals(180, r.dst.width)
        assertTrue(r.dst.left > 0)
        assertEquals(r.dst.left, 1280 - r.dst.right)
    }

    @Test
    fun `fit never exceeds the output frame`() {
        // Any image is permitted; result must stay inside the frame.
        listOf(
            intArrayOf(1, 1), intArrayOf(4000, 3000), intArrayOf(3000, 4000),
            intArrayOf(1280, 720), intArrayOf(17, 23), intArrayOf(9999, 2),
        ).forEach { (w, h) ->
            val r = FrameGeometry.compute(w, h, 1280, 720, FitMode.FIT)
            assertTrue(r.dst.left >= 0 && r.dst.top >= 0)
            assertTrue(r.dst.right <= 1280 && r.dst.bottom <= 720)
            assertTrue(r.dst.width > 0 && r.dst.height > 0)
        }
    }

    @Test
    fun `fit in a portrait frame`() {
        val r = FrameGeometry.compute(2000, 500, 720, 1280, FitMode.FIT)
        assertEquals(720, r.dst.width)
        assertEquals(180, r.dst.height)
        assertEquals(r.dst.top, 1280 - r.dst.bottom)
    }

    @Test
    fun `fill center crops a wide image`() {
        // 2000x500 into 1280x720 -> crop source to 16:9 then fill frame.
        val r = FrameGeometry.compute(2000, 500, 1280, 720, FitMode.FILL)
        assertEquals(IntRect(0, 0, 1280, 720), r.dst)
        assertEquals(500, r.src.height)
        assertEquals(889, r.src.width) // 500 * 16/9
        // horizontally centered crop (within 1px rounding)
        assertTrue(kotlin.math.abs(r.src.left - (2000 - r.src.right)) <= 1)
    }

    @Test
    fun `fill center crops a tall image`() {
        val r = FrameGeometry.compute(500, 2000, 1280, 720, FitMode.FILL)
        assertEquals(IntRect(0, 0, 1280, 720), r.dst)
        assertEquals(500, r.src.width)
        assertEquals(281, r.src.height)
        assertTrue(kotlin.math.abs(r.src.top - (2000 - r.src.bottom)) <= 1)
    }

    @Test
    fun `fill keeps square images intact in a square-equivalent sense`() {
        // 1000x1000 into 720x1280 -> crop width to the 9:16 ratio column.
        val r = FrameGeometry.compute(1000, 1000, 720, 1280, FitMode.FILL)
        assertEquals(IntRect(0, 0, 720, 1280), r.dst)
        assertEquals(1000, r.src.height)
        assertEquals(563, r.src.width)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero-size source`() {
        FrameGeometry.compute(0, 100, 1280, 720, FitMode.FIT)
    }
}
