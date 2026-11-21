package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.common.WallpaperPalette
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeDocTest {
    private fun backup(): String =
        javaClass.getResourceAsStream("/nice_liquid.wmtheme.json")!!.bufferedReader().use { it.readText() }

    @Test
    fun aWmThemeRoundTripsWithEveryField() {
        val doc = ThemeDoc.parse(backup())!!
        val again = ThemeDoc.parse(doc.encode())!!
        assertEquals(doc, again)
        // Fields the launcher does not draw stay in the file.
        assertEquals("BOTTOM", again.string("frost.wallpaperBand"))
        assertEquals(3.3675797f, again.float("backgroundImageBlur")!!, 0.0001f)
        assertEquals("SWOOSH", again.string("boardEffect.kind"))
    }

    @Test
    fun nestedPathsAreWrittenAndRemoved() {
        val d = ThemeDoc.parse("""{"keyBackground": 1}""")!!
            .withFloat("frost.backdropBlur", 12.5f)
            .withColor("keyMaterial.edgeColor", 0xFF112233)
        assertEquals(12.5f, d.float("frost.backdropBlur")!!, 0.0001f)
        assertEquals(0xFF112233, d.color("keyMaterial.edgeColor"))
        val gone = d.without("frost.backdropBlur")
        assertNull(gone.float("frost.backdropBlur"))
        // Auto is written as null, which reads as unset.
        assertNull(d.withColor("keyText", null).color("keyText"))
    }

    @Test
    fun aFollowedColourTakesTheWallpapersRoleAndKeepsItsOpacity() {
        val palette = WallpaperPalette(0xFF101010.toInt(), 0xFFFF0000.toInt(), 0xFF808080.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        val d = ThemeDocs.Legacy010.withColor("keyBackground", 0x40123456).withFollow("keyBackground", WallpaperRole.VIBRANT)
        val t = ThemeResolve.resolve(d, palette)
        assertEquals(0x40FF0000, t.button.fill)
        // Without a palette (not known yet), the stored colour.
        assertEquals(0x40123456, ThemeResolve.resolve(d, null).button.fill)
        // Only colours set to follow change.
        assertEquals(ThemeDocs.BLUE, t.accent)
        assertEquals(mapOf("keyBackground" to "vibrant"), d.followed)
        assertNull(d.withFollow("keyBackground", null).follow("keyBackground"))
    }

    @Test
    fun theDefaultThemeOf010StaysForConvertingOldThemes() {
        val t = ThemeResolve.resolve(ThemeDocs.Legacy010)
        assertEquals(ThemeDocs.BLUE, t.accent)
        assertEquals(ThemeDocs.DARK_PILL, t.space!!.fill)
        assertEquals("block", t.mainColor)
        assertNull(t.material)
        assertNull(t.effect)
        // The controls are your first keyboard theme's, as 0.9.14 drew them.
        val k = LiquidThemes.Keyboard
        assertEquals(k.button.fill, t.button.fill)
        assertEquals(k.chip.fill, t.chip.fill)
        assertEquals(k.tool.fill, t.tool.fill)
    }

    private val keyboardFile = "/wallpaper-stoped.wmtheme.json"

    /** A wallpaper: dominant, vibrant, muted, dark, light, average. */
    private fun wallpaper(average: Long) = WallpaperPalette(
        0xFF6A7A3A.toInt(), 0xFFB8D02A.toInt(), 0xFF7C8470.toInt(), 0xFF1E2418.toInt(), 0xFFEEF0E8.toInt(), average.toInt(),
    )

    @Test
    fun theDefaultIsYourKeyboardThemeFieldForField() {
        val file = ThemeDoc.parse(javaClass.getResourceAsStream(keyboardFile)!!.bufferedReader().use { it.readText() })!!
        val d = ThemeDocs.Default
        // Every field of the keyboard's file is there with its value, but for the three the launcher changes.
        val changed = setOf("id", "frost", "boardEffect", ThemeDoc.EXT)
        for ((k, v) in file.json) if (k !in changed) assertEquals(k, v, d.json[k])
        assertEquals("wallpaper stoped", d.name)
        assertEquals("liquid_default", d.string("id"))
        assertEquals("AUTO", d.string("frost.backdrop"))
        assertEquals("BOTTOM", d.string("frost.wallpaperBand"))
        assertEquals(9f, d.float("frost.backdropBlur")!!, 0f)
        assertEquals(ThemeDocs.defaultFollow, d.followed)
        val t = ThemeResolve.resolve(d)
        assertEquals("LETTERPRESS", t.material!!.kind)
        assertNull(t.effect)
        assertEquals("plain", t.mainColor)
        assertEquals("SQUISH", t.bloom!!.motion)
        // The pills: clear glass, no fill of their own and no material; the search bar a light tint.
        assertEquals(t.button.fill, t.space!!.fill)
        assertFalse(t.space!!.material)
        assertTrue(t.button.material)
        assertEquals(ThemeDocs.SEARCH_TINT, t.search!!.fill)
        assertTrue(ThemeColors.alpha(t.search!!.fill) < ThemeColors.alpha(ThemeDocs.DARK_PILL) / 2.5f)
    }

    @Test
    fun theDefaultTakesTheWallpapersColoursAndKeepsEachOpacity() {
        val p = wallpaper(0xFF303828)
        val t = ThemeResolve.resolve(ThemeDocs.Default, p)
        fun rgb(c: Long) = c and 0xFFFFFFL
        fun a(c: Long) = (c ushr 24) and 0xFFL
        val dark = p.dark.toLong() and 0xFFFFFFL
        assertEquals(dark, rgb(t.button.fill)); assertEquals(0x1CL, a(t.button.fill))
        assertEquals(dark, rgb(t.modifier!!.fill)); assertEquals(0x1CL, a(t.modifier!!.fill))
        assertEquals(dark, rgb(t.tool.fill)); assertEquals(0x62L, a(t.tool.fill))
        assertEquals(dark, rgb(t.popup!!.fill)); assertEquals(0xFFL, a(t.popup!!.fill))
        assertEquals(dark, rgb(t.board!!.background)); assertEquals(0L, a(t.board!!.background))
        assertEquals(dark, rgb(t.card!!.fill)); assertEquals(0x59L, a(t.card!!.fill))
        assertEquals(dark, rgb(t.search!!.fill)); assertEquals(0x37L, a(t.search!!.fill))
        assertEquals(p.muted.toLong() and 0xFFFFFFL, rgb(t.chip.fill)); assertEquals(0xFFL, a(t.chip.fill))
        val vibrant = p.vibrant.toLong() and 0xFFFFFFL
        assertEquals(vibrant, rgb(t.main.fill)); assertEquals(vibrant, rgb(t.accent)); assertEquals(vibrant, rgb(t.button.pressed))
        assertEquals(vibrant, rgb(t.tool.active)); assertEquals(0x8AL, a(t.tool.active))
        // On a dark wallpaper the light text reads, so it is the wallpaper's light colour.
        assertEquals(p.light.toLong() and 0xFFFFFFFFL, t.button.text)
        assertTrue(t.adaptive)
    }

    @Test
    fun textStaysReadableOnALightWallpaper() {
        val t = ThemeResolve.resolve(ThemeDocs.Default, wallpaper(0xFFD8DCCF))
        // The buttons and the pills are see-through: over a light wallpaper their light text would not read.
        for (c in listOf(t.button, t.space!!, t.search!!)) {
            val bg = ThemeColors.blendOver(ThemeColors.opaque(c.fill), 0xFFD8DCCF, ThemeColors.alpha(c.fill))
            assertTrue("${ThemeDoc.formatColor(c.text)} on ${ThemeDoc.formatColor(bg)}", ThemeColors.contrast(c.text, bg) >= 3f)
            assertTrue(ThemeColors.luminance(c.text) < 0.2f)
        }
        // The main button is filled with the vibrant green: its text reads on it.
        assertTrue(ThemeColors.contrast(t.main.text, ThemeColors.opaque(t.main.fill)) >= 3f)
        // Menus are solid dark: their text stays light.
        assertTrue(ThemeColors.luminance(t.popup!!.text) > 0.5f)
    }

    @Test
    fun aTextColourYouFixedIsKept() {
        val d = ThemeDocs.Default.withColor("keyText", 0xFFFFFFFF).withFollow("keyText", null)
        val t = ThemeResolve.resolve(d, wallpaper(0xFFE0E0E0))
        assertEquals(0xFFFFFFFF, t.button.text)
        // Without a picture's colours nothing is changed.
        assertFalse(ThemeResolve.resolve(ThemeDocs.Default, null).adaptive)
    }

    @Test
    fun aV1ThemeBecomesTheSameControls() {
        val v1 = LiquidThemes.Keyboard.copy(button = LiquidThemes.Keyboard.button.copy(fill = 0x33445566, glowDp = 4f))
        val doc = ThemeDoc.parse(LiquidThemes.encode(v1))!!
        val t = ThemeResolve.resolve(doc)
        assertEquals(0x33445566, t.button.fill)
        assertEquals(4f, t.button.glowDp, 0f)
        assertEquals(v1.chip.active, t.chip.active)
        assertEquals(v1.tool.text, t.tool.text)
        assertEquals(v1.accent, t.accent)
    }

    @Test
    fun individualPillsResolveTheirOwnTheme() {
        val search = ThemeDocs.Default.withColor("${ThemeDoc.EXT}.space.background", 0xFF000000)
        val d = ThemeDocs.Default.withPillLook("individual").withPill(ThemePill.SEARCH, search)
        val t = ThemeResolve.resolve(d)
        assertEquals(0xFF000000, t.pills[ThemePill.SEARCH]!!.space!!.fill)
        assertNull(t.pills[ThemePill.HOME])
        // A pill's theme carries no pills of its own.
        assertNull(d.pill(ThemePill.SEARCH)!!.pill(ThemePill.SEARCH))
    }

    @Test
    fun everyFieldIsDescribedAndInRangeInTheDefault() {
        val paths = ThemeFields.all.map { it.path }
        assertEquals(paths.size, paths.toSet().size)
        for (f in ThemeFields.all) {
            assertTrue(f.title, f.title.split(' ').size <= 4)
            assertTrue(f.path, f.summary.isNotBlank())
            if (f.kind == ThemeFieldKind.FLOAT || f.kind == ThemeFieldKind.INT) {
                val v = ThemeDocs.Default.float(f.path) ?: continue
                assertTrue("${f.path} = $v", v >= f.min - 0.0001f && v <= f.max + 0.0001f)
            }
        }
    }

    @Test
    fun conditionsOfFields() {
        val liquidOnly = ThemeFields.field("frost.refraction")!!
        assertFalse(ThemeFields.applies(liquidOnly, ThemeDocs.Default.withString("frost.style", "FROSTED")))
        assertTrue(ThemeFields.applies(liquidOnly, ThemeDocs.Default.withString("frost.style", "LIQUID")))
        // 0.11.2: the default theme's glass is liquid.
        assertTrue(ThemeFields.applies(liquidOnly, ThemeDocs.Default))
        val needsMaterial = ThemeFields.field("keyMaterial.gloss")!!
        assertFalse(ThemeFields.applies(needsMaterial, ThemeDocs.Legacy010))
        assertTrue(ThemeFields.applies(needsMaterial, ThemeDocs.Legacy010.withString("keyMaterial.kind", "METAL")))
        assertTrue(ThemeFields.applies(ThemeFields.field("${ThemeDoc.EXT}.pillMaterial")!!, ThemeDocs.Default))
        val speed = ThemeFields.field("animationSpeed")!!
        assertFalse(ThemeFields.applies(speed, ThemeDocs.Default))
        assertTrue(ThemeFields.applies(speed, ThemeDocs.Default.with("animation", JsonPrimitive("FLOW"))))
    }

    @Test
    fun materialPresetsAreWmKeyboards() {
        val metal = ThemeResolve.resolve(ThemeDocs.Default.withString("keyMaterial.kind", "METAL")).material!!
        assertEquals("BRUSHED", metal.texture)
        assertEquals(0.32f, metal.gloss, 0.0001f)
        // A part set in the theme wins over the preset.
        val glossy = ThemeResolve.resolve(ThemeDocs.Default.withString("keyMaterial.kind", "METAL").withFloat("keyMaterial.gloss", 0.9f)).material!!
        assertEquals(0.9f, glossy.gloss, 0.0001f)
    }
}
