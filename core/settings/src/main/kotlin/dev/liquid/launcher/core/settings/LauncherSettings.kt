package dev.liquid.launcher.core.settings

/**
 * The registry. Every option of the launcher is declared here (or, later, in a feature's own
 * registry object that is added to [all]).
 */
object LauncherSettings {

    object Sections {
        val Redirect = SettingsSection("redirect", "Redirect engine", "When and how the launcher is brought to the front")
        val Detection = SettingsSection("detection", "Detection", "How HONOR Home and recents are recognised")
        val Exclusions = SettingsSection("exclusions", "Exclusions and safety", "When not to redirect, loop guard")
        val Handover = SettingsSection("handover", "Hand-over", "How the launcher appears after a redirect")
        val Home = SettingsSection("home", "Home screen", "Temporary home screen options for this build")
        val Glass = SettingsSection("glass", "Glass", "Liquid glass quality")
        val Log = SettingsSection("log", "Debug log", "What is recorded")

        val all = listOf(Redirect, Detection, Exclusions, Handover, Home, Glass, Log)
    }

    // ---- Redirect engine ----

    val RedirectEnabled = BoolSetting(
        key = "redirect.enabled", default = true,
        title = "Redirect enabled",
        summary = "Bring the launcher to the front whenever the system launcher would be shown",
        section = "redirect", keywords = "on off master switch accessibility",
    )
    val OnHome = BoolSetting(
        key = "redirect.onHome", default = true,
        title = "When the system launcher appears",
        summary = "Replaces the \"Activity launched\" macro",
        section = "redirect", keywords = "trigger home honor",
    )
    val OnHomeDelay = IntSetting(
        key = "redirect.onHomeDelay", default = 160, min = 0, max = 1000, step = 10, unit = "ms",
        title = "Delay after the system launcher appears",
        summary = "Recents is checked again when the delay ends. Lower is faster; too low can interrupt opening recents",
        section = "redirect", keywords = "delay latency flash",
    )
    val RequireHomeScreen = BoolSetting(
        key = "redirect.requireHomeScreen", default = true,
        title = "Wait until the home screen is visible",
        summary = "While you are opening recents the system launcher's home screen is not shown yet, " +
            "so waiting for it keeps the redirect from interrupting recents",
        section = "redirect", keywords = "home screen signature workspace recents interrupt",
    )
    val FastWhenProven = BoolSetting(
        key = "redirect.fastWhenProven", default = true,
        title = "Shorten the delay once proven safe",
        summary = "After enough redirects and recents openings have shown that the home screen check is " +
            "reliable on this phone, the short delay below is used instead. Diagnostics shows the counts",
        section = "redirect", keywords = "fast adaptive learn delay",
    )
    val FastDelay = IntSetting(
        key = "redirect.fastDelay", default = 30, min = 0, max = 200, step = 10, unit = "ms",
        title = "Short delay",
        section = "redirect", keywords = "fast delay",
    )
    val HomeScreenFallback = IntSetting(
        key = "redirect.homeScreenFallback", default = 700, min = 300, max = 3000, step = 100, unit = "ms",
        title = "Redirect anyway after",
        summary = "If the home screen never becomes visible (for example after an update renamed it)",
        section = "redirect", keywords = "fallback timeout home screen",
    )
    val OnRecentsExit = BoolSetting(
        key = "redirect.onRecentsExit", default = true,
        title = "When leaving recents to home",
        summary = "Replaces the \"Screen content\" macro",
        section = "redirect", keywords = "trigger overview recents",
    )
    val OnRecentsExitDelay = IntSetting(
        key = "redirect.onRecentsExitDelay", default = 80, min = 0, max = 1000, step = 10, unit = "ms",
        title = "Delay after leaving recents",
        section = "redirect", keywords = "delay overview",
    )
    val ScreenOffMode = ChoiceSetting(
        key = "redirect.screenOff", default = "always",
        options = listOf(
            "always" to "Always",
            "home" to "Only if a home screen was in front",
            "never" to "Never",
        ),
        title = "When the screen turns off",
        summary = "Places the launcher in front while the screen is off, so it is already there on unlock",
        section = "redirect", keywords = "lock unlock screen off trigger",
    )
    val ScreenOffDelay = IntSetting(
        key = "redirect.screenOffDelay", default = 1000, min = 0, max = 5000, step = 50, unit = "ms",
        title = "Delay after screen off",
        section = "redirect", keywords = "delay lock",
    )
    val UnlockCheck = BoolSetting(
        key = "redirect.unlockCheck", default = true,
        title = "Check again after unlock",
        summary = "If the system launcher is in front right after unlocking, redirect",
        section = "redirect", keywords = "unlock user present",
    )

    // ---- Detection ----

    val SystemLauncherPackage = TextSetting(
        key = "detect.systemLauncher", default = "", placeholder = "Automatic (current default launcher)",
        title = "System launcher package",
        summary = "Leave empty to use the phone's default launcher",
        section = "detection", keywords = "honor home package com.hihonor.android.launcher",
    )
    val HomeClasses = StringListSetting(
        key = "detect.homeClasses", default = emptyList(),
        title = "Home activity classes",
        summary = "Leave empty to use the system launcher's home activities automatically",
        section = "detection", keywords = "DrawerLauncher activity class",
    )
    val RecentsIds = StringListSetting(
        key = "detect.recentsIds",
        default = listOf("overview_panel_launcher3", "overview_panel"),
        title = "Recents view ids",
        summary = "If any of these ids is on screen, recents is open. Short names use the system launcher's package",
        section = "detection", keywords = "overview panel view id recents signature",
    )
    val RecentsIdSubstrings = StringListSetting(
        key = "detect.recentsIdSubstrings", default = emptyList(),
        title = "Recents id fragments",
        summary = "Slower fallback: any view id containing one of these texts counts as recents",
        section = "detection", keywords = "substring fuzzy rename update",
    )
    val HomeIds = StringListSetting(
        key = "detect.homeIds", default = listOf("workspace", "hotseat"),
        title = "Home screen view ids",
        summary = "All of these are visible when the system launcher shows its home screen. Leave empty to switch the check off",
        section = "detection", keywords = "home workspace hotseat view id signature",
    )
    val LearnedIds = StringListSetting(
        key = "detect.learnedIds", default = emptyList(),
        title = "Learned recents ids",
        summary = "Filled in by calibration",
        section = "detection", keywords = "calibration learned",
    )
    val PollInterval = IntSetting(
        key = "detect.pollInterval", default = 250, min = 100, max = 1000, step = 50, unit = "ms",
        title = "Check interval while the system launcher is in front",
        summary = "A safety net in case the system launcher sends no events",
        section = "detection", keywords = "poll interval battery",
    )

    // ---- Exclusions and safety ----

    val ExcludeInCall = BoolSetting(
        key = "exclude.inCall", default = true,
        title = "Do not redirect during calls",
        section = "exclusions", keywords = "phone call ringing",
    )
    val ExcludeSplitScreen = BoolSetting(
        key = "exclude.splitScreen", default = true,
        title = "Do not redirect in split screen",
        section = "exclusions", keywords = "multi window split",
    )
    val SystemHomePause = IntSetting(
        key = "redirect.systemHomePause", default = 60, min = 5, max = 600, step = 5, unit = "s",
        title = "\"Open system launcher\" pause",
        summary = "How long the redirect stays off after you open the system launcher on purpose",
        section = "exclusions", keywords = "honor home pause open",
    )
    val GuardCount = IntSetting(
        key = "guard.count", default = 4, min = 2, max = 10, unit = "redirects",
        title = "Loop guard: redirects",
        summary = "This many redirects inside the time window pauses the engine",
        section = "exclusions", keywords = "loop guard safety",
    )
    val GuardWindow = IntSetting(
        key = "guard.window", default = 3, min = 1, max = 10, unit = "s",
        title = "Loop guard: time window",
        section = "exclusions", keywords = "loop guard safety",
    )
    val GuardPause = IntSetting(
        key = "guard.pause", default = 30, min = 5, max = 300, step = 5, unit = "s",
        title = "Loop guard: pause length",
        section = "exclusions", keywords = "loop guard safety",
    )

    // ---- Hand-over ----

    val SettleStyle = ChoiceSetting(
        key = "handover.settle", default = "scale",
        options = listOf("scale" to "Scale in", "fade" to "Fade in", "none" to "None"),
        title = "Content animation on return",
        section = "handover", keywords = "animation settle return home",
    )
    val SettleDuration = IntSetting(
        key = "handover.settleDuration", default = 180, min = 0, max = 400, step = 10, unit = "ms",
        title = "Content animation length",
        section = "handover", keywords = "animation duration speed",
    )
    val ExcludeFromRecents = BoolSetting(
        key = "handover.excludeFromRecents", default = true,
        title = "Hide the launcher from recents",
        section = "handover", keywords = "recents card overview",
    )

    // ---- Home (temporary, replaced by the real home screen in Phase 1B) ----

    val HomeColumns = IntSetting(
        key = "home.columns", default = 5, min = 3, max = 8,
        title = "App grid columns",
        section = "home", keywords = "grid columns",
    )
    val HomeLabels = BoolSetting(
        key = "home.labels", default = true,
        title = "Show app labels",
        section = "home", keywords = "labels names text",
    )
    val Clock24h = BoolSetting(
        key = "home.clock24h", default = true,
        title = "24-hour clock",
        section = "home", keywords = "clock time format",
    )

    // ---- Glass ----

    val GlassQuality = ChoiceSetting(
        key = "glass.quality", default = "full",
        options = listOf(
            "full" to "Full (refraction and blur)",
            "frosted" to "Frosted (blur only)",
            "tint" to "Tint (no blur)",
        ),
        title = "Glass quality",
        summary = "Run the glass benchmark in Diagnostics to see which tier holds the frame rate",
        section = "glass", keywords = "liquid glass blur refraction performance",
    )

    val GlassAuto = BoolSetting(
        key = "glass.auto", default = true,
        title = "Lower quality automatically",
        summary = "Never use a tier the glass benchmark showed this phone cannot hold at full frame rate",
        section = "glass", keywords = "performance fallback benchmark automatic",
    )
    val GlassCap = ChoiceSetting(
        key = "state.glassCap", default = "full",
        options = listOf("full" to "Full", "frosted" to "Frosted", "tint" to "Tint"),
        title = "Best tier the benchmark allows", section = "state", hidden = true,
    )

    // ---- Debug log ----

    val LogEnabled = BoolSetting(
        key = "log.enabled", default = true,
        title = "Record debug log",
        section = "log", keywords = "debug log",
    )
    val LogVerbosity = ChoiceSetting(
        key = "log.verbosity", default = "decisions",
        options = listOf(
            "events" to "Events only",
            "decisions" to "Events and decisions",
            "tree" to "Everything, including view ids",
        ),
        title = "Detail level",
        section = "log", keywords = "verbosity debug",
    )
    val LogSize = IntSetting(
        key = "log.size", default = 2000, min = 200, max = 10000, step = 200, unit = "lines",
        title = "Log size",
        section = "log", keywords = "size lines",
    )

    // ---- Internal state (hidden) ----

    val LastSystemLauncherVersion = TextSetting(
        key = "state.systemLauncherVersion", default = "",
        title = "Last seen system launcher version", section = "state", hidden = true,
    )
    val LastSystemLauncher = TextSetting(
        key = "state.lastSystemLauncher", default = "",
        title = "Last detected system launcher", section = "state", hidden = true,
    )
    val StatsKey = TextSetting(
        key = "stats.key", default = "", title = "What the counts below were collected for",
        section = "state", hidden = true,
    )
    val StatsHomeSeen = counter("stats.homeSeen", "Redirects with the home screen visible")
    val StatsHomeUnseen = counter("stats.homeUnseen", "Redirects without the home screen visible")
    val StatsRecentsSeen = counter("stats.recentsSeen", "Recents openings although the home screen was visible")
    val StatsRecentsUnseen = counter("stats.recentsUnseen", "Recents openings with the home screen not visible")
    val StatsInterrupted = counter("stats.interrupted", "Recents openings that a redirect interrupted")
    val StatsFallbacks = counter("stats.fallbacks", "Redirects sent without the home screen becoming visible")
    val DetectorVerified = BoolSetting(
        key = "state.detectorVerified", default = false,
        title = "Recents detection confirmed", section = "state", hidden = true,
    )
    val SetupAppLaunchDone = BoolSetting(
        key = "state.setupAppLaunch", default = false,
        title = "App launch set to manual", section = "state", hidden = true,
    )
    val SetupEmptyHomeDone = BoolSetting(
        key = "state.setupEmptyHome", default = false,
        title = "System launcher home emptied", section = "state", hidden = true,
    )
    val SetupMacrosDone = BoolSetting(
        key = "state.setupMacros", default = false,
        title = "MacroDroid macros disabled", section = "state", hidden = true,
    )

    private fun counter(key: String, title: String) = IntSetting(
        key = key, default = 0, min = 0, max = Int.MAX_VALUE, title = title, section = "state", hidden = true,
    )

    val all: List<Setting<*>> = listOf(
        RedirectEnabled, OnHome, OnHomeDelay, RequireHomeScreen, FastWhenProven, FastDelay, HomeScreenFallback,
        OnRecentsExit, OnRecentsExitDelay,
        ScreenOffMode, ScreenOffDelay, UnlockCheck,
        SystemLauncherPackage, HomeClasses, RecentsIds, RecentsIdSubstrings, HomeIds, LearnedIds, PollInterval,
        ExcludeInCall, ExcludeSplitScreen, SystemHomePause, GuardCount, GuardWindow, GuardPause,
        SettleStyle, SettleDuration, ExcludeFromRecents,
        HomeColumns, HomeLabels, Clock24h,
        GlassQuality, GlassAuto, GlassCap,
        LogEnabled, LogVerbosity, LogSize,
        LastSystemLauncherVersion, LastSystemLauncher, DetectorVerified,
        StatsKey, StatsHomeSeen, StatsHomeUnseen, StatsRecentsSeen, StatsRecentsUnseen, StatsInterrupted, StatsFallbacks, SetupAppLaunchDone, SetupEmptyHomeDone, SetupMacrosDone,
    )

    fun inSection(sectionId: String): List<Setting<*>> = all.filter { it.section == sectionId && !it.hidden }

    /** Simple contains-all-words search; the fuzzy engine arrives with universal search. */
    fun search(query: String): List<Setting<*>> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return all.filter { s -> !s.hidden && words.all { it in s.searchText } }
    }
}
