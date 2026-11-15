package dev.liquid.launcher.feature.home

import dev.liquid.launcher.core.data.StudyDay
import dev.liquid.launcher.core.data.StudyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the clock line and the pill's study item say (0.6.3, D92). */
class StudyGlanceTest {
    private val min = 60_000L
    private val t0 = 1_800_000_000_000L - 1_800_000_000_000L % (60 * min)

    private fun block(id: Long, startMin: Int, endMin: Int, title: String) =
        StudyEvent(id, 1, t0 + startMin * min, t0 + endMin * min, false, title)

    private fun task(id: Long, title: String) = StudyEvent(id, 2, t0, t0 + 24 * 60 * min, true, title)

    private val proba = block(1, 0, 90, "Proba 2: théorèmes de convergence")
    private val micro = block(2, 105, 150, "Micro: refaire le TD")

    @Test
    fun aRunningBlockShowsItsTimeLeftAndTheTimeGone() {
        val g = studyGlance(StudyDay(blocks = listOf(proba, micro)), t0 + 38 * min)
        assertTrue(g is StudyGlance.Running)
        g as StudyGlance.Running
        assertEquals(1L, g.block.eventId)
        assertEquals(52, g.minutesLeft)
        assertEquals(38f / 90f, g.progress, 0.001f)
        assertEquals("Proba 2 · 52 min left", glanceText(g))
        assertEquals("Proba 2 · 52 min", glanceText(g, short = true))
    }

    @Test
    fun aBlockMarkedDoneOrSkippedGivesWayToTheNextOne() {
        val done = proba.copy(rawTitle = "✓ " + proba.rawTitle)
        val g = studyGlance(StudyDay(blocks = listOf(done, micro)), t0 + 60 * min)
        assertTrue(g is StudyGlance.Next)
        g as StudyGlance.Next
        assertEquals(2L, g.block.eventId)
        assertEquals(45, g.minutesUntil)
        assertEquals("Next: Micro in 45 min", glanceText(g))
        val skipped = micro.copy(rawTitle = "✗ " + micro.rawTitle)
        assertNull(studyGlance(StudyDay(blocks = listOf(done, skipped)), t0 + 60 * min))
    }

    @Test
    fun aBlockThatEndedUnmarkedAsksToBeMarked_unlessTheNextOneStartsWithinTheHour() {
        // 10 min after Proba ended, Micro starts in 5 min: the next block wins.
        val soon = studyGlance(StudyDay(blocks = listOf(proba, micro)), t0 + 100 * min)
        assertTrue(soon is StudyGlance.Next)
        // A later block far away: the unmarked one comes first.
        val far = block(5, 400, 460, "Algo: un exercice")
        val g = studyGlance(StudyDay(blocks = listOf(proba, far)), t0 + 120 * min)
        assertEquals(StudyGlance.Unmarked(proba, 1), g)
        assertEquals("Proba 2 · not marked yet", glanceText(g!!))
        assertEquals("Mark Proba 2", glanceText(g, short = true))
        // Marked: the far block shows.
        val marked = studyGlance(StudyDay(blocks = listOf(proba.copy(rawTitle = "✓ " + proba.rawTitle), far)), t0 + 120 * min)
        assertTrue(marked is StudyGlance.Next)
        assertEquals("Next: Algo at " + java.time.Instant.ofEpochMilli(far.begin).atZone(java.time.ZoneId.systemDefault()).toLocalTime().let { "${it.hour}:%02d".format(it.minute) }, glanceText(marked!!))
    }

    @Test
    fun withEverythingMarkedItCountsTheOpenTasks_thenShowsTomorrow() {
        val later = t0 + 200 * min
        val doneDay = listOf(proba.copy(rawTitle = "✓ " + proba.rawTitle), micro.copy(rawTitle = "✗ " + micro.rawTitle))
        val g = studyGlance(StudyDay(blocks = doneDay, tasks = listOf(task(3, "Scolarité: relevé de notes"), task(4, "✓ R class: due day"))), later)
        assertEquals(StudyGlance.Tasks(1), g)
        assertEquals("1 task left today", glanceText(g!!))
        // Nothing left today: tomorrow's first block, so the line under the clock never just vanishes (D93).
        val stat = block(9, 24 * 60 + 17 * 60, 24 * 60 + 18 * 60 + 30, "Stat Des: cours du jour")
        val tomorrow = studyGlance(StudyDay(blocks = doneDay, tomorrowBlocks = listOf(stat)), later)
        assertEquals(StudyGlance.Tomorrow(stat), tomorrow)
        assertTrue(glanceText(tomorrow!!).startsWith("Tomorrow ") && glanceText(tomorrow).endsWith(" · Stat Des"))
        // "Today only" leaves tomorrow out; with nothing at all there is nothing to say.
        assertNull(studyGlance(StudyDay(blocks = doneDay, tomorrowBlocks = listOf(stat)), later, withTomorrow = false))
        assertNull(studyGlance(StudyDay(), later))
    }
}
