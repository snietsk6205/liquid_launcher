package dev.liquid.launcher.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Icon shapes for adaptive icons: "system" (the phone's own mask) or one of ours. */
object IconShapes {
    val ids = listOf("system", "circle", "squircle", "rounded", "square", "teardrop", "pebble")

    /** The outline of [shape] filling a square of [size] pixels, or null for the system shape. */
    fun path(shape: String, size: Float): Path? {
        val path = Path()
        val r = RectF(0f, 0f, size, size)
        when (shape) {
            "circle" -> path.addOval(r, Path.Direction.CW)
            "squircle" -> superellipse(path, size, 4.0)
            "pebble" -> superellipse(path, size, 2.6)
            "rounded" -> path.addRoundRect(r, size * 0.22f, size * 0.22f, Path.Direction.CW)
            "square" -> path.addRoundRect(r, size * 0.04f, size * 0.04f, Path.Direction.CW)
            "teardrop" -> {
                val big = size / 2f
                val small = size * 0.1f
                path.addRoundRect(r, floatArrayOf(big, big, big, big, small, small, big, big), Path.Direction.CW)
            }
            else -> return null
        }
        return path
    }

    private fun superellipse(path: Path, size: Float, n: Double) {
        val half = size / 2.0
        val steps = 96
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = half + half * sign(c) * abs(c).pow(2.0 / n)
            val y = half + half * sign(s) * abs(s).pow(2.0 / n)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
    }

    /**
     * Renders [icon] in [shape]: an adaptive icon's layers clipped to the outline. Other icons,
     * and the system shape, are drawn as they are. Work apps get their badge back.
     */
    fun render(context: Context, icon: Drawable, shape: String, sizePx: Int, user: UserHandle?, isWork: Boolean): Bitmap {
        val outline = if (icon is AdaptiveIconDrawable) path(shape, sizePx.toFloat()) else null
        val bitmap = if (outline == null) {
            icon.toBitmap(sizePx, sizePx)
        } else {
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { out ->
                val canvas = Canvas(out)
                canvas.clipPath(outline)
                val inset = sizePx / 4
                val adaptive = icon as AdaptiveIconDrawable
                adaptive.background?.let { it.setBounds(-inset, -inset, sizePx + inset, sizePx + inset); it.draw(canvas) }
                adaptive.foreground?.let { it.setBounds(-inset, -inset, sizePx + inset, sizePx + inset); it.draw(canvas) }
            }
        }
        if (!isWork || user == null) return bitmap
        return try {
            context.packageManager.getUserBadgedIcon(BitmapDrawable(context.resources, bitmap), user).toBitmap(sizePx, sizePx)
        } catch (e: Exception) {
            bitmap
        }
    }
}
