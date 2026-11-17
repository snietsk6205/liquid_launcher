package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore

/**
 * Glass presets (P4, 0.9.12): the real glass's values in one tap, in "Tune the glass". A preset sets the overall
 * glass (blur, bend, tint, colour) and every value of the real glass; the light's angle and colour, the shadow, the
 * glow, the per-surface values and the quality stay as they are. Values equal to a default are left out, so the
 * preset in use is recognised after a slider is moved back.
 */
object GlassPresets {
    private val S = LauncherSettings
    private fun i(v: Int) = "i:$v"
    private fun b(v: Boolean) = "b:$v"

    /** The keys a preset sets; any it does not name go back to their defaults. */
    val keys: Set<String> by lazy {
        listOf(
            S.GlassDefault.blur, S.GlassDefault.refraction, S.GlassDefault.tint, S.GlassSaturation, S.GlassBrightness,
            S.GlassDepth, S.GlassSwell, S.GlassDispersion, S.GlassDispersionAmount, S.GlassBevel, S.GlassThickness,
            S.GlassSpecular, S.GlassShade, S.GlassGrain, S.GlassRim,
        ).map { it.key }.toSet()
    }

    val presets = listOf(
        LookPreset("Real glass", "The launcher's glass as it ships: a clear bend, rainbow edges, light on one side", emptyMap()),
        LookPreset(
            "Clear", "Hardly any blur, a strong bend and bright edges",
            mapOf(
                S.GlassDefault.blur.key to i(2), S.GlassDefault.refraction.key to i(70), S.GlassDefault.tint.key to i(10),
                S.GlassSaturation.key to i(140), S.GlassBevel.key to i(18), S.GlassThickness.key to i(60),
                S.GlassSpecular.key to i(75), S.GlassShade.key to i(35), S.GlassGrain.key to i(5),
            ),
        ),
        LookPreset(
            "Frosted", "Milky blur, a soft bend and a fine grain",
            mapOf(
                S.GlassDefault.blur.key to i(14), S.GlassDefault.refraction.key to i(40), S.GlassDefault.tint.key to i(26),
                S.GlassSaturation.key to i(115), S.GlassDepth.key to b(false), S.GlassDispersion.key to b(false),
                S.GlassBevel.key to i(14), S.GlassThickness.key to i(30), S.GlassSpecular.key to i(45), S.GlassShade.key to i(30),
                S.GlassGrain.key to i(30),
            ),
        ),
        LookPreset(
            "Thick", "A heavy slab: a deep bend, wide rainbow edges and a swelling middle",
            mapOf(
                S.GlassDefault.blur.key to i(3), S.GlassDefault.refraction.key to i(85), S.GlassDefault.tint.key to i(8),
                S.GlassBevel.key to i(24), S.GlassThickness.key to i(85), S.GlassSpecular.key to i(70), S.GlassShade.key to i(55),
                S.GlassGrain.key to i(5), S.GlassDispersionAmount.key to i(70), S.GlassSwell.key to i(70),
            ),
        ),
        LookPreset(
            "Subtle", "Barely there: a light bend and a faint edge",
            mapOf(
                S.GlassDefault.blur.key to i(6), S.GlassDefault.refraction.key to i(35), S.GlassDefault.tint.key to i(14),
                S.GlassSaturation.key to i(120), S.GlassDepth.key to b(false), S.GlassDispersion.key to b(false),
                S.GlassBevel.key to i(10), S.GlassThickness.key to i(25), S.GlassSpecular.key to i(30), S.GlassShade.key to i(15),
                S.GlassGrain.key to i(0),
            ),
        ),
        LookPreset(
            "Your keyboard", "The frost of your WM Keyboard theme: blur 9, colour 125 %, edge light 0.86, bend 0.6, rainbow 0.25, no grain",
            mapOf(
                S.GlassDefault.blur.key to i(9), S.GlassDefault.tint.key to i(10), S.GlassSaturation.key to i(125),
                S.GlassSpecular.key to i(85), S.GlassDispersionAmount.key to i(25), S.GlassGrain.key to i(0),
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
