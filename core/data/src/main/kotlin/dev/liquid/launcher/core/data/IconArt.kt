package dev.liquid.launcher.core.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * A themed icon in parts, coloured only when it is drawn, with the current palette. A new
 * wallpaper then only means a redraw with other colours: nothing is rendered again.
 */
sealed interface IconArt {
    /**
     * The app's monochrome [glyph] in the foreground colour on [plate] (the icon shape) in the
     * background colour. Both are alpha masks. [badge] (the work profile's briefcase) is drawn
     * over them in its own colours.
     */
    class Mono(val plate: Bitmap, val glyph: Bitmap, val badge: ImageBitmap? = null) : IconArt

    /** A full-colour icon shown in two tones of the palette: dark parts in the darker colour, light parts in the lighter. */
    class Duotone(val source: ImageBitmap) : IconArt

    /** A line icon (one colour on nothing): drawn in one colour that reads on the wallpaper. */
    class Tint(val source: ImageBitmap) : IconArt

    companion object {
        /**
         * Art for an icon that has no monochrome layer (an icon pack's, a picked one, an older
         * app's): a line icon is tinted, anything else becomes two-tone. Two-tone on a line icon
         * would paint white strokes in the pale colour, which disappears on a light wallpaper.
         */
        fun of(icon: ImageBitmap): IconArt = if (isLineIcon(icon)) Tint(icon) else Duotone(icon)

        /** True when the opaque part of [icon] is small and of one brightness: strokes or a flat glyph. */
        fun isLineIcon(icon: ImageBitmap): Boolean = try {
            val bitmap = icon.asAndroidBitmap()
            val w = bitmap.width
            val h = bitmap.height
            var opaque = 0
            var min = 255
            var max = 0
            val steps = 20
            for (iy in 0 until steps) {
                for (ix in 0 until steps) {
                    val c = bitmap.getPixel((ix * 2 + 1) * w / (steps * 2), (iy * 2 + 1) * h / (steps * 2))
                    if ((c ushr 24) < 140) continue
                    opaque++
                    val l = (((c shr 16) and 0xFF) * 54 + ((c shr 8) and 0xFF) * 183 + (c and 0xFF) * 19) shr 8
                    if (l < min) min = l
                    if (l > max) max = l
                }
            }
            opaque > 0 && opaque < steps * steps * 0.58f && max - min < 70
        } catch (e: Throwable) {
            false
        }
    }
}

/** Alpha masks of the icon shapes, made once per shape and size and shared by every icon. */
object IconPlates {
    private val cache = HashMap<String, Bitmap>()

    fun plate(shape: String, sizePx: Int): Bitmap = synchronized(cache) {
        cache.getOrPut("$shape:$sizePx") { make(shape, sizePx) }
    }

    private fun make(shape: String, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val outline = IconShapes.path(shape, size.toFloat())
        if (outline != null) {
            canvas.drawPath(outline, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
        } else {
            AdaptiveIconDrawable(ColorDrawable(android.graphics.Color.WHITE), ColorDrawable(0)).apply {
                setBounds(0, 0, size, size)
                draw(canvas)
            }
        }
        return out.extractAlpha().also { out.recycle() }
    }
}
