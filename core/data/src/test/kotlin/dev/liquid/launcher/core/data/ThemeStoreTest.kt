package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStoreTest {
    private fun migrated(prepare: (SettingsStore) -> Unit): SettingsStore {
        val s = SettingsStore.inMemory()
        prepare(s)
        ThemeStore.migrate010(s)
        return s
    }

    @Test
    fun anAutomaticOrPinkAccentBecameTheFixedBlueIn010() {
        for (accent in listOf("", "#D182AF")) {
            val s = migrated {
                it.set(LauncherSettings.ThemeJson, LiquidThemes.encode(LiquidThemes.Keyboard))
                if (accent.isNotEmpty()) it.set(LauncherSettings.ThemeAccent, accent)
            }
            assertEquals(ThemeDocs.BLUE, ThemeStore.doc(s).color("accent"))
            assertNull(ThemeStore.doc(s).follow("accent"))
        }
    }

    @Test
    fun aNewInstallKeepsTheDefaultWithItsAccentFromTheWallpaper() {
        val s = migrated { it.set(LauncherSettings.ThemeAccent, "#D182AF") }
        assertEquals("", s.get(LauncherSettings.ThemeJson))
        assertEquals(ThemeDocs.Default, ThemeStore.doc(s))
        assertEquals(WallpaperRole.VIBRANT, ThemeStore.doc(s).follow("accent"))
        ThemeStore.migrate0102(s)
        assertEquals("", s.get(LauncherSettings.ThemeJson))
        assertNull(ThemeStore.before0102(s))
    }

    @Test
    fun anAccentYouChoseIsKept() {
        val s = migrated { it.set(LauncherSettings.ThemeAccent, "#123456") }
        assertEquals(0xFF123456, ThemeStore.doc(s).color("accent"))
        assertNull(ThemeStore.doc(s).follow("accent"))
    }

    @Test
    fun yourEditedThemeIsConvertedAndKeptAsCurrentLook() {
        val edited = LiquidThemes.Keyboard.copy(chip = LiquidThemes.Keyboard.chip.copy(fill = 0xFF010203))
        val s = migrated {
            it.set(LauncherSettings.ThemeJson, LiquidThemes.encode(edited))
            it.set(LauncherSettings.ButtonMain, "accent")
        }
        val doc = ThemeStore.doc(s)
        assertEquals(0xFF010203, doc.color("chipBackground"))
        assertEquals("accent", doc.string("${ThemeDoc.EXT}.mainColor"))
        assertEquals(0xFF010203, ThemeResolve.resolve(doc).chip.fill)
        assertNotNull(ThemeStore.previous(s))
        assertEquals(doc, ThemeStore.previous(s))
        assertTrue(s.get(LauncherSettings.ThemeMigrated010))
    }

    @Test
    fun aKeyboardThemeKeepsItsOwnPillsAndWhatItSays() {
        val keyboard = """{"name":"Mine","keyBackground":469512766,"liquidLauncher":{"card":{"background":4278190335}}}"""
        val s = migrated { it.set(LauncherSettings.ThemeJson, keyboard) }
        val doc = ThemeStore.doc(s)
        assertNull(doc.color("${ThemeDoc.EXT}.space.background"))
        assertEquals(0xFF0000FF, doc.color("${ThemeDoc.EXT}.card.background"))
        assertEquals(469512766L, doc.color("keyBackground"))
        assertEquals(469512766L, ThemeResolve.resolve(doc).space?.fill)
    }

    /** A phone as 0.10.1 left it: your converted theme with the dark pills 0.10.1 added, and a look of your own. */
    private fun phoneOn0101(tune: (ThemeDoc) -> ThemeDoc = { it }): SettingsStore {
        val s = SettingsStore.inMemory()
        s.set(LauncherSettings.ThemeJson, LiquidThemes.encode(LiquidThemes.Keyboard))
        s.set(LauncherSettings.ThemeMigrated010, false)
        ThemeStore.migrate010(s)
        assertEquals(ThemeDocs.DARK_PILL, ThemeStore.doc(s).color("${ThemeDoc.EXT}.space.background"))
        ThemeStore.save(s, tune(ThemeStore.doc(s)))
        return s
    }

    @Test
    fun on0102TheDarkPillsGoAndYourLookIsKeptAsAPreset() {
        val s = phoneOn0101 { it.withColor("chipBackground", 0xFF102030).with("variants", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonObject(mapOf("name" to kotlinx.serialization.json.JsonPrimitive("Other")))))) }
        ThemeStore.migrate0102(s)
        // The default is now your keyboard theme in the wallpaper's colours, with the other looks of your theme.
        val now = ThemeStore.doc(s)
        assertEquals(ThemeDocs.Default.without("variants"), now.without("variants"))
        assertEquals(1, (now.element("variants") as kotlinx.serialization.json.JsonArray).size)
        assertEquals("theme", s.get(LauncherSettings.ThemeControls))
        // Your look, without the dark pills: the home and drawer pills are the theme's glass, the search bar keeps a light tint.
        val before = ThemeStore.before0102(s)!!
        assertEquals(0xFF102030, before.color("chipBackground"))
        assertNull(before.color("${ThemeDoc.EXT}.space.background"))
        assertNull(before.color("${ThemeDoc.EXT}.space.text"))
        assertEquals(ThemeDocs.SEARCH_TINT, before.color("${ThemeDoc.EXT}.space.searchBackground"))
        val t = ThemeResolve.resolve(before)
        assertEquals(t.button.fill, t.space!!.fill)
        assertEquals(ThemeDocs.SEARCH_TINT, t.search!!.fill)
        // The look from before 0.10.1 is cleaned the same way.
        assertNull(ThemeStore.previous(s)!!.color("${ThemeDoc.EXT}.space.background"))
        assertTrue(s.get(LauncherSettings.ThemeMigrated0102))
    }

    @Test
    fun aPillColourYouSetIsKept() {
        val s = phoneOn0101 { it.withColor("${ThemeDoc.EXT}.space.background", 0x80FF0000).withColor("${ThemeDoc.EXT}.space.text", 0xFF00FF00) }
        ThemeStore.migrate0102(s)
        val before = ThemeStore.before0102(s)!!
        assertEquals(0x80FF0000, before.color("${ThemeDoc.EXT}.space.background"))
        assertEquals(0xFF00FF00, before.color("${ThemeDoc.EXT}.space.text"))
        assertNull(before.color("${ThemeDoc.EXT}.space.searchBackground"))
    }

    @Test
    fun individualPillsLoseTheDarkFillToo() {
        val s = phoneOn0101 { d ->
            var x = d.withPillLook("individual")
            for (p in ThemePill.all) x = x.withPill(p, ThemeDocs.Legacy010)
            x
        }
        ThemeStore.migrate0102(s)
        val before = ThemeStore.before0102(s)!!
        assertNull(before.pill(ThemePill.HOME)!!.color("${ThemeDoc.EXT}.space.background"))
        assertNull(before.pill(ThemePill.DRAWER)!!.color("${ThemeDoc.EXT}.space.background"))
        assertEquals(ThemeDocs.SEARCH_TINT, before.pill(ThemePill.SEARCH)!!.color("${ThemeDoc.EXT}.space.background"))
    }

    @Test
    fun the0102SwitchRunsOnce() {
        val s = phoneOn0101()
        ThemeStore.migrate0102(s)
        ThemeStore.save(s, ThemeStore.doc(s).withColor("accent", 0xFF00FF00).withFollow("accent", null))
        ThemeStore.migrate0102(s)
        assertEquals(0xFF00FF00, ThemeStore.doc(s).color("accent"))
    }

    @Test
    fun itRunsOnce() {
        val s = migrated { }
        ThemeStore.save(s, ThemeStore.doc(s).withColor("accent", 0xFF00FF00))
        ThemeStore.migrate010(s)
        assertEquals(0xFF00FF00, ThemeStore.doc(s).color("accent"))
    }
}
