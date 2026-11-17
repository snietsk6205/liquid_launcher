package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextLayoutsTest {
    private val min = 60_000L
    private val t0 = 1_800_000_000_000L
    private val cls = StudyEvent(1, 3, t0, t0 + 90 * min, false, "Stat Des")
    private val exam = StudyEvent(-1, 0, t0 + 5 * 24 * 60 * min, t0 + 5 * 24 * 60 * min + 120 * min, true, "DS + Examens P2")

    @Test
    fun commuteFromTheLastClassUntilHome() {
        val day = StudyDay(classes = listOf(cls))
        assertNull(ContextLayouts.context(day, t0 + 60 * min, null, commute = true, exam = false))
        assertEquals("commute", ContextLayouts.context(day, t0 + 100 * min, null, commute = true, exam = false))
        // Home (after the class): over. At most four hours without an arrival.
        assertNull(ContextLayouts.context(day, t0 + 150 * min, t0 + 140 * min, commute = true, exam = false))
        assertNull(ContextLayouts.context(day, t0 + 90 * min + 241 * min, null, commute = true, exam = false))
        // Not chosen: never.
        assertNull(ContextLayouts.context(day, t0 + 100 * min, null, commute = false, exam = false))
    }

    @Test
    fun examWeek_andCommuteComesFirst() {
        val day = StudyDay(classes = listOf(cls), exams = listOf(exam))
        assertEquals("exam", ContextLayouts.context(day, t0 + 30 * min, null, commute = true, exam = true))
        assertEquals("commute", ContextLayouts.context(day, t0 + 100 * min, null, commute = true, exam = true))
        assertNull(ContextLayouts.context(StudyDay(exams = listOf(exam)), exam.begin - 8 * 24 * 60 * min, null, commute = false, exam = true))
        // The exam day itself still counts, until the exam is over.
        assertEquals("exam", ContextLayouts.context(StudyDay(exams = listOf(exam)), exam.begin + 30 * min, null, commute = false, exam = true))
        assertNull(ContextLayouts.context(StudyDay(exams = listOf(exam)), exam.end + 1, null, commute = false, exam = true))
    }

    @Test
    fun stepsRememberTheBaseAndRespectAHandSwitch() {
        assertEquals(ContextLayouts.Step.Enter("commute", "trip", "home"), ContextLayouts.step(null, "commute", "home", "trip", null, null))
        assertEquals(ContextLayouts.Step.Stay, ContextLayouts.step("commute", "commute", "trip", "trip", "trip", "home"))
        assertEquals(ContextLayouts.Step.Leave("home"), ContextLayouts.step("commute", null, "trip", null, "trip", "home"))
        // Switched by hand during the trip: leaving does not undo it.
        assertEquals(ContextLayouts.Step.Leave(null), ContextLayouts.step("commute", null, "study", null, "trip", "home"))
        // From the exam week straight into a trip: the base stays the layout from before the exam week.
        assertEquals(ContextLayouts.Step.Enter("commute", "trip", "home"), ContextLayouts.step("exam", "commute", "exam-l", "trip", "exam-l", "home"))
    }
}
