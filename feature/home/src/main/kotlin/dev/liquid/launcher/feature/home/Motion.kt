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

    /** Leaving: quick and without bounce, so it never delays what comes next. */
    fun exit(): FiniteAnimationSpec<Float> = tween(MotionScale.ms(150))
}

/** 1 while an overlay is shown, falling to 0 while it leaves (see [Presence]). */
internal val LocalPresence = compositionLocalOf<State<Float>> { mutableFloatStateOf(1f) }

/**
 * Shows [content] while [value] is not null and keeps it on screen, with the last value, for
 * the moment it takes to fade and sink away after [value] became null. The overlays keep their
 * own entrance; this gives all of them the same exit.
 */
@Composable
internal fun <T : Any> Presence(value: T?, content: @Composable (T) -> Unit) {
    val holder = remember { arrayOfNulls<Any>(1) }
    if (value != null) holder[0] = value
    val out = remember { Animatable(if (value != null) 1f else 0f) }
    var present by remember { mutableStateOf(value != null) }
    LaunchedEffect(value != null) {
        if (value != null) {
            present = true
            out.snapTo(1f)
        } else if (present) {
            out.animateTo(0f, Motion.exit())
            present = false
            holder[0] = null
        }
    }
    @Suppress("UNCHECKED_CAST")
    val shown = value ?: (if (present) holder[0] as T? else null) ?: return
    CompositionLocalProvider(LocalPresence provides out.asState()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = out.value.coerceIn(0f, 1f)
                    alpha = p
                    val scale = 0.97f + 0.03f * p
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            content(shown)
        }
    }
}
