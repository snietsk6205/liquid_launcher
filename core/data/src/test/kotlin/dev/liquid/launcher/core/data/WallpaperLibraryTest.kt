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
}
