package dev.liquid.launcher.feature.home

import dev.liquid.launcher.core.data.HomeLayout

import dev.liquid.launcher.core.designsystem.WallpaperStore

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
    /** The folder the app was opened from. */
    val folderId: String? = null,
)

sealed interface DragSource {
    data class Home(val itemId: String) : DragSource
    data class NewApp(val ref: AppRef) : DragSource

    /** An app dragged out of an open folder. */
    data class FolderApp(val folderId: String, val ref: AppRef) : DragSource
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
    /** The icon's themed art, drawn in the current colours while dragging. */
    val art: dev.liquid.launcher.core.data.IconArt? = null,
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
    var iconPicking by mutableStateOf<AppEntry?>(null)

    /** Folder shown open. */
    var openFolder by mutableStateOf<String?>(null)

    /** Home item whose swipe actions are being edited. */
    var swipeEditing by mutableStateOf<String?>(null)

    /** Widget showing resize handles in edit mode. */
    var selectedItem by mutableStateOf<String?>(null)
    var resizing by mutableStateOf<ResizePreview?>(null)
    var pagesSheet by mutableStateOf(false)
    var drawerQuery by mutableStateOf("")

    /** Tags the drawer is filtered by (any of them); empty = no filter. */
    var tagFilter by mutableStateOf<Set<String>>(emptySet())

    /** The full list of tags (with its own search), over the drawer. */
    var tagSheet by mutableStateOf(false)

    /** Where the tag list grows from (the button or chip that opened it), in root coordinates. */
    var tagSheetOrigin: Rect? = null

    fun openTagSheet(from: Rect?) {
        tagSheetOrigin = from
        tagSheet = true
    }

    /** "left", "right" or null: the tag list button inside the search field (set by the drawer). */
    var tagButtonInField by mutableStateOf<String?>(null)

    /** Set by the drawer: the filter's name, the chip order sideways swipes step through, and whether they may. */
    var tagFilterLabel by mutableStateOf("")
    var tagSwipeOrder: List<String> = emptyList()
    var tagSwipeEnabled = false

    /** Filters the drawer by [tags], remembering the use so often-used tags come first. */
    fun filterByTags(tags: Set<String>) {
        val added = tags - tagFilter
        val changed = tags != tagFilter
        tagFilter = tags
        added.forEach { data.usage.record("tag:$it") }
        if (changed) {
            val names = data.organisation.value.value.tags.filter { it.id in tags }.map { it.name }
            notice(
                dev.liquid.launcher.core.common.NoticeKind.TagFilter,
                if (names.isEmpty()) "All apps" else names.joinToString(" + "),
            )
        }
    }

    /** Best match for the drawer query, launched by the keyboard's Go key. */
    var drawerFirstResult: AppEntry? = null

    /** Settings preview of the scroll index: a pretend finger moves along the letters. */
    var indexDemo = false

    /** The drawer's tabs, when they are shown inside the search pill (set by the drawer). */
    internal var tabsBar by mutableStateOf<TabsBar?>(null)

    /** Bumped to ask the drawer to drop keyboard focus. */
    var drawerFocusReset by mutableIntStateOf(0)
    var screenHeight by mutableFloatStateOf(1f)
    var screenWidth by mutableFloatStateOf(1f)

    lateinit var pager: PagerState
    val pagerReady: Boolean get() = ::pager.isInitialized

    /** Grid area of each page, for turning a finger position into a cell. */
    val pageGrids = HashMap<Int, LayoutCoordinates>()
    var removeZone: Rect = Rect.Zero

    val drawerOpen: Boolean get() = drawer.targetValue > 0.5f || drawer.value > 0.01f
    val anyOverlay: Boolean get() = drawerOpen || searchOpen || menu != null || editMode || renaming != null || tagging != null ||
        iconPicking != null || swipeEditing != null || openFolder != null || homeMenu || wallpaperStudio || layoutsSheet || savingLayout ||
        wallpaperStrip || clockStudio != null || todaySheet || widgetPicker

    // ---- home menu, wallpaper, layouts ----

    /** The home menu's glass pills are open. */
    var homeMenu by mutableStateOf(false)

    /** Finger position (root coordinates) while sliding from the long press onto a home menu item, or null. */
    var homeMenuFinger by mutableStateOf<Offset?>(null)

    /** Home menu items' bounds in root coordinates, by key, for press-and-slide. */
    internal val homeMenuTargets = HashMap<String, androidx.compose.ui.geometry.Rect>()
    internal var homeMenuRun: (String) -> Unit = {}

    var wallpaperStudio by mutableStateOf(false)

    /** The clock gallery, open on one clock widget; the widget draws its draft style meanwhile. */
    var clockStudio by mutableStateOf<ClockStudioState?>(null)

    fun openClockStudio(itemId: String) {
        val item = data.layout.layout.value.find(itemId)?.second ?: return
        val base = ClockStyle.fromSettings(data.settings.clockConfig())
        if (editMode) exitEdit()
        closeAll()
        clockStudio = ClockStudioState(itemId, base, ClockStyle.decode(item.options, base))
    }

    /** Closes the gallery; [save] keeps the style on the widget (one undo step). */
    fun closeClockStudio(save: Boolean) {
        val studio = clockStudio ?: return
        clockStudio = null
        if (!save || studio.style == studio.before) return
        val options = studio.style.encode(studio.base).takeIf { it.isNotEmpty() }
        val layout = data.layout.layout.value
        data.layout.commit(
            layout.copy(pages = layout.pages.map { page -> page.copy(items = page.items.map { if (it.id == studio.itemId) it.copy(options = options) else it }) })
        )
    }

    var layoutsSheet by mutableStateOf(false)

    // ---- study companion and widgets ----

    /** The Today panel (study blocks and tasks). */
    var todaySheet by mutableStateOf(false)

    /** The list of built-in widgets, over edit mode. */
    var widgetPicker by mutableStateOf(false)

    /** Switches the study companion on and asks for the calendar. */
    fun setupStudy() {
        data.settings.set(LauncherSettings.StudyEnabled, true)
        callbacks.requestCalendar { granted ->
            data.study.refresh()
            data.study.watch(true)
            if (!granted) notice(dev.liquid.launcher.core.common.NoticeKind.Info, "Calendar access is needed", "Allow it in the phone's app settings to see your blocks")
        }
    }

    private fun studyWrite(change: suspend () -> Boolean) {
        scope.launch {
            if (change()) return@launch
            when {
                !data.settings.get(LauncherSettings.StudyWriteBack) -> notice(dev.liquid.launcher.core.common.NoticeKind.Info, "Writing to the calendar is off", "Settings → Study companion", LauncherAction.Section("study"))
                !data.study.canWrite -> callbacks.requestCalendar { data.study.refresh() }
                else -> notice(dev.liquid.launcher.core.common.NoticeKind.Info, "That event could not be changed", "Its calendar may be read-only")
            }
        }
    }

    fun studyTick(event: dev.liquid.launcher.core.data.StudyEvent, index: Int, done: Boolean) {
        Haptics.tick(view)
        studyWrite { data.study.setTask(event, index, done) }
    }

    fun studyStatus(event: dev.liquid.launcher.core.data.StudyEvent, status: dev.liquid.launcher.core.data.StudyStatus) {
        Haptics.confirm(view)
        studyWrite {
            // Finishing the block also ends its focus timer, so the minutes land in the same event.
            if (status != dev.liquid.launcher.core.data.StudyStatus.Open && data.study.focus.value?.eventKey == event.key) data.study.stopFocus()
            data.study.setStatus(event, status)
        }
    }

    /** Starts the focus timer on [event], or stops it and writes the minutes to the event. */
    fun studyFocus(event: dev.liquid.launcher.core.data.StudyEvent) {
        Haptics.confirm(view)
        val running = data.study.focus.value
        scope.launch {
            if (running != null) {
                val minutes = data.study.stopFocus()
                notice(dev.liquid.launcher.core.common.NoticeKind.Study, "Focus stopped: $minutes min", if (minutes > 0) "Written to the block" else null)
                if (running.eventKey == event.key) return@launch
            }
            data.study.startFocus(event)
            notice(dev.liquid.launcher.core.common.NoticeKind.Study, "Focus on ${event.title.subject}", "Until the block ends; tap Focus again to stop")
        }
    }

    /** Shares today's study log with the Claude app (or the share sheet), ready for a note. */
    fun tellClaude() {
        val text = studyReport(data.study.day.value)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        val target = data.settings.get(LauncherSettings.StudyClaudeApp).trim()
        val direct = if (target.isNotEmpty()) Intent(send).setPackage(target) else null
        val intent = if (direct != null && direct.resolveActivity(context.packageManager) != null) direct else Intent.createChooser(send, "Send the study log")
        start(intent)
    }

    /** Opens the study plan: a link, or an app given by its package name ("a link or app of your choice"). */
    fun openStudyPlan() {
        val link = data.settings.get(LauncherSettings.StudyPlanLink).trim()
        val app = if (link.isNotEmpty() && ':' !in link && '/' !in link) context.packageManager.getLaunchIntentForPackage(link) else null
        when {
            link.isEmpty() -> run(LauncherAction.OpenCalendar)
            app != null -> start(app)
            // No scheme and not an installed app: a web address typed without "https://".
            ':' !in link -> openStudyLink("https://$link")
            else -> openStudyLink(link)
        }
    }

    private var lastStudyLink = 0L

    /**
     * Opens a study link (the plan, a block's link) straight in the app chosen in Settings → Study
     * companion (0.6.4, D96), instead of handing it to the phone, which may first show a link
     * chooser or a link-checking app. A second tap within a moment does not open it twice.
     */
    fun openStudyLink(url: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastStudyLink < 900) return
        lastStudyLink = now
        val pm = context.packageManager
        val uri = Uri.parse(url)
        fun view(pkg: String?) = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).also { if (pkg != null) it.setPackage(pkg) }
        fun resolves(intent: Intent) = intent.resolveActivity(pm) != null
        // A Custom Tab: Chrome's own view of the page over the launcher, signed in like Chrome.
        // Built by hand (the extras of androidx.browser), so no library is needed for it.
        fun tab(): Intent? {
            val browsers = try {
                pm.queryIntentServices(Intent("android.support.customtabs.action.CustomTabsService"), 0).map { it.serviceInfo.packageName }
            } catch (e: Exception) {
                emptyList()
            }
            val pkg = listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.brave.browser").firstOrNull { it in browsers } ?: browsers.firstOrNull() ?: return null
            return view(pkg).apply {
                putExtras(android.os.Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
                putExtra("android.support.customtabs.extra.TITLE_VISIBILITY", 1)
                putExtra("android.support.customtabs.extra.SHARE_STATE", 1)
            }
        }
        val claude = data.settings.get(LauncherSettings.StudyClaudeApp).trim().ifEmpty { "com.anthropic.claude" }
        val intent = when (data.settings.get(LauncherSettings.StudyLinkOpener)) {
            "system" -> view(null)
            "chrome" -> view("com.android.chrome").takeIf(::resolves) ?: tab() ?: view(null)
            "claude" -> view(claude).takeIf(::resolves) ?: run {
                notice(dev.liquid.launcher.core.common.NoticeKind.Info, "The Claude app does not open links", "Opened in a Chrome tab instead")
                tab() ?: view(null)
            }
            else -> tab() ?: view(null)
        }
        DebugLog.event("Study", "opening a study link in " + (intent.`package` ?: "the phone's choice"))
        start(intent)
    }

    /** Adds a built-in widget of [type] to the current page: across its width, or its own width when it has one. */
    fun addWidget(type: String) {
        val pageIndex = if (::pager.isInitialized) pager.currentPage else 0
        val (columns, rows) = gridFor(pageIndex)
        val kind = WidgetTypes.firstOrNull { it.type == type }
        if (widgetTarget == "search") {
            val item = HomeItem(LayoutOps.newId(), ItemKind.WIDGET, 0, 0, columns, (kind?.h ?: 3).coerceIn(1, 4), widget = type)
            addSearchWidget(item)
            if (type in setOf("now", "today", "next") && !data.study.loaded.value) setupStudy()
            return
        }
        val wantedW = kind?.w?.takeIf { it > 0 }?.coerceAtMost(columns) ?: columns
        val item = placeWidget(pageIndex, columns, rows, wantedW, kind?.h ?: 3) { x, y, w, h ->
            HomeItem(LayoutOps.newId(), ItemKind.WIDGET, x, y, w, h, widget = type)
        } ?: return
        when (type) {
            // Straight into the gallery: a new clock is there to be chosen and styled.
            "clock" -> openClockStudio(item.id)
            "now", "today", "next" -> if (!data.study.loaded.value) setupStudy()
        }
    }

    var savingLayout by mutableStateOf(false)

    /**
     * How far search is open, 0..1 (0.7.0, D99): like [drawer], it follows the finger of a swipe
     * down and springs open or closed on release; [searchOpen] is where it is going.
     */
    val search = Animatable(0f)

    /** How far search is open (0..1), for the blur behind it. */
    val searchAmount: Float get() = search.value.coerceIn(0f, 1f)

    /** What is typed in search; kept here so the field inside the morphing pill and the results share it (0.7.1). */
    var searchQuery by mutableStateOf("")

    /** Opens search's first result (the keyboard's Go); set by the search screen. */
    var searchSubmit: (() -> Unit)? = null

    /** How far the pill has turned into a search field: by the drawer or by search, whichever is open. */
    fun morph(): Float = maxOf(drawer.value, search.value)

    /** Where the finger has put search, ahead of [search] (see [dragPosition]). */
    private var searchDragPosition: Float? = null

    /** Lets the activity pick an image (photo picker); set by the activity. */
    internal val pickImage: ((android.net.Uri?) -> Unit) -> Unit get() = callbacks.pickImage
    internal val pickImages: ((List<android.net.Uri>) -> Unit) -> Unit get() = callbacks.pickImages
    internal val pickFolder: ((android.net.Uri?) -> Unit) -> Unit get() = callbacks.pickFolder
    internal val pickFont: ((android.net.Uri?) -> Unit) -> Unit get() = callbacks.pickFont

    /** The finger let go after sliding: run the item under it, if any. */
    fun releaseHomeMenuSlide(finger: Offset, moved: Boolean) {
        homeMenuFinger = null
        if (!moved) return
        val hit = homeMenuTargets.entries.firstOrNull { it.value.contains(finger) }?.key ?: return
        homeMenuRun(hit)
    }

    // ---- changing the whole screen at once (layouts, wallpapers) ----

    /** Takes a picture of the home screen as it is now; set by the home screen. */
    internal var captureHome: (suspend () -> androidx.compose.ui.graphics.ImageBitmap?)? = null

    /** The picture of the screen before a change, fading out over the new screen; null when no change is running. */
    var transitionShot by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    val transitionProgress = Animatable(0f)
    private var transitionJob: kotlinx.coroutines.Job? = null

    /** Where the change was started (the wallpaper tapped in the strip), in screen pixels: a ripple spreads from there. */
    var transitionOrigin: androidx.compose.ui.geometry.Offset? = null

    /** The wallpaper strip from the home menu. */
    var wallpaperStrip by mutableStateOf(false)

    /** Announces something as a glass notice (see NoticeHost); which kinds show is a setting. */
    fun notice(kind: dev.liquid.launcher.core.common.NoticeKind, text: String, detail: String? = null, action: LauncherAction? = null) =
        dev.liquid.launcher.core.common.Notices.post(kind, text, detail, action?.encode())

    /**
     * Changes the screen in one go: [prepare] does the slow work first (loading and placing the
     * next wallpaper) while the old screen stays, then a picture of the old screen is laid over,
     * [commit] swaps everything underneath, and the picture melts away. [after] runs once the
     * transition is done (writing files, setting the system wallpaper), so it never stutters.
     */
    fun <T : Any> changeScreen(prepare: suspend () -> T?, commit: (T) -> Unit, after: suspend (T) -> Unit = {}) {
        transitionJob?.cancel()
        transitionJob = scope.launch {
            val value = prepare() ?: return@launch
            val style = data.settings.get(LauncherSettings.WallpaperTransition)
            val shot = if (style != "none") runCatching { captureHome?.invoke() }.getOrNull() else null
            transitionProgress.snapTo(0f)
            transitionShot = shot
            commit(value)
            try {
                if (shot != null) {
                    // Let the new screen draw once under the picture before it starts to fade.
                    androidx.compose.runtime.withFrameNanos { }
                    transitionProgress.animateTo(
                        1f,
                        androidx.compose.animation.core.tween(
                            MotionScale.ms((data.settings.get(LauncherSettings.WallpaperTransitionMs) * WallpaperTransitions.lengthFactor(style)).toInt()),
                            easing = WallpaperTransitions.easing(style),
                        ),
                    )
                }
            } finally {
                transitionShot = null
                transitionOrigin = null
            }
            after(value)
        }
    }

    /** A saved layout's wallpaper and its colours, worked out before switching so they arrive with the icons. */
    private class LayoutSwitch(
        val wallpaper: android.graphics.Bitmap?,
        val file: java.io.File?,
        val wallpaperId: String?,
        val tone: dev.liquid.launcher.core.common.WallpaperTone?,
        val palette: Pair<Int, Int>?,
    )

    fun switchLayout(id: String) {
        val peek = data.layouts.peek(id) ?: return toast("That layout no longer exists")
        val store = WallpaperStore.get(context)
        dev.liquid.launcher.core.common.ColourTiming.begin("layout switch")
        changeScreen(
            prepare = {
                val bitmap = peek.wallpaper?.let { store.decodeFile(it) }
                val tone = bitmap?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { dev.liquid.launcher.core.common.WallpaperTones.fromBitmap(it) } }
                dev.liquid.launcher.core.common.ColourTiming.mark("wallpaper loaded, palette ready")
                LayoutSwitch(bitmap, peek.wallpaper, peek.wallpaperId, tone, tone?.let { data.paletteFor(it.primary) })
            },
            commit = { switch ->
                data.layouts.switchTo(id, data.settings.get(LauncherSettings.LayoutsAutoSave))
                data.layouts.find(id)?.let { notice(dev.liquid.launcher.core.common.NoticeKind.Layout, it.name) }
                switch.wallpaper?.let {
                    store.show(it, switch.tone)
                    IconPaint.now(switch.palette)
                    HomeTheme.refreshAccent(context)
                    dev.liquid.launcher.core.common.ColourTiming.mark("wallpaper and colours on screen")
                }
                val home = data.layout.layout.value.defaultIndex
                runCatching { scope.launch { pager.scrollToPage(home) } }
            },
            after = { switch ->
                val bitmap = switch.wallpaper ?: return@changeScreen
                store.persist(bitmap, data.settings.get(LauncherSettings.WallpaperLockToo), switch.file, Wallpapers.systemWrite(data))
                switch.wallpaperId?.let { wid -> data.wallpapers.find(wid)?.let { data.wallpapers.markUsed(it.id, null) } }
            },
        )
    }

    /** Makes [pick] the wallpaper with the transition (wallpaper strip, shuffle). */
    fun applyWallpaper(pick: WallpaperPick) {
        dev.liquid.launcher.core.common.ColourTiming.begin("wallpaper switch")
        changeScreen(
            prepare = { Wallpapers.prepare(context, data, pick) ?: run { toast("Could not read that picture"); null } },
            commit = { prepared ->
                WallpaperStore.get(context).show(prepared.bitmap, prepared.tone)
                IconPaint.now(prepared.palette)
                HomeTheme.refreshAccent(context)
                dev.liquid.launcher.core.common.ColourTiming.mark("wallpaper and colours on screen")
            },
            after = { prepared -> Wallpapers.persist(context, data, prepared)?.let { toast("Could not set the wallpaper: $it") } },
        )
    }

    fun shuffleWallpaper() {
        scope.launch {
            val pick = Wallpapers.next(data)
            if (pick == null) {
                notice(dev.liquid.launcher.core.common.NoticeKind.Info, "Nothing to shuffle yet", "Add favourites in the wallpaper screen", LauncherAction.WallpaperStudio)
                return@launch
            }
            applyWallpaper(pick)
            notice(dev.liquid.launcher.core.common.NoticeKind.Wallpaper, "Wallpaper shuffled", "Tap for the wallpaper strip", LauncherAction.Wallpaper)
        }
    }

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
        dragPosition = null
        menu = null
        if (searchOpen || search.value > 0f) closeSearch(immediately = true)
        DebugLog.event("Home", "drawer: open from " + "%.2f".format(drawer.value))
        scope.launch { drawer.animateTo(1f, drawerSpring(), initialVelocity = velocity) }
    }

    fun closeDrawer(immediately: Boolean = false, velocity: Float = 0f) {
        dragPosition = null
        DebugLog.event("Home", "drawer: close from " + "%.2f".format(drawer.value))
        drawerFocusReset++
        drawerQuery = ""
        tagSheet = false
        if (!data.settings.get(LauncherSettings.TagFilterKeep)) tagFilter = emptySet()
        scope.launch {
            if (immediately) drawer.snapTo(0f) else drawer.animateTo(0f, drawerSpring(), initialVelocity = velocity)
        }
    }

    /** Screen heights of finger travel between closed and open. */
    private fun drawerTravel(): Float = (motion?.travel ?: 1f).coerceIn(0.15f, 1f)

    /**
     * Where the finger has put the drawer, ahead of [drawer] itself: the snaps run on the main
     * thread's queue, so on a slow frame the Animatable can lag several events behind.
     */
    private var dragPosition: Float? = null

    /** How an app opens from an icon at [rect] (root coordinates), per the Motion settings. */
    fun launchOptions(view: android.view.View, rect: android.graphics.Rect?): android.os.Bundle? {
        if (rect == null) return null
        val options = when (motionConfig.launch) {
            "scale" -> android.app.ActivityOptions.makeScaleUpAnimation(view, rect.left, rect.top, rect.width(), rect.height())
            "fade" -> android.app.ActivityOptions.makeCustomAnimation(context, android.R.anim.fade_in, android.R.anim.fade_out)
            "system" -> return null
            else -> android.app.ActivityOptions.makeClipRevealAnimation(view, rect.left, rect.top, rect.width(), rect.height())
        }
        return options.toBundle()
    }

    /** The home screen's view, for haptic ticks; set by the live home screen. */
    var view: android.view.View? = null
    var motionConfig = MotionConfig()

    /** [deltaFraction] is the finger's movement as a fraction of the screen height (up = positive). */
    fun dragDrawerBy(deltaFraction: Float) {
        val before = dragPosition ?: drawer.value
        val next = (before + deltaFraction / drawerTravel()).coerceIn(0f, 1f)
        // A tick where letting go starts to open the drawer (see settleDrawer).
        if (motionConfig.drawerTick && (before > OPEN_POINT) != (next > OPEN_POINT)) Haptics.tick(view)
        dragPosition = next
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { drawer.snapTo(next) }
    }

    /** Finger released while moving the drawer: open or close depending on position and speed. */
    fun settleDrawer(velocityPxPerSec: Float) {
        val position = dragPosition ?: drawer.value
        DebugLog.event("Home", "drawer: released at " + "%.2f".format(position) + ", " + velocityPxPerSec.toInt() + " px/s")
        val open = when {
            velocityPxPerSec < -800f -> true
            velocityPxPerSec > 800f -> false
            else -> position > OPEN_POINT
        }
        // Upward finger speed is negative on screen and positive for the drawer.
        val velocity = (-velocityPxPerSec / (screenHeight * drawerTravel())).coerceIn(-14f, 14f)
        if (open) openDrawer(velocity) else closeDrawer(velocity = velocity)
    }

    // ---- other overlays ----

    /** Opens search; [velocity] in search heights per second, so a flick keeps its momentum (D99). */
    fun openSearch(velocity: Float = 0f) {
        menu = null
        searchDragPosition = null
        searchOpen = true
        scope.launch { search.animateTo(1f, drawerSpring(), initialVelocity = velocity) }
    }

    fun closeSearch(immediately: Boolean = false, velocity: Float = 0f) {
        searchDragPosition = null
        searchOpen = false
        searchEditing = false
        scope.launch {
            if (immediately) search.snapTo(0f) else search.animateTo(0f, drawerSpring(), initialVelocity = velocity)
            if (!searchOpen) searchQuery = ""
        }
    }

    /** [deltaFraction]: the finger's movement as a fraction of the screen height (down = positive). */
    fun dragSearchBy(deltaFraction: Float) {
        val before = searchDragPosition ?: search.value
        val next = (before + deltaFraction / drawerTravel()).coerceIn(0f, 1f)
        if (motionConfig.drawerTick && (before > OPEN_POINT) != (next > OPEN_POINT)) Haptics.tick(view)
        searchDragPosition = next
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { search.snapTo(next) }
    }

    /** Finger released while moving search: open or close by position and speed, as the drawer does. */
    fun settleSearch(velocityPxPerSec: Float) {
        val position = searchDragPosition ?: search.value
        DebugLog.event("Home", "search: released at " + "%.2f".format(position) + ", " + velocityPxPerSec.toInt() + " px/s")
        val open = when {
            velocityPxPerSec > 800f -> true
            velocityPxPerSec < -800f -> false
            else -> position > OPEN_POINT
        }
        val velocity = (velocityPxPerSec / (screenHeight * drawerTravel())).coerceIn(-14f, 14f)
        if (open) openSearch(velocity) else closeSearch(velocity = velocity)
    }

    fun closeAll(immediately: Boolean = false) {
        homeMenu = false
        wallpaperStrip = false
        layoutsSheet = false
        wallpaperStudio = false
        todaySheet = false
        widgetPicker = false
        if (clockStudio != null) closeClockStudio(save = true)
        menu = null
        openFolder = null
        renaming = null
        if (searchOpen || search.value > 0f) closeSearch(immediately)
        drag = null
        if (drawerOpen) closeDrawer(immediately)
    }

    /** Back gesture: close the top-most thing. Returns false when nothing was open. */
    fun back(): Boolean {
        when {
            renaming != null -> renaming = null
            tagging != null -> tagging = null
            iconPicking != null -> iconPicking = null
            swipeEditing != null -> swipeEditing = null
            menu != null -> menu = null
            tagSheet -> tagSheet = false
            savingLayout -> savingLayout = false
            homeMenu -> homeMenu = false
            wallpaperStrip -> wallpaperStrip = false
            layoutsSheet -> layoutsSheet = false
            wallpaperStudio -> wallpaperStudio = false
            todaySheet -> todaySheet = false
            widgetPicker -> widgetPicker = false
            clockStudio != null -> closeClockStudio(save = true)
            openFolder != null -> openFolder = null
            searchOpen -> closeSearch()
            drawerOpen && tagFilter.isNotEmpty() && drawerQuery.isEmpty() -> tagFilter = emptySet()
            drawerOpen -> closeDrawer()
            pageSettings != null -> pageSettings = null
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
        val (columns, rows) = gridFor(data.layout.layout.value.find(preview.itemId)?.first ?: 0)
        val resized = LayoutOps.resizeItem(
            data.layout.layout.value, preview.itemId, preview.x, preview.y, preview.w, preview.h, columns, rows,
        )
        if (resized != null) data.layout.commit(resized) else if (preview != currentArea(preview.itemId)) toast("Not enough room there")
    }

    private fun currentArea(itemId: String): ResizePreview? =
        data.layout.layout.value.find(itemId)?.second?.let { ResizePreview(it.id, it.x, it.y, it.w, it.h) }

    fun takeFromFolder(folderId: String, ref: AppRef) {
        val (columns, rows) = gridFor(data.layout.layout.value.find(folderId)?.first ?: 0)
        LayoutOps.takeFromFolder(data.layout.layout.value, folderId, ref, columns, rows)?.let {
            data.layout.commit(it)
            if (it.find(folderId)?.second?.kind != ItemKind.FOLDER) openFolder = null
        }
    }

    fun renameFolder(folderId: String, name: String) {
        LayoutOps.renameFolder(data.layout.layout.value, folderId, name)?.let { data.layout.commit(it) }
    }

    fun setSwipe(itemId: String, up: Boolean, action: LauncherAction) {
        LayoutOps.setSwipe(data.layout.layout.value, itemId, up, action.encode())?.let { data.layout.commit(it) }
    }

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
            val (pc, pr) = gridFor(page)
            val added = LayoutOps.addApp(layout, page, ref, pc, pr)
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

    fun addClock() = addWidget("clock")

    /**
     * Puts an item of [wantedW] × [wantedH] cells on page [pageIndex] at the first free place, giving
     * up height (to 1) and then width when it does not fit, and commits it. Null when there is no room.
     */
    private fun placeWidget(pageIndex: Int, columns: Int, rows: Int, wantedW: Int, wantedH: Int, make: (x: Int, y: Int, w: Int, h: Int) -> HomeItem): HomeItem? {
        val layout = data.layout.layout.value
        val page = layout.pages.getOrNull(pageIndex) ?: return null
        var w = wantedW.coerceIn(1, columns)
        var h = wantedH.coerceIn(1, rows)
        var spot = LayoutOps.firstFree(page, w, h, columns, rows)
        while (spot == null && (h > 1 || w > 1)) {
            if (h > 1) h-- else w--
            spot = LayoutOps.firstFree(page, w, h, columns, rows)
        }
        if (spot == null) {
            toast("No room for it on this page")
            return null
        }
        val item = make(spot.first, spot.second, w, h)
        data.layout.commit(layout.copy(pages = layout.pages.mapIndexed { i, p -> if (i == pageIndex) p.copy(items = p.items + item) else p }))
        return item
    }

    /**
     * Adds another app's widget to the current page (0.7.0, D100): an id from the host, the system's
     * permission (asked once per app, unless already given), the widget's own set-up screen when it
     * has one, then the item at its own size. Anything cancelled gives the id back.
     */
    fun addAppWidget(info: android.appwidget.AppWidgetProviderInfo) {
        val widgets = data.widgets
        val id = try {
            widgets.allocate()
        } catch (e: Exception) {
            DebugLog.error("Widgets", "no widget id: $e")
            toast("This widget could not be added")
            return
        }
        val pageIndex = if (::pager.isInitialized) pager.currentPage else 0
        val label = info.loadLabel(context.packageManager).orEmpty()
        val target = widgetTarget
        fun place() {
            val (columns, rows) = gridFor(pageIndex)
            val (w, h) = dev.liquid.launcher.core.data.AppWidgets.cells(info, screenWidth / columns, screenHeight * 0.8f / rows, columns, rows)
            if (target == "search") {
                // On the search screen a widget takes the full width and its own height.
                addSearchWidget(
                    HomeItem(
                        LayoutOps.newId(), ItemKind.WIDGET, 0, 0, columns, h, widget = "app",
                        options = mapOf("appWidgetId" to id.toString(), "provider" to info.provider.flattenToString(), "label" to label),
                    )
                )
                return
            }
            val item = placeWidget(pageIndex, columns, rows, w, h) { x, y, cw, ch ->
                HomeItem(
                    LayoutOps.newId(), ItemKind.WIDGET, x, y, cw, ch, widget = "app",
                    options = mapOf("appWidgetId" to id.toString(), "provider" to info.provider.flattenToString(), "label" to label),
                )
            }
            if (item == null) widgets.delete(id) else DebugLog.event("Widgets", "added \"$label\" (${item.w} × ${item.h})")
        }
        fun setUpThenPlace() {
            if (!widgets.needsSetUp(info)) return place()
            callbacks.setUpWidget(id) { ok -> if (ok) place() else widgets.delete(id) }
        }
        if (widgets.bindIfAllowed(id, info)) {
            setUpThenPlace()
        } else {
            callbacks.bindWidget(widgets.bindIntent(id, info)) { ok ->
                if (ok) setUpThenPlace() else {
                    widgets.delete(id)
                    toast("The widget was not added")
                }
            }
        }
    }

    // ---- widgets on the search screen (0.7.1, D105) ----

    /** Where the widget picker adds: "page" (the current home page) or "search" (the search screen). */
    var widgetTarget by mutableStateOf("page")

    /** The search screen's widgets show remove and move buttons. */
    var searchEditing by mutableStateOf(false)

    /** Opens the widget picker for the search screen. */
    fun pickSearchWidget() {
        widgetTarget = "search"
        widgetPicker = true
    }

    fun addSearchWidget(item: HomeItem) {
        val layout = data.layout.layout.value
        data.layout.commit(layout.copy(search = layout.search + item))
        DebugLog.event("Widgets", "added a widget to the search screen (${item.widget})")
    }

    fun removeSearchWidget(id: String) {
        val layout = data.layout.layout.value
        data.layout.commit(layout.copy(search = layout.search.filter { it.id != id }))
    }

    /** Moves a search widget one place up ([up]) or down. */
    fun moveSearchWidget(id: String, up: Boolean) {
        val layout = data.layout.layout.value
        val list = layout.search.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        data.layout.commit(layout.copy(search = list))
    }

    /** Makes a search widget one cell taller or shorter (1 to 6 cells). */
    fun resizeSearchWidget(id: String, delta: Int) {
        val layout = data.layout.layout.value
        data.layout.commit(layout.copy(search = layout.search.map { if (it.id == id) it.copy(h = (it.h + delta).coerceIn(1, 6)) else it }))
    }

    /** The page the home screen shows, or the first before the pager exists. */
    fun currentPageIndex(): Int = if (::pager.isInitialized) pager.currentPage else 0

    /** Sets one of a widget's own options ([HomeItem.options]); null removes it. One undo step. */
    fun setItemOption(itemId: String, key: String, value: String?) {
        val layout = data.layout.layout.value
        data.layout.commit(
            layout.copy(pages = layout.pages.map { page ->
                page.copy(items = page.items.map { item ->
                    if (item.id != itemId) item else {
                        val options = (item.options.orEmpty() - key).let { if (value != null) it + (key to value) else it }
                        item.copy(options = options.takeIf { it.isNotEmpty() })
                    }
                })
            })
        )
    }

    /** Opens the set-up screen of a placed app widget again (its "Customise"), when it has one. */
    fun reconfigureAppWidget(item: HomeItem) {
        val id = dev.liquid.launcher.core.data.AppWidgets.idOf(item) ?: return
        val info = data.widgets.info(id) ?: return
        if (info.configure == null) {
            toast("This widget has no settings of its own")
            return
        }
        callbacks.setUpWidget(id) { }
    }

    fun addPage() {
        data.layout.commit(LayoutOps.addPage(data.layout.layout.value))
        scope.launch { pager.animateScrollToPage(data.layout.layout.value.pages.size - 1) }
    }

    private fun gridSize(): Pair<Int, Int> =
        data.settings.get(LauncherSettings.HomeColumns) to data.settings.get(LauncherSettings.HomeRows)

    /** Grid of page [index]: its own if it has one (page settings), otherwise the home screen's. */
    fun gridFor(index: Int): Pair<Int, Int> {
        val (columns, rows) = gridSize()
        val page = data.layout.layout.value.pages.getOrNull(index) ?: return columns to rows
        return (page.columns ?: columns) to (page.rows ?: rows)
    }

    /** The dock's bounds on screen while it is shown, for dropping apps onto it. */
    var dockBounds: androidx.compose.ui.geometry.Rect? = null
    var dockSlots = 5

    /** Dock slot under [finger], or null when the finger is not over the dock. */
    fun dockSlotAt(finger: Offset): Int? {
        val bounds = dockBounds?.takeIf { it.contains(finger) } ?: return null
        return ((finger.x - bounds.left) / (bounds.width / dockSlots)).toInt().coerceIn(0, dockSlots - 1)
    }

    /** Folder look and behaviour; set by the home screen from the settings. */
    var folderConfig by mutableStateOf(FolderConfig())

    /** Where the open folder was tapped (root coordinates), so it can grow out of it. */
    var folderOrigin: Offset? = null

    /** Page settings dialog for this page index, or null. */
    var pageSettings by mutableStateOf<Int?>(null)

    fun savePageSettings(index: Int, columns: Int?, rows: Int?, labels: Boolean?, name: String?): Boolean {
        val (c, r) = gridSize()
        val updated = LayoutOps.setPageSettings(data.layout.layout.value, index, columns, rows, labels, name, c, r)
        if (updated == null) {
            toast("Some items on this page would not fit that grid")
            return false
        }
        data.layout.commit(updated)
        return true
    }

    // ---- drag and drop ----

    fun startDrag(state: DragState) {
        Haptics.confirm(view)
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
        if (d.source is DragSource.FolderApp) openFolder = null
        if (d.overRemove) {
            when (val source = d.source) {
                is DragSource.Home -> removeFromHome(source.itemId)
                is DragSource.FolderApp -> takeOutOfFolder(source, place = false)?.let { data.layout.commit(it) }
                is DragSource.NewApp -> Unit
            }
            return
        }
        dockSlotAt(d.finger)?.let { slot ->
            val layout = data.layout.layout.value
            val updated = when (val source = d.source) {
                is DragSource.Home -> LayoutOps.dockMoveItem(layout, source.itemId, slot, dockSlots)
                is DragSource.NewApp -> LayoutOps.dockAddApp(layout, slot, dockSlots, source.ref)
                is DragSource.FolderApp -> takeOutOfFolder(source, place = false)?.let { LayoutOps.dockAddApp(it, slot, dockSlots, source.ref) }
            }
            if (updated != null) data.layout.commit(updated) else toast("That place in the dock is taken (or that item cannot go there)")
            return
        }
        val target = cellUnder(d) ?: return
        val (page, x, y) = target
        val (columns, rows) = gridFor(page)
        val layout = data.layout.layout.value
        // On a free cell the item lands there; an app on an app or a folder makes or joins a folder.
        val updated = when (val source = d.source) {
            is DragSource.Home -> LayoutOps.dropItem(layout, source.itemId, page, x, y, columns, rows)
            is DragSource.NewApp -> LayoutOps.dropApp(layout, page, x, y, source.ref, columns, rows)
                ?: run {
                    toast("That cell is taken")
                    null
                }
            is DragSource.FolderApp -> takeOutOfFolder(source, place = false)?.let { without ->
                LayoutOps.dropApp(without, page, x, y, source.ref, columns, rows)
                    ?: LayoutOps.addApp(without, page, source.ref, columns, rows)
                    ?: run {
                        toast("No room on this page")
                        null
                    }
            }
        }
        if (updated != null) data.layout.commit(updated)
    }

    fun cancelDrag() {
        if (drag?.source is DragSource.FolderApp) openFolder = null
        drag = null
    }

    private fun takeOutOfFolder(source: DragSource.FolderApp, place: Boolean): HomeLayout? {
        val layout = data.layout.layout.value
        val (columns, rows) = gridFor(layout.find(source.folderId)?.first ?: 0)
        return LayoutOps.takeFromFolder(layout, source.folderId, source.ref, columns, rows, place)
    }

    /** Page and top-left cell the dragged item would land on, or null if outside the grid. */
    private fun cellUnder(d: DragState): Triple<Int, Int, Int>? {
        val page = pager.currentPage
        val coords = pageGrids[page]?.takeIf { it.isAttached } ?: return null
        // Corners on screen: the page is scaled down while editing, so the layout size alone is wrong.
        val origin = coords.localToRoot(Offset.Zero)
        val corner = coords.localToRoot(Offset(coords.size.width.toFloat(), coords.size.height.toFloat()))
        val (columns, rows) = gridFor(page)
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
            is LauncherAction.LaunchActivity -> try {
                LaunchClock.mark()
                context.startActivity(Intent().setClassName(action.pkg, action.cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: SecurityException) {
                toast("That screen can only be opened from inside its app")
            } catch (e: Exception) {
                DebugLog.error("Home", "activity failed: $e")
                toast("That screen is not there any more")
            }
            is LauncherAction.LaunchApp -> {
                val app = data.apps.find(action.ref)
                if (app != null) launch(app) else toast("That app is not installed")
            }
            LauncherAction.HomeMenu -> {
                closeAll()
                homeMenu = true
            }
            LauncherAction.Wallpaper -> {
                closeAll()
                if (editMode) exitEdit()
                wallpaperStrip = true
            }
            LauncherAction.WallpaperStudio -> {
                closeAll()
                if (editMode) exitEdit()
                wallpaperStudio = true
            }
            LauncherAction.ShuffleWallpaper -> shuffleWallpaper()
            LauncherAction.TagFilter -> {
                closeAll()
                if (editMode) exitEdit()
                openDrawer()
                openTagSheet(null)
            }
            LauncherAction.AddPage -> addPage()
            LauncherAction.Pages -> {
                closeAll()
                editMode = true
                pagesSheet = true
            }
            LauncherAction.Layouts -> {
                closeAll()
                layoutsSheet = true
            }
            LauncherAction.SaveLayout -> savingLayout = true
            LauncherAction.NextLayout -> data.layouts.next()?.let { switchLayout(it.id) } ?: toast("No saved layouts yet")
            is LauncherAction.SwitchLayout -> switchLayout(action.id)
            LauncherAction.Today -> {
                closeAll()
                if (editMode) exitEdit()
                if (!data.study.loaded.value) data.study.refresh()
                data.study.requestSync()
                todaySheet = true
            }
            LauncherAction.TellClaude -> tellClaude()
            LauncherAction.StudyPlan -> openStudyPlan()
            LauncherAction.FocusTimer -> {
                val now = System.currentTimeMillis()
                val block = data.study.day.value.current(now)
                when {
                    block != null -> studyFocus(block)
                    data.study.focus.value != null -> scope.launch { data.study.stopFocus() }
                    else -> notice(dev.liquid.launcher.core.common.NoticeKind.Study, "No study block is running", "The focus timer belongs to a block", LauncherAction.Today)
                }
            }
            LauncherAction.Widgets -> {
                closeAll()
                editMode = true
                widgetPicker = true
            }
            is LauncherAction.Section -> callbacks.openSection(action.id)
        }
    }

    private fun global(action: Int) {
        if (!RedirectStatus.performGlobalAction(action)) toast("Needs the redirect service (Settings → Setup checklist)")
    }

    fun openSection(id: String) = callbacks.openSection(id)

    /** Static shortcuts may point at activities their app does not export; then only the default launcher can open them. */
    fun openShortcut(shortcut: dev.liquid.launcher.core.data.StaticShortcut) {
        try {
            LaunchClock.mark()
            context.startActivity(Intent(shortcut.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        } catch (e: SecurityException) {
            toast("The app only lets the system launcher open this shortcut")
        } catch (e: Exception) {
            DebugLog.error("Home", "shortcut failed: $e")
            toast("This shortcut could not be opened")
        }
    }

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

/** Past this fraction a released drawer opens. */
private const val OPEN_POINT = 0.4f

/** What the home screen asks the activity to do. */
class HomeCallbacks(
    val openSettings: () -> Unit,
    val openSetup: () -> Unit,
    /** Opens one settings section by its id. */
    val openSection: (String) -> Unit = { openSettings() },
    /** Shows the photo picker; the callback gets the picked image or null. */
    val pickImage: ((android.net.Uri?) -> Unit) -> Unit = { it(null) },
    /** Shows the photo picker for several images; the callback gets them (maybe none). */
    val pickImages: ((List<android.net.Uri>) -> Unit) -> Unit = { it(emptyList()) },
    /** Shows the system folder picker; the callback gets the folder or null. */
    val pickFolder: ((android.net.Uri?) -> Unit) -> Unit = { it(null) },
    /** Asks for calendar access (read and write); the callback gets whether reading was granted. */
    val requestCalendar: ((Boolean) -> Unit) -> Unit = { it(false) },
    /** Shows the system file picker for a font file; the callback gets it or null. */
    val pickFont: ((android.net.Uri?) -> Unit) -> Unit = { it(null) },
    /** Shows the system's dialog allowing the launcher to add a widget; the callback gets whether it was allowed. */
    val bindWidget: (android.content.Intent, (Boolean) -> Unit) -> Unit = { _, done -> done(false) },
    /** Opens a widget's own set-up screen; the callback gets whether it was completed. */
    val setUpWidget: (Int, (Boolean) -> Unit) -> Unit = { _, done -> done(true) },
)
