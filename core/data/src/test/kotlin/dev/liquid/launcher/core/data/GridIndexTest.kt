package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GridIndexTest {
    private fun cells(vararg labels: String) = labels.map { IndexItem.Cell(GridIndex.letterOf(it)) }

    @Test
    fun lettersGroupAndRowsFillColumns() {
        val index = GridIndex.build(cells("Alpha", "Atlas", "Bank", "Camera", "Chrome", "Clock", "Zoom"), columns = 3)
        assertEquals(listOf("A", "B", "C", "Z"), index.entries.map { it.label })
        assertEquals(listOf(0, 0, 1, 2), index.entries.map { it.firstRow })
        assertEquals(3, index.rowCount)
        assertEquals(3, index.firstItemOfRow(1))
    }

    @Test
    fun positionsMoveContinuouslyBetweenLetters() {
        val index = GridIndex.build(cells("A1", "A2", "A3", "A4", "A5", "A6", "B1", "C1"), columns = 2)
        // A starts at row 0, B at row 3, C at row 3 (same row as B).
        assertEquals(0f, index.rowAt(0f), 0.001f)
        assertEquals(1.5f, index.rowAt(0.5f), 0.001f)
        assertEquals(3f, index.rowAt(1f), 0.001f)
        assertEquals(3f, index.rowAt(5f), 0.001f)
        assertEquals(0, index.entryForRow(2.9f))
        assertEquals(2, index.entryForRow(3f))
    }

    @Test
    fun sectionsComeFirstAndHeadersTakeARow() {
        val items = listOf(
            IndexItem.Header("Favourites"), IndexItem.Cell(null), IndexItem.Cell(null),
            IndexItem.Header(null),
        ) + cells("Alpha", "Bank")
        val index = GridIndex.build(items, columns = 4)
        assertEquals(listOf("Favourites", "A", "B"), index.entries.map { it.label })
        assertEquals(true, index.entries[0].section)
        assertEquals(listOf(0, 3, 3), index.entries.map { it.firstRow })
        assertEquals(true, index.isHeaderRow(2))
        assertEquals(4, index.rowCount)
    }

    @Test
    fun lettersAreNormalised() {
        assertEquals("E", GridIndex.letterOf("éclair"))
        assertEquals("#", GridIndex.letterOf("4 Pics"))
        assertEquals("#", GridIndex.letterOf(""))
    }
}
