package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore

/** A built-in look: a name, one line about it, and setting values (as [SettingsStore.exportValues] writes them). */
data class LookPreset(val name: String, val about: String, val values: Map<String, String>)

/**
 * Looks (0.8.0, D110): the glass, the buttons and the effects in one tap, the way a keyboard
 * theme sets its keys, frost, press effect and key motion together (after wmkeyboard's theme
 * cards). A look sets only those keys; colours, fonts and the wallpaper stay as they are.
 */
object Looks {
    private val S = LauncherSettings
    private fun s(v: String) = "s:$v"
    private fun i(v: Int) = "i:$v"
    private fun b(v: Boolean) = "b:$v"

    /** The keys a look sets; any it does not name go back to their defaults. */
    val keys: Set<String> by lazy {
        S.all.filter {
            (it.section == "buttons" && it != S.ThemeControls && it != S.ThemeJson) || it.section == "effects" ||
                (it.section == "glass" && it != S.GlassQuality && it != S.GlassEngine && it != S.GlassAuto && it != S.GlassCap && it != S.GlassFullPercent && it != S.GlassTintMode)
        }.map { it.key }.toSet()
    }

    val presets = listOf(
        LookPreset("Liquid", "The default: thick glass with rainbow edges, a ripple under the finger, squish", emptyMap()),
        LookPreset(
            "Nice liquid",
            "Your WM Keyboard theme: its frost (frosted, blur 9, colour 125 %, a thin rim of light 0.86, no grain), its slow swoosh and its squish",
            mapOf(
                S.GlassDefault.blur.key to i(9), S.GlassDefault.refraction.key to i(0), S.GlassDefault.tint.key to i(10),
                S.GlassSaturation.key to i(125), S.GlassBevel.key to i(4), S.GlassSpecular.key to i(85), S.GlassShade.key to i(60),
                S.GlassGrain.key to i(0), S.GlassDispersion.key to b(false), S.GlassDepth.key to b(false), S.GlassGlow.key to i(0),
                S.ButtonRim.key to i(90), S.ButtonTint.key to i(10),
                S.TouchEffect.key to s("swoosh"), S.TouchSpeed.key to i(40), S.TouchBrightness.key to i(40), S.TouchAmount.key to i(50),
                S.TouchColour.key to s("rainbow"), S.PressFeel.key to s("squishy"), S.PressDepth.key to i(40), S.PressWobble.key to i(50),
                S.TouchLight.key to i(50),
            ),
        ),
        LookPreset(
            "Water drop", "Clear, deep glass that swells what is behind it; drops land where you press",
            mapOf(
                S.GlassDefault.blur.key to i(2), S.GlassDefault.refraction.key to i(85), S.GlassDefault.tint.key to i(8),
                S.GlassGlow.key to i(45), S.GlassSaturation.key to i(140),
                S.TouchEffect.key to s("drop"), S.TouchColour.key to s("white"), S.TouchBrightness.key to i(55),
                S.PressFeel.key to s("squishy"), S.PressWobble.key to i(70),
                S.WallpaperEffect.key to s("bubbles"), S.WallpaperEffectAmount.key to i(30), S.WallpaperEffectBrightness.key to i(30),
            ),
        ),
        LookPreset(
            "Frosted ice", "Milky, cold glass with a sharp rim; a glint of light on each press",
            mapOf(
                S.GlassDefault.blur.key to i(16), S.GlassDefault.tint.key to i(30), S.GlassDefault.refraction.key to i(40),
                S.GlassSaturation.key to i(110), S.GlassDepth.key to b(false), S.GlassDispersion.key to b(false),
                S.GlassRim.key to i(60), S.GlassGlow.key to i(10), S.GlassRimColour.key to s("#CFE8FF"),
                S.TouchEffect.key to s("glint"), S.TouchColour.key to s("white"), S.PressFeel.key to s("squishy"), S.PressWobble.key to i(20),
                S.WallpaperEffect.key to s("snow"), S.WallpaperEffectAmount.key to i(35),
            ),
        ),
        LookPreset(
            "Neon", "Vivid glass, coloured light and a swoosh of a new colour each time",
            mapOf(
                S.GlassSaturation.key to i(220), S.GlassBrightness.key to i(4), S.GlassRim.key to i(70), S.GlassGlow.key to i(40),
                S.GlassRimColour.key to s("#B388FF"),
                S.TouchEffect.key to s("swoosh"), S.TouchColour.key to s("rainbow"), S.TouchBrightness.key to i(70), S.TouchSpeed.key to i(90),
                S.PressFeel.key to s("tilt"), S.TouchLight.key to i(60),
                S.WallpaperEffect.key to s("orbs"), S.WallpaperEffectBrightness.key to i(35),
            ),
        ),
        LookPreset(
            "Bubble", "Round, glowing glass; bubbles rise from your finger",
            mapOf(
                S.GlassDefault.refraction.key to i(100), S.GlassGlow.key to i(50), S.GlassRim.key to i(30),
                S.ButtonRefraction.key to i(90),
                S.TouchEffect.key to s("bubbles"), S.TouchAmount.key to i(60), S.PressFeel.key to s("squishy"), S.PressWobble.key to i(80),
            ),
        ),
        LookPreset(
            "Calm", "Quiet glass; a soft light under the finger and no bounce",
            mapOf(
                S.GlassDispersion.key to b(false), S.GlassGlow.key to i(0),
                S.TouchEffect.key to s("light"), S.TouchBrightness.key to i(30), S.TouchColour.key to s("white"),
                S.PressFeel.key to s("squishy"), S.PressWobble.key to i(0), S.TouchLight.key to i(20),
            ),
        ),
        LookPreset(
            "Glass of 0.7", "The glass and presses as they were before 0.8: no rainbow edges, no glow, no effects",
            mapOf(
                S.GlassDepth.key to b(false), S.GlassDispersion.key to b(false), S.GlassGlow.key to i(0),
                S.ButtonRim.key to i(70), S.ButtonTint.key to i(16),
                S.TouchEffect.key to s("none"), S.TouchLight.key to i(0),
            ),
        ),
    )

    fun apply(settings: SettingsStore, preset: LookPreset) = settings.importValues(preset.values, keys)

    /** The preset whose values match the settings now, if any. */
    fun current(settings: SettingsStore): LookPreset? {
        val now = settings.exportValues(keys)
        return presets.firstOrNull { it.values == now }
    }
}
