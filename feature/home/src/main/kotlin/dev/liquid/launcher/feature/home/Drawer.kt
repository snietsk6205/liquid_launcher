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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.Fuzzy
import dev.liquid.launcher.core.data.Usage
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.ln

/** Apps in the order the drawer shows them. */
fun sortApps(apps: List<AppEntry>, sort: String, usage: Map<String, Usage>): List<AppEntry> = when (sort) {
    "used" -> apps.sortedByDescending { usage[it.key]?.count ?: 0 }
    "recent" -> apps.sortedByDescending { it.installedAt }
    else -> apps
}

fun usageBoost(app: AppEntry, usage: Map<String, Usage>): Int =
    (ln(1.0 + (usage[app.key]?.count ?: 0)) * 20).toInt()

/**
 * The app drawer: a dimmed sheet that follows the finger up from the home screen, with a
 * floating glass search pill. Always composed (off screen while closed) so it opens without
 * a first-frame stall and a drag that starts in it survives the drawer closing.
 */
@Composable
fun AppDrawer(
    controller: HomeController,
    config: DrawerConfig,
    glass: GlassConfig,
    lightBackground: Boolean,
    apps: List<AppEntry>,
    homeBackdrop: Backdrop,
    interactive: Boolean,
) {
    // Collected here, not in the home screen: launch counts change after every launch.
    val usage by controller.data.usage.usage.collectAsState()
    val drawerLayer = rememberLayerBackdrop()
    val pillBackdrop = rememberCombinedBackdrop(homeBackdrop, drawerLayer)
    // A dimmed sheet reads as dark unless the dim is light and the wallpaper bright.
    val light = lightBackground && config.dim < 0.35f
    val color = if (light) Color(0xFF16181C) else Color.White
    val query = controller.drawerQuery

    val visible = remember(apps) { apps.filter { !it.hidden } }
    val personal = remember(visible, config.sort, usage, config.work) {
        sortApps(visible.filter { config.work == "merged" || !it.isWorkProfile }, config.sort, usage)
    }
    val work = remember(visible, config.sort, usage) { sortApps(visible.filter { it.isWorkProfile }, config.sort, usage) }
    val showWorkTab = config.work == "tab" && work.isNotEmpty()
    val results = remember(query, visible, usage) {
        if (query.isBlank()) emptyList() else Fuzzy.rank(query, visible, { it.label }, { usageBoost(it, usage) })
    }

    val closeConnection = remember(controller) { drawerCloseConnection(controller) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = controller.drawer.value
                translationY = (1f - p) * size.height
                alpha = if (p <= 0f) 0f else 1f
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(drawerLayer)
                .background(Color.Black.copy(alpha = if (light) config.dim * 0.6f else config.dim))
                .nestedScroll(closeConnection)
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                DrawerHeader(controller, showWorkTab && query.isBlank(), color, interactive) { pagerState ->
                    if (query.isNotBlank()) {
                        AppGrid(results, config, color, controller, interactive, reverse = config.fromBottom)
                    } else if (!showWorkTab) {
                        AppGrid(personal, config, color, controller, interactive, reverse = config.fromBottom)
                    } else {
                        HorizontalPager(pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                            AppGrid(if (page == 0) personal else work, config, color, controller, interactive, reverse = config.fromBottom)
                        }
                    }
                }
            }
        }
        DrawerSearchPill(
            controller = controller,
            glass = glass,
            lightBackground = light,
            backdrop = pillBackdrop,
            first = { results.firstOrNull() },
            autoKeyboard = config.autoKeyboard,
            interactive = interactive,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

/** Tabs (All / Work) and a strip that closes the drawer when dragged down. */
@Composable
private fun DrawerHeader(
    controller: HomeController,
    showTabs: Boolean,
    color: Color,
    interactive: Boolean,
    content: @Composable (androidx.compose.foundation.pager.PagerState) -> Unit,
) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .then(if (interactive) Modifier.closeOnDrag(controller) else Modifier),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showTabs) {
                listOf("Apps" to LauncherIcons.Apps, "Work" to LauncherIcons.Work).forEachIndexed { index, (label, icon) ->
                    val selected = pagerState.currentPage == index
                    Row(
                        Modifier
                            .padding(horizontal = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) color.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LauncherIcon(icon, contentDescription = null, tint = color, size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                    }
                }
            } else {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(color.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
            }
        }
        Box(Modifier.weight(1f)) { content(pagerState) }
    }
}

@Composable
private fun AppGrid(
    apps: List<AppEntry>,
    config: DrawerConfig,
    color: Color,
    controller: HomeController,
    interactive: Boolean,
    reverse: Boolean,
) {
    val state = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(config.columns),
        state = state,
        reverseLayout = reverse,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, if (reverse) Alignment.Bottom else Alignment.Top),
    ) {
        items(apps, key = { it.key }, contentType = { 0 }) { app ->
            DrawerItem(app, config, color, controller, interactive)
        }
    }
}

@Composable
private fun DrawerItem(app: AppEntry, config: DrawerConfig, color: Color, controller: HomeController, interactive: Boolean) {
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

@Composable
private fun DrawerSearchPill(
    controller: HomeController,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    first: () -> AppEntry?,
    autoKeyboard: Boolean,
    interactive: Boolean,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(50)
    val color = if (glass.lightGlass(Surfaces.Drawer, lightBackground)) Color(0xFF16181C) else Color.White
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .liquidGlass(backdrop, shape, glass.style(Surfaces.Drawer, lightBackground))
            .clip(shape)
            .then(if (interactive) Modifier.closeOnDrag(controller) else Modifier)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                keyboardActions = KeyboardActions(onGo = { first()?.let { controller.launch(it, fromDrawer = true) } }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (controller.drawerQuery.isNotEmpty()) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable { controller.drawerQuery = "" }, contentAlignment = Alignment.Center) {
                LauncherIcon(LauncherIcons.Close, contentDescription = "Clear", tint = color, size = 20.dp)
            }
        }
    }
}

/** Dragging down on this area moves the drawer with the finger and closes it on release. */
private fun Modifier.closeOnDrag(controller: HomeController): Modifier = pointerInput(controller) {
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
