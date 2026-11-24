package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidaysTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun hijriDatesComeFromTheUmmAlQuraTables() {
        assertEquals(Hijri.Date(1448, 6, 14), Hijri.of(d("2026-11-24")))
        assertEquals("14 Jumada al-Akhira 1448", Hijri.format(Hijri.of(d("2026-11-24"))!!))
        assertEquals(d("2027-03-09"), Hijri.toGregorian(1448, 10, 1))
        // Tunisia a day behind the tables: the Islamic dates move a day later.
        assertEquals(d("2027-03-10"), Hijri.toGregorian(1448, 10, 1, shift = 1))
        assertEquals(Hijri.Date(1448, 10, 1), Hijri.of(d("2027-03-10"), shift = 1))
    }

    @Test
    fun tunisiasHolidaysOf2026() {
        val all = TunisiaHolidays.between(d("2026-01-01"), d("2026-12-31"))
        val names = all.map { it.date.toString() + " " + it.name + if (it.dayNumber > 1) " 2" else "" }
        assertEquals(
            listOf(
                "2026-01-01 New Year's Day", "2026-03-20 Aïd el-Fitr", "2026-03-20 Independence Day", "2026-03-21 Aïd el-Fitr 2",
                "2026-04-09 Martyrs' Day", "2026-05-01 Labour Day", "2026-05-27 Aïd el-Idha", "2026-05-28 Aïd el-Idha 2",
                "2026-06-16 Ras el Am el Hijri", "2026-07-25 Republic Day", "2026-08-13 Women's Day", "2026-08-25 Mouled",
                "2026-10-15 Evacuation Day", "2026-12-17 Revolution Day",
            ),
            names,
        )
        // 14 January is no longer a holiday (decree of 2021); the Islamic ones are estimates.
        assertFalse(all.any { it.date == d("2026-01-14") })
        assertTrue(all.filter { it.islamic }.all { it.label.startsWith("≈ ") })
        assertEquals("Revolution Day", all.last().label)
    }

    @Test
    fun aHolidayCanBeMovedOrHiddenAndIsThenNoLongerAnEstimate() {
        val fitr = TunisiaHolidays.onDay(d("2027-03-09")).single()
        assertEquals("fitr.1448", fitr.id)
        val moved = TunisiaHolidays.move(emptyMap(), fitr, 1)
        val after = TunisiaHolidays.between(d("2027-03-08"), d("2027-03-12"), changes = moved)
        assertEquals(listOf(d("2027-03-10"), d("2027-03-11")), after.map { it.date })
        assertTrue(after.all { it.confirmed && !it.label.startsWith("≈") })
        assertEquals("Aïd el-Fitr (day 2)", after[1].label)
        // Moving from its second day moves the whole holiday.
        assertEquals(moved, TunisiaHolidays.move(emptyMap(), TunisiaHolidays.onDay(d("2027-03-10"))[0], 1))
        val hidden = TunisiaHolidays.hide(moved, fitr)
        assertTrue(TunisiaHolidays.between(d("2027-03-08"), d("2027-03-12"), changes = hidden).isEmpty())
        assertEquals(emptyMap<String, String>(), TunisiaHolidays.reset(hidden, "fitr.1448"))
        assertEquals(hidden, TunisiaHolidays.parseChanges(TunisiaHolidays.encodeChanges(hidden)))
        assertEquals("", TunisiaHolidays.encodeChanges(emptyMap()))
    }
}
