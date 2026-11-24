package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.data.RotationRules.Moment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RotationRulesTest {
    private val zone = ZoneId.of("Africa/Tunis")
    private fun at(day: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun timesAreReadForgivingly() {
        assertEquals(listOf(7 * 60, 12 * 60 + 30, 22 * 60), RotationRules.parseTimes("22:00, 07:00; 12h30 25:00 x 7:00"))
        assertTrue(RotationRules.parseTimes("").isEmpty())
    }

    @Test
    fun unlockAndDailyKeepToTheScreenTurningOff() {
        assertTrue(RotationRules.due("unlock", Moment.SCREEN_OFF, at(23, 9, 0), at(23, 9, 5), 30, emptyList(), zone))
        assertFalse(RotationRules.due("unlock", Moment.AWAY, at(23, 9, 0), at(23, 9, 5), 30, emptyList(), zone))
        assertFalse(RotationRules.due("daily", Moment.SCREEN_OFF, at(23, 8, 0), at(23, 23, 0), 30, emptyList(), zone))
        assertTrue(RotationRules.due("daily", Moment.SCREEN_OFF, at(22, 23, 0), at(23, 0, 10), 30, emptyList(), zone))
        assertFalse(RotationRules.due("daily", Moment.AWAY, at(22, 23, 0), at(23, 0, 10), 30, emptyList(), zone))
        assertFalse(RotationRules.due("off", Moment.SCREEN_OFF, 0, at(23, 9, 0), 30, emptyList(), zone))
    }

    @Test
    fun everyFewMinutes() {
        assertFalse(RotationRules.due("interval", Moment.AWAY, at(23, 9, 0), at(23, 9, 29), 30, emptyList(), zone))
        assertTrue(RotationRules.due("interval", Moment.AWAY, at(23, 9, 0), at(23, 9, 30), 30, emptyList(), zone))
        assertTrue(RotationRules.due("interval", Moment.SCREEN_OFF, 0, at(23, 9, 0), 30, emptyList(), zone))
    }

    @Test
    fun atSetTimes() {
        val times = RotationRules.parseTimes("07:00, 12:00, 18:00, 22:00")
        // Changed at 12:05: nothing more until 18:00.
        assertFalse(RotationRules.due("times", Moment.AWAY, at(23, 12, 5), at(23, 17, 59), 30, times, zone))
        assertTrue(RotationRules.due("times", Moment.AWAY, at(23, 12, 5), at(23, 18, 0), 30, times, zone))
        // Before the first time of the day, the last one was yesterday's 22:00.
        assertEquals(at(22, 22, 0), RotationRules.lastSetTime(at(23, 6, 30), times, zone))
        assertTrue(RotationRules.due("times", Moment.SCREEN_OFF, at(22, 21, 0), at(23, 6, 30), 30, times, zone))
        assertFalse(RotationRules.due("times", Moment.SCREEN_OFF, at(22, 22, 30), at(23, 6, 30), 30, times, zone))
        assertFalse(RotationRules.due("times", Moment.AWAY, 0, at(23, 9, 0), 30, emptyList(), zone))
    }
}
