package dev.liquid.launcher.core.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * A themed icon in parts, coloured only when it is drawn, with the current palette. A new
 * wallpaper then only means a redraw with other colours: nothing is rendered again.
 */
sealed interface IconArt {
    /** The app's monochrome [glyph] in the foreground colour on [plate] (the icon shape) in the background colour. Both are alpha masks. */
    class Mono(val plate: Bitmap, val glyph: Bitmap) : IconArt

    /** A full-colour icon shown in two tones of the palette: dark parts in the darker colour, light parts in the lighter. */
    class Duotone(val source: ImageBitmap) : IconArt
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
