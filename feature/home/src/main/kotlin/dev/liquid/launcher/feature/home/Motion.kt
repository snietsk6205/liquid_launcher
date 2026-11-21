package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.graphics.layer.drawLayer
import kotlinx.coroutines.launch

/**
 * The launcher's motion in one place. Every sheet, menu, folder and label takes its spring
 * from here, so they all move alike and one setting ("Feel") changes them together. Up to
 * 0.5.0 each surface had its own numbers (damping 0.72 to 0.85, stiffness 360 to 700) and
 * most of them appeared with a spring but vanished in a single frame.
 */
object Motion {
    /** "calm" (no overshoot), "liquid" (a soft overshoot) or "lively" (a clear bounce). */
    @Volatile
    var style = "liquid"

    private fun damping(): Float = when (style) {
        "calm" -> 1f
        "lively" -> 0.6f
        else -> 0.78f
    }

    private fun stiffness(): Float = when (style) {
        "calm" -> 440f
        "lively" -> 640f
        else -> 540f
    }

    /** Something large arriving: a sheet, a menu, a folder opening. */
    fun enter(): SpringSpec<Float> = spring(dampingRatio = damping(), stiffness = MotionScale.stiffness(stiffness()))

    /** Something small popping in: a notice, a bubble, a chip. Quicker, a touch more bounce. */
    fun pop(): SpringSpec<Float> = spring(dampingRatio = (damping() - 0.08f).coerceAtLeast(0.5f), stiffness = MotionScale.stiffness(stiffness() * 1.35f))

    /**
     * Leaving: quick and without bounce, so it never delays what comes next. 0.11.2: 110 ms and a fade only (Med: "now
     * almost all the animations lag"): 0.11.1's sheets sank back as they faded, which drew their glass anew every frame.
     */
    fun exit(): FiniteAnimationSpec<Float> = tween(MotionScale.ms(110))

    /** 0.13.0 (D176): an overlay leaving as a picture of itself: no bounce, about a quarter of a second. */
    fun leave(): SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = MotionScale.stiffness(420f), visibilityThreshold = 0.005f)
}

/** 1 while an overlay is shown, falling to 0 while it leaves (see [Presence]). */
internal val LocalPresence = compositionLocalOf<State<Float>> { mutableFloatStateOf(1f) }

/**
 * Shows [content] while [value] is not null and keeps it on screen, with the last value, while it leaves after [value]
 * became null. The overlays keep their own entrance; this gives all of them the same exit. [instantExit], asked when
 * [value] goes, skips the exit (a sheet that leaves inside a picture of the old screen).
 *
 * 0.13.0 (D176, Med: "smoother transitions, popups and closing animations"): an overlay leaves as a picture of itself,
 * glass included, drawn once by the GPU ([rememberGraphicsLayer], `toImageBitmap`): it shrinks back into what opened it
 * ([origin]), or sinks, on a spring without bounce, so nothing live is redrawn while it goes (0.11.2's 110 ms fade was
 * there only because moving live glass dropped frames). With [onClose] it is a sheet: it follows a finger dragging it
 * down (from anywhere, or from the top of its own list) and closes past a quarter of the screen or on a flick, and
 * Android's predictive Back gesture shrinks it towards the edge as the thumb moves and closes it on release.
 */
@Composable
internal fun <T : Any> Presence(
    value: T?,
    instantExit: () -> Boolean = { false },
    origin: ((T) -> androidx.compose.ui.geometry.Rect?)? = null,
    onClose: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    val holder = remember { arrayOfNulls<Any>(1) }
    if (value != null) holder[0] = value
    val out = remember { Animatable(if (value != null) 1f else 0f) }
    var present by remember { mutableStateOf(value != null) }
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    var shot by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val exit = remember { ExitState() }
    // A sheet's own hand: the finger's drag (px down) and the back gesture's progress (0..1, from which edge).
    val drag = remember { Animatable(0f) }
    val back = remember { mutableFloatStateOf(0f) }
    val backEdge = remember { mutableFloatStateOf(1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(value != null) {
        if (value != null) {
            shot = null
            present = true
            drag.snapTo(0f)
            back.floatValue = 0f
            out.snapTo(1f)
        } else if (present) {
            @Suppress("UNCHECKED_CAST")
            val last = holder[0] as T?
            if (instantExit()) {
                out.snapTo(0f)
            } else {
                val picture = runCatching { layer.toImageBitmap() }.getOrNull()
                if (picture != null) {
                    exit.target = last?.let { origin?.invoke(it) }
                    exit.fromDrag = drag.value
                    exit.fromBack = back.floatValue
                    exit.edge = backEdge.floatValue
                    exit.sink = if (onClose != null) 0.12f else 0.03f
                    shot = picture
                    out.animateTo(0f, Motion.leave())
                    shot = null
                } else {
                    out.animateTo(0f, Motion.exit())
                }
            }
            present = false
            holder[0] = null
        }
    }
    shot?.let { picture ->
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawLeaving(picture, out.value, exit) }
        return
    }
    @Suppress("UNCHECKED_CAST")
    val shown = value ?: (if (present) holder[0] as T? else null) ?: return
    val sheet = onClose != null && value != null
    if (sheet) {
        androidx.activity.compose.PredictiveBackHandler(enabled = true) { events ->
            try {
                events.collect { e ->
                    back.floatValue = e.progress
                    backEdge.floatValue = if (e.swipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT) -1f else 1f
                }
                onClose?.invoke()
            } catch (c: kotlin.coroutines.cancellation.CancellationException) {
                back.floatValue = 0f
                throw c
            }
        }
    }
    val connection = remember(sheet) { if (sheet) SheetDrag(drag, scope) { onClose?.invoke() } else null }
    CompositionLocalProvider(LocalPresence provides out.asState()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (connection != null) {
                        Modifier
                            .androidxNestedScroll(connection)
                            .androidxDraggable(connection)
                    } else Modifier
                )
                .graphicsLayer {
                    // The sheet in the hand: down with the finger, smaller towards the edge with the back gesture.
                    val b = back.floatValue
                    translationY = drag.value
                    val s = 1f - 0.1f * b
                    scaleX = s
                    scaleY = s
                    translationX = backEdge.floatValue * b * size.width * 0.06f
                }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
        ) {
            content(shown)
        }
    }
}

/** Where a leaving overlay goes, and where the hand had it when it let go. */
private class ExitState {
    var target: androidx.compose.ui.geometry.Rect? = null
    var fromDrag = 0f
    var fromBack = 0f
    var edge = 1f

    /** How far a picture with no origin sinks (a share of the screen): a sheet goes, a small overlay settles. */
    var sink = 0.12f
}

/** The picture of a leaving overlay at [t] (1: where it was, 0: gone): into its origin, or sinking. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLeaving(
    picture: androidx.compose.ui.graphics.ImageBitmap,
    t: Float,
    exit: ExitState,
) {
    val k = (1f - t).coerceIn(0f, 1f)
    val startScale = 1f - 0.1f * exit.fromBack
    val startX = exit.edge * exit.fromBack * size.width * 0.06f
    val target = exit.target
    val scale: Float
    val dx: Float
    val dy: Float
    if (target != null && size.width > 0f) {
        // Back into what opened it: the whole picture maps onto the origin's box.
        val endScale = (target.width / size.width).coerceIn(0.04f, 1f)
        scale = startScale + (endScale - startScale) * k
        dx = startX + (target.center.x - size.width / 2f - startX) * k
        dy = exit.fromDrag + (target.center.y - size.height / 2f - exit.fromDrag) * k
    } else {
        // A sheet sinks away, from wherever the finger left it.
        scale = startScale + (0.94f - startScale) * k
        dx = startX * (1f - k)
        dy = exit.fromDrag + (size.height * exit.sink) * k
    }
    val alpha = (t / 0.7f).coerceIn(0f, 1f)
    withTransform({
        translate(dx, dy)
        scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f))
    }) {
        drawImage(picture, alpha = alpha)
    }
}

/**
 * A sheet's drag: down from anywhere, or from the top of its own list (nested scrolling); back up before its list
 * scrolls. Released past a quarter of the screen, or flicked down, it closes; else it springs back.
 */
private class SheetDrag(
    val drag: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    val scope: kotlinx.coroutines.CoroutineScope,
    val close: () -> Unit,
) : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    var height = 2000f

    fun by(dy: Float): Float {
        val before = drag.value
        val next = (before + dy).coerceAtLeast(0f)
        scope.launch { drag.snapTo(next) }
        return next - before
    }

    fun release(velocity: Float) {
        if (drag.value > height * 0.25f || (velocity > 1600f && drag.value > 0f)) {
            close()
        } else {
            scope.launch { drag.animateTo(0f, spring(dampingRatio = 1f, stiffness = MotionScale.stiffness(500f)), initialVelocity = velocity) }
        }
    }

    override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset =
        // Moving back up: the sheet goes up first, then its list scrolls.
        if (available.y < 0f && drag.value > 0f) androidx.compose.ui.geometry.Offset(0f, by(available.y)) else androidx.compose.ui.geometry.Offset.Zero

    override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset =
        // The list is at its top and the finger keeps pulling down: the sheet follows.
        if (available.y > 0f && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) androidx.compose.ui.geometry.Offset(0f, by(available.y)) else androidx.compose.ui.geometry.Offset.Zero

    override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
        if (drag.value <= 0f) return androidx.compose.ui.unit.Velocity.Zero
        release(available.y)
        return available
    }
}

private fun Modifier.androidxNestedScroll(c: SheetDrag): Modifier =
    this.nestedScroll(c).onSizeChanged { c.height = it.height.toFloat().coerceAtLeast(1f) }

private fun Modifier.androidxDraggable(c: SheetDrag): Modifier = this.draggable(
    state = androidx.compose.foundation.gestures.DraggableState { c.by(it) },
    orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
    onDragStopped = { velocity: Float -> c.release(velocity) },
)
