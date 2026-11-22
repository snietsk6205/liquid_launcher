package dev.liquid.launcher.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardBandTest {
    @Test
    fun theBandIsTheBottomRowsTheKeyboardCovers() {
        // Med's phone: 1080 x 2388, the keyboard 933 px, a wallpaper as wide as the screen.
        assertEquals(KeyboardBand.Rect(0, 2388 - 933, 1080, 933), KeyboardBand.rect(1080, 2388, 1080, 2388, 933))
    }

    @Test
    fun aWiderCanvasGivesItsMiddleAndAnotherScaleScales() {
        // Parallax: 1.3 times as wide; the window is the middle one.
        assertEquals(KeyboardBand.Rect((1404 - 1080) / 2, 2388 - 933, 1080, 933), KeyboardBand.rect(1404, 2388, 1080, 2388, 933))
        // A canvas at half the screen's size: everything halves.
        assertEquals(KeyboardBand.Rect(0, 1194 - 467, 540, 467), KeyboardBand.rect(540, 1194, 1080, 2388, 933))
    }

    @Test
    fun nothingSensibleGivesNothing() {
        assertNull(KeyboardBand.rect(0, 2388, 1080, 2388, 933))
        assertNull(KeyboardBand.rect(1080, 2388, 1080, 2388, 0))
        // Taller than the screen: the whole height at most.
        assertEquals(KeyboardBand.Rect(0, 0, 1080, 2388), KeyboardBand.rect(1080, 2388, 1080, 2388, 5000))
    }

    @Test
    fun theLookSetsBlurAndDim() {
        assertEquals(0.4f, KeyboardBand.dim("search", 40), 1e-6f)
        assertEquals(24f, KeyboardBand.blur("search", 24f), 1e-6f)
        assertEquals(KeyboardBand.HOME_DIM, KeyboardBand.dim("home", 40), 1e-6f)
        assertEquals(0f, KeyboardBand.blur("home", 24f), 1e-6f)
    }
}
