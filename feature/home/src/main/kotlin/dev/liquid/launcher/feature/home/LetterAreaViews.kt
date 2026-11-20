package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.common.WallpaperTones
import dev.liquid.launcher.core.data.GridIndex
import dev.liquid.launcher.core.settings.LetterArea
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** One letter area on the screen, in window coordinates: where a swipe may start the letters. */
internal class AreaHit(val centre: Offset, val radius: Float, val right: Boolean)

/**
 * What the letter areas tell the letter index (0.11.1): while a swipe that started in an area is under way, [window] is
 * the finger in window coordinates and the string acts as if that finger were on it (see ScrollIndex); null otherwise.
 */
@Stable
internal class IndexDrive {
    /** A swipe from an area is under way. */
    var active by mutableStateOf(false)

    /** The finger, in window coordinates, while [active]. */
    var window by mutableStateOf<Offset?>(null)

    /** The edge the letters come out of: the area's. */
    var fromRight by mutableStateOf(true)

    /** The area the swipe started in (for its look while in use), or -1. */
    var area by mutableStateOf(-1)

    /** The areas on screen now, published by [LetterAreaCircles] for [letterAreaSwipes]. */
    @Volatile
    var hits: List<AreaHit> = emptyList()
}

/**
 * Starts the letters from a letter area (0.11.1, Med's design): a swipe that begins inside an area is taken as the
 * finger on the string from its first move on, wherever it goes; the app list does not scroll for it. A tap or a long
 * press inside an area is left alone, so the app under the circle still opens or shows its menu. Read first, in the
 * Initial pass, by the drawer's outer box, so the apps underneath see the touch only when it is not a swipe.
 */
internal fun Modifier.letterAreaSwipes(drive: IndexDrive, enabled: () -> Boolean): Modifier = composed {
    val holder = remember { CoordinatesHolder() }
    this
        .onGloballyPositioned { holder.value = it }
        .pointerInput(drive) {
            val slop = viewConfiguration.touchSlop
            val holdMs = viewConfiguration.longPressTimeoutMillis
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!enabled()) return@awaitEachGesture
                val coords = holder.value?.takeIf { it.isAttached } ?: return@awaitEachGesture
                val start = coords.localToWindow(down.position)
                val index = drive.hits.indexOfFirst { (start - it.centre).getDistance() <= it.radius }
                if (index < 0) return@awaitEachGesture
                // A tap (lifted first) and a long press (held still) belong to the app under the circle; a move belongs to us.
                val moved = withTimeoutOrNull(holdMs) {
                    var result: androidx.compose.ui.input.pointer.PointerInputChange? = null
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed || change.isConsumed) break
                        if ((change.position - down.position).getDistance() > slop) {
                            change.consume()
                            result = change
                            break
                        }
                    }
                    result
                } ?: return@awaitEachGesture
                val hit = drive.hits.getOrNull(index) ?: return@awaitEachGesture
                drive.fromRight = hit.right
                drive.area = index
                drive.window = coords.localToWindow(moved.position)
                drive.active = true
                try {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        drive.window = coords.localToWindow(change.position)
                    }
                } finally {
                    drive.active = false
                    drive.window = null
                    drive.area = -1
                }
            }
        }
}

/**
 * The letter areas' circles (0.11.1): plain circles in the wallpaper's colour (or the accent, or Med's own) over the
 * apps, as clear as "Letter areas: opacity" says (0 %: invisible, still working). They fade while the apps scroll and
 * light up while a swipe from them drives the letters. They take no touches themselves ([letterAreaSwipes] does, at
 * the drawer's outer box); here they only publish where they are. In the settings preview a pretend thumb swipes
 * from the first one through the letters, twice, then rests.
 */
@Composable
internal fun LetterAreaCircles(
    state: LazyGridState,
    index: GridIndex,
    config: IndexConfig,
    drive: IndexDrive,
    controller: HomeController,
    modifier: Modifier,
) {
    val areas = config.areas
    if (areas.isEmpty() || index.entries.isEmpty()) return
    val density = LocalDensity.current
    val tone by WallpaperTones.tone.collectAsState()
    val base = when (config.dialColour) {
        "accent" -> HomeTheme.accent
        "custom" -> config.dialCustom ?: Color(0xFF3F9B6E)
        else -> tone?.roles?.vibrant?.let { Color(it) } ?: HomeTheme.accent
    }

    // Opacity: as set; lower while the apps scroll (not while a swipe from an area moves them); a little brighter in use.
    val scrolling by remember(state) { derivedStateOf { state.isScrollInProgress } }
    val using = drive.active
    var justUsed by remember { mutableStateOf(false) }
    LaunchedEffect(using) {
        if (!using) {
            justUsed = true
            delay(700)
            justUsed = false
        }
    }
    val rest = config.dialOpacity
    val fade = remember { Animatable(rest) }
    LaunchedEffect(using, justUsed, scrolling, rest, config.dialScrollOpacity) {
        val target = when {
            using -> if (rest <= 0f) 0f else (rest + 0.25f).coerceAtMost(1f)
            justUsed -> rest
            scrolling -> minOf(rest, config.dialScrollOpacity)
            else -> rest
        }
        if (target > fade.value && !using) delay(250)
        fade.animateTo(target, tween(if (target < fade.value) 160 else 420))
    }
    val lift = remember { Animatable(0f) }
    LaunchedEffect(using) { lift.animateTo(if (using) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f)) }

    // However the drawer goes away (closing under the thumb), the letters are let go.
    DisposableEffect(drive) {
        onDispose {
            drive.active = false
            drive.window = null
            drive.hits = emptyList()
        }
    }

    val holder = remember { CoordinatesHolder() }
    if (controller.indexDemo) {
        // Settings preview: a pretend thumb starts in the first area and swipes up and down through the letters, twice,
        // then the apps scroll a little (the circles fade as set), and it rests until an option changes.
        LaunchedEffect(config) {
            try {
                var down = true
                // The circles publish where they are once laid out.
                var waited = 0
                while (drive.hits.isEmpty() && waited++ < 60) androidx.compose.runtime.withFrameNanos { }
                repeat(2) {
                    val hit = drive.hits.firstOrNull() ?: return@repeat
                    val reach = with(density) { 180.dp.toPx() }
                    drive.fromRight = hit.right
                    drive.area = 0
                    drive.window = hit.centre
                    drive.active = true
                    val begin = androidx.compose.runtime.withFrameNanos { it }
                    var t = 0f
                    while (t < 1f) {
                        t = ((androidx.compose.runtime.withFrameNanos { it } - begin) / 1e9f / 3.2f).coerceAtMost(1f)
                        drive.window = hit.centre + Offset(0f, -reach * sin(t * 2f * PI.toFloat()))
                    }
                    drive.active = false
                    drive.window = null
                    drive.area = -1
                    delay(1400)
                    state.animateScrollBy(if (down) 700f else -700f, tween(1300))
                    down = !down
                    delay(1200)
                }
            } finally {
                drive.active = false
                drive.window = null
                drive.area = -1
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                holder.value = coords
                val w = coords.size.width.toFloat()
                val h = coords.size.height.toFloat()
                drive.hits = areas.map { a ->
                    val r = with(density) { a.sizeDp.dp.toPx() } / 2f
                    val local = areaCentre(a, w, h, r)
                    AreaHit(coords.localToWindow(local), r, a.right)
                }
            }
            .semantics { contentDescription = "Letter areas" }
            .drawBehind {
                val alpha = fade.value
                if (alpha <= 0.005f) return@drawBehind
                val ring = 1.6.dp.toPx()
                areas.forEachIndexed { i, a ->
                    val r = a.sizeDp.dp.toPx() / 2f
                    val c = areaCentre(a, size.width, size.height, r)
                    val active = drive.area == i
                    val grow = if (active) 1f + 0.06f * lift.value else 1f
                    val glow = if (active) lift.value else 0f
                    val radius = (r - ring) * grow
                    drawCircle(base.copy(alpha = alpha * (0.16f + 0.12f * glow)), radius = radius, center = c)
                    drawCircle(base.copy(alpha = alpha * 0.85f), radius = radius, center = c, style = Stroke(ring * (1f + 0.5f * glow)))
                }
            },
    )
}

/** The middle of [area] in a box of [w] × [h], kept inside it by its radius [r]. */
private fun areaCentre(area: LetterArea, w: Float, h: Float, r: Float): Offset {
    val margin = 4f
    val x = (area.across / 100f * w).coerceIn(r + margin, (w - r - margin).coerceAtLeast(r + margin))
    val y = (area.down / 100f * h).coerceIn(r + margin, (h - r - margin).coerceAtLeast(r + margin))
    return Offset(x, y)
}
