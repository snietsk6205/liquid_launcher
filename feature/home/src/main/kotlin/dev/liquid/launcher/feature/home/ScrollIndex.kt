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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.GridIndex
import dev.liquid.launcher.core.designsystem.liquidGlass
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Width of the touch strip along the edge. */
internal val IndexStrip = 24.dp

private val BubbleSize = 54.dp
private val EdgeInset = 16.dp

/** Space between the finger's touch point and the bubble's near edge with "above": about half a thumb plus a margin. */
private val BubbleGap = 52.dp

/**
 * Centre of the letter bubble for a finger at ([fingerX], [fingerY]) in a box [width] wide,
 * pulling the string from the right edge ([fromRight]) or the left one (0.6.3, D91; 0.6.4, D97).
 * - "beside": past the bent letters: [anchorX] is the inner edge of the letter under the finger,
 *   and the bubble's near edge is [gap] further towards the middle, [lift] higher. When there is no
 *   room on that side, above the finger instead.
 * - "above": straight above the finger by [aboveGap] plus half the bubble (below it near the top).
 * - "finger": at the finger, as before 0.6.3.
 */
internal fun bubbleCentre(
    place: String,
    fingerX: Float,
    fingerY: Float,
    anchorX: Float,
    fromRight: Boolean,
    bubbleWidth: Float,
    bubbleHeight: Float,
    gap: Float,
    aboveGap: Float,
    lift: Float,
    width: Float,
): Offset {
    fun above(): Offset {
        val y = fingerY - aboveGap - bubbleHeight / 2f
        return Offset(fingerX, if (y - bubbleHeight / 2f >= 0f) y else fingerY + aboveGap + bubbleHeight / 2f)
    }
    return when (place) {
        "finger" -> Offset(fingerX, fingerY)
        "above" -> above()
        else -> {
            val x = if (fromRight) anchorX - gap - bubbleWidth / 2f else anchorX + gap + bubbleWidth / 2f
            val fits = if (fromRight) x - bubbleWidth / 2f >= 0f else x + bubbleWidth / 2f <= width
            if (fits) Offset(x, fingerY - lift) else above()
        }
    }
}

/**
 * How far the string's apex is pulled in from its resting line (0.6.4, D97): [letterGap] past the
 * finger, so the letters bend out from under the thumb towards the middle of the screen, plus
 * [track] of how far the finger itself has moved in ([inward], from the resting line), capped at [maxPull].
 */
internal fun pullFor(inward: Float, letterGap: Float, track: Float, maxPull: Float): Float =
    (letterGap + maxOf(0f, inward) * track).coerceIn(0f, maxPull)

/** With pinned ends, the curve never gets tighter than this many letters on either side of the finger. */
private const val MIN_REACH_SLOTS = 5f

/** Names that turn a section's index mark into a star. */
private val FavouriteNames = setOf("favourites", "favorites", "favourite", "favorite", "fav", "★")

/**
 * Touch, string and glide state of the index. The list position is a continuous row
 * ([GridIndex.rowAt]), so it moves smoothly between letters instead of stepping.
 */
@Stable
private class IndexTouch {
    var touching by mutableStateOf(false)

    /** The finger, in the index box. */
    var fingerX by mutableFloatStateOf(0f)
    var fingerY by mutableFloatStateOf(0f)

    /** The edge the string is pulled from: true = right. */
    var fromRight by mutableStateOf(true)

    /** How far the string's apex is pulled in from its edge right now (pixels), and its speed. */
    var pull by mutableFloatStateOf(0f)
    var pullVelocity = 0f

    /** Where the finger wants the apex. */
    var pullTarget = 0f

    /** Entry under the finger, or -1. */
    var active by mutableIntStateOf(-1)

    /** Row the list is gliding towards. */
    var target = 0f

    /** Row the list is at during a glide; NaN when no glide is running. */
    var position = Float.NaN

    /** The rail's magnifier: the entry under the finger, continuous (0.9.6). */
    var lens by mutableFloatStateOf(0f)
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
 * How far the letter [slots] letters away from the finger follows the pull, 0..1: a raised
 * cosine that is 1 at the finger and 0 at [reach] letters and beyond, with no kink at either end.
 */
internal fun stringWeight(slots: Float, reach: Float): Float {
    val d = abs(slots)
    if (reach <= 0f || d >= reach) return 0f
    return (1f + cos(PI.toFloat() * d / reach)) / 2f
}

/**
 * How many letters the curve spans on one side of the finger. [toEnd] is the distance from the
 * finger to that end of the column, [free] the span the "curve reach" setting gives. With
 * [pinned] ends the curve is fitted between the finger and the end, so the end letter stays on
 * the edge (it only gives way when the finger is closer to it than [MIN_REACH_SLOTS]).
 */
internal fun stringReach(toEnd: Float, free: Float, pinned: Boolean): Float =
    if (pinned) minOf(free, maxOf(toEnd, MIN_REACH_SLOTS)) else free

/**
 * The drawer's letter index: a column of letters held at the top and bottom of an edge that
 * bends like a string pulled by the finger. Since 0.6.4 (D97) the string bends out from under the
 * thumb: the letter under the finger rides the apex "Letters beside the finger" past it, towards
 * the middle of the screen, the letters between lie on a smooth curve, and the further in the
 * finger pulls, the further the curve follows. The bubble sits past the letters.
 * The app list never bends: it scrolls to the letter. On release the string springs back
 * straight and hides.
 *
 * Opus built this twice from a description (0.4.1: the list bent; 0.4.2: the whole column
 * slid sideways with a small bulge). This version follows the user's reference screenshots.
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
    if (config.style == "rail") {
        RailIndex(state, index, config, reverse, glass, lightBackground, backdrop, controller, interactive, highlight, modifier)
        return
    }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val touch = remember { IndexTouch() }
    val pop = highlight.pop
    val color = if (glass.lightGlass(Surfaces.Drawer, lightBackground)) Color(0xFF16181C) else Color.White
    SideEffect { if (!touch.touching && config.side != "both") touch.fromRight = config.side != "left" }

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
            // The string springs back straight first, then the letters hide.
            kotlinx.coroutines.delay(config.hideMs.toLong())
            visibility.animateTo(0f, tween(320))
        }
    }

    // ---- the list following the finger ----
    GlideList(state, index, touch, config.followMs)

    // ---- the string: one spring pulls the apex after the finger, and back to the edge on release ----
    LaunchedEffect(touch.touching, config.stiffness) {
        if (!touch.touching && abs(touch.pull) < 0.5f && abs(touch.pullVelocity) < 5f) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 1f / 90f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            last = now
            val held = touch.touching
            val target = if (held) touch.pullTarget else 0f
            val stiffness = config.stiffness
            // Held: it follows without wobbling. Let go: under-damped, so it snaps back like a plucked string.
            val damping = 2f * (if (held) 0.86f else 0.5f) * sqrt(stiffness)
            var x = touch.pull
            var v = touch.pullVelocity
            repeat(2) {
                val h = dt / 2f
                v += (-stiffness * (x - target) - damping * v) * h
                x += v * h
            }
            touch.pullVelocity = v
            touch.pull = x
            if (!held && abs(x) < 0.5f && abs(v) < 5f) {
                touch.pull = 0f
                touch.pullVelocity = 0f
                break
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val height = constraints.maxHeight.toFloat()
        val width = constraints.maxWidth.toFloat()
        val n = entries.size
        val spacing = minOf(with(density) { config.spacingDp.dp.toPx() }, (height - with(density) { 16.dp.toPx() }) / n).coerceAtLeast(1f)
        val top = (height - spacing * n) / 2f
        val edge = with(density) { EdgeInset.toPx() }
        val letterGap = with(density) { config.letterGapDp.dp.toPx() }
        val bubbleGap = with(density) { config.bubbleGapDp.dp.toPx() }
        val aboveGap = with(density) { BubbleGap.toPx() }
        val maxPull = width * 0.86f
        val freeReach = (config.reach * n).coerceAtLeast(MIN_REACH_SLOTS)
        SideEffect { highlight.enabled = config.focus }
        /** Items of entry [i], for the highlight. */
        fun focusOn(i: Int) {
            highlight.from = entries.getOrNull(i)?.firstItem ?: -1
            highlight.to = entries.getOrNull(i + 1)?.firstItem ?: Int.MAX_VALUE
        }

        /** The line the letters rest on. */
        fun restX(right: Boolean) = if (right) width - edge else edge

        /** Visual slot of entry [i]: entries run bottom to top when the list starts at the bottom. */
        fun slotOf(i: Int) = if (reverse) n - 1 - i else i
        fun centreY(i: Int) = top + (slotOf(i) + 0.5f) * spacing
        /** Index position (0 = centre of the first entry) under a finger at [y]. */
        fun positionAt(y: Float): Float {
            val slot = (y - top) / spacing - 0.5f
            return if (reverse) (n - 1) - slot else slot
        }
        /** Where the letter at height [y] is drawn: on the string, pulled in around the finger. */
        fun letterX(y: Float): Float {
            val right = touch.fromRight
            val pull = touch.pull
            if (abs(pull) < 0.01f) return restX(right)
            val slots = (y - touch.fingerY) / spacing
            // Distance from the finger to the end of the column on this letter's side, in letters.
            val toEnd = if (slots < 0f) (touch.fingerY - (top + 0.5f * spacing)) / spacing else ((top + (n - 0.5f) * spacing) - touch.fingerY) / spacing
            val w = stringWeight(slots, stringReach(toEnd, freeReach, config.pinned))
            return if (right) restX(true) - pull * w else restX(false) + pull * w
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
        /** The finger is at ([x], [y]) in this box: aim the string and the list at it. */
        fun follow(x: Float, y: Float) {
            touch.fingerX = x
            touch.fingerY = y.coerceIn(top + 0.5f * spacing, top + (n - 0.5f) * spacing)
            val inward = if (touch.fromRight) restX(true) - x else x - restX(false)
            touch.pullTarget = pullFor(inward, letterGap, config.track, maxPull)
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
            // Settings preview: a pretend finger runs along the letters and pulls the string in and out.
            LaunchedEffect(index, height, width, config.side) {
                touch.fromRight = config.side != "left"
                touch.touching = true
                touch.active = -1
                pop.snapTo(1f)
                var t = 0f
                while (isActive) {
                    withFrameNanos { }
                    t += 0.006f
                    val swing = (1f - cos(t * 2f * PI.toFloat())) / 2f
                    val inward = width * 0.4f * (1f - cos(t * 3f * PI.toFloat())) / 2f
                    val y = top + spacing * (0.5f + swing * (n - 1))
                    follow(if (touch.fromRight) restX(true) - inward else restX(false) + inward, y)
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
                    val held = touch.touching
                    val highlighted = if (held) touch.active else index.entryForRow(currentRow(state, index))
                    for (i in 0 until n) {
                        val y = centreY(i)
                        val x = letterX(y)
                        val on = i == highlighted
                        val layout: TextLayoutResult = if (on) bold[i] else normal[i]
                        val w = layout.size.width.toFloat()
                        val h = layout.size.height.toFloat()
                        // Lighter at rest: the letters float over the apps' labels.
                        val alpha = if (on) 1f else if (held) 0.86f else 0.5f
                        val grow = if (on && held) 1.3f else 1f
                        scale(grow, Offset(x, y)) {
                            if (entries[i].section && glyph(entries[i].label, true) != "★") {
                                drawCircle(color.copy(alpha = alpha * 0.8f), radius = maxOf(w, h) * 0.62f, center = Offset(x, y), style = Stroke(1.dp.toPx()))
                            }
                            drawText(layout, topLeft = Offset(x - w / 2f, y - h / 2f), alpha = alpha)
                        }
                    }
                }
        )

        // ---- bubble: beside the finger (or where "Bubble position" says), the letter it is on ----
        if (config.bubble != "none") {
            val label = entries.getOrNull(touch.active)?.label.orEmpty()
            val bubbleColor = HomeTheme.accent
            val textColor = if (config.bubble == "solid") readableOn(bubbleColor) else color
            val shape = RoundedCornerShape(50)
            Box(
                Modifier
                    // Placed by its centre, read while placing: the bubble follows the finger without recomposing.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val right = touch.fromRight
                            // "Above" and "finger": never on the letters' resting line.
                            val x = if (right) minOf(touch.fingerX, restX(true) - edge) else maxOf(touch.fingerX, restX(false) + edge)
                            // "Beside": past the letter under the finger, which rides the string's apex (it grows by 1.3 while held).
                            val letterHalf = (bold.getOrNull(touch.active)?.size?.width ?: 0) * 0.65f
                            val anchor = if (right) restX(true) - touch.pull - letterHalf else restX(false) + touch.pull + letterHalf
                            val centre = bubbleCentre(
                                config.bubblePlace, x, touch.fingerY, anchor, right,
                                placeable.width.toFloat(), placeable.height.toFloat(), bubbleGap, aboveGap, 0f, width,
                            )
                            val left = (centre.x - placeable.width / 2f).coerceIn(0f, (width - placeable.width).coerceAtLeast(0f))
                            val topY = (centre.y - placeable.height / 2f).coerceIn(0f, (height - placeable.height).coerceAtLeast(0f))
                            placeable.place(left.roundToInt(), topY.roundToInt())
                        }
                    }
                    .graphicsLayer {
                        val p = pop.value
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = 0.6f + 0.4f * p
                        scaleY = 0.6f + 0.4f * p
                    }
                    .height(BubbleSize)
                    .widthIn(min = BubbleSize)
                    // Re-draw as the finger and the string move, so the glass samples its new spot.
                    .drawBehind {
                        touch.fingerX
                        touch.fingerY
                        touch.pull
                    }
                    .then(
                        if (config.bubble == "glass") {
                            Modifier.liquidGlass(backdrop, shape, glass.style(Surfaces.Drawer, lightBackground))
                        } else {
                            Modifier.background(bubbleColor, shape)
                        }
                    )
                    .clip(shape)
                    .padding(horizontal = if (label.length <= 2) 0.dp else 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = TextStyle(fontFamily = HomeTheme.font, color = textColor, fontSize = if (label.length <= 2) 26.sp else 16.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            }
        }

        // ---- touch strips: one per edge in use ----
        if (interactive) {
            val stripPx = with(density) { IndexStrip.toPx() }
            for (right in listOf(true, false)) {
                if (config.side != "both" && (config.side != "left") != right) continue
                Box(
                    Modifier
                        .align(if (right) Alignment.TopEnd else Alignment.TopStart)
                        .fillMaxHeight()
                        .width(IndexStrip)
                        // Pulling in from the edge is the system's back gesture; ask for the strip (the system grants up to 200 dp per edge).
                        .systemGestureExclusion()
                        .pointerInput(index, reverse, spacing, top, width, config.track, right) {
                            // The strip's own x in the index box, to turn finger positions into box positions.
                            val stripLeft = if (right) width - stripPx else 0f
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                touch.fromRight = right
                                touch.active = -1
                                touch.touching = true
                                follow(stripLeft + down.position.x, down.position.y)
                                scope.launch { pop.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 700f)) }
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) break
                                    follow(stripLeft + change.position.x, change.position.y)
                                }
                                touch.touching = false
                                scope.launch { pop.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 500f)) }
                            }
                        }
                )
            }
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

/** The list glides to [IndexTouch.target] while the finger moves, with the "Follow" delay (shared by the string and the rail). */
@Composable
private fun GlideList(state: LazyGridState, index: GridIndex, touch: IndexTouch, followMs: Int) {
    LaunchedEffect(index, touch.touching) {
        if (!touch.touching && touch.position.isNaN()) return@LaunchedEffect
        var last = 0L
        var applied = Float.NaN
        if (touch.position.isNaN()) touch.position = currentRow(state, index)
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 16f else ((now - last) / 1_000_000f).coerceIn(1f, 50f)
            last = now
            val tau = followMs.toFloat()
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
}

private val RailWidth = 30.dp
private val LensWidth = 66.dp
private val LensStep = 34.dp

/**
 * The rail (0.9.6, P6). Android keeps at most 200 dp of each screen edge from the Back gesture for an app, so the
 * old index, a touch strip along the whole edge, still fired Back outside that. The rail is short (at most 180 dp,
 * plus a little margin: inside the 200 dp), floats a little in from the edge, low where the thumb is, and its whole
 * touch area is excluded from the gesture. It shows a few letters with dots between; while the finger is on it, a
 * magnifier beside it turns like a wheel through the letters, the one under the finger large and in the accent, its
 * neighbours bending away in a wave. A tick for each letter; the list glides as with the string.
 */
@Composable
private fun RailIndex(
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
    val n = entries.size
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val touch = remember { IndexTouch() }
    val pop = highlight.pop
    val color = if (glass.lightGlass(Surfaces.Drawer, lightBackground)) Color(0xFF16181C) else Color.White
    val accent = HomeTheme.accent
    val right = config.side != "left"
    SideEffect { highlight.enabled = config.focus }
    GlideList(state, index, touch, config.followMs)

    // Where the list is, as an entry: the rail's marker glides between letters as the list scrolls.
    val marker = remember { Animatable(0f) }
    LaunchedEffect(index) {
        androidx.compose.runtime.snapshotFlow {
            if (touch.touching) touch.lens else index.entryForRow(currentRow(state, index)).toFloat()
        }.collectLatest { target ->
            if (touch.touching) marker.snapTo(target) else marker.animateTo(target, spring(dampingRatio = 0.82f, stiffness = 380f))
        }
    }
    val busy by remember(state) { derivedStateOf { touch.touching || state.isScrollInProgress } }
    val glow by androidx.compose.animation.core.animateFloatAsState(if (busy) 1f else 0f, tween(220), label = "railGlow")

    val measurer = rememberTextMeasurer(cacheSize = 96)
    val small = remember(entries, color, HomeTheme.font) {
        val style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        entries.map { measurer.measure(glyph(it.label, it.section), style) }
    }
    val big = remember(entries, color, HomeTheme.font) {
        val style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        entries.map { measurer.measure(glyph(it.label, it.section), style) }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val height = constraints.maxHeight.toFloat()
        val width = constraints.maxWidth.toFloat()
        val railH = minOf(with(density) { config.railHeightDp.dp.toPx() }, height * 0.8f)
        val railW = with(density) { RailWidth.toPx() }
        val inset = with(density) { config.railInsetDp.dp.toPx() }
        val railBottom = (height - height * config.railLift).coerceIn(railH, height)
        val railTop = railBottom - railH
        val railLeft = if (right) width - inset - railW else inset
        val pad = with(density) { 10.dp.toPx() }
        val step = (railH - 2f * pad) / n
        /** Height (in the box) of entry position [p] (continuous). */
        fun yOf(p: Float): Float = railTop + pad + ((if (reverse) (n - 1) - p else p) + 0.5f) * step
        fun positionAt(y: Float): Float {
            val slot = (y - railTop - pad) / step - 0.5f
            return (if (reverse) (n - 1) - slot else slot).coerceIn(0f, (n - 1).toFloat())
        }
        // A few letters on the rail, dots between them.
        val shownCount = maxOf(2, (railH / with(density) { 17.dp.toPx() }).toInt())
        val picks = remember(n, shownCount) {
            if (n <= shownCount) (0 until n).toList() else (0 until shownCount).map { ((it * (n - 1f)) / (shownCount - 1)).roundToInt() }.distinct()
        }
        fun focusOn(i: Int) {
            highlight.from = entries.getOrNull(i)?.firstItem ?: -1
            highlight.to = entries.getOrNull(i + 1)?.firstItem ?: Int.MAX_VALUE
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
            touch.fingerY = y
            val position = positionAt(y)
            touch.lens = position
            touch.target = index.rowAt(position)
            val entry = position.roundToInt().coerceIn(0, n - 1)
            if (entry != touch.active) {
                touch.active = entry
                focusOn(entry)
                tick()
            }
        }

        if (controller.indexDemo) {
            // Settings preview: a pretend thumb runs up and down the rail.
            LaunchedEffect(index, railTop, railH) {
                touch.touching = true
                touch.active = -1
                pop.snapTo(1f)
                var t = 0f
                while (isActive) {
                    withFrameNanos { }
                    t += 0.004f
                    val swing = (1f - cos(t * 2f * PI.toFloat())) / 2f
                    follow(railTop + pad + swing * (railH - 2f * pad))
                }
            }
        }

        // ---- the rail ----
        val shape = RoundedCornerShape(50)
        Box(
            Modifier
                .offset { IntOffset(railLeft.roundToInt(), railTop.roundToInt()) }
                .size(with(density) { railW.toDp() }, with(density) { railH.toDp() })
                .graphicsLayer {
                    // Comes in from its edge with the drawer, brighter while in use.
                    val d = overlayFade(controller)
                    alpha = d * (0.6f + 0.4f * glow)
                    translationX = (1f - d) * 24.dp.toPx() * (if (right) 1f else -1f)
                }
                .liquidGlass(backdrop, shape, glass.style(Surfaces.Drawer, lightBackground))
                .clip(shape)
                .drawBehind {
                    // The marker: a liquid drop of the accent where the list is (or the finger).
                    val my = yOf(marker.value) - railTop
                    val mh = maxOf(step * 1.8f, 18.dp.toPx())
                    drawRoundRect(
                        accent.copy(alpha = 0.55f + 0.35f * glow),
                        topLeft = Offset(3.dp.toPx(), my - mh / 2f),
                        size = androidx.compose.ui.geometry.Size(size.width - 6.dp.toPx(), mh),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2f, size.width / 2f),
                    )
                    for ((k, i) in picks.withIndex()) {
                        val layout = small[i]
                        val y = yOf(i.toFloat()) - railTop
                        // A letter sitting on the drop is drawn in the drop's readable colour.
                        val on = abs(y - my) < mh * 0.4f
                        drawText(
                            layout,
                            color = if (on) readableOn(accent) else color,
                            topLeft = Offset(size.width / 2f - layout.size.width / 2f, y - layout.size.height / 2f),
                            alpha = if (on) 1f else 0.85f,
                        )
                        // A dot halfway to the next letter shown, for the letters in between.
                        picks.getOrNull(k + 1)?.let { next ->
                            if (next - i > 1) drawCircle(color.copy(alpha = 0.5f), 1.4.dp.toPx(), Offset(size.width / 2f, (y + yOf(next.toFloat()) - railTop) / 2f))
                        }
                    }
                },
        )

        // ---- the magnifier: a wheel of letters beside the rail while the finger is on it ----
        val lensW = with(density) { LensWidth.toPx() }
        val lensStep = with(density) { LensStep.toPx() }
        val lensH = lensStep * 5f
        val gap = with(density) { 12.dp.toPx() }
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(lensW.roundToInt(), lensH.roundToInt()))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val cy = touch.fingerY.coerceIn(lensH / 2f, (height - lensH / 2f).coerceAtLeast(lensH / 2f))
                        val x = if (right) railLeft - gap - lensW else railLeft + railW + gap
                        placeable.place(x.roundToInt(), (cy - lensH / 2f).roundToInt())
                    }
                }
                .graphicsLayer {
                    val p = pop.value
                    alpha = p.coerceIn(0f, 1f) * overlayFade(controller)
                    scaleX = 0.7f + 0.3f * p
                    scaleY = 0.45f + 0.55f * p
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (right) 1f else 0f, 0.5f)
                }
                .liquidGlass(backdrop, RoundedCornerShape(28.dp), glass.style(Surfaces.Drawer, lightBackground))
                .clip(RoundedCornerShape(28.dp))
                .drawBehind {
                    if (pop.value <= 0.01f) return@drawBehind
                    val lens = touch.lens
                    val centre = size.height / 2f
                    val from = floor(lens - 3f).toInt().coerceAtLeast(0)
                    val to = ceil(lens + 3f).toInt().coerceAtMost(n - 1)
                    for (j in from..to) {
                        val d = j - lens
                        val a = (1f - abs(d) / 2.7f).coerceIn(0f, 1f)
                        if (a <= 0f) continue
                        val near = (1f - abs(d)).coerceIn(0f, 1f)
                        val s = 0.62f + 0.38f * near
                        // The wave: letters leaving the middle bend away from the rail.
                        val bend = (1f - cos(minOf(abs(d), 2.6f) * 0.85f)) * 16.dp.toPx() * (if (right) -1f else 1f)
                        val layout = big[j]
                        val cx = size.width / 2f + bend
                        val cy = centre + (if (reverse) -d else d) * lensStep
                        scale(s, Offset(cx, cy)) {
                            drawText(
                                layout,
                                color = if (abs(d) < 0.5f) accent else color,
                                topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f),
                                alpha = a,
                            )
                        }
                    }
                },
        )

        // ---- touch: the rail and a margin around it, all inside the gesture exclusion ----
        if (interactive) {
            val grab = with(density) { 18.dp.toPx() }
            val boxLeft = if (right) railLeft - grab else 0f
            val boxRight = if (right) width else railLeft + railW + grab
            val margin = with(density) { 8.dp.toPx() }
            val boxTop = (railTop - margin).coerceAtLeast(0f)
            val boxH = railH + 2f * margin
            Box(
                Modifier
                    .offset { IntOffset(boxLeft.roundToInt(), boxTop.roundToInt()) }
                    .size(with(density) { (boxRight - boxLeft).toDp() }, with(density) { boxH.toDp() })
                    // At most 196 dp tall: all of it is kept from the Back gesture.
                    .systemGestureExclusion()
                    .pointerInput(index, reverse, railTop, railH, right) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            touch.active = -1
                            touch.touching = true
                            follow(boxTop + down.position.y)
                            scope.launch { pop.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 700f)) }
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                                follow(boxTop + change.position.y)
                            }
                            touch.touching = false
                            scope.launch { pop.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 500f)) }
                        }
                    },
            )
        }
    }
}
