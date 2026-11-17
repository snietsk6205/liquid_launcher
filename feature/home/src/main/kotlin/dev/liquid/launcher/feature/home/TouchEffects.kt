package dev.liquid.launcher.feature.home

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.ColorSetting
import dev.liquid.launcher.core.settings.SettingsStore
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import dev.liquid.launcher.core.settings.LauncherSettings as S

/*
 * Effects and touch (0.8.0, D111 and D113): what a press throws over the screen, and effects
 * that move over the wallpaper, the way a keyboard theme does over its keys.
 *
 * The press kinds follow wasi-master/wmkeyboard's PressEffects (MIT): a splash is a pure
 * function of where and when its press happened, so nothing is kept per press but a place and a
 * time, nothing is allocated per frame, and the frame loop sleeps between presses. Ripple, Drop
 * and Bubbles are the launcher's own, for its liquid look.
 */

/** The touch effect's settings. */
data class TouchFxConfig(
    /** "ripple", "drop", "light", "ink", "paint", "flood", "puff", "glint", "pop", "swoosh", "specks", "bubbles" or "none". */
    val kind: String = "ripple",
    /** Also taps on empty space (not only icons, buttons, pill items and results). */
    val everywhere: Boolean = false,
    /** How many drops, specks and bits, 0..1. */
    val amount: Float = 0.5f,
    /** 1 is the effect's own pace. */
    val speed: Float = 0.7f,
    val brightness: Float = 0.45f,
    val size: Float = 1f,
    /** "wallpaper", "accent", "rainbow", "white" or "custom". */
    val colour: String = "wallpaper",
    val custom: Color? = null,
)

fun SettingsStore.touchFxConfig() = TouchFxConfig(
    kind = get(S.TouchEffect),
    everywhere = get(S.TouchWhere) == "everywhere",
    amount = get(S.TouchAmount) / 100f,
    speed = get(S.TouchSpeed) / 100f,
    brightness = get(S.TouchBrightness) / 100f,
    size = get(S.TouchSize) / 100f,
    colour = get(S.TouchColour),
    custom = ColorSetting.parse(get(S.TouchCustomColour))?.let { Color(it) },
)

/** The moving wallpaper's settings. */
data class WallpaperFxConfig(
    /** "orbs", "bubbles", "motes", "snow", "rain" or "none". */
    val kind: String = "none",
    val amount: Float = 0.5f,
    /** 0 holds it still. */
    val speed: Float = 0.6f,
    val brightness: Float = 0.4f,
    val size: Float = 1f,
    val colour: String = "wallpaper",
    val custom: Color? = null,
    /** How much touches stir it, 0..1. */
    val react: Float = 0.4f,
)

fun SettingsStore.wallpaperFxConfig() = WallpaperFxConfig(
    kind = get(S.WallpaperEffect),
    amount = get(S.WallpaperEffectAmount) / 100f,
    speed = get(S.WallpaperEffectSpeed) / 100f,
    brightness = get(S.WallpaperEffectBrightness) / 100f,
    size = get(S.WallpaperEffectSize) / 100f,
    colour = get(S.WallpaperEffectColour),
    custom = ColorSetting.parse(get(S.TouchCustomColour))?.let { Color(it) },
    react = get(S.WallpaperEffectReact) / 100f,
)

/** The colours an effect picks from. Read while composing, so a new wallpaper's colours arrive at once. */
@Composable
internal fun effectColours(mode: String, custom: Color?): List<Color> {
    val accent = HomeTheme.accent
    val fg = IconPaint.foreground
    val bg = IconPaint.background
    return remember(mode, custom, accent, fg, bg) {
        when (mode) {
            "accent" -> listOf(accent, lerp(accent, Color.White, 0.35f))
            "white" -> listOf(Color.White)
            "custom" -> (custom ?: accent).let { listOf(it, lerp(it, Color.White, 0.3f)) }
            "rainbow" -> List(12) { Color.hsv(it * 30f, 0.7f, 1f) }
            else -> buildList {
                add(accent)
                if (fg != 0) add(Color(fg))
                if (bg != 0) add(lerp(Color(bg), Color.White, 0.25f))
                add(lerp(accent, Color.White, 0.45f))
            }
        }
    }
}

/**
 * The presses of one home screen: where the finger went down and the recent splashes, kept in a
 * small ring. Icons and buttons throw a splash through [fromPress] when their press shows (so a
 * scroll through them never splashes); taps on empty space through [noteTap] when the touch
 * effect is set to everywhere. One splash per touch at most.
 */
@Stable
class TouchFx {
    var config by androidx.compose.runtime.mutableStateOf(TouchFxConfig())

    private val xs = FloatArray(MAX)
    private val ys = FloatArray(MAX)
    private val times = LongArray(MAX)
    private var next = 0

    /** Bumped by every splash; wakes the layer. */
    var revision by mutableIntStateOf(0)
        private set

    /** When the newest splash was thrown. */
    var lastAt = 0L
        private set

    private var down = Offset.Unspecified
    private var downSeq = 0
    private var thrownSeq = -1

    /** The home screen's own coordinates, for the settings preview's pretend presses. */
    internal var root: LayoutCoordinates? = null

    internal fun noteDown(at: Offset) {
        down = at
        downSeq++
    }

    /** A tap on something that is not an icon or a button. */
    internal fun noteTap(at: Offset) {
        if (config.everywhere) throwAt(at)
    }

    /** An icon, button, pill item or result showed its press. */
    fun fromPress() = throwAt(down)

    /** A pretend press for the settings preview, in the home screen's coordinates. */
    internal fun pretend(at: Offset) {
        noteDown(at)
        throwAt(at)
    }

    private fun throwAt(at: Offset) {
        if (!at.isSpecified || thrownSeq == downSeq) return
        thrownSeq = downSeq
        val now = SystemClock.uptimeMillis()
        xs[next] = at.x
        ys[next] = at.y
        times[next] = now
        next = (next + 1) % MAX
        lastAt = now
        revision++
    }

    /** Calls [block] for each splash younger than [lifeMs] at [now], oldest first. */
    internal fun forEachRecent(now: Long, lifeMs: Long, block: (x: Float, y: Float, age: Long, seed: Int) -> Unit) {
        for (k in 0 until MAX) {
            val i = (next + k) % MAX
            val t = times[i]
            if (t == 0L) continue
            val age = now - t
            if (age in 0 until lifeMs) block(xs[i], ys[i], age, seedOf(t))
        }
    }

    /** How stirred up the screen is: each press adds 1, halving every 1.2 s. */
    internal fun energy(now: Long): Float {
        var e = 0f
        for (t in times) {
            if (t == 0L) continue
            val age = now - t
            if (age in 0..6000) e += 0.5f.pow(age / 1200f)
        }
        return e
    }

    private companion object {
        const val MAX = 24
    }
}

internal val LocalTouchFx = staticCompositionLocalOf<TouchFx?> { null }

/** Watches every finger going down on the home screen without taking it, for [TouchFx]. */
internal fun Modifier.touchFxDowns(fx: TouchFx): Modifier = pointerInput(fx) {
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        fx.noteDown(first.position)
        var far = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == first.id } ?: break
            far = maxOf(far, (change.position - first.position).getDistance())
            if (!change.pressed) {
                if (far < viewConfiguration.touchSlop) fx.noteTap(change.position)
                break
            }
        }
    }
}

// ---- Press splashes ----

/** Everything a splash draws with, made once per setting and colour change. */
private class SplashKit(val config: TouchFxConfig, val colors: List<Color>) {
    val lights: List<Color> = colors.map { lerp(it, Color.White, 0.6f) }
    val glows: List<Brush> = colors.map { softBrush(it) }
    val smokes: List<Brush> = colors.map { softBrush(lerp(it, Color.Gray, 0.55f)) }
    val lifeMs: Long = (baseLife(config.kind) / config.speed.coerceAtLeast(0.2f)).toLong()

    private var strokeDensity = 0f
    var ring = Stroke(width = 2f)
        private set
    var arc = Stroke(width = 2f, cap = StrokeCap.Round)
        private set

    fun strokesFor(density: Float) {
        if (density == strokeDensity) return
        strokeDensity = density
        ring = Stroke(width = 2f * density * config.size)
        arc = Stroke(width = 2.6f * density * config.size, cap = StrokeCap.Round)
    }

    fun colorIndex(seed: Int): Int = (rnd(seed, 997) * colors.size).toInt().coerceIn(0, colors.size - 1)

    companion object {
        fun baseLife(kind: String): Float = when (kind) {
            "ripple" -> 1100f
            "drop" -> 1000f
            "light" -> 900f
            "ink", "paint", "puff" -> 1500f
            "flood" -> 1100f
            "glint" -> 550f
            "pop", "swoosh" -> 650f
            "specks" -> 900f
            "bubbles" -> 1700f
            else -> 1000f
        }
    }
}

private fun softBrush(c: Color): Brush = Brush.radialGradient(
    0f to c,
    0.5f to c.copy(alpha = c.alpha * 0.75f),
    1f to c.copy(alpha = 0f),
    center = Offset.Zero,
    radius = 1f,
)

/**
 * The splashes over the home screen: composed only for a touch effect, asleep between presses.
 * It takes no touches.
 */
@Composable
internal fun TouchFxLayer(fx: TouchFx, config: TouchFxConfig, modifier: Modifier = Modifier) {
    if (config.kind == "none") return
    val colours = effectColours(config.colour, config.custom)
    val kit = remember(config, colours) { SplashKit(config, colours.ifEmpty { listOf(Color.White) }) }
    var now by remember { mutableLongStateOf(0L) }
    val revision = fx.revision
    LaunchedEffect(kit, revision) {
        if (fx.lastAt == 0L) return@LaunchedEffect
        while (true) {
            val t = withFrameMillis { SystemClock.uptimeMillis() }
            now = t
            if (t - fx.lastAt > kit.lifeMs) break
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = now
        if (t == 0L) return@Canvas
        kit.strokesFor(density)
        fx.forEachRecent(t, kit.lifeMs) { x, y, age, seed -> splash(kit, x, y, age, seed) }
    }
}

/** About one icon across, in dp, before the effect's size. */
private const val SPAN_DP = 40f

private const val TAU = (2 * PI).toFloat()

/** A stable 0..1 value for a splash's [salt]th property. */
private fun rnd(seed: Int, salt: Int): Float {
    var h = seed * 374761393 + salt * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777215f
}

/** A press's seed, from the time it happened, so a splash keeps its shape frame to frame. */
private fun seedOf(pressedAt: Long): Int = (pressedAt xor (pressedAt ushr 21)).toInt() * -1640531535

private fun easeOut(u: Float): Float {
    val v = 1f - u.coerceIn(0f, 1f)
    return 1f - v * v * v
}

/** A soft round spot: [brush] is one of the kit's unit-sized spots, moved to ([x], [y]) and grown to [r]. */
private fun DrawScope.softDot(brush: Brush, x: Float, y: Float, r: Float, alpha: Float) {
    if (r <= 0f || alpha <= 0.004f) return
    translate(x, y) {
        scale(r, r, pivot = Offset.Zero) {
            drawCircle(brush, radius = 1f, center = Offset.Zero, alpha = alpha.coerceAtMost(1f))
        }
    }
}

/** One press's splash, [age] ms after the press at ([cx], [cy]). */
private fun DrawScope.splash(kit: SplashKit, cx: Float, cy: Float, age: Long, seed: Int) {
    val fx = kit.config
    val life = kit.lifeMs
    if (age < 0 || age >= life) return
    val u = age.toFloat() / life
    val dp = density
    val k = SPAN_DP * dp * fx.size
    val ci = kit.colorIndex(seed)
    val base = kit.colors[ci]
    val a = fx.brightness
    when (fx.kind) {
        "ripple" -> {
            // Rings that spread like on water, one after another, thinning as they go.
            softDot(kit.glows[ci], cx, cy, k * 0.55f * (1f - u * 0.5f), a * 0.45f * (1f - u).pow(2f) * min(1f, age / 60f))
            val rings = 2 + (fx.amount * 2.5f).toInt()
            for (j in 0 until rings) {
                val start = j * 0.16f
                if (u < start) continue
                val uj = ((u - start) / (1f - start)).coerceIn(0f, 1f)
                val r = k * (0.18f + 1.45f * easeOut(uj)) * (1f - 0.12f * j)
                val c = kit.lights[ci]
                val alpha = (a * (1f - uj).pow(1.3f) * (1f - 0.18f * j)).coerceIn(0f, 1f)
                drawCircle(c.copy(alpha = c.alpha * alpha), r, Offset(cx, cy), style = Stroke(width = (0.7f + 2.2f * (1f - uj)) * dp * fx.size))
            }
        }
        "drop" -> {
            val c = kit.lights[ci]
            if (u < 0.22f) {
                // The drop falling onto the glass: it shrinks as it lands.
                val f = u / 0.22f
                val r = k * (0.34f - 0.18f * f)
                softDot(kit.glows[ci], cx, cy, r * 1.6f, a * 0.5f * f)
                drawCircle(c.copy(alpha = c.alpha * (a * (0.4f + 0.6f * f)).coerceAtMost(1f)), r * 0.55f, Offset(cx, cy))
            } else {
                val v = (u - 0.22f) / 0.78f
                val e = easeOut(v)
                // The ring of the impact.
                drawCircle(
                    c.copy(alpha = c.alpha * (a * (1f - v)).coerceIn(0f, 1f)), k * (0.2f + 1.1f * e), Offset(cx, cy),
                    style = Stroke(width = (0.8f + 2f * (1f - v)) * dp * fx.size),
                )
                // A crown of droplets flung out, falling back as they go.
                val n = 6 + (fx.amount * 8).toInt()
                for (i in 0 until n) {
                    val ang = TAU * i / n + rnd(seed, i) * 0.5f
                    val dist = k * (0.25f + 0.85f * e) * (0.7f + 0.5f * rnd(seed, i + 30))
                    val fall = k * 0.55f * v * v
                    val r = (1.4f + 2.6f * rnd(seed, i + 60)) * dp * fx.size * (1f - 0.6f * v)
                    drawCircle(c.copy(alpha = c.alpha * (a * (1f - v)).coerceIn(0f, 1f)), r, Offset(cx + cos(ang) * dist, cy + sin(ang) * dist * 0.8f + fall))
                }
            }
        }
        "bubbles" -> {
            val n = 4 + (fx.amount * 9).toInt()
            for (i in 0 until n) {
                val start = rnd(seed, i + 5) * 0.3f
                if (u < start) continue
                val v = ((u - start) / (1f - start)).coerceIn(0f, 1f)
                val e = easeOut(v)
                val x = cx + (rnd(seed, i) - 0.5f) * k * 0.9f + sin(v * 7f + i) * k * 0.12f
                val y = cy - k * (0.1f + 2.4f * rnd(seed, i + 10).coerceAtLeast(0.3f)) * e
                val r = (2.5f + 5.5f * rnd(seed, i + 20)) * dp * fx.size * (0.6f + 0.5f * v)
                val alpha = (a * (1f - v).pow(0.8f) * min(1f, v * 6f)).coerceIn(0f, 1f)
                val c = kit.lights[(ci + i) % kit.lights.size]
                drawCircle(c.copy(alpha = c.alpha * alpha * 0.25f), r, Offset(x, y))
                drawCircle(c.copy(alpha = c.alpha * alpha), r, Offset(x, y), style = Stroke(width = 1.1f * dp))
                drawCircle(Color.White.copy(alpha = alpha * 0.9f), r * 0.22f, Offset(x - r * 0.35f, y - r * 0.35f))
            }
        }
        "ink" -> {
            val alpha = a * (1f - u).pow(1.3f) * min(1f, age / 80f)
            val r = k * (0.45f + 0.6f * easeOut(u))
            for (i in 0 until 5) {
                val ang = rnd(seed, i) * TAU
                val dist = r * 0.4f * rnd(seed, i + 20)
                softDot(kit.glows[ci], cx + cos(ang) * dist, cy + sin(ang) * dist, r * (0.55f + 0.4f * rnd(seed, i + 40)), alpha)
            }
        }
        "light" -> {
            val alpha = a * min(1f, age / 70f) * (1f - u).pow(1.5f)
            softDot(kit.glows[ci], cx, cy, k * (0.95f + 0.45f * easeOut(u)), alpha)
        }
        "flood" -> {
            val diagonal = sqrt(size.width * size.width + size.height * size.height)
            val alpha = a * 0.7f * sin(PI.toFloat() * u) * min(1f, age / 60f)
            softDot(kit.glows[ci], cx, cy, diagonal * (0.25f + 0.85f * easeOut(u)), alpha)
        }
        "puff" -> {
            val drift = k * 0.9f * easeOut(u)
            val alpha = a * 0.65f * (1f - u) * min(1f, age / 120f)
            for (i in 0 until 4) {
                val x = cx + (rnd(seed, i) - 0.5f) * k * 0.8f + sin(u * 3f + i) * k * 0.1f
                val y = cy - drift * (0.6f + 0.6f * rnd(seed, i + 10))
                val r = k * (0.35f + 0.65f * u) * (0.7f + 0.5f * rnd(seed, i + 20))
                softDot(kit.smokes[ci], x, y, r, alpha)
            }
        }
        "glint" -> {
            val swell = sin(PI.toFloat() * u)
            val c = kit.lights[ci]
            softDot(kit.glows[ci], cx, cy, k * 0.5f * swell, a * 0.6f * swell)
            val tone = c.copy(alpha = c.alpha * (a * swell).coerceAtMost(1f))
            val ray = k * 0.85f * swell
            val short = ray * 0.4f
            drawLine(tone, Offset(cx - ray, cy), Offset(cx + ray, cy), strokeWidth = 2f * dp, cap = StrokeCap.Round)
            drawLine(tone, Offset(cx, cy - ray), Offset(cx, cy + ray), strokeWidth = 2f * dp, cap = StrokeCap.Round)
            drawLine(tone, Offset(cx - short, cy - short), Offset(cx + short, cy + short), strokeWidth = 1.2f * dp, cap = StrokeCap.Round)
            drawLine(tone, Offset(cx - short, cy + short), Offset(cx + short, cy - short), strokeWidth = 1.2f * dp, cap = StrokeCap.Round)
        }
        "pop" -> {
            val e = easeOut(u)
            val c = base.copy(alpha = base.alpha * (a * (1f - u)).coerceAtMost(1f))
            drawCircle(c, k * (0.25f + 0.65f * e), Offset(cx, cy), style = kit.ring)
            val bits = 6 + (fx.amount * 6).toInt()
            for (i in 0 until bits) {
                val ang = TAU * i / bits + rnd(seed, i) * 0.6f
                val dist = k * (0.3f + 0.95f * e) * (0.8f + 0.4f * rnd(seed, i + 30))
                drawCircle(c, (2.6f - 1.6f * u) * dp * fx.size, Offset(cx + cos(ang) * dist, cy + sin(ang) * dist))
            }
            for (j in 0 until 2) {
                val ang = rnd(seed, 60 + j) * TAU
                val dist = k * (0.5f + 0.7f * e)
                val px = cx + cos(ang) * dist
                val py = cy + sin(ang) * dist
                val arm = 3f * dp * fx.size
                drawLine(c, Offset(px - arm, py), Offset(px + arm, py), strokeWidth = 1.4f * dp)
                drawLine(c, Offset(px, py - arm), Offset(px, py + arm), strokeWidth = 1.4f * dp)
            }
        }
        "swoosh" -> {
            val e = easeOut(u)
            for (j in 0 until 3) {
                val r = k * (0.42f + 0.18f * j) * (0.85f + 0.3f * e)
                val turn = if (j % 2 == 0) 220f else -220f
                val start = rnd(seed, j) * 360f + turn * e
                val c = base.copy(alpha = base.alpha * (a * (1f - u) * (1f - 0.2f * j)).coerceAtMost(1f))
                drawArc(
                    c, start, 70f + 50f * (1f - u), useCenter = false,
                    topLeft = Offset(cx - r, cy - r), size = Size(r * 2f, r * 2f), style = kit.arc,
                )
            }
        }
        "specks" -> {
            val e = easeOut(u)
            val n = 8 + (fx.amount * 14).toInt()
            for (i in 0 until n) {
                val ang = rnd(seed, i) * TAU
                val dist = k * (0.25f + 1.1f * rnd(seed, i + 30)) * (0.4f + 0.6f * e)
                val c = kit.colors[(ci + i) % kit.colors.size]
                val alpha = (a * (1f - u) * (0.5f + 0.5f * rnd(seed, i + 60))).coerceAtMost(1f)
                drawCircle(c.copy(alpha = c.alpha * alpha), (0.9f + 1.5f * rnd(seed, i + 90)) * dp * fx.size, Offset(cx + cos(ang) * dist, cy + sin(ang) * dist))
            }
        }
        "paint" -> paintSplat(kit, cx, cy, age, u, k, base, a, seed)
    }
}

/** A splat of paint: a round core with lobes, flung drops, and drips that run down as it dries. */
private fun DrawScope.paintSplat(kit: SplashKit, cx: Float, cy: Float, age: Long, u: Float, k: Float, base: Color, a: Float, seed: Int) {
    val fx = kit.config
    val dp = density
    val grow = easeOut(age / 110f)
    val fade = if (u < 0.55f) 1f else 1f - (u - 0.55f) / 0.45f
    val c = base.copy(alpha = base.alpha * (a * fade).coerceIn(0f, 1f))
    val r0 = k * 0.36f * grow
    if (r0 <= 0f) return
    drawCircle(c, r0, Offset(cx, cy))
    val lobes = 5 + (fx.amount * 5).toInt()
    for (i in 0 until lobes) {
        val ang = rnd(seed, i) * TAU
        val dist = r0 * (0.55f + 0.45f * rnd(seed, i + 40))
        drawCircle(c, r0 * (0.32f + 0.3f * rnd(seed, i + 80)), Offset(cx + cos(ang) * dist, cy + sin(ang) * dist))
    }
    val spikes = 4 + (fx.amount * 4).toInt()
    for (i in 0 until spikes) {
        val ang = rnd(seed, i + 120) * TAU
        val dx = cos(ang)
        val dy = sin(ang)
        val reach = r0 * (1.35f + 0.9f * rnd(seed, i + 160))
        drawLine(
            c, Offset(cx + dx * r0 * 0.6f, cy + dy * r0 * 0.6f), Offset(cx + dx * reach, cy + dy * reach),
            strokeWidth = 2.2f * dp * fx.size * grow, cap = StrokeCap.Round,
        )
        val drop = reach + r0 * 0.3f
        drawCircle(c, 1.8f * dp * fx.size * grow + r0 * 0.08f, Offset(cx + dx * drop, cy + dy * drop))
    }
    val run = easeOut((age - 120f) / (kit.lifeMs * 0.7f))
    if (age < 120 || run <= 0f) return
    val drips = 2 + (rnd(seed, 200) * 2f).toInt()
    for (i in 0 until drips) {
        val x = cx + (rnd(seed, i + 210) - 0.5f) * r0 * 1.4f
        val top = cy + r0 * 0.4f
        val len = k * (0.35f + 0.6f * rnd(seed, i + 230)) * run
        val w = (2.4f + 1.4f * rnd(seed, i + 250)) * dp * fx.size
        drawLine(c, Offset(x, top), Offset(x, top + len), strokeWidth = w, cap = StrokeCap.Round)
        drawCircle(c, w * 0.75f, Offset(x, top + len))
    }
}

// ---- Moving wallpaper ----

/**
 * An effect moving over the wallpaper, under the icons (D113): drawn inside the layer glass
 * samples, so the pill and the dock bend it like the picture. [running] false (an app, the
 * drawer or search over the home screen) stops its clock; speed 0 holds one still frame.
 */
@Composable
internal fun WallpaperFxLayer(config: WallpaperFxConfig, fx: TouchFx?, running: () -> Boolean, modifier: Modifier = Modifier) {
    if (config.kind == "none") return
    val colours = effectColours(config.colour, config.custom).ifEmpty { listOf(Color.White) }
    val glows = remember(colours) { colours.map { softBrush(it) } }
    var phase by remember { mutableFloatStateOf(0f) }
    var stir by remember { mutableFloatStateOf(0f) }
    val active by remember { derivedStateOf { running() } }
    LaunchedEffect(active, config) {
        if (!active || config.speed <= 0f) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val t = withFrameNanos { it }
            val dt = ((t - last) / 1e9f).coerceIn(0f, 0.1f)
            last = t
            val energy = if (fx != null && config.react > 0f) fx.energy(SystemClock.uptimeMillis()).coerceAtMost(4f) else 0f
            stir = config.react * energy
            phase += dt * config.speed * (1f + 1.5f * stir)
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = phase
        val bright = (config.brightness * (1f + 0.6f * stir)).coerceAtMost(1f)
        ambient(config, colours, glows, t, bright)
    }
}

private fun DrawScope.ambient(config: WallpaperFxConfig, colours: List<Color>, glows: List<Brush>, t: Float, bright: Float) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val dp = density
    val s = config.size
    val seed = 0x5EED
    when (config.kind) {
        "orbs" -> {
            val n = 3 + (config.amount * 4).toInt()
            val big = maxOf(w, h)
            for (i in 0 until n) {
                val x = w * (0.5f + 0.42f * sin(t * 0.11f * (1f + 0.4f * rnd(seed, i)) + rnd(seed, i + 10) * TAU))
                val y = h * (0.5f + 0.44f * sin(t * 0.08f * (1f + 0.4f * rnd(seed, i + 20)) + rnd(seed, i + 30) * TAU))
                val r = big * (0.18f + 0.16f * rnd(seed, i + 40)) * s
                val pulse = 0.75f + 0.25f * sin(t * 0.3f + i)
                softDot(glows[i % glows.size], x, y, r, bright * 0.55f * pulse)
            }
        }
        "bubbles" -> {
            val n = 8 + (config.amount * 30).toInt()
            for (i in 0 until n) {
                val r = (3f + 9f * rnd(seed, i + 40)) * dp * s
                val span = h + 4f * r
                val vy = (18f + 36f * rnd(seed, i)) * dp
                val y = h + 2f * r - ((t * vy + rnd(seed, i + 10) * span) % span)
                val x = rnd(seed, i + 20) * w + sin(t * (0.6f + rnd(seed, i + 30)) + i) * 12f * dp
                val c = colours[i % colours.size]
                val light = lerp(c, Color.White, 0.6f)
                val alpha = bright * (0.5f + 0.5f * rnd(seed, i + 50))
                drawCircle(light.copy(alpha = alpha * 0.18f), r, Offset(x, y))
                drawCircle(light.copy(alpha = alpha * 0.8f), r, Offset(x, y), style = Stroke(width = 1f * dp))
                drawCircle(Color.White.copy(alpha = alpha * 0.8f), r * 0.2f, Offset(x - r * 0.38f, y - r * 0.38f))
            }
        }
        "motes" -> {
            val n = 15 + (config.amount * 60).toInt()
            for (i in 0 until n) {
                val vx = (rnd(seed, i) - 0.5f) * 16f * dp
                val vy = -(2f + 9f * rnd(seed, i + 10)) * dp
                val x = posMod(rnd(seed, i + 20) * w + t * vx, w)
                val y = posMod(rnd(seed, i + 30) * h + t * vy, h)
                val twinkle = 0.4f + 0.6f * (0.5f + 0.5f * sin(t * (1f + 2f * rnd(seed, i + 40)) + i))
                val r = (1f + 2.2f * rnd(seed, i + 50)) * dp * s
                val c = lerp(colours[i % colours.size], Color.White, 0.5f)
                softDot(glows[i % glows.size], x, y, r * 4f, bright * 0.35f * twinkle)
                drawCircle(c.copy(alpha = (bright * twinkle).coerceAtMost(1f)), r, Offset(x, y))
            }
        }
        "snow" -> {
            val n = 20 + (config.amount * 100).toInt()
            for (i in 0 until n) {
                val vy = (22f + 40f * rnd(seed, i)) * dp
                val span = h + 20f * dp
                val y = posMod(rnd(seed, i + 10) * span + t * vy, span) - 10f * dp
                val x = rnd(seed, i + 20) * w + sin(t * (0.5f + rnd(seed, i + 30)) + i * 1.7f) * 16f * dp
                val r = (1.2f + 2.3f * rnd(seed, i + 40)) * dp * s
                drawCircle(Color.White.copy(alpha = (bright * (0.5f + 0.5f * rnd(seed, i + 50))).coerceAtMost(1f)), r, Offset(x, y))
            }
        }
        "rain" -> {
            val n = 15 + (config.amount * 60).toInt()
            for (i in 0 until n) {
                val len = (14f + 16f * rnd(seed, i)) * dp * s
                val vy = (520f + 420f * rnd(seed, i + 10)) * dp
                val span = h + len
                val y = posMod(rnd(seed, i + 20) * span + t * vy, span) - len
                val x = rnd(seed, i + 30) * (w + h * 0.12f) - y * 0.12f
                val c = lerp(colours[i % colours.size], Color.White, 0.65f)
                drawLine(
                    c.copy(alpha = (bright * (0.35f + 0.4f * rnd(seed, i + 40))).coerceAtMost(1f)),
                    Offset(x, y), Offset(x - len * 0.12f, y + len),
                    strokeWidth = (0.9f + 0.7f * rnd(seed, i + 50)) * dp, cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun posMod(v: Float, m: Float): Float = ((v % m) + m) % m

/**
 * Settings → Effects and touch: a row of glass buttons over the live home screen that press
 * themselves in turn, so the touch effect, the press motion, the light from the finger and the
 * moving wallpaper all show as they are changed.
 */
@Composable
internal fun EffectsDemo(fx: TouchFx, glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop) {
    val icons = listOf(LauncherIcons.Search, LauncherIcons.Apps, LauncherIcons.Sparkle, LauncherIcons.Edit)
    val feels = icons.map { rememberPressFeel() }
    val places = remember { arrayOfNulls<LayoutCoordinates>(icons.size) }
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    LaunchedEffect(Unit) {
        var i = 0
        while (true) {
            kotlinx.coroutines.delay(1100)
            val root = fx.root ?: continue
            val coords = places[i]?.takeIf { it.isAttached } ?: continue
            // A little off the centre each time, so Tilt shows which side was pressed.
            val local = Offset(coords.size.width * (0.3f + 0.4f * ((i * 3) % 4) / 3f), coords.size.height * (0.35f + 0.1f * i))
            fx.noteDown(root.localPositionOf(coords, local))
            feels[i].press(position = local)
            kotlinx.coroutines.delay(190)
            feels[i].release()
            i = (i + 1) % icons.size
        }
    }
    // Every few presses one icon lands, as when coming back from its app (0.9.4).
    LaunchedEffect(Unit) {
        var i = 0
        while (true) {
            kotlinx.coroutines.delay(4300)
            feels[i].land()
            i = (i + 2) % icons.size
        }
    }
    // "Wobble while editing" shows here too while it is on (0.9.4).
    val wobble = EditWobble.degrees
    if (wobble > 0f) {
        LaunchedEffect(Unit) {
            val start = androidx.compose.runtime.withFrameNanos { it }
            while (true) androidx.compose.runtime.withFrameNanos { EditWobble.time.floatValue = (it - start) / 1e9f }
        }
    }
    val style = glass.style(Surfaces.Menu, lightBackground)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            icons.forEachIndexed { i, icon ->
                Box(
                    Modifier
                        .size(64.dp)
                        .onGloballyPositioned { places[i] = it }
                        .graphicsLayer {
                            if (wobble > 0f) rotationZ = kotlin.math.sin(EditWobble.time.floatValue * (2.4f + 0.17f * i) * 6.2832f + i * 1.9f) * wobble
                        }
                        .pressSquash(feels[i])
                        .liquidGlass(backdrop, CircleShape, style)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    LauncherIcon(icon, contentDescription = null, tint = color, size = 28.dp)
                }
            }
        }
    }
}
