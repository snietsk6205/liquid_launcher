package dev.liquid.launcher.feature.home

import android.app.ActivityOptions
import android.graphics.Rect as AndroidRect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.DrawerTabDef
import dev.liquid.launcher.core.data.Fuzzy
import dev.liquid.launcher.core.data.GridIndex
import dev.liquid.launcher.core.data.IndexItem
import dev.liquid.launcher.core.data.Organisation
import dev.liquid.launcher.core.data.OrganisationOps
import dev.liquid.launcher.core.data.Usage
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.roundToInt

/** Apps in the order the drawer shows them. */
fun sortApps(apps: List<AppEntry>, sort: String, usage: Map<String, Usage>): List<AppEntry> = when (sort) {
    "used" -> apps.sortedByDescending { usage[it.key]?.count ?: 0 }
    "recent" -> apps.sortedByDescending { it.installedAt }
    else -> apps
}

fun usageBoost(app: AppEntry, usage: Map<String, Usage>): Int =
    (ln(1.0 + (usage[app.key]?.count ?: 0)) * 20).toInt()

/** One cell of the drawer list: an app, or a full-width section header. */
internal sealed interface GridCell {
    val key: String
}

internal class AppCell(val app: AppEntry, override val key: String) : GridCell

internal class HeaderCell(val title: String, override val key: String) : GridCell

/** One page of the drawer: its pill label and icon, its cells and its letter index. */
private class DrawerTab(
    val def: DrawerTabDef?,
    val id: String,
    val label: String,
    val icon: Int,
    val apps: List<AppEntry>,
    val cells: List<GridCell>,
    val index: GridIndex,
    val sort: String,
)

internal fun tabIcon(def: DrawerTabDef): Int {
    def.icon?.let { name -> LauncherIcons.named[name]?.let { return it } }
    return when {
        def.source == "personal" -> LauncherIcons.Apps
        def.source == "work" -> LauncherIcons.Work
        def.source.startsWith("tag:") -> LauncherIcons.Label
        else -> LauncherIcons.CircleOutline
    }
}

/** Ids of the tags whose name starts with what follows "#", or null when the query is not a tag search. */
private fun tagQuery(query: String, org: Organisation): Set<String>? {
    if (!query.startsWith("#")) return null
    val name = query.drop(1).trim()
    return org.tags.filter { it.name.startsWith(name, ignoreCase = true) }.map { it.id }.toSet()
}

/** Builds a tab's cells: optional sections at the top (tags), then the tab's own apps. */
private fun makeTab(
    def: DrawerTabDef?,
    id: String,
    label: String,
    icon: Int,
    apps: List<AppEntry>,
    sort: String,
    sections: List<Pair<dev.liquid.launcher.core.data.Tag, List<AppEntry>>>,
    columns: Int,
): DrawerTab {
    val cells = ArrayList<GridCell>(apps.size + sections.sumOf { it.second.size + 1 } + 1)
    val items = ArrayList<IndexItem>(cells.size)
    val letters = sort == "az"
    for ((tag, list) in sections) {
        cells += HeaderCell(tag.name, "h:${tag.id}")
        items += IndexItem.Header(tag.name)
        for (app in list) {
            cells += AppCell(app, "s:${tag.id}:${app.key}")
            items += IndexItem.Cell(null)
        }
    }
    if (sections.isNotEmpty()) {
        cells += HeaderCell(label, "h:all")
        items += IndexItem.Header(null)
    }
    for (app in apps) {
        cells += AppCell(app, app.key)
        items += IndexItem.Cell(if (letters) GridIndex.letterOf(app.label) else null)
    }
    return DrawerTab(def, id, label, icon, apps, cells, GridIndex.build(items, columns), sort)
}

/** Space the floating search field takes at the bottom (54 dp and its margins). */
internal val SearchZone = 90.dp
private val HandleHeight = 52.dp

internal fun tabsHeight(size: String): Dp = when (size) {
    "small" -> 36.dp
    "large" -> 52.dp
    else -> 44.dp
}

/** Height the tabs add to the search pill when they sit inside it. */
internal fun tabsRowHeight(size: String): Dp = tabsHeight(size) + 4.dp

/**
 * Content padding of the list: room for the status bar and the floating parts at the top,
 * and for the keyboard (or navigation bar) and the search field at the bottom. Insets are
 * read during layout, so the keyboard sliding in moves the list without recomposing the drawer.
 */
@Stable
private class DrawerPadding(
    private val bottomInsets: WindowInsets,
    private val topInsets: WindowInsets,
    private val screen: Density,
    private val extraTop: Dp,
    private val extraBottom: Dp,
    private val left: Dp,
    private val right: Dp,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = left
    override fun calculateRightPadding(layoutDirection: LayoutDirection) = right
    override fun calculateTopPadding(): Dp = with(screen) { topInsets.getTop(screen).toDp() } + extraTop
    override fun calculateBottomPadding(): Dp = with(screen) { bottomInsets.getBottom(screen).toDp() } + extraBottom
}

/**
 * The app drawer. Always composed (hidden while closed) so it opens without a first-frame
 * stall and a drag that starts in it survives the drawer closing.
 *
 * Two layers: the dim and the apps are recorded into [drawerLayer] (what the glass parts
 * refract); the glass parts (tabs, scroll index, search field) float above it and move with
 * it. The list runs under the floating parts at both ends.
 */
@Composable
fun AppDrawer(
    controller: HomeController,
    config: DrawerConfig,
    motion: DrawerMotion,
    indexConfig: IndexConfig,
    glass: GlassConfig,
    lightBackground: Boolean,
    apps: List<AppEntry>,
    homeBackdrop: Backdrop,
    drawerLayer: LayerBackdrop,
    ownSearchPill: Boolean,
    interactive: Boolean,
) {
    // Collected here, not in the home screen: launch counts change after every launch.
    val usage by controller.data.usage.usage.collectAsState()
    val glassBackdrop = rememberCombinedBackdrop(homeBackdrop, drawerLayer)
    // A dimmed drawer reads as dark unless the dim is light and the wallpaper bright.
    val light = lightBackground && config.dim < 0.35f
    val color = if (light) Color(0xFF16181C) else Color.White
    val query = controller.drawerQuery
    val searching = query.isNotBlank()

    val org by controller.data.organisation.value.collectAsState()
    val visible = remember(apps) { apps.filter { !it.hidden } }

    // Pages from the user's tab list: the main list (with sections at its top), work, and tabs built from tags.
    val tabs = remember(visible, org, config.work, config.sort, usage, config.topSections, config.columns) {
        val known = org.tags.map { it.id }.toSet()
        val sections = config.topSections.mapNotNull { id ->
            val tag = org.tag(id) ?: return@mapNotNull null
            val list = visible.filter { id in it.tags && !(it.isWorkProfile && config.work == "hidden") }
            if (list.isEmpty()) null else tag to sortApps(list, config.sort, usage)
        }
        val built = org.tabs.mapNotNull { def ->
            val list = OrganisationOps.filter(def, visible, config.work, { it.isWorkProfile }, { it.tags }, known)
            if (def.source == "work" && def.extra.isEmpty() && list.isEmpty()) return@mapNotNull null
            val sort = def.sort ?: config.sort
            makeTab(
                def, def.id, def.name, tabIcon(def), sortApps(list, sort, usage), sort,
                if (def.source == "personal") sections else emptyList(), config.columns,
            )
        }
        built.ifEmpty { listOf(makeTab(null, "apps", "Apps", LauncherIcons.Apps, sortApps(visible, config.sort, usage), config.sort, sections, config.columns)) }
    }

    // Search: optionally limited to one tab; "#name" lists a tag's apps and highlights its tab.
    var searchScope by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(searching) { if (!searching) searchScope = null }
    val tagIds = remember(query, org) { tagQuery(query, org) }
    val results = remember(query, visible, usage, org, searchScope, tabs) {
        when {
            !searching -> emptyList()
            tagIds != null -> visible.filter { app -> app.tags.any { it in tagIds } }
            else -> {
                val pool = searchScope?.let { id -> tabs.firstOrNull { it.id == id }?.apps } ?: visible
                Fuzzy.rank(query, pool, { it.label }, { usageBoost(it, usage) })
            }
        }
    }
    val resultCells = remember(results) { results.map { AppCell(it, it.key) } }
    SideEffect { controller.drawerFirstResult = results.firstOrNull() }
    val searchHighlight = when {
        !searching -> -1
        tagIds != null -> tabs.indexOfFirst { tab -> tab.def?.sources?.any { it.startsWith("tag:") && it.removePrefix("tag:") in tagIds } == true }
        else -> tabs.indexOfFirst { it.id == searchScope }
    }

    val position = config.tabsPosition
    val showTabs = tabs.size > 1 && position != "hidden" && (!searching || config.tabsInSearch)
    val pager = rememberPagerState { tabs.size }
    // One list state per tab, kept across recompositions and tab edits.
    val tabStates = remember { HashMap<String, LazyGridState>() }
    fun stateFor(id: String) = tabStates.getOrPut(id) { LazyGridState() }
    val resultsState = rememberLazyGridState()
    val currentTab = tabs[pager.currentPage.coerceIn(0, tabs.lastIndex)]
    val active: LazyGridState = if (searching) resultsState else stateFor(currentTab.id)
    val activeApps = if (searching) results else currentTab.apps

    var tabMenu by remember { mutableStateOf<Int?>(null) }
    var tabDialog by remember { mutableStateOf<TabDialog?>(null) }
    val bar = TabsBar(
        labels = tabs.map { TabLabel(it.label, it.icon) },
        selected = { if (searching) searchHighlight.toFloat() else pager.currentPage + pager.currentPageOffsetFraction },
        onTap = { i ->
            val tab = tabs.getOrNull(i)
            when {
                tab == null -> Unit
                searching -> searchScope = if (searchScope == tab.id) null else tab.id
                pager.currentPage == i -> controller.scope.launchScrollToStart(stateFor(tab.id))
                else -> controller.scope.launch { pager.animateScrollToPage(i) }
            }
        },
        onLongPress = { i -> tabMenu = i },
        buttons = config.tabsButtons,
        onButton = { id ->
            when (id) {
                "add" -> tabDialog = TabDialog.NewTag
                "sort" -> tabMenu = pager.currentPage
                "settings" -> controller.openSection(dev.liquid.launcher.core.settings.LauncherSettings.Sections.Drawer.id)
            }
        },
    )
    val inPill = showTabs && config.tabsInPill
    SideEffect { controller.tabsBar = if (inPill) bar else null }
    DisposableEffect(Unit) { onDispose { controller.tabsBar = null } }

    val useIndex = motion.scrollbar == "index" && !searching && currentTab.sort == "az"
    // The index floats over the list, so both sides keep the same margin whether it shows or not.
    val wave = remember { IndexWave() }
    val density = LocalDensity.current
    val keyboardOrNav = WindowInsets.ime.union(WindowInsets.navigationBars)
    val tabsTop = showTabs && config.tabsTop
    val tabsBottom = showTabs && config.tabsBottom
    val tabsH = tabsHeight(config.tabsSize)
    val headerHeight = if (tabsTop) maxOf(HandleHeight, tabsH + 16.dp) else HandleHeight
    val bottomExtra = SearchZone + when {
        tabsBottom -> tabsH + 10.dp
        inPill -> tabsRowHeight(config.tabsSize)
        else -> 0.dp
    }
    val statusInsets = WindowInsets.statusBars
    val padding = remember(keyboardOrNav, statusInsets, density, bottomExtra, headerHeight, config.sideMarginDp) {
        DrawerPadding(
            keyboardOrNav, statusInsets, density, headerHeight + 4.dp, bottomExtra,
            left = config.sideMarginDp.dp,
            right = config.sideMarginDp.dp,
        )
    }

    val fx = remember(controller) { ScrollFx(controller.scope) }
    fx.mode = motion.scrollEffect
    fx.strength = motion.strength

    val closeConnection = remember(controller) { drawerCloseConnection(controller) }
    // Flips only when the drawer fully closes or starts opening, not on every frame.
    val closed by remember { derivedStateOf { controller.drawer.value <= 0f } }

    Box(
        Modifier
            .fillMaxSize()
            .then(if (closed) Modifier.clearAndSetSemantics { } else Modifier)
            // Closed: placed below the screen, so the invisible list cannot take the home screen's touches.
            .offset { if (controller.drawer.value <= 0f) IntOffset(0, controller.screenHeight.roundToInt() * 2) else IntOffset.Zero }
            .graphicsLayer { alpha = if (controller.drawer.value <= 0f) 0f else 1f }
    ) {
        // ---- Recorded for the glass: dim and apps ----
        Box(Modifier.fillMaxSize().layerBackdrop(drawerLayer)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { dimLayer(controller.drawer.value, motion) }
                    .background(Color.Black.copy(alpha = if (light) config.dim * 0.6f else config.dim))
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { contentLayer(controller.drawer.value, motion) }
                    .nestedScroll(closeConnection)
                    .nestedScroll(fx.connection)
            ) {
                // The list fills the screen and scrolls under the floating parts; it fades out under the status bar.
                Box(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { fx.viewport = it.height.toFloat() }
                        .onPlaced { fx.grid = it }
                        .statusBarFade()
                ) {
                    when {
                        searching -> AppGrid(resultCells, resultsState, padding, config, motion, fx, null, color, controller, interactive)
                        tabs.size == 1 -> AppGrid(tabs[0].cells, stateFor(tabs[0].id), padding, config, motion, fx, wave, color, controller, interactive)
                        else -> HorizontalPager(pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { tabs.getOrNull(it)?.id ?: it }) { page ->
                            val tab = tabs.getOrNull(page) ?: return@HorizontalPager
                            AppGrid(tab.cells, stateFor(tab.id), padding, config, motion, fx, wave.takeIf { page == pager.currentPage }, color, controller, interactive)
                        }
                    }
                }
                // The strip at the top closes the drawer when dragged down; a handle shows when no tabs float there.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(headerHeight)
                        .then(if (interactive) remember(controller) { Modifier.closeOnDrag(controller) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!tabsTop) {
                        Box(Modifier.size(width = 36.dp, height = 4.dp).background(color.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                    }
                }
            }
        }

        // ---- Glass parts, above what they refract, moving with the drawer ----
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = overlayAlpha(controller.drawer.value, motion) }
                .offset { IntOffset(0, overlayOffset(controller.drawer.value, motion, controller.screenHeight)) }
        ) {
            val sideArea = Modifier
                .statusBarsPadding()
                .padding(top = headerHeight + 8.dp)
                .padding(bottom = 8.dp)
                .keyboardInsetBottom(keyboardOrNav, bottomExtra)
            if (useIndex) {
                ScrollIndex(
                    state = active,
                    index = currentTab.index,
                    config = indexConfig,
                    reverse = config.fromBottom,
                    glass = glass,
                    lightBackground = light,
                    backdrop = glassBackdrop,
                    controller = controller,
                    interactive = interactive,
                    wave = wave,
                    modifier = sideArea,
                )
            } else if (motion.scrollbar != "off") {
                DrawerScrollbar(
                    state = active,
                    apps = activeApps,
                    columns = config.columns,
                    reverse = config.fromBottom,
                    motion = motion,
                    glass = glass,
                    lightBackground = light,
                    backdrop = glassBackdrop,
                    controller = controller,
                    interactive = interactive,
                    modifier = Modifier.align(Alignment.TopEnd).then(sideArea),
                )
            }
            if (showTabs && (tabsTop || tabsBottom)) {
                DrawerTabs(
                    bar = bar,
                    style = config.tabsStyle,
                    size = config.tabsSize,
                    icons = config.tabsIcons,
                    glass = glass,
                    lightBackground = light,
                    backdrop = glassBackdrop,
                    controller = controller,
                    interactive = interactive,
                    modifier = if (tabsTop) {
                        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = (headerHeight - tabsH) / 2)
                    } else {
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = SearchZone)
                    },
                )
            }
            if (ownSearchPill) {
                DrawerSearchPill(
                    controller = controller,
                    config = config,
                    glass = glass,
                    lightBackground = light,
                    backdrop = glassBackdrop,
                    interactive = interactive,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
            val menuFor = tabMenu
            if (menuFor != null && interactive) {
                TabMenu(
                    tab = tabs.getOrNull(menuFor)?.def,
                    tabIndex = menuFor,
                    tabCount = tabs.size,
                    config = config,
                    glass = glass,
                    lightBackground = light,
                    backdrop = glassBackdrop,
                    controller = controller,
                    atTop = tabsTop,
                    onDialog = { tabDialog = it },
                    onDismiss = { tabMenu = null },
                )
            }
        }
    }
    tabDialog?.let { dialog -> TabDialogView(dialog, controller) { tabDialog = null } }
}

/** Section headers bend with the list too. */
@Composable
private fun Modifier.headerWave(wave: IndexWave, index: Int): Modifier {
    val holder = remember { CoordinatesHolder() }
    return onPlaced { holder.value = it }.graphicsLayer { applyIndexWave(wave, holder.value, index) }
}

/** Fades the list out under the status bar, so app labels do not run into the clock and icons. */
@Composable
private fun Modifier.statusBarFade(): Modifier {
    val insets = WindowInsets.statusBars
    return this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val top = insets.getTop(this).toFloat()
        val fade = top + 20.dp.toPx()
        if (fade > 0f) {
            drawRect(
                brush = Brush.verticalGradient(0f to Color.Transparent, (top / fade) to Color.Transparent, 1f to Color.Black, startY = 0f, endY = fade),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}

/** Bottom padding that follows the keyboard or navigation bar, applied during layout. */
private fun Modifier.keyboardInsetBottom(insets: WindowInsets, extra: Dp): Modifier =
    layout { measurable, constraints ->
        val bottom = insets.getBottom(this) + extra.roundToPx()
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = (constraints.maxHeight - bottom).coerceAtLeast(0)))
        layout(placeable.width, placeable.height + bottom) { placeable.place(0, 0) }
    }

// ---- the transition ----

/** The dim: slides with the sheet, or fades in place. */
private fun GraphicsLayerScope.dimLayer(p: Float, motion: DrawerMotion) {
    if (motion.slide) {
        translationY = (1f - p) * size.height
        alpha = if (p <= 0f) 0f else 1f
    } else {
        alpha = p.coerceIn(0f, 1f)
    }
}

/** The apps: a sheet that follows the finger, a short rise out of depth, or a fade. */
private fun GraphicsLayerScope.contentLayer(p: Float, motion: DrawerMotion) {
    val pc = p.coerceIn(0f, 1f)
    when (motion.transition) {
        "slide" -> {
            translationY = (1f - p) * size.height
            alpha = if (p <= 0f) 0f else 1f
        }
        "fade" -> alpha = pc
        else -> {
            alpha = ((pc - 0.12f) / 0.88f).coerceIn(0f, 1f)
            translationY = (1f - p) * motion.rise * size.height
            val scale = 0.94f + 0.06f * p
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0.5f, 1f)
        }
    }
}

/** Glass parts move by layout (not by layer) so they keep sampling what is really behind them. */
private fun overlayOffset(p: Float, motion: DrawerMotion, height: Float): Int = when (motion.transition) {
    "slide" -> ((1f - p) * height).roundToInt()
    "fade" -> 0
    else -> ((1f - p) * motion.rise * height).roundToInt()
}

private fun overlayAlpha(p: Float, motion: DrawerMotion): Float = when {
    p <= 0f -> 0f
    motion.slide -> 1f
    else -> ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)
}

// ---- the list ----

@Composable
private fun AppGrid(
    cells: List<GridCell>,
    state: LazyGridState,
    padding: PaddingValues,
    config: DrawerConfig,
    motion: DrawerMotion,
    fx: ScrollFx,
    wave: IndexWave?,
    color: Color,
    controller: HomeController,
    interactive: Boolean,
) {
    val overscroll = if (motion.stretch) rememberOverscrollEffect() else null
    LazyVerticalGrid(
        columns = GridCells.Fixed(config.columns),
        state = state,
        reverseLayout = config.fromBottom,
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(10.dp, if (config.fromBottom) Alignment.Bottom else Alignment.Top),
        overscrollEffect = overscroll,
    ) {
        itemsIndexed(
            cells,
            key = { _, cell -> cell.key },
            span = { _, cell -> if (cell is HeaderCell) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            contentType = { _, cell -> if (cell is HeaderCell) 1 else 0 },
        ) { i, cell ->
            when (cell) {
                is AppCell -> DrawerItem(cell.app, config, color, controller, interactive, fx, state, wave, i)
                is HeaderCell -> Text(
                    cell.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (wave != null) Modifier.headerWave(wave, i) else Modifier)
                        .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 2.dp),
                    style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DrawerItem(
    app: AppEntry,
    config: DrawerConfig,
    color: Color,
    controller: HomeController,
    interactive: Boolean,
    fx: ScrollFx,
    state: LazyGridState,
    wave: IndexWave?,
    index: Int,
) {
    val holder = remember { CoordinatesHolder() }
    val view = LocalView.current
    val handler = remember(app, controller) {
        object : IconGestureHandler {
            override fun onTap() {
                val bounds = holder.value?.takeIf { it.isAttached }?.boundsInRoot()
                val rect = bounds?.let { AndroidRect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
                val options = rect?.let { ActivityOptions.makeClipRevealAnimation(view, it.left, it.top, it.width(), it.height()).toBundle() }
                controller.launch(app, rect, options, fromDrawer = true)
            }

            override fun onLongPress() {
                val bounds = holder.value?.takeIf { it.isAttached }?.boundsInRoot() ?: return
                controller.menu = MenuTarget(app, null, bounds)
            }

            override fun onDragStart(grab: Offset, finger: Offset) {
                val size = holder.value?.size ?: IntSize.Zero
                controller.startDrag(
                    DragState(
                        source = DragSource.NewApp(app.ref),
                        icon = app.icon,
                        widget = null,
                        cells = IntSize(1, 1),
                        grab = grab,
                        size = Size(size.width.toFloat(), size.height.toFloat()),
                        start = finger,
                    )
                )
            }

            override fun onDrag(finger: Offset) = controller.moveDrag(finger)
            override fun onDragEnd() = controller.endDrag()
            override fun onDragCancel() = controller.cancelDrag()
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .onPlaced { holder.value = it }
            .scrollEffect(fx, state, holder, wave, index)
            .iconGestures({ holder.value }, handler, interactive)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIconImage(app.icon, Modifier.size(config.iconSizeDp.dp), app.label)
        if (config.labels) {
            Text(
                text = app.label,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 2.dp, end = 2.dp),
                style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 11.sp, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---- the search field ----

/** The drawer's own search pill, used when the home pill does not turn into it. Holds the tabs when they sit inside it. */
@Composable
private fun DrawerSearchPill(
    controller: HomeController,
    config: DrawerConfig,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    interactive: Boolean,
    modifier: Modifier,
) {
    val bar = if (config.tabsInPill) controller.tabsBar else null
    val shape = RoundedCornerShape(if (bar != null) 28.dp else 27.dp)
    val color = if (glass.lightGlass(Surfaces.Drawer, lightBackground)) Color(0xFF16181C) else Color.White
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Re-draw while the drawer moves, so the glass samples its new position.
            .drawBehind { controller.drawer.value }
            .liquidGlass(backdrop, shape, glass.style(Surfaces.Drawer, lightBackground))
            .clip(shape)
            .then(if (interactive) remember(controller) { Modifier.closeOnDrag(controller) } else Modifier),
    ) {
        if (bar != null) {
            TabsRow(bar, config.tabsSize, config.tabsIcons, color, interactive, Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp).height(tabsHeight(config.tabsSize) - 2.dp))
        }
        DrawerSearchField(controller, color, config.autoKeyboard, interactive, Modifier.fillMaxWidth().height(54.dp))
    }
}

/** Search icon, text field and clear button. Shared by the drawer's pill and the morphing home pill. */
@Composable
internal fun DrawerSearchField(
    controller: HomeController,
    color: Color,
    autoKeyboard: Boolean,
    interactive: Boolean,
    modifier: Modifier,
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    if (interactive) {
        LaunchedEffect(autoKeyboard) {
            snapshotFlow { controller.drawer.value >= 0.99f }
                .distinctUntilChanged()
                .collect { open -> if (open && autoKeyboard) runCatching { focus.requestFocus() } }
        }
        LaunchedEffect(controller.drawerFocusReset) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    Row(modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        LauncherIcon(LauncherIcons.Search, contentDescription = null, tint = color, size = 22.dp)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (controller.drawerQuery.isEmpty()) {
                Text("Search apps", style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.65f), fontSize = 16.sp))
            }
            BasicTextField(
                value = controller.drawerQuery,
                onValueChange = { controller.drawerQuery = it },
                enabled = interactive,
                singleLine = true,
                textStyle = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 16.sp),
                cursorBrush = SolidColor(color),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { controller.drawerFirstResult?.let { controller.launch(it, fromDrawer = true) } }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .semantics { contentDescription = "Search apps" },
            )
        }
        if (controller.drawerQuery.isNotEmpty()) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable { controller.drawerQuery = "" }, contentAlignment = Alignment.Center) {
                LauncherIcon(LauncherIcons.Close, contentDescription = "Clear", tint = color, size = 20.dp)
            }
        }
    }
}

// ---- closing by dragging ----

/** Dragging down on this area moves the drawer with the finger and closes it on release. */
internal fun Modifier.closeOnDrag(controller: HomeController): Modifier = pointerInput(controller) {
    val tracker = VelocityTracker()
    detectVerticalDragGestures(
        onDragStart = { tracker.resetTracking() },
        onVerticalDrag = { change, dy ->
            tracker.addPointerInputChange(change)
            change.consume()
            controller.dragDrawerBy(-dy / controller.screenHeight)
        },
        onDragEnd = { controller.settleDrawer(tracker.calculateVelocity().y) },
        onDragCancel = { controller.settleDrawer(0f) },
    )
}

/** When the app list cannot scroll any further down, the rest of the drag closes the drawer. */
private fun drawerCloseConnection(controller: HomeController) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Partly pulled down: moving up first re-opens the drawer before the list scrolls.
        if (source == NestedScrollSource.UserInput && available.y < 0 && controller.drawer.value < 1f && controller.drawer.value > 0f) {
            controller.dragDrawerBy(-available.y / controller.screenHeight)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 0) {
            controller.dragDrawerBy(-available.y / controller.screenHeight)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (controller.drawer.value < 1f && controller.drawer.value > 0f) {
            controller.settleDrawer(available.y)
            return available
        }
        return Velocity.Zero
    }
}
