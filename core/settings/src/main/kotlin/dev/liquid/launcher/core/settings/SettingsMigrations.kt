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
}
