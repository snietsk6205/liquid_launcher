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
import kotlin.math.roundToInt

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

    /**
     * Whether the drawer's moves go to the debug log (0.10.4): not for the settings previews, whose loop opened and
     * closed the drawer every 1.7 s in Med's log. Set by the home screen.
     */
    var logMoves = true
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
        wallpaperStrip || clockStudio != null || todaySheet || widgetPicker || speedBump != null || reviewSheet != null

    // ---- home menu, wallpaper, layouts ----

    /** The home menu's glass pills are open. */
    var homeMenu by mutableStateOf(false)

    /**
     * 0.13.0 (D178): the quick review of the flashcards, over the home screen (from the pill's live item, or a card
     * found by search). The value is the card to start with ("" for the first due); null when closed.
     */
    var reviewSheet by mutableStateOf<String?>(null)

    /** Opens the quick review on [cardId] (or the first card due). */
    fun openReview(cardId: String = "") {
        if (drawerOpen) closeDrawer(immediately = true)
        homeMenu = false
        menu = null
        openFolder = null
        Haptics.tick(view)
        reviewSheet = cardId
    }

    /**
     * 0.13.0 (D178): the pill swells and settles like a drop when its live item changes (Settings → Pill → "Changes
     * shape when something happens"). Read while drawing only.
     */
    val livePop = Animatable(0f)

    fun popPill() {
        scope.launch {
            livePop.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = MotionScale.stiffness(300f)), initialVelocity = 9f)
        }
    }

    /** W8 (P8): the layout whose wallpaper is being chosen from the library; null when none. */
    var layoutWallpaperPick by mutableStateOf<String?>(null)

    /** W6: apps picked in the drawer for one action on all of them; null while not picking. */
    var selection by mutableStateOf<Set<AppRef>?>(null)

    /** W6: the tag sheet for the picked apps: true adds a tag to all, false takes one off all. */
    var bulkTag by mutableStateOf<Boolean?>(null)

    /** W6: the sheet listing every app with a check for this tag. */
    var tagApps by mutableStateOf<dev.liquid.launcher.core.data.Tag?>(null)

    fun startSelecting(first: AppRef) {
        menu = null
        Haptics.confirm(view)
        selection = setOf(first)
    }

    fun toggleSelected(ref: AppRef) {
        val now = selection ?: return
        Haptics.tick(view)
        selection = if (ref in now) now - ref else now + ref
    }

    fun endSelecting() {
        selection = null
        bulkTag = null
    }

    /** Every picked app on the home screen, then one notice. */
    fun addSelectionToHome() {
        val picked = selection ?: return
        var layout = data.layout.layout.value
        val (columns, rows) = gridSize()
        var placed = 0
        for (ref in picked) {
            if (layout.pages.any { page -> page.items.any { it.app == ref } }) continue
            val start = if (::pager.isInitialized) pager.currentPage else 0
            val order = (start until layout.pages.size) + (0 until start)
            val added = order.firstNotNullOfOrNull { page -> gridFor(page).let { (pc, pr) -> LayoutOps.addApp(layout, page, ref, pc, pr) } }
                ?: LayoutOps.addPage(layout).let { withPage -> LayoutOps.addApp(withPage, withPage.pages.size - 1, ref, columns, rows) }
                ?: continue
            layout = added
            placed++
        }
        if (placed > 0) data.layout.commit(layout)
        toast(if (placed == 0) "Already on the home screen" else if (placed == 1) "1 app added to home" else "$placed apps added to home")
        endSelecting()
    }

    fun hideSelection() {
        val picked = selection ?: return
        data.apps.setHidden(picked, true)
        toast(if (picked.size == 1) "1 app hidden from the drawer" else "${picked.size} apps hidden from the drawer")
        endSelecting()
    }

    /** W4: the "Customise" chip a long press on a surface shows: its settings page (a 0.9 section id), name, place. */
    var customise by mutableStateOf<CustomiseOffer?>(null)

    /**
     * A long press on the pill runs its own action (Settings by default; 0.10.1 showed "Customise the pill" instead,
     * which 0.10.2 undid). "Customise the pill" is one of the actions it can have: the chip then shows where the finger is.
     */
    fun longPressPill(action: LauncherAction, at: androidx.compose.ui.geometry.Offset?) {
        val bounds = pillBounds
        if (action == LauncherAction.CustomisePill && bounds != null && at != null) {
            offerCustomise("pill", "the pill", bounds.topLeft + at)
        } else {
            run(action)
        }
    }

    fun offerCustomise(section: String, title: String, at: androidx.compose.ui.geometry.Offset) {
        Haptics.confirm(view)
        customise = CustomiseOffer(section, title, at)
    }

    /** Finger position (root coordinates) while sliding from the long press onto a home menu item, or null. */
    var homeMenuFinger by mutableStateOf<Offset?>(null)

    /** Home menu items' bounds in root coordinates, by key, for press-and-slide. */
    internal val homeMenuTargets = HashMap<String, androidx.compose.ui.geometry.Rect>()
    internal var homeMenuRun: (String) -> Unit = {}

    var wallpaperStudio by mutableStateOf(false)

    /**
     * 0.16.0: Back while the wallpaper screen is open goes one step back inside it first (the peek, selecting, placing);
     * set by the screen. Returns true when it handled Back.
     */
    internal var wallpaperStudioBack: (() -> Boolean)? = null

    /** 0.16.0: a picture shared to the launcher ("Set as Liquid wallpaper"), picked by the wallpaper screen as it opens. */
    var sharedPick by mutableStateOf<dev.liquid.launcher.core.data.WallpaperItem?>(null)

    /** The clock gallery, open on one clock widget; the widget draws its draft style meanwhile. */
    var clockStudio by mutableStateOf<ClockStudioState?>(null)

    /** P7 (0.9.22): the date or battery widget whose "Customise" sheet is open (its item id). */
    var widgetOptions by mutableStateOf<String?>(null)

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

    // ---- the study hub (0.9.1): Today grows out of the pill and shrinks back into it ----

    /** 0 = closed (the pill), 1 = open; a little over 1 while the spring overshoots. Follows the finger while dragged. */
    val hub = Animatable(0f)
    val hubAmount: Float get() = hub.value.coerceIn(0f, 1f)

    /** W7: the open hub's height between collapsed (0: the block now or next) and the whole day (1), on its own spring. */
    val hubExpand = Animatable(1f)
    val hubExpanded: Boolean get() = hubExpand.targetValue > 0.5f

    fun expandHub(expand: Boolean) {
        if (hubExpanded == expand) return
        Haptics.tick(view)
        scope.launch {
            hubExpand.animateTo(
                if (expand) 1f else 0f,
                spring(dampingRatio = 0.82f, stiffness = MotionScale.stiffness(420f), visibilityThreshold = 0.001f),
                initialVelocity = hubExpand.velocity,
            )
        }
    }

    /** What the hub grows from and shrinks back into (root px): the pill, or what was tapped. Null: the pill's usual place. */
    var hubOrigin: androidx.compose.ui.geometry.Rect? = null

    /** The pill's bounds (root px), kept up to date by the pill. */
    var pillBounds: androidx.compose.ui.geometry.Rect? = null

    /** W2: the top of the pill while it is the drawer's field (root px), so sheets over the drawer end above it. */
    var drawerPillTop: Float? = null

    /** Finger travel in px between the hub closed and open, from its last layout. */
    var hubTravel = 1f

    /** Asks the hub's note field for the keyboard once it is open (double tap on the clock, D5). */
    var hubNoteFocus by mutableStateOf(false)

    /** Where the finger has put the hub, ahead of [hub] itself (its snaps run on the main thread's queue). */
    private var hubDragPos: Float? = null
    val hubDragging: Boolean get() = hubDragPos != null

    private fun hubSpring(opening: Boolean): androidx.compose.animation.core.SpringSpec<Float> {
        val bounce = data.settings.get(LauncherSettings.StudyHubBounce) / 100f
        return spring(
            dampingRatio = if (opening) (1f - 0.55f * bounce).coerceIn(0.42f, 1f) else 1f,
            stiffness = MotionScale.stiffness(if (opening) 360f else 480f),
            visibilityThreshold = 0.001f,
        )
    }

    /**
     * Opens Today as the study hub, growing out of [from] (root px; null: the pill). [velocity] in hub heights per
     * second, so a flick or an interrupted close hands its momentum to the spring instead of restarting from rest.
     */
    fun openHub(from: androidx.compose.ui.geometry.Rect? = null, velocity: Float? = null) {
        hubOrigin = from ?: pillBounds
        hubDragPos = null
        menu = null
        // 0.14.0 (D186): Today is one simple screen, always at its full height (W7's collapsed day is gone with the blocks).
        if (!todaySheet && hub.value < 0.05f) {
            scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { hubExpand.snapTo(1f) }
        }
        todaySheet = true
        scope.launch { hub.animateTo(1f, hubSpring(opening = true), initialVelocity = velocity ?: hub.velocity) }
    }

    fun closeHub(immediately: Boolean = false, velocity: Float? = null) {
        hubDragPos = null
        if (todaySheet) hideKeyboard()
        todaySheet = false
        hubNoteFocus = false
        scope.launch {
            if (immediately) hub.snapTo(0f) else hub.animateTo(0f, hubSpring(opening = false), initialVelocity = velocity ?: hub.velocity)
        }
    }

    /** Moves the hub by [deltaPx] of finger travel (down closes). Returns the px it used, for nested scrolling. */
    fun dragHubBy(deltaPx: Float): Float {
        val travel = hubTravel.coerceAtLeast(1f)
        val before = hubDragPos ?: hub.value.coerceIn(0f, 1f)
        val next = (before - deltaPx / travel).coerceIn(0f, 1f)
        if (next == before) return 0f
        hubDragPos = next
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { hub.snapTo(next) }
        return (before - next) * travel
    }

    /** Finger released while moving the hub: open or close by position and speed (px per second, down positive). */
    fun settleHub(velocityPxPerSec: Float) {
        val position = hubDragPos ?: hub.value
        val velocity = (-velocityPxPerSec / hubTravel.coerceAtLeast(1f)).coerceIn(-12f, 12f)
        val open = when {
            velocityPxPerSec > 900f -> false
            velocityPxPerSec < -900f -> true
            else -> position > 0.55f
        }
        if (open) openHub(hubOrigin, velocity) else closeHub(velocity = velocity)
    }

    /** Today from anywhere: the hub, grown out of [from] (the clock line, a notice) or the pill. */
    fun openToday(from: androidx.compose.ui.geometry.Rect? = null) {
        if (drawerOpen) closeDrawer(immediately = true)
        if (searchOpen || search.value > 0f) closeSearch(immediately = true)
        homeMenu = false
        wallpaperStrip = false
        layoutsSheet = false
        widgetPicker = false
        openFolder = null
        if (editMode) exitEdit()
        if (!data.study.loaded.value) data.study.refresh()
        data.study.requestSync()
        openHub(from)
    }

    // ---- Claude from the study companion (0.9.1, D1–D2) ----

    /** "[1A ESSAI · P2 · now: Micro: chapitre 3]" (D1); empty when the companion is off or the header is empty. */
    fun studyHeader(now: Long = System.currentTimeMillis()): String {
        val header = data.settings.get(LauncherSettings.StudyHeader).trim()
        if (header.isEmpty() || !data.study.enabled) return ""
        val block = data.study.day.value.current(now)?.takeIf { it.status == dev.liquid.launcher.core.data.StudyStatus.Open }
        return "[" + header + " · now: " + (block?.title?.text ?: "free") + "]"
    }

    /** [text] with the study header in front (D1). */
    fun withStudyHeader(text: String): String {
        val header = studyHeader()
        return if (header.isEmpty()) text else header + "\n" + text
    }

    /** A block as a few lines of context: title, time, steps, the start of its notes. */
    fun blockContext(block: dev.liquid.launcher.core.data.StudyEvent): String {
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        return buildString {
            append(block.title.text).append(" (").append(time.format(java.util.Date(block.begin))).append("–").append(time.format(java.util.Date(block.end))).append(")")
            block.tasks.forEach { append("\n").append(if (it.done) "[x] " else "[ ] ").append(it.text) }
            val notes = block.notes.trim()
            if (notes.isNotEmpty()) append("\n").append(notes.take(600))
        }
    }

    /** A prompt template (D2) with the header and, when there is one, the block it is about. */
    fun askTemplate(prompt: String, block: dev.liquid.launcher.core.data.StudyEvent?) {
        Haptics.tick(view)
        askClaude(withStudyHeader(prompt.trim() + (block?.let { "\n\n" + blockContext(it) } ?: "")))
    }

    /**
     * 0.14.0 (D186): the check-in, now the start of a study chat: the check-in message (Settings › Study companion, which
     * points Claude to the plan doc) and the day as context ([dev.liquid.launcher.core.data.Kickoff]), to Claude. Today's
     * "Study with Claude" also starts a 45-minute session on what was picked ([studyKickoff]); the evening line and the
     * search card only open the chat.
     */
    fun eveningCheckin() {
        Haptics.confirm(view)
        sendKickoff("", recommendedNow())
    }

    /** 0.14.0 (D186): Today's "Study with Claude": a 45-minute session on [subject] and the chat with the day as context. */
    fun studyKickoff(subject: String, recommended: List<String>) {
        Haptics.confirm(view)
        startStudySession(subject)
        sendKickoff(subject, recommended)
    }

    private fun sendKickoff(subject: String, recommended: List<String>) {
        data.study.markCheckin()
        val message = data.settings.get(LauncherSettings.StudyCheckin).trim()
        val date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.ENGLISH))
        val text = dev.liquid.launcher.core.data.Kickoff.context(
            message, data.study.day.value, subject, data.study.studiedToday.value, recommended,
            System.currentTimeMillis(), date, ::studyClock,
        )
        askClaude(withStudyHeader(text))
    }

    /** What Today recommends now (the ones not ticked), as words, for a check-in sent from elsewhere. */
    private fun recommendedNow(): List<String> {
        val day = data.study.day.value
        val now = System.currentTimeMillis()
        val today = java.time.LocalDate.now()
        val due = dev.liquid.launcher.core.data.FlashcardDeck.due(data.study.deck.value, today).size
        val ticked = dev.liquid.launcher.core.data.Recommend.ticked(data.settings.get(LauncherSettings.StudyRecommendDone), today.toString())
        val header = day.exams.firstOrNull { it.begin > now }?.key
        return dev.liquid.launcher.core.data.Recommend.items(day.feed, day.exams, day.unconfirmedExams, due, now, header)
            .filter { it.key !in ticked }.map { it.text }
    }

    // ---- arrival, shift tonight, quick notes (0.9.1, A2, A3, D5) ----

    /** Quick capture (D5): [text] becomes a "Note: …" all-day event in the task calendar. */
    fun addStudyNote(text: String, done: (Boolean) -> Unit = {}) {
        if (text.isBlank()) return
        scope.launch {
            val ok = data.study.addNote(text)
            if (ok) {
                Haptics.confirm(view)
                notice(dev.liquid.launcher.core.common.NoticeKind.Study, "Note saved for tonight's check-in", text.trim().take(70))
            } else {
                toast(if (!data.study.canWrite) "The launcher may not write to the calendar" else "No calendar for tasks found to write the note to")
            }
            done(ok)
        }
    }

    /** B3: present or not at [cls]; absent becomes a note for tonight's check-in. */
    fun answerAttendance(cls: dev.liquid.launcher.core.data.StudyEvent, present: Boolean) {
        if (present) Haptics.tick(view) else Haptics.confirm(view)
        scope.launch {
            val ok = data.study.answerAttendance(cls, present)
            if (!present) {
                if (ok) notice(dev.liquid.launcher.core.common.NoticeKind.Study, "Absent: ${cls.title.subject}", "Noted for tonight's check-in")
                else toast("Could not write the absence to the calendar")
            }
        }
    }

    /** E4: a flashcard of the deck, right or wrong (wrong comes back tomorrow and becomes a note for the check-in). */
    fun answerCard(card: dev.liquid.launcher.core.data.DeckCard, right: Boolean) {
        if (right) Haptics.tick(view) else Haptics.confirm(view)
        scope.launch {
            val ok = data.study.answerCard(card, right)
            if (!right && !ok) toast("Could not write the note to the calendar")
        }
    }

    /** The class running now or ended in the last 30 minutes (E1: what a board photo belongs to), else null. */
    fun classForPhoto(now: Long = System.currentTimeMillis()): dev.liquid.launcher.core.data.StudyEvent? =
        data.study.day.value.classes.lastOrNull { now >= it.begin - 10 * 60_000L && now <= it.end + 30 * 60_000L }

    /**
     * E1: the camera (the app chosen in Settings → Study companion, else the phone's default) for a photo saved in
     * Pictures/ESSAI/<module>/<date>_<module>.jpg: the module of the class now, else of the block now or next, else
     * "Notes". 0.11.2: the camera writes into a private file and the launcher saves it once it answers ([BoardPhotos]).
     */
    fun boardPhoto() {
        val now = System.currentTimeMillis()
        val module = classForPhoto(now)?.title?.subject ?: data.study.day.value.current(now)?.title?.subject ?: "Notes"
        // 0.15.3 (D196): the launcher's own camera, several photos in a row (a camera app when chosen in Settings).
        if (data.settings.get(LauncherSettings.StudyBoardCamera) != "app") {
            Haptics.tick(view)
            callbacks.openBoardCamera(module)
            return
        }
        val intent = try {
            dev.liquid.launcher.core.data.BoardPhotos.start(context, data.settings, module, now)
        } catch (e: Exception) {
            DebugLog.error("Home", "board photo: could not prepare the camera: $e")
            null
        } ?: return toast("No camera app to take the photo")
        Haptics.tick(view)
        callbacks.takePhoto(intent)
    }

    /**
     * A4 / C4 (0.9.11): the commute layout from the end of the last class until home, the exam layout in the week before
     * an exam, and back to the layout from before afterwards (not if it was changed by hand meanwhile). Runs on the
     * home screen's minute tick; does nothing until the calendar has been read.
     */
    fun applyContextLayout() {
        val s = data.settings
        val commuteId = s.get(LauncherSettings.StudyCommuteLayout).takeIf { it.isNotBlank() && data.layouts.find(it) != null }
        val examId = s.get(LauncherSettings.StudyExamLayout).takeIf { it.isNotBlank() && data.layouts.find(it) != null }
        val windowId = s.get(LauncherSettings.LayoutWindowLayout).takeIf { it.isNotBlank() && data.layouts.find(it) != null }
        val active = s.get(LauncherSettings.ContextActive).ifBlank { null }
        if (commuteId == null && examId == null && windowId == null && active == null) return
        // The study contexts wait for the calendar; a time of day does not.
        val studyReady = data.study.enabled && data.study.loaded.value
        if (!studyReady && active != null && active != dev.liquid.launcher.core.data.ContextLayouts.WINDOW) return
        val now = System.currentTimeMillis()
        val studyCtx = if (studyReady) {
            dev.liquid.launcher.core.data.ContextLayouts.context(data.study.day.value, now, data.study.homeAt.value, commuteId != null, examId != null)
        } else {
            null
        }
        val ctx = studyCtx ?: dev.liquid.launcher.core.data.ContextLayouts.WINDOW.takeIf {
            windowId != null && dev.liquid.launcher.core.data.ContextLayouts.inWindow(
                s.get(LauncherSettings.LayoutWindowFrom), s.get(LauncherSettings.LayoutWindowTo), s.get(LauncherSettings.LayoutWindowDays),
                java.time.LocalDateTime.now(),
            )
        }
        val wanted = when (ctx) {
            dev.liquid.launcher.core.data.ContextLayouts.COMMUTE -> commuteId
            dev.liquid.launcher.core.data.ContextLayouts.EXAM -> examId
            dev.liquid.launcher.core.data.ContextLayouts.WINDOW -> windowId
            else -> null
        }
        val current = data.layouts.state.value.current
        val step = dev.liquid.launcher.core.data.ContextLayouts.step(
            active, ctx, current, wanted,
            s.get(LauncherSettings.ContextApplied).ifBlank { null }, s.get(LauncherSettings.ContextBase).ifBlank { null },
        )
        when (step) {
            dev.liquid.launcher.core.data.ContextLayouts.Step.Stay -> Unit
            is dev.liquid.launcher.core.data.ContextLayouts.Step.Enter -> {
                s.set(LauncherSettings.ContextActive, step.context)
                s.set(LauncherSettings.ContextApplied, step.layout)
                s.set(LauncherSettings.ContextBase, step.base.orEmpty())
                DebugLog.event("Home", "context layout: ${step.context}")
                if (current != step.layout) switchLayout(step.layout)
            }
            is dev.liquid.launcher.core.data.ContextLayouts.Step.Leave -> {
                s.reset(LauncherSettings.ContextActive)
                s.reset(LauncherSettings.ContextApplied)
                s.reset(LauncherSettings.ContextBase)
                DebugLog.event("Home", "context layout over")
                step.layout?.let { back -> if (back != current && data.layouts.find(back) != null) switchLayout(back) }
            }
        }
    }

    /** P8 (0.9.21): switches to [id] for [minutes], then back to the layout on now (see [checkLayoutTimer]). */
    fun switchLayoutFor(id: String, minutes: Int) {
        val s = data.settings
        val back = data.layouts.state.value.current
        if (back == null || back == id) {
            switchLayout(id)
            return
        }
        val until = System.currentTimeMillis() + minutes * 60_000L
        s.set(LauncherSettings.LayoutTimerUntil, until.toString())
        s.set(LauncherSettings.LayoutTimerLayout, id)
        s.set(LauncherSettings.LayoutTimerBack, back)
        DebugLog.event("Home", "layout $id for $minutes min, then $back")
        switchLayout(id)
    }

    /** P8: the timed layout's time is up: back to the one before, unless another layout was chosen meanwhile. */
    fun checkLayoutTimer() {
        val s = data.settings
        val until = s.get(LauncherSettings.LayoutTimerUntil).toLongOrNull() ?: return
        val timed = s.get(LauncherSettings.LayoutTimerLayout)
        val back = s.get(LauncherSettings.LayoutTimerBack)
        val now = System.currentTimeMillis()
        val current = data.layouts.state.value.current
        val to = dev.liquid.launcher.core.data.LayoutTimer.due(now, until, timed, back, current)
        if (dev.liquid.launcher.core.data.LayoutTimer.stale(now, until, timed, current)) cancelLayoutTimer()
        if (to != null && data.layouts.find(to) != null) {
            DebugLog.event("Home", "timed layout over: back to $to")
            switchLayout(to)
        }
    }

    /** P8: keeps the timed layout ("Keep it"). */
    fun cancelLayoutTimer() {
        val s = data.settings
        s.reset(LauncherSettings.LayoutTimerUntil)
        s.reset(LauncherSettings.LayoutTimerLayout)
        s.reset(LauncherSettings.LayoutTimerBack)
    }

    /** "I'm home" (A2, by hand). */
    fun markHomeNow() {
        Haptics.confirm(view)
        scope.launch {
            val at = data.study.markHome(how = "manual")
            notice(dev.liquid.launcher.core.common.NoticeKind.Study, "Home $at", "Noted on tonight's first block")
        }
    }

    /** Moves tonight's blocks by [plan] (A3). */
    fun applyShift(plan: dev.liquid.launcher.core.data.ShiftPlan) {
        Haptics.confirm(view)
        data.study.answerShift()
        scope.launch {
            val moved = data.study.applyShift(plan)
            if (moved > 0) {
                val cut = plan.shortened?.let { "${it.title.subject} shortened by ${plan.shortenedMinutes} min" }
                notice(dev.liquid.launcher.core.common.NoticeKind.Study, "Tonight moved +${plan.deltaMinutes} min", cut ?: (if (moved == 1) "1 block moved" else "$moved blocks moved"))
            } else {
                toast("Could not move the blocks: the launcher may not write to the calendar")
            }
        }
    }

    /** Keeps tonight as planned: the shift is not offered again today. */
    fun keepEvening() {
        Haptics.tick(view)
        data.study.answerShift()
    }

    /** Remembers the Wi-Fi the phone is on as home (A2). Android only tells an app its name with location access. */
    fun rememberHomeWifi() {
        callbacks.requestLocation { granted ->
            val ssid = if (granted) data.study.currentWifi() else null
            when {
                !granted -> toast("Without location access Android hides the Wi-Fi name")
                ssid == null -> toast("Connect to your home Wi-Fi with location on, then try again")
                else -> {
                    data.settings.set(LauncherSettings.StudyHomeWifi, ssid)
                    notice(dev.liquid.launcher.core.common.NoticeKind.Study, "Home Wi-Fi: $ssid", "Getting home after the last class is noticed by it")
                }
            }
        }
    }

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

    /**
     * 0.14.0 (D184): a 45-minute study session on [subject], from Today's "Study with Claude" (a focus or session running
     * is stopped first). The pill counts it down, the line under the clock says what is studied, and the speed bump holds
     * distracting apps back while it runs.
     */
    fun startStudySession(subject: String) {
        scope.launch {
            if (data.study.focus.value != null) data.study.stopFocus()
            data.study.startSession(subject)
            DebugLog.event("Study", "session: ${subject.ifBlank { "(no subject)" }}, ${dev.liquid.launcher.core.data.StudyFocus.SESSION_MINUTES} min")
        }
    }

    /** 0.14.0: ends the study session now (the ✓ on the line under the clock). */
    fun stopStudySession() {
        Haptics.confirm(view)
        scope.launch {
            val minutes = data.study.stopFocus()
            notice(dev.liquid.launcher.core.common.NoticeKind.Study, "Session done: $minutes min", "${data.study.studiedToday.value} min studied today")
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
        fun tab(): Intent? = customTab(uri)
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

    /**
     * A Custom Tab: Chrome's own view of the page over the launcher, signed in like Chrome.
     * Built by hand (the extras of androidx.browser), so no library is needed for it.
     */
    private fun customTab(uri: Uri): Intent? {
        val pm = context.packageManager
        val browsers = try {
            pm.queryIntentServices(Intent("android.support.customtabs.action.CustomTabsService"), 0).map { it.serviceInfo.packageName }
        } catch (e: Exception) {
            emptyList()
        }
        val pkg = listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.brave.browser").firstOrNull { it in browsers } ?: browsers.firstOrNull() ?: return null
        return Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(pkg).apply {
            putExtras(android.os.Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
            putExtra("android.support.customtabs.extra.TITLE_VISIBILITY", 1)
            putExtra("android.support.customtabs.extra.SHARE_STATE", 1)
        }
    }

    // ---- Claude (0.7.2, D109) ----

    /** Search sends what is typed to Claude: the field's icon switches it, the "Ask Claude" action opens search this way. */
    var askClaudeMode by mutableStateOf(false)

    /** 0.10.2: the search page's "Today · …" line opened onto the calendar month and the exam countdown. */
    var searchGlanceOpen by mutableStateOf(false)

    /** The Claude app's package (the one "Tell Claude" uses). */
    val claudePackage: String get() = data.settings.get(LauncherSettings.StudyClaudeApp).trim().ifEmpty { "com.anthropic.claude" }

    /**
     * Sends [text] to Claude: into a new chat of the Claude app (it arrives as shared text, ready to
     * send or add to), or claude.ai with the question filled in when the app is not there or
     * "Ask Claude in" says so.
     */
    fun askClaude(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty()) return
        DebugLog.event("Home", "ask Claude (" + prompt.length + " characters)")
        if (data.settings.get(LauncherSettings.SearchClaudeTarget) == "app") {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, prompt).setPackage(claudePackage)
            if (send.resolveActivity(context.packageManager) != null) {
                start(send)
                return
            }
        }
        val uri = Uri.parse("https://claude.ai/new?q=" + Uri.encode(prompt))
        start(customTab(uri) ?: Intent(Intent.ACTION_VIEW, uri))
    }

    /** Starts a chat with Claude about a study block: its title, time, steps and notes. */
    fun askClaudeAbout(block: dev.liquid.launcher.core.data.StudyEvent) {
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        val steps = block.tasks.joinToString("\n") { (if (it.done) "[x] " else "[ ] ") + it.text }
        val prompt = buildString {
            append("I'm about to study this block and would like your help with it.\n\n")
            append(block.title.text).append(" (").append(time.format(java.util.Date(block.begin))).append("–").append(time.format(java.util.Date(block.end))).append(")\n")
            if (steps.isNotEmpty()) append("\nSteps:\n").append(steps).append('\n')
            if (block.notes.isNotBlank()) append("\nNotes:\n").append(block.notes.trim()).append('\n')
            append("\nHow should I go about it?")
        }
        askClaude(prompt)
    }

    /** Opens search ready to ask Claude: what is typed goes to Claude on Go. */
    fun openAskClaude() {
        askClaudeMode = true
        openSearch()
    }

    /** P5 (0.9.15): the drawer field hands a command (!next, !exam, !plan, !abs) or a question (?) to the search page. */
    fun handOverToSearch(text: String) {
        DebugLog.event("Home", "drawer field: \"$text\" handed to search")
        drawerQuery = ""
        searchQuery = text
        closeDrawer()
        openSearch()
    }

    /**
     * True when text was copied in the last [withinMs]. Only the clip's description is looked at,
     * which Android does not report as reading the clipboard; the text is read when it is used.
     */
    fun recentClip(withinMs: Long = 120_000L): Boolean = try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val description = clipboard.primaryClipDescription
        description != null &&
            (description.hasMimeType("text/*") || description.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN)) &&
            System.currentTimeMillis() - description.timestamp in 0..withinMs
    } catch (e: Exception) {
        false
    }

    /** The copied text, or null. Android shows that the launcher read the clipboard. */
    fun clipText(): String? = try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
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
    fun morph(): Float = maxOf(drawer.value, if (searchInPill) search.value else 0f)

    // ---- where search's field goes (0.7.2, D107) ----

    /** Search grows the pill into its field (false: the field is at the top and the pill steps away). Set by the home screen. */
    var searchInPill by mutableStateOf(true)

    /** "keyboard", "bottom" or "top" (Settings → Search → Search field). Set by the home screen. */
    var searchField by mutableStateOf("keyboard")

    /** The keyboard comes with search (Settings → Search → Open the keyboard). Set by the home screen. */
    var searchKeyboard by mutableStateOf(true)

    /**
     * The heights the keyboard opened at lately (px from the bottom of the screen, navigation bar included), newest
     * last, remembered across starts. 0.11.2 (Med: "never above"): the field waits at the lowest of them before the
     * keyboard is there, and the keyboard pushes it up from there ([KeyboardLine]); up to 0.11.1 it went to the last
     * height, and came back down whenever the keyboard opened lower. Starts from 0.11.1's one height.
     */
    private var keyboardOpenings by mutableStateOf(
        data.settings.get(LauncherSettings.SearchKeyboardOpenings).mapNotNull { it.toIntOrNull() }
            .ifEmpty { listOfNotNull(data.settings.get(LauncherSettings.SearchKeyboardHeight).takeIf { it > 0 }) },
    )

    /** Where the field waits for the keyboard: the lowest recent opening (or a low first guess). */
    val keyboardGuess: Float get() = dev.liquid.launcher.core.common.KeyboardLine.guess(keyboardOpenings, screenHeight)

    /**
     * 0.13.1: the keyboard's height is known from this phone (its picture, or an opening seen), not only the low first
     * guess: only then is a keyboard that says it is much taller doubted ([dev.liquid.launcher.core.common.KeyboardDrive.believes]).
     */
    val keyboardHeightKnown: Boolean get() = KeyboardPicture.height > 0 || keyboardOpenings.any { it > 0 }

    /**
     * Search is counting on the keyboard: from the moment it starts opening until the keyboard is
     * put away while search stays open (then the field follows the keyboard down).
     */
    var expectKeyboard by mutableStateOf(false)

    /**
     * The keyboard settled at [px]: an [opening] is added to the recent openings; a later settle while it stays open
     * only lowers the newest (a strip that went away), never raises it (0.11.2, [KeyboardLine.record]).
     */
    fun rememberKeyboard(px: Float, opening: Boolean) {
        val next = dev.liquid.launcher.core.common.KeyboardLine.record(keyboardOpenings, px.roundToInt(), opening, screenHeight)
        if (next == keyboardOpenings) return
        keyboardOpenings = next
        data.settings.set(LauncherSettings.SearchKeyboardOpenings, next.map { it.toString() })
        data.settings.set(LauncherSettings.SearchKeyboardHeight, px.roundToInt())
    }

    /**
     * Where the bottom of the open search field sits now, in px from the bottom of the screen, before its gap: on the
     * keyboard or the navigation bar, never above them (0.11.3: the launcher slides the keyboard in with the pill, and
     * the field rides on it; [KeyboardLine.ride]). In a settings preview the keyboard is a plate at the usual height.
     */
    fun searchFieldLine(nav: Float, ime: Float, @Suppress("UNUSED_PARAMETER") target: Float): Float =
        dev.liquid.launcher.core.common.KeyboardLine.ride(nav, previewIme(ime))

    /**
     * Where the bottom of the open search field will end up, for laying search's results out above it (they then stay
     * still while the keyboard slides in): the keyboard's height once known, else the lowest recent opening.
     */
    fun searchFieldPlanned(nav: Float, ime: Float, target: Float): Float {
        if (!expectKeyboard) return maxOf(nav, previewIme(ime))
        if (keyboardRise > 0f) return maxOf(nav, previewIme(ime), keyboardRise)
        return dev.liquid.launcher.core.common.KeyboardLine.planned(nav, previewIme(ime), target, keyboardDriveShown, keyboardGuess)
    }

    /** A settings preview has no keyboard: the plate it shows instead slides in with search, at the usual height. */
    private fun previewIme(ime: Float): Float =
        if (live || !expectKeyboard) ime else (if (keyboardRise > 0f) keyboardRise else keyboardGuess) * search.value.coerceIn(0f, 1f)

    /** False for a settings preview (set by the home screen): it never asks for a keyboard and shows a plate instead. */
    var live by mutableStateOf(true)

    /** 0.11.3: the open search pill's size and its gap above the keyboard (Settings → Search → Layout). Set by the home screen. */
    var searchFieldSize by mutableStateOf(SearchFieldSize())

    /**
     * 0.11.3: how the keyboard came for the current opening of search: "drive" (the launcher slides it in with the
     * pill), "usual" (shown the usual way, the field rides on it), or null (not asked yet).
     */
    var keyboardPath by mutableStateOf<String?>(null)

    /** The keyboard's height fully up, from the system, while the launcher drives it (0: not known). */
    var keyboardDriveShown by mutableFloatStateOf(0f)

    /** Counts openings of search that count on the keyboard: each starts one drive. */
    var keyboardSession by mutableIntStateOf(0)
        private set

    private fun startExpectingKeyboard() {
        expectKeyboard = searchKeyboard && searchField == "keyboard" && searchInPill && !speedChecking
        keyboardSteady = false
        if (expectKeyboard) {
            keyboardPath = null
            keyboardDriveShown = 0f
            keyboardDriving = false
            riseGlide?.cancel()
            // 0.13.0 (D175): the keyboard rises with the pill from the first frame, at the height of its picture (or
            // the height it usually opens at): its picture until it is ready, then the keyboard itself.
            keyboardRise = KeyboardPicture.height.takeIf { it > 0 }?.toFloat() ?: keyboardGuess
            keyboardStandInOn = live
            keyboardSession++
        }
    }

    /**
     * 0.13.0 (D175): while search opens with the keyboard, the height the keyboard rises to (px from the bottom of the
     * screen); the pill goes only up, from where it rests to just above that height, on search's own spring, and the
     * keyboard (its picture until it is ready) rises in step under it. 0: not rising (the field rides on the keyboard).
     */
    var keyboardRise by mutableFloatStateOf(0f)

    /** The keyboard's picture stands in for it (from the swipe's first frame until the keyboard itself is in place). */
    var keyboardStandInOn by mutableStateOf(false)

    /** The keyboard was put away or never came: search stops planning around it. */
    fun stopKeyboardRise() {
        riseGlide?.cancel()
        keyboardRise = 0f
        keyboardStandInOn = false
        keyboardSteady = false
    }

    /**
     * 0.15.0 (D188): search is open and the keyboard stands up under the pill, handed back to the system (or shown the
     * usual way and settled). While steady, the pill keeps to the line the keyboard settled at ([keyboardRise]): a
     * keyboard that says it is taller for a moment (WM Keyboard rebuilding itself, 25 ms on Med's phone) no longer throws
     * the pill up and down; one that stays taller is followed after [dev.liquid.launcher.core.common.KeyboardDrive.GROW_SETTLE_MS],
     * on a glide. False while search opens or closes, where the pill rides on the keyboard as it moves.
     */
    var keyboardSteady by mutableStateOf(false)

    /** The keyboard line the pill sits on now (see [keyboardSteady]), px from the bottom of the screen. */
    fun pillKeyboardLine(ime: Float): Float =
        dev.liquid.launcher.core.common.KeyboardDrive.steadyLine(ime, keyboardRise, keyboardSteady && searchOpen)

    /**
     * 0.13.1: the launcher holds the keyboard right now (between the drive's "ready" and its hand-back), so its height
     * fully up is [keyboardDriveShown], not what the system's own animation would report.
     */
    var keyboardDriving by mutableStateOf(false)

    private var riseGlide: kotlinx.coroutines.Job? = null

    /** 0.15.2: the rise is gliding to a new height (the keyboard is not handed back meanwhile). */
    val keyboardRiseGliding: Boolean get() = riseGlide?.isActive == true

    /**
     * 0.13.1 (Med: "after I change the wallpaper the pill goes higher than intended the first time"): moves [keyboardRise]
     * to [px] on a short spring without bounce instead of in one frame, so the pill (and the keyboard while the launcher
     * holds it) glides to a height that turned out different: the keyboard's real height once it is ready, or where it
     * settled while search stays open. Not rising: set at once.
     */
    /** 0.15.0: the rise set at once (the pill already stands there, riding on the keyboard), any glide stopped. */
    fun settleKeyboardRise(px: Float) {
        riseGlide?.cancel()
        if (keyboardRise > 0f && px > 0f) keyboardRise = px
    }

    fun glideKeyboardRise(px: Float) {
        riseGlide?.cancel()
        val from = keyboardRise
        if (from <= 0f || px <= 0f || kotlin.math.abs(from - px) < 1f) {
            keyboardRise = px
            return
        }
        riseGlide = scope.launch {
            // 0.15.2 (D194): over within half a pixel (it then lands exactly): the keyboard is handed back only once the
            // glide is over, and a spring's last hundredth of a pixel took a third of its time (about 170 ms).
            androidx.compose.animation.core.animate(
                from, px,
                animationSpec = spring(dampingRatio = 1f, stiffness = MotionScale.stiffness(650f), visibilityThreshold = 0.5f),
            ) { value, _ -> if (keyboardRise > 0f) keyboardRise = value }
        }
    }

    /** 0.11.2: the speed check is playing (Settings → Diagnostics): search opens without the keyboard. */
    var speedChecking by mutableStateOf(false)
        private set

    /**
     * 0.11.2, Settings → Diagnostics → Speed check: the drawer, search, the home menu and the study hub opened and closed
     * with each way of drawing the glass ([dev.liquid.launcher.core.designsystem.GlassTest]), each step a line of its
     * own in the debug log from the frame meter: what each part of the glass costs on this phone, measured.
     */
    fun runSpeedCheck() {
        if (speedChecking) return
        speedChecking = true
        scope.launch {
            val glassTest = dev.liquid.launcher.core.designsystem.GlassTest
            notice(dev.liquid.launcher.core.common.NoticeKind.Info, "Speed check: about a minute", "Leave the screen alone; the numbers go to the debug log")
            DebugLog.event("Frames", "speed check: started")
            try {
                kotlinx.coroutines.delay(2500)
                if (drawer.value > 0f) closeDrawer(immediately = true)
                if (searchOpen) closeSearch(immediately = true)
                suspend fun step(what: String, variant: dev.liquid.launcher.core.designsystem.GlassTest.Variant, block: suspend () -> Unit) {
                    FrameCost.check = "speed check: $what, ${variant.label}"
                    block()
                    FrameCost.check = null
                    kotlinx.coroutines.delay(200)
                }
                for (variant in dev.liquid.launcher.core.designsystem.GlassTest.Variant.entries) {
                    glassTest.variant = variant
                    kotlinx.coroutines.delay(600)
                    step("drawer", variant) {
                        openDrawer()
                        kotlinx.coroutines.delay(900)
                        closeDrawer()
                        kotlinx.coroutines.delay(900)
                    }
                    step("search", variant) {
                        openSearch()
                        kotlinx.coroutines.delay(900)
                        closeSearch()
                        kotlinx.coroutines.delay(900)
                    }
                    step("home menu", variant) {
                        homeMenu = true
                        kotlinx.coroutines.delay(800)
                        homeMenu = false
                        kotlinx.coroutines.delay(700)
                    }
                    if (data.study.enabled) {
                        step("study hub", variant) {
                            openHub()
                            kotlinx.coroutines.delay(1100)
                            closeHub()
                            kotlinx.coroutines.delay(900)
                        }
                    }
                }
                DebugLog.event("Frames", "speed check: done")
                notice(dev.liquid.launcher.core.common.NoticeKind.Info, "Speed check done", "Send the debug log (Settings → Diagnostics → Debug log)")
            } finally {
                FrameCost.check = null
                glassTest.variant = dev.liquid.launcher.core.designsystem.GlassTest.Variant.AsSet
                speedChecking = false
            }
        }
    }

    /** Where the finger has put search, ahead of [search] (see [dragPosition]). */
    private var searchDragPosition: Float? = null

    /**
     * A finger is moving search (0.11.3: the keyboard drive waits for it to let go before handing the keyboard over).
     * 0.15.0: snapshot state, so the keyboard can start following a finger that pushes open search away.
     */
    var searchDragging by mutableStateOf(false)
        private set

    /** Where the bottom of search's field was at the last layout, px from the bottom of the screen (for the log; not state). */
    var fieldBottomPx = 0f

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

    /** 0.16.0: the style of the change running (a layout switch can have its own, D202); read while it plays. */
    var transitionStyle by mutableStateOf("ripple")
        private set

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
    fun <T : Any> changeScreen(prepare: suspend () -> T?, commit: (T) -> Unit, after: suspend (T) -> Unit = {}, style: String? = null) {
        transitionJob?.cancel()
        transitionJob = scope.launch {
            val value = prepare() ?: return@launch
            val style = style ?: data.settings.get(LauncherSettings.WallpaperTransition)
            transitionStyle = style
            // 0.16.0: the liquid wipe grows out of the pill.
            if (style == "pill") transitionOrigin = pillBounds?.center
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
                store.persist(bitmap, Wallpapers.lockToo(data), switch.file, Wallpapers.systemWrite(data))
                switch.wallpaperId?.let { wid -> data.wallpapers.find(wid)?.let { Wallpapers.markUsed(data, it.id, null) } }
            },
            // 0.16.0 (D202): "When the layout switches" (by default the wallpaper's transition).
            style = data.settings.get(LauncherSettings.LayoutsTransition).takeIf { it != "same" },
        )
    }

    /**
     * Makes [pick] the wallpaper with the transition (wallpaper strip, shuffle). 0.16.0 (D200): a glass notice offers
     * Undo for 10 s ([noticeText], with the label's own [noticeDetail] and [noticeAction] as before).
     */
    fun applyWallpaper(
        pick: WallpaperPick,
        noticeText: String = "New wallpaper",
        noticeDetail: String? = null,
        noticeAction: LauncherAction? = null,
    ) {
        dev.liquid.launcher.core.common.ColourTiming.begin("wallpaper switch")
        changeScreen(
            prepare = {
                val prepared = Wallpapers.prepare(context, data, pick) ?: run { toast("Could not read that picture"); null }
                if (prepared != null) keepForUndo((pick as? WallpaperPick.Library)?.item?.id, Wallpapers.ownLayout(data))
                prepared
            },
            commit = { prepared ->
                WallpaperStore.get(context).show(prepared.bitmap, prepared.tone)
                IconPaint.now(prepared.palette)
                HomeTheme.refreshAccent(context)
                dev.liquid.launcher.core.common.ColourTiming.mark("wallpaper and colours on screen")
            },
            after = { prepared ->
                offerWallpaperUndo(noticeText, noticeDetail, noticeAction)
                Wallpapers.persist(context, data, prepared)?.let { toast("Could not set the wallpaper: $it") }
            },
        )
    }

    fun shuffleWallpaper() {
        scope.launch {
            val pick = Wallpapers.next(data)
            if (pick == null) {
                notice(dev.liquid.launcher.core.common.NoticeKind.Info, "Nothing to shuffle yet", "Add favourites in the wallpaper screen", LauncherAction.WallpaperStudio)
                return@launch
            }
            applyWallpaper(pick, "Wallpaper shuffled", "Tap for the wallpaper strip", LauncherAction.Wallpaper)
        }
    }

    // ---- 0.16.0 (D200): Undo after a wallpaper change ----

    /** What the last wallpaper change replaced, while its Undo is offered. */
    private class WallpaperUndo(
        /** The wallpaper as it was: the finished picture, placement included. */
        val file: java.io.File,
        /** The library picture it was made from. */
        val current: String?,
        /** The record of the picture the change applied, as it was before (its placement and use count). */
        val before: dev.liquid.launcher.core.data.WallpaperItem?,
        /** A layout whose own wallpaper the change replaced, and a copy of that wallpaper (null: it had none). */
        val layoutId: String?,
        val layoutFile: java.io.File?,
        val layoutWallpaperId: String?,
    ) {
        /** Uptime when the Undo was offered (0: not yet). */
        var offeredAt = 0L
    }

    private var wallpaperUndo: WallpaperUndo? = null
    private var undoExpiry: kotlinx.coroutines.Job? = null

    /**
     * Keeps what a wallpaper change is about to replace: the wallpaper as it is now, the library's current picture, the
     * record of the picture applied ([appliedId]) and, when the change also replaces [layoutId]'s own wallpaper, that one.
     * Call before anything is written (in the change's prepare step).
     */
    internal suspend fun keepForUndo(appliedId: String?, layoutId: String?) {
        val dir = java.io.File(context.cacheDir, "wallpaper-undo")
        val file = java.io.File(dir, "wallpaper.jpg")
        if (!WallpaperStore.get(context).copyCurrent(file)) {
            wallpaperUndo = null
            return
        }
        val layout = layoutId?.let { data.layouts.find(it) }
        val layoutCopy = layout?.let { data.layouts.wallpaperFile(it) }?.let { source ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { source.copyTo(java.io.File(dir, "layout.jpg"), overwrite = true) }.getOrNull()
            }
        }
        val library = data.wallpapers.get()
        wallpaperUndo = WallpaperUndo(file, library.current, data.wallpapers.snapshot(appliedId), layout?.id, layoutCopy, layout?.wallpaperId)
    }

    /**
     * After a wallpaper change: a glass notice with Undo for [UNDO_MS] (when wallpaper notices show), and the hand-over
     * to the system waits that long, so an Undo never costs a second one. Call before the new wallpaper is written.
     */
    internal fun offerWallpaperUndo(text: String, detail: String? = null, action: LauncherAction? = null) {
        val undo = wallpaperUndo ?: return
        val notices = data.settings.noticeConfig()
        if (!notices.enabled || dev.liquid.launcher.core.common.NoticeKind.Wallpaper.id !in notices.kinds) {
            wallpaperUndo = null
            return
        }
        undo.offeredAt = android.os.SystemClock.uptimeMillis()
        val store = WallpaperStore.get(context)
        store.holdSystemWrite(UNDO_MS)
        dev.liquid.launcher.core.common.Notices.post(
            dev.liquid.launcher.core.common.Notice(
                dev.liquid.launcher.core.common.NoticeKind.Wallpaper, text, detail, action?.encode(),
                buttonLabel = "Undo", button = { undoWallpaper() }, showMs = UNDO_MS, holds = true,
            )
        )
        undoExpiry?.cancel()
        undoExpiry = scope.launch {
            kotlinx.coroutines.delay(UNDO_MS + 1_000)
            if (wallpaperUndo === undo) wallpaperUndo = null
            store.releaseSystemWrite()
        }
    }

    /** Undo of the last wallpaper change: the wallpaper, its placement and the library's records come back, with the transition. */
    fun undoWallpaper() {
        val undo = wallpaperUndo ?: return toast("Too late to undo")
        wallpaperUndo = null
        undoExpiry?.cancel()
        val store = WallpaperStore.get(context)
        DebugLog.event("Wallpaper", "undo: back to the wallpaper before")
        changeScreen(
            prepare = {
                val bitmap = store.decodeFile(undo.file) ?: return@changeScreen run { toast("Could not bring the wallpaper back"); null }
                val tone = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { dev.liquid.launcher.core.common.WallpaperTones.fromBitmap(bitmap) }
                Triple(bitmap, tone, tone?.let { data.paletteFor(it.primary) })
            },
            commit = { (bitmap, tone, palette) ->
                store.show(bitmap, tone)
                IconPaint.now(palette)
                HomeTheme.refreshAccent(context)
            },
            after = { (bitmap, _, _) ->
                store.persist(bitmap, Wallpapers.lockToo(data), undo.file, Wallpapers.systemWrite(data))
                store.releaseSystemWrite()
                data.wallpapers.undoApply(undo.current, undo.before)
                val layoutId = undo.layoutId
                if (layoutId != null) {
                    if (undo.layoutFile != null) data.layouts.setWallpaper(layoutId, undo.layoutFile, undo.layoutWallpaperId)
                    else data.layouts.setWallpaper(layoutId, null)
                }
            },
        )
    }

    /**
     * 0.16.0: pictures shared to "Set as Liquid wallpaper" (or "Set as" in a gallery) go into the library, and the
     * wallpaper screen opens with the first one picked, ready to Place or Apply.
     */
    fun importShared(uris: List<android.net.Uri>) {
        scope.launch {
            val items = uris.mapNotNull { data.wallpapers.importUri(it) }
            DebugLog.event("Wallpaper", "shared: ${items.size} of ${uris.size} picture(s) added to the library")
            if (items.isEmpty()) {
                toast("Could not read that picture")
                return@launch
            }
            if (!wallpaperStudio) closeAll(immediately = true)
            sharedPick = items.first()
            wallpaperStudio = true
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
    fun openDrawer(velocity: Float = Float.NaN) {
        dragPosition = null
        menu = null
        if (searchOpen || search.value > 0f) closeSearch(immediately = true)
        if (logMoves) DebugLog.event("Home", "drawer: open from " + "%.2f".format(drawer.value))
        // Without a finger's speed, the spring keeps the speed it has: an interrupted close turns round without stopping (0.9.3).
        val v = if (velocity.isNaN()) drawer.velocity else velocity
        scope.launch { drawer.animateTo(1f, drawerSpring(), initialVelocity = v) }
    }

    fun closeDrawer(immediately: Boolean = false, velocity: Float = Float.NaN) {
        dragPosition = null
        if (logMoves) DebugLog.event("Home", "drawer: close from " + "%.2f".format(drawer.value))
        drawerFocusReset++
        drawerQuery = ""
        tagSheet = false
        if (!data.settings.get(LauncherSettings.TagFilterKeep)) tagFilter = emptySet()
        val v = if (velocity.isNaN()) drawer.velocity else velocity
        scope.launch {
            if (immediately) drawer.snapTo(0f) else drawer.animateTo(0f, drawerSpring(), initialVelocity = v)
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
        val open = when {
            velocityPxPerSec < -800f -> true
            velocityPxPerSec > 800f -> false
            else -> position > OPEN_POINT
        }
        // 0.15.2: with what the release does, so a log (and the emulator test) can tell a swipe too short and slow apart.
        DebugLog.event(
            "Home",
            "drawer: released at " + "%.2f".format(position) + ", " + velocityPxPerSec.toInt() + " px/s: " + if (open) "opens" else "closes",
        )
        // Upward finger speed is negative on screen and positive for the drawer.
        val velocity = (-velocityPxPerSec / (screenHeight * drawerTravel())).coerceIn(-14f, 14f)
        if (open) openDrawer(velocity) else closeDrawer(velocity = velocity)
    }

    // ---- other overlays ----

    /** Opens search; [velocity] in search heights per second, so a flick keeps its momentum (D99). */
    /**
     * Search's own spring (0.8.1, D118): Settings → Search, Bounce and Speed. Opening may overshoot
     * by the bounce; closing never springs past closed.
     */
    private fun searchSpring(opening: Boolean): androidx.compose.animation.core.SpringSpec<Float> {
        val bounce = data.settings.get(LauncherSettings.SearchBounce) / 100f
        val speed = data.settings.get(LauncherSettings.SearchSpeed) / 100f
        val damping = 1f - 0.62f * bounce
        return spring(
            dampingRatio = if (opening) damping else maxOf(damping, 0.9f),
            stiffness = MotionScale.stiffness(360f * speed * speed),
            visibilityThreshold = 0.001f,
        )
    }

    /** Asks for the calendar (the Calendar widget), then runs [then] if it may be read. */
    fun askCalendar(then: () -> Unit) = callbacks.requestCalendar { granted -> if (granted) then() }

    fun openSearch(velocity: Float = Float.NaN) {
        menu = null
        searchDragPosition = null
        searchDragging = false
        if (!searchOpen && search.value < 0.001f) startExpectingKeyboard()
        searchOpen = true
        val v = if (velocity.isNaN()) search.velocity else velocity
        scope.launch { search.animateTo(1f, searchSpring(opening = true), initialVelocity = v) }
    }

    fun closeSearch(immediately: Boolean = false, velocity: Float = Float.NaN) {
        searchDragPosition = null
        searchDragging = false
        searchOpen = false
        keyboardSteady = false
        searchEditing = false
        searchGlanceOpen = false
        val v = if (velocity.isNaN()) search.velocity else velocity
        scope.launch {
            if (immediately) search.snapTo(0f) else search.animateTo(0f, searchSpring(opening = false), initialVelocity = v)
            if (!searchOpen) {
                searchQuery = ""
                askClaudeMode = false
                expectKeyboard = false
                stopKeyboardRise()
            }
        }
    }

    /** [deltaFraction]: the finger's movement as a fraction of the screen height (down = positive). */
    fun dragSearchBy(deltaFraction: Float) {
        val before = searchDragPosition ?: search.value
        if (!searchOpen && before < 0.001f) startExpectingKeyboard()
        val next = (before + deltaFraction / drawerTravel()).coerceIn(0f, 1f)
        if (motionConfig.drawerTick && (before > OPEN_POINT) != (next > OPEN_POINT)) Haptics.tick(view)
        searchDragPosition = next
        if (!searchDragging) searchDragging = true
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { search.snapTo(next) }
    }

    /** Finger released while moving search: open or close by position and speed, as the drawer does. */
    fun settleSearch(velocityPxPerSec: Float) {
        val position = searchDragPosition ?: search.value
        val open = when {
            velocityPxPerSec > 800f -> true
            velocityPxPerSec < -800f -> false
            else -> position > OPEN_POINT
        }
        DebugLog.event(
            "Home",
            "search: released at " + "%.2f".format(position) + ", " + velocityPxPerSec.toInt() + " px/s: " + if (open) "opens" else "closes",
        )
        val velocity = (velocityPxPerSec / (screenHeight * drawerTravel())).coerceIn(-14f, 14f)
        if (open) openSearch(velocity) else closeSearch(velocity = velocity)
    }

    fun closeAll(immediately: Boolean = false) {
        speedBump = null
        reviewSheet = null
        customise = null
        endSelecting()
        tagApps = null
        homeMenu = false
        wallpaperStrip = false
        layoutsSheet = false
        wallpaperStudio = false
        if (todaySheet || hub.value > 0f) closeHub(immediately)
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
            layoutWallpaperPick != null -> layoutWallpaperPick = null
            customise != null -> customise = null
            bulkTag != null -> bulkTag = null
            tagApps != null -> tagApps = null
            selection != null -> endSelecting()
            speedBump != null -> speedBump = null
            reviewSheet != null -> reviewSheet = null
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
            wallpaperStudio -> if (wallpaperStudioBack?.invoke() != true) wallpaperStudio = false
            todaySheet -> closeHub()
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

    /** 0 by day, 1 at wind-down (D4): how much colour the wallpaper (and so the glass) loses. Eased in and out. */
    val windDown = Animatable(0f)
    private var windDownMatrix: Pair<Float, androidx.compose.ui.graphics.ColorFilter?> = 0f to null

    /** The wallpaper's colour filter now, or null at full colour. Read while drawing. */
    /** W8 (P8): the wallpaper's colour filter: the evening's wind-down and the picture's own saturation (1 = as it is). */
    fun wallpaperFilter(saturation: Float): androidx.compose.ui.graphics.ColorFilter? {
        if (kotlin.math.abs(saturation - 1f) < 0.01f) return windDownFilter()
        val w = windDown.value
        val cached = pictureMatrix
        if (cached.first == w && cached.second == saturation) return cached.third
        val matrix = androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(saturation * (1f - 0.68f * w)) }
        val dim = 1f - 0.12f * w
        if (w > 0.001f) {
            matrix.timesAssign(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(dim, 0f, 0f, 0f, 0f, 0f, dim, 0f, 0f, 0f, 0f, 0f, dim, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        }
        val filter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(matrix)
        pictureMatrix = Triple(w, saturation, filter)
        return filter
    }

    private var pictureMatrix: Triple<Float, Float, androidx.compose.ui.graphics.ColorFilter?> = Triple(-1f, -1f, null)

    fun windDownFilter(): androidx.compose.ui.graphics.ColorFilter? {
        val w = windDown.value
        if (w <= 0.001f) return null
        val cached = windDownMatrix
        if (cached.first == w) return cached.second
        val matrix = androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(1f - 0.68f * w) }
        // A touch darker too: evening light.
        val dim = 1f - 0.12f * w
        val scale = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(dim, 0f, 0f, 0f, 0f, 0f, dim, 0f, 0f, 0f, 0f, 0f, dim, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        matrix.timesAssign(scale)
        val filter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(matrix)
        windDownMatrix = w to filter
        return filter
    }

    /** The app last opened from the launcher, until the launcher comes back (its icon lands then; 0.9.4). */
    var lastLaunchKey: String? = null

    /** C1: an app tagged as distracting was asked for during a study block; it waits here. */
    var speedBump by mutableStateOf<SpeedBump?>(null)

    /** A4 (0.9.18): on the trip home with the battery under 30 % (not charging): heavy things rest. */
    var batterySaving by mutableStateOf(false)

    /** The block running now when [app] carries a "distracting" tag (C1, Settings → Study companion); else null. */
    private fun speedBumpFor(app: AppEntry): dev.liquid.launcher.core.data.StudyEvent? {
        if (!data.study.enabled || !data.settings.get(LauncherSettings.StudySpeedBump)) return null
        val words = data.settings.get(LauncherSettings.StudySpeedBumpTags).split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (words.isEmpty() || app.tags.isEmpty()) return null
        val ids = data.organisation.value.value.tags.filter { it.name.trim().lowercase() in words }.map { it.id }.toSet()
        if (app.tags.none { it in ids }) return null
        val now = System.currentTimeMillis()
        data.study.day.value.current(now)?.takeIf { it.status == dev.liquid.launcher.core.data.StudyStatus.Open }?.let { return it }
        // 0.14.0 (D184): a study session holds them back too (as a block named after what is studied).
        return data.study.focus.value?.takeIf { it.isSession && it.until > now }?.let {
            dev.liquid.launcher.core.data.StudyEvent(-1, 0, it.startedAt, it.until, false, it.subject.ifBlank { "Study session" })
        }
    }

    /** The speed bump's "Open anyway": the app it held back opens as it would have. */
    fun openAnyway() {
        val bump = speedBump ?: return
        speedBump = null
        launch(bump.app, bump.bounds, bump.options, bump.fromDrawer, bypass = true)
    }

    /** The speed bump's "Back to <block>": the study hub, on the block. */
    fun backToBlock() {
        speedBump = null
        openToday()
    }

    fun launch(app: AppEntry, bounds: android.graphics.Rect? = null, options: android.os.Bundle? = null, fromDrawer: Boolean = false, bypass: Boolean = false) {
        if (!bypass) {
            speedBumpFor(app)?.let { block ->
                Haptics.confirm(view)
                menu = null
                speedBump = SpeedBump(app, bounds, options, fromDrawer, block)
                DebugLog.event("Home", "speed bump: ${app.label} during ${block.title.subject}")
                return
            }
        }
        val started = data.apps.launch(app, bounds, options)
        if (started) lastLaunchKey = app.key
        if (!started) {
            toast(if (app.isWorkProfile) "Could not open: is the work profile paused?" else "Could not open ${app.label}")
            return
        }
        leaveForApp()
    }

    /** An app was started: something here will be closed once it covers the launcher. */
    private var tidyPending = false

    /**
     * True when the launcher is about to leave the front with the drawer, search or a menu showing
     * that [tidyAfterLeaving] will close: the system's picture of this screen will then be out of date.
     */
    val closesWhileAway: Boolean
        get() = tidyPending && (drawerOpen || searchOpen || search.value > 0.01f || menu != null || todaySheet || hub.value > 0.01f)

    /** 0.11.3 (D170): an app was just started from the launcher (a keyboard still up is only on its way out). */
    val appJustStarted: Boolean get() = tidyPending

    /**
     * Called right after an app was started from anywhere in the launcher. The keyboard goes away at
     * once (so it is neither in the system's picture of this screen nor brought back on return);
     * the drawer, search and menus are closed once the app's opening animation covers them, and at
     * the latest when the launcher is no longer visible. Coming home then shows a clean home screen.
     */
    fun leaveForApp() {
        hideKeyboard()
        tidyPending = true
        scope.launch {
            kotlinx.coroutines.delay(LEAVE_TIDY_MS)
            tidyAfterLeaving()
        }
    }

    /** Closes what [leaveForApp] left open. Safe to call at any time; does nothing unless an app was just started. */
    fun tidyAfterLeaving() {
        if (!tidyPending) return
        tidyPending = false
        menu = null
        tagSheet = false
        if (todaySheet || hub.value > 0f) closeHub(immediately = true)
        if (searchOpen || search.value > 0f) closeSearch(immediately = true)
        if (drawerOpen && data.settings.get(LauncherSettings.DrawerCloseOnLaunch)) closeDrawer(immediately = true)
        hideKeyboard()
    }

    /** Drops the focus of the launcher's text fields and puts the keyboard away, now. */
    fun hideKeyboard() {
        drawerFocusReset++
        expectKeyboard = false
        val v = view ?: return
        try {
            v.windowInsetsController?.hide(android.view.WindowInsets.Type.ime())
        } catch (e: Exception) {
            DebugLog.error("Home", "could not hide the keyboard: $e")
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
            DebugLog.event("Widgets", "\"$label\": opening its set-up screen")
            callbacks.setUpWidget(id) { ok ->
                DebugLog.event("Widgets", "\"$label\": set-up " + if (ok) "done" else "cancelled")
                if (ok) place() else widgets.delete(id)
            }
        }
        if (widgets.bindIfAllowed(id, info)) {
            DebugLog.event("Widgets", "\"$label\": allowed already")
            setUpThenPlace()
        } else {
            DebugLog.event("Widgets", "\"$label\": asking the system for permission")
            callbacks.bindWidget(widgets.bindIntent(id, info)) { ok ->
                DebugLog.event("Widgets", "\"$label\": permission " + if (ok) "given" else "refused")
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
            LauncherAction.OpenSearch -> {
                if (!searchOpen) askClaudeMode = false
                openSearch()
            }
            LauncherAction.OpenSettings -> callbacks.openSettings()
            LauncherAction.CustomisePill -> pillBounds?.let { offerCustomise("pill", "the pill", it.topCenter) } ?: callbacks.openSettings()
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
            LauncherAction.Today -> openToday()
            LauncherAction.StudyNote -> {
                hubNoteFocus = true
                openToday()
            }
            LauncherAction.BoardPhoto -> boardPhoto()
            LauncherAction.TellClaude -> tellClaude()
            LauncherAction.AskClaude -> openAskClaude()
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

    /** 0.13.0: Settings at a page, a tab and an option ("page:pill:layout:pill.live.music"). */
    fun openSettingsAt(target: String) = callbacks.openSettingsAt(target)

    /** 0.13.0: asks for the contacts; [done] says whether search may read them. */
    fun requestContacts(done: (Boolean) -> Unit) = callbacks.requestContacts(done)

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
            // Leaving for another app (Claude, a link, the calendar): come back to a clean home screen (P1b).
            leaveForApp()
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

/** 0.16.0 (D200): how long Undo is offered after a wallpaper change (Med: 10 seconds). */
internal const val UNDO_MS = 10_000L

/** What the home screen asks the activity to do. */
/** How long after starting an app the drawer, search and menus are closed (its opening animation covers them by then). */
private const val LEAVE_TIDY_MS = 250L

/** An app held back by the speed bump (C1), with how it was going to open. */
class SpeedBump(
    val app: AppEntry,
    val bounds: android.graphics.Rect?,
    val options: android.os.Bundle?,
    val fromDrawer: Boolean,
    val block: dev.liquid.launcher.core.data.StudyEvent,
)

class HomeCallbacks(
    val openSettings: () -> Unit,
    val openSetup: () -> Unit,
    /** Opens one settings section by its id. */
    val openSection: (String) -> Unit = { openSettings() },
    /** 0.13.0: Settings at a page, a tab and an option ("page:pill:layout:pill.live.music"), for search's results. */
    val openSettingsAt: (String) -> Unit = { openSettings() },
    /** 0.13.0: asks for the contacts (search finds people); answers whether they may be read. */
    val requestContacts: ((Boolean) -> Unit) -> Unit = { it(false) },
    /** Shows the photo picker; the callback gets the picked image or null. */
    val pickImage: ((android.net.Uri?) -> Unit) -> Unit = { it(null) },
    /** Shows the photo picker for several images; the callback gets them (maybe none). */
    val pickImages: ((List<android.net.Uri>) -> Unit) -> Unit = { it(emptyList()) },
    /** Shows the system folder picker; the callback gets the folder or null. */
    val pickFolder: ((android.net.Uri?) -> Unit) -> Unit = { it(null) },
    /** Asks for calendar access (read and write); the callback gets whether reading was granted. */
    val requestCalendar: ((Boolean) -> Unit) -> Unit = { it(false) },
    /** Opens the camera to take a picture into [Uri]; the callback gets whether one was taken (E1). */
    /** 0.11.2: starts the camera with [BoardPhotos]' intent; the answer is handled by the activity. */
    val takePhoto: (android.content.Intent) -> Unit = {},
    /** 0.15.3 (D196): opens the launcher's board camera for a class (its module). */
    val openBoardCamera: (String) -> Unit = {},
    /** Asks for location access (Android's condition for reading the Wi-Fi name); the callback gets whether it was given. */
    val requestLocation: ((Boolean) -> Unit) -> Unit = { it(false) },
    /** Shows the system file picker for a font file; the callback gets it or null. */
    val pickFont: ((android.net.Uri?) -> Unit) -> Unit = { it(null) },
    /** Shows the system's dialog allowing the launcher to add a widget; the callback gets whether it was allowed. */
    val bindWidget: (android.content.Intent, (Boolean) -> Unit) -> Unit = { _, done -> done(false) },
    /** Opens a widget's own set-up screen; the callback gets whether it was completed. */
    val setUpWidget: (Int, (Boolean) -> Unit) -> Unit = { _, done -> done(true) },
)
