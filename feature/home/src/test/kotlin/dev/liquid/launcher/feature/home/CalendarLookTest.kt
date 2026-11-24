package dev.liquid.launcher.feature.home

import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** The calendar's shared month (0.16.0, D211): where days fall, the widget's own look, the notes of each day. */
class CalendarLookTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun theMonthStartsOnTheChosenFirstDay() {
        val store = SettingsStore.inMemory()
        val monday = store.calendarLook(widget = false)
        assertEquals(DayOfWeek.MONDAY, monday.firstDay)
        // November 2026 starts on a Sunday: six empty days before it from Monday, six weeks; none from Sunday, five.
        val november = YearMonth.of(2026, 11)
        assertEquals(6, monday.lead(november))
        assertEquals(6, monday.weeks(november))
        val sunday = monday.copy(firstDay = DayOfWeek.SUNDAY)
        assertEquals(0, sunday.lead(november))
        assertEquals(5, sunday.weeks(november))
        assertEquals(d("2026-11-23"), monday.weekOf(d("2026-11-24")))
        assertEquals(d("2026-11-22"), sunday.weekOf(d("2026-11-24")))
        assertEquals(DayOfWeek.MONDAY, monday.weekDays().first())
    }

    @Test
    fun theWidgetKeepsItsOwnLookUntilItSharesTheCalendars() {
        val store = SettingsStore.inMemory()
        store.set(LauncherSettings.CalendarMarks, "bars")
        store.set(LauncherSettings.CalendarWeekNumbers, true)
        val own = store.calendarLook(widget = true)
        // As before 0.16.0: dots, a filled today, no week numbers, no holidays or school dates, every calendar.
        assertEquals("dots", own.marks)
        assertEquals("filled", own.today)
        assertFalse(own.weekNumbers)
        assertFalse(own.holidays)
        assertFalse(own.school)
        assertTrue(own.hidden.isEmpty())
        store.set(LauncherSettings.CalendarShareWidget, true)
        val shared = store.calendarLook(widget = true)
        assertEquals("bars", shared.marks)
        assertTrue(shared.weekNumbers)
        assertTrue(shared.holidays)
        // The widget's size is its own: the calendar's density does not change it.
        store.set(LauncherSettings.CalendarDensity, "roomy")
        assertEquals("normal", store.calendarLook(widget = true).density)
        assertEquals("roomy", store.calendarLook(widget = false).density)
    }

    @Test
    fun eachDayHasItsHolidaysAndSchoolDates() {
        val store = SettingsStore.inMemory()
        val look = store.calendarLook(widget = false)
        val notes = dayNotes(store, look, d("2027-01-01"), d("2027-01-31"))
        assertEquals("New Year's Day", notes.getValue(d("2027-01-01")).holidays.single().name)
        assertEquals("DS et Examens P2", notes.getValue(d("2027-01-07")).school.single().title)
        assertTrue(d("2027-01-02") !in notes)
        // Off: nothing.
        assertTrue(dayNotes(store, look.copy(holidays = false, school = false), d("2027-01-01"), d("2027-01-31")).isEmpty())
        // The retakes are on each of their three days.
        val may = dayNotes(store, look, d("2027-05-01"), d("2027-05-31"))
        assertEquals(3, may.count { (_, n) -> n.school.any { it.id == "essai27.retakes" } })
    }
}
