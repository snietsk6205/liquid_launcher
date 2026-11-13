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
        if (layout.dockItem(itemId) != null) return layout.copy(dock = layout.dock.filter { it.id != itemId })
        val (pageIndex, _) = layout.find(itemId) ?: return null
        val page = layout.pages[pageIndex]
        return layout.replacePage(pageIndex, page.copy(items = page.items.filter { it.id != itemId }))
    }

    fun removeApp(layout: HomeLayout, ref: AppRef): HomeLayout =
        layout.copy(
            pages = layout.pages.map { page -> page.copy(items = page.items.filter { it.app != ref }) },
            dock = layout.dock.filter { it.app != ref },
        )

    // ---- dock ----

    /** A new app in dock slot [slot] (0 until [slots]); null when the slot is taken. */
    fun dockAddApp(layout: HomeLayout, slot: Int, slots: Int, ref: AppRef): HomeLayout? {
        if (slot !in 0 until slots || layout.dock.any { it.x == slot }) return null
        return layout.copy(dock = layout.dock + HomeItem(newId(), ItemKind.APP, slot, 0, 1, 1, app = ref))
    }

    /**
     * Moves an item into dock slot [slot]: from another slot (swapping with what is there) or
     * an app from a page (only into a free slot). Null when not possible.
     */
    fun dockMoveItem(layout: HomeLayout, itemId: String, slot: Int, slots: Int): HomeLayout? {
        if (slot !in 0 until slots) return null
        val occupant = layout.dock.firstOrNull { it.x == slot }
        layout.dockItem(itemId)?.let { item ->
            if (occupant?.id == item.id) return null
            return layout.copy(dock = layout.dock.map {
                when (it.id) {
                    item.id -> it.copy(x = slot)
                    occupant?.id -> it.copy(x = item.x)
                    else -> it
                }
            })
        }
        val (_, item) = layout.find(itemId) ?: return null
        val ref = item.app ?: return null
        if (item.kind != ItemKind.APP || occupant != null) return null
        val removed = removeItem(layout, itemId) ?: return null
        return removed.copy(dock = removed.dock + item.copy(x = slot, y = 0, w = 1, h = 1, app = ref))
    }

    /** Moves a dock item onto a page cell (an app on an app makes a folder, as on the page). */
    fun dockToPage(layout: HomeLayout, itemId: String, toPage: Int, x: Int, y: Int, columns: Int, rows: Int): HomeLayout? {
        val item = layout.dockItem(itemId) ?: return null
        val ref = item.app ?: return null
        val without = layout.copy(dock = layout.dock.filter { it.id != itemId })
        return dropApp(without, toPage, x, y, ref, columns, rows)
    }

    /** Drops slots past [slots] (after the dock was made smaller), keeping their apps nowhere. */
    fun dockTrim(layout: HomeLayout, slots: Int): HomeLayout =
        if (layout.dock.all { it.x < slots }) layout else layout.copy(dock = layout.dock.filter { it.x < slots })

    // ---- folders ----

    /** The 1 × 1 app or folder at a cell, which a dropped app can be combined with. */
    private fun combinable(page: HomePage, x: Int, y: Int, ignoreId: String? = null): HomeItem? =
        page.items.firstOrNull { it.id != ignoreId && it.covers(x, y) && it.w == 1 && it.h == 1 && (it.kind == ItemKind.APP || it.kind == ItemKind.FOLDER) }

    /** [ref] added to the app or folder [target]: a folder either way. Null when it is already there. */
    private fun combine(target: HomeItem, ref: AppRef): HomeItem? = when (target.kind) {
        ItemKind.APP -> {
            val first = target.app ?: return null
            if (first == ref) null else target.copy(kind = ItemKind.FOLDER, app = null, folder = listOf(first, ref), name = "Folder", swipeUp = null, swipeDown = null)
        }
        ItemKind.FOLDER -> {
            val list = target.folder.orEmpty()
            if (ref in list) null else target.copy(folder = list + ref)
        }
        ItemKind.WIDGET -> null
    }

    /**
     * Drops a new app (from the drawer) on a cell: placed when free, put into a folder when the
     * cell holds an app or a folder. Null when nothing can happen there.
     */
    fun dropApp(layout: HomeLayout, pageIndex: Int, x: Int, y: Int, ref: AppRef, columns: Int, rows: Int): HomeLayout? {
        val page = layout.pages.getOrNull(pageIndex) ?: return null
        if (isFree(page, x, y, 1, 1, columns, rows)) {
            val item = HomeItem(id = newId(), kind = ItemKind.APP, x = x, y = y, app = ref)
            return layout.replacePage(pageIndex, page.copy(items = page.items + item))
        }
        val target = combinable(page, x, y) ?: return null
        val combined = combine(target, ref) ?: return null
        return layout.replacePage(pageIndex, page.copy(items = page.items.map { if (it.id == target.id) combined else it }))
    }

    /** Moves an item; an app dropped on an app or a folder joins it in a folder. */
    fun dropItem(layout: HomeLayout, itemId: String, toPage: Int, x: Int, y: Int, columns: Int, rows: Int): HomeLayout? {
        if (layout.dockItem(itemId) != null) return dockToPage(layout, itemId, toPage, x, y, columns, rows)
        moveItem(layout, itemId, toPage, x, y, columns, rows)?.let { return it }
        val (_, item) = layout.find(itemId) ?: return null
        val ref = item.app ?: return null
        if (item.kind != ItemKind.APP) return null
        val page = layout.pages.getOrNull(toPage) ?: return null
        val target = combinable(page, x, y, ignoreId = itemId) ?: return null
        val combined = combine(target, ref) ?: return null
        val removed = removeItem(layout, itemId) ?: return null
        val targetPage = removed.pages[toPage]
        return removed.replacePage(toPage, targetPage.copy(items = targetPage.items.map { if (it.id == target.id) combined else it }))
    }

    /**
     * Takes [ref] out of a folder and places it in the first free cell of the folder's page
     * (or only removes it when [place] is false). A folder left with one app becomes that app.
     */
    fun takeFromFolder(layout: HomeLayout, folderId: String, ref: AppRef, columns: Int, rows: Int, place: Boolean = true): HomeLayout? {
        val (pageIndex, folder) = layout.find(folderId) ?: return null
        val list = folder.folder ?: return null
        if (ref !in list) return null
        val rest = list - ref
        val page = layout.pages[pageIndex]
        val replaced = when (rest.size) {
            0 -> page.items.filter { it.id != folderId }
            1 -> page.items.map { if (it.id == folderId) it.copy(kind = ItemKind.APP, app = rest[0], folder = null, name = null) else it }
            else -> page.items.map { if (it.id == folderId) it.copy(folder = rest) else it }
        }
        var result = layout.replacePage(pageIndex, page.copy(items = replaced))
        if (place) result = addApp(result, pageIndex, ref, columns, rows) ?: result
        return result
    }

    fun renameFolder(layout: HomeLayout, folderId: String, name: String): HomeLayout? {
        val (pageIndex, folder) = layout.find(folderId) ?: return null
        val clean = name.trim().ifEmpty { return null }
        if (folder.name == clean) return null
        val page = layout.pages[pageIndex]
        return layout.replacePage(pageIndex, page.copy(items = page.items.map { if (it.id == folderId) it.copy(name = clean) else it }))
    }

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
    /**
     * Sets a page's own grid, labels and name (null = like the other pages). Returns null when
     * an item on the page would not fit the new grid.
     */
    fun setPageSettings(
        layout: HomeLayout,
        index: Int,
        columns: Int?,
        rows: Int?,
        labels: Boolean?,
        name: String?,
        defaultColumns: Int,
        defaultRows: Int,
    ): HomeLayout? {
        val page = layout.pages.getOrNull(index) ?: return null
        val c = columns ?: defaultColumns
        val r = rows ?: defaultRows
        if (page.items.any { it.x + it.w > c || it.y + it.h > r }) return null
        return layout.replacePage(index, page.copy(columns = columns, rows = rows, labels = labels, name = name?.trim()?.ifEmpty { null }))
    }

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
