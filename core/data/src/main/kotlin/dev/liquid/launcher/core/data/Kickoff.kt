package dev.liquid.launcher.core.data

/**
 * 0.14.0 (D186, Med: "I click a button, it directs me to Claude, adds the context of what I studied today and asks me
 * what I would like to study, some questions to see my progress, and gets some context out of me like today's lesson,
 * then we start studying"): what Today's "Study with Claude" sends. The check-in message (Settings › Study companion,
 * which points Claude to his plan doc) comes first, then the day in a few plain lines Claude reads before its first
 * question: the date, today's classes, what Med picked, the minutes studied, his notes, what is recommended and the
 * next exam. Absence notes are left out (Med: Claude never asks about absences; the launcher counts them).
 */
object Kickoff {
    private const val DAY = 86_400_000L

    fun context(
        message: String,
        day: StudyDay,
        subject: String,
        studiedMinutes: Int,
        recommended: List<String>,
        now: Long,
        dateText: String,
        time: (Long) -> String,
    ): String = buildString {
        append(message.trim()).append("\n\n")
        append("Today: ").append(dateText).append(", ").append(time(now)).append(".\n")
        val classes = day.classes.filter { !it.allDay }.sortedBy { it.begin }
        if (classes.isEmpty()) {
            append("No class today.\n")
        } else {
            append("In class today: ")
            append(classes.joinToString("; ") { c -> c.title.text.ifBlank { c.rawTitle }.trim() + " " + time(c.begin) + "–" + time(c.end) })
            append(".\n")
        }
        val picked = subject.trim()
        if (picked.isNotEmpty()) append("I want to study: ").append(picked).append(".\n") else append("I have not chosen what to study yet: ask me.\n")
        if (studiedMinutes > 0) append("Studied today so far: ").append(studiedMinutes).append(" min.\n")
        val notes = day.notes.map { it.rawTitle.trim().removePrefix(StudyFormat.NOTE_PREFIX).trim() }
            .filter { it.isNotEmpty() && !it.lowercase().startsWith("absent") }
        if (notes.isNotEmpty()) append("My notes today: ").append(notes.joinToString("; ")).append(".\n")
        if (recommended.isNotEmpty()) append("Recommended for me: ").append(recommended.joinToString("; ")).append(".\n")
        day.exams.firstOrNull { it.begin > now }?.let { e ->
            val days = (e.begin - now) / DAY
            val left = when (days) {
                0L -> "today"
                1L -> "in 1 day"
                else -> "in $days days"
            }
            append("Next exam: ").append(e.title.text.ifBlank { e.rawTitle }.trim()).append(' ').append(left)
            if (e.key in day.unconfirmedExams) append(" (date not confirmed)")
            append(".\n")
        }
    }.trimEnd()
}
