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
        map[LauncherSettings.StudyCommute.key] = LauncherSettings.StudyLeaveBy
        map[LauncherSettings.StudySpeedBumpTags.key] = LauncherSettings.StudySpeedBump
        map[LauncherSettings.StudyHomeWifi.key] = LauncherSettings.StudyArrival
        map[LauncherSettings.StudyArrivalCharger.key] = LauncherSettings.StudyArrival
        map[LauncherSettings.FastDelay.key] = LauncherSettings.FastOnHomeAnimation
        map[LauncherSettings.HomeScreenFallback.key] = LauncherSettings.RequireHomeScreen
        map[LauncherSettings.ReopenWindow.key] = LauncherSettings.ReopenRecents
        map[LauncherSettings.ReopenOnHint.key] = LauncherSettings.ReopenRecents
        for (s in listOf(LauncherSettings.ThemedIconsWhere, LauncherSettings.ThemedIconsUncovered, LauncherSettings.ThemedIconsPacks, LauncherSettings.ThemedIconsColors, LauncherSettings.ThemedIconsStyle)) {
            map[s.key] = LauncherSettings.ThemedIcons
        }
        for (s in listOf(LauncherSettings.NoticePosition, LauncherSettings.NoticeDuration, LauncherSettings.NoticeStyle, LauncherSettings.NoticeIcons) + LauncherSettings.noticeKinds.values) {
            map[s.key] = LauncherSettings.NoticesEnabled
        }
        for (s in listOf(LauncherSettings.HapticsStrength, LauncherSettings.HapticDrawer, LauncherSettings.HapticPickUp)) {
            map[s.key] = LauncherSettings.HapticsEnabled
        }
        map
    }

    /** Options that only matter for some values of a choice (0.9.1): the key, the choice and those values. */
    val choiceParents: Map<String, Pair<ChoiceSetting, Set<String>>> by lazy {
        val look = LauncherSettings.PillStudyLook
        val index = LauncherSettings.IndexStyle
        val stringOnly = listOf(
            LauncherSettings.IndexVisibility, LauncherSettings.IndexReach, LauncherSettings.IndexEnds, LauncherSettings.IndexLetterSize,
            LauncherSettings.IndexSpacing, LauncherSettings.IndexBubble, LauncherSettings.IndexBubblePlace, LauncherSettings.IndexLetterGap,
            LauncherSettings.IndexBubbleGap, LauncherSettings.IndexTrack, LauncherSettings.IndexSpring, LauncherSettings.IndexHideDelay,
        )
        val railOnly = listOf(LauncherSettings.IndexRailHeight, LauncherSettings.IndexRailLift, LauncherSettings.IndexRailInset)
        mapOf(
            LauncherSettings.PillStudyGlyph.key to (look to setOf("glyph")),
            LauncherSettings.PillStudyFrame.key to (look to setOf("glyph", "app", "picture")),
            LauncherSettings.PillStudyRing.key to (look to setOf("glyph", "app", "picture")),
            LauncherSettings.PillStudyTint.key to (look to setOf("glyph", "app", "picture", "circle")),
        ) + stringOnly.associate { it.key to (index to setOf("string")) } + railOnly.associate { it.key to (index to setOf("rail")) }
    }

    fun isVisible(setting: Setting<*>, store: SettingsStore): Boolean {
        choiceParents[setting.key]?.let { (choice, values) -> if (store.get(choice) !in values) return false }
        val parent = parents[setting.key] ?: return true
        return store.get(parent)
    }
}
