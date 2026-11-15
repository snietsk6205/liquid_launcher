package dev.liquid.launcher.feature.home

import android.graphics.RuntimeShader
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.dp

/**
 * Wallpaper and layout transitions (0.4.1, D59; 0.7.0, D103). The old screen is a picture laid over
 * the new one, which is already live underneath. The picture leaves (faded, blurred, or through a
 * GPU shader for ripple, ink, shine and melt) while the new screen settles into place, so both
 * halves move and the change reads as one motion rather than a picture fading off.
 *
 * Shaders are AGSL (Android 13+, the launcher's minimum), applied to the picture's layer only.
 */
internal object WallpaperTransitions {
    /** Every style, in the order the setting lists them. */
    val styles = listOf("liquid", "ripple", "ink", "shine", "melt", "zoom", "depth", "slide", "fade", "none")

    /** The curve progress follows: shader styles sweep at a steady pace, the others decelerate softly. */
    fun easing(style: String): Easing = when (style) {
        "ripple", "ink", "shine", "melt" -> CubicBezierEasing(0.35f, 0f, 0.25f, 1f)
        else -> CubicBezierEasing(0.2f, 0f, 0f, 1f)
    }

    /** How long a style takes relative to the "Transition length" setting: the shader sweeps need a little more. */
    fun lengthFactor(style: String): Float = when (style) {
        "ripple", "ink", "melt" -> 1.35f
        "shine" -> 1.2f
        else -> 1f
    }

    private const val NOISE = """
        float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        float noise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float2 u = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
        }
        float fbm(float2 p) {
            float v = 0.0;
            float a = 0.5;
            for (int i = 0; i < 4; i++) { v += a * noise(p); p *= 2.03; a *= 0.5; }
            return v;
        }
    """

    /** A ring wave from [origin]: the old picture bends outward on the crest and is gone behind it. */
    private const val RIPPLE = """
        uniform shader image;
        uniform float2 size;
        uniform float progress;
        uniform float2 origin;
        uniform float density;
        half4 main(float2 p) {
            float2 far = max(origin, size - origin);
            float r = progress * length(far) * 1.12;
            float d = distance(p, origin);
            float width = 70.0 * density;
            float k = (d - r) / width;
            float crest = exp(-k * k * 3.0);
            float2 dir = (p - origin) / max(d, 0.001);
            float2 q = p - dir * crest * 26.0 * density * (1.0 - progress * 0.6);
            half4 c = image.eval(q);
            c.rgb += half(crest * 0.12) * c.a;
            float a = smoothstep(-0.7, 0.7, k);
            return c * half(a);
        }
    """

    /** The old picture dissolves through soft, cloudy patches with a faint glow at the edge. */
    private const val INK = NOISE + """
        uniform shader image;
        uniform float2 size;
        uniform float progress;
        half4 main(float2 p) {
            float n = clamp((fbm(p / size.y * 5.0) - 0.12) / 0.72, 0.0, 1.0);
            float edge = 0.09;
            float t = progress * (1.0 + edge * 2.0) - edge;
            float a = smoothstep(t - edge, t + edge, n);
            float glow = clamp(1.0 - abs(n - t) / edge, 0.0, 1.0);
            half4 c = image.eval(p);
            c.rgb += half(glow * 0.22) * c.a;
            return c * half(a);
        }
    """

    /** A diagonal sweep with a bright pane of glass at its edge that bends what it passes over. */
    private const val SHINE = """
        uniform shader image;
        uniform float2 size;
        uniform float progress;
        uniform float density;
        half4 main(float2 p) {
            float2 uv = p / size;
            float s = (uv.x * 0.55 + (1.0 - uv.y) * 0.45);
            float w = 0.07;
            float front = mix(-w * 2.0, 1.0 + w * 2.0, progress);
            float a = smoothstep(front - w, front + w, s);
            float z = (s - front) / w * 1.4;
            float sheen = exp(-z * z);
            float2 q = p + float2(0.55, -0.45) * sheen * 22.0 * density;
            half4 c = image.eval(q);
            c.rgb += half(sheen * 0.32) * c.a;
            return c * half(a);
        }
    """

    /** The old picture runs down in uneven streams, like paint, and thins out as it goes. */
    private const val MELT = NOISE + """
        uniform shader image;
        uniform float2 size;
        uniform float progress;
        half4 main(float2 p) {
            float x = p.x / size.x;
            float speed = 0.35 + 0.65 * noise(float2(x * 14.0, 3.7));
            float t = clamp(progress * 1.3 - (1.0 - speed) * 0.3, 0.0, 1.0);
            float drop = t * t * size.y * (0.45 + 0.55 * speed);
            float2 q = float2(p.x + (noise(float2(x * 30.0, p.y / size.y * 4.0)) - 0.5) * 10.0 * t, p.y - drop);
            half4 c = q.y < 0.0 ? half4(0.0) : image.eval(q);
            float a = 1.0 - smoothstep(0.45, 1.0, progress * (0.85 + 0.3 * speed));
            return c * half(a);
        }
    """

    private val sources = mapOf("ripple" to RIPPLE, "ink" to INK, "shine" to SHINE, "melt" to MELT)
    private val compiled = HashMap<String, RuntimeShader?>()

    /** The shader of [style], compiled once; null when it does not compile here (that style then falls back to liquid). */
    @Synchronized
    private fun shader(style: String): RuntimeShader? {
        if (compiled.containsKey(style)) return compiled[style]
        val source = sources[style] ?: return null
        val shader = try {
            RuntimeShader(source)
        } catch (e: Throwable) {
            dev.liquid.launcher.core.common.DebugLog.error("Wallpaper", "transition shader \"$style\" unavailable: $e")
            null
        }
        compiled[style] = shader
        return shader
    }

    /** Compiles every shader ahead of the first change (off the main thread) and says which work. */
    fun warmUp() {
        val ok = sources.keys.filter { shader(it) != null }
        val failed = sources.keys - ok.toSet()
        dev.liquid.launcher.core.common.DebugLog.event(
            "Wallpaper",
            "transition shaders ready: " + ok.joinToString(", ") + if (failed.isEmpty()) "" else "; failed: " + failed.joinToString(", "),
        )
    }

    /** The old screen's picture leaving, at progress [p] (0 = all there, 1 = gone). [origin] is where a ripple starts. */
    fun GraphicsLayerScope.oldScreenLeaving(p: Float, style: String, origin: Offset?) {
        val pc = p.coerceIn(0f, 1f)
        val density = density
        fun shaderStyle(setUp: RuntimeShader.() -> Unit): Boolean {
            val shader = shader(style) ?: return false
            shader.setUp()
            renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "image").asComposeRenderEffect()
            return true
        }
        val done = when (style) {
            "ripple" -> shaderStyle {
                run {
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("progress", pc)
                    val o = origin ?: Offset(size.width / 2f, size.height * 0.8f)
                    setFloatUniform("origin", o.x, o.y)
                    setFloatUniform("density", density)
                }
            }
            "ink" -> shaderStyle {
                run {
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("progress", pc)
                }
            }
            "shine" -> shaderStyle {
                run {
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("progress", pc)
                    setFloatUniform("density", density)
                }
            }
            "melt" -> shaderStyle {
                run {
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("progress", pc)
                }
            }
            "slide" -> {
                translationX = -pc * size.width
                true
            }
            "depth" -> {
                alpha = (1f - pc * 1.1f).coerceIn(0f, 1f)
                val scale = 1f - 0.1f * pc
                scaleX = scale
                scaleY = scale
                true
            }
            "zoom" -> {
                // You pass through the old screen: it grows towards you, blurs and fades.
                alpha = (1f - pc * 1.25f).coerceIn(0f, 1f)
                val scale = 1f + 0.16f * pc
                scaleX = scale
                scaleY = scale
                val radius = 22.dp.toPx() * pc
                renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Clamp) else null
                true
            }
            "fade" -> {
                alpha = 1f - pc
                true
            }
            else -> false
        }
        if (!done) {
            // Liquid: blurs as it fades, swelling very slightly.
            alpha = (1f - pc * 1.15f).coerceIn(0f, 1f)
            val radius = 26.dp.toPx() * pc
            renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Clamp) else null
            val scale = 1f + 0.025f * pc
            scaleX = scale
            scaleY = scale
        }
    }

    /**
     * The new screen's part (pages and widgets): it settles into place while the old picture leaves.
     * Multiplies into what the layer already has (the drawer and search transitions).
     */
    fun GraphicsLayerScope.newScreenArriving(p: Float, style: String) {
        val rest = 1f - p.coerceIn(0f, 1f)
        if (rest <= 0f) return
        when (style) {
            "liquid", "ripple" -> {
                val scale = 1f + 0.025f * rest
                scaleX *= scale
                scaleY *= scale
            }
            "depth" -> {
                // Comes forward as the old one sinks back.
                val scale = 1f + 0.06f * rest
                scaleX *= scale
                scaleY *= scale
            }
            "zoom" -> {
                // Was behind the old screen and comes into focus.
                val scale = 1f - 0.08f * rest
                scaleX *= scale
                scaleY *= scale
            }
            "slide" -> translationX += rest * size.width * 0.3f
            "shine" -> translationX += rest * size.width * 0.04f
            "melt" -> translationY -= rest * size.height * 0.03f
        }
    }

    /** The new wallpaper's part: a slight settle for the styles that move the new screen (never smaller than the screen). */
    fun GraphicsLayerScope.newWallpaperArriving(p: Float, style: String) {
        val rest = 1f - p.coerceIn(0f, 1f)
        if (rest <= 0f) return
        val grow = when (style) {
            "liquid", "ripple" -> 0.03f
            "depth" -> 0.06f
            "ink" -> 0.02f
            else -> 0f
        }
        if (grow == 0f) return
        val scale = 1f + grow * rest
        scaleX *= scale
        scaleY *= scale
    }
}
