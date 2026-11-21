package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.common.WallpaperPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardThemeTest {
    private val palette = WallpaperPalette(
        dominant = 0xFF2E312A.toInt(), vibrant = 0xFFC8EEA8.toInt(), muted = 0xFF6A7060.toInt(),
        dark = 0xFF101210.toInt(), light = 0xFFF0F4EA.toInt(),
    )

    private fun doc(): ThemeDoc = ThemeDoc.parse(
        """{"id":"custom_1","name":"wallpaper stoped","dark":true,"boardBackground":1381915,"keyBackground":472657461,
           "keyText":4293519854,"accent":4285362036,"backgroundPhoto":{"provider":"unsplash"},
           "frost":{"enabled":true,"windowBlurRadiusDp":4},
           "liquidLauncher":{"follow":{"accent":"vibrant","keyBackground":"dominant"}}}""",
    )!!

    @Test
    fun followedColoursAreWrittenOutKeepingTheirOpacity() {
        val out = KeyboardTheme.build(doc(), palette, "Liquid", 1_793_966_705_811)
        // accent was opaque: it becomes the wallpaper's vibrant colour.
        assertEquals(0xFFC8EEA8L, out.color("accent"))
        // keyBackground kept its own alpha (0x1C) with the dominant colour.
        assertEquals((472657461L ushr 24 shl 24) or 0x2E312AL, out.color("keyBackground"))
        // Not followed: unchanged. The glass (frost) travels as it is.
        assertEquals(4293519854L, out.color("keyText"))
        assertEquals(4f, out.float("frost.windowBlurRadiusDp"))
        assertEquals("custom_1793966705811", out.string("id"))
        assertEquals("Liquid", out.string("name"))
        assertTrue(KeyboardTheme.recognisable(out))
    }

    @Test
    fun theWallpaperBecomesTheKeyboardsBackground() {
        val out = KeyboardTheme.build(doc(), palette, "Liquid", 1, wallpaperJpegBase64 = "AAAA", wallpaperBlur = 40f)
        assertEquals("AAAA", out.string("backgroundImageBase64"))
        assertEquals(25f, out.float("backgroundImageBlur"))
        assertNull(out.string("backgroundPhoto"))
    }

    @Test
    fun withoutAPaletteNothingIsGuessed() {
        val out = KeyboardTheme.build(doc(), null, "Liquid", 1)
        assertEquals(4285362036L, out.color("accent"))
    }

    @Test
    fun fileNamesEndTheWayWmKeyboardRecognisesThem() {
        assertEquals("Liquid-21-Nov.wmtheme.json", KeyboardTheme.fileName("Liquid · 21 Nov"))
        assertEquals("liquid.wmtheme.json", KeyboardTheme.fileName("···"))
    }
}
