package dev.liquid.launcher.core.settings

/**
 * One letter area (0.11.1): a circle over the drawer's apps where a swipe starts the letters. [right] is the screen
 * edge the letters come out of; [across] and [down] place its middle, in % of the drawer's width and of the app list's
 * height (0 at its top, 100 just above the search field); [sizeDp] is its diameter.
 */
data class LetterArea(val right: Boolean, val across: Int, val down: Int, val sizeDp: Int) {
    companion object {
        /** Where the Calculator sat in Med's drawer: right column, a little above the middle. */
        val Default = LetterArea(right = true, across = 84, down = 45, sizeDp = 120)
        const val MIN_SIZE = 48
        const val MAX_SIZE = 240
    }
}

/** How [LauncherSettings.IndexAreas] stores the areas: one "right,84,45,120" per entry; bad entries are skipped. */
object LetterAreas {
    fun format(area: LetterArea): String =
        listOf(if (area.right) "right" else "left", area.across, area.down, area.sizeDp).joinToString(",")

    fun parse(entry: String): LetterArea? {
        val parts = entry.split(',').map { it.trim() }
        if (parts.size != 4) return null
        val right = when (parts[0]) {
            "right" -> true
            "left" -> false
            else -> return null
        }
        val across = parts[1].toIntOrNull() ?: return null
        val down = parts[2].toIntOrNull() ?: return null
        val size = parts[3].toIntOrNull() ?: return null
        return LetterArea(right, across.coerceIn(0, 100), down.coerceIn(0, 100), size.coerceIn(LetterArea.MIN_SIZE, LetterArea.MAX_SIZE))
    }

    fun parseAll(entries: List<String>): List<LetterArea> = entries.mapNotNull { parse(it) }

    fun formatAll(areas: List<LetterArea>): List<String> = areas.map { format(it) }

    /** A new area for "Add an area": on the other side from the last one, lower down, so it never lands on top of it. */
    fun next(existing: List<LetterArea>): LetterArea {
        val last = existing.lastOrNull() ?: return LetterArea.Default
        val right = !last.right
        return LetterArea(right, if (right) 84 else 16, (last.down + 20).let { if (it > 90) 25 else it }, last.sizeDp)
    }
}
