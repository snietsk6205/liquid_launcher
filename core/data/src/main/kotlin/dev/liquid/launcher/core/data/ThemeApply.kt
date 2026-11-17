package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.settings.ColorSetting
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore

/** Putting a theme to work (P4, 0.9.13): its controls through Settings → Buttons, its colours through Settings → Theme. */
object ThemeApply {
    /** The theme in use: the one imported, else your keyboard theme. */
    fun current(settings: SettingsStore): LiquidTheme =
        settings.get(LauncherSettings.ThemeJson).takeIf { it.isNotBlank() }?.let { LiquidThemes.decode(it) } ?: LiquidThemes.Keyboard

    /** Stores [theme] as the theme of buttons and chips (an edit), leaving Settings → Theme alone. */
    fun save(settings: SettingsStore, theme: LiquidTheme) {
        if (theme == LiquidThemes.Keyboard) settings.reset(LauncherSettings.ThemeJson) else settings.set(LauncherSettings.ThemeJson, LiquidThemes.encode(theme))
        settings.set(LauncherSettings.ThemeControls, "theme")
    }

    /** Makes [theme] the theme of buttons and chips, and the launcher's accent and glass colour. */
    fun use(settings: SettingsStore, theme: LiquidTheme) {
        if (theme == LiquidThemes.Keyboard) settings.reset(LauncherSettings.ThemeJson) else settings.set(LauncherSettings.ThemeJson, LiquidThemes.encode(theme))
        settings.set(LauncherSettings.ThemeControls, "theme")
        colours(settings, theme)
    }

    /** Writes [theme]'s accent and glass tint to Settings → Theme (they stay yours to change). */
    fun colours(settings: SettingsStore, theme: LiquidTheme) {
        settings.set(LauncherSettings.ThemeAccent, ColorSetting.format(theme.accent.toInt()))
        settings.set(LauncherSettings.ThemeGlassColor, ColorSetting.format(theme.glassTint.toInt()))
    }

    /**
     * Once, on the first start of a launcher with themes: your keyboard theme's accent and glass colour, where you
     * never chose your own (a colour you set is kept).
     */
    fun firstRun(settings: SettingsStore) {
        if (settings.get(LauncherSettings.ThemeColoursApplied)) return
        settings.set(LauncherSettings.ThemeColoursApplied, true)
        if (settings.get(LauncherSettings.ThemeControls) != "theme") return
        val theme = current(settings)
        if (!settings.isChanged(LauncherSettings.ThemeAccent)) {
            settings.set(LauncherSettings.ThemeAccent, ColorSetting.format(theme.accent.toInt()))
        }
        if (!settings.isChanged(LauncherSettings.ThemeGlassColor)) {
            settings.set(LauncherSettings.ThemeGlassColor, ColorSetting.format(theme.glassTint.toInt()))
        }
    }
}
