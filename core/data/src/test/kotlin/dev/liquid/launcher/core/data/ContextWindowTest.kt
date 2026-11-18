package dev.liquid.launcher.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ContextWindowTest {
    // 2026-11-16 is a Monday, 2026-11-21 a Saturday.
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)

    @Test
    fun aMorningOnWeekdays() {
        assertTrue(ContextLayouts.inWindow("08:00", "12:00", "weekdays", at(16, 8)))
        assertTrue(ContextLayouts.inWindow("08:00", "12:00", "weekdays", at(16, 11, 59)))
        assertFalse(ContextLayouts.inWindow("08:00", "12:00", "weekdays", at(16, 12)))
        assertFalse(ContextLayouts.inWindow("08:00", "12:00", "weekdays", at(21, 9)))
        assertTrue(ContextLayouts.inWindow("08:00", "12:00", "weekend", at(21, 9)))
        assertTrue(ContextLayouts.inWindow("08:00", "12:00", "every", at(21, 9)))
    }

    @Test
    fun overMidnightBelongsToTheDayItStarted() {
        // Friday night into Saturday: still Friday's (a weekday's) window.
        assertTrue(ContextLayouts.inWindow("22:00", "06:00", "weekdays", at(21, 1)))
        assertTrue(ContextLayouts.inWindow("22:00", "06:00", "weekdays", at(20, 23)))
        assertFalse(ContextLayouts.inWindow("22:00", "06:00", "weekdays", at(21, 23)))
        assertFalse(ContextLayouts.inWindow("22:00", "06:00", "every", at(20, 12)))
        assertFalse(ContextLayouts.inWindow("08:00", "08:00", "every", at(20, 8)))
    }
}
