package dev.liquid.launcher.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the letter bubble goes (0.6.3, D91) and how far the letters bend out from under the finger (0.6.4, D97). */
class BubblePlaceTest {
    private val w = 120f
    private val h = 120f
    private val gap = 90f
    private val aboveGap = 110f
    private val screen = 1080f

    @Test
    fun besideSitsPastTheLetters_towardsTheMiddle() {
        // The letter under the finger rides the apex; its inner edge is at 800.
        val right = bubbleCentre("beside", 1050f, 1000f, 800f, true, w, h, gap, aboveGap, 0f, screen)
        assertEquals(800f - gap - w / 2f, right.x, 0.01f)
        assertEquals(1000f, right.y, 0.01f)
        // Its near edge is a full gap away from the letters.
        assertTrue(800f - (right.x + w / 2f) >= gap - 0.01f)

        val left = bubbleCentre("beside", 30f, 1000f, 280f, false, w, h, gap, aboveGap, 0f, screen)
        assertEquals(280f + gap + w / 2f, left.x, 0.01f)
    }

    @Test
    fun besideGoesAboveWhenThereIsNoRoomInward() {
        // Letters pulled almost to the far edge: no room on the inner side.
        val c = bubbleCentre("beside", 400f, 1000f, 150f, true, w, h, gap, aboveGap, 0f, screen)
        assertEquals(400f, c.x, 0.01f)
        assertEquals(1000f - aboveGap - h / 2f, c.y, 0.01f)
    }

    @Test
    fun aboveFlipsBelowNearTheTop() {
        assertEquals(1000f - aboveGap - h / 2f, bubbleCentre("above", 900f, 1000f, 0f, true, w, h, gap, aboveGap, 0f, screen).y, 0.01f)
        assertEquals(60f + aboveGap + h / 2f, bubbleCentre("above", 900f, 60f, 0f, true, w, h, gap, aboveGap, 0f, screen).y, 0.01f)
    }

    @Test
    fun fingerKeepsTheOldPlace() {
        val c = bubbleCentre("finger", 900f, 1000f, 0f, true, w, h, gap, aboveGap, 0f, screen)
        assertEquals(900f, c.x, 0.01f)
        assertEquals(1000f, c.y, 0.01f)
    }

    @Test
    fun theLettersBendOutFromUnderTheFinger_evenBeforeItMovesIn() {
        // Finger on the edge strip (slightly outside the resting line): the bend is the gap alone.
        assertEquals(190f, pullFor(inward = -10f, letterGap = 190f, track = 1f, maxPull = 900f), 0.01f)
        // Pulled 200 px in: the letters keep their distance from the finger.
        assertEquals(390f, pullFor(200f, 190f, 1f, 900f), 0.01f)
        // Half tracking follows half as far; the cap holds.
        assertEquals(290f, pullFor(200f, 190f, 0.5f, 900f), 0.01f)
        assertEquals(900f, pullFor(5000f, 190f, 1f, 900f), 0.01f)
        // 0: the letter under the finger comes to the finger.
        assertEquals(200f, pullFor(200f, 0f, 1f, 900f), 0.01f)
    }
}
