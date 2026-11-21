package dev.liquid.launcher.core.data

import java.text.Normalizer

/** What one item of the drawer list is, as far as rows and the letter index care. */
sealed interface IndexItem {
    /** A full-width section header; [entry] is its name in the index, or null for none. */
    data class Header(val entry: String?) : IndexItem

    /** An app cell; [letter] is its index letter, or null inside a section at the top. */
    data class Cell(val letter: String?) : IndexItem
}

/** One entry of the letter index: a section at the top or a letter of the alphabetical list. */
data class IndexEntry(val label: String, val firstItem: Int, val firstRow: Int, val section: Boolean)

/**
 * Rows and index entries of a drawer list (headers take a whole row, cells fill rows of a
 * fixed number of columns). Positions are continuous rows, so the list can follow a finger
 * between two letters instead of jumping from one to the next.
 */
class GridIndex private constructor(
    val entries: List<IndexEntry>,
    private val rowFirstItem: IntArray,
    private val rowHeader: BooleanArray,
    private val itemRow: IntArray,
) {
    val rowCount: Int get() = rowFirstItem.size

    fun firstItemOfRow(row: Int): Int = rowFirstItem[row.coerceIn(0, (rowCount - 1).coerceAtLeast(0))]

    fun isHeaderRow(row: Int): Boolean = rowHeader.getOrElse(row) { false }

    fun rowOfItem(item: Int): Int = itemRow.getOrElse(item) { (rowCount - 1).coerceAtLeast(0) }

    /**
     * The continuous row for an index position: [position] 0 is the centre of the first
     * entry, 1 the centre of the second. Between two centres the row moves linearly from one
     * entry's first row to the next one's.
     */
    fun rowAt(position: Float): Float {
        if (entries.isEmpty()) return 0f
        val c = position.coerceIn(0f, (entries.size - 1).toFloat())
        val i = c.toInt().coerceAtMost(entries.size - 1)
        val t = c - i
        val from = entries[i].firstRow.toFloat()
        val to = entries.getOrNull(i + 1)?.firstRow?.toFloat() ?: from
        return from + (to - from) * t
    }

    /** The entry whose part of the list contains [row]. */
    fun entryForRow(row: Float): Int {
        if (entries.isEmpty()) return -1
        var result = 0
        for (i in entries.indices) {
            if (entries[i].firstRow <= row + 0.001f) result = i else break
        }
        return result
    }

    companion object {
        val Empty = GridIndex(emptyList(), IntArray(0), BooleanArray(0), IntArray(0))

        fun build(items: List<IndexItem>, columns: Int): GridIndex {
            val cols = columns.coerceAtLeast(1)
            val rowFirst = ArrayList<Int>()
            val rowHeader = ArrayList<Boolean>()
            val itemRow = IntArray(items.size)
            val entries = ArrayList<IndexEntry>()
            var column = 0
            items.forEachIndexed { i, item ->
                when (item) {
                    is IndexItem.Header -> {
                        rowFirst += i
                        rowHeader += true
                        itemRow[i] = rowFirst.lastIndex
                        column = 0
                        item.entry?.let { entries += IndexEntry(it, i, rowFirst.lastIndex, section = true) }
                    }
                    is IndexItem.Cell -> {
                        if (column == 0) {
                            rowFirst += i
                            rowHeader += false
                        }
                        itemRow[i] = rowFirst.lastIndex
                        column = (column + 1) % cols
                        val letter = item.letter
                        val last = entries.lastOrNull()
                        if (letter != null && (last == null || last.section || last.label != letter)) {
                            entries += IndexEntry(letter, i, rowFirst.lastIndex, section = false)
                        }
                    }
                }
            }
            return GridIndex(entries, rowFirst.toIntArray(), rowHeader.toBooleanArray(), itemRow)
        }

        /** "Éclair" → "E", "4 Pics" → "#", "Ägypten" → "A". Letters of other scripts keep their own letter. */
        fun letterOf(label: String): String {
            val first = label.trimStart().firstOrNull() ?: return "#"
            val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).firstOrNull() ?: first
            return if (base.isLetter()) base.uppercaseChar().toString() else "#"
        }
    }
}
