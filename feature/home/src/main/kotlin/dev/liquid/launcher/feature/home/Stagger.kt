package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp

/*
 * 0.15.0 (D188): a short list arriving one entry after the other, after WM Keyboard's settings ("StaggerIn",
 * app/SettingsMotion.kt, MIT licence, © 2026 Wasi Master): each entry fades in and rises 6 dp into place, a beat after
 * the one before it, and past eight entries the rest come together. Used where a screen opens under the finger (search's
 * page): composing the entries over a few frames instead of all on the first one is also what keeps that first frame
 * quick.
 */

/** How long one entry takes to settle in. */
private const val StaggerInMs = 220

/** The beat between one entry starting and the next. */
private const val StaggerStepMs = 35

/** Past this many entries the rest start together. */
private const val StaggerMaxSteps = 8

/** How far below its place an entry starts. */
private val StaggerRise = 6.dp

/**
 * How many of [total] entries to compose now: one more each frame from the first, so a page that opens under the finger
 * spreads its composition over its first frames. Starts again when this leaves the composition.
 */
@Composable
internal fun rememberRevealCount(total: Int): Int {
    var shown by remember { mutableIntStateOf(1) }
    LaunchedEffect(total) {
        while (shown < total) {
            androidx.compose.runtime.withFrameNanos { }
            shown++
        }
    }
    return shown.coerceAtMost(total)
}

/**
 * One entry arriving: fades in and rises into place, [index] beats after the first. Plays once, the first time it is
 * composed; read in the draw phase, so it costs a redraw a frame and no recomposition. Follows the launcher's animation
 * speed.
 */
@Composable
internal fun StaggerIn(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        shown.animateTo(
            1f,
            tween(
                durationMillis = MotionScale.ms(StaggerInMs),
                delayMillis = MotionScale.ms(StaggerStepMs * index.coerceIn(0, StaggerMaxSteps)),
                easing = FastOutSlowInEasing,
            ),
        )
    }
    Box(
        modifier.graphicsLayer {
            val t = shown.value
            alpha = t
            translationY = (1f - t) * StaggerRise.toPx()
        },
    ) { content() }
}

/** [gap] above this entry while it shows something; nothing at all while it is empty (as if it were not there). */
internal fun Modifier.gapAboveWhenShown(gap: androidx.compose.ui.unit.Dp): Modifier =
    this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val above = if (placeable.height > 0) gap.roundToPx() else 0
            layout(placeable.width, placeable.height + above) { placeable.place(0, above) }
        },
    )
