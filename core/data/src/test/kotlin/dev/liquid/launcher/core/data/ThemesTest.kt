package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class ThemesTest {
    @Test
    fun presetResetsWhatItDoesNotNameAndLeavesQualityAlone() {
        val settings = SettingsStore.inMemory()
        settings.set(LauncherSettings.ThemeIconShape, "circle")
        settings.set(LauncherSettings.GlassQuality, "tint")
        Themes.apply(settings, Themes.presets.first { it.name == "Mono" })
        assertEquals("mono", settings.get(LauncherSettings.ThemeFont))
        assertEquals("square", settings.get(LauncherSettings.ThemeIconShape))
        assertEquals("tint", settings.get(LauncherSettings.GlassQuality))
        Themes.apply(settings, Themes.presets.first())
        assertEquals("default", settings.get(LauncherSettings.ThemeFont))
    }

    @Test
    fun exportImportRoundTrip() {
        val dir = Files.createTempDirectory("fonts").toFile()
        val a = SettingsStore.inMemory()
        a.set(LauncherSettings.ThemeAccent, "#FF0000")
        a.set(LauncherSettings.ThemeFont, "serif")
        val bytes = ByteArrayOutputStream().also { Themes.export(a, dir, it) }.toByteArray()
        val b = SettingsStore.inMemory()
        assertTrue(Themes.import(b, dir, ByteArrayInputStream(bytes)))
        assertEquals("#FF0000", b.get(LauncherSettings.ThemeAccent))
        assertEquals("serif", b.get(LauncherSettings.ThemeFont))
    }
}
