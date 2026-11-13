package dev.liquid.launcher.core.data

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.ColorUtils

/** Themed icons: on which surfaces, and in which colours. [epoch] changes when the wallpaper does. */
data class ThemedIcons(
    val home: Boolean,
    val drawer: Boolean,
    /** Apps without a monochrome icon: true = two-tone in the colours, false = unchanged. */
    val tintUncovered: Boolean,
    val background: Int,
    val foreground: Int,
) {
    val key: String get() = "t${background.toUInt().toString(16)}${foreground.toUInt().toString(16)}$tintUncovered"
}

/** Colours for themed icons, from the wallpaper or from the system palette. */
object ThemedColors {
    /** Background and foreground colours. [source] is "wallpaper" or "system", [style] "auto", "light" or "dark". */
    fun palette(context: Context, source: String, style: String): Pair<Int, Int> {
        val dark = when (style) {
            "dark" -> true
            "light" -> false
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        if (source == "system" && Build.VERSION.SDK_INT >= 31) {
            return try {
                if (dark) {
                    context.getColor(android.R.color.system_neutral1_800) to context.getColor(android.R.color.system_accent1_100)
                } else {
                    context.getColor(android.R.color.system_accent1_100) to context.getColor(android.R.color.system_accent1_700)
                }
            } catch (e: Exception) {
                fromSeed(seed(context), dark)
            }
        }
        return fromSeed(seed(context), dark)
    }

    /** The wallpaper's main colour (system wallpaper, which the launcher also sets). */
    fun seed(context: Context): Int = try {
        WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
    } catch (e: Exception) {
        null
    } ?: 0xFF6A7BA8.toInt()

    /** Pale and deep tones of the seed's hue, swapped for dark mode. */
    fun fromSeed(seed: Int, dark: Boolean): Pair<Int, Int> {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed, hsl)
        val hue = hsl[0]
        val sat = hsl[1]
        return if (dark) {
            ColorUtils.HSLToColor(floatArrayOf(hue, (sat * 0.6f).coerceAtMost(0.32f), 0.17f)) to
                ColorUtils.HSLToColor(floatArrayOf(hue, sat.coerceIn(0.25f, 0.7f), 0.82f))
        } else {
            ColorUtils.HSLToColor(floatArrayOf(hue, (sat * 0.8f).coerceAtMost(0.6f), 0.9f)) to
                ColorUtils.HSLToColor(floatArrayOf(hue, sat.coerceIn(0.3f, 0.75f), 0.27f))
        }
    }

    /**
     * The app's monochrome layer (Android 13+) as an alpha mask the size of the icon, placed
     * the way an adaptive icon places its layers. Null when the app has none.
     */
    fun monochromeMask(icon: Drawable?, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < 33 || icon !is AdaptiveIconDrawable) return null
        val mono = icon.monochrome ?: return null
        return try {
            val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ALPHA_8)
            val inset = sizePx / 4
            mono.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
            mono.draw(Canvas(out))
            out
        } catch (e: Exception) {
            null
        }
    }

    /** The system icon shape filled with the background and the monochrome glyph in the foreground colour. */
    fun themed(mask: Bitmap, sizePx: Int, background: Int, foreground: Int): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        AdaptiveIconDrawable(ColorDrawable(background), ColorDrawable(0)).apply {
            setBounds(0, 0, sizePx, sizePx)
            draw(canvas)
        }
        canvas.drawBitmap(mask, null, android.graphics.Rect(0, 0, sizePx, sizePx), Paint(Paint.FILTER_BITMAP_FLAG).apply { color = foreground })
        return out
    }

    /** Just the glyph in the foreground colour, on transparent (for icon packs that draw their own background). */
    fun glyph(mask: Bitmap, sizePx: Int, foreground: Int): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(mask, null, android.graphics.Rect(0, 0, sizePx, sizePx), Paint(Paint.FILTER_BITMAP_FLAG).apply { color = foreground })
        return out
    }

    /**
     * Two-tone version of a full-colour icon: dark parts take the darker of the two colours,
     * light parts the lighter one, so the icon keeps its shapes and contrast.
     */
    fun duotone(icon: Bitmap, background: Int, foreground: Int): Bitmap {
        val lightFirst = ColorUtils.calculateLuminance(background) > ColorUtils.calculateLuminance(foreground)
        val lo = if (lightFirst) foreground else background
        val hi = if (lightFirst) background else foreground
        fun channel(shift: Int): Pair<Float, Float> {
            val a = ((lo shr shift) and 0xFF).toFloat()
            val b = ((hi shr shift) and 0xFF).toFloat()
            return a to (b - a)
        }
        val (r0, rd) = channel(16)
        val (g0, gd) = channel(8)
        val (b0, bd) = channel(0)
        // Luminance weights; result = lo + (hi - lo) * L.
        val wr = 0.2126f
        val wg = 0.7152f
        val wb = 0.0722f
        val matrix = ColorMatrix(
            floatArrayOf(
                rd * wr / 255f, rd * wg / 255f, rd * wb / 255f, 0f, r0,
                gd * wr / 255f, gd * wg / 255f, gd * wb / 255f, 0f, g0,
                bd * wr / 255f, bd * wg / 255f, bd * wb / 255f, 0f, b0,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        val out = Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(icon, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return out
    }
}
