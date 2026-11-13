package dev.liquid.launcher.core.settings

/**
 * The registry. Every option of the launcher is declared here (or, later, in a feature's own
 * registry object that is added to [all]).
 */
object LauncherSettings {

    object Sections {
        val Home = SettingsSection("home", "Home screen", "Grid, icons, labels and the clock")
        val Icons = SettingsSection("icons", "Icons", "Icon packs for home and drawer, and icons the pack does not cover")
        val Clock = SettingsSection("clock", "Clock", "Time format, size, date line and glass plate. Resize it in edit mode; more styles come with widgets")
        val Gestures = SettingsSection("gestures", "Gestures", "What swipes, taps and presses on the home screen do")
        val Pill = SettingsSection("pill", "Pill", "The glass bar: its items, size, position and gestures")
        val Drawer = SettingsSection("drawer", "App drawer", "Layout, sorting, search, tabs and the work profile")
        val Tags = SettingsSection("tags", "Drawer tabs and tags", "Your own drawer pages built from tags; rename, reorder, remove")
        val DrawerMotion = SettingsSection("drawerMotion", "Drawer motion", "Opening transition, scroll bar and scroll effects")
        val Menu = SettingsSection("menu", "Long-press menu", "Which entries appear and in what order")
        val Search = SettingsSection("search", "Search", "Swipe-down search and web search")
        val Glass = SettingsSection("glass", "Liquid glass", "Quality, blur, refraction and tint, overall and per surface")

        val Redirect = SettingsSection("redirect", "Redirect engine", "When and how the launcher is brought to the front", "Redirect")
        val Detection = SettingsSection("detection", "Detection", "How the system launcher's home screen and recents are recognised", "Redirect")
        val Exclusions = SettingsSection("exclusions", "Exclusions and safety", "When not to redirect, loop guard", "Redirect")
        val Handover = SettingsSection("handover", "Hand-over", "How the launcher appears after a redirect", "Redirect")
        val Log = SettingsSection("log", "Debug log", "What is recorded", "Redirect")

        val all = listOf(Home, Icons, Clock, Gestures, Pill, Drawer, Tags, DrawerMotion, Menu, Search, Glass, Redirect, Detection, Exclusions, Handover, Log)
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
        title = "Only redirect over the home screen or drawer",
        summary = "Waits until the system launcher's home screen (or its app drawer) is visible. " +
            "When leaving recents this is strict: closing recents into an app, a lock screen or anything else never redirects",
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
    val ReopenRecents = BoolSetting(
        key = "redirect.reopenRecents", default = true,
        title = "Reopen recents if a redirect covered it",
        summary = "A slow recents gesture can look like going home at first. If recents opens just after a redirect, " +
            "it is opened again over the launcher",
        section = "redirect", keywords = "recents interrupted recovery reopen overview",
    )
    val ReopenWindow = IntSetting(
        key = "redirect.reopenWindow", default = 600, min = 200, max = 1500, step = 50, unit = "ms",
        title = "Counts as covered within",
        summary = "How soon after the redirect recents must open",
        section = "redirect", keywords = "recents interrupted window",
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
    val DrawerIds = StringListSetting(
        key = "detect.drawerIds", default = listOf("apps_list_view", "all_apps_tabs_view_pager"),
        title = "System launcher drawer view ids",
        summary = "If any of these is visible, the system launcher's own app drawer is showing, which a redirect may replace",
        section = "detection", keywords = "drawer apps list view id signature",
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

    // ---- Home screen ----

    val HomeColumns = IntSetting(
        key = "home.columns", default = 5, min = 2, max = 12,
        title = "Columns",
        section = "home", keywords = "grid columns",
    )
    val HomeRows = IntSetting(
        key = "home.rows", default = 9, min = 2, max = 16,
        title = "Rows",
        section = "home", keywords = "grid rows",
    )
    val HomeIconSize = IntSetting(
        key = "home.iconSize", default = 70, min = 40, max = 100, step = 5, unit = "%",
        title = "Icon size",
        summary = "Share of a grid cell the icon fills",
        section = "home", keywords = "icon size scale",
    )
    val HomeLabels = BoolSetting(
        key = "home.labels", default = false,
        title = "Show app labels",
        section = "home", keywords = "labels names text",
    )
    val HomeLabelSize = IntSetting(
        key = "home.labelSize", default = 11, min = 8, max = 16, unit = "sp",
        title = "Label size",
        section = "home", keywords = "label text size font",
    )
    val HomeMarginSide = IntSetting(
        key = "home.marginSide", default = 16, min = 0, max = 64, step = 2, unit = "dp",
        title = "Side margin",
        section = "home", keywords = "margin padding left right",
    )
    val HomeMarginTop = IntSetting(
        key = "home.marginTop", default = 8, min = 0, max = 160, step = 4, unit = "dp",
        title = "Top margin",
        section = "home", keywords = "margin padding top",
    )
    val HomeMarginBottom = IntSetting(
        key = "home.marginBottom", default = 8, min = 0, max = 200, step = 4, unit = "dp",
        title = "Bottom margin",
        summary = "Space between the grid and the pill",
        section = "home", keywords = "margin padding bottom",
    )
    val Clock24h = BoolSetting(
        key = "home.clock24h", default = true,
        title = "24-hour time",
        section = "clock", keywords = "clock time format",
    )
    val ClockSize = IntSetting(
        key = "home.clockSize", default = 100, min = 40, max = 160, step = 5, unit = "%",
        title = "Text size",
        section = "clock", keywords = "clock size font",
    )
    val ClockDate = BoolSetting(
        key = "home.clockDate", default = false,
        title = "Show the date",
        section = "clock", keywords = "clock date",
    )

    // ---- Icons ----

    val IconPack = TextSetting(
        key = "icons.pack", default = "",
        title = "Icon pack", section = "icons", keywords = "icon pack theme", hidden = true,
    )
    val IconPackHome = TextSetting(
        key = "icons.packHome", default = SAME_AS_MAIN,
        title = "Icon pack on the home screen", section = "icons", keywords = "icon pack home", hidden = true,
    )
    val IconPackDrawer = TextSetting(
        key = "icons.packDrawer", default = SAME_AS_MAIN,
        title = "Icon pack in the drawer", section = "icons", keywords = "icon pack drawer", hidden = true,
    )
    val IconMask = BoolSetting(
        key = "icons.mask", default = true,
        title = "Fit other icons to the pack",
        summary = "Apps the pack has no icon for get the pack's background, shape and overlay, if it has them",
        section = "icons", keywords = "icon mask shape uncovered adaptive",
    )

    /** Value of the home and drawer pack settings that means "use the main pack". */
    const val SAME_AS_MAIN = "="

    // ---- Gestures ----

    val GestureSwipeUp = ActionSetting(
        key = "gesture.swipeUp", default = "drawer",
        title = "Swipe up", section = "gestures", keywords = "gesture swipe up drawer",
    )
    val GestureSwipeDown = ActionSetting(
        key = "gesture.swipeDown", default = "search",
        title = "Swipe down", section = "gestures", keywords = "gesture swipe down search notifications",
    )
    val GestureDoubleTap = ActionSetting(
        key = "gesture.doubleTap", default = "lock",
        title = "Double tap on empty space", section = "gestures", keywords = "gesture double tap lock",
    )
    val GestureLongPress = ActionSetting(
        key = "gesture.longPress", default = "edit",
        title = "Long press on empty space", section = "gestures", keywords = "gesture long press edit",
    )

    // ---- Pill ----

    val PillEnabled = BoolSetting(
        key = "pill.enabled", default = true,
        title = "Show the pill",
        section = "pill", keywords = "pill bar dock search",
    )

    /** Ordered slot codes, see PillSlot in core/data. Edited with the pill editor, not a generic row. */
    val PillSlots = StringListSetting(
        key = "pill.slots", default = listOf("search", "date"),
        title = "Pill items", section = "pill", hidden = true,
    )
    val PillPosition = ChoiceSetting(
        key = "pill.position", default = "bottom",
        options = listOf("bottom" to "Bottom", "top" to "Top"),
        title = "Position",
        section = "pill", keywords = "position top bottom",
    )
    val PillOffset = IntSetting(
        key = "pill.offset", default = 20, min = 0, max = 400, step = 4, unit = "dp",
        title = "Distance from the edge",
        section = "pill", keywords = "offset margin position",
    )
    val PillWidth = IntSetting(
        key = "pill.width", default = 86, min = 30, max = 100, step = 2, unit = "%",
        title = "Width",
        section = "pill", keywords = "width size",
    )
    val PillHeight = IntSetting(
        key = "pill.height", default = 54, min = 36, max = 96, step = 2, unit = "dp",
        title = "Height",
        section = "pill", keywords = "height size",
    )
    val PillCorner = IntSetting(
        key = "pill.corner", default = 50, min = 0, max = 50, step = 2, unit = "%",
        title = "Corner roundness",
        summary = "50% is a full capsule",
        section = "pill", keywords = "corner radius shape capsule",
    )
    val PillIconSize = IntSetting(
        key = "pill.iconSize", default = 24, min = 14, max = 48, step = 2, unit = "dp",
        title = "Icon size",
        section = "pill", keywords = "icon size",
    )
    val PillHint = TextSetting(
        key = "pill.hint", default = "Search", placeholder = "No text",
        title = "Search item text",
        section = "pill", keywords = "hint placeholder search text",
    )
    val PillTap = ActionSetting(
        key = "pill.tap", default = "search",
        title = "Tap on empty pill space", section = "pill", keywords = "gesture tap",
    )
    val PillLongPress = ActionSetting(
        key = "pill.longPress", default = "settings",
        title = "Long press on the pill", section = "pill", keywords = "gesture long press",
    )
    val PillSwipeUp = ActionSetting(
        key = "pill.swipeUp", default = "drawer",
        title = "Swipe up on the pill", section = "pill", keywords = "gesture swipe",
    )
    val PillSwipeDown = ActionSetting(
        key = "pill.swipeDown", default = "notifications",
        title = "Swipe down on the pill", section = "pill", keywords = "gesture swipe",
    )

    // ---- App drawer ----

    val DrawerColumns = IntSetting(
        key = "drawer.columns", default = 5, min = 3, max = 8,
        title = "Columns",
        section = "drawer", keywords = "grid columns",
    )
    val DrawerIconSize = IntSetting(
        key = "drawer.iconSize", default = 52, min = 32, max = 72, step = 2, unit = "dp",
        title = "Icon size",
        section = "drawer", keywords = "icon size",
    )
    val DrawerLabels = BoolSetting(
        key = "drawer.labels", default = true,
        title = "Show app labels",
        section = "drawer", keywords = "labels names",
    )
    val DrawerFromBottom = BoolSetting(
        key = "drawer.fromBottom", default = true,
        title = "Start from the bottom",
        summary = "The first apps sit next to your thumb and the list grows upwards",
        section = "drawer", keywords = "bottom thumb reverse fill",
    )
    val DrawerAutoKeyboard = BoolSetting(
        key = "drawer.autoKeyboard", default = false,
        title = "Open the keyboard with the drawer",
        summary = "Start typing straight away to filter apps",
        section = "drawer", keywords = "keyboard focus search automatic",
    )
    val DrawerSort = ChoiceSetting(
        key = "drawer.sort", default = "az",
        options = listOf("az" to "Alphabetical", "used" to "Most used", "recent" to "Recently installed"),
        title = "Sort by",
        section = "drawer", keywords = "sort order alphabetical most used recent",
    )
    val DrawerWork = ChoiceSetting(
        key = "drawer.work", default = "tab",
        options = listOf("tab" to "On their own page", "merged" to "Mixed with other apps", "hidden" to "Hidden"),
        title = "Work profile apps",
        section = "drawer", keywords = "work profile island tab page",
    )
    val DrawerCloseOnLaunch = BoolSetting(
        key = "drawer.closeOnLaunch", default = true,
        title = "Close after opening an app",
        section = "drawer", keywords = "close launch",
    )
    val DrawerDim = IntSetting(
        key = "drawer.dim", default = 45, min = 0, max = 90, step = 5, unit = "%",
        title = "Wallpaper dimming",
        section = "drawer", keywords = "dim background wallpaper",
    )
    val DrawerTabsPosition = ChoiceSetting(
        key = "drawer.tabsPosition", default = "top",
        options = listOf("top" to "Top", "bottom" to "Bottom, above the search field"),
        title = "Tabs position",
        summary = "The glass pill with your tabs. Tap a tab to switch, tap it again to jump to the start, hold it for quick options",
        section = "tags", keywords = "tabs pill work apps position top bottom",
    )

    // ---- Drawer motion ----

    val DrawerTransition = ChoiceSetting(
        key = "drawer.transition", default = "depth",
        options = listOf(
            "depth" to "Depth: the home screen sinks back, the drawer rises a little",
            "slide" to "Slide: the drawer is a sheet that follows the finger",
            "fade" to "Fade",
        ),
        title = "Transition",
        section = "drawerMotion", keywords = "transition animation open drawer style",
    )
    val DrawerPillMorph = BoolSetting(
        key = "drawer.pillMorph", default = true,
        title = "The pill becomes the search field",
        summary = "When the pill is at the bottom, it changes shape into the drawer's search field as the drawer opens",
        section = "drawerMotion", keywords = "pill morph search shape continuous",
    )
    val DrawerSpeed = IntSetting(
        key = "drawer.speed", default = 100, min = 50, max = 200, step = 10, unit = "%",
        title = "Speed",
        summary = "How quickly the drawer settles after you let go",
        section = "drawerMotion", keywords = "speed stiffness spring animation",
    )
    val DrawerBounce = IntSetting(
        key = "drawer.bounce", default = 15, min = 0, max = 60, step = 5, unit = "%",
        title = "Bounce",
        section = "drawerMotion", keywords = "bounce spring damping overshoot",
    )
    val DrawerRise = IntSetting(
        key = "drawer.rise", default = 12, min = 0, max = 50, step = 2, unit = "%",
        title = "How far the drawer rises",
        summary = "Depth transition only",
        section = "drawerMotion", keywords = "distance rise translate",
    )
    val DrawerHomeScale = IntSetting(
        key = "drawer.homeScale", default = 8, min = 0, max = 20, unit = "%",
        title = "Home screen shrinks by",
        section = "drawerMotion", keywords = "scale home depth",
    )
    val DrawerHomeBlur = IntSetting(
        key = "drawer.homeBlur", default = 14, min = 0, max = 40, step = 2, unit = "dp",
        title = "Blur behind the drawer",
        summary = "Blurs the wallpaper and home screen while the drawer is open. 0 is the lightest on the battery. " +
            "Off automatically when the glass benchmark found the phone only manages tint glass",
        section = "drawerMotion", keywords = "blur background wallpaper depth",
    )
    val DrawerScrollbar = ChoiceSetting(
        key = "drawer.scrollbar", default = "glass",
        options = listOf("glass" to "Glass handle", "line" to "Thin line", "off" to "Off"),
        title = "Scroll bar",
        summary = "Floats over the apps while you scroll. Drag the handle to move through the list quickly",
        section = "drawerMotion", keywords = "scroll bar fast scroller handle thumb",
    )
    val DrawerScrollBubble = BoolSetting(
        key = "drawer.scrollBubble", default = true,
        title = "Letter bubble while dragging the scroll bar",
        section = "drawerMotion", keywords = "letter bubble index fast scroll",
    )
    val DrawerScrollEffect = ChoiceSetting(
        key = "drawer.scrollEffect", default = "elastic",
        options = listOf(
            "elastic" to "Elastic: rows spread apart with speed and spring back",
            "curve" to "Curved: rows shrink towards the top and bottom edge",
            "none" to "None",
        ),
        title = "Scroll effect",
        section = "drawerMotion", keywords = "scroll effect animation elastic curve wheel",
    )
    val DrawerScrollStrength = IntSetting(
        key = "drawer.scrollStrength", default = 50, min = 10, max = 100, step = 10, unit = "%",
        title = "Scroll effect strength",
        section = "drawerMotion", keywords = "scroll effect strength",
    )
    val DrawerOverscroll = ChoiceSetting(
        key = "drawer.overscroll", default = "stretch",
        options = listOf("stretch" to "Stretch", "none" to "None"),
        title = "At the end of the list",
        section = "drawerMotion", keywords = "overscroll stretch bounce edge",
    )

    // ---- Long-press menu ----

    /** Entry ids in display order; see MenuEntry in feature/home. Edited with the menu editor. */
    val MenuEntries = StringListSetting(
        key = "menu.entries", default = listOf("shortcuts", "info", "home", "rename", "icon", "swipe", "tags", "hide", "uninstall"),
        title = "Menu entries", section = "menu", hidden = true,
    )

    // ---- Glass ----

    val GlassQuality = ChoiceSetting(
        key = "glass.quality", default = "full",
        options = listOf(
            "full" to "Full (refraction and blur)",
            "frosted" to "Frosted (blur only)",
            "tint" to "Tint (no blur)",
        ),
        title = "Quality",
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
    val GlassTintMode = ChoiceSetting(
        key = "glass.tintMode", default = "auto",
        options = listOf("auto" to "Follow the wallpaper", "light" to "Light", "dark" to "Dark"),
        title = "Tint colour",
        section = "glass", keywords = "tint colour color light dark",
    )

    /** Blur, refraction and tint for one glass surface, or the overall values ([custom] null). */
    class GlassSurface(val id: String, val name: String, hasCustomSwitch: Boolean) {
        val custom: BoolSetting? = if (!hasCustomSwitch) null else BoolSetting(
            key = "glass.$id.custom", default = false,
            title = "$name: own glass values",
            summary = "Off: uses the overall values",
            section = "glass", keywords = "glass $name surface custom",
        )
        private val prefix = if (hasCustomSwitch) "$name: " else ""
        val blur = IntSetting(
            key = "glass.$id.blur", default = 4, min = 0, max = 40, unit = "dp",
            title = "${prefix}blur".replaceFirstChar { it.uppercase() },
            section = "glass", keywords = "blur $name",
        )
        val refraction = IntSetting(
            key = "glass.$id.refraction", default = 60, min = 0, max = 100, step = 5, unit = "%",
            title = "${prefix}refraction".replaceFirstChar { it.uppercase() },
            summary = if (hasCustomSwitch) "" else "How strongly the edges bend what is behind the glass (Full quality only)",
            section = "glass", keywords = "refraction lens intensity $name",
        )
        val tint = IntSetting(
            key = "glass.$id.tint", default = 18, min = 0, max = 70, step = 2, unit = "%",
            title = "${prefix}tint strength".replaceFirstChar { it.uppercase() },
            section = "glass", keywords = "tint opacity $name",
        )
        val settings: List<Setting<*>> get() = listOfNotNull(custom, blur, refraction, tint)
    }

    val GlassDefault = GlassSurface("all", "Overall", hasCustomSwitch = false)
    val GlassPill = GlassSurface("pill", "Pill", hasCustomSwitch = true)
    val GlassDrawer = GlassSurface("drawer", "Drawer: search field, tabs and scroll bar", hasCustomSwitch = true)
    val GlassMenu = GlassSurface("menu", "Long-press menu", hasCustomSwitch = true)
    val GlassClock = GlassSurface("clock", "Clock", hasCustomSwitch = true)
    val glassSurfaces = listOf(GlassPill, GlassDrawer, GlassMenu, GlassClock)

    val ClockGlass = BoolSetting(
        key = "home.clockGlass", default = false,
        title = "Glass plate behind it",
        section = "clock", keywords = "clock glass background plate",
    )

    // ---- Search ----

    val SearchEngine = ChoiceSetting(
        key = "search.engine", default = "ddg",
        options = listOf(
            "ddg" to "DuckDuckGo",
            "google" to "Google",
            "brave" to "Brave Search",
            "startpage" to "Startpage",
        ),
        title = "Web search engine",
        section = "search", keywords = "web search engine google duckduckgo",
    )
    val SearchOpenKeyboard = BoolSetting(
        key = "search.keyboard", default = true,
        title = "Open the keyboard",
        section = "search", keywords = "keyboard focus",
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
    val StatsReopened = counter("stats.reopened", "Recents openings reopened after a redirect covered them")
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
        ReopenRecents, ReopenWindow, OnRecentsExit, OnRecentsExitDelay,
        ScreenOffMode, ScreenOffDelay, UnlockCheck,
        SystemLauncherPackage, HomeClasses, RecentsIds, RecentsIdSubstrings, HomeIds, DrawerIds, LearnedIds, PollInterval,
        ExcludeInCall, ExcludeSplitScreen, SystemHomePause, GuardCount, GuardWindow, GuardPause,
        SettleStyle, SettleDuration, ExcludeFromRecents,
        HomeColumns, HomeRows, HomeIconSize, HomeLabels, HomeLabelSize,
        IconPack, IconPackHome, IconPackDrawer, IconMask,
        HomeMarginSide, HomeMarginTop, HomeMarginBottom, Clock24h, ClockSize, ClockDate, ClockGlass,
        GestureSwipeUp, GestureSwipeDown, GestureDoubleTap, GestureLongPress,
        PillEnabled, PillSlots, PillPosition, PillOffset, PillWidth, PillHeight, PillCorner, PillIconSize,
        PillHint, PillTap, PillLongPress, PillSwipeUp, PillSwipeDown,
        DrawerColumns, DrawerIconSize, DrawerLabels, DrawerFromBottom, DrawerAutoKeyboard, DrawerSort,
        DrawerWork, DrawerCloseOnLaunch, DrawerDim, DrawerTabsPosition,
        DrawerTransition, DrawerPillMorph, DrawerSpeed, DrawerBounce, DrawerRise, DrawerHomeScale, DrawerHomeBlur,
        DrawerScrollbar, DrawerScrollBubble, DrawerScrollEffect, DrawerScrollStrength, DrawerOverscroll,
        MenuEntries,
        SearchEngine, SearchOpenKeyboard,
        GlassQuality, GlassAuto, GlassCap, GlassTintMode,
    ) + GlassDefault.settings + glassSurfaces.flatMap { it.settings } + listOf(
        LogEnabled, LogVerbosity, LogSize,
        LastSystemLauncherVersion, LastSystemLauncher, DetectorVerified,
        StatsKey, StatsHomeSeen, StatsHomeUnseen, StatsRecentsSeen, StatsRecentsUnseen, StatsInterrupted, StatsFallbacks, StatsReopened, SetupAppLaunchDone, SetupEmptyHomeDone, SetupMacrosDone,
    )

    fun inSection(sectionId: String): List<Setting<*>> = all.filter { it.section == sectionId && !it.hidden }

    /** Simple contains-all-words search; the fuzzy engine arrives with universal search. */
    fun search(query: String): List<Setting<*>> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return all.filter { s -> !s.hidden && words.all { it in s.searchText } }
    }
}
