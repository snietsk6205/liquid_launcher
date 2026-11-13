package dev.liquid.launcher.core.settings

/**
 * One option, declared once. The settings screens, settings search, per-option reset and
 * (later) presets, profiles and export are all generated from these declarations.
 */
sealed class Setting<T>(
    val key: String,
    val default: T,
    val title: String,
    val summary: String,
    val section: String,
    val keywords: String,
    /** Internal state that is stored like a setting but never shown. */
    val hidden: Boolean,
) {
    /** Text used by settings search. */
    val searchText: String get() = "$title $summary $keywords".lowercase()
}

class BoolSetting(
    key: String,
    default: Boolean,
    title: String,
    summary: String = "",
    section: String,
    keywords: String = "",
    hidden: Boolean = false,
) : Setting<Boolean>(key, default, title, summary, section, keywords, hidden)

class IntSetting(
    key: String,
    default: Int,
    val min: Int,
    val max: Int,
    val step: Int = 1,
    val unit: String = "",
    title: String,
    summary: String = "",
    section: String,
    keywords: String = "",
    hidden: Boolean = false,
) : Setting<Int>(key, default, title, summary, section, keywords, hidden)

class ChoiceSetting(
    key: String,
    default: String,
    /** value to label */
    val options: List<Pair<String, String>>,
    title: String,
    summary: String = "",
    section: String,
    keywords: String = "",
    hidden: Boolean = false,
) : Setting<String>(key, default, title, summary, section, keywords, hidden) {
    fun labelOf(value: String): String = options.firstOrNull { it.first == value }?.second ?: value
}

class TextSetting(
    key: String,
    default: String,
    val placeholder: String = "",
    title: String,
    summary: String = "",
    section: String,
    keywords: String = "",
    hidden: Boolean = false,
) : Setting<String>(key, default, title, summary, section, keywords, hidden)

/** A launcher action (see LauncherAction), stored as its short text form. */
class ActionSetting(
    key: String,
    default: String,
    title: String,
    summary: String = "",
    section: String,
    keywords: String = "",
    hidden: Boolean = false,
) : Setting<String>(key, default, title, summary, section, keywords, hidden)

class StringListSetting(
    key: String,
    default: List<String>,
    title: String,
    summary: String = "",
    section: String,
    keywords: String = "",
    hidden: Boolean = false,
) : Setting<List<String>>(key, default, title, summary, section, keywords, hidden)

data class SettingsSection(
    val id: String,
    val title: String,
    val summary: String,
    /** Heading the section is listed under on the first settings screen. */
    val group: String = "Launcher",
)
