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
import androidx.compose.foundation.pager.PagerState
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

/**
 * The drawer's pages in a glass pill. The highlight follows the pager as it is swiped. Tap
 * switches, tapping the current tab jumps to the start, holding opens quick options.
 */
@Composable
internal fun DrawerTabs(
    labels: List<Pair<String, Int>>,
    pager: PagerState,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    controller: HomeController,
    interactive: Boolean,
    onReselect: (Int) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(50)
    val color = if (glass.lightGlass(Surfaces.Drawer, lightBackground)) Color(0xFF16181C) else Color.White
    Box(
        modifier
            .height(44.dp)
            .width(TabWidth * labels.size + 8.dp)
            .drawBehind { controller.drawer.value }
            .liquidGlass(backdrop, shape, glass.style(Surfaces.Drawer, lightBackground))
            .clip(shape)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(((pager.currentPage + pager.currentPageOffsetFraction) * TabWidth.toPx()).roundToInt(), 0) }
                .size(TabWidth, 36.dp)
                .background(color.copy(alpha = 0.16f), shape)
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, (label, icon) ->
                Row(
                    Modifier
                        .width(TabWidth)
                        .fillMaxHeight()
                        .then(
                            if (!interactive) Modifier else Modifier.pointerInput(index) {
                                detectTapGestures(
                                    onTap = {
                                        if (pager.currentPage == index) onReselect(index)
                                        else scope.launch { pager.animateScrollToPage(index) }
                                    },
                                    onLongPress = { onLongPress() },
                                )
                            }
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LauncherIcon(icon, contentDescription = null, tint = color, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}

/** Held tab: sort order and a way into the drawer settings. */
@Composable
internal fun TabQuickMenu(
    config: DrawerConfig,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    controller: HomeController,
    atTop: Boolean,
    onDismiss: () -> Unit,
) {
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
                .then(if (atTop) Modifier.statusBarsPadding().padding(top = 56.dp) else Modifier.navigationBarsPadding().padding(bottom = SearchZone + 56.dp))
                .width(260.dp)
                .liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground))
                .clip(shape)
                .padding(vertical = 8.dp),
        ) {
            Text(
                "Sort",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = TextStyle(color = color.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
            for ((value, label) in LauncherSettings.DrawerSort.options) {
                MenuRow(
                    label = label,
                    icon = if (config.sort == value) LauncherIcons.Check else null,
                    color = color,
                ) {
                    controller.data.settings.set(LauncherSettings.DrawerSort, value)
                    onDismiss()
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(color.copy(alpha = 0.12f)))
            MenuRow("Drawer settings", LauncherIcons.Settings, color) {
                onDismiss()
                controller.openSection(LauncherSettings.Sections.Drawer.id)
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: Int?, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (icon != null) LauncherIcon(icon, contentDescription = null, tint = color, size = 20.dp)
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = TextStyle(color = color, fontSize = 15.sp))
    }
}
