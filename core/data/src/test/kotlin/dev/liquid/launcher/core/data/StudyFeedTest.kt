package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFeedTest {
    private val specExample = """
        {"v":1,"updated":"2026-11-16T20:50",
         "exams":[{"label":"DS + Examens P2","date":"2027-01-07","confirmed":false}],
         "absences":{"rule_confirmed":false,"limit":0.20,
           "modules":[{"name":"Intégration et Probabilité TD","absent":3,"total":null}]},
         "flashcard":{"module":"Micro","q":"…","a":"…"},
         "grades":[{"module":"Stat Des","type":"DS","mark":null,"coef":null}]}
    """.trimIndent()

    @Test
    fun theSpecsExampleParses() {
        val feed = StudyFeedParser.parse(specExample)!!
        assertEquals(1, feed.v)
        assertEquals("DS + Examens P2", feed.exams.single().label)
        assertFalse(feed.exams.single().confirmed)
        assertFalse(feed.absences!!.ruleConfirmed)
        assertEquals(0.20, feed.absences!!.limit, 1e-9)
        assertNull(feed.absences!!.modules.single().total)
        assertEquals("Micro", feed.flashcard!!.module)
        assertNull(feed.grades.single().mark)
        assertEquals("1 exam, absences of 1 module, a flashcard, 1 grade", StudyFeedParser.describe(feed))
    }

    @Test
    fun htmlNotesAndUnknownFieldsAreTolerated_brokenTextIsNot() {
        val html = "Written by the check-in<br>{&quot;exams&quot;:[{&quot;label&quot;:&quot;DS1&quot;,&quot;date&quot;:&quot;2026-12-01&quot;,&quot;confirmed&quot;:true}],&quot;later&quot;:1}"
        assertTrue(StudyFeedParser.parse(html)!!.exams.single().confirmed)
        assertNull(StudyFeedParser.parse("no json here"))
        assertNull(StudyFeedParser.parse("{\"exams\": [ {\"label\": }"))
        assertNull(StudyFeedParser.parse(null))
    }

    @Test
    fun absenceTubes() {
        val levels = AbsenceMeter.levels(
            FeedAbsences(
                limit = 0.20,
                modules = listOf(
                    FeedModule("A", absent = 1, total = 20), // 5 % of 20 % → 0.25
                    FeedModule("B", absent = 2, total = 15), // 13 % → 0.67
                    FeedModule("C", absent = 4, total = 20), // 20 % → 1.0
                    FeedModule("D", absent = 3, total = null),
                ),
            )
        )
        assertEquals(listOf("green", "amber", "red", "unknown"), levels.map { it.band })
        assertEquals(0.25, levels[0].fill!!, 1e-9)
        assertEquals(1.0, levels[2].fill!!, 1e-9)
        assertNull(levels[3].fill)
        assertEquals(3, levels[3].absent)
    }

    @Test
    fun theAverageWaitsForEveryCoefficient() {
        assertNull(Grades.average(listOf(FeedGrade("Stat Des", "DS", mark = null, coef = null))))
        assertNull(Grades.average(listOf(FeedGrade("A", mark = 12.0, coef = 2.0), FeedGrade("B", mark = 15.0, coef = null))))
        assertEquals(13.0, Grades.average(listOf(FeedGrade("A", mark = 12.0, coef = 2.0), FeedGrade("B", mark = 15.0, coef = 1.0), FeedGrade("C", mark = null, coef = null)))!!, 1e-9)
    }
}
