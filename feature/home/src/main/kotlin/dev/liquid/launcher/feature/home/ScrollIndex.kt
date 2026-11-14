package dev.liquid.launcher.feature.home

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.GridIndex
import dev.liquid.launcher.core.designsystem.liquidGlass
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

/** Width of the touch strip along the edge. */
internal val IndexStrip = 24.dp

private val MaxBulge = 64.dp
private val BubbleSize = 62.dp
private val EdgeInset = 12.dp

/** Names that turn a section's index mark into a star. */
private val FavouriteNames = setOf("favourites", "favorites", "favourite", "favorite", "fav", "★")

/**
 * Touch, glide and highlight state of the index. The list position is a continuous row
 * ([GridIndex.rowAt]), so it moves smoothly between letters instead of stepping.
 */
@Stable
private class IndexTouch {
    var touching by mutableStateOf(false)
    var fingerY by mutableFloatStateOf(0f)

    /** Entry under the finger, or -1. */
    var active by mutableIntStateOf(-1)

    /** Row the list is gliding towards. */
    var target = 0f

    /** Row the list is at during a glide; NaN when no glide is running. */
    var position = Float.NaN
}

/**
 * Which apps the index is pointing at, for the highlight. Written by [ScrollIndex], read by
 * every drawer item in its graphics layer. It depends only on list positions (never on where
 * an item is on screen), so an item that scrolls in during the glide is highlighted at once.
 */
@Stable
internal class IndexHighlight {
    /** 0 = no highlight, 1 = full; springs in on touch and out on release. */
    val pop = Animatable(0f)

    /** Items (list positions) of the entry under the finger. */
    var from by mutableIntStateOf(-1)
    var to by mutableIntStateOf(-1)
    var enabled = true
}

/**
 * The highlight on one list item: apps of the held letter stay bright and lift slightly, the
 * others fade. The list itself does not move.
 */
internal fun GraphicsLayerScope.applyIndexHighlight(highlight: IndexHighlight, itemIndex: Int) {
    val p = highlight.pop.value.coerceIn(0f, 1f)
    if (p <= 0.002f || !highlight.enabled || highlight.from < 0) return
    if (itemIndex in highlight.from until highlight.to) {
        val scale = 1f + 0.05f * p
        scaleX = scale
        scaleY = scale
    } else {
        alpha = 1f - 0.62f * p
    }
}

/**
 * The drawer's letter index: letters along one edge that bend out around the finger, a
 * bubble with the letter (or section name) under it, and a list that follows the finger
 * continuously. Takes no space unless its letters are always shown.
 */
@Composable
internal fun ScrollIndex(
    state: LazyGridState,
    index: GridIndex,
    config: IndexConfig,
    reverse: Boolean,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    controller: HomeController,
    interactive: Boolean,
    highlight: IndexHighlight,
    modifier: Modifier,
) {
    val entries = index.entries
    if (entries.isEmpty()) return
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val touch = remember { IndexTouch() }
    val pop = highlight.pop
    /** How far the letter column has slid in from its edge, towards the finger (pixels). */
    val column = remember { Animatable(0f) }
    val springSpec = spring<Float>(dampingRatio = 0.78f, stiffness = config.stiffness)
    val color = if (glass.lightGlass(Surfaces.Drawer, lightBackground)) Color(0xFF16181C) else Color.White

    // ---- visibility ----
    val scrolling by remember(state) { derivedStateOf { state.isScrollInProgress } }
    val shown = when (config.visibility) {
        "always" -> true
        "scroll" -> touch.touching || scrolling
        else -> touch.touching
    }
    val visibility = remember { Animatable(if (shown) 1f else 0f) }
    LaunchedEffect(shown) {
        if (shown) {
            visibility.animateTo(1f, tween(120))
        } else {
            // Letters spring back to the edge first, then hide.
            kotlinx.coroutines.delay(config.hideMs.toLong())
            visibility.animateTo(0f, tween(320))
        }
    }

    // ---- the list following the finger ----
    LaunchedEffect(index, touch.touching) {
        if (!touch.touching && touch.position.isNaN()) return@LaunchedEffect
        var last = 0L
        var applied = Float.NaN
        if (touch.position.isNaN()) touch.position = currentRow(state, index)
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 16f else ((now - last) / 1_000_000f).coerceIn(1f, 50f)
            last = now
            val tau = config.followMs.toFloat()
            val k = if (tau <= 0f) 1f else 1f - exp(-dt / tau)
            touch.position += (touch.target - touch.position) * k
            if (applied.isNaN() || abs(touch.position - applied) > 0.0005f) {
                scrollToRow(state, index, touch.position)
                applied = touch.position
            }
            if (!touch.touching && abs(touch.target - touch.position) < 0.004f) break
        }
        touch.position = Float.NaN
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val height = constraints.maxHeight.toFloat()
        val width = constraints.maxWidth.toFloat()
        val n = entries.size
        val spacing = minOf(with(density) { config.spacingDp.dp.toPx() }, (height - with(density) { 16.dp.toPx() }) / n).coerceAtLeast(1f)
        val top = (height - spacing * n) / 2f
        val edge = with(density) { EdgeInset.toPx() }
        val bulgeMax = with(density) { MaxBulge.toPx() } * config.curve
        val radius = maxOf(spacing * 4.5f, with(density) { 96.dp.toPx() })
        SideEffect { highlight.enabled = config.focus }
        /** Items of entry [i], for the highlight. */
        fun focusOn(i: Int) {
            highlight.from = entries.getOrNull(i)?.firstItem ?: -1
            highlight.to = entries.getOrNull(i + 1)?.firstItem ?: Int.MAX_VALUE
        }
        val stripWidth = with(density) { IndexStrip.toPx() }
        val maxTravel = width * 0.62f
        /**
         * The column slides towards a finger at [x] (in this box): with "Letters follow the
         * finger" at 100 % the letters' base line sits at the finger, and the curve pushes the
         * letters next to it out from under the finger.
         */
        fun slideTowards(x: Float) {
            val fromEdge = if (config.right) width - x else x
            val target = ((fromEdge - edge) * config.track).coerceIn(0f, maxTravel)
            scope.launch { column.animateTo(target, springSpec) }
        }

        /** Visual slot of entry [i]: entries run bottom to top when the list starts at the bottom. */
        fun slotOf(i: Int) = if (reverse) n - 1 - i else i
        fun centreY(i: Int) = top + (slotOf(i) + 0.5f) * spacing
        /** Index position (0 = centre of the first entry) under a finger at [y]. */
        fun positionAt(y: Float): Float {
            val slot = (y - top) / spacing - 0.5f
            return if (reverse) (n - 1) - slot else slot
        }
        fun bulgeAt(y: Float): Float {
            if (bulgeMax <= 0f) return 0f
            val d = (y - touch.fingerY) / radius
            if (abs(d) >= 1f) return 0f
            return bulgeMax * pop.value * (1f + cos(PI.toFloat() * d)) / 2f
        }
        fun tick() {
            if (config.haptics == "off") return
            val strong = config.haptics == "strong"
            val constant = when {
                Build.VERSION.SDK_INT >= 34 -> if (strong) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                strong -> HapticFeedbackConstants.VIRTUAL_KEY
                else -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
            }
            view.performHapticFeedback(constant)
        }
        fun follow(y: Float) {
            touch.fingerY = y.coerceIn(0f, height)
            val position = positionAt(touch.fingerY)
            touch.target = index.rowAt(position)
            val entry = position.roundToInt().coerceIn(0, n - 1)
            if (entry != touch.active) {
                touch.active = entry
                focusOn(entry)
                tick()
            }
        }

        val measurer = rememberTextMeasurer(cacheSize = 96)
        val normal = remember(entries, config.letterSp, color, HomeTheme.font) {
            val style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = config.letterSp.sp, fontWeight = FontWeight.Medium)
            entries.map { measurer.measure(glyph(it.label, it.section), style) }
        }
        val bold = remember(entries, config.letterSp, color, HomeTheme.font) {
            val style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = config.letterSp.sp, fontWeight = FontWeight.Bold)
            entries.map { measurer.measure(glyph(it.label, it.section), style) }
        }

        if (controller.indexDemo) {
            LaunchedEffect(index, height) {
                touch.touching = true
                touch.active = -1
                pop.snapTo(1f)
                var t = 0f
                while (isActive) {
                    withFrameNanos { }
                    t += 0.006f
                    // Down and up the letters, easing at the ends.
                    val swing = (1f - cos(t * 2f * PI.toFloat())) / 2f
                    touch.fingerY = top + spacing * (0.5f + swing * (n - 1))
                    // The pretend finger also wanders inwards a little, so the column's slide shows.
                    column.snapTo(((1f - cos(t * 3f * PI.toFloat())) / 2f) * with(density) { 48.dp.toPx() } * config.track)
                    touch.active = positionAt(touch.fingerY).roundToInt().coerceIn(0, n - 1)
                    focusOn(touch.active)
                }
            }
        }

        // ---- letters ----
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = visibility.value * overlayFade(controller) }
                .drawBehind {
                    // Read so scrolling updates the highlight while not touching.
                    state.firstVisibleItemIndex
                    state.firstVisibleItemScrollOffset
                    val highlighted = if (touch.touching) touch.active else index.entryForRow(currentRow(state, index))
                    for (i in 0 until n) {
                        val y = centreY(i)
                        val bend = bulgeAt(y)
                        val slide = column.value
                        val x = if (config.right) width - edge - slide - bend else edge + slide + bend
                        val on = i == highlighted
                        val layout: TextLayoutResult = if (on) bold[i] else normal[i]
                        val w = layout.size.width.toFloat()
                        val h = layout.size.height.toFloat()
                        // Lighter at rest: the letters float over the apps' labels.
                        val alpha = if (on) 1f else if (touch.touching) 0.72f else 0.5f
                        val grow = if (on && touch.touching) 1.25f else 1f
                        scale(grow, Offset(x, y)) {
                            if (entries[i].section && glyph(entries[i].label, true) != "★") {
                                drawCircle(color.copy(alpha = alpha * 0.8f), radius = maxOf(w, h) * 0.62f, center = Offset(x, y), style = Stroke(1.dp.toPx()))
                            }
                            drawText(layout, topLeft = Offset(x - w / 2f, y - h / 2f), alpha = alpha)
                        }
                    }
                }
        )

        // ---- bubble ----
        if (config.bubble != "none") {
            val label = entries.getOrNull(touch.active)?.label.orEmpty()
            val bubbleColor = HomeTheme.accent
            val textColor = if (config.bubble == "solid") (if (androidx.core.graphics.ColorUtils.calculateLuminance(android.graphics.Color.valueOf(bubbleColor.red, bubbleColor.green, bubbleColor.blue).toArgb()) > 0.5) Color(0xFF16181C) else Color.White) else color
            val shape = RoundedCornerShape(50)
            Box(
                Modifier
                    .align(if (config.right) Alignment.TopEnd else Alignment.TopStart)
                    .offset {
                        val gap = 18.dp.toPx() + edge + column.value + bulgeMax * pop.value
                        val y = (touch.fingerY - BubbleSize.toPx() / 2f).coerceIn(0f, (height - BubbleSize.toPx()).coerceAtLeast(0f))
                        IntOffset((if (config.right) -gap else gap).roundToInt(), y.roundToInt())
                    }
                    .graphicsLayer {
                        val p = pop.value
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = 0.6f + 0.4f * p
                        scaleY = 0.6f + 0.4f * p
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (config.right) 1f else 0f, 0.5f)
                    }
                    .height(BubbleSize)
                    .widthIn(min = BubbleSize)
                    // Re-draw as the finger moves, so the glass samples its new spot.
                    .drawBehind { touch.fingerY }
                    .then(
                        if (config.bubble == "glass") {
                            Modifier.liquidGlass(backdrop, shape, glass.style(Surfaces.Drawer, lightBackground))
                        } else {
                            Modifier.background(bubbleColor, shape)
                        }
                    )
                    .clip(shape)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = TextStyle(fontFamily = HomeTheme.font, color = textColor, fontSize = if (label.length <= 2) 28.sp else 17.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            }
        }

        // ---- touch strip ----
        if (interactive) {
            Box(
                Modifier
                    .align(if (config.right) Alignment.TopEnd else Alignment.TopStart)
                    .fillMaxHeight()
                    .width(IndexStrip)
                    .pointerInput(index, reverse, spacing, top, width, config.track, config.stiffness) {
                        // The strip's own x in the index box, to turn finger positions into box positions.
                        val stripLeft = if (config.right) width - stripWidth else 0f
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            touch.active = -1
                            touch.touching = true
                            follow(down.position.y)
                            slideTowards(stripLeft + down.position.x)
                            scope.launch { pop.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 700f)) }
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                                follow(change.position.y)
                                slideTowards(stripLeft + change.position.x)
                            }
                            touch.touching = false
                            // Back to the edge with a spring, then the letters hide (see visibility).
                            scope.launch { column.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = config.stiffness)) }
                            scope.launch { pop.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 500f)) }
                        }
                    }
            )
        }
    }
}

/** The mark drawn in the index: the letter, a star for favourites, or a section's initial. */
private fun glyph(label: String, section: Boolean): String = when {
    !section -> label
    label.trim().lowercase() in FavouriteNames -> "★"
    else -> label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"
}

/** The index fades with the rest of the drawer's glass parts. */
private fun overlayFade(controller: HomeController): Float = controller.drawer.value.coerceIn(0f, 1f)

/** The list's position as a continuous row. */
private fun currentRow(state: LazyGridState, index: GridIndex): Float {
    val first = state.firstVisibleItemIndex
    val row = index.rowOfItem(first)
    val size = rowHeight(state, index, row)
    return row + state.firstVisibleItemScrollOffset / size
}

private fun rowHeight(state: LazyGridState, index: GridIndex, row: Int): Float {
    val info = state.layoutInfo
    val firstItem = index.firstItemOfRow(row)
    val visible = info.visibleItemsInfo
    val match = visible.firstOrNull { it.index == firstItem }
    val sample = match ?: visible.firstOrNull { index.isHeaderRow(index.rowOfItem(it.index)) == index.isHeaderRow(row) } ?: visible.firstOrNull()
    return ((sample?.size?.height ?: 1) + info.mainAxisItemSpacing).toFloat().coerceAtLeast(1f)
}

private suspend fun scrollToRow(state: LazyGridState, index: GridIndex, row: Float) {
    if (index.rowCount == 0) return
    val whole = floor(row).toInt().coerceIn(0, index.rowCount - 1)
    val fraction = (row - whole).coerceIn(0f, 1f)
    val offset = (fraction * rowHeight(state, index, whole)).roundToInt()
    state.scrollToItem(index.firstItemOfRow(whole), offset)
}
