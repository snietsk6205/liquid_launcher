package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KickoffTest {
    private val min = 60_000L
    private val t0 = 1_795_000_000_000L
    private val time: (Long) -> String = { "T+" + (it - t0) / min }

    private fun note(id: Long, text: String) = StudyEvent(id, 2, t0, t0 + 24 * 60 * min, true, "Note: $text")

    @Test
    fun theDayInPlainLines() {
        val day = StudyDay(
            classes = listOf(
                StudyEvent(2, 9, t0 + 120 * min, t0 + 210 * min, false, "Proba 2"),
                StudyEvent(1, 9, t0, t0 + 90 * min, false, "Analyse: séries"),
            ),
            notes = listOf(note(5, "absent Proba 2 10:15"), note(6, "flashcard wrong Proba 2: énoncé"), note(7, "le prof a parlé du DS")),
            exams = listOf(StudyEvent(9, 9, t0 + 8 * 24 * 60 * min + 60 * min, t0 + 8 * 24 * 60 * min + 180 * min, false, "DS Analyse")),
            unconfirmedExams = setOf("9@" + (t0 + 8 * 24 * 60 * min + 60 * min)),
        )
        val text = Kickoff.context("Study check-in. Read section 9 of my plan doc.", day, "Proba 2", 45, listOf("Proba 2: TD 4, ex. 3", "2 flashcards due"), t0 + 300 * min, "Sunday 22 November", time)
        assertEquals(
            """
            Study check-in. Read section 9 of my plan doc.

            Today: Sunday 22 November, T+300.
            In class today: Analyse: séries T+0–T+90; Proba 2 T+120–T+210.
            I want to study: Proba 2.
            Studied today so far: 45 min.
            My notes today: flashcard wrong Proba 2: énoncé; le prof a parlé du DS.
            Recommended for me: Proba 2: TD 4, ex. 3; 2 flashcards due.
            Next exam: DS Analyse in 7 days (date not confirmed).
            """.trimIndent(),
            text,
        )
        // Absences stay with the launcher: never in what Claude reads.
        assertFalse(text.contains("absent"))
    }

    @Test
    fun nothingPickedAndAnEmptyDay() {
        val text = Kickoff.context("Study check-in.", StudyDay(), " ", 0, emptyList(), t0, "Monday 23 November", time)
        assertEquals("Study check-in.\n\nToday: Monday 23 November, T+0.\nNo class today.\nI have not chosen what to study yet: ask me.", text)
        assertTrue(!text.contains("Studied"))
    }
}
