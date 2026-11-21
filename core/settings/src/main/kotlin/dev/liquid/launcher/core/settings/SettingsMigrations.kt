package dev.liquid.launcher.core.settings

import dev.liquid.launcher.core.common.DebugLog

/** One-time changes to settings that a new version asks for, each run once and never again. */
object SettingsMigrations {

    /**
     * 0.11.0: with the letter circle, the letters at the edge hide until you use them (Med asked for it: the edge is
     * his Back swipe). "Always" becomes "Hidden until you use them", once; any other choice stays.
     */
    fun migrate0110(settings: SettingsStore) {
        if (settings.get(LauncherSettings.IndexMigrated0110)) return
        if (settings.isChanged(LauncherSettings.IndexVisibility) && settings.get(LauncherSettings.IndexVisibility) == "always") {
            settings.set(LauncherSettings.IndexVisibility, "touch")
            DebugLog.event("Settings", "0.11.0: the letters hide until you use them (the letter circle, or the edge); they were shown always")
        }
        settings.set(LauncherSettings.IndexMigrated0110, true)
    }

    /**
     * 0.11.1: the letter circle became letter areas. If the circle had been moved or resized (by holding and dragging it,
     * in 0.11.0), the first area takes its place and size, on the right; "The edge pulls the letters too" becomes the
     * opposite of "Letters only from the areas". Once.
     */
    fun migrate0111(settings: SettingsStore) {
        if (settings.get(LauncherSettings.IndexMigrated0111)) return
        val moved = settings.isChanged(LauncherSettings.IndexDialX) || settings.isChanged(LauncherSettings.IndexDialY) ||
            settings.isChanged(LauncherSettings.IndexDialSize)
        if (moved && !settings.isChanged(LauncherSettings.IndexAreas)) {
            val area = LetterArea(
                right = true,
                across = settings.get(LauncherSettings.IndexDialX),
                down = settings.get(LauncherSettings.IndexDialY),
                sizeDp = settings.get(LauncherSettings.IndexDialSize).coerceIn(LetterArea.MIN_SIZE, LetterArea.MAX_SIZE),
            )
            settings.set(LauncherSettings.IndexAreas, listOf(LetterAreas.format(area)))
            DebugLog.event("Settings", "0.11.1: the letter circle is now a letter area, where it was (${area.across} % across, ${area.down} % down)")
        }
        if (settings.isChanged(LauncherSettings.IndexDialEdge)) {
            settings.set(LauncherSettings.IndexAreasOnly, !settings.get(LauncherSettings.IndexDialEdge))
        }
        settings.set(LauncherSettings.IndexMigrated0111, true)
    }
}
