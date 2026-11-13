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
            OpenSettings to "Launcher settings",
            SystemLauncher to "Open the system launcher",
        )

        fun decode(text: String): LauncherAction {
            if (text.startsWith("app:")) {
                return AppRef.parse(text.removePrefix("app:"))?.let { LaunchApp(it) } ?: None
            }
            return builtIns.firstOrNull { it.first.encode() == text }?.first ?: None
        }

        fun label(action: LauncherAction, appLabel: (AppRef) -> String?): String = when (action) {
            is LaunchApp -> appLabel(action.ref) ?: action.ref.pkg
            else -> builtIns.firstOrNull { it.first == action }?.second ?: "Nothing"
        }
    }
}
