package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidThemeTest {
    private fun backup(): String =
        javaClass.getResourceAsStream("/nice_liquid.wmtheme.json")!!.bufferedReader().use { it.readText() }

    @Test
    fun yourKeyboardThemeImportsAsTheBundledDefault() {
        val imported = LiquidThemes.decode(backup())
        assertNotNull(imported)
        assertEquals("wallpaper stoped", imported!!.name)
        assertEquals("WM Keyboard: wallpaper stoped", imported.source)
        // The four kinds 0.9.13 drew are as they were.
        val k = LiquidThemes.Keyboard
        for ((a, b) in listOf(imported.button to k.button, imported.main to k.main, imported.chip to k.chip, imported.tool to k.tool)) {
            assertEquals(b.fill, a.fill)
            assertEquals(b.text, a.text)
            assertEquals(b.pressed, a.pressed)
            assertEquals(b.active, a.active)
            assertEquals(b.activeText, a.activeText)
            assertEquals(b.shape, a.shape)
            assertEquals(b.cornerDp, a.cornerDp)
            assertEquals(b.glass, a.glass)
        }
        assertEquals(k.accent, imported.accent)
        assertEquals(k.glassTint, imported.glassTint)
        assertEquals(k.frost.blurDp, imported.frost.blurDp, 0.0001f)
        assertEquals(k.frost.rim, imported.frost.rim, 0.0001f)
        // 0.10.0 (W3): the rest of the file is read too.
        val m = imported.material!!
        assertEquals("LETTERPRESS", m.kind)
        assertEquals("PAPER", m.texture)
        assertEquals("INSET", m.edge)
        assertEquals("DEBOSS", m.label)
        assertEquals("SQUISH", imported.bloom!!.motion)
        assertEquals(0.5f, imported.bloom!!.light, 0.0001f)
        assertEquals("SWOOSH", imported.effect!!.kind)
        assertTrue(imported.effect!!.onPress)
        assertEquals(0xFF443A47, imported.popup!!.fill)
        assertEquals(3.3675797f, imported.board!!.imageBlurDp, 0.0001f)
        assertEquals(0.96f, imported.button.textureOpacity, 0.0001f)
        assertEquals("NONE", imported.frost.backdrop)
        assertTrue(imported.frost.on("KEYS") && imported.frost.on("POPUPS"))
    }

    @Test
    fun theDerivedColoursFollowWmKeyboard() {
        val t = LiquidThemes.Keyboard
        // The theme sets the pressed key; the active chip and tool fall back to it.
        assertEquals(0xFF723F61, t.chip.active)
        assertEquals(0xFF723F61, t.tool.active)
        // The accent does not read on the pressed plum (under 3:1), the key text does.
        assertTrue(ThemeColors.contrast(0xFFD182AF, 0xFF723F61) < 3f)
        assertEquals(0xFFE9E9EE, t.chip.activeText)
        // No toolbar icon colour: the key text at 65 %.
        assertEquals(0xA6E9E9EE, t.tool.text)
        assertEquals(0xFF838385, t.tool.activeText)
        assertEquals(0xFF3C323E, t.glassTint)
        assertEquals("FROSTED", t.frost.style)
        assertEquals(0.86f, t.frost.rim, 0.0001f)
        assertTrue(t.button.glass && t.main.glass && t.chip.glass && t.tool.glass)
    }

    @Test
    fun ownFormatRoundTrips_andJunkIsRefused() {
        val text = LiquidThemes.encode(LiquidThemes.Keyboard)
        assertTrue(text.contains("\"liquidTheme\""))
        assertEquals(LiquidThemes.Keyboard, LiquidThemes.decode(text))
        assertNull(LiquidThemes.decode("not json"))
        assertNull(LiquidThemes.decode("{\"hello\": 1}"))
    }

    @Test
    fun aMinimalWmThemeGetsEverythingDerived() {
        val t = LiquidThemes.decode("""{"name":"Light","dark":false,"boardBackground":4294967295,"keyBackground":4294967295,"keyText":4278190080,"accent":4278221567,"keyShape":"HEXAGON","toolShape":"CIRCLE"}""")!!
        // Pressed: a quarter of the way from the key to its text.
        val p = t.button.pressed
        assertTrue(ThemeColors.luminance(p) < 1f && ThemeColors.luminance(p) > 0.3f)
        // A decorative key shape is drawn rounded; a round tool stays round.
        assertEquals("ROUNDED", t.button.shape)
        assertEquals("CIRCLE", t.tool.shape)
        // Without frost nothing is glass.
        assertTrue(!t.button.glass && !t.chip.glass)
        assertEquals("Light", t.name)
    }

    @Test
    fun colourArithmetic() {
        assertEquals(21f, ThemeColors.contrast(0xFF000000, 0xFFFFFFFF), 0.01f)
        assertEquals(0xFFFFFFFF, ThemeColors.legibleOn(0xFF101010, emptyList()))
        assertEquals(0xFF000000, ThemeColors.legibleOn(0xFFF0F0F0, emptyList()))
        // Black at half over white is mid grey.
        assertEquals(0xFF808080, ThemeColors.blendOver(0xFF000000, 0xFFFFFFFF, 0.5f))
        assertEquals(0xFF123456, ThemeColors.lerp(0xFF123456, 0xFFABCDEF, 0f))
        assertEquals(0xFFABCDEF, ThemeColors.lerp(0xFF123456, 0xFFABCDEF, 1f))
    }
}
