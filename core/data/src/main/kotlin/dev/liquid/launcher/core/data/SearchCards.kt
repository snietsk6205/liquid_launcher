package dev.liquid.launcher.core.data

/**
 * The cards of the search page with nothing typed (0.9.3), top to bottom; the last one sits just above the
 * field, under the thumb. Stored as the ids of the cards shown, in order ([LauncherSettings.SearchCards]).
 */
object SearchCards {
    data class Card(val id: String, val name: String, val about: String)

    val all = listOf(
        Card("glance", "Today line", "One line: \"Today\" and the next exam (24 days to …); a tap opens your widgets (the calendar month) and the exam countdown above it"),
        Card("widgets", "Your widgets", "The search page's own widgets (calendar, music, notes…), with Add and Edit"),
        Card("timeline", "Today's timeline", "Classes and study blocks of the day on one line, with the time now"),
        Card("exam", "Exam countdown", "The next exam from your calendar; grey, amber from 14 days, red from 3"),
        Card("annales", "Annales", "In the week before an exam: your past papers, one folder per module (choose the folder in Study companion)"),
        Card("absences", "Absence meter", "One tube per module from launcher-data, against the absence limit; always UNVERIFIED until the rule is confirmed"),
        Card("grades", "Grade average", "Your weighted average, once every mark has its coefficient"),
        Card("flashcard", "Daily flashcard", "Face down; tap to turn it over, then right or wrong"),
        Card("review", "Sunday review", "On Sundays: the week's blocks, focus, absences and the next exam; a tap reviews it with Claude"),
        Card("next", "Next block", "The block now or next with today's progress, its next step and one action"),
        Card("commands", "Study commands", "!next, !exam, !plan, !abs and ? for Claude, one tap each (they also appear as you type !)"),
        Card("claude", "Ask Claude", "One Ask Claude chip; your prompt templates are under it (and appear as you type ?)"),
        Card("apps", "Frequent apps", "The apps you open most, just above the field"),
    )

    /**
     * 0.10.2: a calm page by default: the "Today" line (the calendar month and the exam countdown one tap away), the
     * study card, one Ask Claude chip and your apps above the field. Every other card can be added back.
     */
    val defaultOrder: List<String> = listOf("glance", "next", "claude", "apps")

    /** The order of 0.9.3 to 0.10.1. */
    val oldOrder: List<String> = listOf("widgets", "timeline", "exam", "annales", "absences", "grades", "flashcard", "review", "next", "commands", "claude", "apps")

    /** Known ids of [stored], in order, without repeats. */
    fun shown(stored: List<String>): List<String> = stored.filter { id -> all.any { it.id == id } }.distinct()
}
