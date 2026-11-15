package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFormatTest {
    private val plain = "[ ] Exercise sheet 4, ex. 1–5\n- [x] Read chapter 3.2\n[ ] Flashcards deck 2\n\n" +
        "Focus on the convergence tests [not a task]. Sheet: https://example.org/sheet4.pdf."

    @Test
    fun titles() {
        assertEquals(StudyTitle(StudyStatus.Open, "Analysis", "series convergence"), StudyFormat.parseTitle("Analysis: series convergence"))
        assertEquals(StudyTitle(StudyStatus.Done, "Analysis", "series"), StudyFormat.parseTitle("✓ Analysis: series"))
        assertEquals(StudyTitle(StudyStatus.Skipped, "Gym", ""), StudyFormat.parseTitle("✗ Gym"))
        // A colon inside a time is not a subject.
        assertEquals("Call at 14:30", StudyFormat.parseTitle("Call at 14:30").subject)
        assertEquals("✓ Analysis: series", StudyFormat.withStatus("Analysis: series", StudyStatus.Done))
        assertEquals("✗ Analysis: series", StudyFormat.withStatus("✓ Analysis: series", StudyStatus.Skipped))
        assertEquals("Analysis: series", StudyFormat.withStatus("✓ Analysis: series", StudyStatus.Open))
        assertEquals("Analysis: series", StudyFormat.parseTitle("✓ Analysis: series").text)
    }

    @Test
    fun tasksNotesAndLink() {
        val tasks = StudyFormat.tasks(plain)
        assertEquals(listOf("Exercise sheet 4, ex. 1–5", "Read chapter 3.2", "Flashcards deck 2"), tasks.map { it.text })
        assertEquals(listOf(false, true, false), tasks.map { it.done })
        assertTrue(StudyFormat.notes(plain).startsWith("Focus on the convergence tests"))
        assertEquals("https://example.org/sheet4.pdf", StudyFormat.firstLink(plain))
    }

    @Test
    fun tickingATaskChangesOnlyItsBox() {
        val ticked = StudyFormat.setTask(plain, 2, true)!!
        assertEquals(plain.replace("[ ] Flashcards", "[x] Flashcards"), ticked)
        val unticked = StudyFormat.setTask(ticked, 1, false)!!
        assertEquals(listOf(false, false, true), StudyFormat.tasks(unticked).map { it.done })
        assertTrue("- [ ] Read chapter 3.2" in unticked)
        assertNull(StudyFormat.setTask(plain, 7, true))
    }

    @Test
    fun htmlDescriptionsAreReadAndEditedInPlace() {
        val html = "<ul><li>[ ] One</li><li>[x] Two</li></ul>Remember the &amp; sign<br>Second note"
        assertEquals(listOf("One", "Two"), StudyFormat.tasks(html).map { it.text })
        assertEquals("Remember the & sign\nSecond note", StudyFormat.notes(html))
        assertEquals("<ul><li>[x] One</li><li>[x] Two</li></ul>Remember the &amp; sign<br>Second note", StudyFormat.setTask(html, 0, true))
        val withLine = StudyFormat.withStatusLine(html, "— launcher: done 9:05")
        assertTrue(withLine.endsWith("<br>— launcher: done 9:05"))
        assertEquals(html, StudyFormat.withStatusLine(withLine, null))
    }

    @Test
    fun statusLineIsReplacedNotRepeated() {
        val once = StudyFormat.refreshed(StudyFormat.setTask(plain, 0, true)!!, StudyNote(doneAt = "15:31", focusMinutes = 84))
        assertTrue(once.endsWith("\n— launcher: done 15:31 · 2/3 tasks · focus 84 min"))
        val note = StudyFormat.note(once)
        assertEquals("15:31", note.doneAt)
        assertEquals(84, note.focusMinutes)
        val twice = StudyFormat.refreshed(StudyFormat.setTask(once, 2, true)!!, note)
        assertEquals(1, Regex("— launcher:").findAll(twice).count())
        assertTrue(twice.endsWith("— launcher: done 15:31 · 3/3 tasks · focus 84 min"))
        // The status line is neither a task nor a note.
        assertEquals(3, StudyFormat.tasks(twice).size)
        assertFalse("launcher" in StudyFormat.notes(twice))
        // Nothing ticked and nothing noted: no line at all.
        val untouched = "[ ] One\n[ ] Two"
        assertEquals(untouched, StudyFormat.refreshed(untouched, StudyNote()))
    }

    @Test
    fun currentAndNextBlock() {
        fun block(id: Long, from: Long, to: Long, title: String = "A: b") = StudyEvent(id, 1, from, to, false, title)
        val day = StudyDay(blocks = listOf(block(1, 100, 200), block(2, 200, 300, "✓ Done: early"), block(3, 400, 500)))
        assertEquals(1L, day.current(150)?.eventId)
        assertEquals(2L, day.current(250)?.eventId)
        assertNull(day.current(350))
        assertEquals(3L, day.next(350)?.eventId)
        assertNull(day.next(450))
    }
}
