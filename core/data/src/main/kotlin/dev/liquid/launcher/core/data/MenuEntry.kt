package dev.liquid.launcher.core.data

/** Entries the long-press menu can show, in their default order. Stored by [id]. */
enum class MenuEntry(val id: String, val title: String) {
    Info("info", "App info"),
    Home("home", "Add to / remove from home"),
    Rename("rename", "Rename"),
    Icon("icon", "Change icon"),
    Swipe("swipe", "Swipe actions (home only)"),
    Tags("tags", "Tags"),
    Hide("hide", "Hide from / show in the drawer"),
    Uninstall("uninstall", "Uninstall");

    companion object {
        fun fromIds(ids: List<String>): List<MenuEntry> = ids.mapNotNull { id -> entries.firstOrNull { it.id == id } }
    }
}
