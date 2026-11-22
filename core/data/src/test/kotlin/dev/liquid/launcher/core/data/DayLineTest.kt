package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayLineTest {
    private val min = 60_000L
    private val t0 = 1_795_000_000_000L // a morning
    private val time: (Long) -> String = { "T+" + (it - t0) / min }

    private fun event(id: Long, title: String, from: Int, to: Int, description: String = "", location: String = "") =
        StudyEvent(id, 1, t0 + from * min, t0 + to * min, false, title, description, location)

    private val projectR = event(1, "Project R: chapter 3", 0, 120, "[x] Exercise sheet 4\n[ ] Read chapter 3.2\n[ ] Flashcards deck 2")
    private val proba = event(2, "Probabilités", 240, 330, location = "B12")
    private val algo = event(3, "Algo: graphs", 400, 460)

    @Test
    fun whileTheFocusRunsTheLineHoldsTheNextStep() {
        val day = StudyDay(blocks = listOf(projectR, algo), classes = listOf(proba))
        val m = DayLine.focus(day, projectR.key, t0 + 30 * min)
        assertEquals(DayLine.Main.Step(projectR, StudyTask(1, "Read chapter 3.2", false), 2, 3), m)
        // Every step ticked: the block is left to mark done.
        val ticked = projectR.copy(description = "[x] a\n[x] b")
        assertEquals(DayLine.Main.StepsDone(ticked, 2), DayLine.focus(StudyDay(blocks = listOf(ticked)), ticked.key, t0 + 30 * min))
        // No steps: what comes after it (the class at T+240), or nothing.
        val plain = projectR.copy(description = "")
        assertEquals(DayLine.Main.After(plain, proba), DayLine.focus(StudyDay(blocks = listOf(plain), classes = listOf(proba)), plain.key, t0 + 30 * min))
        assertEquals(DayLine.Main.After(plain, null), DayLine.focus(StudyDay(blocks = listOf(plain)), plain.key, t0 + 30 * min))
        // No focus, a focus on another block, or the block not on now: nothing of the line's own.
        assertNull(DayLine.focus(day, null, t0 + 30 * min))
        assertNull(DayLine.focus(day, algo.key, t0 + 30 * min))
        assertNull(DayLine.focus(day, projectR.key, t0 + 130 * min))
    }

    @Test
    fun classesSayUntilWhenAndInHowLong() {
        val day = StudyDay(blocks = listOf(algo), classes = listOf(proba))
        val inClass = DayLine.inClass(day, t0 + 270 * min)!!
        assertEquals(proba, inClass.event)
        assertEquals(60, inClass.minutesLeft)
        assertEquals(30f / 90f, inClass.progress, 1e-4f)
        assertNull(DayLine.inClass(day, t0 + 200 * min))
        assertEquals(DayLine.Main.ClassSoon(proba, 25), DayLine.classSoon(day, t0 + 215 * min))
        assertNull(DayLine.classSoon(day, t0 + 150 * min))
    }

    @Test
    fun aLongGapIsFreeTime() {
        val day = StudyDay(blocks = listOf(algo), classes = listOf(proba))
        assertEquals(DayLine.Main.Free(proba, isClass = true), DayLine.free(day, t0 + 130 * min))
        // Within the hour it is not "free" (the main line counts down instead); after the class, the block.
        assertNull(DayLine.free(day, t0 + 200 * min))
        assertEquals(DayLine.Main.Free(algo, isClass = false), DayLine.free(day, t0 + 335 * min))
        // A done block is not waited for.
        assertNull(DayLine.free(StudyDay(blocks = listOf(algo.copy(rawTitle = "✓ Algo: graphs"))), t0 + 335 * min))
    }

    @Test
    fun theSecondLineSaysWhatTheFirstDoesNot() {
        val exam = event(9, "DS Analyse", 9 * 24 * 60, 9 * 24 * 60 + 120)
        val task = StudyEvent(7, 2, t0, t0 + 24 * 60 * min, true, "Scolarité: relevé")
        val day = StudyDay(blocks = listOf(projectR, algo), classes = listOf(proba), tasks = listOf(task), exams = listOf(exam))
        // During Project R (until T+120): then the class, then the task; the exam waits for room.
        val facts = DayLine.facts(day, t0 + 30 * min, from = projectR.end, shown = setOf(projectR.key), tasksShown = false, time = time)
        assertEquals(listOf("then Probabilités T+240", "1 task left"), facts.map { it.text })
        // With room for three, the exam (in 9 days, at the same time of day) comes last, amber.
        val three = DayLine.facts(day, t0 + 30 * min, from = projectR.end, shown = setOf(projectR.key), tasksShown = false, time = time, max = 3)
        // Counted as search counts it: whole days left (8 d 23 h is "8 d").
        assertEquals(DayLine.Fact("exam", "DS Analyse in 8 d", DayLine.Tone.Soon), three[2])
        // The main line already about the class: the next thing after it.
        val after = DayLine.facts(day, t0 + 270 * min, from = proba.end, shown = setOf(proba.key), tasksShown = true, time = time)
        assertEquals(listOf("then Algo T+400"), after.take(1).map { it.text })
        assertTrue(after.none { it.kind == "tasks" })
        // Three days or less: red, in hours, first.
        val close = exam.copy(begin = t0 + 50 * 60 * min, end = t0 + 52 * 60 * min)
        val urgent = DayLine.facts(day.copy(exams = listOf(close), unconfirmedExams = setOf(close.key)), t0 + 30 * min, from = t0 + 30 * min, shown = emptySet(), tasksShown = false, time = time)
        assertEquals(DayLine.Fact("exam", "DS Analyse in ≈49 h", DayLine.Tone.Urgent), urgent[0])
        // Far exams, no tasks, nothing after: nothing to say.
        val far = exam.copy(begin = t0 + 40L * 24 * 60 * min)
        assertEquals(emptyList<DayLine.Fact>(), DayLine.facts(StudyDay(blocks = listOf(algo), exams = listOf(far)), t0 + 410 * min, from = algo.end, shown = setOf(algo.key), tasksShown = false, time = time))
    }
}
