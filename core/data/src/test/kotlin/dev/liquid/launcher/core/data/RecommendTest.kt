package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendTest {
    private val hour = 3_600_000L
    private val now = 1_795_000_000_000L

    private fun exam(id: Long, title: String, inHours: Long) = StudyEvent(id, 3, now + inHours * hour, now + (inHours + 2) * hour, false, title)

    @Test
    fun claudesTasksTheCardsAndADeadlineInOrder() {
        val feed = StudyFeed(recommended = listOf("Proba 2: convergence dominée", " ", "Proba 2: convergence dominée", "Micro: TD 3, ex. 2"))
        val analyse = exam(1, "DS Analyse", 8 * 24)
        val eco = exam(2, "Projet Économie", 11 * 24)
        val items = Recommend.items(feed, listOf(analyse, eco), emptySet(), due = 3, now = now, headerExam = analyse.key)
        assertEquals(
            listOf("Proba 2: convergence dominée", "Micro: TD 3, ex. 2", "3 flashcards due", "Projet Économie in 11 d"),
            items.map { it.text },
        )
        assertTrue(items.none { it.urgent })
    }

    @Test
    fun aDeadlineWithinThreeDaysComesFirstInHours() {
        val close = exam(1, "Checkpoint R", 50)
        val items = Recommend.items(StudyFeed(recommended = listOf("Algo: tri fusion")), listOf(close), setOf(close.key), due = 0, now = now)
        assertEquals(Recommend.Item("exam:" + close.key, "Checkpoint R in ≈50 h", Recommend.Kind.Exam, urgent = true), items[0])
        assertEquals(2, items.size)
    }

    @Test
    fun atMostFourAndNothingWithoutSources() {
        val feed = StudyFeed(recommended = listOf("a", "b", "c", "d"))
        // Claude's are three at most; with the cards and an exam, four in all.
        val items = Recommend.items(feed, listOf(exam(1, "DS", 5 * 24)), emptySet(), due = 2, now = now)
        assertEquals(4, items.size)
        assertEquals(listOf("a", "b", "c", "2 flashcards due"), items.map { it.text })
        assertEquals(emptyList<Recommend.Item>(), Recommend.items(null, emptyList(), emptySet(), due = 0, now = now))
        // An exam far away is not recommended.
        assertEquals(emptyList<Recommend.Item>(), Recommend.items(null, listOf(exam(1, "DS", 40 * 24)), emptySet(), due = 0, now = now))
    }

    @Test
    fun ticksAreKeptForTheDayOnly() {
        var stored = Recommend.tick(listOf("2026-11-21|cards"), "2026-11-22", "claude:a", true)
        assertEquals(listOf("2026-11-22|claude:a"), stored)
        assertEquals(setOf("claude:a"), Recommend.ticked(stored, "2026-11-22"))
        assertEquals(emptySet<String>(), Recommend.ticked(stored, "2026-11-23"))
        stored = Recommend.tick(stored, "2026-11-22", "claude:a", false)
        assertEquals(emptyList<String>(), stored)
    }

    @Test
    fun launcherDataReadsRecommendedTasks() {
        val feed = StudyFeedParser.parse("""{"v":1,"recommended":["Proba 2: TD 4, ex. 3","Algo: tri rapide"],"exams":[]}""")!!
        assertEquals(listOf("Proba 2: TD 4, ex. 3", "Algo: tri rapide"), feed.tasks)
        assertTrue(StudyFeedParser.describe(feed).contains("2 recommended tasks"))
        // Older launcher-data, without it, reads as before.
        assertEquals(emptyList<String>(), StudyFeedParser.parse("""{"v":1,"exams":[]}""")!!.tasks)
    }
}
