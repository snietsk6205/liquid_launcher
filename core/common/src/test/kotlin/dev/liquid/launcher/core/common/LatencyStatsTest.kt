package dev.liquid.launcher.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatencyStatsTest {
    @Test
    fun emptyHasNoPercentiles() {
        val stats = LatencyStats()
        assertNull(stats.median())
        assertEquals("no samples", stats.summary())
    }

    @Test
    fun medianAndP95() {
        val stats = LatencyStats()
        (1..100).forEach { stats.add(it.toLong()) }
        assertEquals(51L, stats.median())
        assertEquals(95L, stats.p95())
    }

    @Test
    fun keepsOnlyMostRecentSamples() {
        val stats = LatencyStats(capacity = 3)
        listOf(100L, 1L, 2L, 3L).forEach { stats.add(it) }
        assertEquals(3, stats.count)
        assertEquals(2L, stats.median())
    }
}
