package dev.liquid.launcher.core.common

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The wallpaper's colours by role (W3, 0.10.0), for theme colours set to follow the wallpaper: the one that covers
 * most of it (dominant), the most vivid, a quiet one (muted), a dark and a light one. ARGB, opaque.
 */
data class WallpaperPalette(val dominant: Int, val vibrant: Int, val muted: Int, val dark: Int, val light: Int) {

    fun of(role: String): Int = when (role) {
        "vibrant" -> vibrant
        "muted" -> muted
        "dark" -> dark
        "light" -> light
        else -> dominant
    }

    fun encode(): String = listOf(dominant, vibrant, muted, dark, light).joinToString(",") { Integer.toHexString(it) }

    companion object {
        fun decode(text: String?): WallpaperPalette? {
            val parts = text?.split(',')?.mapNotNull { it.trim().toLongOrNull(16)?.toInt() } ?: return null
            if (parts.size != 5) return null
            return WallpaperPalette(parts[0], parts[1], parts[2], parts[3], parts[4])
        }

        /**
         * From the pixels of a small copy of the wallpaper (a few thousand are plenty). Colours are counted in boxes of
         * 16 levels per channel; each role picks the box that suits it best, weighted by how much of the picture it covers.
         */
        fun extract(pixels: IntArray): WallpaperPalette? {
            val counts = HashMap<Int, IntArray>()
            for (p in pixels) {
                if ((p ushr 24) < 0x80) continue
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val bin = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                val acc = counts.getOrPut(bin) { IntArray(4) }
                acc[0]++
                acc[1] += r
                acc[2] += g
                acc[3] += b
            }
            if (counts.isEmpty()) return null
            val total = counts.values.sumOf { it[0] }.toFloat()
            class Box(val color: Int, val share: Float, val hsl: FloatArray)
            val boxes = counts.values.map { a ->
                val c = (0xFF shl 24) or ((a[1] / a[0]) shl 16) or ((a[2] / a[0]) shl 8) or (a[3] / a[0])
                Box(c, a[0] / total, hsl(c))
            }
            val dominant = boxes.maxByOrNull { it.share }!!.color
            fun pick(score: (Box) -> Float): Int? = boxes.map { it to score(it) }.filter { it.second > 0f }.maxByOrNull { it.second }?.first?.color
            val vibrant = pick { b ->
                val (_, s, l) = b.hsl
                if (l !in 0.25f..0.85f || s < 0.3f) 0f else s * (1f - abs(l - 0.55f) * 1.4f) * sqrt(b.share)
            } ?: withHsl(dominant, s = 0.7f, l = 0.55f)
            val muted = pick { b ->
                val (_, s, l) = b.hsl
                if (l !in 0.25f..0.75f || s > 0.45f) 0f else (1f - abs(s - 0.22f) * 2f).coerceAtLeast(0.05f) * sqrt(b.share)
            } ?: withHsl(dominant, s = 0.22f, l = 0.5f)
            val dark = pick { b -> if (b.hsl[2] > 0.28f) 0f else (0.3f - b.hsl[2] + 0.05f) * sqrt(b.share) } ?: withHsl(dominant, l = 0.16f)
            val light = pick { b -> if (b.hsl[2] < 0.72f) 0f else (b.hsl[2] - 0.65f) * sqrt(b.share) } ?: withHsl(dominant, l = 0.86f)
            return WallpaperPalette(dominant, vibrant, muted, dark, light)
        }

        /** Without the picture (another app's wallpaper): the system's main colour spread into roles. */
        fun fromSeed(primary: Int): WallpaperPalette = WallpaperPalette(
            dominant = primary or (0xFF shl 24),
            vibrant = withHsl(primary, s = max(0.6f, hsl(primary)[1]), l = 0.55f),
            muted = withHsl(primary, s = 0.22f, l = 0.5f),
            dark = withHsl(primary, l = 0.16f),
            light = withHsl(primary, l = 0.86f),
        )

        /** Hue 0..360, saturation and lightness 0..1. */
        fun hsl(c: Int): FloatArray {
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val l = (mx + mn) / 2f
            if (mx == mn) return floatArrayOf(0f, 0f, l)
            val d = mx - mn
            val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
            var h = when (mx) {
                r -> (g - b) / d + (if (g < b) 6f else 0f)
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            }
            h *= 60f
            return floatArrayOf(h, s, l)
        }

        /** [c] with its saturation and/or lightness replaced. */
        fun withHsl(c: Int, s: Float? = null, l: Float? = null): Int {
            val (h, s0, l0) = hsl(c)
            return fromHsl(h, (s ?: s0).coerceIn(0f, 1f), (l ?: l0).coerceIn(0f, 1f))
        }

        fun fromHsl(h: Float, s: Float, l: Float): Int {
            val c = (1f - abs(2f * l - 1f)) * s
            val x = c * (1f - abs((h / 60f) % 2f - 1f))
            val m = l - c / 2f
            val (r, g, b) = when {
                h < 60f -> Triple(c, x, 0f)
                h < 120f -> Triple(x, c, 0f)
                h < 180f -> Triple(0f, c, x)
                h < 240f -> Triple(0f, x, c)
                h < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            fun ch(v: Float) = ((v + m).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
        }
    }
}
