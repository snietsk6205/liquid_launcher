package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SchoolYearTest {
    private val year = SchoolYear.ESSAI_2026_27
    private fun d(s: String) = LocalDate.parse(s)
    private fun byId(list: List<SchoolDate>, id: String) = list.first { it.id == id }

    @Test
    fun theBuiltInYearIsMedsListInOrderAndNothingIsPinned() {
        assertEquals(14, year.size)
        assertEquals(year, SchoolYear.sorted(year))
        assertTrue(year.none { it.pinned })
        assertTrue(byId(year, "essai27.winter").approx)
        val retakes = byId(year, "essai27.retakes")
        assertEquals(d("2027-05-12"), retakes.endDate)
        assertTrue(retakes.covers(d("2027-05-11")))
        assertFalse(retakes.covers(d("2027-05-13")))
        // Empty or broken text is the built-in year; an emptied list stays empty.
        assertEquals(year, SchoolYear.parse(""))
        assertEquals(year, SchoolYear.parse("{broken"))
        assertEquals(emptyList<SchoolDate>(), SchoolYear.parse(SchoolYear.encode(emptyList())))
        assertEquals(year, SchoolYear.parse(SchoolYear.encode(year)))
    }

    @Test
    fun countdownsSayHowLongAndAboutWhenTheSchoolSaysAbout() {
        val ds = byId(year, "essai27.p2exams")
        assertEquals("DS P2 in 5 days", SchoolYear.countdown(ds, d("2027-01-02")))
        assertEquals("DS P2 tomorrow", SchoolYear.countdown(ds, d("2027-01-06")))
        assertEquals("DS P2 today", SchoolYear.countdown(ds, d("2027-01-07")))
        assertNull(SchoolYear.countdown(ds, d("2027-01-08")))
        assertEquals("DS P2 in 44 days", SchoolYear.countdown(ds, d("2026-11-24")))
        assertEquals("Examens SC in 28 weeks", SchoolYear.countdown(byId(year, "essai27.examsSC"), d("2026-11-24")))
        assertEquals("Winter break in ≈ 32 days", SchoolYear.countdown(byId(year, "essai27.winter"), d("2026-11-24")))
        assertEquals("Rattrapages · day 2 of 3", SchoolYear.countdown(byId(year, "essai27.retakes"), d("2027-05-11")))
        // Exams turn amber within two weeks and red within three days; a break never does.
        assertEquals(DayLine.Tone.Plain, SchoolYear.tone(ds, d("2026-12-20")))
        assertEquals(DayLine.Tone.Soon, SchoolYear.tone(ds, d("2026-12-30")))
        assertEquals(DayLine.Tone.Urgent, SchoolYear.tone(ds, d("2027-01-05")))
        assertEquals(DayLine.Tone.Plain, SchoolYear.tone(byId(year, "essai27.winter"), d("2026-12-25")))
    }

    @Test
    fun thePinnedCountdownIsTheFirstPinnedDateNotOverYet() {
        assertNull(SchoolYear.pinnedFact(year, d("2026-11-24")))
        val pinned = SchoolYear.togglePin(SchoolYear.togglePin(year, "essai27.p3exams"), "essai27.p2exams")
        val fact = SchoolYear.pinnedFact(pinned, d("2027-01-02"))!!
        assertEquals(DayLine.Pinned(d("2027-01-07"), "DS P2 in 5 days", "DS P2", DayLine.Tone.Soon), fact)
        // Once DS P2 is over, DS P3 takes its place.
        assertEquals("DS P3 in 51 days", SchoolYear.pinnedFact(pinned, d("2027-01-14"))!!.text)
        assertEquals(1, SchoolYear.onDay(year, d("2027-01-07")).size)
    }

    @Test
    fun editsMoveDeleteAndRestoreTheBuiltInDates() {
        val moved = SchoolYear.move(year, "essai27.retakes", 1)
        assertEquals("2027-05-11", byId(moved, "essai27.retakes").start)
        assertEquals("2027-05-13", byId(moved, "essai27.retakes").end)
        assertEquals("", byId(SchoolYear.move(year, "essai27.p2exams", -1), "essai27.p2exams").end)
        val own = SchoolDate(SchoolYear.newId(year, 42L), "Projet tutoré", start = "2027-04-01")
        val edited = SchoolYear.put(SchoolYear.remove(SchoolYear.togglePin(moved, "essai27.p4exams"), "essai27.delibSC"), own)
        assertEquals(14, edited.size)
        assertFalse(edited.any { it.id == "essai27.delibSC" })
        val restored = SchoolYear.restoreBuiltIn(edited)
        assertEquals(15, restored.size)
        assertEquals("2027-05-10", byId(restored, "essai27.retakes").start)
        assertTrue(byId(restored, "essai27.p4exams").pinned)
        assertTrue(restored.any { it.id == own.id })
    }
}
