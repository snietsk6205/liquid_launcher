package liquid.kit

/*
 * The app's options, declared once (Liquid Launcher's D10): the settings screens, settings search, per-option reset,
 * the "changed" dots, export, import and backups are all generated from these declarations. Replace the examples;
 * keep RecentlyChanged (SettingsStore keeps the last options changed in it, for the first settings screen).
 *
 * Rules that kept 196 decisions' worth of options sane:
 * - A key never changes once shipped (stored values and backups use it); rename the title instead.
 * - Only values that differ from the default are stored, so a better default reaches everyone who never touched it.
 * - Every option has a section, a one-line summary in plain words, and search keywords (the words a person would
 *   type, synonyms included).
 * - Internal state lives here too, as hidden settings in the "state" section (stored, backed up, never listed).
 */
object AppSettings {
    val RecentlyChanged = StringListSetting(
        key = "state.recentlyChanged", default = emptyList(),
        title = "Recently changed options", section = "state", hidden = true,
    )

    val Feel = ChoiceSetting(
        key = "motion.feel", default = "liquid",
        options = listOf("calm" to "Calm (no overshoot)", "liquid" to "Liquid (a soft overshoot)", "lively" to "Lively (a clear bounce)"),
        title = "Feel",
        summary = "How sheets, menus and popups move: one spring for all of them",
        section = "motion", keywords = "animation spring bounce overshoot motion feel",
    )

    val Speed = IntSetting(
        key = "motion.speed", default = 100, min = 50, max = 200, step = 10, unit = "%",
        title = "Animation speed",
        summary = "Every animation follows it: lengths divide by it, springs stiffen with its square",
        section = "motion", keywords = "animation speed fast slow motion duration",
    )

    val Haptics = BoolSetting(
        key = "motion.haptics", default = true,
        title = "Haptics",
        summary = "A light tick when something snaps into place or a gesture is recognised",
        section = "motion", keywords = "vibration vibrate haptic feedback tick",
    )

    val all: List<Setting<*>> = listOf(RecentlyChanged, Feel, Speed, Haptics)
}
