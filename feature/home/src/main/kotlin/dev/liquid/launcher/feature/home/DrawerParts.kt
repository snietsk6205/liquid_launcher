package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import dev.liquid.launcher.core.data.DrawerTabDef
import dev.liquid.launcher.core.data.OrganisationOps
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

// ---- scroll effect ----

/**
 * Shared state of the drawer's scroll effect. "elastic": scrolling builds up a pull that
 * makes rows trail behind the leading edge in proportion to speed, then springs back.
 * "curve": rows shrink and fade towards the top and bottom edge.
 */
@Stable
class ScrollFx(private val scope: CoroutineScope) {
    var mode = "none"
    var strength = 0.5f

    /** Height of the list area, in pixels. */
    var viewport = 1f
    var grid: LayoutCoordinates? = null

    /** Content movement not yet caught up with, in pixels. Negative while the content moves up. */
    val pull = Animatable(0f)

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (mode == "elastic" && consumed.y != 0f) kick(consumed.y)
            return Offset.Zero
        }
    }

    private fun kick(dy: Float) {
        val limit = viewport * 0.07f * strength
        val target = (pull.value + dy * 0.45f * strength).coerceIn(-limit, limit)
        scope.launch {
            pull.snapTo(target)
            pull.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 230f))
        }
    }
}

/** Applies [fx] to one drawer item. All work happens in the layer, so scrolling recomposes nothing. */
internal fun Modifier.scrollEffect(fx: ScrollFx, state: LazyGridState, holder: CoordinatesHolder): Modifier =
    graphicsLayer {
        val mode = fx.mode
        if (mode == "none") return@graphicsLayer
        // Read so the layer is updated on every scroll step.
        state.firstVisibleItemScrollOffset
        val coords = holder.value?.takeIf { it.isAttached } ?: return@graphicsLayer
        val grid = fx.grid?.takeIf { it.isAttached } ?: return@graphicsLayer
        val viewport = fx.viewport.coerceAtLeast(1f)
        val y = (grid.localPositionOf(coords, Offset.Zero).y + coords.size.height / 2f) / viewport
        when (mode) {
            "elastic" -> {
                val pull = fx.pull.value
                // The edge the content moves towards leads; rows further from it trail behind.
                val behind = (if (pull < 0f) y else 1f - y).coerceIn(0f, 1f)
                translationY = -pull * behind
            }
            "curve" -> {
                val c = ((y - 0.5f) * 2f).coerceIn(-1.2f, 1.2f)
                val k = c * c
                val scale = 1f - 0.16f * fx.strength * k
                scaleX = scale
                scaleY = scale
                alpha = (1f - 0.4f * fx.strength * k).coerceIn(0f, 1f)
            }
        }
    }

internal fun CoroutineScope.launchScrollToStart(state: LazyGridState) {
    launch { state.animateScrollToItem(0) }
}

/** How far through the list [state] is, from 0 (first app) to 1 (last row in view). */
private fun scrollFraction(state: LazyGridState, columns: Int): Float {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val first = info.visibleItemsInfo.firstOrNull() ?: return 0f
    val rowHeight = (first.size.height + info.mainAxisItemSpacing).toFloat().coerceAtLeast(1f)
    val rows = ceil(total / columns.toFloat())
    val visibleRows = info.viewportSize.height / rowHeight
    val maxRow = (rows - visibleRows).coerceAtLeast(0.001f)
    val row = state.firstVisibleItemIndex / columns + state.firstVisibleItemScrollOffset / rowHeight
    return (row / maxRow).coerceIn(0f, 1f)
}

// ---- scroll bar ----

/**
 * Floats over the right edge of the list: shows while scrolling, fades out after a pause,
 * and can be dragged to move through the list quickly. Takes no space from the list.
 */
@Composable
internal fun DrawerScrollbar(
    state: LazyGridState,
    apps: List<AppEntry>,
    columns: Int,
    reverse: Boolean,
    motion: DrawerMotion,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    controller: HomeController,
    interactive: Boolean,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val scrolling by remember(state) { derivedStateOf { state.isScrollInProgress } }
    val canScroll by remember(state) { derivedStateOf { state.canScrollForward || state.canScrollBackward } }
    val shown = canScroll && (scrolling || dragging)
    val visibility = remember { Animatable(0f) }
    LaunchedEffect(shown) {
        if (shown) {
            visibility.animateTo(1f, tween(120))
        } else {
            delay(900)
            visibility.animateTo(0f, tween(450))
        }
    }
    val touchable by remember { derivedStateOf { visibility.value > 0.05f } }
    if (!canScroll) return

    val glassThumb = motion.scrollbar == "glass"
    val thumbWidth = if (glassThumb) 22.dp else if (dragging) 8.dp else 4.dp
    val thumbHeight = if (glassThumb) 56.dp else 44.dp
    val color = if (glass.lightGlass(Surfaces.Drawer, lightBackground)) Color(0xFF16181C) else Color.White
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxHeight().width(124.dp)) {
        val range = (constraints.maxHeight - with(density) { thumbHeight.toPx() }).coerceAtLeast(1f)
        fun position(): Float {
            val f = if (dragging) dragFraction else scrollFraction(state, columns)
            return (if (reverse) 1f - f else f) * range
        }

        if (dragging && motion.bubble) {
            val total = apps.size
            val index = ((dragFraction * (total - 1)).roundToInt()).coerceIn(0, (total - 1).coerceAtLeast(0))
            val letter = apps.getOrNull(index)?.label?.firstOrNull()?.uppercaseChar()?.toString() ?: ""
            val bubble = 56.dp
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(-(thumbWidth + 30.dp).roundToPx(), (position() - (bubble - thumbHeight).toPx() / 2).roundToInt()) }
                    .size(bubble)
                    .drawBehind { dragFraction }
                    .liquidGlass(backdrop, CircleShape, glass.style(Surfaces.Drawer, lightBackground))
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, style = TextStyle(color = color, fontSize = 24.sp, fontWeight = FontWeight.SemiBold))
            }
        }

        // Touch target wider than the handle; only present while the handle can be seen.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, position().roundToInt()) }
                .size(width = 40.dp, height = thumbHeight)
                .graphicsLayer { alpha = visibility.value }
                .then(
                    if (interactive && touchable) {
                        Modifier.pointerInput(state, columns, reverse) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragging = true
                                    dragFraction = scrollFraction(state, columns)
                                },
                                onVerticalDrag = { change, dy ->
                                    change.consume()
                                    dragFraction = (dragFraction + (if (reverse) -dy else dy) / range).coerceIn(0f, 1f)
                                    val total = state.layoutInfo.totalItemsCount
                                    val target = ((dragFraction * (total - 1)).roundToInt() / columns) * columns
                                    scope.launch { state.scrollToItem(target.coerceIn(0, (total - 1).coerceAtLeast(0))) }
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            val shape = RoundedCornerShape(50)
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(width = thumbWidth, height = thumbHeight)
                    // Re-draw on every scroll step and drawer movement, so the glass samples its new spot.
                    .drawBehind {
                        state.firstVisibleItemScrollOffset
                        controller.drawer.value
                    }
                    .then(
                        if (glassThumb) {
                            Modifier.liquidGlass(backdrop, shape, glass.style(Surfaces.Drawer, lightBackground))
                        } else {
                            Modifier.background(color.copy(alpha = if (dragging) 0.85f else 0.55f), shape)
                        }
                    )
                    .clip(shape),
            )
        }
    }
}

// ---- tabs ----

private val TabWidth = 104.dp

/** One tab as the pill shows it. */
internal data class TabLabel(val name: String, val icon: Int)

/**
 * The drawer's tabs and what they do, shared by the floating pill and the search pill.
 * [selected] is the highlighted tab as a continuous position (it follows the pager while
 * swiping), or -1 for none (searching all apps).
 */
@Stable
internal class TabsBar(
    val labels: List<TabLabel>,
    val selected: () -> Float,
    val onTap: (Int) -> Unit,
    val onLongPress: (Int) -> Unit,
    /** Extra buttons after the tabs: "add" (new tag), "sort", "settings". */
    val buttons: List<String>,
    val onButton: (String) -> Unit,
)

private class TabMetrics(val height: Dp, val font: TextUnit, val icon: Dp)

private fun tabMetrics(size: String) = when (size) {
    "small" -> TabMetrics(36.dp, 13.sp, 16.dp)
    "large" -> TabMetrics(52.dp, 16.sp, 20.dp)
    else -> TabMetrics(44.dp, 14.sp, 18.dp)
}

private fun buttonIcon(id: String): Int = when (id) {
    "add" -> LauncherIcons.Add
    "sort" -> LauncherIcons.Sort
    else -> LauncherIcons.Settings
}

/**
 * The tabs floating at the top or bottom of the drawer. "pill": one glass pill with a
 * highlight that slides with the pager; "chips": a glass chip per tab; "text": labels only.
 */
@Composable
internal fun DrawerTabs(
    bar: TabsBar,
    style: String,
    size: String,
    icons: String,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    controller: HomeController,
    interactive: Boolean,
    modifier: Modifier,
) {
    val m = tabMetrics(size)
    val color = if (glass.lightGlass(Surfaces.Drawer, lightBackground)) Color(0xFF16181C) else Color.White
    val density = LocalDensity.current
    val n = bar.labels.size.coerceAtLeast(1)
    val buttonsWidth = m.height * bar.buttons.size
    val available = with(density) { controller.screenWidth.toDp() } - 40.dp - buttonsWidth
    val tabWidth = minOf(TabWidth, available / n)
    val shape = RoundedCornerShape(50)
    when (style) {
        "chips" -> Row(modifier.height(m.height), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val chipWidth = minOf(TabWidth, (available - 6.dp * (n - 1)) / n)
            bar.labels.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .width(chipWidth)
                        .fillMaxHeight()
                        .drawBehind { controller.drawer.value }
                        .liquidGlass(backdrop, shape, glass.style(Surfaces.Drawer, lightBackground))
                        .clip(shape)
                        .drawBehind {
                            val closeness = (1f - kotlin.math.abs(bar.selected() - i)).coerceIn(0f, 1f)
                            if (bar.selected() >= 0f && closeness > 0f) drawRect(color.copy(alpha = 0.18f * closeness))
                        }
                        .tabGestures(bar, i, interactive),
                    contentAlignment = Alignment.Center,
                ) {
                    TabContent(label, m, color, showIcon(icons, chipWidth), showText(icons, chipWidth))
                }
            }
            for (id in bar.buttons) {
                Box(
                    Modifier
                        .size(m.height)
                        .drawBehind { controller.drawer.value }
                        .liquidGlass(backdrop, CircleShape, glass.style(Surfaces.Drawer, lightBackground))
                        .clip(CircleShape)
                        .then(if (interactive) Modifier.clickable { bar.onButton(id) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) { LauncherIcon(buttonIcon(id), contentDescription = id, tint = color, size = m.icon) }
            }
        }
        "text" -> TabsRow(
            bar, size, icons, color, interactive,
            modifier.height(m.height).width(tabWidth * n + buttonsWidth),
            underline = true,
        )
        else -> Box(
            modifier
                .height(m.height)
                .width(tabWidth * n + buttonsWidth + 8.dp)
                .drawBehind { controller.drawer.value }
                .liquidGlass(backdrop, shape, glass.style(Surfaces.Drawer, lightBackground))
                .clip(shape)
                .padding(4.dp),
        ) {
            TabsRow(bar, size, icons, color, interactive, Modifier.fillMaxSize())
        }
    }
}

/**
 * Tabs side by side with a highlight that follows [TabsBar.selected], and the extra buttons
 * at the end. No glass of its own: used inside the pill and inside the search pill.
 */
@Composable
internal fun TabsRow(
    bar: TabsBar,
    size: String,
    icons: String,
    color: Color,
    interactive: Boolean,
    modifier: Modifier,
    underline: Boolean = false,
) {
    val m = tabMetrics(size)
    BoxWithConstraints(modifier) {
        val n = bar.labels.size.coerceAtLeast(1)
        val buttonSize = maxHeight
        val tabWidth = (maxWidth - buttonSize * bar.buttons.size) / n
        val shape = RoundedCornerShape(50)
        Box(
            Modifier
                .offset { IntOffset((bar.selected().coerceAtLeast(0f) * tabWidth.toPx()).roundToInt(), 0) }
                .size(tabWidth, maxHeight)
                .graphicsLayer { alpha = if (bar.selected() < 0f) 0f else 1f }
                .then(
                    if (underline) {
                        Modifier.drawBehind {
                            val area = this.size
                            val w = area.width * 0.4f
                            drawRoundRect(
                                color,
                                topLeft = Offset((area.width - w) / 2f, area.height - 3.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(w, 2.5.dp.toPx()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                            )
                        }
                    } else {
                        Modifier.background(color.copy(alpha = 0.16f), shape)
                    }
                )
        )
        Row(Modifier.fillMaxSize()) {
            bar.labels.forEachIndexed { i, label ->
                Box(
                    Modifier.width(tabWidth).fillMaxHeight().clip(shape).tabGestures(bar, i, interactive),
                    contentAlignment = Alignment.Center,
                ) {
                    TabContent(label, m, color, showIcon(icons, tabWidth), showText(icons, tabWidth))
                }
            }
            for (id in bar.buttons) {
                Box(
                    Modifier.size(buttonSize).clip(CircleShape).then(if (interactive) Modifier.clickable { bar.onButton(id) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) { LauncherIcon(buttonIcon(id), contentDescription = id, tint = color, size = m.icon) }
            }
        }
    }
}

private fun showIcon(mode: String, width: Dp) = when (mode) {
    "off" -> false
    "on" -> true
    else -> width >= 96.dp
}

/** With icons always on, narrow tabs show only the icon. */
private fun showText(mode: String, width: Dp) = !(mode == "on" && width < 64.dp)

@Composable
private fun TabContent(label: TabLabel, m: TabMetrics, color: Color, icon: Boolean, text: Boolean) {
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
        if (icon) {
            LauncherIcon(label.icon, contentDescription = if (text) null else label.name, tint = color, size = m.icon)
            if (text) Spacer(Modifier.width(7.dp))
        }
        if (text) {
            Text(
                label.name,
                style = TextStyle(color = color, fontSize = m.font, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Modifier.tabGestures(bar: TabsBar, index: Int, interactive: Boolean): Modifier =
    if (!interactive) this else pointerInput(bar, index) {
        detectTapGestures(onTap = { bar.onTap(index) }, onLongPress = { bar.onLongPress(index) })
    }

/**
 * Held tab: what can be changed about this tab, in the order the user chose (Settings →
 * Drawer tabs and tags → Held tab menu). No settings entries unless the user adds them.
 */
@Composable
internal fun TabMenu(
    tab: DrawerTabDef?,
    tabIndex: Int,
    tabCount: Int,
    config: DrawerConfig,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    controller: HomeController,
    atTop: Boolean,
    onDialog: (TabDialog) -> Unit,
    onDismiss: () -> Unit,
) {
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    val shape = RoundedCornerShape(24.dp)
    var sortOpen by remember { mutableStateOf(false) }
    val org = controller.data.organisation
    val tagSource = tab?.sources?.firstOrNull { it.startsWith("tag:") }?.removePrefix("tag:")
    val orgIndex = tab?.let { t -> org.value.value.tabs.indexOfFirst { it.id == t.id } } ?: -1
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
                .then(if (atTop) Modifier.statusBarsPadding().padding(top = 64.dp) else Modifier.navigationBarsPadding().padding(bottom = SearchZone + 60.dp))
                .width(270.dp)
                .liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground))
                .clip(shape)
                .padding(vertical = 8.dp),
        ) {
            if (tab != null) {
                Text(
                    tab.name,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    style = TextStyle(color = color.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
            for (entry in config.tabMenu) {
                when (entry) {
                    "rename" -> if (tab != null) MenuRow("Rename", LauncherIcons.Edit, color) { onDismiss(); onDialog(TabDialog.Rename(tab.id, tab.name)) }
                    "icon" -> if (tab != null) MenuRow("Icon", tabIcon(tab), color) { onDismiss(); onDialog(TabDialog.Icon(tab.id)) }
                    "contents" -> if (tab != null) MenuRow("Which apps it shows", LauncherIcons.Tune, color) { onDismiss(); onDialog(TabDialog.Contents(tab.id)) }
                    "apps" -> if (tagSource != null) MenuRow("Choose apps", LauncherIcons.Apps, color) { onDismiss(); onDialog(TabDialog.Apps(tagSource)) }
                    "sort" -> if (tab != null) {
                        val current = tab.sort
                        val label = current?.let { c -> dev.liquid.launcher.core.settings.LauncherSettings.DrawerSort.options.firstOrNull { it.first == c }?.second } ?: "Like the drawer"
                        MenuRow("Sort: $label", LauncherIcons.Sort, color) { sortOpen = !sortOpen }
                        if (sortOpen) {
                            val options = listOf<Pair<String?, String>>(null to "Like the drawer") + dev.liquid.launcher.core.settings.LauncherSettings.DrawerSort.options
                            for ((value, name) in options) {
                                MenuRow(name, if (current == value) LauncherIcons.Check else null, color, indent = true) {
                                    org.update { OrganisationOps.editTab(it, tab.id) { t -> t.copy(sort = value) } }
                                    onDismiss()
                                }
                            }
                        }
                    }
                    "move" -> if (tab != null && orgIndex >= 0) {
                        val size = org.value.value.tabs.size
                        if (orgIndex > 0) MenuRow("Move left", LauncherIcons.ArrowBack, color) { org.update { OrganisationOps.moveTab(it, orgIndex, orgIndex - 1) }; onDismiss() }
                        if (orgIndex < size - 1) MenuRow("Move right", LauncherIcons.ChevronRight, color) { org.update { OrganisationOps.moveTab(it, orgIndex, orgIndex + 1) }; onDismiss() }
                    }
                    "newtag" -> MenuRow("New tag…", LauncherIcons.Add, color) { onDismiss(); onDialog(TabDialog.NewTag) }
                    "remove" -> if (tab != null && tab.source != "personal") MenuRow("Remove tab", LauncherIcons.Close, color) {
                        org.update { OrganisationOps.removeTab(it, tab.id) }
                        onDismiss()
                    }
                    "edit" -> MenuRow("Edit tabs and tags", LauncherIcons.Label, color) { onDismiss(); controller.openSection(LauncherSettings.Sections.Tags.id) }
                    "settings" -> MenuRow("Drawer settings", LauncherIcons.Settings, color) { onDismiss(); controller.openSection(LauncherSettings.Sections.Drawer.id) }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: Int?, color: Color, indent: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = if (indent) 54.dp else 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (icon != null) LauncherIcon(icon, contentDescription = null, tint = color, size = 20.dp)
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = TextStyle(color = color, fontSize = 15.sp))
    }
}
