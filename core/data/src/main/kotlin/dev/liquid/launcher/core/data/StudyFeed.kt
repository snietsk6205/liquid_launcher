package dev.liquid.launcher.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * "launcher-data" (spec part 1, build step 8; 0.9.5): what the evening check-in tells the launcher beyond the
 * calendars. One all-day event of today, titled "launcher-data", in a calendar named "Launcher", with this JSON
 * as its description. Missing or broken: the launcher falls back to the calendars and to a local copy the user
 * can edit, and hides what neither has. Everything is optional; unknown fields are ignored.
 */

@Serializable
data class StudyFeed(
    val v: Int = 1,
    val updated: String? = null,
    val exams: List<FeedExam> = emptyList(),
    val absences: FeedAbsences? = null,
    val flashcard: FeedCard? = null,
    /** 0.12.0 (D173): a small deck; the single [flashcard] still reads as a deck of one. */
    val flashcards: List<FeedCard> = emptyList(),
    val grades: List<FeedGrade> = emptyList(),
    /**
     * 0.14.0 (D185): up to three short tasks Claude recommends for the next days (written at the end of each study chat),
     * shown in Today under "Recommended". Older launcher-data without it reads as before.
     */
    val recommended: List<String> = emptyList(),
) {
    /** True when there is nothing in it the launcher shows. */
    val isEmpty: Boolean get() = exams.isEmpty() && (absences?.modules.isNullOrEmpty()) && cards.isEmpty() && grades.isEmpty() && tasks.isEmpty()

    /** [recommended] without blanks or repeats, at most three. */
    val tasks: List<String> get() = recommended.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3)

    /** Every card in it, the deck's and the single one, with a question. */
    val cards: List<FeedCard> get() = (flashcards + listOfNotNull(flashcard)).filter { it.q.isNotBlank() }
}

/** An exam; [date] is "yyyy-MM-dd". Unconfirmed dates are shown with "≈" (B1). */
@Serializable
data class FeedExam(val label: String = "", val date: String = "", val confirmed: Boolean = false)

/** The absence rule (B2): at most [limit] of a module's sessions. While [ruleConfirmed] is false the meter says UNVERIFIED. */
@Serializable
data class FeedAbsences(
    @SerialName("rule_confirmed") val ruleConfirmed: Boolean = false,
    val limit: Double = 0.20,
    val modules: List<FeedModule> = emptyList(),
)

/** One module's absences; [total] is its sessions this semester, null while unknown. */
@Serializable
data class FeedModule(val name: String = "", val absent: Int = 0, val total: Int? = null)

/** A flashcard (E4); [id] names it across days (0.12.0: when missing, its module and question do). */
@Serializable
data class FeedCard(val id: String = "", val module: String = "", val q: String = "", val a: String = "")

/** A grade (E2); [mark] out of 20 and [coef] null while unknown. */
@Serializable
data class FeedGrade(val module: String = "", val type: String = "", val mark: Double? = null, val coef: Double? = null)

object StudyFeedParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * The feed in [text] (a calendar description: plain text or Google's HTML), or null when it holds no valid
     * JSON object. Text around the object (a note above it) is ignored.
     */
    fun parse(text: String?): StudyFeed? {
        if (text.isNullOrBlank()) return null
        val plain = if (StudyFormat.isHtml(text)) StudyFormat.lines(text).joinToString("\n") else text
        val start = plain.indexOf('{')
        val end = plain.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.decodeFromString(StudyFeed.serializer(), plain.substring(start, end + 1)) }.getOrNull()
    }

    /** A short summary of [feed] for the settings screen. */
    fun describe(feed: StudyFeed): String {
        val parts = ArrayList<String>()
        if (feed.exams.isNotEmpty()) parts += if (feed.exams.size == 1) "1 exam" else "${feed.exams.size} exams"
        feed.absences?.modules?.takeIf { it.isNotEmpty() }?.let { parts += if (it.size == 1) "absences of 1 module" else "absences of ${it.size} modules" }
        feed.cards.size.takeIf { it > 0 }?.let { parts += if (it == 1) "a flashcard" else "$it flashcards" }
        if (feed.grades.isNotEmpty()) parts += if (feed.grades.size == 1) "1 grade" else "${feed.grades.size} grades"
        feed.tasks.size.takeIf { it > 0 }?.let { parts += if (it == 1) "1 recommended task" else "$it recommended tasks" }
        return if (parts.isEmpty()) "valid, but empty" else parts.joinToString(", ")
    }
}

/** How full a module's absence tube is (B2). */
data class AbsenceLevel(
    val module: String,
    val absent: Int,
    /** Sessions of the module this semester; null while unknown (then only the count is shown). */
    val total: Int?,
    /** absences ÷ total, or null. */
    val rate: Double?,
    /** rate ÷ limit, 0 and up (above 1: past the limit), or null. */
    val fill: Double?,
    /** "green" below 0.5, "amber" from 0.5 to 0.8, "red" above 0.8, "unknown" without a total. */
    val band: String,
)

object AbsenceMeter {
    /** The tubes of [absences] (B2): rate = absent / total, fill = rate / limit. */
    fun levels(absences: FeedAbsences): List<AbsenceLevel> = absences.modules.filter { it.name.isNotBlank() }.map { m ->
        val total = m.total?.takeIf { it > 0 }
        val rate = total?.let { m.absent.toDouble() / it }
        val limit = absences.limit.takeIf { it > 0.0 } ?: 0.20
        val fill = rate?.let { it / limit }
        val band = when {
            fill == null -> "unknown"
            fill > 0.8 -> "red"
            fill >= 0.5 -> "amber"
            else -> "green"
        }
        AbsenceLevel(m.name.trim(), m.absent, total, rate, fill, band)
    }
}

object Grades {
    /**
     * The weighted average (E2), Σ cᵢxᵢ / Σ cᵢ over the grades with a mark, or null while any of those has no
     * coefficient yet ("hidden until the coefficients are known") or there is no mark at all.
     */
    fun average(grades: List<FeedGrade>): Double? {
        val marked = grades.filter { it.mark != null }
        if (marked.isEmpty() || marked.any { it.coef == null || it.coef <= 0.0 }) return null
        val weights = marked.sumOf { it.coef!! }
        if (weights <= 0.0) return null
        return marked.sumOf { it.coef!! * it.mark!! } / weights
    }
}
