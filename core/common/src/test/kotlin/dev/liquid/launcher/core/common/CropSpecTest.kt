package dev.liquid.launcher.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class CropSpecTest {
    @Test
    fun centreCoversCanvasWithShortSide() {
        // A landscape picture on a portrait canvas: full height, centred horizontally.
        val f = CropSpec.Centre.frame(4000, 3000, 1080f, 2400f)
        assertEquals(3000f, f.height, 0.5f)
        assertEquals(3000f * 1080f / 2400f, f.width, 0.5f)
        assertEquals((4000f - f.width) / 2f, f.left, 0.5f)
        assertEquals(0f, f.top, 0.01f)
    }

    @Test
    fun zoomShrinksFrameAndCentreIsClamped() {
        val f = CropSpec(cx = 0f, cy = 0.5f, zoom = 2f).frame(4000, 3000, 1080f, 2400f)
        assertEquals(1500f, f.height, 0.5f)
        // Asked for the very left edge: the frame stops at the picture's edge.
        assertEquals(0f, f.left, 0.01f)
    }

    @Test
    fun dragMovesContentWithFinger() {
        val start = CropSpec.Centre
        val before = start.frame(4000, 3000, 1080f, 2400f)
        // Finger moves right by 100 canvas px: the picture follows, so the frame moves left.
        val moved = start.moved(4000, 3000, 1080f, 2400f, 100f, 0f, 1f)
        val after = moved.frame(4000, 3000, 1080f, 2400f)
        val pxPerCanvas = before.width / 1080f
        assertEquals(before.left - 100f * pxPerCanvas, after.left, 0.5f)
    }

    @Test
    fun zoomIsLimited() {
        val z = CropSpec.Centre.moved(4000, 3000, 1080f, 2400f, 0f, 0f, 100f)
        assertEquals(CropSpec.MAX_ZOOM, z.zoom, 0.001f)
        val out = CropSpec.Centre.moved(4000, 3000, 1080f, 2400f, 0f, 0f, 0.1f)
        assertEquals(1f, out.zoom, 0.001f)
    }

    @Test
    fun parallaxCanvasIsWider() {
        val still = CropSpec.Centre.frame(4000, 3000, 1080f, 2400f)
        val wide = CropSpec.Centre.frame(4000, 3000, 1080f * 1.3f, 2400f)
        assertEquals(still.height, wide.height, 0.5f)
        assertEquals(still.width * 1.3f, wide.width, 1f)
    }
}
