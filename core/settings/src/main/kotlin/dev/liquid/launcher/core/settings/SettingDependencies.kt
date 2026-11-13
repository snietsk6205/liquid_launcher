package dev.liquid.launcher.core.settings

/** Options that only matter while another (boolean) option is on; settings screens hide them otherwise. */
object SettingDependencies {
    val parents: Map<String, BoolSetting> by lazy {
        val map = HashMap<String, BoolSetting>()
        for (surface in LauncherSettings.glassSurfaces) {
            val custom = surface.custom ?: continue
            listOf(surface.blur, surface.refraction, surface.tint).forEach { map[it.key] = custom }
        }
        map[LauncherSettings.HomeLabelSize.key] = LauncherSettings.HomeLabels
        map[LauncherSettings.FastDelay.key] = LauncherSettings.FastWhenProven
        map[LauncherSettings.HomeScreenFallback.key] = LauncherSettings.RequireHomeScreen
        map[LauncherSettings.ReopenWindow.key] = LauncherSettings.ReopenRecents
        map
    }

    fun isVisible(setting: Setting<*>, store: SettingsStore): Boolean {
        val parent = parents[setting.key] ?: return true
        return store.get(parent)
    }
}
