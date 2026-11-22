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
    fun medsFileIsTheBaseWithTheLaunchersKeysAndItFollowsTheWallpaper() {
        val base = ThemeDoc.parse(
            """{"id":"custom_1795289804543","name":"wallpaper ","boardBackground":4280162336,"backgroundImageBase64":null,
               "toolCircleBackground":1128084031,"chipBackground":4281477941,"keyBackground":1,"keyText":2,
               "followWallpaper":false,"frost":{"enabled":true,"style":"FROSTED","backdropBlur":9.0}}""",
        )!!
        val launcher = doc().withString("backgroundImageBase64", "PICTURE")
        val out = KeyboardTheme.build(base, launcher, palette, "Liquid", 7)
        // The toolbar, chips, board and frost stay his; the keys are the launcher's (followed colours written out).
        assertEquals(1128084031L, out.color("toolCircleBackground"))
        assertEquals(4281477941L, out.color("chipBackground"))
        assertEquals(4280162336L, out.color("boardBackground"))
        assertEquals("FROSTED", out.string("frost.style"))
        assertEquals((472657461L ushr 24 shl 24) or 0x2E312AL, out.color("keyBackground"))
        assertEquals(4293519854L, out.color("keyText"))
        assertEquals(0xFFC8EEA8L, out.color("accent"))
        // No picture of its own (the toolbar stays crisp), and WM Keyboard follows the wallpaper by itself.
        assertNull(out.string("backgroundImageBase64"))
        assertEquals(true, out.bool("followWallpaper"))
        assertEquals("custom_7", out.string("id"))
    }

    @Test
    fun fileNamesEndTheWayWmKeyboardRecognisesThem() {
        assertEquals("Liquid-21-Nov.wmtheme.json", KeyboardTheme.fileName("Liquid · 21 Nov"))
        assertEquals("liquid.wmtheme.json", KeyboardTheme.fileName("···"))
    }

    @Test
    fun aPictureThemeReadsItsPictureByPathAndShowsItAsItIs() {
        val built = KeyboardTheme.build(doc(), doc(), palette, "Liquid wallpaper 1", 2)
        val out = KeyboardTheme.withPicture(built, "/storage/emulated/0/Pictures/Liquid Launcher/keyboard-wallpaper-1.jpg")
        assertEquals("/storage/emulated/0/Pictures/Liquid Launcher/keyboard-wallpaper-1.jpg", out.string("backgroundImage"))
        assertNull(out.string("backgroundImageBase64"))
        assertNull(out.element("backgroundPhoto")?.takeIf { it !is kotlinx.serialization.json.JsonNull })
        // The launcher made the look: WM Keyboard neither blurs it again nor lays the board's colour over it.
        assertEquals(0f, out.float("backgroundImageBlur"))
        assertEquals(1f, out.float("backgroundImageOpacity"))
        assertEquals(0L, (out.color("boardBackground") ?: -1L) ushr 24)
        assertEquals(1381915L and 0xFFFFFFL, (out.color("boardBackground") ?: 0L) and 0xFFFFFFL)
        // The keys, the name and the colours that follow the wallpaper stay.
        assertEquals("Liquid wallpaper 1", out.string("name"))
        assertEquals(true, out.bool("followWallpaper"))
        assertEquals(built.color("keyBackground"), out.color("keyBackground"))
    }
}
