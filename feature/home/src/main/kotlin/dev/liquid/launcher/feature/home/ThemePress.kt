package dev.liquid.launcher.feature.home

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.liquid.launcher.core.data.ThemeParticles
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * W3 (0.10.0): WM Keyboard's press effects on the launcher's controls. The touch bloom's light spreads under the finger
 * across a sheet (its colour, strength and reach); the particles burst from every themed control pressed (stars,
 * hearts, sparkle, confetti, emoji or your pictures, with their colour, size, speed, spread, gravity, duration and
 * intensity). A burst is a pure function of where and when its press happened: nothing is kept but a place and a
 * time, and the frame clock runs only while a burst lives.
 */

/** The bloom's light under the finger, over a sheet's surface and under its content. */
@Composable
internal fun Modifier.bloomLight(): Modifier {
    val bloom = currentTheme?.bloom ?: return this
    if (bloom.light <= 0.01f) return this
    val colour = bloom.color?.let { Color(it) } ?: HomeTheme.accent
    return composed {
        var at by remember { mutableStateOf(Offset.Unspecified) }
        val glow = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                at = down.position
                scope.launch { glow.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = MotionScale.stiffness(700f))) }
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    at = change.position
                    if (!change.pressed) break
                }
                scope.launch { glow.animateTo(0f, tween(MotionScale.ms(420))) }
            }
        }.drawBehind {
            val g = glow.value
            if (g <= 0.01f || at == Offset.Unspecified) return@drawBehind
            val r = bloom.radiusDp.dp.toPx()
            drawCircle(
                Brush.radialGradient(listOf(colour.copy(alpha = (bloom.light * 0.6f * g).coerceAtMost(1f)), Color.Transparent), center = at, radius = r),
                radius = r, center = at,
            )
        }
    }
}

/** Where particle bursts are thrown; one per screen. */
internal class ParticleBursts {
    class Burst(val at: Offset, val start: Long, val seed: Int)

    val bursts = mutableStateListOf<Burst>()
    var tick by mutableLongStateOf(0L)
    private var count = 0

    fun burst(at: Offset) {
        val now = SystemClock.uptimeMillis()
        bursts.removeAll { now - it.start > 3000 }
        if (bursts.size > 12) bursts.removeAt(0)
        bursts.add(Burst(at, now, ++count * 7919))
    }
}

internal val LocalParticles = staticCompositionLocalOf<ParticleBursts?> { null }

/** Throws a burst from where [interaction] presses this control, when the theme has particles. */
@Composable
internal fun Modifier.particlesOn(interaction: InteractionSource): Modifier {
    val bursts = LocalParticles.current ?: return this
    currentTheme?.particles ?: return this
    return composed {
        var origin by remember { mutableStateOf(Offset.Zero) }
        LaunchedEffect(interaction) {
            interaction.interactions.collect { i -> if (i is PressInteraction.Press) bursts.burst(origin + i.pressPosition) }
        }
        onGloballyPositioned { origin = it.positionInRoot() }
    }
}

/** The particles in flight, over everything. */
@Composable
internal fun ParticleLayer(bursts: ParticleBursts) {
    val theme = currentTheme ?: return
    val p = theme.particles ?: return
    if (bursts.bursts.isEmpty()) return
    val accent = HomeTheme.accent
    val text = Color(theme.button.text)
    val trail = theme.trail?.let { Color(it) } ?: accent
    val measurer = rememberTextMeasurer()
    val images = p.images.filter { it.isNotBlank() }.take(6).map { rememberThemeImage(it, 160) }
    LaunchedEffect(bursts.bursts.size) {
        while (bursts.bursts.isNotEmpty()) {
            withFrameNanos { bursts.tick = it }
            val now = SystemClock.uptimeMillis()
            bursts.bursts.removeAll { now - it.start > p.durationMs + 50 }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE") val frame = bursts.tick
        val now = SystemClock.uptimeMillis()
        for (b in bursts.bursts) {
            val age = (now - b.start) / 1000f
            val life = age * 1000f / p.durationMs
            if (life >= 1f) continue
            drawBurst(p, b, age, life, accent, text, trail, images.filterNotNull()) { glyph, at, size, colour ->
                val layout = measurer.measure(glyph, TextStyle(fontSize = size.sp, color = colour))
                drawText(layout, topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f), alpha = colour.alpha)
            }
        }
    }
}

private fun rndP(seed: Int, salt: Int): Float {
    var h = seed * 374761393 + salt * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
}

private fun DrawScope.drawBurst(
    p: ThemeParticles,
    b: ParticleBursts.Burst,
    age: Float,
    life: Float,
    accent: Color,
    text: Color,
    trail: Color,
    images: List<androidx.compose.ui.graphics.ImageBitmap>,
    glyph: DrawScope.(String, Offset, Float, Color) -> Unit,
) {
    val n = (7f * p.intensity).toInt().coerceIn(2, 24)
    val dp = density
    val fade = (1f - life).coerceIn(0f, 1f)
    for (k in 0 until n) {
        // Upwards, fanned out by the spread; a little faster or slower each.
        val angle = (-PI / 2 + (rndP(b.seed, k) - 0.5) * PI * 1.6 * p.spread).toFloat()
        val v = (160f + 220f * rndP(b.seed, k + 50)) * dp * p.speed
        val g = 900f * dp * p.gravity * 0.5f
        val pos = b.at + Offset(cos(angle) * v * age, sin(angle) * v * age + 0.5f * g * age * age)
        val size = (5f + 4f * rndP(b.seed, k + 100)) * dp * p.size
        val colour = when (p.colorMode) {
            "KEY_TEXT" -> text
            "ACCENT" -> accent
            "GESTURE_TRAIL" -> trail
            "CUSTOM" -> Color(p.color)
            "RANDOM" -> Color.hsv(rndP(b.seed, k + 200) * 360f, 0.75f, 1f)
            else -> when (p.kind) {
                "HEARTS" -> Color(0xFFFF5A8A)
                "STARS" -> Color(0xFFFFD54F)
                "CONFETTI" -> Color.hsv(rndP(b.seed, k + 200) * 360f, 0.7f, 1f)
                else -> Color.White
            }
        }.let { it.copy(alpha = it.alpha * fade) }
        val turn = age * (180f + 240f * rndP(b.seed, k + 300)) * (if (k % 2 == 0) 1f else -1f)
        when (p.kind) {
            "HEARTS" -> drawPath(heart(pos, size), colour)
            "SPARKLE" -> rotate(turn * 0.3f, pos) { drawPath(sparkle(pos, size), colour) }
            "CONFETTI" -> rotate(turn, pos) { drawRect(colour, pos - Offset(size / 2f, size / 4f), Size(size, size / 2f)) }
            "EMOJI" -> {
                val list = p.emoji?.let { e -> Regex("\\X").findAll(e).map { it.value }.filter { it.isNotBlank() }.toList() }.orEmpty().ifEmpty { listOf("✨") }
                glyph(list[k % list.size], pos, size / dp * 2.2f, Color.White.copy(alpha = fade))
            }
            "CUSTOM_IMAGE" -> {
                val image = images.getOrNull(k % images.size.coerceAtLeast(1))
                if (image != null) {
                    val w = size * 2.4f
                    val h = w * image.height / image.width.coerceAtLeast(1)
                    rotate(turn * 0.4f, pos) {
                        drawImage(image, dstOffset = IntOffset((pos.x - w / 2f).toInt(), (pos.y - h / 2f).toInt()), dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)), alpha = fade)
                    }
                }
            }
            else -> rotate(turn * 0.5f, pos) { drawPath(star(pos, size), colour) }
        }
    }
}

private fun star(c: Offset, r: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val a = -PI / 2 + i * PI / 5
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = c.x + (cos(a) * rr).toFloat()
        val y = c.y + (sin(a) * rr).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun sparkle(c: Offset, r: Float): Path = Path().apply {
    moveTo(c.x, c.y - r)
    quadraticTo(c.x, c.y, c.x + r, c.y)
    quadraticTo(c.x, c.y, c.x, c.y + r)
    quadraticTo(c.x, c.y, c.x - r, c.y)
    quadraticTo(c.x, c.y, c.x, c.y - r)
    close()
}

private fun heart(c: Offset, r: Float): Path = Path().apply {
    moveTo(c.x, c.y + r * 0.9f)
    cubicTo(c.x - r * 1.3f, c.y + r * 0.1f, c.x - r * 0.9f, c.y - r * 1.0f, c.x, c.y - r * 0.35f)
    cubicTo(c.x + r * 0.9f, c.y - r * 1.0f, c.x + r * 1.3f, c.y + r * 0.1f, c.x, c.y + r * 0.9f)
    close()
}
