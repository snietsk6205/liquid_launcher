package dev.liquid.launcher.core.data

/**
 * 0.13.1 (D181, Med: "reimagine the line under the clock and make it more practical and useful to me"). Up to 0.13.0
 * the line said what the pill already said (the block running and its minutes left, "· focus", while the pill counted
 * the same focus down). Reimagined around two questions, what needs me now and what is left of my day:
 *
 * - The main line never repeats the pill. While the pill counts a focus down, the line holds the block's next step
 *   (its checklist line) with a tick, or, without steps, what comes after the block. A class running says until when and
 *   where; one within the hour, in how long and where; a long gap, "free until" the next thing.
 * - A quieter second line: what comes after the main line's event, the tasks left, an exam within two weeks.
 *
 * The rest (leaving in the morning, "present?", the rest after getting home, the evening check-in, a block to mark, a
 * block soon, tasks, tomorrow) stays the study glance's, in the home screen module. This part is pure, so it is tested
 * here.
 */
object DayLine {
    /** What the main line says when it is the line's own (the event it is about is [event]). */
    sealed interface Main {
        val event: StudyEvent

        /** Focus runs on [event] (the pill counts it down): its next open step, [number] of [total]. */
        data class Step(override val event: StudyEvent, val task: StudyTask, val number: Int, val total: Int) : Main

        /** Focus runs on [event] and its [total] steps are all ticked: what is left is marking it done. */
        data class StepsDone(override val event: StudyEvent, val total: Int) : Main

        /** Focus runs on [event], a block without steps: what comes after it ([next]; null: nothing more today). */
        data class After(override val event: StudyEvent, val next: StudyEvent?) : Main

        /** The class [event] is on: [minutesLeft] to its end, [progress] of it gone (0..1). */
        data class InClass(override val event: StudyEvent, val minutesLeft: Int, val progress: Float) : Main

        /** The class [event] begins in [minutesUntil] minutes (an hour or less). */
        data class ClassSoon(override val event: StudyEvent, val minutesUntil: Int) : Main

        /** Nothing for more than an hour: free until [event] (a class when [isClass]) begins. */
        data class Free(override val event: StudyEvent, val isClass: Boolean) : Main
    }

    /** How a fact on the second line is coloured: plain, soon (amber), urgent (red). */
    enum class Tone { Plain, Soon, Urgent }

    /** One fact on the second line; [kind] tells them apart ("then", "tasks", "exam"). */
    data class Fact(val kind: String, val text: String, val tone: Tone = Tone.Plain)

    /** Within this many minutes, a class or block is "soon" (the main line counts down to it). */
    const val SOON_MINUTES = 60

    /** An exam this many days away or closer shows on the second line (amber; red from [URGENT_DAYS]). */
    const val EXAM_DAYS = 14
    const val URGENT_DAYS = 3

    private const val MINUTE = 60_000L
    private const val DAY = 86_400_000L

    private fun minutesUntil(at: Long, now: Long): Int = ((at - now + MINUTE - 1) / MINUTE).toInt()

    /** Today's open blocks and classes that begin after [from], soonest first (a class and a block never twice). */
    fun upcoming(day: StudyDay, from: Long): List<StudyEvent> =
        (day.blocks.filter { it.status == StudyStatus.Open } + day.classes)
            .filter { !it.allDay && it.begin > from }
            .distinctBy { it.key }
            .sortedBy { it.begin }

    /**
     * While a focus runs on the block [focusKey] and the block is on now: its next open step (the first checklist line
     * not ticked), all steps ticked, or, without steps, what comes after it. Null when no focus runs on a block of today
     * that is on now.
     */
    fun focus(day: StudyDay, focusKey: String?, now: Long): Main? {
        if (focusKey == null) return null
        val block = day.blocks.firstOrNull { it.key == focusKey } ?: return null
        if (now < block.begin || now >= block.end || block.status != StudyStatus.Open) return null
        val steps = block.tasks
        if (steps.isEmpty()) return Main.After(block, upcoming(day, now).firstOrNull { it.key != block.key })
        val open = steps.indexOfFirst { !it.done }
        return if (open < 0) Main.StepsDone(block, steps.size) else Main.Step(block, steps[open], open + 1, steps.size)
    }

    /** The class on at [now] (the latest begun, when two overlap). */
    fun inClass(day: StudyDay, now: Long): Main.InClass? =
        day.classes.filter { !it.allDay && now >= it.begin && now < it.end }.maxByOrNull { it.begin }?.let { c ->
            val length = (c.end - c.begin).coerceAtLeast(1)
            Main.InClass(c, minutesUntil(c.end, now), ((now - c.begin).toFloat() / length).coerceIn(0f, 1f))
        }

    /** The next class, when it begins within [SOON_MINUTES]. */
    fun classSoon(day: StudyDay, now: Long): Main.ClassSoon? =
        day.classes.filter { !it.allDay && it.begin > now }.minByOrNull { it.begin }?.let { c ->
            val minutes = minutesUntil(c.begin, now)
            if (minutes <= SOON_MINUTES) Main.ClassSoon(c, minutes) else null
        }

    /** The next block or class, when it begins more than [SOON_MINUTES] from now: free until then. */
    fun free(day: StudyDay, now: Long): Main.Free? =
        upcoming(day, now).firstOrNull()?.let { e ->
            if (minutesUntil(e.begin, now) > SOON_MINUTES) Main.Free(e, day.classes.any { it.key == e.key }) else null
        }

    /**
     * The second line: at most [max] facts the main line does not say. In order: an exam within [URGENT_DAYS] (red),
     * what comes after [from] (the end of what the main line is about, or now) other than the events in [shown], the
     * tasks left (unless the main line is about them, [tasksShown]), an exam within [EXAM_DAYS] (amber; "≈" when its
     * date is not confirmed). [time] writes a clock time as the launcher does.
     */
    fun facts(
        day: StudyDay,
        now: Long,
        from: Long,
        shown: Set<String>,
        tasksShown: Boolean,
        time: (Long) -> String,
        max: Int = 2,
    ): List<Fact> {
        val out = ArrayList<Fact>(3)
        val exam = day.exams.firstOrNull { it.begin > now }
        val examFact = exam?.let { e ->
            val days = (e.begin - now) / DAY
            if (days > EXAM_DAYS) return@let null
            val about = if (e.key in day.unconfirmedExams) "≈" else ""
            val left = if (days <= URGENT_DAYS) "${((e.begin - now) / 3_600_000L).coerceAtLeast(1)} h" else "$days d"
            Fact("exam", e.title.text.ifBlank { e.rawTitle } + " in " + about + left, if (days <= URGENT_DAYS) Tone.Urgent else Tone.Soon)
        }
        if (examFact?.tone == Tone.Urgent) out += examFact
        upcoming(day, from - 1).firstOrNull { it.key !in shown }?.let { next ->
            out += Fact("then", "then " + next.title.subject.ifBlank { next.rawTitle } + " " + time(next.begin))
        }
        val tasks = day.openTasks
        if (!tasksShown && tasks > 0) out += Fact("tasks", if (tasks == 1) "1 task left" else "$tasks tasks left")
        if (examFact != null && examFact.tone != Tone.Urgent) out += examFact
        return out.take(max)
    }
}
