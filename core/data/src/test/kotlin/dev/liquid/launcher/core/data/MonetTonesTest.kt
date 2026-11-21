package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.11.0: Material You's colour maths, checked against the values material-color-utilities' own tests use. */
class MonetTonesTest {
    @Test
    fun cam16OfThePrimariesMatchesMaterialColorUtilities() {
        val red = Cam16.fromArgb(0xFFFF0000.toInt())
        assertEquals(27.408, red.hue, 0.01)
        assertEquals(113.357, red.chroma, 0.01)
        assertEquals(46.445, red.j, 0.01)
        val green = Cam16.fromArgb(0xFF00FF00.toInt())
        assertEquals(142.139, green.hue, 0.01)
        assertEquals(108.410, green.chroma, 0.01)
        assertEquals(79.331, green.j, 0.01)
        val blue = Cam16.fromArgb(0xFF0000FF.toInt())
        assertEquals(282.788, blue.hue, 0.01)
        assertEquals(87.230, blue.chroma, 0.01)
        assertEquals(25.465, blue.j, 0.01)
    }

    @Test
    fun toneIsLStar() {
        assertEquals(53.233, MonetTones.lstarFromArgb(0xFFFF0000.toInt()), 0.01)
        assertEquals(87.737, MonetTones.lstarFromArgb(0xFF00FF00.toInt()), 0.01)
        assertEquals(32.302, MonetTones.lstarFromArgb(0xFF0000FF.toInt()), 0.01)
    }

    @Test
    fun aColourOfHueChromaAndToneHasThatToneAndKeepsItsHue() {
        for (hue in listOf(10.0, 95.0, 160.0, 220.0, 282.0, 330.0)) {
            for (tone in listOf(20.0, 30.0, 50.0, 90.0)) {
                val argb = MonetTones.fromHct(hue, 36.0, tone)
                val (h, c, t) = MonetTones.hct(argb)
                assertEquals("tone of hue $hue at $tone", tone, t, 0.6)
                assertTrue("chroma of hue $hue at $tone: $c", c <= 37.5)
                // Near the edge of sRGB the search lets the hue slip by a few degrees (a hardly visible difference).
                if (c > 10.0) assertEquals("hue $hue at tone $tone", hue, h, 4.0)
            }
        }
    }

    @Test
    fun iconColoursAreTheTonalSpotShades() {
        val seed = 0xFF3F9B6E.toInt()
        val (plate, glyph) = MonetTones.iconColours(seed, dark = false)
        assertEquals(90.0, MonetTones.lstarFromArgb(plate), 0.6)
        assertEquals(30.0, MonetTones.lstarFromArgb(glyph), 0.6)
        val (darkPlate, darkGlyph) = MonetTones.iconColours(seed, dark = true)
        assertEquals(20.0, MonetTones.lstarFromArgb(darkPlate), 0.6)
        assertEquals(90.0, MonetTones.lstarFromArgb(darkGlyph), 0.6)
        // Neutral 1 is nearly grey; accent 1 has colour.
        assertTrue(MonetTones.hct(darkPlate).second < 8.0)
        assertTrue(MonetTones.hct(glyph).second > 25.0)
    }
}
