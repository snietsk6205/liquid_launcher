package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.common.WallpaperPalette
import kotlinx.serialization.json.JsonArray

/**
 * 0.12.0 (D172): a WM Keyboard theme in the launcher's colours and glass as they are now, to open in WM Keyboard, save
 * or share. The launcher's theme already is a WM Keyboard theme ([ThemeDoc]); what WM Keyboard cannot read is which
 * colours follow the wallpaper (the launcher's own block, which it skips), so those colours are written out as the
 * launcher shows them with this wallpaper. The keys, frost (the keyboard's glass), material and effects stay as the
 * theme has them. With [wallpaperJpegBase64], the launcher's wallpaper becomes the keyboard's background picture,
 * blurred by [wallpaperBlur] (WM Keyboard's backgroundImageBlur), so the keyboard sits in the same picture.
 */
object KeyboardTheme {
    fun build(
        doc: ThemeDoc,
        palette: WallpaperPalette?,
        name: String,
        idMillis: Long,
        wallpaperJpegBase64: String? = null,
        wallpaperBlur: Float = 3f,
    ): ThemeDoc {
        var d = doc
        if (palette != null) {
            for ((path, role) in doc.followed) {
                val element = doc.element(path)
                if (element is JsonArray) {
                    val list = doc.colors(path)
                    val tones = listOf(palette.of(role), palette.vibrant, palette.muted, palette.light).distinct()
                        .take(maxOf(1, list.size)).map { it.toLong() and 0xFFFFFFFFL }
                    d = d.withColors(path, tones)
                } else {
                    val stored = doc.color(path)
                    val alpha = ((stored ?: 0xFF000000L) ushr 24) and 0xFFL
                    d = d.withColor(path, (alpha shl 24) or (palette.of(role).toLong() and 0x00FFFFFFL))
                }
            }
        }
        d = d.withString("id", "custom_$idMillis").withString("name", name)
        if (wallpaperJpegBase64 != null) {
            d = d.withString("backgroundImage", null)
                .withString("backgroundImageBase64", wallpaperJpegBase64)
                .withFloat("backgroundImageOpacity", 1f)
                .withFloat("backgroundImageBlur", wallpaperBlur.coerceIn(0f, 25f))
                .with("backgroundPhoto", null)
                .withBool("backgroundAnimated", false)
        }
        return d
    }

    /** A theme a file manager and WM Keyboard both recognise: its name ends in .wmtheme.json. */
    fun fileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 _-]"), " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .joinToString("-").ifBlank { "liquid" } + ".wmtheme.json"

    /** WM Keyboard claims a theme without its file name only when it has both of these (its File formats page). */
    fun recognisable(doc: ThemeDoc): Boolean = doc.element("boardBackground") != null && doc.element("keyBackground") != null

    /** A colour field as an ARGB int for drawing a preview (the theme's value, or [fallback]). */
    fun argb(doc: ThemeDoc, path: String, fallback: Long): Int = (doc.color(path) ?: fallback).toInt()
}
