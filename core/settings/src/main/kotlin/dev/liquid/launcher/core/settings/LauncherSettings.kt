package dev.liquid.launcher.core.settings

/**
 * The registry. Every option of the launcher is declared here (or, later, in a feature's own
 * registry object that is added to [all]).
 */
object LauncherSettings {

    /** Font choices: system families every Android phone has, and an imported file. */
    val FONT_OPTIONS = listOf(
        "default" to "System default",
        "light" to "Sans light",
        "medium" to "Sans medium",
        "condensed" to "Sans condensed",
        "serif" to "Serif",
        "mono" to "Monospace",
        "casual" to "Casual",
        "cursive" to "Cursive",
        "file" to "Imported font file",
    )

    object Sections {
        val Home = SettingsSection("home", "Home screen", "Grid, icons, labels and the clock", "Home screen")
        val Theme = SettingsSection("theme", "Theme", "Colours, fonts and icon shape; presets, export and import", "Look")
        val Folders = SettingsSection("folders", "Folders", "Preview, background, shape, columns and what a tap does", "Home screen")
        val Dock = SettingsSection("dock", "Dock", "A row of apps above the pill that stays on every page", "Home screen")
        val Icons = SettingsSection("icons", "Icons", "Icon packs for home and drawer, and icons the pack does not cover", "Look")
        val Clock = SettingsSection("clock", "Clock", "Defaults for new clocks. Each clock has its own gallery: hold it on the home screen, then \"Customise\" (designs, fonts, colours, glass)", "Look")
        val Gestures = SettingsSection("gestures", "Gestures", "Swipes, taps, presses, pinches, two-finger swipes and edge swipes on the home screen", "Home screen")
        val Pill = SettingsSection("pill", "Pill", "The glass bar: its items, size, position and gestures", "Home screen")
        val Drawer = SettingsSection("drawer", "App drawer", "Layout, sorting, search, tabs and the work profile", "App drawer")
        val Tags = SettingsSection("tags", "Drawer tabs and tags", "Your own drawer pages built from tags, and filtering the drawer by tag in one tap", "App drawer")
        val DrawerMotion = SettingsSection("drawerMotion", "Drawer motion", "Opening transition, scroll bar and scroll effects", "App drawer")
        val Motion = SettingsSection("motion", "Motion and haptics", "Opening apps and search, animation speed, haptic ticks", "Motion and feedback")
        val DrawerIndex = SettingsSection("drawerIndex", "Scroll index", "Letters at the edge of the drawer that bend to your finger: side, depth, width, bubble, haptics, sections at the top", "App drawer")
        val Menu = SettingsSection("menu", "Long-press menu", "Which entries appear and in what order", "Home screen")
        val HomeMenu = SettingsSection("homeMenu", "Home menu", "The two glass pills from holding empty space: items, order, labels", "Home screen")
        val Wallpaper = SettingsSection("wallpaper", "Wallpaper", "Your library, placing and parallax, blur and dim per screen, transitions, changing automatically", "Look")
        val Layouts = SettingsSection("layouts", "Saved layouts and backup", "Home setups you can switch between; back up and restore everything", "Layouts and backup")
        val Search = SettingsSection("search", "Search", "How search opens, where its field sits, blur and zoom, the keyboard, Ask Claude and web search", "App drawer")
        val Notices = SettingsSection("notices", "Glass notices", "Short glass labels for layout switches, wallpapers, tag filters, the redirect, updates and study blocks", "Motion and feedback")
        val Study = SettingsSection("study", "Study companion", "Study blocks and tasks from your calendar: Now card, Today, notices, quick actions", "Study")
        val Glass = SettingsSection("glass", "Liquid glass", "Quality, blur, refraction, tint and edge light, overall and per surface", "Look")
        val Buttons = SettingsSection("buttons", "Buttons", "Liquid glass buttons in Today, the Now card and the sheets: style, shape, size, tint, edge light, the main button's colour", "Look")
        val Effects = SettingsSection("effects", "Effects and touch", "What a press throws over the screen (ripples, paint, ink, light, swooshes…), how icons and buttons move under your finger, and moving effects over the wallpaper, like a keyboard theme", "Look")

        val Redirect = SettingsSection("redirect", "Redirect engine", "When and how the launcher is brought to the front", "Redirect")
        val Detection = SettingsSection("detection", "Detection", "How the system launcher's home screen and recents are recognised", "Redirect")
        val Exclusions = SettingsSection("exclusions", "Exclusions and safety", "When not to redirect, loop guard", "Redirect")
        val Handover = SettingsSection("handover", "Hand-over", "How the launcher appears after a redirect", "Redirect")
        val Log = SettingsSection("log", "Debug log", "What is recorded", "Redirect")

        // In the order the settings app lists them; sections with the same group sit under one heading.
        val all = listOf(
            Theme, Wallpaper, Icons, Glass, Buttons, Effects, Clock,
            Home, Pill, Dock, Folders, HomeMenu, Menu, Gestures,
            Drawer, Tags, DrawerMotion, DrawerIndex, Search,
            Motion, Notices,
            Layouts,
            Study,
            Redirect, Detection, Exclusions, Handover, Log,
        )
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
        // A new key in 0.5.1 (was redirect.onHomeDelay, 160 ms): most arrivals no longer wait for it, so it can be safer.
        key = "redirect.unsureDelay", default = 260, min = 0, max = 1000, step = 10, unit = "ms",
        title = "Delay while it could still be recents",
        summary = "Used only when the system launcher shows no going-home animation, so a recents gesture could still be " +
            "under way. On this phone the recents panel showed up within 250 ms; lower is faster but can interrupt recents",
        section = "redirect", keywords = "delay latency flash recents unsure",
    )
    val RequireHomeScreen = BoolSetting(
        key = "redirect.requireHomeScreen", default = true,
        title = "Only redirect over the home screen or drawer",
        summary = "Waits until the system launcher's home screen (or its app drawer) is visible. " +
            "When leaving recents this is strict: closing recents into an app, a lock screen or anything else never redirects",
        section = "redirect", keywords = "home screen signature workspace recents interrupt",
    )
    val FastOnHomeAnimation = BoolSetting(
        key = "redirect.fastOnHomeAnimation", default = true,
        title = "Redirect at once when the system launcher animates home",
        summary = "The system launcher draws its home screen smaller and lets it grow when a gesture ends towards home. " +
            "Recents never follows that, so there is nothing to wait for",
        section = "redirect", keywords = "fast animation scale home certain delay",
    )
    val FastDelay = IntSetting(
        key = "redirect.fastDelay", default = 0, min = 0, max = 200, step = 10, unit = "ms",
        title = "Delay once that animation is seen",
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
    val ReopenOnHint = BoolSetting(
        key = "redirect.reopenOnHint", default = true,
        title = "Also reopen on a sign of recents behind the launcher",
        summary = "If the system launcher changes its window just after an uncertain redirect and its views cannot be read, " +
            "treat that as recents. Switch off if recents ever opens by itself after going home",
        section = "redirect", keywords = "recents interrupted recovery reopen hint",
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

    /**
     * Up to 0.8.1 this was a switch ("exclude.inCall", on by default) that stopped every redirect
     * while the phone's audio was in call or communication mode, so going home during a phone or
     * Messenger call stayed on the system launcher. The new key starts from "redirect as usual".
     */
    val ExcludeCalls = ChoiceSetting(
        key = "exclude.calls", default = "never",
        options = listOf(
            "never" to "Redirect as usual",
            "ringing" to "Not while the phone rings",
            "call" to "Not during any call",
        ),
        title = "During calls",
        summary = "Going home during a phone or Messenger call opens Liquid Launcher like at any other time",
        section = "exclusions", keywords = "phone call ringing messenger whatsapp voip in-call",
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

    // ---- Theme ----

    val ThemeTextColor = ColorSetting(
        key = "theme.textColor", autoLabel = "Automatic (light or dark from the wallpaper)",
        title = "Text and icon colour on the home screen",
        section = "theme", keywords = "theme colour text label clock content",
    )
    val ThemeAccent = ColorSetting(
        key = "theme.accent", autoLabel = "Automatic (from the wallpaper)",
        title = "Accent colour",
        summary = "Highlights: the selected tab, the scroll index bubble, the current layout, folder tiles set to accent",
        section = "theme", keywords = "theme accent colour highlight",
    )
    val ThemeGlassColor = ColorSetting(
        key = "theme.glassColor", autoLabel = "Neutral (white or black)",
        title = "Glass tint colour",
        summary = "The colour glass surfaces are tinted with; how strongly is set per surface in Liquid glass",
        section = "theme", keywords = "theme glass tint colour",
    )
    val ThemeFont = ChoiceSetting(
        key = "theme.font", default = "default",
        options = FONT_OPTIONS,
        title = "Font",
        summary = "Labels, pill, menus, drawer and search. Import your own font file below",
        section = "theme", keywords = "theme font typeface family",
    )
    val ThemeClockFont = ChoiceSetting(
        key = "theme.clockFont", default = "same",
        options = listOf("same" to "Same as the font above") + FONT_OPTIONS,
        title = "Clock font",
        section = "theme", keywords = "theme clock font typeface",
    )
    val ThemeClockWeight = ChoiceSetting(
        key = "theme.clockWeight", default = "light",
        options = listOf("thin" to "Thin", "light" to "Light", "normal" to "Regular", "medium" to "Medium", "bold" to "Bold"),
        title = "Clock weight",
        section = "theme", keywords = "theme clock weight bold thin",
    )
    val ThemeIconShape = ChoiceSetting(
        key = "theme.iconShape", default = "system",
        options = listOf(
            "system" to "Like the system",
            "circle" to "Circle",
            "squircle" to "Squircle",
            "rounded" to "Rounded square",
            "square" to "Square",
            "teardrop" to "Teardrop",
            "pebble" to "Pebble",
        ),
        title = "Icon shape",
        summary = "For apps with adaptive icons; icon pack icons keep the pack's own shape",
        section = "theme", keywords = "theme icon shape mask circle squircle square teardrop",
    )
    /** File name of an imported font in files/fonts, used when a font setting is "file". */
    val ThemeFontFile = TextSetting(
        key = "theme.fontFile", default = "",
        title = "Imported font", section = "theme", hidden = true,
    )

    // ---- Folders ----

    val FolderPreview = ChoiceSetting(
        key = "folder.preview", default = "grid",
        options = listOf("grid" to "Grid of four", "stack" to "Fanned stack", "first" to "First app large"),
        title = "Preview on the home screen",
        section = "folders", keywords = "folder preview style grid stack",
    )
    val FolderBackground = ChoiceSetting(
        key = "folder.background", default = "tint",
        options = listOf("tint" to "Soft tint", "glass" to "Liquid glass", "accent" to "Accent colour", "none" to "None"),
        title = "Tile background",
        section = "folders", keywords = "folder background glass tint",
    )
    val FolderShape = ChoiceSetting(
        key = "folder.shape", default = "icons",
        options = listOf("icons" to "Like the app icons", "rounded" to "Rounded square", "circle" to "Circle", "squircle" to "Squircle"),
        title = "Tile shape",
        section = "folders", keywords = "folder shape",
    )
    val FolderTapFirst = BoolSetting(
        key = "folder.tapFirst", default = false,
        title = "Tap opens the first app",
        summary = "Swipe up on the folder (or hold it) to open it instead",
        section = "folders", keywords = "folder tap first app swipe open",
    )
    val FolderColumns = IntSetting(
        key = "folder.columns", default = 4, min = 3, max = 6,
        title = "Columns in an open folder",
        section = "folders", keywords = "folder columns grid open",
    )

    // ---- Dock ----

    val DockEnabled = BoolSetting(
        key = "dock.enabled", default = false,
        title = "Show the dock",
        summary = "Drag apps onto it from the drawer or the home screen; hold an app in it for its menu",
        section = "dock", keywords = "dock hotseat favourites row bottom",
    )
    val DockSlots = IntSetting(
        key = "dock.slots", default = 5, min = 3, max = 7,
        title = "Apps in the dock",
        section = "dock", keywords = "dock slots count",
    )
    val DockBackground = ChoiceSetting(
        key = "dock.background", default = "glass",
        options = listOf("glass" to "Liquid glass", "none" to "None"),
        title = "Background",
        section = "dock", keywords = "dock background glass",
    )
    val DockLabels = BoolSetting(
        key = "dock.labels", default = false,
        title = "Labels under dock icons",
        section = "dock", keywords = "dock labels",
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

    val ThemedIcons = BoolSetting(
        key = "icons.themed", default = false,
        title = "Icons follow the wallpaper colours",
        summary = "Every icon takes your wallpaper's colours and changes in the same moment as the wallpaper: apps with a themed " +
            "(monochrome) icon use it, line icons are tinted, everything else becomes a two-tone version of itself. " +
            "\"Change icon\" can keep one app's own colours. Off: icons stay as they are",
        section = "icons", keywords = "themed icons monochrome material you dynamic colour wallpaper tint",
    )
    val ThemedIconsWhere = ChoiceSetting(
        key = "icons.themedWhere", default = "both",
        options = listOf("both" to "Home screen and drawer", "home" to "Home screen only", "drawer" to "Drawer only"),
        title = "Where",
        section = "icons", keywords = "themed icons home drawer",
    )
    val ThemedIconsUncovered = ChoiceSetting(
        key = "icons.themedUncovered", default = "tint",
        options = listOf("tint" to "Two-tone in the wallpaper colours", "keep" to "Keep their own colours"),
        title = "Apps without a themed icon",
        summary = "Also applies to icons fitted to an icon pack's style",
        section = "icons", keywords = "themed icons uncovered legacy tint duotone",
    )
    val ThemedIconsPacks = ChoiceSetting(
        key = "icons.themedPacks", default = "follow",
        options = listOf("follow" to "Follow the wallpaper too", "keep" to "Keep the pack's colours"),
        title = "Icons drawn by an icon pack",
        summary = "Line icons are tinted in one colour, full icons become two-tone",
        section = "icons", keywords = "themed icons icon pack recolour tint follow wallpaper",
    )
    val ThemedIconsColors = ChoiceSetting(
        key = "icons.themedColors", default = "wallpaper",
        options = listOf(
            "wallpaper" to "Taken from the wallpaper (works on every phone)",
            "system" to "System colours (Material You, if the phone sets them)",
        ),
        title = "Colours",
        section = "icons", keywords = "themed icons colour source monet system wallpaper",
    )
    val ThemedIconsStyle = ChoiceSetting(
        key = "icons.themedStyle", default = "auto",
        options = listOf("auto" to "Follow dark theme", "light" to "Light", "dark" to "Dark"),
        title = "Themed icon style",
        section = "icons", keywords = "themed icons light dark",
    )

    val HomePageTransition = ChoiceSetting(
        key = "home.pageTransition", default = "slide",
        options = listOf(
            "slide" to "Slide",
            "depth" to "Depth: the page you leave sinks back",
            "stack" to "Stack: the next page slides over",
            "cube" to "Cube",
            "carousel" to "Carousel",
            "fade" to "Fade",
        ),
        title = "Page transition",
        section = "home", keywords = "page transition swipe effect cube depth stack fade",
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
        key = "gesture.longPress", default = "home_menu",
        title = "Long press on empty space", section = "gestures", keywords = "gesture long press edit",
    )

    val GesturePinchIn = ActionSetting(
        key = "gesture.pinchIn", default = "pages",
        title = "Pinch in (two fingers together)", section = "gestures", keywords = "gesture pinch in zoom out two fingers pages",
    )
    val GesturePinchOut = ActionSetting(
        key = "gesture.pinchOut", default = "none",
        title = "Pinch out (two fingers apart)", section = "gestures", keywords = "gesture pinch out zoom in two fingers",
    )
    val GestureTwoUp = ActionSetting(
        key = "gesture.twoUp", default = "none",
        title = "Two-finger swipe up", section = "gestures", keywords = "gesture two fingers swipe up",
    )
    val GestureTwoDown = ActionSetting(
        key = "gesture.twoDown", default = "quick_settings",
        title = "Two-finger swipe down", section = "gestures", keywords = "gesture two fingers swipe down quick settings",
    )
    val GestureEdgeLeftUp = ActionSetting(
        key = "gesture.edgeLeftUp", default = "none",
        title = "Swipe up along the left edge", section = "gestures", keywords = "gesture edge left side swipe up zone",
    )
    val GestureEdgeLeftDown = ActionSetting(
        key = "gesture.edgeLeftDown", default = "none",
        title = "Swipe down along the left edge", section = "gestures", keywords = "gesture edge left side swipe down zone",
    )
    val GestureEdgeRightUp = ActionSetting(
        key = "gesture.edgeRightUp", default = "none",
        title = "Swipe up along the right edge", section = "gestures", keywords = "gesture edge right side swipe up zone",
    )
    val GestureEdgeRightDown = ActionSetting(
        key = "gesture.edgeRightDown", default = "none",
        title = "Swipe down along the right edge", section = "gestures", keywords = "gesture edge right side swipe down zone",
    )
    val GestureEdgeWidth = IntSetting(
        key = "gesture.edgeWidth", default = 24, min = 12, max = 48, step = 4, unit = "dp",
        title = "Edge zone width",
        summary = "Swipes that start this close to a side edge use the edge gestures (when they do something; otherwise the normal swipe)",
        section = "gestures", keywords = "gesture edge zone width",
    )

    /** Daily update check when settings opens; edited on the Updates screen. */
    val UpdatesDaily = BoolSetting(
        key = "updates.daily", default = false,
        title = "Check for updates once a day", section = "updates", hidden = true,
    )

    // ---- Motion and haptics ----

    val HapticsEnabled = BoolSetting(
        key = "motion.haptics", default = true,
        title = "Haptic feedback",
        summary = "Short ticks for the launcher's own gestures. Also needs vibration for touches on in MagicOS",
        section = "motion", keywords = "haptic vibration feedback tick",
    )
    val HapticsStrength = ChoiceSetting(
        key = "motion.hapticsStrength", default = "light",
        options = listOf("light" to "Light", "strong" to "Strong"),
        title = "Haptic strength",
        section = "motion", keywords = "haptic strength vibration",
    )
    val HapticDrawer = BoolSetting(
        key = "motion.hapticDrawer", default = true,
        title = "Tick when the drawer passes the open point",
        summary = "While you drag it: past this point it opens when you let go",
        section = "motion", keywords = "haptic drawer tick open point",
    )
    val HapticPickUp = BoolSetting(
        key = "motion.hapticPickUp", default = true,
        title = "Tick when an item is picked up or a gesture is recognised",
        section = "motion", keywords = "haptic drag pick up gesture recognised",
    )
    val LaunchAnimation = ChoiceSetting(
        key = "motion.launch", default = "reveal",
        options = listOf(
            "reveal" to "Reveal from the icon",
            "scale" to "Grow from the icon",
            "fade" to "Fade",
            "system" to "The system's own",
        ),
        title = "Opening an app",
        section = "motion", keywords = "launch animation open app icon reveal scale grow fade",
    )
    val SearchTransition = ChoiceSetting(
        // Default "lift" since 0.7.2 (D107); was "space".
        key = "motion.search", default = "lift",
        options = listOf(
            "lift" to "Lift: the home screen falls back into the blur while the pill flows up to the keyboard",
            "space" to "One space: the home screen glides down under your finger",
            "zoom" to "Zoom: the home screen falls back, search comes forward from the field",
            "depth" to "Depth (home sinks back)",
            "fade" to "Fade",
        ),
        title = "Opening search",
        summary = "Search follows a swipe down the way the drawer follows a swipe up, and springs open or back when you let go. " +
            "The wallpaper grows and melts into its blur as in the drawer",
        section = "search", keywords = "search transition lift space zoom depth fade animation swipe down follow blur",
    )
    val SearchField = ChoiceSetting(
        key = "search.field", default = "keyboard",
        options = listOf(
            "keyboard" to "Above the keyboard: the pill flows straight to where the keyboard will be",
            "bottom" to "At the bottom (the keyboard pushes it up)",
            "top" to "At the top of the screen",
        ),
        title = "Search field",
        summary = "Above the keyboard and at the bottom, the pill itself becomes the field (with \"The pill becomes the search field\" " +
            "in Drawer motion and the pill at the bottom). The launcher remembers your keyboard's height, so the field never goes down and up again",
        section = "search", keywords = "search field position keyboard bottom top pill morph",
    )
    val SearchHomeScale = IntSetting(
        key = "search.homeScale", default = 10, min = 0, max = 25, unit = "%",
        title = "Home screen falls back by",
        summary = "How far the home screen shrinks into the distance as search opens (Lift and Zoom)",
        section = "search", keywords = "search zoom scale home depth fall back",
    )
    val SearchBounce = IntSetting(
        key = "search.bounce", default = 30, min = 0, max = 100, step = 5, unit = "%",
        title = "Bounce",
        summary = "How much search overshoots and settles as it opens. 0: it glides to a stop",
        section = "search", keywords = "search bounce spring overshoot smooth wobble",
    )
    val SearchSpeed = IntSetting(
        key = "search.speed", default = 100, min = 50, max = 200, step = 10, unit = "%",
        title = "Speed",
        summary = "How quickly search opens and closes after you let go",
        section = "search", keywords = "search speed fast slow spring stiffness",
    )
    val SearchStretch = IntSetting(
        key = "search.stretch", default = 50, min = 0, max = 100, step = 10, unit = "%",
        title = "Liquid stretch",
        summary = "The pill stretches like a drop on its way to the keyboard and wobbles as it lands. 0: it keeps its shape",
        section = "search", keywords = "search stretch liquid drop wobble pill",
    )
    val PressFeel = ChoiceSetting(
        key = "motion.press", default = "squishy",
        options = listOf(
            "squishy" to "Squish: a crisp squash under your finger and a rebound",
            "tilt" to "Tilt: tips towards where your finger landed, with a highlight",
            "off" to "Off",
        ),
        title = "Press motion",
        summary = "How icons, pill items, search results, menus and glass buttons move under your finger. Big things such as widgets move only a little",
        section = "effects", keywords = "press squish bounce tap feedback button icon spring tilt highlight motion",
    )
    val PressDepth = IntSetting(
        key = "motion.pressDepth", default = 50, min = 0, max = 100, step = 10, unit = "%",
        title = "Press intensity",
        summary = "How far a press goes: a deeper squash, more tilt",
        section = "effects", keywords = "press squish depth amount dip intensity",
    )
    val PressSpeed = IntSetting(
        key = "motion.pressSpeed", default = 100, min = 50, max = 200, step = 10, unit = "%",
        title = "Press speed",
        summary = "How quickly a press goes down and springs back",
        section = "effects", keywords = "press speed fast slow spring",
    )
    val EditWobble = ChoiceSetting(
        key = "motion.editWobble", default = "off",
        options = listOf("off" to "Off", "subtle" to "Subtle", "lively" to "Lively"),
        title = "Wobble while editing",
        summary = "Icons and widgets shiver while the home screen is in edit mode, each at its own pace",
        section = "effects", keywords = "edit wobble jiggle shake icons edit mode",
    )
    val PressWobble = IntSetting(
        key = "motion.pressWobble", default = 50, min = 0, max = 100, step = 10, unit = "%",
        title = "Wobble",
        summary = "How much a pressed thing rings as it springs back; 0 settles without overshooting",
        section = "effects", keywords = "press wobble bounce spring overshoot jelly",
    )
    val MotionStyle = ChoiceSetting(
        key = "motion.style", default = "liquid",
        options = listOf(
            "calm" to "Calm: everything settles without overshooting",
            "liquid" to "Liquid: a soft overshoot",
            "lively" to "Lively: a clear bounce",
        ),
        title = "Feel",
        summary = "One spring for every sheet, menu, folder and label, so they all move alike",
        section = "motion", keywords = "motion spring bounce feel style calm lively liquid",
    )
    val MotionSpeed = IntSetting(
        key = "motion.speed", default = 100, min = 50, max = 200, step = 10, unit = "%",
        title = "Speed of the launcher's animations",
        summary = "Menus, sheets, transitions and springs. The drawer has its own speed in Drawer motion",
        section = "motion", keywords = "animation speed duration fast slow motion",
    )

    val WidgetBackground = ChoiceSetting(
        key = "widgets.background", default = "none",
        options = listOf("none" to "None: on the wallpaper, like the clock", "glass" to "A liquid glass card"),
        title = "Behind the calendar and study widgets",
        summary = "The Calendar, Now card and Today widgets, on pages and on the search screen",
        section = "home", keywords = "widget background box card glass calendar now today",
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

    /** Ids of the search page's cards, top to bottom (see SearchCards in core/data). Edited with its own editor. */
    val SearchCards = StringListSetting(
        key = "search.cards", default = listOf("widgets", "timeline", "exam", "absences", "grades", "flashcard", "review", "next", "commands", "claude", "apps"),
        title = "Search page cards", section = "search", hidden = true,
    )

    // ---- Pill: the study item and each item's gestures (0.9.1) ----

    val PillStudyLook = ChoiceSetting(
        key = "pill.study.look", default = "glyph",
        options = listOf(
            "glyph" to "An icon",
            "app" to "An app's icon (follows your icon pack)",
            "picture" to "Your picture",
            "circle" to "A liquid circle that fills as blocks are done",
            "text" to "The block's name and time",
        ),
        title = "Study item: what it shows",
        summary = "The pill's study item is the \"Today\" button or the \"Study block\" item; tapping it grows the study hub out of the pill. " +
            "Pick an app or your picture with the buttons above",
        section = "pill", keywords = "pill study icon picture glyph circle today look item",
    )
    val PillStudyGlyph = ChoiceSetting(
        key = "pill.study.glyph", default = "school",
        options = listOf(
            "school" to "Graduation cap", "schedule" to "Clock", "calendar" to "Calendar", "sparkle" to "Sparkle",
            "chat" to "Speech bubble", "star" to "Star", "heart" to "Heart", "check" to "Tick", "code" to "Code", "layers" to "Layers",
        ),
        title = "Study item: icon",
        section = "pill", keywords = "pill study icon glyph vector symbol",
    )
    val PillStudyApp = TextSetting(
        key = "pill.study.app", default = "", title = "Study item: app", section = "pill", hidden = true,
    )
    val PillStudyPicture = TextSetting(
        key = "pill.study.picture", default = "", title = "Study item: picture", section = "pill", hidden = true,
    )
    val PillStudyFrame = ChoiceSetting(
        key = "pill.study.frame", default = "none",
        options = listOf(
            "none" to "No frame", "circle" to "Circle", "squircle" to "Squircle", "pebble" to "Pebble",
            "rounded" to "Rounded square", "teardrop" to "Drop", "square" to "Square",
        ),
        title = "Study item: frame",
        summary = "A shape filled with the item's colour around the icon",
        section = "pill", keywords = "pill study frame shape circle squircle drop",
    )
    val PillStudySize = IntSetting(
        key = "pill.study.size", default = 24, min = 16, max = 44, step = 2, unit = "dp",
        title = "Study item: size",
        section = "pill", keywords = "pill study size",
    )
    val PillStudyTint = ColorSetting(
        key = "pill.study.tint", autoLabel = "The next block's colour",
        title = "Study item: colour",
        summary = "Of the frame, the ring and the liquid. Automatic follows the block now or next (its colour in your calendar)",
        section = "pill", keywords = "pill study tint colour color module",
    )
    val PillStudyRing = ChoiceSetting(
        key = "pill.study.ring", default = "segments",
        options = listOf(
            "segments" to "The day: one arc per block, a gap where one was missed",
            "continuous" to "The day as one arc",
            "block" to "Time gone in the block now",
            "none" to "No ring",
        ),
        title = "Study item: progress ring",
        section = "pill", keywords = "pill study ring progress day",
    )

    /** One line per pill item: its code and four actions (tap, long press, swipe up, swipe down), tab separated. */
    val PillSlotGestures = StringListSetting(
        key = "pill.slotGestures", default = emptyList(),
        title = "Gestures of each pill item", section = "pill", hidden = true,
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
    val DrawerSideMargin = IntSetting(
        key = "drawer.sideMargin", default = 8, min = 0, max = 32, step = 2, unit = "dp",
        title = "Side margins",
        summary = "The same space on the left and the right of the apps",
        section = "drawer", keywords = "margin padding edge side space symmetric",
    )
    val DrawerDim = IntSetting(
        key = "drawer.dim", default = 45, min = 0, max = 90, step = 5, unit = "%",
        title = "Wallpaper dimming",
        section = "drawer", keywords = "dim background wallpaper",
    )
    val DrawerTabsPosition = ChoiceSetting(
        key = "drawer.tabsPosition", default = "top",
        options = listOf(
            "top" to "Top, floating over the apps",
            "bottom" to "Bottom, above the search field",
            "search" to "Inside the search pill",
            "hidden" to "Hidden (swipe sideways to change tab)",
        ),
        title = "Tabs position",
        summary = "The glass pill with your tabs. Tap a tab to switch, tap it again to jump to the start, hold it for that tab's options",
        section = "tags", keywords = "tabs pill work apps position top bottom search merged hidden",
    )
    val DrawerTabsStyle = ChoiceSetting(
        key = "drawer.tabsStyle", default = "pill",
        options = listOf(
            "pill" to "One glass pill with a sliding highlight",
            "chips" to "Separate glass chips",
            "text" to "Text only, no glass",
        ),
        title = "Tabs style",
        section = "tags", keywords = "tabs style pill chips text glass",
    )
    val DrawerTabsSize = ChoiceSetting(
        key = "drawer.tabsSize", default = "medium",
        options = listOf("small" to "Small", "medium" to "Medium", "large" to "Large"),
        title = "Tabs size",
        section = "tags", keywords = "tabs size height small large",
    )
    val DrawerTabsIcons = ChoiceSetting(
        key = "drawer.tabsIcons", default = "auto",
        options = listOf("auto" to "When there is room", "on" to "Always (icons only when tight)", "off" to "Never"),
        title = "Tab icons",
        section = "tags", keywords = "tabs icons",
    )
    val DrawerTabsButtons = StringListSetting(
        key = "drawer.tabsButtons", default = emptyList(),
        title = "Buttons in the tabs pill", section = "tags", hidden = true,
    )
    val DrawerTabMenu = StringListSetting(
        key = "drawer.tabMenu", default = listOf("rename", "icon", "contents", "apps", "sort", "move", "newtag", "remove"),
        title = "Held tab menu", section = "tags", hidden = true,
    )
    val DrawerTabsInSearch = BoolSetting(
        key = "drawer.tabsInSearch", default = true,
        title = "Tabs filter search results",
        summary = "While you type, the tabs stay visible: tap one to search only its apps, tap it again for all apps. #name highlights that tag's tab",
        section = "tags", keywords = "tabs search filter scope tag hash",
    )

    // ---- Filtering by tag ----

    val TagStripPosition = ChoiceSetting(
        key = "tags.stripPosition", default = "above",
        options = listOf(
            "above" to "Above the search field",
            "top" to "At the top, under the tabs",
            "hidden" to "Hidden (the tag button and swipes still work)",
        ),
        title = "Tag chips in the drawer",
        summary = "A row of your tags: tap one to show only its apps, hold to add more tags. \"All apps\" clears the filter",
        section = "tags", keywords = "tag filter chips strip row position quick",
    )
    val TagButtonPosition = ChoiceSetting(
        key = "tags.button", default = "rowEnd",
        options = listOf(
            "rowEnd" to "Fixed at the end of the chip row",
            "rowStart" to "Fixed at the start of the chip row",
            "fieldRight" to "In the search field, on the right",
            "fieldLeft" to "In the search field, on the left",
        ),
        title = "Button for the full tag list",
        summary = "A round button that opens the list of all your tags, growing out of the button. In the chip row it never scrolls away",
        section = "tags", keywords = "tag list button all tags position search field",
    )
    val TagButtonSize = IntSetting(
        key = "tags.buttonSize", default = 50, min = 36, max = 64, step = 2, unit = "dp",
        title = "Tag button size",
        summary = "The round button at the end of the chip row that opens all your tags; the row grows with it",
        section = "tags", keywords = "tag button size big thumb reach",
    )
    val TagStripOrder = ChoiceSetting(
        key = "tags.stripOrder", default = "used",
        options = listOf("used" to "Pinned first, then most used", "az" to "Pinned first, then A to Z", "pinned" to "Only pinned tags"),
        title = "Order of the chips",
        summary = "Pin tags in the full tag list (the last chip, or hold the tag on the search field)",
        section = "tags", keywords = "tag filter order pinned favourite most used alphabetical",
    )
    val TagStripCount = IntSetting(
        key = "tags.stripCount", default = 10, min = 0, max = 40,
        title = "Chips shown",
        summary = "The rest are one tap away in the full list. 0 shows every tag",
        section = "tags", keywords = "tag filter count number chips",
    )
    val TagStripCounts = BoolSetting(
        key = "tags.stripCounts", default = false,
        title = "Show how many apps each tag has",
        section = "tags", keywords = "tag filter count apps number badge",
    )
    val TagTap = ChoiceSetting(
        key = "tags.tap", default = "single",
        options = listOf("single" to "Shows only that tag (hold to add more)", "multi" to "Adds or removes it (several tags at once)"),
        title = "Tapping a tag chip",
        section = "tags", keywords = "tag filter tap multi select several",
    )
    val TagMatch = ChoiceSetting(
        key = "tags.match", default = "any",
        options = listOf("any" to "Apps with any of the tags", "all" to "Only apps with all of the tags"),
        title = "With several tags, show",
        section = "tags", keywords = "tag filter match any all and or",
    )
    val TagSwipe = BoolSetting(
        key = "tags.swipe", default = true,
        title = "Swipe the search field sideways to change tag",
        summary = "Left for the next tag in the chip order, right for the one before; past the ends shows all apps",
        section = "tags", keywords = "tag filter swipe gesture pill cycle next",
    )
    val TagFilterKeep = BoolSetting(
        key = "tags.keep", default = false,
        title = "Keep the filter when the drawer closes",
        summary = "Off: the drawer always opens with all apps",
        section = "tags", keywords = "tag filter remember keep persist",
    )

    /** Tag ids pinned to the front of the tag chips, in order. Edited in the tag list. */
    val TagPinned = StringListSetting(
        key = "tags.pinned", default = emptyList(),
        title = "Pinned tags", section = "tags", hidden = true,
    )

    // ---- Drawer motion ----

    val DrawerTransition = ChoiceSetting(
        key = "drawer.transition", default = "space",
        options = listOf(
            "space" to "One space: home glides up and away as the apps rise from below, the wallpaper drifts with them",
            "depth" to "Depth: you move through the home screen into the apps behind it",
            "slide" to "Slide: the drawer is a sheet that follows the finger",
            "fade" to "Fade",
        ),
        title = "Transition",
        summary = "In all of them the pill becomes the search field and the wallpaper melts into its blurred copy",
        section = "drawerMotion", keywords = "transition animation open drawer style continuous space depth",
    )
    val DrawerTravel = IntSetting(
        key = "drawer.travel", default = 36, min = 20, max = 100, step = 2, unit = "%",
        title = "Finger travel to open",
        summary = "How far up the screen your finger moves for the drawer to be fully open. Shorter: more of the transition " +
            "happens under your finger instead of after you let go (the sheet transition always follows the finger 1:1)",
        section = "drawerMotion", keywords = "drawer travel distance finger follow drag",
    )
    val DrawerPillMorph = BoolSetting(
        key = "drawer.pillMorph", default = true,
        title = "The pill becomes the search field",
        summary = "When the pill is at the bottom, it changes shape into the drawer's search field as the drawer opens, " +
            "and into the search field above the keyboard as search opens (0.7.1)",
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
        key = "drawer.rise", default = 18, min = 0, max = 50, step = 2, unit = "%",
        title = "How far the apps rise",
        summary = "\"One space\" and \"Depth\"",
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
        summary = "The wallpaper melts into a blurred copy of itself as the drawer opens. The copy is made once, so this costs nothing while moving",
        section = "drawerMotion", keywords = "blur background wallpaper depth",
    )
    val DrawerBlurLive = BoolSetting(
        key = "drawer.blurLive", default = false,
        title = "Blur the icons too (heavy)",
        summary = "Blurs the whole home screen live in every frame instead of melting the wallpaper. Looks slightly richer, " +
            "but it is the most expensive effect in the launcher and drops frames on this phone",
        section = "drawerMotion", keywords = "blur live icons heavy performance",
    )
    val DrawerScrollbar = ChoiceSetting(
        key = "drawer.scrollbar", default = "index",
        options = listOf(
            "index" to "Letter index on a curve (alphabetical order; otherwise the glass handle)",
            "glass" to "Glass handle",
            "line" to "Thin line",
            "off" to "Off",
        ),
        title = "Scroll bar",
        summary = "Floats over the apps. Drag it to move through the list quickly. The letter index has its own section",
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

    // ---- Scroll index ----

    val IndexStyle = ChoiceSetting(
        key = "index.style", default = "rail",
        options = listOf(
            "rail" to "Rail: short, in thumb reach, away from the edge's Back gesture",
            "string" to "String: letters along the whole edge, pulled by the finger",
        ),
        title = "Letter index",
        summary = "Android lets an app keep at most 200 dp of each edge from the Back gesture: the rail fits inside that, so it never " +
            "triggers Back; a magnifier shows the exact letter while you drag. The string covers the whole edge and can trigger Back outside those 200 dp",
        section = "drawerIndex", keywords = "index letters rail back gesture thumb short magnifier string edge",
    )
    val IndexRailHeight = IntSetting(
        key = "index.railHeight", default = 176, min = 120, max = 180, step = 4, unit = "dp",
        title = "Rail height",
        section = "drawerIndex", keywords = "index rail height size",
    )
    val IndexRailLift = IntSetting(
        key = "index.railLift", default = 26, min = 8, max = 60, step = 2, unit = "% from the bottom",
        title = "Rail position",
        summary = "Where the rail's lower end sits, measured from the bottom of the screen: low for the thumb",
        section = "drawerIndex", keywords = "index rail position height thumb reach",
    )
    val IndexRailInset = IntSetting(
        key = "index.railInset", default = 12, min = 4, max = 40, step = 2, unit = "dp",
        title = "Rail distance from the edge",
        section = "drawerIndex", keywords = "index rail inset edge margin",
    )
    val IndexVisibility = ChoiceSetting(
        key = "index.visibility", default = "scroll",
        options = listOf(
            "always" to "Always",
            "scroll" to "While scrolling or touching",
            "touch" to "Only while touching the edge",
        ),
        title = "Show the letters",
        summary = "The letters float over the list; they never push the apps aside",
        section = "drawerIndex", keywords = "index letters visible always touch hide",
    )
    val IndexSide = ChoiceSetting(
        key = "index.side", default = "right",
        options = listOf("right" to "Right edge", "left" to "Left edge", "both" to "Both edges"),
        title = "Side",
        summary = "Touch the edge and pull: the letters come from that side",
        section = "drawerIndex", keywords = "index side left right both hand edge",
    )
    val IndexReach = IntSetting(
        key = "index.reach", default = 70, min = 20, max = 100, step = 5, unit = "%",
        title = "Curve width",
        summary = "How much of the column bends with your finger: low values make a narrow dent, high values a long sweep",
        section = "drawerIndex", keywords = "index curve bend arc string width reach",
    )
    val IndexEnds = ChoiceSetting(
        key = "index.ends", default = "pinned",
        options = listOf(
            "pinned" to "Held at the edge (a string fixed at both ends)",
            "loose" to "Follow when your finger is near them",
        ),
        title = "Top and bottom letters",
        section = "drawerIndex", keywords = "index ends pinned fixed string loose",
    )
    val IndexLetterSize = IntSetting(
        key = "index.letterSize", default = 12, min = 9, max = 20, unit = "sp",
        title = "Letter size",
        section = "drawerIndex", keywords = "index letter text size font",
    )
    val IndexSpacing = IntSetting(
        key = "index.spacing", default = 22, min = 12, max = 34, unit = "dp",
        title = "Letter spacing",
        summary = "Gets tighter by itself when the letters would not fit",
        section = "drawerIndex", keywords = "index spacing gap distance",
    )
    val IndexBubble = ChoiceSetting(
        key = "index.bubble", default = "glass",
        options = listOf("glass" to "Liquid glass", "solid" to "Solid", "none" to "None"),
        title = "Bubble",
        summary = "The letter (or section name) you are on, shown large next to your finger",
        section = "drawerIndex", keywords = "index bubble popup letter style",
    )
    val IndexBubblePlace = ChoiceSetting(
        key = "index.bubblePlace", default = "beside",
        options = listOf("beside" to "Beyond the letters", "above" to "Above the finger", "finger" to "Under the finger"),
        title = "Bubble position",
        summary = "Beyond the letters: past the bent letters, towards the middle of the screen, where your thumb does not cover it " +
            "(above the finger when there is no room). Under the finger is how it was before 0.6.3",
        section = "drawerIndex", keywords = "index bubble position place finger covered hidden see letter",
    )
    val IndexLetterGap = IntSetting(
        key = "index.letterGap", default = 64, min = 0, max = 140, step = 4, unit = "dp",
        title = "Letters beside the finger",
        summary = "How far the curve carries the letters past your finger, towards the middle of the screen, so the thumb does not cover them. " +
            "0: the letter you are on comes to your finger",
        section = "drawerIndex", keywords = "index curve larger bigger deeper letters finger covered gap distance edge bulge",
    )
    val IndexBubbleGap = IntSetting(
        key = "index.bubbleGap", default = 32, min = 0, max = 120, step = 4, unit = "dp",
        title = "Bubble distance",
        summary = "Space between the bent letters and the bubble",
        section = "drawerIndex", keywords = "index bubble distance gap further space",
    )
    val IndexHaptics = ChoiceSetting(
        key = "index.haptics", default = "light",
        options = listOf("light" to "Light tick per letter", "strong" to "Stronger tick per letter", "off" to "Off"),
        title = "Haptics",
        section = "drawerIndex", keywords = "index haptic vibration tick feedback",
    )
    val IndexFollow = IntSetting(
        key = "index.follow", default = 45, min = 0, max = 150, step = 15, unit = "ms",
        title = "List glide",
        summary = "How softly the list scrolls to the letter under your finger. 0 jumps instantly; higher values glide over big jumps",
        section = "drawerIndex", keywords = "index smooth glide follow lag animation",
    )
    val IndexTrack = IntSetting(
        key = "index.track", default = 100, min = 0, max = 100, step = 10, unit = "%",
        title = "Pull depth",
        summary = "The letters bend like a string, out from under your finger (\"Letters beside the finger\" sets how far). When you pull inwards, " +
            "at 100 % they keep that distance from your finger wherever it goes; lower values follow less, 0 keeps the curve where it started. " +
            "It springs back when you let go. The app list never moves sideways",
        section = "drawerIndex", keywords = "index follow finger pull string depth curve track",
    )
    val IndexSpring = ChoiceSetting(
        key = "index.spring", default = "medium",
        options = listOf("soft" to "Soft", "medium" to "Medium", "snappy" to "Snappy"),
        title = "Spring",
        summary = "How quickly the string catches up with your finger and snaps back",
        section = "drawerIndex", keywords = "index spring stiffness bounce speed",
    )
    val IndexHideDelay = IntSetting(
        key = "index.hideDelay", default = 500, min = 0, max = 2500, step = 100, unit = "ms",
        title = "Hide after",
        summary = "How long the letters stay after you let go or stop scrolling (not with \"Always\")",
        section = "drawerIndex", keywords = "index hide delay fade time",
    )
    val IndexFocus = BoolSetting(
        key = "index.focus", default = true,
        title = "Highlight the letter's apps",
        summary = "While you hold a letter, its apps stay bright and lift slightly; the other apps fade",
        section = "drawerIndex", keywords = "index focus highlight dim fade letter",
    )

    /** Tag ids shown as sections at the top of the main drawer list, in order. Edited in the tabs and tags editor. */
    val DrawerTopSections = StringListSetting(
        key = "drawer.topSections", default = emptyList(),
        title = "Sections at the top of the list", section = "drawerIndex", hidden = true,
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
        title = "Spare the phone on large surfaces",
        summary = "If the glass benchmark found full glass too heavy for this phone, large sheets (folders, lists, the wallpaper " +
            "screen) are drawn frosted. Pills, chips, menus and the clock always stay liquid; only \"Quality\" above turns that off",
        section = "glass", keywords = "performance fallback benchmark automatic",
    )
    val GlassCap = ChoiceSetting(
        key = "state.glassCap", default = "full",
        options = listOf("full" to "Full", "frosted" to "Frosted", "tint" to "Tint"),
        title = "Best tier the benchmark allows", section = "state", hidden = true,
    )
    val GlassFullPercent = IntSetting(
        key = "state.glassFullPercent", default = 0, min = 0, max = 200,
        title = "Full glass frame rate in the benchmark, percent of the refresh rate", section = "state", hidden = true,
    )
    val GlassRim = IntSetting(
        key = "glass.rim", default = 0, min = 0, max = 100, step = 5, unit = "%",
        title = "Edge light",
        summary = "A thin bright rim along the edge of every glass surface (pill, menus, sheets), like light catching real glass. " +
            "Off by default since 0.7.0: Fable's glass has none. Buttons have their own edge light (Settings → Buttons)",
        section = "glass", keywords = "glass edge light rim highlight specular shine border",
    )
    val GlassSaturation = IntSetting(
        key = "glass.saturation", default = 150, min = 0, max = 250, step = 5, unit = "%",
        title = "Colour through the glass",
        summary = "How vivid what is behind the glass looks. 100 %: unchanged; 150 %: the launcher's usual glass",
        section = "glass", keywords = "glass saturation vibrancy colour vivid",
    )
    val GlassBrightness = IntSetting(
        key = "glass.brightness", default = 0, min = -40, max = 40, step = 2, unit = "%",
        title = "Brightness through the glass",
        section = "glass", keywords = "glass brightness lighter darker",
    )
    val GlassDepth = BoolSetting(
        key = "glass.depth", default = true,
        title = "Thick glass",
        summary = "The middle swells what is behind it a little as well as the edge bending it, like a drop of water (Full quality)",
        section = "glass", keywords = "glass depth thick lens magnify drop liquid",
    )
    val GlassDispersion = BoolSetting(
        key = "glass.dispersion", default = true,
        title = "Rainbow edges",
        summary = "The bent light splits into colours at the edge, like a prism (Full quality)",
        section = "glass", keywords = "glass dispersion chromatic aberration rainbow prism edge",
    )
    val GlassGlow = IntSetting(
        key = "glass.glow", default = 20, min = 0, max = 100, step = 5, unit = "%",
        title = "Inner glow",
        summary = "Light gathered inside the edge, as in a drop of water",
        section = "glass", keywords = "glass glow inner light shine liquid",
    )
    val GlassShadow = IntSetting(
        key = "glass.shadow", default = 100, min = 0, max = 100, step = 10, unit = "%",
        title = "Shadow",
        summary = "The soft shadow glass casts on the wallpaper",
        section = "glass", keywords = "glass shadow depth elevation",
    )
    val GlassRimColour = ColorSetting(
        key = "glass.rimColour", autoLabel = "White light",
        title = "Edge light colour",
        section = "glass", keywords = "glass edge light rim colour color",
    )
    val GlassLightAngle = IntSetting(
        key = "glass.lightAngle", default = 45, min = 0, max = 345, step = 15, unit = "°",
        title = "Light comes from",
        summary = "The angle of the edge light: 45° is the top left",
        section = "glass", keywords = "glass light angle direction edge rim",
    )
    // P4 (0.9.12): the real glass. Every value shows at once in the preview above and in Liquid glass → Tune the glass.
    val GlassEngine = ChoiceSetting(
        key = "glass.engine", default = "real",
        options = listOf("real" to "Real glass", "lens" to "Simple lens (up to 0.9.11)"),
        title = "Glass",
        summary = "Real glass: the light bends by the shape of the rounded edge, splits into colours, catches the light on one " +
            "side and darkens on the other, with a fine grain (Full quality). The simple lens is the glass of earlier versions",
        section = "glass", keywords = "glass engine shader real liquid lens agsl refraction",
    )
    val GlassBevel = IntSetting(
        key = "glass.bevel", default = 16, min = 4, max = 40, unit = "dp",
        title = "Edge width",
        summary = "How wide the rounded edge that bends the light is; never more than half of a surface",
        section = "glass", keywords = "glass edge bevel width refraction lens",
    )
    val GlassThickness = IntSetting(
        key = "glass.thickness", default = 50, min = 0, max = 100, step = 5, unit = "%",
        title = "Thickness",
        summary = "Thicker glass bends what is behind its edge further",
        section = "glass", keywords = "glass thickness thick depth bend refraction",
    )
    val GlassSpecular = IntSetting(
        key = "glass.specular", default = 60, min = 0, max = 100, step = 5, unit = "%",
        title = "Light on the edge",
        summary = "The edge facing the light catches it, the opposite edge a little, in the edge light colour (\"Light comes from\" below)",
        section = "glass", keywords = "glass specular highlight light edge glint shine rim",
    )
    val GlassShade = IntSetting(
        key = "glass.shade", default = 40, min = 0, max = 100, step = 5, unit = "%",
        title = "Depth shading",
        summary = "The edge away from the light darkens, as thick glass does",
        section = "glass", keywords = "glass shade shading depth dark shadow edge",
    )
    val GlassGrain = IntSetting(
        key = "glass.grain", default = 10, min = 0, max = 100, step = 5, unit = "%",
        title = "Grain",
        summary = "A fine grain over the glass, like frosted glass seen up close",
        section = "glass", keywords = "glass grain noise texture frost",
    )
    val GlassSwell = IntSetting(
        key = "glass.swell", default = 40, min = 5, max = 100, step = 5, unit = "%",
        title = "Swell",
        summary = "How much the middle of thick glass magnifies what is behind it",
        section = "glass", keywords = "glass swell magnify thick dome drop",
    )
    val GlassDispersionAmount = IntSetting(
        key = "glass.dispersionAmount", default = 35, min = 5, max = 100, step = 5, unit = "%",
        title = "Rainbow strength",
        summary = "How far the colours split at the edge",
        section = "glass", keywords = "glass dispersion rainbow prism chromatic amount",
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

    // ---- Buttons (0.6.4, D95) ----

    // P4 (0.9.13): the theme of buttons, chips and round buttons, after WM Keyboard's themes.
    val ThemeControls = ChoiceSetting(
        key = "theme.controls", default = "theme",
        options = listOf("theme" to "Your theme", "launcher" to "The launcher's own buttons (the options below)"),
        title = "Buttons and chips",
        summary = "Your theme styles every button, chip and round button: fill, text, pressed and selected colours, border, " +
            "shape and the glass under them. It starts as your WM Keyboard theme (Nice liquid); import another below",
        section = "buttons", keywords = "theme buttons chips keyboard wm style import export",
    )
    val ThemeJson = TextSetting(key = "theme.json", default = "", title = "Theme", section = "buttons", hidden = true)
    val ThemeColoursApplied = BoolSetting(key = "state.themeColours", default = false, title = "Theme colours applied once", section = "state", hidden = true)
    val ButtonStyle = ChoiceSetting(
        key = "buttons.style", default = "glass",
        options = listOf(
            "glass" to "Liquid glass: bends the glass it sits on",
            "crystal" to "Crystal: drawn glass, lighter on the phone",
            "tint" to "Tinted: flat, as before 0.6.4",
            "outline" to "Outline",
            "solid" to "Solid",
        ),
        title = "Button style",
        summary = "For the buttons in Today, the Now card, the layouts sheet and the clock gallery. Liquid glass follows \"Quality\" in Liquid glass (crystal when it is Tint)",
        section = "buttons", keywords = "button style glass liquid crystal flat outline solid chip",
    )
    val ButtonShape = IntSetting(
        key = "buttons.corner", default = 50, min = 0, max = 50, step = 5, unit = "%",
        title = "Corner roundness",
        summary = "50 %: capsules. Lower values give rounded rectangles, 0 square corners",
        section = "buttons", keywords = "button shape corner radius round square capsule pill",
    )
    val ButtonSize = ChoiceSetting(
        key = "buttons.size", default = "medium",
        options = listOf("small" to "Small", "medium" to "Medium", "large" to "Large"),
        title = "Button size",
        section = "buttons", keywords = "button size height small large big",
    )
    val ButtonTint = IntSetting(
        key = "buttons.tint", default = 12, min = 0, max = 60, step = 2, unit = "%",
        title = "Tint strength",
        summary = "How much white the glass of a button carries; higher stands out more from the sheet",
        section = "buttons", keywords = "button tint opacity fill strength",
    )
    val ButtonRim = IntSetting(
        key = "buttons.rim", default = 85, min = 0, max = 100, step = 5, unit = "%",
        title = "Edge light",
        summary = "The bright rim along a button's edge",
        section = "buttons", keywords = "button edge light rim highlight border shine",
    )
    val ButtonRefraction = IntSetting(
        key = "buttons.refraction", default = 60, min = 0, max = 100, step = 5, unit = "%",
        title = "Refraction",
        summary = "How strongly a liquid glass button bends what is behind it (Full quality only)",
        section = "buttons", keywords = "button refraction lens bend",
    )
    val ButtonMain = ChoiceSetting(
        key = "buttons.main", default = "block",
        options = listOf(
            "block" to "The block's colour",
            "accent" to "The theme's accent",
            "plain" to "Like the other buttons (with your theme: its main button)",
        ),
        title = "Main button colour",
        summary = "The button that matters most where it sits: Done on the block running now, a running focus timer, \"Switch it on\"",
        section = "buttons", keywords = "button main primary colour color accent block done",
    )
    val ButtonSquish = BoolSetting(
        key = "buttons.squish", default = true,
        title = "Squish when pressed",
        summary = "A button shrinks a little under your finger and springs back",
        section = "buttons", keywords = "button press animation squish spring scale",
    )

    // ---- Effects and touch (0.8.0, D111–D113) ----

    /** Colours an effect can take; shared by the touch and the wallpaper effects. */
    val EFFECT_COLOURS = listOf(
        "wallpaper" to "Wallpaper colours",
        "accent" to "Theme accent",
        "rainbow" to "A new colour for every press",
        "white" to "White light",
        "custom" to "Your colour (below)",
    )

    val TouchEffect = ChoiceSetting(
        key = "touch.effect", default = "ripple",
        options = listOf(
            "ripple" to "Ripple: rings spread like on water",
            "drop" to "Drop: a liquid drop lands and splashes",
            "light" to "Light pool: soft light under the finger",
            "ink" to "Ink blot: blooms out and fades",
            "paint" to "Paint splash: splats, flings drops, drips",
            "flood" to "Colour flood: a wash over the whole screen",
            "puff" to "Smoke puff: drifts up and thins out",
            "glint" to "Glint: a star of light",
            "pop" to "Pop: a ring that throws small bits",
            "swoosh" to "Swoosh: curved strokes sweep round",
            "specks" to "Specks: a scatter of tiny dots",
            "bubbles" to "Bubbles: rise from the finger",
            "none" to "None",
        ),
        title = "Touch effect",
        summary = "What a press throws over the screen, like a keyboard theme. It fades a second later and costs nothing while nobody touches",
        section = "effects", keywords = "touch effect press splash ripple paint ink light flood smoke glint pop swoosh specks bubbles drop",
    )
    val TouchWhere = ChoiceSetting(
        key = "touch.where", default = "items",
        options = listOf(
            "items" to "Icons, buttons, pill items and results",
            "everywhere" to "Also empty space on the home screen",
        ),
        title = "Where",
        section = "effects", keywords = "touch effect where empty space everywhere",
    )
    val TouchAmount = IntSetting(
        key = "touch.amount", default = 50, min = 0, max = 100, step = 5, unit = "%",
        title = "Amount",
        summary = "How many drops, specks, bits and strokes",
        section = "effects", keywords = "touch effect amount density count",
    )
    val TouchSpeed = IntSetting(
        key = "touch.speed", default = 70, min = 20, max = 300, step = 10, unit = "%",
        title = "Speed",
        summary = "100 % is the effect's own pace; lower lingers longer",
        section = "effects", keywords = "touch effect speed duration slow fast",
    )
    val TouchBrightness = IntSetting(
        key = "touch.brightness", default = 45, min = 5, max = 100, step = 5, unit = "%",
        title = "Brightness",
        section = "effects", keywords = "touch effect brightness intensity opacity strength",
    )
    val TouchSize = IntSetting(
        key = "touch.size", default = 100, min = 50, max = 200, step = 10, unit = "%",
        title = "Size",
        section = "effects", keywords = "touch effect size scale big small",
    )
    val TouchColour = ChoiceSetting(
        key = "touch.colour", default = "wallpaper", options = EFFECT_COLOURS,
        title = "Colour",
        section = "effects", keywords = "touch effect colour color accent wallpaper rainbow",
    )
    val TouchCustomColour = ColorSetting(
        key = "touch.customColour", autoLabel = "Theme accent",
        title = "Your colour",
        summary = "Used when Colour is \"Your colour\" (touch and wallpaper effects)",
        section = "effects", keywords = "touch effect custom colour color",
    )
    val TouchLight = IntSetting(
        key = "touch.light", default = 35, min = 0, max = 100, step = 5, unit = "%",
        title = "Light from the finger",
        summary = "A soft light on the icon or button under your finger while it is pressed",
        section = "effects", keywords = "touch light glow bloom finger highlight press",
    )
    val WallpaperEffect = ChoiceSetting(
        key = "effects.wallpaper", default = "none",
        options = listOf(
            "none" to "None",
            "orbs" to "Colour orbs: soft pools of colour drifting",
            "bubbles" to "Bubbles: rising slowly through the picture",
            "motes" to "Motes: specks of light floating",
            "snow" to "Snow",
            "rain" to "Rain: drops running down the glass",
        ),
        title = "Moving wallpaper",
        summary = "An effect that moves over the picture, under the icons; glass bends it like the picture. It stops while an app, the drawer or search covers the home screen",
        section = "effects", keywords = "wallpaper effect animated moving orbs bubbles motes snow rain live",
    )
    val WallpaperEffectAmount = IntSetting(
        key = "effects.wallpaperAmount", default = 50, min = 0, max = 100, step = 5, unit = "%",
        title = "Wallpaper effect: amount",
        section = "effects", keywords = "wallpaper effect amount density",
    )
    val WallpaperEffectSpeed = IntSetting(
        key = "effects.wallpaperSpeed", default = 60, min = 0, max = 300, step = 10, unit = "%",
        title = "Wallpaper effect: speed",
        summary = "0 holds it still",
        section = "effects", keywords = "wallpaper effect speed still",
    )
    val WallpaperEffectBrightness = IntSetting(
        key = "effects.wallpaperBrightness", default = 40, min = 5, max = 100, step = 5, unit = "%",
        title = "Wallpaper effect: brightness",
        section = "effects", keywords = "wallpaper effect brightness intensity",
    )
    val WallpaperEffectSize = IntSetting(
        key = "effects.wallpaperSize", default = 100, min = 50, max = 200, step = 10, unit = "%",
        title = "Wallpaper effect: size",
        section = "effects", keywords = "wallpaper effect size scale",
    )
    val WallpaperEffectColour = ChoiceSetting(
        key = "effects.wallpaperColour", default = "wallpaper", options = EFFECT_COLOURS,
        title = "Wallpaper effect: colour",
        section = "effects", keywords = "wallpaper effect colour color",
    )
    val WallpaperEffectReact = IntSetting(
        key = "effects.wallpaperReact", default = 40, min = 0, max = 100, step = 10, unit = "%",
        title = "Wallpaper effect: stirred by touches",
        summary = "Each press speeds it up and brightens it for a moment; 0 ignores touches",
        section = "effects", keywords = "wallpaper effect reactive touch stir energy",
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
        summary = "It comes up as soon as search is opening for good, under a field that is already in its place",
        section = "search", keywords = "keyboard focus",
    )
    val SearchClaude = ChoiceSetting(
        key = "search.claude", default = "questions",
        options = listOf(
            "questions" to "First for questions and longer requests, otherwise after the apps",
            "always" to "Always, after the apps",
            "first" to "Always first",
            "off" to "Off",
        ),
        title = "Ask Claude",
        summary = "Offers \"Ask Claude\" for what you type; !c, !claude or !ai sends it straight away. Tap the search icon in the field to " +
            "switch it to Claude, so Go sends anything you type",
        section = "search", keywords = "claude ai assistant ask question chat prompt",
    )
    val SearchClaudeTarget = ChoiceSetting(
        key = "search.claudeTarget", default = "app",
        options = listOf(
            "app" to "The Claude app (claude.ai in a Chrome tab when it is not installed)",
            "web" to "claude.ai in a Chrome tab",
        ),
        title = "Ask Claude in",
        section = "search", keywords = "claude app web browser chrome tab",
    )
    val SearchClipboard = BoolSetting(
        key = "search.clipboard", default = true,
        title = "Offer what you just copied",
        summary = "For two minutes after you copy text, search shows Paste and Ask Claude about it. Nothing is read until you tap one",
        section = "search", keywords = "clipboard copy paste claude",
    )
    val SearchKeyboardHeight = IntSetting(
        key = "state.keyboardHeight", default = 0, min = 0, max = 10000,
        title = "Height of the keyboard last time, px", section = "state", hidden = true,
    )

    val SearchDim = IntSetting(
        key = "search.dim", default = 40, min = 0, max = 90, step = 5, unit = "%",
        title = "Dim behind search",
        section = "search", keywords = "search dim background wallpaper",
    )
    val SearchBlur = IntSetting(
        // 14 dp since 0.7.2, like the drawer (was 0).
        key = "search.blur", default = 14, min = 0, max = 40, step = 2, unit = "dp",
        title = "Blur behind search",
        summary = "The wallpaper melts into a blurred copy of itself as search opens, as it does for the drawer. The copy is made once, so this costs nothing while moving",
        section = "search", keywords = "search blur background wallpaper",
    )

    // ---- Home menu ----

    /** Encoded launcher actions of the upper ("set up") pill. Edited with the home menu editor. */
    val HomeMenuSetup = StringListSetting(
        key = "homeMenu.setup", default = listOf("settings", "layouts", "today"),
        title = "Set-up pill", section = "homeMenu", hidden = true,
    )
    /** Encoded launcher actions of the lower ("actions") pill. */
    val HomeMenuActions = StringListSetting(
        key = "homeMenu.actions", default = listOf("wallpaper", "edit", "widgets", "add_page", "pages"),
        title = "Actions pill", section = "homeMenu", hidden = true,
    )
    val HomeMenuLabels = BoolSetting(
        key = "homeMenu.labels", default = true,
        title = "Show labels under the icons",
        section = "homeMenu", keywords = "home menu labels names",
    )
    val HomeMenuSink = BoolSetting(
        key = "homeMenu.sink", default = true,
        title = "Home screen sinks back while the menu is open",
        section = "homeMenu", keywords = "home menu depth scale",
    )

    // ---- Wallpaper ----

    val WallpaperHomeBlur = IntSetting(
        key = "wallpaper.homeBlur", default = 0, min = 0, max = 40, step = 2, unit = "dp",
        title = "Blur on the home screen",
        section = "wallpaper", keywords = "wallpaper blur home",
    )
    val WallpaperHomeDim = IntSetting(
        key = "wallpaper.homeDim", default = 0, min = 0, max = 80, step = 5, unit = "%",
        title = "Dim on the home screen",
        section = "wallpaper", keywords = "wallpaper dim darken home",
    )
    val WallpaperLockToo = BoolSetting(
        key = "wallpaper.lockToo", default = false,
        title = "Also set it as the lock screen wallpaper",
        section = "wallpaper", keywords = "wallpaper lock screen",
    )
    val WallpaperSystemWrite = ChoiceSetting(
        key = "wallpaper.systemWrite", default = "soon",
        options = listOf(
            "soon" to "A few seconds after a change",
            "screenoff" to "When the screen turns off",
            "never" to "Never (the launcher's wallpaper stays inside the launcher)",
        ),
        title = "Hand the wallpaper to the system",
        summary = "The launcher shows a new wallpaper at once. Giving it to the phone as well (so it is behind closing apps " +
            "and in recents) takes MagicOS several seconds, in which the phone is sluggish and the system launcher can flash by. " +
            "Quick switches cost one hand-over, a picture the phone already has costs none",
        section = "wallpaper", keywords = "wallpaper system set lag slow layout switch lock screen",
    )
    val WallpaperHistory = IntSetting(
        key = "wallpaper.history", default = 12, min = 4, max = 40, step = 2,
        title = "Recent wallpapers to show",
        summary = "In the home menu's wallpaper strip and the library. Each picture appears once, however often you used it",
        section = "wallpaper", keywords = "wallpaper history recent",
    )
    val WallpaperParallax = IntSetting(
        key = "wallpaper.parallax", default = 0, min = 0, max = 50, step = 5, unit = "%",
        title = "Parallax for new wallpapers",
        summary = "How much wider than the screen a wallpaper is made, so it slides a little as you change pages. " +
            "Each wallpaper can have its own while you place it. 0 keeps it still",
        section = "wallpaper", keywords = "wallpaper parallax scroll pages move slide",
    )
    val WallpaperTransition = ChoiceSetting(
        key = "wallpaper.transition", default = "ripple",
        options = listOf(
            "liquid" to "Liquid: blurs away as the new screen settles",
            "ripple" to "Ripple: a wave from the picture you tapped",
            "ink" to "Ink: dissolves in soft clouds",
            "shine" to "Shine: a pane of glass sweeps across",
            "melt" to "Melt: runs down like paint",
            "zoom" to "Zoom: you pass through the old screen",
            "depth" to "Depth: the old one sinks, the new one comes forward",
            "slide" to "Slide",
            "fade" to "Cross-fade",
            "none" to "None",
        ),
        title = "Transition",
        summary = "When the wallpaper or the layout changes: wallpaper, icons and widgets change together. Ripple, ink, shine and melt run on the GPU",
        section = "wallpaper", keywords = "wallpaper transition crossfade fade animation layout switch",
    )
    val WallpaperTransitionMs = IntSetting(
        key = "wallpaper.transitionMs", default = 520, min = 200, max = 1400, step = 40, unit = "ms",
        title = "Transition length",
        section = "wallpaper", keywords = "wallpaper transition duration speed",
    )
    val WallpaperRotation = ChoiceSetting(
        key = "wallpaper.rotation", default = "off",
        options = listOf(
            "off" to "Off",
            "unlock" to "Every time you unlock",
            "daily" to "Once a day",
        ),
        title = "Change automatically",
        summary = "A new picture is put in place while the screen is off, so it is ready when you unlock. \"Shuffle wallpaper\" in the home menu changes it now",
        section = "wallpaper", keywords = "wallpaper rotation automatic change shuffle daily unlock",
    )
    val WallpaperRotationPool = ChoiceSetting(
        key = "wallpaper.rotationPool", default = "favourites",
        options = listOf(
            "favourites" to "Favourites",
            "recent" to "Recently used",
            "all" to "Everything in the library",
            "folders" to "Linked phone folders",
        ),
        title = "Change between",
        section = "wallpaper", keywords = "wallpaper rotation pool favourites folder",
    )
    val WallpaperRotationShuffle = BoolSetting(
        key = "wallpaper.rotationShuffle", default = true,
        title = "Shuffle",
        summary = "Off: in order",
        section = "wallpaper", keywords = "wallpaper rotation shuffle random order",
    )

    // ---- Study companion ----

    val StudyEnabled = BoolSetting(
        key = "study.enabled", default = false,
        title = "Study companion",
        summary = "Shows the study blocks and tasks your assistant puts into your calendar, and lets you tick them off. " +
            "Asks for calendar access when switched on from the home screen (Home menu → Today, or a study widget)",
        section = "study", keywords = "study calendar tasks blocks plan claude assistant",
    )
    val StudyClockLine = ChoiceSetting(
        key = "study.clockLine", default = "block",
        options = listOf(
            "block" to "Now, next, unmarked, tasks left, then tomorrow",
            "today" to "Today only (nothing about tomorrow)",
            "off" to "Nothing",
        ),
        title = "Under the clock",
        summary = "A line under your clock widget: the block running now with a ring for the time gone; the next block when it starts within the hour; " +
            "a block that ended without Done or Skip; the next block later; the tasks left; then tomorrow's first block. " +
            "Tap it for Today. The pill can show the same: add \"Study block\" to its items",
        section = "study", keywords = "study clock line now current block next glance pill tomorrow unmarked",
    )
    val StudyBlockCards = ChoiceSetting(
        key = "study.blockCards", default = "preview",
        options = listOf(
            "compact" to "Compact: title and time; tap to open",
            "preview" to "Tasks and the start of the notes; tap for everything",
            "full" to "Everything, always",
        ),
        title = "Blocks in Today",
        summary = "Tap a block's title to open or close it. In compact mode the block running now starts open",
        section = "study", keywords = "study today block card expand collapse description notes details",
    )
    val StudyButtonSide = ChoiceSetting(
        key = "study.buttonSide", default = "end",
        options = listOf("end" to "Right", "start" to "Left"),
        title = "Done and Skip buttons",
        section = "study", keywords = "study done skip buttons right left side align",
    )
    val StudyTomorrow = ChoiceSetting(
        key = "study.tomorrow", default = "peek",
        options = listOf(
            "peek" to "First block; tap to see the whole day",
            "all" to "All of tomorrow's blocks and tasks",
            "off" to "Hidden",
        ),
        title = "Tomorrow in Today",
        summary = "Tap a block of tomorrow to read its steps and notes",
        section = "study", keywords = "study tomorrow peek expand next day preview hide",
    )
    val StudyLinkOpener = ChoiceSetting(
        key = "study.linkOpener", default = "tab",
        options = listOf(
            "tab" to "A Chrome tab over the launcher",
            "chrome" to "Chrome",
            "claude" to "The Claude app",
            "system" to "Ask the phone (your default link app)",
        ),
        title = "Open the plan and block links in",
        summary = "Straight to that app, without the phone's link chooser. A Chrome tab is signed in like Chrome. " +
            "When the app is missing or does not open links, a Chrome tab is used",
        section = "study", keywords = "study plan link open chrome claude browser tab redirect chooser",
    )
    val StudyBlockCalendars = TextSetting(
        key = "study.blockCalendars", default = "Study",
        placeholder = "Study",
        title = "Calendars with study blocks",
        summary = "Part of the calendar's name; several separated by commas. Timed events in them are blocks",
        section = "study", keywords = "study calendar name blocks",
    )
    val StudyTaskCalendars = TextSetting(
        key = "study.taskCalendars", default = "Tasks",
        placeholder = "Tasks",
        title = "Calendars with tasks",
        summary = "All-day events in them (and in the block calendars) are tasks for that day",
        section = "study", keywords = "study calendar name tasks todo",
    )
    val StudyWriteBack = BoolSetting(
        key = "study.writeBack", default = true,
        title = "Write ticks back to the calendar",
        summary = "A tick changes the event (✓ in front of the title, [x] in a task line, one status line), which syncs to Google " +
            "so your assistant sees what you finished. Off: the calendar is only read",
        section = "study", keywords = "study write sync done tick calendar",
    )
    val StudyOverdueDays = IntSetting(
        key = "study.overdueDays", default = 14, min = 0, max = 60, unit = "days",
        title = "Show unfinished tasks from the last",
        section = "study", keywords = "study overdue tasks days",
    )
    val StudyNoticeLead = IntSetting(
        key = "study.noticeLead", default = 5, min = 0, max = 30, unit = "min",
        title = "Announce a block this long before it ends",
        summary = "0: only when it starts. Which notices show at all is in Glass notices",
        section = "study", keywords = "study notice block start end minutes",
    )
    val StudyClaudeApp = TextSetting(
        key = "study.claudeApp", default = "com.anthropic.claude",
        placeholder = "com.anthropic.claude",
        title = "App that gets \"Tell Claude\"",
        summary = "Package name of the app the study note is shared to. Empty: the phone's share sheet",
        section = "study", keywords = "study claude share app package note",
    )
    val StudyPlanLink = TextSetting(
        key = "study.planLink", default = "",
        placeholder = "https://…",
        title = "Study plan link",
        summary = "Opened by the \"Plan\" quick action: a web page, a document link, any link an app on the phone opens, or an app's package name (opens that app). Empty: the calendar",
        section = "study", keywords = "study plan link url open",
    )

    // ---- Study companion: the hub, arrival and the evening (0.9.1) ----

    val StudyArrival = BoolSetting(
        key = "study.arrival", default = true,
        title = "Notice when I get home",
        summary = "After the last class of the day: by your home Wi-Fi, by the charger, or by the \"I'm home\" chip in the study hub. " +
            "The time is written into tonight's first block as \"— launcher: home HH:MM\"",
        section = "study", keywords = "study arrival home commute wifi charger trajet",
    )
    val StudyHomeWifi = TextSetting(
        key = "study.homeWifi", default = "",
        placeholder = "Not set",
        title = "Home Wi-Fi name",
        summary = "Being on this network after the last class means you are home. Reading the network's name needs the location permission " +
            "(Android's rule); the study hub asks for it when you tap \"Use this Wi-Fi as home\"",
        section = "study", keywords = "study home wifi ssid network arrival location",
    )
    val StudyArrivalCharger = BoolSetting(
        key = "study.arrivalCharger", default = true,
        title = "The charger also means home",
        summary = "Plugged in at least 45 minutes after the last class",
        section = "study", keywords = "study arrival charger plugged home",
    )
    val StudyAttendance = BoolSetting(
        key = "study.attendance", default = true,
        title = "Ask after each class whether I was there",
        summary = "Five minutes after a class ends, the line under the clock asks \"present?\". ✗ writes \"Note: absent <module> <time>\" " +
            "to your task calendar for the check-in, and !abs counts them",
        section = "study", keywords = "study attendance absent present class absence",
    )
    val StudyLeaveBy = BoolSetting(
        key = "study.leaveBy", default = true,
        title = "Morning: when to leave",
        summary = "\"Leave by 6:30\" under the clock from an hour before: the first class minus the trip. Red from 10 minutes before",
        section = "study", keywords = "study leave commute morning trajet bus class",
    )
    val StudyCommute = IntSetting(
        key = "study.commute", default = 90, min = 15, max = 180, step = 5, unit = "min",
        title = "Morning trip to class",
        section = "study", keywords = "study commute trip travel morning minutes trajet",
    )
    val StudySpeedBump = BoolSetting(
        key = "study.speedBump", default = true,
        title = "Speed bump during blocks",
        summary = "While a study block runs, an app with the tag below opens only after \"Open anyway\", which waits 5 seconds. " +
            "Only for apps opened from Liquid Launcher (not from notifications or recents, which Android does not let a launcher hold back)",
        section = "study", keywords = "study focus distracting speed bump block apps social",
    )
    val StudySpeedBumpTags = TextSetting(
        key = "study.speedBumpTags", default = "distracting",
        placeholder = "distracting",
        title = "Tag of the apps that wait",
        summary = "A tag name, or several separated by commas. Give apps the tag from their long-press menu (Tags)",
        section = "study", keywords = "study distracting tag speed bump",
    )
    val StudyFeedCalendar = TextSetting(
        key = "study.feedCalendar", default = "Launcher",
        placeholder = "Launcher",
        title = "Calendar with \"launcher-data\"",
        summary = "Part of the calendar's name. The evening check-in writes one all-day event \"launcher-data\" there, with exams, absences, " +
            "a flashcard and grades as JSON (docs/05-study-integration.md)",
        section = "study", keywords = "study launcher-data calendar json claude exams absences flashcard grades",
    )

    /** The local copy of launcher-data, used while the calendar has none (edited in the Study companion screen). */
    val StudyFeedLocal = TextSetting(
        key = "study.feedLocal", default = "", title = "Local launcher-data", section = "study", hidden = true,
    )
    val StudyGlassTint = BoolSetting(
        key = "study.glassTint", default = true,
        title = "Glass in the colour of the block",
        summary = "Every glass surface takes a touch of the colour of the block now or next (its colour in your calendar), easing from one block to the next",
        section = "study", keywords = "study glass tint colour module block e3",
    )
    // C5 (0.9.16): notification triage during blocks.
    val StudyQuiet = BoolSetting(
        key = "study.quiet", default = false,
        title = "Quiet during blocks",
        summary = "While a study block runs, notifications wait until it ends: Android gives them back afterwards, and the home " +
            "screen says how many waited and from which apps. Calls, alarms, music and the people, school email and apps " +
            "below always get through. Needs notification access (below)",
        section = "study", keywords = "notifications quiet focus block triage do not disturb hold snooze c5",
    )
    val StudyQuietPeople = TextSetting(
        key = "study.quietPeople", default = "", placeholder = "Maman, Yassine",
        title = "Let through: people",
        summary = "Names as they appear in your messages, separated by commas",
        section = "study", keywords = "quiet notifications people contacts whitelist",
    )
    val StudyQuietEmail = TextSetting(
        key = "study.quietEmail", default = "", placeholder = "@school.tn",
        title = "Let through: school email",
        summary = "Your school address or its end (for example @school.tn): mail for that account gets through",
        section = "study", keywords = "quiet notifications email school mail whitelist",
    )
    val StudyQuietTags = TextSetting(
        key = "study.quietTags", default = "important",
        title = "Let through: apps with the tag",
        summary = "Give an app this tag from its long-press menu (Tags); several, separated by commas",
        section = "study", keywords = "quiet notifications apps tag important whitelist",
    )
    /** A4 / C4 (0.9.11): saved-layout ids, chosen in the Study companion screen; empty = none. */
    val StudyCommuteLayout = TextSetting(key = "study.commuteLayout", default = "", title = "Layout while travelling home", section = "study", hidden = true)
    val StudyExamLayout = TextSetting(key = "study.examLayout", default = "", title = "Layout in exam week", section = "study", hidden = true)
    val ContextActive = TextSetting(key = "state.ctxActive", default = "", title = "Context layout on", section = "state", hidden = true)
    val ContextApplied = TextSetting(key = "state.ctxApplied", default = "", title = "Context layout applied", section = "state", hidden = true)
    val ContextBase = TextSetting(key = "state.ctxBase", default = "", title = "Layout before the context", section = "state", hidden = true)
    val StudyRest = IntSetting(
        key = "study.rest", default = 20, min = 0, max = 45, step = 5, unit = "min",
        title = "Rest after getting home",
        summary = "The line under the clock shows the rest running out, then \"Start\" for tonight's first block. 0: no rest",
        section = "study", keywords = "study rest decompression home arrival break",
    )
    val StudyWindDown = ChoiceSetting(
        key = "study.windDown", default = "1350",
        options = listOf("off" to "Off", "1290" to "21:30", "1320" to "22:00", "1350" to "22:30", "1380" to "23:00"),
        title = "Wind down from",
        summary = "From this time until morning the wallpaper and the glass lose most of their colour, and the line under the clock offers the evening check-in until you send it",
        section = "study", keywords = "study wind down evening night check-in saturation colour",
    )
    val StudyShiftLead = IntSetting(
        key = "study.shiftLead", default = 20, min = 0, max = 60, step = 5, unit = "min",
        title = "Offer to shift the evening when home later than this before the first block",
        summary = "The shift moves every block you have not started so the first one begins this long after you got home",
        section = "study", keywords = "study shift tonight late home delay move blocks",
    )
    val StudyLatestEnd = ChoiceSetting(
        key = "study.latestEnd", default = "1380",
        options = listOf("1290" to "21:30", "1320" to "22:00", "1350" to "22:30", "1380" to "23:00", "1410" to "23:30", "1440" to "Midnight"),
        title = "An evening ends by",
        summary = "A shift that would run past it offers to shorten the lowest-priority block instead",
        section = "study", keywords = "study shift latest end 23:00 shorten",
    )
    val StudyClassCalendars = TextSetting(
        key = "study.classCalendars", default = "",
        placeholder = "Your main calendar",
        title = "Calendars with classes",
        summary = "Part of the calendar's name; several separated by commas. Empty: the account's main calendar. " +
            "Timed events in them are classes; the end of the last one starts the trip home",
        section = "study", keywords = "study class timetable calendar school last class",
    )
    val StudyHeader = TextSetting(
        key = "study.header", default = "1A ESSAI · P2",
        placeholder = "No header",
        title = "Header of what is sent to Claude",
        summary = "Sent as \"[header · now: block or free]\" in front of questions and templates",
        section = "study", keywords = "study claude header prompt context",
    )
    val StudyCheckin = TextSetting(
        key = "study.checkin", default = "Evening check-in. Here is my day; ask me what you need, then plan tomorrow.",
        placeholder = "Evening check-in",
        title = "Evening check-in message",
        summary = "The study hub's Evening check-in button sends this with today's log to Claude",
        section = "study", keywords = "study evening check-in claude prompt",
    )

    /** "Label|prompt" lines; the hub shows the labels as chips. */
    val StudyTemplates = StringListSetting(
        key = "study.templates",
        default = listOf(
            "Explain|Explain this to me step by step, with one worked example:",
            "Quiz me|Quiz me on the block I am working on: five short questions, one at a time.",
            "Stuck|I am stuck on this step. Give me a hint, not the answer:",
            "Reschedule|I missed a block today. Suggest how to reschedule it this week without touching fixed dates.",
            "Résumé|Fais-moi un résumé en 5 points de ce que je viens d'étudier :",
        ),
        title = "Prompt templates",
        summary = "One per line as \"Label|prompt\". Shown as chips in the study hub; a tap sends the prompt to Claude with the header",
        section = "study", keywords = "study claude prompt templates chips",
    )
    val StudyHubSize = IntSetting(
        key = "study.hubSize", default = 76, min = 50, max = 92, step = 2, unit = "% of the screen",
        title = "Study hub height",
        section = "study", keywords = "study hub size height pill",
    )
    val StudyHubBounce = IntSetting(
        key = "study.hubBounce", default = 35, min = 0, max = 100, step = 5, unit = "%",
        title = "Study hub bounce",
        summary = "How much the hub overshoots as it grows out of the pill. 0: none",
        section = "study", keywords = "study hub spring bounce animation motion",
    )
    val StudyClockAction = ChoiceSetting(
        key = "study.clockAction", default = "auto",
        options = listOf(
            "auto" to "What fits: Focus while a block runs, ✓ for one that ended",
            "focus" to "Focus timer",
            "done" to "Mark the block done",
            "claude" to "Ask Claude about the block",
            "none" to "No button",
        ),
        title = "Button on the line under the clock",
        section = "study", keywords = "study clock line action button focus done",
    )

    // ---- Glass notices ----

    val NoticesEnabled = BoolSetting(
        key = "notices.enabled", default = true,
        title = "Show glass notices",
        summary = "A short label over the home screen when something happens. Tap one to act on it",
        section = "notices", keywords = "notice notification label toast message",
    )
    val NoticePosition = ChoiceSetting(
        key = "notices.position", default = "top",
        options = listOf("top" to "Top", "bottom" to "Bottom, above the pill", "centre" to "Centre"),
        title = "Position",
        section = "notices", keywords = "notice position top bottom centre",
    )
    val NoticeDuration = IntSetting(
        key = "notices.duration", default = 1800, min = 800, max = 6000, step = 200, unit = "ms",
        title = "Stays for",
        summary = "A newer notice replaces it sooner",
        section = "notices", keywords = "notice duration time length",
    )
    val NoticeStyle = ChoiceSetting(
        key = "notices.style", default = "glass",
        options = listOf("glass" to "Liquid glass", "solid" to "Solid, in the accent colour", "plain" to "Text only"),
        title = "Style",
        section = "notices", keywords = "notice style glass solid plain",
    )
    val NoticeIcons = BoolSetting(
        key = "notices.icons", default = true,
        title = "Icon next to the text",
        section = "notices", keywords = "notice icon",
    )
    val NoticeLayout = BoolSetting(
        key = "notices.kind.layout", default = true,
        title = "Layout switched",
        section = "notices", keywords = "notice layout switch name",
    )
    val NoticeWallpaper = BoolSetting(
        key = "notices.kind.wallpaper", default = true,
        title = "Wallpaper changed",
        summary = "Shuffle and the wallpaper strip; also when another app replaced the launcher's wallpaper",
        section = "notices", keywords = "notice wallpaper change shuffle",
    )
    val NoticeTags = BoolSetting(
        key = "notices.kind.tags", default = true,
        title = "Tag filter applied or cleared",
        section = "notices", keywords = "notice tag filter drawer",
    )
    val NoticeRedirect = BoolSetting(
        key = "notices.kind.redirect", default = true,
        title = "Redirect paused or back on",
        section = "notices", keywords = "notice redirect pause resume",
    )
    val NoticeUpdate = BoolSetting(
        key = "notices.kind.update", default = true,
        title = "Update available",
        summary = "After the daily check in Updates found a newer build",
        section = "notices", keywords = "notice update build new version",
    )
    val NoticeStudy = BoolSetting(
        key = "notices.kind.study", default = true,
        title = "Study block starting or ending",
        summary = "Arrives with the study companion",
        section = "notices", keywords = "notice study block calendar start",
    )
    val NoticeTask = BoolSetting(
        key = "notices.kind.task", default = true,
        title = "Task due",
        summary = "Arrives with the study companion",
        section = "notices", keywords = "notice task due deadline",
    )
    val NoticeInfo = BoolSetting(
        key = "notices.kind.info", default = true,
        title = "Other messages",
        section = "notices", keywords = "notice info message",
    )

    /** Notice kind id (NoticeKind.id) to its switch. */
    val noticeKinds: Map<String, BoolSetting> by lazy {
        mapOf(
            "layout" to NoticeLayout, "wallpaper" to NoticeWallpaper, "tags" to NoticeTags, "redirect" to NoticeRedirect,
            "update" to NoticeUpdate, "study" to NoticeStudy, "task" to NoticeTask, "info" to NoticeInfo,
        )
    }

    // ---- Saved layouts ----

    val LayoutsAutoSave = BoolSetting(
        key = "layouts.autoSave", default = true,
        title = "Keep changes in each layout",
        summary = "Before switching, the current layout is saved with your latest changes. Off: switching drops changes you did not save",
        section = "layouts", keywords = "layouts auto save keep changes",
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

    /** Keys of the options changed most recently, newest first (shown at the top of the settings app). */
    val RecentlyChanged = StringListSetting(
        key = "state.recentlyChanged", default = emptyList(),
        title = "Recently changed options", section = "state", hidden = true,
    )
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
        RedirectEnabled, OnHome, FastOnHomeAnimation, FastDelay, OnHomeDelay, RequireHomeScreen, HomeScreenFallback,
        ReopenRecents, ReopenWindow, ReopenOnHint, OnRecentsExit, OnRecentsExitDelay,
        ScreenOffMode, ScreenOffDelay, UnlockCheck,
        SystemLauncherPackage, HomeClasses, RecentsIds, RecentsIdSubstrings, HomeIds, DrawerIds, LearnedIds, PollInterval,
        ExcludeCalls, ExcludeSplitScreen, SystemHomePause, GuardCount, GuardWindow, GuardPause,
        SettleStyle, SettleDuration, ExcludeFromRecents,
        HomeColumns, HomeRows, HomeIconSize, HomeLabels, HomeLabelSize,
        HomePageTransition,
        ThemeTextColor, ThemeAccent, ThemeGlassColor, ThemeFont, ThemeClockFont, ThemeClockWeight, ThemeIconShape, ThemeFontFile,
        FolderPreview, FolderBackground, FolderShape, FolderTapFirst, FolderColumns,
        DockEnabled, DockSlots, DockBackground, DockLabels,
        IconPack, IconPackHome, IconPackDrawer, IconMask,
        ThemedIcons, ThemedIconsWhere, ThemedIconsUncovered, ThemedIconsPacks, ThemedIconsColors, ThemedIconsStyle,
        HomeMarginSide, HomeMarginTop, HomeMarginBottom, Clock24h, ClockSize, ClockDate, ClockGlass,
        GestureSwipeUp, GestureSwipeDown, GestureDoubleTap, GestureLongPress,
        GesturePinchIn, GesturePinchOut, GestureTwoUp, GestureTwoDown, GestureEdgeLeftUp, GestureEdgeLeftDown, GestureEdgeRightUp, GestureEdgeRightDown, GestureEdgeWidth,
        HapticsEnabled, HapticsStrength, HapticDrawer, HapticPickUp, LaunchAnimation, MotionStyle, MotionSpeed, UpdatesDaily,
        WidgetBackground,
        PillEnabled, PillSlots, PillPosition, PillOffset, PillWidth, PillHeight, PillCorner, PillIconSize,
        SearchCards,
        PillStudyLook, PillStudyGlyph, PillStudyApp, PillStudyPicture, PillStudyFrame, PillStudySize, PillStudyTint, PillStudyRing, PillSlotGestures,
        PillHint, PillTap, PillLongPress, PillSwipeUp, PillSwipeDown,
        DrawerColumns, DrawerIconSize, DrawerLabels, DrawerFromBottom, DrawerAutoKeyboard, DrawerSort,
        DrawerWork, DrawerCloseOnLaunch, DrawerSideMargin, DrawerDim, DrawerTabsPosition,
        DrawerTransition, DrawerTravel, DrawerPillMorph, DrawerSpeed, DrawerBounce, DrawerRise, DrawerHomeScale, DrawerHomeBlur, DrawerBlurLive,
        DrawerScrollbar, DrawerScrollBubble, DrawerScrollEffect, DrawerScrollStrength, DrawerOverscroll,
        IndexStyle, IndexRailHeight, IndexRailLift, IndexRailInset, IndexVisibility, IndexSide, IndexTrack, IndexReach, IndexEnds, IndexLetterSize, IndexSpacing, IndexBubble, IndexBubblePlace, IndexLetterGap, IndexBubbleGap, IndexHaptics, IndexFollow, IndexSpring, IndexHideDelay, IndexFocus, DrawerTopSections,
        DrawerTabsStyle, DrawerTabsSize, DrawerTabsIcons, DrawerTabsButtons, DrawerTabMenu, DrawerTabsInSearch,
        TagStripPosition, TagButtonPosition, TagButtonSize, TagStripOrder, TagStripCount, TagStripCounts, TagTap, TagMatch, TagSwipe, TagFilterKeep, TagPinned,
        MenuEntries,
        SearchTransition, SearchBounce, SearchSpeed, SearchStretch, SearchField, SearchOpenKeyboard, SearchBlur, SearchDim, SearchHomeScale,
        SearchClaude, SearchClaudeTarget, SearchClipboard, SearchEngine, SearchKeyboardHeight,
        HomeMenuSetup, HomeMenuActions, HomeMenuLabels, HomeMenuSink,
        WallpaperHomeBlur, WallpaperHomeDim, WallpaperLockToo, WallpaperSystemWrite, WallpaperHistory, WallpaperParallax, WallpaperTransition, WallpaperTransitionMs,
        WallpaperRotation, WallpaperRotationPool, WallpaperRotationShuffle, LayoutsAutoSave,
        StudyEnabled, StudyClockLine, StudyBlockCards, StudyButtonSide, StudyTomorrow, StudyBlockCalendars, StudyTaskCalendars, StudyWriteBack, StudyOverdueDays, StudyNoticeLead, StudyClaudeApp, StudyPlanLink, StudyLinkOpener,
        StudyAttendance, StudyLeaveBy, StudyCommute, StudyRest, StudyWindDown, StudySpeedBump, StudySpeedBumpTags, StudyFeedCalendar, StudyFeedLocal, StudyGlassTint,
        StudyCommuteLayout, StudyExamLayout, ContextActive, ContextApplied, ContextBase,
        StudyQuiet, StudyQuietPeople, StudyQuietEmail, StudyQuietTags,
        StudyArrival, StudyHomeWifi, StudyArrivalCharger, StudyShiftLead, StudyLatestEnd, StudyClassCalendars, StudyHeader, StudyCheckin, StudyTemplates,
        StudyHubSize, StudyHubBounce, StudyClockAction,
        NoticesEnabled, NoticePosition, NoticeDuration, NoticeStyle, NoticeIcons,
        NoticeLayout, NoticeWallpaper, NoticeTags, NoticeRedirect, NoticeUpdate, NoticeStudy, NoticeTask, NoticeInfo,
        GlassQuality, GlassEngine, GlassAuto, GlassCap, GlassFullPercent, GlassTintMode,
        GlassBevel, GlassThickness, GlassSpecular, GlassShade, GlassGrain, GlassRim,
        GlassSaturation, GlassBrightness, GlassDepth, GlassSwell, GlassDispersion, GlassDispersionAmount, GlassGlow, GlassShadow, GlassRimColour, GlassLightAngle,
        ThemeControls, ThemeJson, ThemeColoursApplied,
        ButtonStyle, ButtonShape, ButtonSize, ButtonTint, ButtonRim, ButtonRefraction, ButtonMain, ButtonSquish,
        TouchEffect, TouchWhere, TouchAmount, TouchSpeed, TouchBrightness, TouchSize, TouchColour, TouchCustomColour,
        PressFeel, PressDepth, PressSpeed, EditWobble, PressWobble, TouchLight,
        WallpaperEffect, WallpaperEffectAmount, WallpaperEffectSpeed, WallpaperEffectBrightness, WallpaperEffectSize,
        WallpaperEffectColour, WallpaperEffectReact,
    ) + GlassDefault.settings + glassSurfaces.flatMap { it.settings } + listOf(
        LogEnabled, LogVerbosity, LogSize,
        RecentlyChanged, LastSystemLauncherVersion, LastSystemLauncher, DetectorVerified,
        StatsKey, StatsHomeSeen, StatsHomeUnseen, StatsRecentsSeen, StatsRecentsUnseen, StatsInterrupted, StatsFallbacks, StatsReopened, SetupAppLaunchDone, SetupEmptyHomeDone, SetupMacrosDone,
    )

    /**
     * Keys that belong to a saved home layout: the home grid, clock, gestures, pill, icons,
     * home menu and the home screen's wallpaper blur and dim. Everything else is global.
     */
    val layoutKeys: Set<String> by lazy {
        val sections = setOf("home", "clock", "gestures", "pill", "icons", "homeMenu", "folders", "dock")
        all.filter { it.section in sections }.map { it.key }.toSet() +
            setOf(WallpaperHomeBlur.key, WallpaperHomeDim.key)
    }

    /** Keys that make up a theme: the Theme section and the glass look (not its quality, which is about this phone). */
    val themeKeys: Set<String> by lazy {
        all.filter { it.section == "theme" || it.section == "buttons" || it.section == "effects" || (it.section == "glass" && it != GlassQuality && it != GlassAuto && it != GlassCap) }
            .map { it.key }.toSet()
    }

    /** Keys a backup leaves out: counters, device facts and setup state of this phone. */
    fun isDeviceState(key: String): Boolean {
        val setting = all.firstOrNull { it.key == key } ?: return key.startsWith("stats.") || key.startsWith("state.")
        return setting.section == "state" || setting == GlassCap
    }

    fun inSection(sectionId: String): List<Setting<*>> = all.filter { it.section == sectionId && !it.hidden }

    /**
     * Options for people who tune: shown under "Advanced" at the end of their section, so the
     * first screenful of each section holds what most changes are about. Search finds them all.
     */
    val advancedKeys: Set<String> by lazy {
        setOf(
            // Redirect timing and recovery
            FastOnHomeAnimation, FastDelay, OnHomeDelay, RequireHomeScreen, HomeScreenFallback, ReopenWindow, ReopenOnHint,
            OnRecentsExitDelay, ScreenOffDelay, UnlockCheck,
            // Fine tuning of motion
            DrawerTravel, DrawerBounce, DrawerRise, DrawerHomeScale, DrawerBlurLive, DrawerScrollStrength, DrawerOverscroll,
            IndexReach, IndexEnds, IndexFollow, IndexSpring, IndexHideDelay, IndexSpacing, IndexBubbleGap,
            ButtonRefraction, ButtonSquish, PressDepth, PressWobble, SearchHomeScale, SearchClaudeTarget,
            TouchWhere, TouchCustomColour, WallpaperEffectReact,
            GlassShadow, GlassRimColour, GlassLightAngle, GlassBrightness,
            GestureEdgeWidth, HapticsStrength,
            // Looks most people leave alone
            HomeMarginSide, HomeMarginTop, HomeMarginBottom, DrawerSideMargin, PillOffset, PillCorner, PillIconSize,
            ThemedIconsWhere, ThemedIconsUncovered, ThemedIconsPacks, ThemedIconsColors, ThemedIconsStyle, IconMask,
            GlassAuto, WallpaperSystemWrite, WallpaperHistory, WallpaperTransitionMs,
            NoticeDuration, NoticeIcons, TagFilterKeep, StudyOverdueDays, StudyClaudeApp,
        ).map { it.key }.toSet() + glassSurfaces.flatMap { it.settings }.map { it.key }
    }

    fun isAdvanced(setting: Setting<*>): Boolean = setting.key in advancedKeys

    /**
     * Finds options by what they are called, described as, or filed under. Every word of the
     * query has to match somewhere: as part of a word, or (from four letters) as a word one
     * typing mistake away. Matches in the title come first, then in the section's name.
     */
    fun search(query: String): List<Setting<*>> {
        val words = query.lowercase().split(' ', ',', '-').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val sectionTitles = Sections.all.associate { it.id to "${it.title} ${it.group}".lowercase() }
        return all.asSequence()
            .filter { !it.hidden }
            .mapNotNull { setting ->
                val title = setting.title.lowercase()
                val text = setting.searchText + " " + sectionTitles[setting.section].orEmpty()
                val textWords by lazy { text.split(' ', ',', '.', ':', ';', '(', ')', '"', '/', '-').filter { it.isNotBlank() } }
                var score = 0
                for (word in words) {
                    score += when {
                        word in title -> 4
                        word in text -> 2
                        word.length >= 4 && textWords.any { nearly(word, it) } -> 1
                        else -> return@mapNotNull null
                    }
                }
                setting to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()
    }

    /** True when [word] is [other] (or the start of it) with one letter wrong, missing or extra. */
    internal fun nearly(word: String, other: String): Boolean {
        if (other.length < word.length - 1) return false
        val target = if (other.length > word.length + 1) other.substring(0, word.length) else other
        if (kotlin.math.abs(target.length - word.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < word.length && j < target.length) {
            if (word[i] == target[j]) {
                i++
                j++
                continue
            }
            if (++edits > 1) return false
            when {
                word.length > target.length -> i++
                word.length < target.length -> j++
                else -> {
                    i++
                    j++
                }
            }
        }
        return edits + (word.length - i) + (target.length - j) <= 1
    }
}
