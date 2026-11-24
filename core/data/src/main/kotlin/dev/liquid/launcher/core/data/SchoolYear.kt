package dev.liquid.launcher.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One date of the school year (0.16.0, D209): built in (ESSAI 2026–27) or added, always editable and deletable, because
 * the school calls its dates indicative. [start] and [end] are ISO dates; [end] is empty for one day.
 */
@Serializable
data class SchoolDate(
    val id: String,
    val title: String,
    /** The name in a countdown ("DS P2 in 5 days"); the title when empty. */
    val short: String = "",
    val start: String,
    val end: String = "",
    /** "break", "revision", "exam", "period", "classes", "retake", "deliberation" or "other". */
    val kind: String = "other",
    /** The school gave this date with "~" (about): shown with ≈. */
    val approx: Boolean = false,
    /** Its countdown shows on the line under the clock. */
    val pinned: Boolean = false,
    /** One of the dates the launcher came with. */
    val builtIn: Boolean = false,
) {
    val startDate: LocalDate? get() = runCatching { LocalDate.parse(start) }.getOrNull()

    /** The last day (the start for a one-day date, or when the end is broken or before the start). */
    val endDate: LocalDate?
        get() {
            val s = startDate ?: return null
            val e = if (end.isBlank()) null else runCatching { LocalDate.parse(end) }.getOrNull()
            return if (e == null || e.isBefore(s)) s else e
        }

    val label: String get() = short.ifBlank { title }

    /** An exam or a retake: its countdown turns amber, then red, like the exams under the clock. */
    val isExam: Boolean get() = kind == "exam" || kind == "retake"

    fun covers(day: LocalDate): Boolean {
        val s = startDate ?: return false
        val e = endDate ?: s
        return !day.isBefore(s) && !day.isAfter(e)
    }
}

object SchoolYear {
    /**
     * ESSAI 2026–27, as Med gave it ("the school calls these indicative"): the winter break from about 26 December, the
     * revisions, the DS and exams of each period, the starts of periods 3 and 4, the last day of classes, the retakes,
     * the deliberations. Nothing is pinned until he pins it.
     */
    val ESSAI_2026_27: List<SchoolDate> = listOf(
        SchoolDate("essai27.winter", "Winter break", "Winter break", "2026-12-26", kind = "break", approx = true, builtIn = true),
        SchoolDate("essai27.revision1", "Révision", "Révision", "2027-01-03", kind = "revision", builtIn = true),
        SchoolDate("essai27.p2exams", "DS et Examens P2", "DS P2", "2027-01-07", kind = "exam", builtIn = true),
        SchoolDate("essai27.p3", "Période 3 starts", "Période 3", "2027-01-11", kind = "period", builtIn = true),
        SchoolDate("essai27.p3exams", "DS / Examens P3", "DS P3", "2027-03-06", kind = "exam", builtIn = true),
        SchoolDate("essai27.p4", "Période 4 starts", "Période 4", "2027-03-08", kind = "period", builtIn = true),
        SchoolDate("essai27.lastClass", "Last day of classes", "Last day of classes", "2027-05-08", kind = "classes", builtIn = true),
        SchoolDate("essai27.retakes", "Rattrapages", "Rattrapages", "2027-05-10", "2027-05-12", kind = "retake", builtIn = true),
        SchoolDate("essai27.revision2", "Révision", "Révision", "2027-05-15", kind = "revision", builtIn = true),
        SchoolDate("essai27.p4exams", "Examens P4", "Examens P4", "2027-05-20", kind = "exam", builtIn = true),
        SchoolDate("essai27.delibSP", "Délib SP", "Délib SP", "2027-06-03", kind = "deliberation", builtIn = true),
        SchoolDate("essai27.revisionSC", "Révision SC", "Révision SC", "2027-06-06", kind = "revision", builtIn = true),
        SchoolDate("essai27.examsSC", "Examens SC", "Examens SC", "2027-06-11", kind = "exam", builtIn = true),
        SchoolDate("essai27.delibSC", "Délib SC", "Délib SC", "2027-06-25", kind = "deliberation", builtIn = true),
    )

    val kinds: List<Pair<String, String>> = listOf(
        "exam" to "Exams", "retake" to "Retakes", "revision" to "Revision", "period" to "Period", "classes" to "Classes",
        "break" to "Break", "deliberation" to "Deliberation", "other" to "Other",
    )

    private val serializer = ListSerializer(SchoolDate.serializer())

    /** The stored list: empty text is the built-in list, untouched; broken text too (never an empty year by accident). */
    fun parse(text: String): List<SchoolDate> {
        if (text.isBlank()) return ESSAI_2026_27
        return runCatching { LauncherJson.decodeFromString(serializer, text) }.getOrElse { ESSAI_2026_27 }
    }

    fun encode(dates: List<SchoolDate>): String = LauncherJson.encodeToString(serializer, dates)

    /** In date order (undated ones last). */
    fun sorted(dates: List<SchoolDate>): List<SchoolDate> = dates.sortedWith(compareBy({ it.startDate ?: LocalDate.MAX }, { it.title }))

    fun onDay(dates: List<SchoolDate>, day: LocalDate): List<SchoolDate> = sorted(dates.filter { it.covers(day) })

    /** The dates not over yet on [today], soonest first. */
    fun upcoming(dates: List<SchoolDate>, today: LocalDate): List<SchoolDate> =
        sorted(dates.filter { d -> d.endDate?.let { !it.isBefore(today) } == true })

    /** The pinned date that comes first (or is on now), for the line under the clock. */
    fun pinnedNext(dates: List<SchoolDate>, today: LocalDate): SchoolDate? = upcoming(dates, today).firstOrNull { it.pinned }

    /**
     * "DS P2 in 5 days", "DS P2 tomorrow", "DS P2 today", "Rattrapages · day 2 of 3", "Winter break in ≈ 32 days";
     * past nine weeks, in weeks. Null once it is over (or without a date).
     */
    fun countdown(d: SchoolDate, today: LocalDate): String? {
        val s = d.startDate ?: return null
        val e = d.endDate ?: s
        if (today.isAfter(e)) return null
        val about = if (d.approx) "≈ " else ""
        if (!today.isBefore(s)) {
            if (s == e) return "${d.label} today"
            val n = ChronoUnit.DAYS.between(s, today) + 1
            val total = ChronoUnit.DAYS.between(s, e) + 1
            return "${d.label} · day $n of $total"
        }
        val days = ChronoUnit.DAYS.between(today, s)
        return when {
            days == 1L -> "${d.label} ${about}tomorrow"
            days < 63 -> "${d.label} in $about$days days"
            else -> "${d.label} in $about${days / 7} weeks"
        }
    }

    /** How a countdown is coloured under the clock: an exam or retake within two weeks amber, within three days red. */
    fun tone(d: SchoolDate, today: LocalDate): DayLine.Tone {
        if (!d.isExam) return DayLine.Tone.Plain
        val s = d.startDate ?: return DayLine.Tone.Plain
        val days = ChronoUnit.DAYS.between(today, s)
        return when {
            days <= DayLine.URGENT_DAYS -> DayLine.Tone.Urgent
            days <= DayLine.EXAM_DAYS -> DayLine.Tone.Soon
            else -> DayLine.Tone.Plain
        }
    }

    /** The pinned countdown for the line under the clock, or null. */
    fun pinnedFact(dates: List<SchoolDate>, today: LocalDate): DayLine.Pinned? {
        val d = pinnedNext(dates, today) ?: return null
        val text = countdown(d, today) ?: return null
        val day = if (d.covers(today)) today else d.startDate ?: return null
        return DayLine.Pinned(day, text, d.label, tone(d, today))
    }

    // ---- edits (each returns the new list) ----

    fun togglePin(dates: List<SchoolDate>, id: String): List<SchoolDate> = dates.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }

    /** Moves the date [days] later (earlier when negative), its end with it. */
    fun move(dates: List<SchoolDate>, id: String, days: Long): List<SchoolDate> = dates.map { d ->
        if (d.id != id) return@map d
        val s = d.startDate ?: return@map d
        val e = if (d.end.isBlank()) null else d.endDate
        d.copy(start = s.plusDays(days).toString(), end = e?.plusDays(days)?.toString() ?: "")
    }

    fun remove(dates: List<SchoolDate>, id: String): List<SchoolDate> = dates.filter { it.id != id }

    /** Replaces the date with [changed]'s id, or adds it when new. */
    fun put(dates: List<SchoolDate>, changed: SchoolDate): List<SchoolDate> =
        if (dates.any { it.id == changed.id }) dates.map { if (it.id == changed.id) changed else it } else dates + changed

    /**
     * The built-in dates as they came (each keeps whether it was pinned); dates of your own stay. Built-in dates you
     * deleted come back.
     */
    fun restoreBuiltIn(dates: List<SchoolDate>): List<SchoolDate> {
        val pinned = dates.filter { it.builtIn && it.pinned }.map { it.id }.toSet()
        val own = dates.filter { !it.builtIn }
        return ESSAI_2026_27.map { if (it.id in pinned) it.copy(pinned = true) else it } + own
    }

    /** A new id for a date of your own. */
    fun newId(dates: List<SchoolDate>, now: Long): String {
        var n = now
        while (dates.any { it.id == "own.$n" }) n++
        return "own.$n"
    }
}
