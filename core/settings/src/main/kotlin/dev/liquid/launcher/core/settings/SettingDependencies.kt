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
        map[LauncherSettings.GlassSwell.key] = LauncherSettings.GlassDepth
        for (s in listOf(LauncherSettings.StudyQuietPeople, LauncherSettings.StudyQuietEmail, LauncherSettings.StudyQuietTags)) {
            map[s.key] = LauncherSettings.StudyQuiet
        }
        map[LauncherSettings.GlassDispersionAmount.key] = LauncherSettings.GlassDispersion
        map[LauncherSettings.StudyCommute.key] = LauncherSettings.StudyLeaveBy
        map[LauncherSettings.StudySpeedBumpTags.key] = LauncherSettings.StudySpeedBump
        map[LauncherSettings.StudyHomeWifi.key] = LauncherSettings.StudyArrival
        map[LauncherSettings.StudyArrivalCharger.key] = LauncherSettings.StudyArrival
        map[LauncherSettings.FastDelay.key] = LauncherSettings.FastOnHomeAnimation
        map[LauncherSettings.HomeScreenFallback.key] = LauncherSettings.RequireHomeScreen
        map[LauncherSettings.ReopenWindow.key] = LauncherSettings.ReopenRecents
        map[LauncherSettings.ReopenOnHint.key] = LauncherSettings.ReopenRecents
        for (s in listOf(
            LauncherSettings.IndexAreasOnly, LauncherSettings.IndexDialColour, LauncherSettings.IndexDialCustom, LauncherSettings.IndexDialOpacity,
            LauncherSettings.IndexDialScrollOpacity,
        )) {
            map[s.key] = LauncherSettings.IndexDial
        }
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
            LauncherSettings.IndexStringTop, LauncherSettings.IndexStringBottom,
            // 0.11.1: the letter areas drive the string.
            LauncherSettings.IndexDial, LauncherSettings.IndexAreasOnly, LauncherSettings.IndexDialColour, LauncherSettings.IndexDialOpacity,
            LauncherSettings.IndexDialScrollOpacity,
        )
        val railOnly = listOf(LauncherSettings.IndexRailHeight, LauncherSettings.IndexRailLift, LauncherSettings.IndexRailInset)
        val launcherButtonsOnly = listOf(
            LauncherSettings.ButtonStyle, LauncherSettings.ButtonShape, LauncherSettings.ButtonTint, LauncherSettings.ButtonRim,
            LauncherSettings.ButtonRefraction,
        )
        val realGlassOnly = listOf(
            LauncherSettings.GlassBevel, LauncherSettings.GlassThickness, LauncherSettings.GlassSpecular, LauncherSettings.GlassShade,
            LauncherSettings.GlassGrain, LauncherSettings.GlassSwell, LauncherSettings.GlassDispersionAmount,
        )
        mapOf(
            LauncherSettings.PillStudyGlyph.key to (look to setOf("glyph")),
            LauncherSettings.PillStudyFrame.key to (look to setOf("glyph", "app", "picture")),
            LauncherSettings.PillStudyRing.key to (look to setOf("glyph", "app", "picture")),
            LauncherSettings.PillStudyTint.key to (look to setOf("glyph", "app", "picture", "circle")),
            LauncherSettings.IndexLetterCustom.key to (LauncherSettings.IndexLetterColour to setOf("custom")),
            LauncherSettings.IndexDialCustom.key to (LauncherSettings.IndexDialColour to setOf("custom")),
        ) + stringOnly.associate { it.key to (index to setOf("string")) } + railOnly.associate { it.key to (index to setOf("rail")) } +
            realGlassOnly.associate { it.key to (LauncherSettings.GlassEngine to setOf("real")) } +
            launcherButtonsOnly.associate { it.key to (LauncherSettings.ThemeControls to setOf("launcher")) }
    }

    /** Why [setting] does nothing now ("Only when “Haptic feedback” is on"), or null when it matters (W4). */
    fun whyOff(setting: Setting<*>, store: SettingsStore): String? {
        choiceParents[setting.key]?.let { (choice, values) ->
            if (store.get(choice) !in values) {
                return "Only when \u201C${choice.title}\u201D is " + values.joinToString(" or ") { "\u201C${choice.labelOf(it)}\u201D" }
            }
        }
        val parent = parents[setting.key] ?: return null
        return if (store.get(parent)) null else "Only when \u201C${parent.title}\u201D is on"
    }

    fun isVisible(setting: Setting<*>, store: SettingsStore): Boolean {
        choiceParents[setting.key]?.let { (choice, values) -> if (store.get(choice) !in values) return false }
        val parent = parents[setting.key] ?: return true
        return store.get(parent)
    }
}
