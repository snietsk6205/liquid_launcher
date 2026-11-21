package dev.liquid.launcher.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

enum class GlassQuality {
    /** Refraction, blur and highlight. Needs RuntimeShader (Android 13). */
    Full,

    /** Blur and tint, no refraction. */
    Frosted,

    /** Translucent tint only. No shader work at all. */
    Tint;

    /** This tier, or [cap] if the cap is the cheaper of the two. */
    fun cappedAt(cap: GlassQuality): GlassQuality = if (cap.ordinal > ordinal) cap else this

    companion object {
        fun fromSetting(value: String): GlassQuality = when (value) {
            "frosted" -> Frosted
            "tint" -> Tint
            else -> Full
        }
    }
}

/** How Full glass bends the light (P4, 0.9.12). */
enum class GlassEngine {
    /** The AGSL glass of [LiquidShader]: Snell refraction by the edge's shape, dispersion, specular, shading, grain. */
    Real,

    /** The backdrop library's lens, as up to 0.9.11. */
    Lens;

    companion object {
        fun fromSetting(value: String): GlassEngine = if (value == "lens") Lens else Real
    }
}

/** How one glass surface looks. */
@Immutable
data class GlassStyle(
    val quality: GlassQuality,
    val blur: Dp,
    /** 0..1, how strongly the edges bend what is behind (Full only). */
    val refraction: Float,
    val tint: Color,
    /** 0..1, the bright rim along the edge ("edge light", 0.6.4); 0 draws none. */
    val rim: Float = 0f,
    /**
     * The look of what is seen through the glass (0.8.0, D110): colour saturation (1 unchanged,
     * 1.5 is the "vibrancy" every glass had up to 0.7.2) and brightness (-1..1, 0 unchanged).
     */
    val saturation: Float = 1.5f,
    val brightness: Float = 0f,
    /** Rainbow fringes where the edge bends the light, like a prism (Full only). */
    val dispersion: Boolean = false,
    /** Thick glass: the middle swells what is behind as well as the edge bending it (Full only). */
    val depth: Boolean = false,
    /** Colour of the edge light. */
    val rimColor: Color = Color.White,
    /** Where the light comes from, in degrees (45: top left). */
    val lightAngle: Float = 45f,
    /** 0..1: the soft shadow under the glass (1 is the shadow glass always had). */
    val shadow: Float = 1f,
    /** 0..1: light glowing inside the edge, as in a drop of water. */
    val glow: Float = 0f,
    /** P4 (0.9.12): what draws Full glass. The values below are the real glass's ([GlassEngine.Real]). */
    val engine: GlassEngine = GlassEngine.Real,
    /** Width of the rounded edge that bends the light (at most half the surface). */
    val bevel: Dp = 16.dp,
    /** Height of the glass, as a multiple of [bevel]: thicker glass bends what is behind the edge further. */
    val thickness: Float = 2f,
    /** 0..1: how far red and blue split at the edge, when [dispersion] is on. */
    val dispersionAmount: Float = 0.35f,
    /** 0..1: how much the middle magnifies, when [depth] is on. */
    val swell: Float = 0.4f,
    /** 0..1: light caught by the edge facing [lightAngle] (a little by the opposite edge), in [rimColor]. */
    val specular: Float = 0.6f,
    /** 0..1: the edge away from the light darkens, as thick glass does. */
    val shade: Float = 0.4f,
    /** 0..1: a fine grain over the glass. */
    val grain: Float = 0.1f,
) {
    companion object {
        val Default = GlassStyle(GlassQuality.Full, 4.dp, 0.6f, Color.White.copy(alpha = 0.18f))

        /** The "Thickness" setting (0..100 %) as the glass's height in edge widths: 0.5 (thin) to 3.5 (a slab); 50 % is 2. */
        fun thicknessOf(percent: Int): Float = 0.5f + 3f * percent.coerceIn(0, 100) / 100f
    }
}

/**
 * 0.11.2: the speed check (Settings → Diagnostics) draws every glass surface one way after another while it plays the
 * main animations, so the frame meter measures on the phone itself what each part of the glass costs: its blur, the
 * bending, its shadows, the theme's material, or no glass at all. [Variant.AsSet] outside the check.
 */
object GlassTest {
    enum class Variant(val label: String) {
        AsSet("glass as set"),
        NoBlur("glass without its blur"),
        KindFrosted("frosted glass, no bending"),
        KindLiquid("liquid glass, bending"),
        NoShadow("glass without shadows"),
        NoMaterial("without the theme's material"),
        Tint("a flat tint, no glass"),
    }

    var variant by androidx.compose.runtime.mutableStateOf(Variant.AsSet)

    /** [style] as the variant draws it. */
    fun apply(style: GlassStyle): GlassStyle = when (variant) {
        Variant.AsSet, Variant.NoMaterial -> style
        Variant.NoBlur -> style.copy(blur = 0.dp)
        Variant.KindFrosted -> style.copy(refraction = 0f, dispersion = false, bevel = 3.dp)
        Variant.KindLiquid -> style.copy(refraction = 0.6f, dispersion = true, dispersionAmount = 0.25f, bevel = 12.dp)
        Variant.NoShadow -> style.copy(shadow = 0f, glow = 0f)
        Variant.Tint -> style.copy(quality = GlassQuality.Tint)
    }
}

/** The rim for [style], or null for none. */
private fun rimOf(style: GlassStyle): (() -> Highlight?)? =
    if (style.rim <= 0.01f) null else ({
        Highlight.Default.copy(
            alpha = style.rim.coerceIn(0f, 1f),
            style = HighlightStyle.Default(color = style.rimColor, angle = style.lightAngle),
        )
    })

private fun shadowOf(style: GlassStyle): (() -> Shadow?) =
    if (style.shadow <= 0.01f) ({ null }) else ({ Shadow.Default.copy(alpha = style.shadow.coerceIn(0f, 1f)) })

private fun glowOf(style: GlassStyle): (() -> InnerShadow?)? =
    if (style.glow <= 0.01f) null else ({
        InnerShadow.Default.copy(
            radius = (6f + 10f * style.glow).dp,
            color = style.rimColor.copy(alpha = 0.55f),
            alpha = style.glow.coerceIn(0f, 1f),
        )
    })

/** Saturation and brightness of what is behind; exactly the old vibrancy for the old values. */
private fun BackdropEffectScope.look(style: GlassStyle) {
    if (style.saturation == 1.5f && style.brightness == 0f) {
        vibrancy()
    } else if (style.saturation != 1f || style.brightness != 0f) {
        colorControls(brightness = style.brightness, contrast = 1f, saturation = style.saturation)
    }
}

/**
 * Draws a glass surface that samples [backdrop].
 *
 * The surface must not be a descendant of the content recorded into [backdrop], otherwise it
 * would sample itself. Use it on a few surfaces at a time, never per item in a scrolling list.
 */
fun Modifier.liquidGlass(backdrop: Backdrop, shape: CornerBasedShape, style: GlassStyle, exported: LayerBackdrop? = null): Modifier =
    liquidGlassAsGiven(backdrop, shape, GlassTest.apply(style), exported)

private fun Modifier.liquidGlassAsGiven(backdrop: Backdrop, shape: CornerBasedShape, style: GlassStyle, exported: LayerBackdrop?): Modifier =
    when (style.quality) {
        GlassQuality.Full -> drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            highlight = rimOf(style),
            shadow = shadowOf(style),
            innerShadow = glowOf(style),
            exportedBackdrop = exported,
            effects = {
                look(style)
                if (style.blur > 0.dp) blur(style.blur.toPx())
                // P4: the real glass; the library's lens only where it cannot run.
                if (!realGlass(shape, style) && style.refraction > 0f) {
                    lens(
                        refractionHeight = (10f + 10f * style.refraction).dp.toPx(),
                        refractionAmount = (32f * style.refraction).dp.toPx(),
                        depthEffect = style.depth,
                        chromaticAberration = style.dispersion,
                    )
                }
            },
            onDrawSurface = { drawRect(style.tint) },
        )

        GlassQuality.Frosted -> drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            highlight = rimOf(style),
            shadow = shadowOf(style),
            innerShadow = glowOf(style),
            exportedBackdrop = exported,
            effects = {
                look(style)
                blur(maxOf(style.blur, 12.dp).toPx())
            },
            onDrawSurface = { drawRect(style.tint) },
        )

        GlassQuality.Tint -> this
            .clip(shape)
            .background(style.tint.copy(alpha = (style.tint.alpha * 2.5f).coerceIn(0.35f, 0.85f)))
            .border(1.dp, style.rimColor.copy(alpha = 0.18f + 0.3f * style.rim), shape)
    }

/**
 * 0.10.2: glass whose values are read while drawing ([style] is called in the backdrop's own lambdas), so two looks can
 * blend frame by frame, as the individual pills do while the home pill grows into the drawer's, without a frame ever
 * recomposing. [quality] decides how it is drawn and must not change while it is used.
 */
fun Modifier.liquidGlass(backdrop: Backdrop, shape: CornerBasedShape, quality: GlassQuality, style: () -> GlassStyle): Modifier =
    liquidGlassLive(backdrop, shape, if (GlassTest.variant == GlassTest.Variant.Tint) GlassQuality.Tint else quality) { GlassTest.apply(style()) }

private fun Modifier.liquidGlassLive(backdrop: Backdrop, shape: CornerBasedShape, quality: GlassQuality, style: () -> GlassStyle): Modifier =
    when (quality) {
        GlassQuality.Tint -> liquidGlassAsGiven(backdrop, shape, style(), null)
        else -> drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            highlight = {
                val s = style()
                if (s.rim <= 0.01f) null else Highlight.Default.copy(
                    alpha = s.rim.coerceIn(0f, 1f),
                    style = HighlightStyle.Default(color = s.rimColor, angle = s.lightAngle),
                )
            },
            shadow = {
                val s = style()
                if (s.shadow <= 0.01f) null else Shadow.Default.copy(alpha = s.shadow.coerceIn(0f, 1f))
            },
            innerShadow = {
                val s = style()
                if (s.glow <= 0.01f) null else InnerShadow.Default.copy(
                    radius = (6f + 10f * s.glow).dp,
                    color = s.rimColor.copy(alpha = 0.55f),
                    alpha = s.glow.coerceIn(0f, 1f),
                )
            },
            effects = {
                val s = style()
                look(s)
                if (quality == GlassQuality.Full) {
                    if (s.blur > 0.dp) blur(s.blur.toPx())
                    if (!realGlass(shape, s) && s.refraction > 0f) {
                        lens(
                            refractionHeight = (10f + 10f * s.refraction).dp.toPx(),
                            refractionAmount = (32f * s.refraction).dp.toPx(),
                            depthEffect = s.depth,
                            chromaticAberration = s.dispersion,
                        )
                    }
                } else {
                    blur(maxOf(s.blur, 12.dp).toPx())
                }
            },
            onDrawSurface = { drawRect(style().tint) },
        )
    }

/** From glass [a] to glass [b] by [t]: every amount eased, switches (dispersion, depth, engine) taken half way. */
fun lerpGlass(a: GlassStyle, b: GlassStyle, t: Float): GlassStyle {
    if (t <= 0f || a == b) return a
    if (t >= 1f) return b
    fun f(x: Float, y: Float) = x + (y - x) * t
    fun d(x: Dp, y: Dp) = Dp(f(x.value, y.value))
    val second = t >= 0.5f
    return a.copy(
        blur = d(a.blur, b.blur), refraction = f(a.refraction, b.refraction),
        tint = androidx.compose.ui.graphics.lerp(a.tint, b.tint, t), rim = f(a.rim, b.rim),
        saturation = f(a.saturation, b.saturation), brightness = f(a.brightness, b.brightness),
        dispersion = if (second) b.dispersion else a.dispersion, depth = if (second) b.depth else a.depth,
        rimColor = androidx.compose.ui.graphics.lerp(a.rimColor, b.rimColor, t), lightAngle = f(a.lightAngle, b.lightAngle),
        shadow = f(a.shadow, b.shadow), glow = f(a.glow, b.glow), engine = if (second) b.engine else a.engine,
        bevel = d(a.bevel, b.bevel), thickness = f(a.thickness, b.thickness), dispersionAmount = f(a.dispersionAmount, b.dispersionAmount),
        swell = f(a.swell, b.swell), specular = f(a.specular, b.specular), shade = f(a.shade, b.shade), grain = f(a.grain, b.grain),
    )
}

/** Older form used by the glass benchmark: one quality tier with default blur and refraction. */
fun Modifier.liquidGlass(backdrop: Backdrop, shape: CornerBasedShape, quality: GlassQuality, tint: Color): Modifier =
    liquidGlass(backdrop, shape, GlassStyle(quality, 4.dp, 0.6f, tint))
