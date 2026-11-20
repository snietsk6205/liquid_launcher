package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.common.DialMath
import dev.liquid.launcher.core.common.WallpaperPalette
import dev.liquid.launcher.core.common.WallpaperTones
import dev.liquid.launcher.core.data.GridIndex
import dev.liquid.launcher.core.settings.LauncherSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * What the letter circle tells the letter index (0.11.0): while [active], the string at the edge shows and bends to
 * [position], as it would under a finger, and the list follows (see ScrollIndex).
 */
@Stable
internal class IndexDrive {
    /** The circle is being turned. */
    var active by mutableStateOf(false)

    /** Where it has turned to, in letters: 0 is the first; fractions lie between two letters. */
    var position by mutableFloatStateOf(0f)

    /** How far the rings have turned, in degrees. They stay turned, like a real knob. */
    var spin by mutableFloatStateOf(0f)
}

/** One arc of a ring: where it starts and how far it goes (degrees, clockwise from three o'clock) and its shade. */
private class DialArc(val start: Float, val sweep: Float, val shade: Int)

/** A ring: radius and thickness as parts of the circle's radius, how fast it turns with the thumb, its arcs. */
private class DialRing(val radius: Float, val width: Float, val turn: Float, val arcs: List<DialArc>)

/**
 * Med's picture, simpler: three rings of flat-ended arcs in a dark, a middle and a light shade of one colour, with
 * gaps. The rings turn with the thumb at their own pace (the middle one the other way), so it feels like a dial.
 */
private val DialRings = listOf(
    DialRing(0.86f, 0.13f, 1f, listOf(DialArc(-160f, 155f, 0), DialArc(15f, 115f, 1))),
    DialRing(0.645f, 0.12f, -0.6f, listOf(DialArc(-40f, 190f, 2), DialArc(170f, 120f, 0))),
    DialRing(0.445f, 0.11f, 1.5f, listOf(DialArc(60f, 250f, 1), DialArc(325f, 55f, 2))),
)

/** The dark, middle and light shades of [base] the rings are drawn in. A grey wallpaper still gives a little colour. */
internal fun dialShades(base: Color): List<Color> {
    val (h, s0, _) = WallpaperPalette.hsl(base.toArgb())
    val s = s0.coerceIn(0.28f, 0.78f)
    return listOf(
        Color(WallpaperPalette.fromHsl(h, s, 0.28f)),
        Color(WallpaperPalette.fromHsl(h, s, 0.44f)),
        Color(WallpaperPalette.fromHsl((h + 14f) % 360f, s * 0.85f, 0.64f)),
    )
}

/**
 * The letter circle (0.11.0): a ring floating over the drawer's apps, by default where the Calculator was in Med's
 * screenshot, in thumb reach. Sliding round it drives the letter index: the letters come out at the edge with the
 * bubble and the list follows, exactly as when the string is pulled. As a knob it turns on from wherever it is touched
 * (clockwise goes on through the alphabet); as a clock face A sits at twelve and a touch jumps to its letter. It fades
 * while the apps scroll, so it hides less of them. Holding it still lifts it; then it follows the finger to a new place.
 */
@Composable
internal fun LetterDial(
    state: LazyGridState,
    index: GridIndex,
    config: IndexConfig,
    drive: IndexDrive,
    controller: HomeController,
    interactive: Boolean,
    modifier: Modifier,
) {
    val entries = index.entries
    val n = entries.size
    if (n == 0) return
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val tone by WallpaperTones.tone.collectAsState()
    val base = when (config.dialColour) {
        "accent" -> HomeTheme.accent
        "custom" -> config.dialCustom ?: Color(0xFF3F9B6E)
        else -> tone?.roles?.vibrant?.let { Color(it) } ?: HomeTheme.accent
    }
    val shades = remember(base) { dialShades(base) }
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val font = HomeTheme.font

    // The letter in the middle while it turns, and the lift while it is held to be moved.
    val shown = remember { Animatable(0f) }
    LaunchedEffect(drive.active) { shown.animateTo(if (drive.active) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 600f)) }
    val lift = remember { Animatable(0f) }
    var moving by remember { mutableStateOf(false) }
    // Where the finger has put it while moving it, until the new place is saved (in px, its middle).
    var placed by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(config.dialX, config.dialY) { placed = null }

    // Opacity: full while it is used; faded while the apps scroll; otherwise as set. Right after use the list may
    // still be gliding to the letter: that is not "the apps scroll".
    val scrolling by remember(state) { derivedStateOf { state.isScrollInProgress } }
    var justUsed by remember { mutableStateOf(false) }
    LaunchedEffect(drive.active) {
        if (!drive.active) {
            justUsed = true
            delay(700)
            justUsed = false
        }
    }
    val fade = remember { Animatable(config.dialOpacity) }
    val using = drive.active || moving
    LaunchedEffect(using, justUsed, scrolling, config.dialOpacity, config.dialScrollOpacity) {
        val target = when {
            using -> 1f
            justUsed -> config.dialOpacity
            scrolling -> config.dialScrollOpacity
            else -> config.dialOpacity
        }
        if (target > fade.value && !using) delay(250)
        fade.animateTo(target, tween(if (target < fade.value) 160 else 420))
    }

    // However it leaves (the drawer closing under the thumb), the letters are let go.
    DisposableEffect(drive) { onDispose { drive.active = false } }

    if (controller.indexDemo) {
        // Settings preview: a pretend thumb turns it through the alphabet and back, lets go (its opacity at rest
        // shows), then the apps scroll a little (it fades as set), and again.
        LaunchedEffect(n) {
            var last = 0f
            var down = true
            try {
                while (isActive) {
                    drive.active = true
                    var t = 0f
                    while (t < 1f) {
                        androidx.compose.runtime.withFrameNanos { }
                        t += 0.004f
                        val p = (1f - cos(t * 2f * PI.toFloat())) / 2f * (n - 1)
                        drive.spin += (p - last) * 360f * config.dialTurns / n
                        last = p
                        drive.position = p
                    }
                    drive.active = false
                    delay(1600)
                    state.animateScrollBy(if (down) 700f else -700f, tween(1300))
                    down = !down
                    delay(1200)
                }
            } finally {
                drive.active = false
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val r = with(density) { config.dialSizeDp.dp.toPx() } / 2f
        val margin = with(density) { 4.dp.toPx() }
        fun clampCentre(c: Offset) = Offset(
            c.x.coerceIn(r + margin, (w - r - margin).coerceAtLeast(r + margin)),
            c.y.coerceIn(r + margin, (h - r - margin).coerceAtLeast(r + margin)),
        )
        val centre = clampCentre(placed ?: Offset(config.dialX * w, config.dialY * h))
        // Read by the gesture, which outlives a change of place.
        val centreNow = androidx.compose.runtime.rememberUpdatedState(centre)
        Box(
            Modifier
                .offset { IntOffset((centre.x - r).roundToInt(), (centre.y - r).roundToInt()) }
                .size(config.dialSizeDp.dp)
                .graphicsLayer {
                    alpha = fade.value
                    val s = 1f + 0.07f * lift.value
                    scaleX = s
                    scaleY = s
                }
                .semantics { contentDescription = "Letter circle" }
                .drawBehind {
                    val radius = size.minDimension / 2f
                    val c = center
                    val spin = drive.spin
                    val drop = 1.5.dp.toPx()
                    // A soft shadow under the arcs (two faint, slightly wider strokes a little lower), then the arcs.
                    for (pass in 0..2) {
                        for (ring in DialRings) {
                            val rr = ring.radius * radius
                            val width = ring.width * radius
                            for (arc in ring.arcs) {
                                val (colour, grow, dy) = when (pass) {
                                    0 -> Triple(Color.Black.copy(alpha = 0.10f), 3.dp.toPx(), drop * 1.4f)
                                    1 -> Triple(Color.Black.copy(alpha = 0.14f), 1.dp.toPx(), drop)
                                    else -> Triple(shades[arc.shade], 0f, 0f)
                                }
                                drawArc(
                                    colour, arc.start + spin * ring.turn, arc.sweep, useCenter = false,
                                    topLeft = Offset(c.x - rr, c.y - rr + dy), size = androidx.compose.ui.geometry.Size(rr * 2f, rr * 2f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width + grow, cap = androidx.compose.ui.graphics.StrokeCap.Butt),
                                )
                            }
                        }
                    }
                    if (config.dialStyle == "clock") {
                        // Twelve o'clock, where A is: a small mark on the outer ring.
                        drawCircle(shades[2], radius = radius * 0.05f, center = Offset(c.x, c.y - radius * 0.97f))
                    }
                    val g = shown.value
                    if (g > 0.01f) {
                        val entry = entries.getOrNull(drive.position.roundToInt().coerceIn(0, n - 1))
                        drawCircle(shades[0].copy(alpha = 0.86f * g), radius = radius * 0.36f, center = c)
                        if (entry != null) {
                            val layout = measurer.measure(
                                glyph(entry.label, entry.section),
                                TextStyle(fontFamily = font, color = shades[2], fontSize = (radius * 0.36f).toSp(), fontWeight = FontWeight.SemiBold),
                            )
                            drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f), alpha = g)
                        }
                    }
                }
                .pointerInput(n, config.dialStyle, config.dialTurns, interactive, w, h, r) {
                    if (!interactive) return@pointerInput
                    val slop = viewConfiguration.touchSlop
                    val holdMs = viewConfiguration.longPressTimeoutMillis
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val mid = Offset(size.width / 2f, size.height / 2f)
                        // Only the circle itself takes the finger (the corners of its square do nothing).
                        if ((down.position - mid).getDistance() > size.width / 2f) return@awaitEachGesture
                        down.consume()
                        val clockFace = config.dialStyle == "clock"
                        var lastAngle = DialMath.angle(down.position.x, down.position.y, mid.x, mid.y)
                        var pos = currentEntry(state, index).toFloat().coerceIn(0f, (n - 1).toFloat())
                        var turning = false
                        if (clockFace) {
                            // A clock face answers the touch at once: the letter at that spot.
                            pos = DialMath.clock(lastAngle, n)
                            drive.position = pos
                            drive.active = true
                        }
                        // A slide turns it; holding it still lifts it to be moved (on a clock face too); a tap does
                        // nothing on a knob and leaves a clock face's letter where it took the list.
                        val decided = withTimeoutOrNull(holdMs) {
                            var result = "up"
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                if ((change.position - down.position).getDistance() > slop) {
                                    result = "turn"
                                    break
                                }
                            }
                            result
                        }
                        when (decided) {
                                "turn" -> turning = true
                                null -> {
                                    // Held still: lift it and let the finger carry it.
                                    drive.active = false
                                    moving = true
                                    Haptics.confirm(view)
                                    scope.launch { lift.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 700f)) }
                                    var at = centreNow.value
                                    try {
                                        while (true) {
                                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            change.consume()
                                            at = clampCentre(at + change.positionChange())
                                            placed = at
                                        }
                                    } finally {
                                        moving = false
                                        scope.launch { lift.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
                                        val settings = controller.data.settings
                                        settings.set(LauncherSettings.IndexDialX, (at.x / w * 100f).roundToInt().coerceIn(0, 100))
                                        settings.set(LauncherSettings.IndexDialY, (at.y / h * 100f).roundToInt().coerceIn(0, 100))
                                    }
                                    return@awaitEachGesture
                                }
                                else -> {
                                    drive.active = false
                                    return@awaitEachGesture
                                }
                            }
                        if (!turning) return@awaitEachGesture
                        drive.position = pos
                        drive.active = true
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                val p = change.position
                                // Right at the middle the angle swings wildly: wait until the thumb is back on the ring.
                                if ((p - mid).getDistance() < size.width * 0.09f) continue
                                val a = DialMath.angle(p.x, p.y, mid.x, mid.y)
                                val d = DialMath.turn(lastAngle, a)
                                lastAngle = a
                                drive.spin += d * 180f / PI.toFloat()
                                pos = if (clockFace) DialMath.clock(a, n) else DialMath.knob(pos, d, n, config.dialTurns)
                                drive.position = pos
                            }
                        } finally {
                            drive.active = false
                        }
                    }
                },
        )
    }
}
