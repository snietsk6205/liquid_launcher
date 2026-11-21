package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTimerTest {
    @Test
    fun goesBackWhenTheTimeIsUp_unlessChangedByHand() {
        val until = 1_000L
        assertNull(LayoutTimer.due(999L, until, "exam", "home", "exam"))
        assertEquals("home", LayoutTimer.due(1_000L, until, "exam", "home", "exam"))
        // Switched to another layout meanwhile: stays.
        assertNull(LayoutTimer.due(2_000L, until, "exam", "home", "study"))
        assertNull(LayoutTimer.due(2_000L, 0L, "exam", "home", "exam"))
    }

    @Test
    fun aTimerIsStaleOnceItRangOrAnotherLayoutIsOn() {
        assertFalse(LayoutTimer.stale(500L, 1_000L, "exam", "exam"))
        assertTrue(LayoutTimer.stale(1_000L, 1_000L, "exam", "exam"))
        assertTrue(LayoutTimer.stale(500L, 1_000L, "exam", "home"))
        assertFalse(LayoutTimer.stale(500L, 0L, "exam", "home"))
    }
}
