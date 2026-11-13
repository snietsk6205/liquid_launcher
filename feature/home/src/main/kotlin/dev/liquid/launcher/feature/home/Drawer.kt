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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import dev.liquid.launcher.core.data.Fuzzy
import dev.liquid.launcher.core.data.Usage
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import kotlinx.coroutines.flow.distinctUntilChanged
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

/** One page of the drawer: its pill label, icon and apps. */
private class DrawerTab(val label: String, val icon: Int, val apps: List<AppEntry>)

/** Space the floating search field takes at the bottom (54 dp and its margins). */
internal val SearchZone = 90.dp
private val HeaderHeight = 52.dp
private val TabsHeight = 44.dp

/**
 * Bottom content padding that follows the keyboard. Read during layout, so the keyboard
 * sliding in moves the list without recomposing the drawer.
 */
@Stable
private class KeyboardAwarePadding(
    private val insets: WindowInsets,
    private val screen: Density,
    private val extraBottom: Dp,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 8.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection) = 8.dp
    override fun calculateTopPadding() = 8.dp
    override fun calculateBottomPadding(): Dp = with(screen) { insets.getBottom(screen).toDp() } + extraBottom
}

/**
 * The app drawer. Always composed (hidden while closed) so it opens without a first-frame
 * stall and a drag that starts in it survives the drawer closing.
 *
 * Two layers: the dim and the apps are recorded into [drawerLayer] (what the glass parts
 * refract); the glass parts (tabs, scroll bar, search field) sit above it and move with it.
 */
@Composable
fun AppDrawer(
    controller: HomeController,
    config: DrawerConfig,
    motion: DrawerMotion,
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

    val visible = remember(apps) { apps.filter { !it.hidden } }
    val personal = remember(visible, config.sort, usage, config.work) {
        sortApps(visible.filter { config.work == "merged" || !it.isWorkProfile }, config.sort, usage)
    }
    val work = remember(visible, config.sort, usage) { sortApps(visible.filter { it.isWorkProfile }, config.sort, usage) }
    val results = remember(query, visible, usage) {
        if (query.isBlank()) emptyList() else Fuzzy.rank(query, visible, { it.label }, { usageBoost(it, usage) })
    }
    SideEffect { controller.drawerFirstResult = results.firstOrNull() }

    val tabs = remember(personal, work, config.work) {
        buildList {
            add(DrawerTab("Apps", LauncherIcons.Apps, personal))
            if (config.work == "tab" && work.isNotEmpty()) add(DrawerTab("Work", LauncherIcons.Work, work))
        }
    }
    val searching = query.isNotBlank()
    val showTabs = tabs.size > 1 && !searching
    val pager = rememberPagerState { tabs.size }
    // Two fixed states (Apps, Work): the number of remember calls must not change between compositions.
    val tabStates = listOf(rememberLazyGridState(), rememberLazyGridState())
    val resultsState = rememberLazyGridState()
    val active: LazyGridState = if (searching) resultsState else tabStates[pager.currentPage.coerceIn(0, tabs.lastIndex)]
    val activeApps = if (searching) results else tabs.getOrNull(pager.currentPage)?.apps ?: personal

    val density = LocalDensity.current
    val keyboardOrNav = WindowInsets.ime.union(WindowInsets.navigationBars)
    val tabsAtBottom = showTabs && !config.tabsTop
    val bottomExtra = SearchZone + if (tabsAtBottom) TabsHeight + 10.dp else 0.dp
    val padding = remember(keyboardOrNav, density, bottomExtra) { KeyboardAwarePadding(keyboardOrNav, density, bottomExtra) }

    val fx = remember(controller) { ScrollFx(controller.scope) }
    fx.mode = motion.scrollEffect
    fx.strength = motion.strength

    val closeConnection = remember(controller) { drawerCloseConnection(controller) }
    // Flips only when the drawer fully closes or starts opening, not on every frame.
    val closed by remember { derivedStateOf { controller.drawer.value <= 0f } }
    var tabMenu by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .then(if (closed) Modifier.clearAndSetSemantics { } else Modifier)
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
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    // Above the list: the tabs pill floats here (top position) or a handle shows.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(HeaderHeight)
                            .then(if (interactive) remember(controller) { Modifier.closeOnDrag(controller) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!showTabs || tabsAtBottom) {
                            Box(Modifier.size(width = 36.dp, height = 4.dp).background(color.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .onSizeChanged { fx.viewport = it.height.toFloat() }
                            .onPlaced { fx.grid = it }
                    ) {
                        when {
                            searching -> AppGrid(results, resultsState, padding, config, motion, fx, color, controller, interactive)
                            tabs.size == 1 -> AppGrid(personal, tabStates[0], padding, config, motion, fx, color, controller, interactive)
                            else -> HorizontalPager(pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                                AppGrid(tabs[page].apps, tabStates[page], padding, config, motion, fx, color, controller, interactive)
                            }
                        }
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
            if (motion.scrollbar != "off") {
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
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = HeaderHeight + 8.dp)
                        .padding(bottom = 8.dp)
                        .keyboardInsetBottom(keyboardOrNav, bottomExtra),
                )
            }
            if (showTabs) {
                DrawerTabs(
                    labels = tabs.map { it.label to it.icon },
                    pager = pager,
                    glass = glass,
                    lightBackground = light,
                    backdrop = glassBackdrop,
                    controller = controller,
                    interactive = interactive,
                    onReselect = { page -> controller.scope.launchScrollToStart(tabStates[page]) },
                    onLongPress = { tabMenu = true },
                    modifier = if (config.tabsTop) {
                        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = (HeaderHeight - TabsHeight) / 2)
                    } else {
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = SearchZone)
                    },
                )
            }
            if (ownSearchPill) {
                DrawerSearchPill(
                    controller = controller,
                    glass = glass,
                    lightBackground = light,
                    backdrop = glassBackdrop,
                    autoKeyboard = config.autoKeyboard,
                    interactive = interactive,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
            if (tabMenu && interactive) {
                TabQuickMenu(
                    config = config,
                    glass = glass,
                    lightBackground = light,
                    backdrop = glassBackdrop,
                    controller = controller,
                    atTop = config.tabsTop,
                    onDismiss = { tabMenu = false },
                )
            }
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
    apps: List<AppEntry>,
    state: LazyGridState,
    padding: PaddingValues,
    config: DrawerConfig,
    motion: DrawerMotion,
    fx: ScrollFx,
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
        items(apps, key = { it.key }, contentType = { 0 }) { app ->
            DrawerItem(app, config, color, controller, interactive, fx, state)
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
            .scrollEffect(fx, state, holder)
            .iconGestures({ holder.value }, handler, interactive)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIconImage(app.icon, Modifier.size(config.iconSizeDp.dp), app.label)
        if (config.labels) {
            Text(
                text = app.label,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 2.dp, end = 2.dp),
                style = TextStyle(color = color, fontSize = 11.sp, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---- the search field ----

/** The drawer's own search pill, used when the home pill does not turn into it. */
@Composable
private fun DrawerSearchPill(
    controller: HomeController,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    autoKeyboard: Boolean,
    interactive: Boolean,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(50)
    val color = if (glass.lightGlass(Surfaces.Drawer, lightBackground)) Color(0xFF16181C) else Color.White
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            // Re-draw while the drawer moves, so the glass samples its new position.
            .drawBehind { controller.drawer.value }
            .liquidGlass(backdrop, shape, glass.style(Surfaces.Drawer, lightBackground))
            .clip(shape)
            .then(if (interactive) remember(controller) { Modifier.closeOnDrag(controller) } else Modifier),
    ) {
        DrawerSearchField(controller, color, autoKeyboard, interactive, Modifier.fillMaxSize())
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
                Text("Search apps", style = TextStyle(color = color.copy(alpha = 0.65f), fontSize = 16.sp))
            }
            BasicTextField(
                value = controller.drawerQuery,
                onValueChange = { controller.drawerQuery = it },
                enabled = interactive,
                singleLine = true,
                textStyle = TextStyle(color = color, fontSize = 16.sp),
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
            tracker.addPosition(change.uptimeMillis, change.position)
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
