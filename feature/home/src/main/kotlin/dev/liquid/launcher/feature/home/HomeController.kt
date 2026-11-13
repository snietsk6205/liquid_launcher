package dev.liquid.launcher.feature.home

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.AppRef
import dev.liquid.launcher.core.data.HomeItem
import dev.liquid.launcher.core.data.ItemKind
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.data.LauncherData
import dev.liquid.launcher.core.data.LaunchClock
import dev.liquid.launcher.core.data.LayoutOps
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.feature.redirect.RedirectStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.floor

/** Where a long-press menu was opened from. */
data class MenuTarget(
    val app: AppEntry,
    /** The home item, when opened on the home screen. */
    val itemId: String?,
    /** Bounds of the pressed icon in root coordinates. */
    val anchor: Rect,
)

sealed interface DragSource {
    data class Home(val itemId: String) : DragSource
    data class NewApp(val ref: AppRef) : DragSource
}

/** A widget's new area while one of its edges is being dragged. */
data class ResizePreview(val itemId: String, val x: Int, val y: Int, val w: Int, val h: Int)

/** An item being dragged by the finger. */
@Stable
class DragState(
    val source: DragSource,
    val icon: ImageBitmap?,
    val widget: String?,
    val cells: IntSize,
    /** Where in the item the finger holds it, in pixels. */
    val grab: Offset,
    /** On-screen size of the item when it was picked up. */
    val size: Size,
    start: Offset,
) {
    var finger by mutableStateOf(start)
    var overRemove by mutableStateOf(false)
}

/** Everything the home screen needs to remember between frames. */
@Stable
class HomeController(
    val data: LauncherData,
    val scope: CoroutineScope,
    val context: Context,
    private val callbacks: HomeCallbacks,
) {
    /** 0 = closed, 1 = open. Follows the finger while dragging. */
    val drawer = Animatable(0f)
    var searchOpen by mutableStateOf(false)
    var editMode by mutableStateOf(false)
    var menu by mutableStateOf<MenuTarget?>(null)
    var drag by mutableStateOf<DragState?>(null)
    var renaming by mutableStateOf<AppEntry?>(null)
    var tagging by mutableStateOf<AppEntry?>(null)

    /** Widget showing resize handles in edit mode. */
    var selectedItem by mutableStateOf<String?>(null)
    var resizing by mutableStateOf<ResizePreview?>(null)
    var pagesSheet by mutableStateOf(false)
    var drawerQuery by mutableStateOf("")

    /** Best match for the drawer query, launched by the keyboard's Go key. */
    var drawerFirstResult: AppEntry? = null

    /** Bumped to ask the drawer to drop keyboard focus. */
    var drawerFocusReset by mutableIntStateOf(0)
    var screenHeight by mutableFloatStateOf(1f)
    var screenWidth by mutableFloatStateOf(1f)

    lateinit var pager: PagerState

    /** Grid area of each page, for turning a finger position into a cell. */
    val pageGrids = HashMap<Int, LayoutCoordinates>()
    var removeZone: Rect = Rect.Zero

    val drawerOpen: Boolean get() = drawer.targetValue > 0.5f || drawer.value > 0.01f
    val anyOverlay: Boolean get() = drawerOpen || searchOpen || menu != null || editMode || renaming != null || tagging != null

    // ---- drawer ----

    /** Set by the home screen from the settings; read when the drawer starts moving. */
    var motion: DrawerMotion? = null

    private fun drawerSpring(): androidx.compose.animation.core.SpringSpec<Float> {
        val m = motion
        return spring(
            dampingRatio = m?.damping ?: 0.9f,
            stiffness = m?.stiffness ?: Spring.StiffnessMediumLow,
            visibilityThreshold = 0.001f,
        )
    }

    /**
     * Opens the drawer. [velocity] is the finger's speed in drawer heights per second, so a
     * flick hands its momentum to the spring instead of restarting from rest.
     */
    fun openDrawer(velocity: Float = 0f) {
        menu = null
        searchOpen = false
        DebugLog.event("Home", "drawer: open from " + "%.2f".format(drawer.value))
        scope.launch { drawer.animateTo(1f, drawerSpring(), initialVelocity = velocity) }
    }

    fun closeDrawer(immediately: Boolean = false, velocity: Float = 0f) {
        DebugLog.event("Home", "drawer: close from " + "%.2f".format(drawer.value))
        drawerFocusReset++
        drawerQuery = ""
        scope.launch {
            if (immediately) drawer.snapTo(0f) else drawer.animateTo(0f, drawerSpring(), initialVelocity = velocity)
        }
    }

    fun dragDrawerBy(deltaFraction: Float) {
        scope.launch { drawer.snapTo((drawer.value + deltaFraction).coerceIn(0f, 1f)) }
    }

    /** Finger released while moving the drawer: open or close depending on position and speed. */
    fun settleDrawer(velocityPxPerSec: Float) {
        DebugLog.event("Home", "drawer: released at " + "%.2f".format(drawer.value) + ", " + velocityPxPerSec.toInt() + " px/s")
        val open = when {
            velocityPxPerSec < -800f -> true
            velocityPxPerSec > 800f -> false
            else -> drawer.value > 0.4f
        }
        // Upward finger speed is negative on screen and positive for the drawer.
        val velocity = (-velocityPxPerSec / screenHeight).coerceIn(-8f, 8f)
        if (open) openDrawer(velocity) else closeDrawer(velocity = velocity)
    }

    // ---- other overlays ----

    fun openSearch() {
        menu = null
        searchOpen = true
    }

    fun closeAll(immediately: Boolean = false) {
        menu = null
        renaming = null
        searchOpen = false
        drag = null
        if (drawerOpen) closeDrawer(immediately)
    }

    /** Back gesture: close the top-most thing. Returns false when nothing was open. */
    fun back(): Boolean {
        when {
            renaming != null -> renaming = null
            tagging != null -> tagging = null
            menu != null -> menu = null
            searchOpen -> searchOpen = false
            drawerOpen -> closeDrawer()
            pagesSheet -> pagesSheet = false
            selectedItem != null -> selectedItem = null
            editMode -> exitEdit()
            else -> return false
        }
        return true
    }

    fun exitEdit() {
        editMode = false
        selectedItem = null
        resizing = null
        pagesSheet = false
        tidyPages()
    }

    /** Edit mode with [itemId] selected, for example after holding the clock. */
    fun editItem(itemId: String) {
        if (!editMode) run(LauncherAction.EditHome)
        selectedItem = itemId
    }

    fun commitResize(preview: ResizePreview) {
        val (columns, rows) = gridSize()
        val resized = LayoutOps.resizeItem(
            data.layout.layout.value, preview.itemId, preview.x, preview.y, preview.w, preview.h, columns, rows,
        )
        if (resized != null) data.layout.commit(resized) else if (preview != currentArea(preview.itemId)) toast("Not enough room there")
    }

    private fun currentArea(itemId: String): ResizePreview? =
        data.layout.layout.value.find(itemId)?.second?.let { ResizePreview(it.id, it.x, it.y, it.w, it.h) }

    // ---- pages ----

    fun goToPage(index: Int) {
        scope.launch { pager.animateScrollToPage(index) }
    }

    fun movePage(from: Int, to: Int) {
        val moved = LayoutOps.movePage(data.layout.layout.value, from, to) ?: return
        data.layout.commit(moved)
        scope.launch {
            kotlinx.coroutines.delay(32)
            pager.scrollToPage(to)
        }
    }

    fun setDefaultPage(index: Int) {
        LayoutOps.setDefaultPage(data.layout.layout.value, index)?.let {
            data.layout.commit(it)
            toast("Home goes to page ${index + 1}")
        }
    }

    fun removePage(index: Int) {
        val removed = LayoutOps.removePage(data.layout.layout.value, index)
        if (removed == null) {
            toast("The last page cannot be removed")
            return
        }
        data.layout.commit(removed)
        toast("Page removed. Undo brings it back")
    }

    // ---- apps ----

    fun launch(app: AppEntry, bounds: android.graphics.Rect? = null, options: android.os.Bundle? = null, fromDrawer: Boolean = false) {
        val started = data.apps.launch(app, bounds, options)
        if (!started) {
            toast(if (app.isWorkProfile) "Could not open: is the work profile paused?" else "Could not open ${app.label}")
            return
        }
        if (fromDrawer && data.settings.get(LauncherSettings.DrawerCloseOnLaunch)) {
            scope.launch {
                kotlinx.coroutines.delay(250)
                closeDrawer(immediately = true)
            }
        }
    }

    fun addToHome(ref: AppRef) {
        val (columns, rows) = gridSize()
        val layout = data.layout.layout.value
        val start = if (::pager.isInitialized) pager.currentPage else 0
        val order = (start until layout.pages.size) + (0 until start)
        for (page in order) {
            val added = LayoutOps.addApp(layout, page, ref, columns, rows)
            if (added != null) {
                data.layout.commit(added)
                toast("Added to page ${page + 1}")
                return
            }
        }
        // Every page is full: add one.
        val withPage = LayoutOps.addPage(layout)
        data.layout.commit(LayoutOps.addApp(withPage, withPage.pages.size - 1, ref, columns, rows))
        toast("Added to a new page")
    }

    fun removeFromHome(itemId: String) {
        data.layout.commit(LayoutOps.removeItem(data.layout.layout.value, itemId))
    }

    fun addClock() {
        val (columns, rows) = gridSize()
        val layout = data.layout.layout.value
        val pageIndex = if (::pager.isInitialized) pager.currentPage else 0
        val page = layout.pages.getOrNull(pageIndex) ?: return
        val h = minOf(3, rows)
        val spot = LayoutOps.firstFree(page, columns, h, columns, rows)
        if (spot == null) {
            toast("No room for a clock on this page")
            return
        }
        val clock = HomeItem(LayoutOps.newId(), ItemKind.WIDGET, spot.first, spot.second, columns, h, widget = "clock")
        data.layout.commit(layout.copy(pages = layout.pages.mapIndexed { i, p -> if (i == pageIndex) p.copy(items = p.items + clock) else p }))
    }

    fun addPage() {
        data.layout.commit(LayoutOps.addPage(data.layout.layout.value))
        scope.launch { pager.animateScrollToPage(data.layout.layout.value.pages.size - 1) }
    }

    private fun gridSize(): Pair<Int, Int> =
        data.settings.get(LauncherSettings.HomeColumns) to data.settings.get(LauncherSettings.HomeRows)

    // ---- drag and drop ----

    fun startDrag(state: DragState) {
        menu = null
        selectedItem = null
        pagesSheet = false
        drag = state
        editMode = true
        if (state.source is DragSource.NewApp) closeDrawer(immediately = false)
    }

    fun moveDrag(finger: Offset) {
        val d = drag ?: return
        d.finger = finger
        d.overRemove = removeZone.contains(finger)
    }

    fun endDrag() {
        val d = drag ?: return
        drag = null
        if (d.overRemove) {
            if (d.source is DragSource.Home) removeFromHome(d.source.itemId)
            return
        }
        val target = cellUnder(d) ?: return
        val (page, x, y) = target
        val (columns, rows) = gridSize()
        val layout = data.layout.layout.value
        val updated = when (val source = d.source) {
            is DragSource.Home -> LayoutOps.moveItem(layout, source.itemId, page, x, y, columns, rows)
            is DragSource.NewApp -> {
                val p = layout.pages.getOrNull(page) ?: return
                if (!LayoutOps.isFree(p, x, y, 1, 1, columns, rows)) {
                    toast("That cell is taken")
                    null
                } else {
                    val item = HomeItem(LayoutOps.newId(), ItemKind.APP, x, y, app = source.ref)
                    layout.copy(pages = layout.pages.mapIndexed { i, old -> if (i == page) old.copy(items = old.items + item) else old })
                }
            }
        }
        if (updated != null) data.layout.commit(updated)
    }

    fun cancelDrag() {
        drag = null
    }

    /** Page and top-left cell the dragged item would land on, or null if outside the grid. */
    private fun cellUnder(d: DragState): Triple<Int, Int, Int>? {
        val page = pager.currentPage
        val coords = pageGrids[page]?.takeIf { it.isAttached } ?: return null
        // Corners on screen: the page is scaled down while editing, so the layout size alone is wrong.
        val origin = coords.localToRoot(Offset.Zero)
        val corner = coords.localToRoot(Offset(coords.size.width.toFloat(), coords.size.height.toFloat()))
        val (columns, rows) = gridSize()
        val cellW = (corner.x - origin.x) / columns
        val cellH = (corner.y - origin.y) / rows
        if (cellW <= 0f || cellH <= 0f) return null
        val scale = (corner.x - origin.x) / coords.size.width.coerceAtLeast(1)
        // Use the centre of the item's top-left cell, so the item lands where it is drawn.
        val topLeft = d.finger - d.grab * scale - origin
        val x = floor((topLeft.x + cellW / 2) / cellW).toInt()
        val y = floor((topLeft.y + cellH / 2) / cellH).toInt()
        if (x < 0 || y < 0 || x + d.cells.width > columns || y + d.cells.height > rows) return null
        return Triple(page, x, y)
    }

    /** Cell the drag would land on right now, for the drop highlight. */
    fun previewCell(): Triple<Int, Int, Int>? = drag?.let { cellUnder(it) }

    /**
     * While an item is held near the left or right screen edge, turn the page; past the last
     * page a new one is added. Runs for the duration of one drag.
     */
    suspend fun edgeScrollWhileDragging(edgePx: Float) {
        var side = 0
        var since = 0L
        while (true) {
            val d = drag ?: return
            val now = android.os.SystemClock.uptimeMillis()
            val newSide = when {
                d.finger.x < edgePx -> -1
                d.finger.x > screenWidth - edgePx -> 1
                else -> 0
            }
            if (newSide != side) {
                side = newSide
                since = now
            } else if (side != 0 && now - since > EDGE_DWELL_MS) {
                val target = pager.currentPage + side
                if (target >= 0) {
                    if (target >= data.layout.layout.value.pages.size) {
                        data.layout.commit(LayoutOps.addPage(data.layout.layout.value))
                        kotlinx.coroutines.delay(32) // let the pager see the new page count
                    }
                    pager.animateScrollToPage(target)
                }
                since = android.os.SystemClock.uptimeMillis()
            }
            kotlinx.coroutines.delay(80)
        }
    }

    /** Removes empty pages at the end (never the first page). */
    fun tidyPages() {
        var layout = data.layout.layout.value
        var changed = false
        while (layout.pages.size > 1 && layout.pages.last().items.isEmpty()) {
            layout = layout.copy(pages = layout.pages.dropLast(1))
            changed = true
        }
        if (changed) data.layout.commit(layout)
    }

    // ---- actions ----

    fun run(action: LauncherAction) {
        when (action) {
            LauncherAction.None -> Unit
            LauncherAction.OpenDrawer -> openDrawer()
            LauncherAction.OpenSearch -> openSearch()
            LauncherAction.OpenSettings -> callbacks.openSettings()
            LauncherAction.EditHome -> {
                closeAll()
                editMode = true
            }
            LauncherAction.Notifications -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            LauncherAction.QuickSettings -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            LauncherAction.LockScreen -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            LauncherAction.Recents -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
            LauncherAction.SystemLauncher -> RedirectStatus.openSystemLauncher(
                context, data.settings.get(LauncherSettings.SystemHomePause) * 1000L,
            )
            LauncherAction.OpenCalendar -> start(
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
            )
            is LauncherAction.LaunchApp -> {
                val app = data.apps.find(action.ref)
                if (app != null) launch(app) else toast("That app is not installed")
            }
        }
    }

    private fun global(action: Int) {
        if (!RedirectStatus.performGlobalAction(action)) toast("Needs the redirect service (Settings → Setup checklist)")
    }

    fun openSection(id: String) = callbacks.openSection(id)

    fun openUrl(url: String) = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun start(intent: Intent) {
        try {
            LaunchClock.mark()
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            DebugLog.error("Home", "could not open: $e")
            toast("No app can open this")
        }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

private const val EDGE_DWELL_MS = 650L

/** What the home screen asks the activity to do. */
class HomeCallbacks(
    val openSettings: () -> Unit,
    val openSetup: () -> Unit,
    /** Opens one settings section by its id. */
    val openSection: (String) -> Unit = { openSettings() },
)
