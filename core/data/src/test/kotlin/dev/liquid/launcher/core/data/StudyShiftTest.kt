package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyShiftTest {
    private val min = 60_000L
    private fun at(h: Int, m: Int = 0) = (h * 60 + m) * min

    private fun block(id: Long, title: String, from: Long, to: Long, description: String = "[ ] step\nP2-BLOCK 2026-11-17 b$id") =
        StudyEvent(eventId = id, calendarId = 1, begin = from, end = to, allDay = false, rawTitle = title, description = description)

    private val evening = listOf(
        block(1, "Proba 2: série 1", at(19, 10), at(20, 10)),
        block(2, "Micro: chapitre 3", at(20, 20), at(21, 20)),
        block(3, "Catch-up: Algo", at(21, 30), at(22, 30)),
    )

    @Test
    fun homeEarly_needsNoShift() {
        assertEquals(0, StudyShift.deltaFor(evening, at(18, 40)))
    }

    @Test
    fun homeLate_shiftsSoTheFirstBlockStarts20MinutesAfterArrival() {
        // Home 19:20: the first block (19:10) should start at 19:40.
        assertEquals(30, StudyShift.deltaFor(evening, at(19, 20)))
        // Rounded up to five minutes.
        assertEquals(35, StudyShift.deltaFor(evening, at(19, 22)))
    }

    @Test
    fun shift_keepsLengthsAndLeavesFinishedOrTouchedBlocksAlone() {
        val touched = evening.toMutableList()
        touched[1] = block(2, "Micro: chapitre 3", at(20, 20), at(21, 20), "[x] step\nP2-BLOCK 2026-11-17 b2")
        val plan = StudyShift.plan(touched, at(19, 20), 30, at(23), shorten = true)
        assertEquals(listOf(1L, 3L), plan.moves.map { it.event.eventId })
        assertEquals(at(19, 40), plan.moves[0].begin)
        assertEquals(at(20, 40), plan.moves[0].end)
        assertEquals(at(23), plan.moves[1].end)
        assertNull(plan.shortened)
        assertFalse(plan.endsLate)
    }

    @Test
    fun pastTheLatestEnd_theCatchUpBlockIsShortened() {
        val plan = StudyShift.plan(evening, at(20, 5), 75, at(23), shorten = true)
        // 22:30 + 75 min = 23:45: 45 minutes too long, but the catch-up block keeps 20 minutes.
        assertEquals(3L, plan.shortened?.eventId)
        assertEquals(40, plan.shortenedMinutes)
        assertEquals(at(23, 5), plan.lastEnd)
        assertTrue(plan.endsLate)
    }

    @Test
    fun shorteningAMiddleBlock_pullsTheLaterOnesUp() {
        val blocks = listOf(
            block(1, "Checkpoint: semaine", at(19), at(20)),
            block(2, "Micro: chapitre 3", at(20), at(22)),
        )
        val plan = StudyShift.plan(blocks, at(19, 10), 90, at(23), shorten = true)
        assertEquals(1L, plan.shortened?.eventId)
        assertEquals(30, plan.shortenedMinutes)
        assertEquals(at(21), plan.moves[0].end)
        assertEquals(at(21), plan.moves[1].begin)
        assertEquals(at(23), plan.moves[1].end)
        assertFalse(plan.endsLate)
    }

    @Test
    fun marks_areKeptOnTheirOwnLinesAndTheStatusLineStaysLast() {
        val start = "[ ] step\nWHAT x\nP2-BLOCK 2026-11-17 b1\n— launcher: done 20:15 · 0/1 tasks"
        val home = StudyFormat.withMark(start, StudyFormat.MARK_HOME, "19:20")
        assertEquals("[ ] step\nWHAT x\nP2-BLOCK 2026-11-17 b1\n— launcher: home 19:20\n— launcher: done 20:15 · 0/1 tasks", home)
        val shifted = StudyFormat.withMark(home, StudyFormat.MARK_SHIFTED, "+30 min")
        assertEquals("19:20", StudyFormat.mark(shifted, StudyFormat.MARK_HOME))
        assertEquals(30, StudyFormat.shiftedBy(shifted))
        assertEquals("20:15", StudyFormat.note(shifted).doneAt)
        // A tick afterwards rewrites the status line and nothing else.
        val ticked = StudyFormat.refreshed(StudyFormat.setTask(shifted, 0, true)!!, StudyFormat.note(shifted))
        assertTrue(ticked.endsWith("— launcher: done 20:15 · 1/1 tasks"))
        assertEquals("19:20", StudyFormat.mark(ticked, StudyFormat.MARK_HOME))
        assertEquals(30, StudyFormat.shiftedBy(ticked))
        assertTrue("P2-BLOCK 2026-11-17 b1\n" in ticked)
        // A second shift replaces the first line.
        assertEquals(45, StudyFormat.shiftedBy(StudyFormat.withMark(ticked, StudyFormat.MARK_SHIFTED, "+45 min")))
        assertEquals("[x] step", StudyFormat.lines(ticked).first())
        assertEquals("", StudyFormat.notes(ticked).lines().firstOrNull { it.startsWith(StudyFormat.STATUS_PREFIX) } ?: "")
    }
}
