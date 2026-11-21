package dev.liquid.launcher.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPaletteTest {
    @Test
    fun rolesOfAPicture() {
        // Mostly a grey-blue sky, a bright orange patch, deep shadow and a little white.
        val pixels = IntArray(1000) { i ->
            when {
                i < 600 -> 0xFF6A7A8C.toInt()
                i < 750 -> 0xFFFF7A10.toInt()
                i < 950 -> 0xFF0E1014.toInt()
                else -> 0xFFF4F4F6.toInt()
            }
        }
        val p = WallpaperPalette.extract(pixels)
        assertNotNull(p)
        p!!
        assertEquals(0xFF6A7A8C.toInt() and 0xF0F0F0, p.dominant and 0xF0F0F0)
        assertEquals(0xFFFF7A10.toInt() and 0xF0F0F0, p.vibrant and 0xF0F0F0)
        assertTrue(WallpaperPalette.hsl(p.dark)[2] < 0.2f)
        assertTrue(WallpaperPalette.hsl(p.light)[2] > 0.85f)
        assertEquals(p, WallpaperPalette.decode(p.encode()))
    }

    @Test
    fun withoutAPictureTheRolesComeFromTheMainColour() {
        val p = WallpaperPalette.fromSeed(0xFF3366CC.toInt())
        assertTrue(WallpaperPalette.hsl(p.dark)[2] < 0.2f)
        assertTrue(WallpaperPalette.hsl(p.light)[2] > 0.8f)
        assertEquals(0xFF3366CC.toInt(), p.dominant)
    }

    @Test
    fun hslRoundTrip() {
        for (c in listOf(0xFF3366CC.toInt(), 0xFFFF7A10.toInt(), 0xFF808080.toInt(), 0xFF000000.toInt())) {
            val (h, s, l) = WallpaperPalette.hsl(c)
            val back = WallpaperPalette.fromHsl(h, s, l)
            for (shift in listOf(16, 8, 0)) {
                assertTrue(kotlin.math.abs(((c shr shift) and 0xFF) - ((back shr shift) and 0xFF)) <= 1)
            }
        }
    }
}
