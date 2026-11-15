package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Other apps' widgets on the home screen (0.7.0, D100): their size in cells and which ids the layouts use. */
class AppWidgetsTest {
    @Test
    fun aWidgetTakesItsOwnCellSize_elseItsMinimumOverTheCellSize() {
        // Android 12+ widgets say how many cells they want.
        assertEquals(4 to 2, AppWidgets.cells(4, 2, 100, 100, 270f, 400f, 4, 6))
        // Older ones give a minimum size in pixels: 2.2 cells wide is 3 cells.
        assertEquals(3 to 1, AppWidgets.cells(0, 0, 600, 300, 270f, 400f, 4, 6))
        // Never larger than the grid, never smaller than one cell.
        assertEquals(4 to 6, AppWidgets.cells(9, 9, 0, 0, 270f, 400f, 4, 6))
        assertEquals(1 to 1, AppWidgets.cells(0, 0, 0, 0, 270f, 400f, 4, 6))
    }

    @Test
    fun theIdsInUseComeFromPagesAndDock_onlyFromAppWidgets() {
        fun widget(id: String, appWidgetId: Int?) = HomeItem(
            id, ItemKind.WIDGET, 0, 0, 4, 2, widget = if (appWidgetId != null) "app" else "clock",
            options = appWidgetId?.let { mapOf("appWidgetId" to it.toString()) },
        )
        val layout = HomeLayout(
            pages = listOf(
                HomePage("p1", items = listOf(widget("a", 12), widget("b", null))),
                HomePage("p2", items = listOf(widget("c", 31))),
            ),
            dock = listOf(widget("d", 7)),
        )
        assertEquals(setOf(12, 31, 7), AppWidgets.idsIn(layout))
        assertEquals(null, AppWidgets.idOf(widget("e", null)))
    }
}
