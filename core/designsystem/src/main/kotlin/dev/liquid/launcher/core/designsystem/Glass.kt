package dev.liquid.launcher.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/**
 * Draws a glass surface that samples [backdrop].
 *
 * The surface must not be a descendant of the content recorded into [backdrop], otherwise it
 * would sample itself. Use it on a few surfaces at a time, never per item in a scrolling list.
 */
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: CornerBasedShape,
    quality: GlassQuality,
    tint: Color,
): Modifier = when (quality) {
    GlassQuality.Full -> drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(4.dp.toPx())
            lens(refractionHeight = 14.dp.toPx(), refractionAmount = 28.dp.toPx())
        },
        onDrawSurface = { drawRect(tint) },
    )

    GlassQuality.Frosted -> drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(20.dp.toPx())
        },
        onDrawSurface = { drawRect(tint) },
    )

    GlassQuality.Tint -> this
        .clip(shape)
        .background(tint.copy(alpha = (tint.alpha * 2.5f).coerceIn(0.35f, 0.85f)))
        .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
}
