package dev.liquid.launcher.feature.redirect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.10.4: the going-home animation is told by the home screen being smaller than the screen and centred on it. */
class RecentsDetectorTest {

    private fun shrunk(l: Int, t: Int, r: Int, b: Int, w: Int = 1080, h: Int = 2388) = RecentsDetector.shrunkInScreen(l, t, r, b, w, h)

    @Test
    fun magicOsGoingHomeIsSeen() {
        // Med's log, 2026-11-20: the home screen at the first look while going home, and near the end of the animation.
        assertTrue(shrunk(54, 119, 1026, 2269))
        assertTrue(shrunk(53, 117, 1027, 2271))
        assertTrue(shrunk(22, 49, 1058, 2339))
    }

    @Test
    fun aHomeScreenAtRestOrUnderRecentsIsNot() {
        assertFalse(shrunk(0, 0, 1080, 2388))
    }

    @Test
    fun aWindowThatLeavesRoomForTheSystemBarsIsNot() {
        assertFalse(shrunk(0, 0, 1080, 2262))
        assertFalse(shrunk(0, 63, 1080, 2262))
        assertFalse(shrunk(0, 0, 780, 2388))
    }

    @Test
    fun aSmallerHomeScreenOffTheMiddleIsNot() {
        assertFalse(shrunk(0, 0, 972, 2149))
    }

    @Test
    fun landscapeWorksTheSameWay() {
        assertTrue(shrunk(119, 54, 2269, 1026, w = 2388, h = 1080))
        assertFalse(shrunk(0, 0, 2388, 1080, w = 2388, h = 1080))
    }

    @Test
    fun noScreenSizeMeansNoAnswer() {
        assertFalse(shrunk(54, 119, 1026, 2269, w = 0, h = 0))
    }
}
