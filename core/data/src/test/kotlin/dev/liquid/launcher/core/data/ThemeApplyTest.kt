package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeApplyTest {
    @Test
    fun firstRunGivesYourKeyboardColoursOnce_keepingColoursYouChose() {
        val fresh = SettingsStore.inMemory()
        ThemeApply.firstRun(fresh)
        assertEquals("#D182AF", fresh.get(LauncherSettings.ThemeAccent))
        assertEquals("#3C323E", fresh.get(LauncherSettings.ThemeGlassColor))
        assertTrue(fresh.get(LauncherSettings.ThemeColoursApplied))
        // Changed afterwards by hand: a later start leaves it alone.
        fresh.set(LauncherSettings.ThemeAccent, "#00FF00")
        ThemeApply.firstRun(fresh)
        assertEquals("#00FF00", fresh.get(LauncherSettings.ThemeAccent))

        val mine = SettingsStore.inMemory()
        mine.set(LauncherSettings.ThemeAccent, "#123456")
        ThemeApply.firstRun(mine)
        assertEquals("#123456", mine.get(LauncherSettings.ThemeAccent))
        assertEquals("#3C323E", mine.get(LauncherSettings.ThemeGlassColor))
    }

    @Test
    fun usingAThemeStoresItAndItsColours_andTheDefaultIsStoredAsNothing() {
        val s = SettingsStore.inMemory()
        val other = LiquidThemes.Keyboard.copy(name = "Other", accent = 0xFF00AAFF)
        ThemeApply.use(s, other)
        assertEquals(other, ThemeApply.current(s))
        assertEquals("#00AAFF", s.get(LauncherSettings.ThemeAccent))
        ThemeApply.use(s, LiquidThemes.Keyboard)
        assertEquals("", s.get(LauncherSettings.ThemeJson))
        assertEquals(LiquidThemes.Keyboard, ThemeApply.current(s))
    }
}
