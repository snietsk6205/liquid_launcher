package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.settings.BoolSetting
import dev.liquid.launcher.core.settings.ChoiceSetting
import dev.liquid.launcher.core.settings.IntSetting
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LooksTest {
    @Test
    fun everyValueFitsItsSetting() {
        val byKey = LauncherSettings.all.associateBy { it.key }
        for (preset in Looks.presets) {
            for ((key, text) in preset.values) {
                assertTrue("${preset.name}: $key is not part of a look", key in Looks.keys)
                when (val setting = byKey.getValue(key)) {
                    is IntSetting -> {
                        val v = text.removePrefix("i:").toInt()
                        assertTrue("${preset.name}: $key = $v", text.startsWith("i:") && v in setting.min..setting.max)
                        assertEquals("${preset.name}: $key = $v is off the slider's steps", 0, (v - setting.min) % setting.step)
                    }
                    is BoolSetting -> assertTrue("${preset.name}: $key", text == "b:true" || text == "b:false")
                    is ChoiceSetting -> assertTrue("${preset.name}: $key = $text", setting.options.any { "s:" + it.first == text })
                    else -> assertTrue("${preset.name}: $key", text.startsWith("s:"))
                }
            }
        }
        assertEquals(Looks.presets.size, Looks.presets.map { it.name }.toSet().size)
    }

    @Test
    fun aLookSetsGlassAndEffectsButNotQualityOrColours() {
        val settings = SettingsStore.inMemory()
        settings.set(LauncherSettings.GlassQuality, "frosted")
        settings.set(LauncherSettings.ThemeAccent, "#FF0000")
        settings.set(LauncherSettings.TouchSize, 150)
        Looks.apply(settings, Looks.presets.first { it.name == "Nice liquid" })
        assertEquals("swoosh", settings.get(LauncherSettings.TouchEffect))
        assertEquals(40, settings.get(LauncherSettings.TouchSpeed))
        // Not named by the look: back to its default.
        assertEquals(100, settings.get(LauncherSettings.TouchSize))
        assertEquals("frosted", settings.get(LauncherSettings.GlassQuality))
        assertEquals("#FF0000", settings.get(LauncherSettings.ThemeAccent))
        assertEquals("Nice liquid", Looks.current(settings)?.name)
        settings.set(LauncherSettings.TouchSize, 120)
        assertNull(Looks.current(settings))
        Looks.apply(settings, Looks.presets.first())
        assertEquals("ripple", settings.get(LauncherSettings.TouchEffect))
        assertEquals("Liquid", Looks.current(settings)?.name)
    }
}
