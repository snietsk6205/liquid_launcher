package dev.liquid.launcher.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/** Pure layout arithmetic, kept free of Android so it runs in unit tests. */
object LayoutOps {
    fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

    fun defaultLayout(columns: Int, rows: Int): HomeLayout {
        val clock = HomeItem(
            id = newId(), kind = ItemKind.WIDGET, x = 0, y = 0,
            w = columns, h = minOf(3, rows), widget = "clock",
        )
        return HomeLayout(pages = listOf(HomePage(id = newId(), items = listOf(clock))))
    }

    fun inBounds(x: Int, y: Int, w: Int, h: Int, columns: Int, rows: Int): Boolean =
        x >= 0 && y >= 0 && x + w <= columns && y + h <= rows

    fun isFree(page: HomePage, x: Int, y: Int, w: Int, h: Int, columns: Int, rows: Int, ignoreId: String? = null): Boolean =
        inBounds(x, y, w, h, columns, rows) &&
            page.items.none { it.id != ignoreId && it.overlaps(x, y, w, h) }

    /** First free spot scanning left to right, top to bottom. */
    fun firstFree(page: HomePage, w: Int, h: Int, columns: Int, rows: Int): Pair<Int, Int>? {
        for (y in 0..rows - h) {
            for (x in 0..columns - w) {
                if (isFree(page, x, y, w, h, columns, rows)) return x to y
            }
        }
        return null
    }

    fun addApp(layout: HomeLayout, pageIndex: Int, ref: AppRef, columns: Int, rows: Int): HomeLayout? {
        val page = layout.pages.getOrNull(pageIndex) ?: return null
        val (x, y) = firstFree(page, 1, 1, columns, rows) ?: return null
        val item = HomeItem(id = newId(), kind = ItemKind.APP, x = x, y = y, app = ref)
        return layout.replacePage(pageIndex, page.copy(items = page.items + item))
    }

    fun moveItem(layout: HomeLayout, itemId: String, toPage: Int, x: Int, y: Int, columns: Int, rows: Int): HomeLayout? {
        val (fromPage, item) = layout.find(itemId) ?: return null
        val target = layout.pages.getOrNull(toPage) ?: return null
        if (fromPage == toPage && item.x == x && item.y == y) return null
        if (!isFree(target, x, y, item.w, item.h, columns, rows, ignoreId = itemId)) return null
        val moved = item.copy(x = x, y = y)
        return if (fromPage == toPage) {
            layout.replacePage(toPage, target.copy(items = target.items.map { if (it.id == itemId) moved else it }))
        } else {
            val source = layout.pages[fromPage]
            layout
                .replacePage(fromPage, source.copy(items = source.items.filter { it.id != itemId }))
                .replacePage(toPage, target.copy(items = target.items + moved))
        }
    }

    fun removeItem(layout: HomeLayout, itemId: String): HomeLayout? {
        val (pageIndex, _) = layout.find(itemId) ?: return null
        val page = layout.pages[pageIndex]
        return layout.replacePage(pageIndex, page.copy(items = page.items.filter { it.id != itemId }))
    }

    fun removeApp(layout: HomeLayout, ref: AppRef): HomeLayout =
        layout.copy(pages = layout.pages.map { page -> page.copy(items = page.items.filter { it.app != ref }) })

    fun addPage(layout: HomeLayout): HomeLayout = layout.copy(pages = layout.pages + HomePage(id = newId()))

    fun removePage(layout: HomeLayout, index: Int): HomeLayout? {
        if (layout.pages.size <= 1 || index !in layout.pages.indices) return null
        val removed = layout.pages[index]
        return layout.copy(
            pages = layout.pages.filterIndexed { i, _ -> i != index },
            defaultPage = layout.defaultPage.takeIf { it != removed.id },
        )
    }

    /** Moves the page at [from] to position [to]; the default page stays the same page. */
    fun movePage(layout: HomeLayout, from: Int, to: Int): HomeLayout? {
        if (from !in layout.pages.indices || to !in layout.pages.indices || from == to) return null
        val pages = layout.pages.toMutableList()
        pages.add(to, pages.removeAt(from))
        return layout.copy(pages = pages)
    }

    fun setDefaultPage(layout: HomeLayout, index: Int): HomeLayout? {
        val page = layout.pages.getOrNull(index) ?: return null
        if (layout.defaultIndex == index) return null
        return layout.copy(defaultPage = page.id)
    }

    /** Sets (or clears, with null or "none") the swipe-up or swipe-down action of an item. */
    fun setSwipe(layout: HomeLayout, itemId: String, up: Boolean, action: String?): HomeLayout? {
        val (pageIndex, item) = layout.find(itemId) ?: return null
        val value = action?.takeIf { it != "none" }
        val updated = if (up) item.copy(swipeUp = value) else item.copy(swipeDown = value)
        if (updated == item) return null
        val page = layout.pages[pageIndex]
        return layout.replacePage(pageIndex, page.copy(items = page.items.map { if (it.id == itemId) updated else it }))
    }

    /** Gives a widget a new position and size, if that area is free. */
    fun resizeItem(layout: HomeLayout, itemId: String, x: Int, y: Int, w: Int, h: Int, columns: Int, rows: Int): HomeLayout? {
        val (pageIndex, item) = layout.find(itemId) ?: return null
        if (w < 1 || h < 1) return null
        if (item.x == x && item.y == y && item.w == w && item.h == h) return null
        val page = layout.pages[pageIndex]
        if (!isFree(page, x, y, w, h, columns, rows, ignoreId = itemId)) return null
        val resized = item.copy(x = x, y = y, w = w, h = h)
        return layout.replacePage(pageIndex, page.copy(items = page.items.map { if (it.id == itemId) resized else it }))
    }

    private fun HomeLayout.replacePage(index: Int, page: HomePage): HomeLayout =
        copy(pages = pages.mapIndexed { i, old -> if (i == index) page else old })
}

/** The home layout with undo and redo. Every change goes through [commit]. */
class LayoutRepository(private val store: JsonStore<HomeLayout>) {
    val layout: StateFlow<HomeLayout> = store.value

    private val undoStack = ArrayDeque<HomeLayout>()
    private val redoStack = ArrayDeque<HomeLayout>()
    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo
    val canRedo: StateFlow<Boolean> = _canRedo

    /** Creates the first layout on a fresh install. */
    fun ensureDefault(columns: Int, rows: Int) {
        if (store.get().pages.isEmpty()) store.set(LayoutOps.defaultLayout(columns, rows))
    }

    /** Applies [newLayout] (ignored when null, which the operations return for "not possible"). */
    fun commit(newLayout: HomeLayout?): Boolean {
        if (newLayout == null || newLayout == store.get()) return false
        undoStack.addLast(store.get())
        while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        redoStack.clear()
        store.set(newLayout)
        publish()
        return true
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(store.get())
        store.set(previous)
        publish()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(store.get())
        store.set(next)
        publish()
    }

    private fun publish() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private companion object {
        const val UNDO_LIMIT = 50
    }
}
