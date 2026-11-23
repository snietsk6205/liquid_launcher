package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.common.CropSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperLibraryTest {
    private fun item(id: String, added: Long = 1, lastUsed: Long = 0, favourite: Boolean = false) =
        WallpaperItem(id = id, file = "$id.jpg", added = added, lastUsed = lastUsed, favourite = favourite)

    @Test
    fun samePictureIsKeptOnce() {
        var s = WallpaperLibraryState()
        s = WallpaperLibraryOps.add(s, item("a"))
        s = WallpaperLibraryOps.add(s, item("a", added = 5))
        assertEquals(1, s.items.size)
        assertEquals(1L, s.items[0].added)
    }

    @Test
    fun recentHasEachPictureOnceNewestFirst() {
        var s = WallpaperLibraryState(items = listOf(item("a"), item("b"), item("c")))
        s = WallpaperLibraryOps.markUsed(s, "a", 10, null)
        s = WallpaperLibraryOps.markUsed(s, "b", 20, null)
        s = WallpaperLibraryOps.markUsed(s, "a", 30, CropSpec(0.3f, 0.5f, 2f))
        val recent = WallpaperLibraryOps.recent(s)
        assertEquals(listOf("a", "b"), recent.map { it.id })
        assertEquals("a", s.current)
        assertEquals(2f, recent[0].zoom, 0.001f)
    }

    @Test
    fun forgetLeavesTheLibrary() {
        var s = WallpaperLibraryState(items = listOf(item("a", lastUsed = 5)))
        s = WallpaperLibraryOps.forget(s, "a")
        assertTrue(WallpaperLibraryOps.recent(s).isEmpty())
        assertEquals(1, s.items.size)
        s = WallpaperLibraryOps.remove(s, "a")
        assertTrue(s.items.isEmpty())
    }

    @Test
    fun nextInPoolInOrderAndShuffledWithoutRepeat() {
        assertEquals(0, WallpaperLibraryOps.nextInPool(3, 2, false) { 0 })
        assertEquals(1, WallpaperLibraryOps.nextInPool(3, 0, false) { 0 })
        // Shuffle never picks the current one again.
        for (r in 0 until 2) {
            val pick = WallpaperLibraryOps.nextInPool(3, 1, true) { r }
            assertTrue(pick != 1)
        }
        assertEquals(-1, WallpaperLibraryOps.nextInPool(1, 0, true) { 0 })
        assertEquals(0, WallpaperLibraryOps.nextInPool(1, -1, true) { 0 })
        assertEquals(-1, WallpaperLibraryOps.nextInPool(0, -1, true) { 0 })
    }

    @Test
    fun idDependsOnContent() {
        val a = WallpaperLibraryOps.idOf(byteArrayOf(1, 2, 3))
        val b = WallpaperLibraryOps.idOf(byteArrayOf(1, 2, 3))
        val c = WallpaperLibraryOps.idOf(byteArrayOf(1, 2, 4))
        assertEquals(a, b)
        assertTrue(a != c)
        assertEquals(20, a.length)
    }

    // ---- 0.16.0 ----

    @Test
    fun usesAreCountedForMostUsed() {
        var s = WallpaperLibraryState(items = listOf(item("a", added = 1), item("b", added = 2), item("c", added = 3)))
        s = WallpaperLibraryOps.markUsed(s, "a", 10, null)
        s = WallpaperLibraryOps.markUsed(s, "a", 20, null)
        s = WallpaperLibraryOps.markUsed(s, "b", 30, null)
        assertEquals(2, s.items.first { it.id == "a" }.uses)
        assertEquals(listOf("a", "b", "c"), WallpaperLibraryOps.sorted(s.items, LibrarySort.USED).map { it.id })
        // Newest: last added first, or last used first in Recent.
        assertEquals(listOf("c", "b", "a"), WallpaperLibraryOps.sorted(s.items, LibrarySort.NEWEST).map { it.id })
        assertEquals(listOf("b", "a"), WallpaperLibraryOps.sorted(WallpaperLibraryOps.recent(s), LibrarySort.NEWEST, recentList = true).map { it.id })
    }

    @Test
    fun coloursGoIntoFamilies() {
        assertEquals(ColourFamily.RED, WallpaperLibraryOps.familyOf(0xFFD32F2F.toInt()))
        assertEquals(ColourFamily.BLUE, WallpaperLibraryOps.familyOf(0xFF1565C0.toInt()))
        assertEquals(ColourFamily.GREEN, WallpaperLibraryOps.familyOf(0xFF2E7D32.toInt()))
        assertEquals(ColourFamily.ORANGE, WallpaperLibraryOps.familyOf(0xFFEF6C00.toInt()))
        assertEquals(ColourFamily.PURPLE, WallpaperLibraryOps.familyOf(0xFF6A1B9A.toInt()))
        assertEquals(ColourFamily.PINK, WallpaperLibraryOps.familyOf(0xFFC2185B.toInt()))
        // Washed out or very dark: grey.
        assertEquals(ColourFamily.GREY, WallpaperLibraryOps.familyOf(0xFF808080.toInt()))
        assertEquals(ColourFamily.GREY, WallpaperLibraryOps.familyOf(0xFF101820.toInt()))
        assertEquals(ColourFamily.GREY, WallpaperLibraryOps.familyOf(0xFFF5F5F5.toInt()))
    }

    @Test
    fun colourSortGoesRoundTheHuesThenGreys() {
        val items = listOf(
            item("grey", added = 1).copy(colour = 0xFF777777.toInt()),
            item("blue", added = 2).copy(colour = 0xFF1E88E5.toInt()),
            item("unknown", added = 3),
            item("red", added = 4).copy(colour = 0xFFE53935.toInt()),
            item("green", added = 5).copy(colour = 0xFF43A047.toInt()),
        )
        assertEquals(listOf("red", "green", "blue", "grey", "unknown"), WallpaperLibraryOps.sorted(items, LibrarySort.COLOUR).map { it.id })
    }

    @Test
    fun filtersByColourAndShape() {
        val items = listOf(
            item("tallBlue").copy(colour = 0xFF1E88E5.toInt(), w = 1080, h = 2400),
            item("wideBlue").copy(colour = 0xFF1E88E5.toInt(), w = 4000, h = 3000),
            item("tallRed").copy(colour = 0xFFE53935.toInt(), w = 1080, h = 2400),
            item("unknown"),
        )
        assertEquals(4, WallpaperLibraryOps.filtered(items, null, null).size)
        assertEquals(listOf("tallBlue", "wideBlue"), WallpaperLibraryOps.filtered(items, ColourFamily.BLUE, null).map { it.id })
        assertEquals(listOf("tallBlue", "tallRed"), WallpaperLibraryOps.filtered(items, null, PictureShape.PORTRAIT).map { it.id })
        assertEquals(listOf("wideBlue"), WallpaperLibraryOps.filtered(items, ColourFamily.BLUE, PictureShape.LANDSCAPE).map { it.id })
        assertEquals(PictureShape.SQUARE, WallpaperLibraryOps.shapeOf(1000, 1020))
        assertEquals(null, WallpaperLibraryOps.shapeOf(0, 0))
    }

    @Test
    fun collectionsHoldPicturesAndLeaveThemWhenRemoved() {
        var s = WallpaperLibraryState(items = listOf(item("a", added = 1), item("b", added = 2), item("c", added = 3)))
        s = WallpaperLibraryOps.addCollection(s, WallpaperCollection("study", "Study"))
        s = WallpaperLibraryOps.addToCollection(s, setOf("a", "c"), "study")
        // Adding twice keeps one membership; an unknown collection changes nothing.
        s = WallpaperLibraryOps.addToCollection(s, setOf("a"), "study")
        s = WallpaperLibraryOps.addToCollection(s, setOf("b"), "nope")
        assertEquals(listOf("c", "a"), WallpaperLibraryOps.inCollection(s, "study").map { it.id })
        assertEquals(listOf("study"), s.items.first { it.id == "a" }.collections)
        s = WallpaperLibraryOps.renameCollection(s, "study", "ESSAI")
        assertEquals("ESSAI", s.collections.single().name)
        s = WallpaperLibraryOps.removeFromCollection(s, setOf("c"), "study")
        assertEquals(listOf("a"), WallpaperLibraryOps.inCollection(s, "study").map { it.id })
        s = WallpaperLibraryOps.removeCollection(s, "study")
        assertTrue(s.collections.isEmpty())
        assertEquals(3, s.items.size)
        assertTrue(s.items.all { it.collections.isEmpty() })
    }

    @Test
    fun severalAtOnceAndUndoOfADelete() {
        var s = WallpaperLibraryState(items = listOf(item("a", lastUsed = 5), item("b", lastUsed = 6), item("c")), current = "b")
        s = WallpaperLibraryOps.setFavourite(s, setOf("a", "c"), true)
        assertEquals(listOf("a", "c"), s.items.filter { it.favourite }.map { it.id })
        s = WallpaperLibraryOps.forget(s, setOf("a", "b"))
        assertTrue(WallpaperLibraryOps.recent(s).isEmpty())
        val before = s
        val taken = s.items.filter { it.id in setOf("b", "c") }
        s = WallpaperLibraryOps.removeAll(s, setOf("b", "c"))
        assertEquals(listOf("a"), s.items.map { it.id })
        assertEquals(null, s.current)
        s = WallpaperLibraryOps.restore(s, taken, "b")
        assertEquals(before.items.map { it.id }.toSet(), s.items.map { it.id }.toSet())
        assertEquals("b", s.current)
    }

    @Test
    fun undoOfAnApplyGivesBackThePlacementButKeepsNewFavourites() {
        var s = WallpaperLibraryState(items = listOf(item("a").copy(cx = 0.2f, uses = 3)))
        val before = s.items.single()
        s = WallpaperLibraryOps.markUsed(s, "a", 50, CropSpec(0.8f, 0.5f, 1.4f))
        s = WallpaperLibraryOps.setFavourite(s, "a", true)
        s = WallpaperLibraryOps.restoreItem(s, before)
        val a = s.items.single()
        assertEquals(0.2f, a.cx, 0.001f)
        assertEquals(3, a.uses)
        assertTrue(a.favourite)
    }

    @Test
    fun eachLayoutKeepsItsOwnRecents() {
        var s = WallpaperLibraryState(items = listOf(item("a", lastUsed = 3), item("b", lastUsed = 2), item("c", lastUsed = 1), item("d")))
        // Every layout starts with today's recents (newest first, as many as kept).
        s = WallpaperLibraryOps.seedRecents(s, listOf("study", "projects"), keep = 2)
        assertEquals(listOf("a", "b"), WallpaperLibraryOps.recentFor(s, "study").map { it.id })
        s = WallpaperLibraryOps.pushRecent(s, "study", "d", keep = 2)
        assertEquals(listOf("d", "a"), WallpaperLibraryOps.recentFor(s, "study").map { it.id })
        assertEquals(listOf("a", "b"), WallpaperLibraryOps.recentFor(s, "projects").map { it.id })
        // Using one again moves it to the front, once.
        s = WallpaperLibraryOps.pushRecent(s, "study", "a", keep = 2)
        assertEquals(listOf("a", "d"), WallpaperLibraryOps.recentFor(s, "study").map { it.id })
        s = WallpaperLibraryOps.forgetFor(s, "study", setOf("a"))
        assertEquals(listOf("d"), WallpaperLibraryOps.recentFor(s, "study").map { it.id })
        // Seeding again leaves a layout's own list alone; a deleted picture drops out of the list shown.
        s = WallpaperLibraryOps.seedRecents(s, listOf("study"), keep = 2)
        assertEquals(listOf("d"), WallpaperLibraryOps.recentFor(s, "study").map { it.id })
        s = WallpaperLibraryOps.removeAll(s, setOf("d"))
        assertTrue(WallpaperLibraryOps.recentFor(s, "study").isEmpty())
    }
}
