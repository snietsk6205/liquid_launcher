package dev.liquid.launcher.core.data

/**
 * 0.15.0 (D189): what WM Keyboard said about the launcher's two wallpaper themes ("Liquid wallpaper 1" and "2"), so
 * Settings can show which one is missing instead of a step that cannot be checked. WM Keyboard reads a theme's picture
 * once each time it loads that theme (its keyboard does not redraw while hidden), so a new picture reaches the keys at
 * once only by switching to the other theme: both are needed for that; with one, the picture waits for the next load.
 */
object KeyboardWallpaperThemes {
    /** The slots (0, 1) WM Keyboard has, from the remembered text ("1", "2", "1,2"; empty: not known). */
    fun parse(text: String): Set<Int> =
        text.split(',').mapNotNull { it.trim().toIntOrNull()?.minus(1) }.filter { it == 0 || it == 1 }.toSet()

    fun encode(slots: Set<Int>): String = slots.filter { it == 0 || it == 1 }.sorted().joinToString(",") { (it + 1).toString() }

    /** WM Keyboard's answer to SET_THEME says it has no theme of that name (or two of it: names must be unique). */
    fun missing(answer: String?): Boolean = answer?.contains("no theme is called", ignoreCase = true) == true

    /** WM Keyboard took the theme, but its automatic light and dark pair shows another one. */
    fun automaticWins(answer: String?): Boolean = answer?.contains("automatic theme is on", ignoreCase = true) == true
}
