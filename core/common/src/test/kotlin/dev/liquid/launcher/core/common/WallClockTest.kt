package dev.liquid.launcher.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WallClockTest {
    private fun at(h: Int, m: Int, s: Int = 0, ms: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 21, h, m, s)
        set(Calendar.MILLISECOND, ms)
    }.timeInMillis

    @Before
    fun setUp() {
        DebugLog.clear()
        DebugLog.enabled = true
        DebugLog.verbosity = LogLevel.DECISION
    }

    @Test
    fun aRefreshInTheSameMinutePublishesNothing() {
        WallClock.reset(at(10, 4, 0, 25))
        assertEquals(0L, WallClock.refresh("resume", now = at(10, 4, 37)))
        assertEquals(0L, WallClock.tick.value.serial)
        assertEquals(at(10, 4, 0, 25), WallClock.tick.value.millis)
    }

    @Test
    fun theMinuteTickIsOnTimeAndNotLogged() {
        WallClock.reset(at(10, 4, 0, 25))
        val late = WallClock.refresh("the minute tick", now = at(10, 5, 0, 40))
        assertEquals(40L, late)
        assertEquals(1L, WallClock.tick.value.serial)
        assertEquals(at(10, 5, 0, 40), WallClock.tick.value.millis)
        assertTrue(DebugLog.snapshot().none { it.tag == "Clock" })
    }

    /** Med's bug: the screen was off for hours; the uptime delay had 23 s left when it came back on. */
    @Test
    fun afterSleepTheScreenOnRefreshCatchesTheOldMinuteAndSaysHowFar() {
        WallClock.reset(at(10, 4, 0, 25))
        val late = WallClock.refresh("screen on", now = at(12, 31, 15))
        assertEquals((2 * 60 + 26) * 60_000L + 15_000L, late)
        assertEquals(at(12, 31, 15), WallClock.tick.value.millis)
        val line = DebugLog.snapshot().single { it.tag == "Clock" }.message
        assertEquals("the shown minute was 2 h 26 min behind (10:04 until 12:31); caught by screen on", line)
        // The backup delay that runs out later finds the minute current and changes nothing.
        assertEquals(0L, WallClock.refresh("backup", now = at(12, 31, 23)))
        assertEquals(1L, WallClock.tick.value.serial)
    }

    @Test
    fun aRefreshWhileTheScreenIsOffPublishesWithoutALogLine() {
        WallClock.reset(at(23, 58, 0, 25))
        WallClock.refresh("the minute tick", now = at(23, 59, 40), visible = false)
        assertEquals(at(23, 59, 40), WallClock.tick.value.millis)
        assertTrue(DebugLog.snapshot().none { it.tag == "Clock" })
    }

    @Test
    fun aClockSetBackOrAZoneChangePublishes() {
        WallClock.reset(at(10, 4, 30))
        // Set back to the previous minute: a new minute, nothing was late.
        assertEquals(0L, WallClock.refresh("time set", now = at(10, 3, 50), force = true))
        assertEquals(1L, WallClock.tick.value.serial)
        // A zone change keeps the instant's minute; forced, it is still published so local times are read again.
        assertEquals(0L, WallClock.refresh("time zone", now = at(10, 3, 51), force = true))
        assertEquals(2L, WallClock.tick.value.serial)
        // A minute earlier without force (the clock was corrected by the network) also publishes.
        WallClock.refresh("resume", now = at(10, 2, 10))
        assertEquals(3L, WallClock.tick.value.serial)
    }

    @Test
    fun latenessAndBackupDelay() {
        assertEquals(0L, WallClock.lateness(at(10, 4, 0), at(10, 4, 59, 999)))
        assertEquals(0L, WallClock.lateness(at(10, 4, 0), at(10, 5, 0)))
        assertEquals(1L, WallClock.lateness(at(10, 4, 0), at(10, 5, 0, 1)))
        assertEquals(0L, WallClock.lateness(at(10, 4, 0), at(10, 3, 0)))
        assertEquals(35L, WallClock.untilNextMinute(at(10, 4, 59, 990)))
        assertEquals(60_025L, WallClock.untilNextMinute(at(10, 5, 0, 0)))
    }

    @Test
    fun describeReadsNaturally() {
        assertEquals("450 ms", WallClock.describe(450))
        assertEquals("12 s", WallClock.describe(12_400))
        assertEquals("3 min 05 s", WallClock.describe(185_000))
        assertEquals("2 h 27 min", WallClock.describe((2 * 60 + 27) * 60_000L + 59_000))
    }

    @Test
    fun theLogTimesAreLocal() {
        val zone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Africa/Tunis"))
            WallClock.reset(at(8, 6, 0))
            WallClock.refresh("unlock", now = at(8, 9, 12))
            val line = DebugLog.snapshot().single { it.tag == "Clock" }.message
            assertTrue(line, line.contains("(08:06 until 08:09)"))
        } finally {
            TimeZone.setDefault(zone)
        }
    }
}
