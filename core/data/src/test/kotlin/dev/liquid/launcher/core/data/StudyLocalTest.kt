package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyLocalTest {
    private val block = StudyEvent(
        eventId = 7, calendarId = 1, begin = 1_000_000, end = 1_000_000 + 90 * 60_000L, allDay = false,
        rawTitle = "Analysis: series", description = "[ ] Sheet 4\n[ ] Chapter 3.2",
    )

    @Test
    fun ticksKeptOnThePhoneShowUntilTheEventChanges() {
        val ticked = StudyFormat.setTask(block.description, 0, true)!!
        val kept = StudyLocalEdit(block.rawTitle, block.description, StudyFormat.withStatus(block.rawTitle, StudyStatus.Done), ticked, 5)
        val shown = StudyLocal.apply(block, kept)
        assertEquals(StudyStatus.Done, shown.status)
        assertEquals(listOf(true, false), shown.tasks.map { it.done })

        // The assistant re-planned the event: the calendar wins.
        val replanned = block.copy(description = "[ ] Sheet 5")
        assertFalse(StudyLocal.isCurrent(kept, replanned.rawTitle, replanned.description))
        assertEquals(replanned, StudyLocal.apply(replanned, kept))
        assertEquals(block, StudyLocal.apply(block, null))
    }

    @Test
    fun keptTicksSurviveTheirTextForm() {
        val edits = mapOf(
            block.key to StudyLocalEdit("A: b", "[ ] one\n[x] two\n\nnotes", "✓ A: b", "[x] one\n[x] two\n\nnotes\n— launcher: done 9:05", 42),
            "8@0" to StudyLocalEdit("", "", "✗ Gym", "", 0),
        )
        assertEquals(edits, StudyLocal.decode(StudyLocal.encode(edits)))
        assertTrue(StudyLocal.decode(null).isEmpty())
        assertTrue(StudyLocal.decode("broken").isEmpty())
    }

    @Test
    fun focusRunsToTheEndOfItsBlock() {
        val start = 0L
        val end = 60 * 60_000L
        assertEquals(25, StudyLocal.focusMinutes(start, 25 * 60_000L, end))
        // Forgotten until the evening: only the block's hour counts.
        assertEquals(60, StudyLocal.focusMinutes(start, 5 * 60 * 60_000L, end))
        // Block end unknown (timer started by an older build): the time until it was stopped, at most 12 hours.
        assertEquals(12 * 60, StudyLocal.focusMinutes(start, 20 * 60 * 60_000L))
        assertFalse(StudyLocal.focusOver(end, end - 1))
        assertTrue(StudyLocal.focusOver(end, end))
        assertFalse(StudyLocal.focusOver(0, end))
    }
}
