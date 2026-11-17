package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How things react to a finger (0.7.2, D108; motions and light 0.8.0, D112): a press moves them
 * (a squash like a drop of liquid by default) and letting go springs them back past their size
 * before they settle, so every icon, pill item, result and button answers the finger the same way.
 *
 * Two springs drive the width and the height with different stiffness, so on release the two
 * wobble slightly out of step: the "jelly" look. Large things (widgets, rows) move by a fixed
 * number of pixels at most, so a full-width card dips gently while an icon squishes.
 *
 * The motions follow wmkeyboard's key motions (Sink, Grow, Shrink, Rise, Tilt, Jelly, Pop,
 * Squish, Fade), applied to the launcher's own icons and glass.
 */
object PressStyle {
    /**
     * "squishy" (default: a crisp squash and rebound), "tilt" (tips towards the finger, with a highlight) or "off".
     * 0.9.4 (P9): the other nine motions of 0.8.0 are gone; a stored one of them falls back to "squishy".
     */
    @Volatile
    var style = "squishy"

    /** 0..1: intensity, how deep a press goes ("Press intensity"). */
    @Volatile
    var depth = 0.5f

    /** 0..1: how much the way back rings ("Wobble"); 0.5 is the 0.7.2 feel. */
    @Volatile
    var wobble = 0.5f

    /** 0..1: the light from the finger on a pressed thing. */
    @Volatile
    var light = 0.35f

    /** 0.5..2: how quickly a press goes down and comes back ("Press speed"). */
    @Volatile
    var speed = 1f

    internal val on: Boolean get() = style != "off"

    /** Fraction an icon-sized thing changes size by when fully pressed. */
    internal fun shrink(): Float = (if (style == "tilt") 0.06f else 0.1f) * (0.4f + 1.2f * depth)

    /** Width squashes less than height: the drop flattens a little under the finger. */
    internal fun widthShare(): Float = 0.72f

    internal fun heightShare(): Float = 1.12f

    /** Damping of the way back: 1 settles without overshoot. [wobble] 0.5 gives each style's own. */
    internal fun releaseDamping(): Float {
        val own = if (style == "tilt") 0.55f else 0.42f
        return (1f - (1f - own) * (wobble / 0.5f)).coerceIn(0.15f, 1f)
    }

    /** A stiffness scaled by the launcher's animation speed and the press speed. */
    internal fun stiffness(base: Float): Float = MotionScale.stiffness(base) * speed * speed
}

/**
 * Coming back from an app (0.9.4, P9): the icon it was opened from lands, a little larger, and settles,
 * at the moment the home screen settles in. [key] is the app's key; [tick] changes on each return.
 */
object IconLanding {
    @Volatile
    var key: String? = null
    val tick = androidx.compose.runtime.mutableIntStateOf(0)

    fun land(appKey: String) {
        key = appKey
        tick.intValue++
    }
}

/** The edit-mode wobble's clock in seconds (0.9.4): one for every icon, read in layers only. */
object EditWobble {
    val time = androidx.compose.runtime.mutableFloatStateOf(0f)

    /** Degrees of the wobble; 0 is off. Set from the settings. */
    @Volatile
    var degrees = 0f
}

/**
 * The press of one element: 0 at rest, 1 fully pressed; while springing back it overshoots
 * below 0, which moves the element slightly past its rest.
 */
@Stable
class PressFeel internal constructor(private val scope: CoroutineScope, private val fx: TouchFx?) {
    internal val x = Animatable(0f)
    internal val y = Animatable(0f)
    private var pending: Job? = null

    /** Where the finger landed in the element, in its own pixels (Unspecified: the centre). */
    internal var at: Offset = Offset.Unspecified
        private set

    /**
     * The finger is down. [delayMs]: wait this long first (inside scrolling lists, so a scroll
     * never presses). [position]: where in the element, for Tilt and the light. When the press
     * shows, the touch effect is thrown where the finger is (D111).
     */
    fun press(delayMs: Long = 0L, position: Offset = Offset.Unspecified) {
        at = position
        thrown = false
        pending?.cancel()
        pending = scope.launch {
            if (delayMs > 0) delay(delayMs)
            thrown = true
            fx?.fromPress()
            if (!PressStyle.on) return@launch
            // Crisp: down fast and without a bounce.
            val spec = spring(dampingRatio = 0.86f, stiffness = PressStyle.stiffness(1800f), visibilityThreshold = 0.002f)
            launch { x.animateTo(1f, spec) }
            launch { y.animateTo(1f, spec) }
        }
    }

    /** Whether this press already threw its touch effect. */
    private var thrown = false

    /**
     * The finger let go on it (a tap): spring back with a bounce. A tap too quick for the press
     * to show still gets one, from a push before the spring brings it back, and its effect.
     */
    fun release() {
        pending?.cancel()
        pending = null
        if (!thrown) {
            thrown = true
            fx?.fromPress()
        }
        if (!PressStyle.on) return
        scope.launch {
            val damping = PressStyle.releaseDamping()
            // A push that moves the element when it had not gone down yet.
            val push = if (y.value < 0.5f) 9f else 0f
            // Width and height a little out of step: a rebound, not a jelly.
            launch { y.animateTo(0f, spring(dampingRatio = damping, stiffness = PressStyle.stiffness(620f), visibilityThreshold = 0.002f), initialVelocity = push) }
            launch { x.animateTo(0f, spring(dampingRatio = damping, stiffness = PressStyle.stiffness(540f), visibilityThreshold = 0.002f), initialVelocity = push * 0.85f) }
        }
    }

    /** Back from the app this element opened (0.9.4): it lands a little larger and settles with the rebound. */
    fun land() {
        if (!PressStyle.on) return
        pending?.cancel()
        pending = null
        at = Offset.Unspecified
        scope.launch {
            x.snapTo(-1.3f)
            y.snapTo(-1.5f)
            val damping = PressStyle.releaseDamping()
            launch { y.animateTo(0f, spring(dampingRatio = damping, stiffness = PressStyle.stiffness(360f), visibilityThreshold = 0.002f)) }
            launch { x.animateTo(0f, spring(dampingRatio = damping, stiffness = PressStyle.stiffness(300f), visibilityThreshold = 0.002f)) }
        }
    }

    /** The touch went elsewhere (a scroll, a swipe): back to rest without a bounce. */
    fun cancel() {
        pending?.cancel()
        pending = null
        if (x.value == 0f && y.value == 0f) return
        scope.launch {
            val spec = spring<Float>(dampingRatio = 1f, stiffness = PressStyle.stiffness(900f), visibilityThreshold = 0.002f)
            launch { x.animateTo(0f, spec) }
            launch { y.animateTo(0f, spec) }
        }
    }

    /** Applies the press to a layer of [GraphicsLayerScope.size]; pivot at the centre. */
    internal fun GraphicsLayerScope.applySquash() {
        val px = x.value
        val py = y.value
        if (px == 0f && py == 0f) return
        val shrink = PressStyle.shrink()
        val minSide = size.minDimension.coerceAtLeast(1f)
        // At most this many pixels off the shorter side, so big things move gently.
        val cap = 9.dp.toPx() / minSide
        val d = minOf(shrink, cap * (shrink / 0.1f))
        // Moves in pixels shrink for big things the same way.
        val big = (64.dp.toPx() / minSide).coerceAtMost(1f)
        val depth = 0.4f + 1.2f * PressStyle.depth
        if (PressStyle.style == "tilt") {
            val p = at
            val tx = if (p.isSpecified) ((p.x / size.width.coerceAtLeast(1f)) * 2f - 1f).coerceIn(-1f, 1f) else 0f
            val ty = if (p.isSpecified) ((p.y / size.height.coerceAtLeast(1f)) * 2f - 1f).coerceIn(-1f, 1f) else 0f
            // Subtle: at most about 9 degrees on an icon, less on big things.
            val deg = 9f * depth * big
            // The side pressed goes down, as a plate on a pivot would.
            rotationY = rotationY - tx * py * deg
            rotationX = rotationX + ty * px * deg
            cameraDistance = maxOf(12f * density, size.maxDimension * 4f)
            val s = 1f - py * d * 0.6f
            scaleX *= s
            scaleY *= s
        } else {
            scaleX *= 1f - px * d * PressStyle.widthShare()
            scaleY *= 1f - py * d * PressStyle.heightShare()
        }
    }

    /** The light from the finger, drawn over the element while it is pressed (D112). */
    internal fun DrawScope.drawLight() {
        val light = if (PressStyle.style == "tilt") maxOf(PressStyle.light, 0.3f) else PressStyle.light
        if (light <= 0.01f || !PressStyle.on) return
        val p = y.value
        if (p <= 0.01f) return
        val centre = if (at.isSpecified) at else Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.75f
        if (r <= 1f) return
        val a = (light * 0.55f * p).coerceIn(0f, 0.6f)
        drawCircle(
            Brush.radialGradient(
                0f to Color.White.copy(alpha = a),
                0.45f to Color.White.copy(alpha = a * 0.4f),
                1f to Color.Transparent,
                center = centre, radius = r,
            ),
            radius = r, center = centre, blendMode = BlendMode.Plus,
        )
    }
}

@Composable
fun rememberPressFeel(): PressFeel {
    val scope = rememberCoroutineScope()
    val fx = LocalTouchFx.current
    return remember(scope, fx) { PressFeel(scope, fx) }
}

/** Draws [feel]'s press and the light from the finger. Put it after the element's pointer input, before what it draws. */
fun Modifier.pressSquash(feel: PressFeel): Modifier =
    graphicsLayer { with(feel) { applySquash() } }
        .drawWithContent {
            drawContent()
            with(feel) { drawLight() }
        }

/** Squashes with the presses of [interaction] (a clickable's interaction source). */
@Composable
fun Modifier.squishOn(interaction: InteractionSource, delayMs: Long = 0L): Modifier {
    val feel = rememberPressFeel()
    LaunchedEffect(interaction) {
        interaction.interactions.collect { event: Interaction ->
            when (event) {
                is PressInteraction.Press -> feel.press(delayMs, event.pressPosition)
                is PressInteraction.Release -> feel.release()
                is PressInteraction.Cancel -> feel.cancel()
            }
        }
    }
    return pressSquash(feel)
}

/**
 * A clickable that squishes (no ripple: the element itself answers). Modifiers after it (its
 * background, clip, padding, content) squash with it. [delayMs]: inside a scrolling list, wait
 * briefly before squashing so a scroll does not.
 */
@Composable
fun Modifier.squishClickable(
    enabled: Boolean = true,
    delayMs: Long = 0L,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    // The touch is read outside the squash, so a finger near the edge stays on the element while it shrinks.
    return this
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
        .squishOn(interaction, delayMs)
}
