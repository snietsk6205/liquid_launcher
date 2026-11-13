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
    data object Wallpaper : LauncherAction
    data object AddPage : LauncherAction
    data object Pages : LauncherAction
    data object Layouts : LauncherAction
    data object SaveLayout : LauncherAction
    data object NextLayout : LauncherAction
    data class SwitchLayout(val id: String) : LauncherAction
    data class Section(val id: String) : LauncherAction
    data class LaunchApp(val ref: AppRef) : LauncherAction

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
        AddPage -> "add_page"
        Pages -> "pages"
        Layouts -> "layouts"
        SaveLayout -> "save_layout"
        NextLayout -> "next_layout"
        is SwitchLayout -> "layout:$id"
        is Section -> "section:$id"
        is LaunchApp -> "app:${ref.key}"
    }

    companion object {
        /** Built-in actions with the name shown in pickers, in picker order. */
        val builtIns: List<Pair<LauncherAction, String>> = listOf(
            None to "Nothing",
            OpenDrawer to "Open the app drawer",
            OpenSearch to "Search",
            Notifications to "Notifications",
            QuickSettings to "Quick settings",
            LockScreen to "Lock the screen",
            Recents to "Recent apps",
            OpenCalendar to "Open the calendar",
            EditHome to "Edit the home screen",
            HomeMenu to "Home menu (glass pills)",
            Wallpaper to "Wallpaper",
            AddPage to "Add a page",
            Pages to "Pages overview",
            Layouts to "Saved layouts",
            SaveLayout to "Save the current layout",
            NextLayout to "Next saved layout",
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
            if (text.startsWith("layout:")) return text.removePrefix("layout:").takeIf { it.isNotBlank() }?.let { SwitchLayout(it) } ?: None
            if (text.startsWith("section:")) return text.removePrefix("section:").takeIf { it.isNotBlank() }?.let { Section(it) } ?: None
            return builtIns.firstOrNull { it.first.encode() == text }?.first ?: None
        }

        fun label(action: LauncherAction, layoutName: (String) -> String? = { null }, appLabel: (AppRef) -> String?): String = when (action) {
            is LaunchApp -> appLabel(action.ref) ?: action.ref.pkg
            is SwitchLayout -> "Layout: " + (layoutName(action.id) ?: "deleted")
            is Section -> sections.firstOrNull { it.first == action.id }?.second ?: "Settings"
            else -> builtIns.firstOrNull { it.first == action }?.second ?: "Nothing"
        }
    }
}
