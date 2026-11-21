package dev.liquid.launcher.core.data

/**
 * Layouts that switch by themselves (A4, C4, P8's context triggers; 0.9.11). Pure decisions, tested without a phone;
 * the home screen applies them on its minute tick.
 */
object ContextLayouts {
    const val COMMUTE = "commute"
    const val EXAM = "exam"
    /** W8 (P8): the layout chosen for a time of day. */
    const val WINDOW = "window"

    /**
     * True when [time] is inside the window from [from] to [to] ("HH:MM"; [to] earlier than [from] runs over midnight)
     * on [days] ("weekdays", "weekend" or "every"). The day is the one the window started on.
     */
    fun inWindow(from: String, to: String, days: String, time: java.time.LocalDateTime): Boolean {
        fun minutes(t: String): Int? = t.split(':').takeIf { it.size == 2 }?.let { (h, m) -> h.toIntOrNull()?.let { hh -> m.toIntOrNull()?.let { hh * 60 + it } } }
        val a = minutes(from) ?: return false
        val b = minutes(to) ?: return false
        if (a == b) return false
        val m = time.hour * 60 + time.minute
        val overnight = b < a
        val inside = if (overnight) m >= a || m < b else m in a until b
        if (!inside) return false
        // After midnight in an overnight window, the window belongs to the day before.
        val day = if (overnight && m < b) time.toLocalDate().minusDays(1) else time.toLocalDate()
        val weekend = day.dayOfWeek == java.time.DayOfWeek.SATURDAY || day.dayOfWeek == java.time.DayOfWeek.SUNDAY
        return when (days) {
            "weekdays" -> !weekend
            "weekend" -> weekend
            else -> true
        }
    }

    /** The longest a trip home is assumed to last when the arrival is never noticed. */
    private const val MAX_TRIP_MS = 4 * 60 * 60_000L
    private const val EXAM_WEEK_MS = 7 * 24 * 60 * 60_000L

    /**
     * The context now: [COMMUTE] from the end of today's last class until home (at most 4 h), when a commute
     * layout is chosen; else [EXAM] from seven days before the next exam until it is over, when an exam layout is chosen;
     * else null.
     */
    fun context(day: StudyDay, now: Long, homeAt: Long?, commute: Boolean, exam: Boolean): String? {
        if (commute) {
            val lastClass = day.lastClassEnd
            if (lastClass != null && now >= lastClass && now - lastClass <= MAX_TRIP_MS && (homeAt == null || homeAt < lastClass)) return COMMUTE
        }
        if (exam) {
            // Until the exam is over: the exam day itself is part of the week.
            val next = day.exams.firstOrNull { it.end > now }
            if (next != null && next.begin - now <= EXAM_WEEK_MS) return EXAM
        }
        return null
    }

    /** What to do now. */
    sealed interface Step {
        /** Nothing changes. */
        data object Stay : Step

        /** Switch to [layout]; remember [base] (the layout to come back to) and that [context] is on. */
        data class Enter(val context: String, val layout: String, val base: String?) : Step

        /** The context is over: back to [layout] (null: stay, the user switched by hand meanwhile). */
        data class Leave(val layout: String?) : Step
    }

    /**
     * The step from [active] (the context applied last, null for none) to [now] (the context now), with [current] the
     * layout on screen, [wanted] the layout of [now], [applied] the layout [active] switched to and [base] the layout
     * from before it. A hand switch during a context is respected: leaving never undoes it.
     */
    fun step(active: String?, now: String?, current: String?, wanted: String?, applied: String?, base: String?): Step = when {
        now == active -> Step.Stay
        now != null && wanted != null -> Step.Enter(now, wanted, if (active == null) current else base)
        active != null -> Step.Leave(if (current == applied) base else null)
        else -> Step.Stay
    }
}
