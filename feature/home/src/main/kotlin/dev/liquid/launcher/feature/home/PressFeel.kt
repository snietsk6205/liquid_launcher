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
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How things react to a finger (0.7.2, D108): a press squashes them like a drop of liquid and
 * letting go springs them back past their size before they settle, so every icon, pill item,
 * result and button answers the finger the same way.
 *
 * Two springs drive the width and the height with different stiffness, so on release the two
 * wobble slightly out of step: the "jelly" look. Large things (widgets, rows) squash by a
 * fixed number of pixels at most, so a full-width card dips gently while an icon squishes.
 */
object PressStyle {
    /** "off", "gentle", "squishy" (default) or "bouncy". */
    @Volatile
    var style = "squishy"

    /** 0..1: how deep a press goes (Settings → Motion, "Press depth"). */
    @Volatile
    var depth = 0.5f

    internal val on: Boolean get() = style != "off"

    /** Fraction an icon-sized thing shrinks by when fully pressed. */
    internal fun shrink(): Float = when (style) {
        "gentle" -> 0.05f
        "bouncy" -> 0.13f
        else -> 0.1f
    } * (0.4f + 1.2f * depth)

    /** Width squashes less than height: the drop flattens a little under the finger. */
    internal fun widthShare(): Float = when (style) {
        "gentle" -> 1f
        "bouncy" -> 0.6f
        else -> 0.72f
    }

    internal fun heightShare(): Float = when (style) {
        "gentle" -> 1f
        "bouncy" -> 1.2f
        else -> 1.12f
    }

    /** Damping of the way back: 1 settles without overshoot. */
    internal fun releaseDamping(): Float = when (style) {
        "gentle" -> 0.9f
        "bouncy" -> 0.3f
        else -> 0.42f
    }
}

/**
 * The squash of one element: 0 at rest, 1 fully pressed; while springing back it overshoots
 * below 0, which draws the element slightly larger than its size.
 */
@Stable
class PressFeel internal constructor(private val scope: CoroutineScope) {
    internal val x = Animatable(0f)
    internal val y = Animatable(0f)
    private var pending: Job? = null

    /** The finger is down. [delayMs]: wait this long first (inside scrolling lists, so a scroll never squishes). */
    fun press(delayMs: Long = 0L) {
        if (!PressStyle.on) return
        pending?.cancel()
        pending = scope.launch {
            if (delayMs > 0) delay(delayMs)
            val spec = spring(dampingRatio = 0.8f, stiffness = MotionScale.stiffness(1500f), visibilityThreshold = 0.002f)
            launch { x.animateTo(1f, spec) }
            launch { y.animateTo(1f, spec) }
        }
    }

    /**
     * The finger let go on it (a tap): spring back with a bounce. A tap too quick for the squash
     * to show still gets one, from a push downwards before the spring brings it back.
     */
    fun release() {
        if (!PressStyle.on) return
        pending?.cancel()
        pending = null
        scope.launch {
            val damping = PressStyle.releaseDamping()
            // A push that dips the element when it had not gone down yet.
            val push = if (y.value < 0.5f) 9f else 0f
            launch { y.animateTo(0f, spring(dampingRatio = damping, stiffness = MotionScale.stiffness(520f), visibilityThreshold = 0.002f), initialVelocity = push) }
            launch { x.animateTo(0f, spring(dampingRatio = damping, stiffness = MotionScale.stiffness(390f), visibilityThreshold = 0.002f), initialVelocity = push * 0.75f) }
        }
    }

    /** The touch went elsewhere (a scroll, a swipe): back to rest without a bounce. */
    fun cancel() {
        pending?.cancel()
        pending = null
        if (x.value == 0f && y.value == 0f) return
        scope.launch {
            val spec = spring<Float>(dampingRatio = 1f, stiffness = MotionScale.stiffness(900f), visibilityThreshold = 0.002f)
            launch { x.animateTo(0f, spec) }
            launch { y.animateTo(0f, spec) }
        }
    }

    /** Applies the squash to a layer of [GraphicsLayerScope.size]; pivot at the centre. */
    internal fun GraphicsLayerScope.applySquash() {
        val px = x.value
        val py = y.value
        if (px == 0f && py == 0f) return
        val shrink = PressStyle.shrink()
        // At most this many pixels off the shorter side, so big things dip gently.
        val cap = 9.dp.toPx() / size.minDimension.coerceAtLeast(1f)
        val d = minOf(shrink, cap * (shrink / 0.1f))
        scaleX *= 1f - px * d * PressStyle.widthShare()
        scaleY *= 1f - py * d * PressStyle.heightShare()
    }
}

@Composable
fun rememberPressFeel(): PressFeel {
    val scope = rememberCoroutineScope()
    return remember(scope) { PressFeel(scope) }
}

/** Draws [feel]'s squash. Put it after the element's pointer input, before what it draws. */
fun Modifier.pressSquash(feel: PressFeel): Modifier = graphicsLayer { with(feel) { applySquash() } }

/** Squashes with the presses of [interaction] (a clickable's interaction source). */
@Composable
fun Modifier.squishOn(interaction: InteractionSource, delayMs: Long = 0L): Modifier {
    val feel = rememberPressFeel()
    LaunchedEffect(interaction) {
        interaction.interactions.collect { event: Interaction ->
            when (event) {
                is PressInteraction.Press -> feel.press(delayMs)
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
