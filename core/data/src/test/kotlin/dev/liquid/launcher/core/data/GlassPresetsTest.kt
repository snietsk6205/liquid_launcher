package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.settings.BoolSetting
import dev.liquid.launcher.core.settings.IntSetting
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassPresetsTest {
    @Test
    fun everyValueFitsItsSettingAndIsNotItsDefault() {
        val byKey = LauncherSettings.all.associateBy { it.key }
        for (preset in GlassPresets.presets) {
            for ((key, text) in preset.values) {
                assertTrue("${preset.name}: $key is not part of a glass preset", key in GlassPresets.keys)
                when (val setting = byKey.getValue(key)) {
                    is IntSetting -> {
                        val v = text.removePrefix("i:").toInt()
                        assertTrue("${preset.name}: $key = $v", text.startsWith("i:") && v in setting.min..setting.max)
                        assertEquals("${preset.name}: $key = $v is off the slider's steps", 0, (v - setting.min) % setting.step)
                        assertNotEquals("${preset.name}: $key = $v is its default", setting.default, v)
                    }
                    is BoolSetting -> {
                        assertTrue("${preset.name}: $key", text == "b:true" || text == "b:false")
                        assertNotEquals("${preset.name}: $key is its default", "b:${setting.default}", text)
                    }
                    else -> throw AssertionError("${preset.name}: $key is neither a number nor a switch")
                }
            }
        }
        assertEquals(GlassPresets.presets.size, GlassPresets.presets.map { it.name }.toSet().size)
    }

    @Test
    fun aPresetSetsTheGlassButNotTheLightOrQuality() {
        val settings = SettingsStore.inMemory()
        settings.set(LauncherSettings.GlassQuality, "frosted")
        settings.set(LauncherSettings.GlassLightAngle, 90)
        settings.set(LauncherSettings.GlassBevel, 30)
        GlassPresets.apply(settings, GlassPresets.presets.first { it.name == "Frosted" })
        assertEquals(14, settings.get(LauncherSettings.GlassDefault.blur))
        assertEquals(false, settings.get(LauncherSettings.GlassDispersion))
        assertEquals(14, settings.get(LauncherSettings.GlassBevel))
        assertEquals("frosted", settings.get(LauncherSettings.GlassQuality))
        assertEquals(90, settings.get(LauncherSettings.GlassLightAngle))
        assertEquals("Frosted", GlassPresets.current(settings)?.name)
        settings.set(LauncherSettings.GlassGrain, 50)
        assertNull(GlassPresets.current(settings))
        // Back to the preset's value by hand: recognised again.
        settings.set(LauncherSettings.GlassGrain, 30)
        assertEquals("Frosted", GlassPresets.current(settings)?.name)
        GlassPresets.apply(settings, GlassPresets.presets.first())
        assertEquals(LauncherSettings.GlassBevel.default, settings.get(LauncherSettings.GlassBevel))
        assertEquals("Real glass", GlassPresets.current(settings)?.name)
    }
}
