package dev.liquid.launcher.core.data

/**
 * Something a gesture, a pill slot or a menu entry can do. Stored in settings as a short
 * text ([encode]) so every "action" option uses the same picker.
 */
sealed interface LauncherAction {
    data object None : LauncherAction
    data object OpenDrawer : LauncherAction
    data object OpenSearch : LauncherAction
    data object OpenSettings : LauncherAction
    data object EditHome : LauncherAction
    data object Notifications : LauncherAction
    data object QuickSettings : LauncherAction
    data object LockScreen : LauncherAction
    data object Recents : LauncherAction
    data object SystemLauncher : LauncherAction
    data object OpenCalendar : LauncherAction
    data object HomeMenu : LauncherAction
    /** The wallpaper strip: recent and favourite wallpapers for a one-tap switch, and the way to the wallpaper screen. */
    data object Wallpaper : LauncherAction
    data object WallpaperStudio : LauncherAction
    data object ShuffleWallpaper : LauncherAction
    /** Opens the drawer with the list of tags to filter by. */
    data object TagFilter : LauncherAction
    data object AddPage : LauncherAction
    data object Pages : LauncherAction
    data object Layouts : LauncherAction
    data object SaveLayout : LauncherAction
    data object NextLayout : LauncherAction
    /** The study companion's Today panel. */
    data object Today : LauncherAction

    /** Shares today's study log to the Claude app, ready for a note. */
    data object TellClaude : LauncherAction

    /** Opens search ready to ask Claude: what is typed goes to Claude (0.7.2). */
    data object AskClaude : LauncherAction

    /** Opens the study plan link (or the calendar). */
    data object StudyPlan : LauncherAction

    /** Starts or stops the focus timer on the block running now. */
    data object FocusTimer : LauncherAction

    /** The study hub with the note field ready: a note for tonight's check-in (0.9.1, D5). */
    data object StudyNote : LauncherAction

    /** E1 (0.9.7): a photo of the board, saved under the class it was taken in. */
    data object BoardPhoto : LauncherAction

    /** The list of built-in widgets to add to the current page. */
    data object Widgets : LauncherAction
    data class SwitchLayout(val id: String) : LauncherAction
    data class Section(val id: String) : LauncherAction
    data class LaunchApp(val ref: AppRef) : LauncherAction

    /** One screen of an app, opened directly ([label] is kept for showing it). */
    data class LaunchActivity(val pkg: String, val cls: String, val label: String) : LauncherAction

    fun encode(): String = when (this) {
        None -> "none"
        OpenDrawer -> "drawer"
        OpenSearch -> "search"
        OpenSettings -> "settings"
        EditHome -> "edit"
        Notifications -> "notifications"
        QuickSettings -> "quick_settings"
        LockScreen -> "lock"
        Recents -> "recents"
        SystemLauncher -> "system_launcher"
        OpenCalendar -> "calendar"
        HomeMenu -> "home_menu"
        Wallpaper -> "wallpaper"
        WallpaperStudio -> "wallpaper_studio"
        ShuffleWallpaper -> "shuffle_wallpaper"
        TagFilter -> "tag_filter"
        AddPage -> "add_page"
        Pages -> "pages"
        Layouts -> "layouts"
        SaveLayout -> "save_layout"
        NextLayout -> "next_layout"
        Today -> "today"
        TellClaude -> "tell_claude"
        AskClaude -> "ask_claude"
        StudyPlan -> "study_plan"
        FocusTimer -> "focus"
        StudyNote -> "study_note"
        BoardPhoto -> "board_photo"
        Widgets -> "widgets"
        is SwitchLayout -> "layout:$id"
        is Section -> "section:$id"
        is LaunchApp -> "app:${ref.key}"
        is LaunchActivity -> "activity:$pkg/$cls|$label"
    }

    companion object {
        /** Built-in actions with the name shown in pickers, in picker order. */
        val builtIns: List<Pair<LauncherAction, String>> = listOf(
            None to "Nothing",
            OpenDrawer to "Open the app drawer",
            OpenSearch to "Search",
            AskClaude to "Ask Claude",
            Notifications to "Notifications",
            QuickSettings to "Quick settings",
            LockScreen to "Lock the screen",
            Recents to "Recent apps",
            OpenCalendar to "Open the calendar",
            EditHome to "Edit the home screen",
            HomeMenu to "Home menu (glass pills)",
            Wallpaper to "Wallpapers (quick switch)",
            WallpaperStudio to "Wallpaper screen (library, placing, blur)",
            ShuffleWallpaper to "Shuffle wallpaper",
            TagFilter to "Filter apps by tag",
            AddPage to "Add a page",
            Pages to "Pages overview",
            Layouts to "Saved layouts",
            SaveLayout to "Save the current layout",
            NextLayout to "Next saved layout",
            Today to "Today: study blocks and tasks",
            TellClaude to "Tell Claude what I studied",
            StudyPlan to "Open my study plan",
            FocusTimer to "Focus timer for the current block",
            StudyNote to "Write a note for tonight's check-in",
            BoardPhoto to "Photo of the board (saved under the class)",
            Widgets to "Add a widget",
            OpenSettings to "Launcher settings",
            SystemLauncher to "Open the system launcher",
        )

        /** Settings sections that make sense as one-tap actions (home menu, gestures). */
        val sections: List<Pair<String, String>> = listOf(
            "icons" to "Icon settings",
            "drawer" to "Drawer settings",
            "tags" to "Drawer tabs and tags",
            "pill" to "Pill settings",
            "gestures" to "Gesture settings",
            "glass" to "Glass settings",
            "wallpaper" to "Wallpaper settings",
        )

        fun decode(text: String): LauncherAction {
            if (text.startsWith("app:")) {
                return AppRef.parse(text.removePrefix("app:"))?.let { LaunchApp(it) } ?: None
            }
            if (text.startsWith("activity:")) {
                val body = text.removePrefix("activity:")
                val component = body.substringBefore('|')
                val pkg = component.substringBefore('/')
                val cls = component.substringAfter('/', "")
                if (pkg.isBlank() || cls.isBlank()) return None
                return LaunchActivity(pkg, cls, body.substringAfter('|', cls.substringAfterLast('.')))
            }
            if (text.startsWith("layout:")) return text.removePrefix("layout:").takeIf { it.isNotBlank() }?.let { SwitchLayout(it) } ?: None
            if (text.startsWith("section:")) return text.removePrefix("section:").takeIf { it.isNotBlank() }?.let { Section(it) } ?: None
            return builtIns.firstOrNull { it.first.encode() == text }?.first ?: None
        }

        fun label(action: LauncherAction, layoutName: (String) -> String? = { null }, appLabel: (AppRef) -> String?): String = when (action) {
            is LaunchApp -> appLabel(action.ref) ?: action.ref.pkg
            is LaunchActivity -> "Open: ${action.label}"
            is SwitchLayout -> "Layout: " + (layoutName(action.id) ?: "deleted")
            is Section -> sections.firstOrNull { it.first == action.id }?.second ?: "Settings"
            else -> builtIns.firstOrNull { it.first == action }?.second ?: "Nothing"
        }
    }
}
