package dev.liquid.launcher.core.settings

/*
 * W4 (0.10.1): where every option lives. Settings has seven entries (eight since 0.16.0: Calendar); each holds pages, one per surface of the
 * launcher (the pill, the dock, the drawer…), and a page shows its options in up to four tabs (Layout, Look,
 * Behaviour, More) under its live preview. The storage keys of 0.9 are unchanged, so nothing is reset; the
 * sections of 0.9 still open the page that took their options ([forSection]), so links and gestures that open a
 * section keep working.
 */

/** The entries of the first settings screen (0.16.0: Calendar is the eighth). */
enum class SettingsEntry(val title: String, val summary: String) {
    HOME("Home", "Grid, pill, dock, folders, clock, menus and gestures"),
    DRAWER("Drawer and search", "The app drawer, its tabs and tags, the letter index and search"),
    STUDY("Study", "Blocks and tasks from your calendar, the hub, quiet, arrival home and Claude"),
    CALENDAR("Calendar", "The calendar from the clock's date: views, look, your calendars, holidays and the school year"),
    LOOK("Look", "The Theme lab, colours and fonts, icons, liquid glass and buttons"),
    WALLPAPER("Wallpaper and layouts", "Your wallpapers, blur and dim, changing by themselves; layouts and backup"),
    MOTION("Motion and feel", "Animations, haptics, press and touch effects, the moving wallpaper, notices"),
    SYSTEM("System", "The redirect, backup and Claude, setup, diagnostics, the debug log, updates and about"),
}

/** The tabs of a page, in this order. */
enum class SettingsTab(val title: String) {
    LAYOUT("Layout"),
    LOOK("Look"),
    BEHAVIOUR("Behaviour"),
    MORE("More"),
}

/**
 * A page: one surface of the launcher. [tabs] names its options by key (a key ending in `*` is a prefix); an
 * option of one of its [sections] that no page names goes to [rest].
 */
class SettingsSurface(
    val id: String,
    val title: String,
    val summary: String,
    val entry: SettingsEntry,
    val sections: List<String>,
    tabs: Map<SettingsTab, List<String>>,
    val rest: SettingsTab = SettingsTab.MORE,
    /** Not listed anywhere: the launcher's own state, stored like options. */
    val internal: Boolean = false,
) {
    internal val exact: Map<String, SettingsTab> = tabs.flatMap { (tab, keys) -> keys.filter { !it.endsWith("*") }.map { it to tab } }.toMap()
    internal val prefixes: List<Pair<String, SettingsTab>> = tabs.flatMap { (tab, keys) -> keys.filter { it.endsWith("*") }.map { it.dropLast(1) to tab } }

    internal fun names(key: String): SettingsTab? = exact[key] ?: prefixes.firstOrNull { key.startsWith(it.first) }?.second
}

/** Everything the registry says about an option, in one place (Explain, the AI export, tests). */
data class SettingInfo(
    val id: String,
    val title: String,
    val summary: String,
    val type: String,
    val default: String,
    /** The range or the choices. */
    val values: String,
    /** "layout" (saved with each home layout), "phone" (this phone's facts) or "launcher". */
    val scope: String,
    /** "key" (on), "!key" (off) or "key=a|b": what must hold for this option to matter. */
    val dependsOn: String?,
    /** "basic" or "advanced". */
    val level: String,
    val keywords: String,
    val entry: SettingsEntry,
    val surface: SettingsSurface,
    val tab: SettingsTab,
)

object SettingsMap {
    private val L = SettingsTab.LAYOUT
    private val K = SettingsTab.LOOK
    private val B = SettingsTab.BEHAVIOUR
    private val M = SettingsTab.MORE

    val surfaces: List<SettingsSurface> = listOf(
        // ---- Home ----
        SettingsSurface(
            "home", "Home screen", "Columns, rows, icons, labels, margins, widgets", SettingsEntry.HOME, listOf("home"),
            mapOf(
                L to listOf("home.columns", "home.rows", "home.iconSize", "home.labels", "home.labelSize", "home.marginSide", "home.marginTop", "home.marginBottom"),
                K to listOf("widgets.background", "widgets.restyle"),
                B to listOf("home.pageTransition"),
            ),
        ),
        SettingsSurface(
            "pill", "Pill", "Its items, size, position, the study item and its gestures", SettingsEntry.HOME, listOf("pill"),
            mapOf(
                L to listOf("pill.enabled", "pill.position", "pill.offset", "pill.width", "pill.height", "pill.iconSize", "pill.live.*"),
                K to listOf("pill.corner", "pill.hint", "pill.study.*"),
                B to listOf("pill.tap", "pill.longPress", "pill.swipeUp", "pill.swipeDown"),
            ),
        ),
        SettingsSurface(
            "dock", "Dock", "A row of apps above the pill on every page", SettingsEntry.HOME, listOf("dock"),
            mapOf(L to listOf("dock.enabled", "dock.slots", "dock.labels"), K to listOf("dock.background")),
        ),
        SettingsSurface(
            "folders", "Folders", "Preview, tile, columns and what a tap does", SettingsEntry.HOME, listOf("folders"),
            mapOf(L to listOf("folder.preview", "folder.columns"), K to listOf("folder.background", "folder.shape"), B to listOf("folder.tapFirst")),
        ),
        SettingsSurface(
            "clock", "Clock", "Defaults for new clocks; each clock has its own Customise sheet", SettingsEntry.HOME, listOf("clock"),
            mapOf(L to listOf("home.clockSize", "home.clockDate"), K to listOf("home.clock24h", "home.clockGlass")),
        ),
        SettingsSurface(
            "menus", "Menus", "The home menu from holding empty space, and an app's long-press menu", SettingsEntry.HOME, listOf("homeMenu", "menu"),
            mapOf(L to listOf("homeMenu.labels"), B to listOf("homeMenu.sink")),
        ),
        SettingsSurface(
            "gestures", "Gestures", "Swipes, taps, pinches, two fingers and the edges of the home screen", SettingsEntry.HOME, listOf("gestures"),
            mapOf(L to listOf("gesture.edgeWidth"), B to listOf("gesture.*")),
            rest = B,
        ),
        // ---- Drawer and search ----
        SettingsSurface(
            "drawer", "App drawer", "Grid, sorting, the work profile, opening, blur and scrolling", SettingsEntry.DRAWER, listOf("drawer", "drawerMotion"),
            mapOf(
                L to listOf("drawer.columns", "drawer.iconSize", "drawer.labels", "drawer.fromBottom", "drawer.sideMargin", "drawer.sort", "drawer.work"),
                K to listOf("drawer.dim", "drawer.homeBlur", "drawer.blurLive", "drawer.homeScale", "drawer.scrollbar", "drawer.scrollBubble"),
                B to listOf(
                    "drawer.autoKeyboard", "drawer.closeOnLaunch", "drawer.transition", "drawer.travel", "drawer.pillMorph", "drawer.speed",
                    "drawer.bounce", "drawer.rise", "drawer.scrollEffect", "drawer.scrollStrength", "drawer.overscroll",
                ),
            ),
        ),
        SettingsSurface(
            "tags", "Tabs and tags", "Your drawer tabs, the tag chips and the full tag list", SettingsEntry.DRAWER, listOf("tags"),
            mapOf(
                L to listOf("drawer.tabsPosition", "drawer.tabsSize", "tags.stripPosition", "tags.button", "tags.buttonSize", "tags.stripOrder", "tags.stripCount"),
                K to listOf("drawer.tabsStyle", "drawer.tabsIcons", "tags.stripCounts"),
                B to listOf("drawer.tabsInSearch", "tags.tap", "tags.match", "tags.swipe", "tags.keep"),
            ),
        ),
        SettingsSurface(
            "index", "Letter index", "The letters at the drawer's edge: the short rail or the full strip", SettingsEntry.DRAWER, listOf("drawerIndex"),
            mapOf(
                L to listOf(
                    "index.style", "index.visibility", "index.dial", "index.areasOnly",
                    "index.stringTop", "index.stringBottom", "index.railHeight", "index.railLift", "index.railInset", "index.side",
                    "index.reach", "index.ends", "index.letterSize", "index.spacing", "index.letterGap", "index.bubblePlace", "index.bubbleGap",
                ),
                K to listOf(
                    "index.dialColour", "index.dialCustom", "index.dialOpacity", "index.dialScrollOpacity",
                    "index.letterColour", "index.letterCustom", "index.bubble", "index.focus",
                ),
                B to listOf("index.haptics", "index.follow", "index.track", "index.spring", "index.hideDelay"),
            ),
        ),
        SettingsSurface(
            "search", "Search", "Its cards, its field, how it opens, the keyboard, web search and Ask Claude", SettingsEntry.DRAWER, listOf("search"),
            mapOf(
                L to listOf("search.field", "search.fieldHeight", "search.fieldWidth", "search.fieldGap", "search.homeScale"),
                K to listOf("search.blur", "search.dim"),
                B to listOf("motion.search", "search.bounce", "search.speed", "search.stretch", "search.keyboard", "search.clipboard"),
                M to listOf("search.find.*", "search.engine", "search.claude", "search.claudeTarget"),
            ),
        ),
        // 0.12.0 (D171, D172): Med's keyboard, WM Keyboard, and the launcher.
        SettingsSurface(
            "keyboard", "Keyboard", "WM Keyboard: your wallpaper behind the keys, a theme that follows it, a mode for search, Liquid from any app",
            SettingsEntry.DRAWER, listOf("keyboard"),
            mapOf(
                // 0.13.1 (D180): the wallpaper behind the keys, above the theme it goes with.
                K to listOf("keyboard.wallpaper", "keyboard.wallpaperLook"),
                B to listOf("search.selectionEntry"),
            ),
        ),
        // ---- Study ----
        SettingsSurface(
            "study", "Study companion", "The line under the clock, the hub, quiet, speed bump, arrival home, calendars and Claude",
            SettingsEntry.STUDY, listOf("study"),
            mapOf(
                // 0.10.2: what Med looked for first sits on the first tab: the line under the clock and its colour, how the
                // hub opens and how its blocks show.
                L to listOf(
                    // 0.14.0 (D186): Today is one simple screen; its blocks, sides, tomorrow and collapsed state are gone.
                    "study.enabled", "study.clockLine", "study.lineAfter", "study.lineInk", "study.lineShadow", "study.clockAction",
                    "study.hubSize",
                ),
                K to listOf("study.glassTint", "study.hubDim", "study.hubBlur", "study.hubInk"),
                B to listOf(
                    "study.writeBack", "study.overdueDays", "study.noticeLead", "study.attendance", "study.leaveBy", "study.commute", "study.rest",
                    "study.windDown", "study.speedBump", "study.speedBumpTags", "study.quiet", "study.quietPeople", "study.quietEmail",
                    "study.quietTags", "study.arrival", "study.homeWifi", "study.arrivalCharger", "study.shiftLead", "study.latestEnd", "study.hubBounce",
                ),
                M to listOf(
                    "study.blockCalendars", "study.taskCalendars", "study.classCalendars", "study.feedCalendar", "study.claudeApp", "study.planLink",
                    "study.linkOpener", "study.header", "study.checkin", "study.templates",
                ),
            ),
        ),
        // ---- Calendar (0.16.0, D209–D212) ----
        SettingsSurface(
            "calendar", "Calendar", "What grows out of the clock's date: its size, views, the week, the days and the glass", SettingsEntry.CALENDAR,
            listOf("calendar"),
            mapOf(
                L to listOf("calendar.size", "calendar.view", "calendar.mondayFirst", "calendar.weekNumbers", "calendar.density"),
                K to listOf("calendar.glass", "calendar.corner", "calendar.today", "calendar.marks", "calendar.hijri", "calendar.dim"),
                B to listOf("calendar.bounce"),
            ),
        ),
        SettingsSurface(
            "calendarDays", "Holidays and school year", "Tunisia's holidays, the Islamic dates, your school year and its countdowns",
            SettingsEntry.CALENDAR, listOf("calendarDays"),
            mapOf(L to listOf("calendar.holidays", "calendar.school"), B to listOf("calendar.hijriShift")),
        ),
        SettingsSurface(
            "calendarWidget", "Calendar widget", "The same settings as the calendar, or its own", SettingsEntry.CALENDAR, listOf("calendarWidget"),
            mapOf(L to listOf("calendar.shareWidget"), K to listOf("calendar.widget.*")),
        ),
        // ---- Look ----
        SettingsSurface(
            "theme", "Colours and fonts", "Text and accent colours, glass tint, fonts and the icon shape", SettingsEntry.LOOK, listOf("theme"),
            mapOf(K to listOf("theme.*")),
            rest = K,
        ),
        SettingsSurface(
            "icons", "Icons", "Icon packs, and icons in the wallpaper's colours", SettingsEntry.LOOK, listOf("icons"),
            mapOf(K to listOf("icons.themed", "icons.themedStyle", "icons.themedColors"), M to listOf("icons.mask", "icons.themedWhere", "icons.themedUncovered", "icons.themedPacks")),
            rest = K,
        ),
        SettingsSurface(
            "glass", "Liquid glass", "The glass itself: edge light, thickness, rainbow edges, blur, per surface", SettingsEntry.LOOK, listOf("glass"),
            mapOf(
                K to listOf(
                    "glass.engine", "glass.tintMode", "glass.bevel", "glass.thickness", "glass.specular", "glass.shade", "glass.grain", "glass.rim",
                    "glass.saturation", "glass.brightness", "glass.depth", "glass.swell", "glass.dispersion", "glass.dispersionAmount", "glass.glow",
                    "glass.shadow", "glass.rimColour", "glass.lightAngle", "glass.all.*",
                ),
                M to listOf("glass.quality", "glass.auto", "glass.pill.*", "glass.drawer.*", "glass.menu.*", "glass.clock.*"),
            ),
        ),
        SettingsSurface(
            "buttons", "Buttons", "Buttons and chips: the theme's or the launcher's own", SettingsEntry.LOOK, listOf("buttons"),
            mapOf(
                L to listOf("buttons.size"),
                K to listOf("theme.controls", "buttons.style", "buttons.corner", "buttons.tint", "buttons.rim", "buttons.refraction", "buttons.main"),
                B to listOf("buttons.squish"),
            ),
        ),
        // ---- Wallpaper and layouts ----
        SettingsSurface(
            "wallpaper", "Wallpaper", "Blur and dim, parallax, transitions, changing by itself, the lock screen", SettingsEntry.WALLPAPER, listOf("wallpaper"),
            mapOf(
                L to listOf("wallpaper.parallax"),
                K to listOf("wallpaper.homeBlur", "wallpaper.homeDim"),
                B to listOf(
                    "wallpaper.transition", "wallpaper.transitionMs", "wallpaper.rotation", "wallpaper.rotationPool", "wallpaper.rotationShuffle",
                    "wallpaper.rotationMinutes", "wallpaper.rotationTimes",
                ),
                M to listOf("wallpaper.lockMode", "wallpaper.systemWrite"),
            ),
        ),
        // 0.16.0: the library's own options, and what each layout keeps of the wallpaper (D202).
        SettingsSurface(
            "wallpaperLibrary", "Library", "The order of the pictures and how many recent ones", SettingsEntry.WALLPAPER, listOf("wallpaperLibrary"),
            mapOf(L to listOf("wallpaper.librarySort"), B to listOf("wallpaper.history")),
        ),
        SettingsSurface(
            "wallpaperLayout", "Per layout", "Each layout's own wallpaper, recents, collection and look; the switch between them", SettingsEntry.WALLPAPER,
            listOf("wallpaperLayout"),
            mapOf(
                K to listOf("wallpaper.homeSaturation", "wallpaper.homeTint", "wallpaper.homeTintAmount"),
                B to listOf("layouts.ownWallpaper", "layouts.ownRecents", "layouts.ownCollection", "layouts.transition"),
            ),
        ),
        SettingsSurface(
            "wallpaperFolders", "Folders and Island", "Linked folders, Island's and Insular's pictures, and what the wallpaper strip shows of them",
            SettingsEntry.WALLPAPER, listOf("wallpaperFolders"),
            mapOf(B to listOf("wallpaper.stripFolders", "wallpaper.stripFolderCount")),
        ),
        SettingsSurface(
            "wallpaperVideo", "Video", "Video, GIF and animated wallpapers: the glass over them, the battery, re-encoding", SettingsEntry.WALLPAPER,
            listOf("wallpaperVideo"),
            mapOf(
                K to listOf("video.glass", "video.stillSeconds"),
                B to listOf("video.pauseSaver", "video.pauseBelow", "video.onlyCharging"),
                M to listOf("video.reencode", "video.maxSize", "video.maxFps"),
            ),
        ),
        SettingsSurface(
            "layouts", "Layouts", "Home setups to switch between, each with its wallpaper; export and import them", SettingsEntry.WALLPAPER, listOf("layouts"),
            mapOf(B to listOf("layouts.autoSave", "layouts.windowFrom", "layouts.windowTo", "layouts.windowDays")),
        ),
        // ---- Motion and feel ----
        SettingsSurface(
            "motion", "Motion and haptics", "Opening apps, the feel and speed of animations, haptic ticks", SettingsEntry.MOTION, listOf("motion"),
            mapOf(B to listOf("motion.*")),
            rest = B,
        ),
        SettingsSurface(
            "effects", "Press and touch", "How buttons and icons move under your finger, and what a touch throws", SettingsEntry.MOTION, listOf("effects"),
            mapOf(
                K to listOf("touch.*"),
                B to listOf("motion.press", "motion.pressDepth", "motion.pressSpeed", "motion.editWobble", "motion.pressWobble"),
            ),
        ),
        SettingsSurface(
            "moving", "Moving wallpaper", "Something moving over the home wallpaper (a theme's moving background comes first)", SettingsEntry.MOTION, listOf("effects"),
            mapOf(
                K to listOf("effects.wallpaper", "effects.wallpaperAmount", "effects.wallpaperBrightness", "effects.wallpaperSize", "effects.wallpaperColour"),
                B to listOf("effects.wallpaperSpeed", "effects.wallpaperReact"),
            ),
        ),
        SettingsSurface(
            "notices", "Glass notices", "Short glass labels: where, how long, and for what", SettingsEntry.MOTION, listOf("notices"),
            mapOf(L to listOf("notices.position"), K to listOf("notices.style", "notices.icons"), B to listOf("notices.*")),
            rest = B,
        ),
        // ---- System ----
        SettingsSurface(
            "redirect", "Redirect", "When the launcher is brought to the front, and how it appears", SettingsEntry.SYSTEM, listOf("redirect", "handover"),
            mapOf(
                K to listOf("handover.settle", "handover.settleDuration"),
                B to listOf("redirect.enabled", "redirect.onHome", "redirect.onRecentsExit", "redirect.screenOff", "redirect.reopenRecents", "handover.excludeFromRecents"),
                M to listOf(
                    "redirect.fastOnHomeAnimation", "redirect.fastDelay", "redirect.unsureDelay", "redirect.requireHomeScreen", "redirect.homeScreenFallback",
                    "redirect.reopenWindow", "redirect.reopenOnHint", "redirect.onRecentsExitDelay", "redirect.screenOffDelay", "redirect.unlockCheck",
                ),
            ),
        ),
        SettingsSurface(
            "safety", "Detection and safety", "When not to redirect, the loop guard, and how the system launcher is recognised", SettingsEntry.SYSTEM,
            listOf("exclusions", "detection"),
            mapOf(B to listOf("exclude.*", "redirect.systemHomePause", "guard.*"), M to listOf("detect.*")),
        ),
        SettingsSurface(
            "backup", "Backup and Claude", "Back up now or every day to a folder, restore with a preview of what changes, tune with Claude",
            SettingsEntry.SYSTEM, listOf("backup"),
            mapOf(B to listOf("backup.*")),
            rest = B,
        ),
        SettingsSurface(
            "log", "Debug log", "What is recorded, in how much detail", SettingsEntry.SYSTEM, listOf("log"),
            mapOf(M to listOf("log.*")),
        ),
        SettingsSurface(
            "state", "The launcher's own state", "Counters, facts about this phone and set-up steps", SettingsEntry.SYSTEM, listOf("state", "updates"),
            emptyMap(), internal = true,
        ),
    )

    /**
     * 0.10.2: what a page edits with an editor of its own rather than option rows (the search page's cards, the pill's
     * items…), so settings search finds them too: its name, what it does, words to find it by, and its page and tab.
     */
    data class Editor(val title: String, val summary: String, val keywords: String, val surfaceId: String, val tab: SettingsTab)

    val editors: List<Editor> = listOf(
        Editor("Search page cards", "Which cards the search page shows and in what order: the Today line (calendar and exams), the study card, Ask Claude, your apps, and every other card",
            "search page cards order today line calendar month exam countdown timeline widgets claude prompts commands next block apps calm", "search", SettingsTab.LAYOUT),
        Editor("Pill items", "What the pill holds and in what order", "pill items slots icons order search apps claude study live focus music", "pill", SettingsTab.LAYOUT),
        Editor(
            "Camera for board photos", "The launcher's camera (several photos in a row) or a camera app for board photos, and a test photo",
            "camera photo board picture app essai class test several series multiple burst launcher", "study", SettingsTab.MORE,
        ),
        Editor("Pill study item", "How the pill's study item looks: icon, circle, picture, block name", "pill study item circle pebble ring look", "pill", SettingsTab.LOOK),
        Editor("Home menu items", "The glass pills of the home menu (long press on empty space)", "home menu items pills long press order", "menus", SettingsTab.LAYOUT),
        Editor("Long-press menu of apps", "The entries of an app's long-press menu", "app long press menu entries order", "menus", SettingsTab.LAYOUT),
        Editor("Drawer tabs and tags", "Your drawer tabs, tags and their order", "drawer tabs tags order pinned", "tags", SettingsTab.LAYOUT),
        Editor(
            "Letter areas", "Circles over the apps where a swipe starts the letters: add as many as you want, each with its edge, place and size",
            "letter areas circle zone add position place size left right edge swipe letters", "index", SettingsTab.LAYOUT,
        ),
        Editor(
            "Island and Insular", "Pictures from Island's or Insular's work profile: what the launcher sees, and how to reach them (File Shuttle, or a folder synced with Syncthing)",
            "island insular work profile file shuttle syncthing folder pictures paused", "wallpaperFolders", SettingsTab.MORE,
        ),
        Editor(
            "Calendars and colours", "Which of the phone's calendars the calendar shows, and the colour of each",
            "calendar calendars accounts hide show colour color events", "calendar", SettingsTab.MORE,
        ),
        Editor(
            "School year", "Your school's dates (ESSAI 2026–27 built in, indicative): edit, move, delete, add, pin a countdown under the clock",
            "school year essai dates exams ds revision period retakes deliberation countdown pin clock edit delete add indicative", "calendarDays", SettingsTab.MORE,
        ),
        Editor(
            "Holidays set by hand", "Islamic holidays moved to the announced date, and holidays hidden; put them back",
            "holidays moved hidden aid eid mouled islamic reset tunisia", "calendarDays", SettingsTab.MORE,
        ),
        Editor("Theme lab", "Every option of your WM Keyboard theme on every button, chip, pill, sheet and menu, with presets (Before 0.10.2)",
            "theme lab keyboard wm colours colors pills search bar tint material accent blueberry preset before", "buttons", SettingsTab.LOOK),
    )

    /** The [editors] every word of [query] matches (in its name, what it does or its words). */
    fun searchEditors(query: String): List<Editor> {
        val words = query.lowercase().split(' ', ',', '-').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return editors.filter { e ->
            val text = (e.title + " " + e.summary + " " + e.keywords).lowercase()
            words.all { it in text }
        }
    }

    val visibleSurfaces: List<SettingsSurface> get() = surfaces.filter { !it.internal }

    fun surface(id: String): SettingsSurface? = surfaces.firstOrNull { it.id == id }

    fun surfacesOf(entry: SettingsEntry): List<SettingsSurface> = surfaces.filter { it.entry == entry && !it.internal }

    /** The page that took a 0.9 section's options, and the tab to open (null: the first). */
    fun forSection(section: String): Pair<SettingsSurface, SettingsTab?>? = when (section) {
        "drawerMotion" -> surface("drawer")?.let { it to SettingsTab.BEHAVIOUR }
        "handover" -> surface("redirect")?.let { it to SettingsTab.LOOK }
        "detection" -> surface("safety")?.let { it to SettingsTab.MORE }
        else -> surfaces.firstOrNull { section in it.sections && !it.internal }?.let { it to null }
            ?: surface(section)?.let { it to null }
    }

    private val placed: Map<String, Pair<SettingsSurface, SettingsTab>> by lazy {
        val out = HashMap<String, Pair<SettingsSurface, SettingsTab>>()
        for (setting in LauncherSettings.all) {
            // A page that names the key wins; else the page holding its section, in its rest tab.
            val named = surfaces.firstNotNullOfOrNull { s -> s.names(setting.key)?.takeIf { setting.section in s.sections }?.let { s to it } }
            val place = named
                ?: surfaces.firstOrNull { setting.section in it.sections }?.let { it to it.rest }
                ?: surfaces.last().let { it to it.rest }
            out[setting.key] = if (setting.hidden) surfaces.last() to SettingsTab.MORE else place
        }
        out
    }

    fun surfaceOf(setting: Setting<*>): SettingsSurface = placed.getValue(setting.key).first

    fun tabOf(setting: Setting<*>): SettingsTab = placed.getValue(setting.key).second

    /** The options of a page's tab, in the registry's order. */
    fun settingsOf(surface: SettingsSurface, tab: SettingsTab): List<Setting<*>> =
        LauncherSettings.all.filter { !it.hidden && placed[it.key]?.first === surface && placed[it.key]?.second == tab }

    fun settingsOf(surface: SettingsSurface): List<Setting<*>> = LauncherSettings.all.filter { !it.hidden && placed[it.key]?.first === surface }

    fun info(setting: Setting<*>): SettingInfo {
        val (surface, tab) = placed.getValue(setting.key)
        return SettingInfo(
            id = setting.key,
            title = setting.title,
            summary = setting.summary,
            type = when (setting) {
                is BoolSetting -> "switch"
                is IntSetting -> "number"
                is ChoiceSetting -> "choice"
                is TextSetting -> "text"
                is ActionSetting -> "action"
                is ColorSetting -> "colour"
                is StringListSetting -> "list"
            },
            default = when (setting) {
                is StringListSetting -> setting.default.joinToString(", ")
                is ChoiceSetting -> setting.default
                else -> setting.default.toString()
            },
            values = when (setting) {
                is IntSetting -> "${setting.min}..${setting.max}" + (if (setting.step != 1) " by ${setting.step}" else "") + if (setting.unit.isNotEmpty()) " ${setting.unit}" else ""
                is ChoiceSetting -> setting.options.joinToString(", ") { "${it.first} (${it.second})" }
                is BoolSetting -> "true, false"
                is ColorSetting -> "#RRGGBB, or empty for ${setting.autoLabel.lowercase()}"
                else -> ""
            },
            scope = when {
                setting.key in LauncherSettings.layoutKeys -> "layout"
                setting.section in setOf("detection", "state") -> "phone"
                else -> "launcher"
            },
            dependsOn = SettingDependencies.parents[setting.key]?.key
                ?: SettingDependencies.offParents[setting.key]?.let { "!" + it.key }
                ?: SettingDependencies.choiceParents[setting.key]?.let { (choice, values) -> "${choice.key}=${values.joinToString("|")}" },
            level = if (LauncherSettings.isAdvanced(setting)) "advanced" else "basic",
            keywords = setting.keywords,
            entry = surface.entry,
            surface = surface,
            tab = tab,
        )
    }

    /** "Home › Pill › Look": where an option is, for search results and Explain. */
    fun path(setting: Setting<*>): String {
        val (surface, tab) = placed.getValue(setting.key)
        return "${surface.entry.title} › ${surface.title} › ${tab.title}"
    }
}
