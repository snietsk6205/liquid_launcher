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
        val d = ThemeDocs.Default.withColor("keyBackground", 0x40123456).withFollow("keyBackground", WallpaperRole.VIBRANT)
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
    fun theDefaultTheme() {
        val t = ThemeResolve.resolve(ThemeDocs.Default)
        assertEquals(ThemeDocs.BLUE, t.accent)
        assertEquals(0xA6141218, t.space!!.fill)
        assertEquals("block", t.mainColor)
        assertNull(t.material)
        assertNull(t.effect)
        assertEquals("SQUISH", t.bloom!!.motion)
        assertEquals(0f, t.bloom!!.light, 0f)
        assertTrue(t.frost.on("POPUPS") && t.frost.on("SPACE_BAR"))
        assertEquals(emptyMap<String, LiquidTheme>(), t.pills)
        // The controls are your keyboard theme's, as 0.9.14 drew them.
        val k = LiquidThemes.Keyboard
        assertEquals(k.button.fill, t.button.fill)
        assertEquals(k.chip.fill, t.chip.fill)
        assertEquals(k.tool.fill, t.tool.fill)
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
        assertFalse(ThemeFields.applies(liquidOnly, ThemeDocs.Default))
        assertTrue(ThemeFields.applies(liquidOnly, ThemeDocs.Default.withString("frost.style", "LIQUID")))
        val needsMaterial = ThemeFields.field("keyMaterial.gloss")!!
        assertFalse(ThemeFields.applies(needsMaterial, ThemeDocs.Default))
        assertTrue(ThemeFields.applies(needsMaterial, ThemeDocs.Default.withString("keyMaterial.kind", "METAL")))
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
