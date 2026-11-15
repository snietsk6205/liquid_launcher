package dev.liquid.launcher.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the letter bubble goes relative to the finger (0.6.3, D91). */
class BubblePlaceTest {
    private val w = 120f
    private val h = 120f
    private val gap = 110f
    private val lift = 40f
    private val screen = 1080f

    @Test
    fun besideSitsInwardOfTheFingerAndALittleHigher_soTheThumbDoesNotCoverIt() {
        val right = bubbleCentre("beside", 900f, 1000f, true, w, h, gap, lift, screen)
        assertEquals(900f - gap - w / 2f, right.x, 0.01f)
        assertEquals(1000f - lift, right.y, 0.01f)
        // Its near edge is a full gap away from the finger.
        assertTrue(900f - (right.x + w / 2f) >= gap - 0.01f)

        val left = bubbleCentre("beside", 180f, 1000f, false, w, h, gap, lift, screen)
        assertEquals(180f + gap + w / 2f, left.x, 0.01f)
    }

    @Test
    fun besideGoesAboveWhenThereIsNoRoomInward() {
        // Finger pulled almost to the far edge: no room on the inner side.
        val c = bubbleCentre("beside", 120f, 1000f, true, w, h, gap, lift, screen)
        assertEquals(120f, c.x, 0.01f)
        assertEquals(1000f - gap - h / 2f, c.y, 0.01f)
    }

    @Test
    fun aboveFlipsBelowNearTheTop() {
        assertEquals(1000f - gap - h / 2f, bubbleCentre("above", 900f, 1000f, true, w, h, gap, lift, screen).y, 0.01f)
        assertEquals(60f + gap + h / 2f, bubbleCentre("above", 900f, 60f, true, w, h, gap, lift, screen).y, 0.01f)
    }

    @Test
    fun fingerKeepsTheOldPlace() {
        val c = bubbleCentre("finger", 900f, 1000f, true, w, h, gap, lift, screen)
        assertEquals(900f, c.x, 0.01f)
        assertEquals(1000f, c.y, 0.01f)
    }
}
