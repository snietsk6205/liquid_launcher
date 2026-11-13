package dev.liquid.launcher.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
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

/** How one glass surface looks. */
@Immutable
data class GlassStyle(
    val quality: GlassQuality,
    val blur: Dp,
    /** 0..1, how strongly the edges bend what is behind (Full only). */
    val refraction: Float,
    val tint: Color,
) {
    companion object {
        val Default = GlassStyle(GlassQuality.Full, 4.dp, 0.6f, Color.White.copy(alpha = 0.18f))
    }
}

/**
 * Draws a glass surface that samples [backdrop].
 *
 * The surface must not be a descendant of the content recorded into [backdrop], otherwise it
 * would sample itself. Use it on a few surfaces at a time, never per item in a scrolling list.
 */
fun Modifier.liquidGlass(backdrop: Backdrop, shape: CornerBasedShape, style: GlassStyle): Modifier =
    when (style.quality) {
        GlassQuality.Full -> drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                if (style.blur > 0.dp) blur(style.blur.toPx())
                if (style.refraction > 0f) {
                    lens(
                        refractionHeight = (10f + 10f * style.refraction).dp.toPx(),
                        refractionAmount = (32f * style.refraction).dp.toPx(),
                    )
                }
            },
            onDrawSurface = { drawRect(style.tint) },
        )

        GlassQuality.Frosted -> drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(maxOf(style.blur, 12.dp).toPx())
            },
            onDrawSurface = { drawRect(style.tint) },
        )

        GlassQuality.Tint -> this
            .clip(shape)
            .background(style.tint.copy(alpha = (style.tint.alpha * 2.5f).coerceIn(0.35f, 0.85f)))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
    }

/** Older form used by the glass benchmark: one quality tier with default blur and refraction. */
fun Modifier.liquidGlass(backdrop: Backdrop, shape: CornerBasedShape, quality: GlassQuality, tint: Color): Modifier =
    liquidGlass(backdrop, shape, GlassStyle(quality, 4.dp, 0.6f, tint))
