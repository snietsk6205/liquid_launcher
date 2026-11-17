package dev.liquid.launcher.core.data

/**
 * The cards of the search page with nothing typed (0.9.3), top to bottom; the last one sits just above the
 * field, under the thumb. Stored as the ids of the cards shown, in order ([LauncherSettings.SearchCards]).
 */
object SearchCards {
    data class Card(val id: String, val name: String, val about: String)

    val all = listOf(
        Card("widgets", "Your widgets", "The search page's own widgets (calendar, music, notes…), with Add and Edit"),
        Card("timeline", "Today's timeline", "Classes and study blocks of the day on one line, with the time now"),
        Card("exam", "Exam countdown", "The next exam from your calendar; grey, amber from 14 days, red from 3"),
        Card("absences", "Absence meter", "One tube per module from launcher-data, against the absence limit; always UNVERIFIED until the rule is confirmed"),
        Card("grades", "Grade average", "Your weighted average, once every mark has its coefficient"),
        Card("flashcard", "Daily flashcard", "Face down; tap to turn it over, then right or wrong"),
        Card("next", "Next block", "The block now or next, its steps and one action"),
        Card("commands", "Study commands", "!next, !exam, !plan, !abs and ? for Claude, one tap each"),
        Card("claude", "Ask Claude", "Ask Claude and your prompt templates, about the block now"),
        Card("apps", "Frequent apps", "The apps you open most, just above the field"),
    )

    val defaultOrder: List<String> = listOf("widgets", "timeline", "exam", "absences", "grades", "flashcard", "next", "commands", "claude", "apps")

    /** Known ids of [stored], in order, without repeats. */
    fun shown(stored: List<String>): List<String> = stored.filter { id -> all.any { it.id == id } }.distinct()
}
