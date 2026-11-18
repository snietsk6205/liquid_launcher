package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun anAutomaticOrPinkAccentBecomesTheFixedBlue() {
        for (accent in listOf("", "#D182AF")) {
            val s = migrated { if (accent.isNotEmpty()) it.set(LauncherSettings.ThemeAccent, accent) }
            assertEquals(ThemeDocs.BLUE, ThemeStore.doc(s).color("accent"))
        }
    }

    @Test
    fun anAccentYouChoseIsKept() {
        val s = migrated { it.set(LauncherSettings.ThemeAccent, "#123456") }
        assertEquals(0xFF123456, ThemeStore.doc(s).color("accent"))
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
    fun itRunsOnce() {
        val s = migrated { }
        ThemeStore.save(s, ThemeStore.doc(s).withColor("accent", 0xFF00FF00))
        ThemeStore.migrate010(s)
        assertEquals(0xFF00FF00, ThemeStore.doc(s).color("accent"))
    }
}
