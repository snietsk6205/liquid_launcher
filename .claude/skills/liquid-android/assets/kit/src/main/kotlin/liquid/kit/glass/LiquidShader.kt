package liquid.kit

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toArgb
import com.kyant.backdrop.effects.effect
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Real liquid glass (P4, 0.9.12): one AGSL shader run on what is behind a glass surface, after its blur.
 *
 * The glass is modelled as a slab with a rounded edge: [GlassStyle.bevel] wide, [GlassStyle.thickness] times as
 * high. Looking straight down, each ray is bent by Snell's law where the edge slopes (index of refraction from
 * [GlassStyle.refraction]) and lands further inside, so the edge bends and squeezes what is behind it. Red, green
 * and blue bend by slightly different indices ([GlassStyle.dispersionAmount]), the middle can swell
 * ([GlassStyle.swell]), the edge facing the light catches it ([GlassStyle.specular], Fresnel-weighted, a little on
 * the opposite edge too), the edge away from it darkens ([GlassStyle.shade]), and a fine grain sits on top.
 * Replaces the backdrop library's lens, which stays as the fallback when this does not compile here.
 */
object LiquidShader {
    const val SOURCE = """
        uniform shader content;

        uniform float2 size;
        uniform float2 offset;
        uniform float4 radii;
        uniform float bevel;
        uniform float thickness;
        uniform float ior;
        uniform float spread;
        uniform float swell;
        uniform float2 toLight;
        uniform float specular;
        uniform float shade;
        uniform float grain;
        layout(color) uniform half4 rimColor;

        float cornerRadius(float2 c, float4 r) {
            if (c.x < 0.0) {
                return c.y < 0.0 ? r.x : r.w;
            }
            return c.y < 0.0 ? r.y : r.z;
        }

        float sdRoundRect(float2 c, float2 h, float r) {
            float2 q = abs(c) - h + r;
            return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
        }

        float2 edgeNormal(float2 c, float2 h, float r) {
            float2 q = abs(c) - h + r;
            float2 n = float2(0.0, 1.0);
            if (q.x > 0.0 && q.y > 0.0) {
                n = normalize(q);
            } else if (q.x > q.y) {
                n = float2(1.0, 0.0);
            }
            return n * (step(0.0, c) * 2.0 - 1.0);
        }

        float2 bend(float3 N, float eta) {
            float cosi = N.z;
            float k = 1.0 - eta * eta * (1.0 - cosi * cosi);
            float3 R = float3(0.0, 0.0, -eta) + (eta * cosi - sqrt(max(k, 0.0))) * N;
            return R.xy / max(-R.z, 0.05);
        }

        float hash(float2 p) {
            float3 p3 = fract(float3(p.xyx) * 0.1031);
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }

        half4 main(float2 coord) {
            float2 h = size * 0.5;
            float2 c = coord + offset - h;
            float r = min(cornerRadius(c, radii), min(h.x, h.y));
            float inside = -sdRoundRect(c, h, r);
            if (inside <= 0.0) {
                return content.eval(coord);
            }
            float b = max(min(bevel, min(h.x, h.y)), 1.0);
            float t = clamp(inside / b, 0.0, 1.0);
            float u = 1.0 - t;
            float2 n = edgeNormal(c, h, min(max(r, b), min(h.x, h.y)));
            float lift = sqrt(max(1.0 - u * u, 0.0));
            float slope = (thickness / b) * u / max(lift, 0.02);
            float3 N = normalize(float3(n * slope, 1.0));
            float height = thickness * lift;
            float2 dome = -c * (swell * 0.07 * t);

            half4 color;
            if (u > 0.0 && spread > 0.0005) {
                half4 red = content.eval(coord + bend(N, 1.0 / (ior - spread)) * height + dome);
                half4 green = content.eval(coord + bend(N, 1.0 / ior) * height + dome);
                half4 blue = content.eval(coord + bend(N, 1.0 / (ior + spread)) * height + dome);
                color = half4(red.r, green.g, blue.b, green.a);
            } else {
                color = content.eval(coord + bend(N, 1.0 / ior) * height + dome);
            }

            float edge = 1.0 - N.z;
            float facing = dot(n, toLight);
            float toward = max(facing, 0.0);
            float away = max(-facing, 0.0);
            float glint = pow(edge, 3.0) * (toward * toward + 0.4 * away * away);
            float dark = shade * (0.10 * edge + 0.28 * pow(edge, 1.5) * away);
            float3 rgb = color.rgb * (1.0 - dark) + rimColor.rgb * (specular * glint) * color.a;
            rgb += (hash(coord) - 0.5) * (grain * 0.07) * color.a;
            return half4(clamp(rgb, 0.0, 1.0), color.a);
        }
    """

    /**
     * The shader, compiled once per process (and logged), or null when it does not compile here. One instance serves
     * every glass surface: each sets its values and makes its effect at once, on the main thread, and an effect keeps
     * the values it was made with.
     */
    internal val shared: android.graphics.RuntimeShader? by lazy {
        try {
            android.graphics.RuntimeShader(SOURCE).also { DebugLog.event("Glass", "liquid glass shader ready") }
        } catch (e: Throwable) {
            DebugLog.error("Glass", "liquid glass shader unavailable, using the simple lens: $e")
            null
        }
    }

    /** True when the shader compiled on this phone. */
    val available: Boolean get() = shared != null

    /** Compiles it ahead of the first glass surface (call off the main thread). */
    fun warmUp() {
        shared
    }
}

/**
 * Adds the real glass to the effects of a backdrop: call it last, after colour and blur. False when it cannot run
 * (the shader did not compile, or [GlassStyle.engine] asks for the simple lens): the caller then draws the lens.
 * [bevelCap]: the widest edge allowed, in pixels (small buttons use a narrower one).
 */
fun BackdropEffectScope.realGlass(shape: CornerBasedShape, style: GlassStyle, bevelCap: Float = Float.MAX_VALUE): Boolean {
    if (style.engine != GlassEngine.Real) return false
    val shader = LiquidShader.shared ?: return false
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return true
    val half = min(w, h) / 2f
    val ltr = layoutDirection == LayoutDirection.Ltr
    fun corner(c: androidx.compose.foundation.shape.CornerSize) = c.toPx(size, this).coerceIn(0f, half)
    val start = corner(shape.topStart)
    val end = corner(shape.topEnd)
    val bottomEnd = corner(shape.bottomEnd)
    val bottomStart = corner(shape.bottomStart)
    val radii = if (ltr) floatArrayOf(start, end, bottomEnd, bottomStart) else floatArrayOf(end, start, bottomStart, bottomEnd)
    val bevel = min(min(style.bevel.toPx(), bevelCap), half).coerceAtLeast(1.dp.toPx())
    val ior = 1f + 0.8f * style.refraction.coerceIn(0f, 1f)
    val spread = if (style.dispersion) 0.3f * style.dispersionAmount.coerceIn(0f, 1f) * (ior - 1f) else 0f
    val swell = if (style.depth) style.swell.coerceIn(0f, 1f) else 0f
    val angle = Math.toRadians(style.lightAngle.toDouble())
    val pad = padding
    val made = with(shader) {
        setFloatUniform("size", w, h)
        setFloatUniform("offset", -pad, -pad)
        setFloatUniform("radii", radii)
        setFloatUniform("bevel", bevel)
        setFloatUniform("thickness", bevel * style.thickness.coerceAtLeast(0f))
        setFloatUniform("ior", ior)
        setFloatUniform("spread", spread)
        setFloatUniform("swell", swell)
        setFloatUniform("toLight", -cos(angle).toFloat(), -sin(angle).toFloat())
        setFloatUniform("specular", style.specular.coerceIn(0f, 1f))
        setFloatUniform("shade", style.shade.coerceIn(0f, 1f))
        setFloatUniform("grain", style.grain.coerceIn(0f, 1f))
        setColorUniform("rimColor", style.rimColor.toArgb())
        android.graphics.RenderEffect.createRuntimeShaderEffect(this, "content")
    }
    effect(made.asComposeRenderEffect())
    return true
}
