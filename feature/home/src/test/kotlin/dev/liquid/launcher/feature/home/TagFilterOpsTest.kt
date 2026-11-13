package dev.liquid.launcher.feature.home

import dev.liquid.launcher.core.data.Organisation
import dev.liquid.launcher.core.data.Tag
import dev.liquid.launcher.core.data.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagFilterOpsTest {
    private val anime = Tag("a", "Anime")
    private val games = Tag("g", "Games")
    private val study = Tag("s", "Study")
    private val tags = listOf(anime, games, study)

    @Test
    fun anyAndAll() {
        assertTrue(TagFilterOps.matches(listOf("a"), emptySet(), "any"))
        assertTrue(TagFilterOps.matches(listOf("a"), setOf("a", "g"), "any"))
        assertFalse(TagFilterOps.matches(listOf("a"), setOf("a", "g"), "all"))
        assertTrue(TagFilterOps.matches(listOf("a", "g", "s"), setOf("a", "g"), "all"))
        assertFalse(TagFilterOps.matches(emptyList(), setOf("a"), "any"))
    }

    @Test
    fun pinnedFirstThenMostUsed() {
        val now = 10L * 86_400_000
        val usage = mapOf("tag:s" to Usage(count = 9, last = now), "tag:a" to Usage(count = 1, last = now))
        val counts = mapOf("a" to 3, "g" to 2, "s" to 4)
        val order = TagFilterOps.ordered(tags, listOf("g"), "used", usage, counts, now)
        assertEquals(listOf("g", "s", "a"), order.map { it.id })
        val az = TagFilterOps.ordered(tags, emptyList(), "az", usage, counts, now)
        assertEquals(listOf("a", "g", "s"), az.map { it.id })
        assertEquals(listOf("g"), TagFilterOps.ordered(tags, listOf("g"), "pinned", usage, counts, now).map { it.id })
    }

    @Test
    fun emptyTagsGoLast() {
        val counts = mapOf("a" to 0, "g" to 2, "s" to 1)
        val order = TagFilterOps.ordered(tags, emptyList(), "az", emptyMap(), counts, 0)
        assertEquals(listOf("g", "s", "a"), order.map { it.id })
    }

    @Test
    fun swipeStepsAndFallsOffTheEnds() {
        val order = listOf("a", "g", "s")
        assertEquals(setOf("a"), TagFilterOps.step(order, emptySet(), 1))
        assertEquals(setOf("s"), TagFilterOps.step(order, emptySet(), -1))
        assertEquals(setOf("g"), TagFilterOps.step(order, setOf("a"), 1))
        assertEquals(emptySet<String>(), TagFilterOps.step(order, setOf("s"), 1))
        assertEquals(emptySet<String>(), TagFilterOps.step(order, setOf("a"), -1))
        // Several tags selected: a swipe starts again from the first.
        assertEquals(setOf("a"), TagFilterOps.step(order, setOf("a", "g"), 1))
    }

    @Test
    fun labels() {
        val org = Organisation(tags = tags)
        assertEquals("Anime", TagFilterOps.label(setOf("a"), org))
        assertEquals("", TagFilterOps.label(emptySet(), org))
        assertTrue(TagFilterOps.label(setOf("a", "g"), org).endsWith(" + 1"))
    }
}
