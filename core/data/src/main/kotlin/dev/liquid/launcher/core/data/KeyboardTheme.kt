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
    /**
     * 0.13.0 (D177): the keys, buttons and popups the launcher gives the keyboard (Med liked them); everything else —
     * the board, the toolbar, the chips, the frost — comes from the base theme (his own file by default).
     */
    val KEY_FIELDS = listOf(
        "keyShape", "keyTexture", "keyTextureModifier", "keyTextureEnter", "keyTextureSpace", "keyTexturePressed",
        "keyTextureScale", "keyTextureOpacity", "keyGradient", "keyBackground", "keyText", "modifierKeyBackground",
        "modifierKeyText", "hintText", "enterKeyBackground", "enterKeyText", "pressedKeyBackground", "keyBorderColor",
        "keyBorderWidthDp", "keyElevationDp", "keyShadowColor", "keyCornerRadiusDp", "modifierKeyCornerRadiusDp",
        "actionKeyCornerRadiusDp", "keyMaterial", "keyLighting", "keySkin", "touchBloom", "keyEffect", "keyEffectParam",
        "keyEffectIntensity", "keyEffectImages", "keyEffectColor", "keyEffectCustomColor", "keyEffectSize",
        "keyEffectSpeed", "keyEffectSpread", "keyEffectGravity", "keyEffectDurationMs", "popupBackground", "popupText",
        "popupSelectedBackground", "popupSelectedText", "accent",
    )

    /** [doc] with the colours that follow the wallpaper written out as the launcher shows them with [palette]. */
    fun withFollowedWritten(doc: ThemeDoc, palette: WallpaperPalette?): ThemeDoc {
        if (palette == null) return doc
        var d = doc
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
        return d
    }

    /**
     * 0.13.0 (D177): the keyboard theme the launcher hands to WM Keyboard: [base] (Med's own theme file unless he chose
     * another) with the launcher's keys and popups ([KEY_FIELDS], from [launcher] with its wallpaper colours written
     * out), no picture of its own (WM Keyboard stands its glass on its own band of the wallpaper, so the toolbar stays
     * crisp), and "Follow wallpaper colors" on: WM Keyboard recolours it from the wallpaper by itself at every change,
     * so it is imported once.
     */
    fun build(base: ThemeDoc, launcher: ThemeDoc, palette: WallpaperPalette?, name: String, idMillis: Long): ThemeDoc {
        val keys = withFollowedWritten(launcher, palette)
        var d = base
        for (field in KEY_FIELDS) {
            val element = keys.element(field) ?: continue
            d = d.with(field, element)
        }
        return d.withString("id", "custom_$idMillis").withString("name", name)
            .withString("backgroundImage", null).withString("backgroundImageBase64", null)
            .withString("backgroundImageLandscape", null).withString("backgroundImageLandscapeBase64", null)
            .with("backgroundPhoto", null)
            .withBool("followWallpaper", true)
    }

    /**
     * 0.13.1 (D180): [doc] (a theme from [build]) standing on the picture at [picturePath], a file WM Keyboard reads by its
     * path (it keeps a path it is given and re-reads the file when it changes; its cache is keyed on the file's time).
     * The picture carries the look (the launcher blurs and dims it), so WM Keyboard shows it as it is: no blur of its
     * own, full opacity, and a see-through board, whose colour would otherwise be laid over the picture (WM Keyboard
     * draws the board's colour on top of a picture, as a scrim). The keys, frost and colours that follow the wallpaper
     * stay the theme's.
     */
    fun withPicture(doc: ThemeDoc, picturePath: String): ThemeDoc {
        val board = doc.color("boardBackground") ?: 0xFF15171AL
        return doc.withString("backgroundImage", picturePath)
            .withString("backgroundImageBase64", null)
            .withString("backgroundImageLandscape", null).withString("backgroundImageLandscapeBase64", null)
            .withFloat("backgroundImageOpacity", 1f)
            .withFloat("backgroundImageBlur", 0f)
            .withBool("backgroundAnimated", false)
            .with("backgroundPhoto", null)
            .with("boardGradient", null)
            .withColor("boardBackground", board and 0x00FFFFFFL)
    }

    /** 0.12.0's build: [doc] alone, its followed colours written out, optionally with a picture (kept for files made before). */
    fun build(
        doc: ThemeDoc,
        palette: WallpaperPalette?,
        name: String,
        idMillis: Long,
        wallpaperJpegBase64: String? = null,
        wallpaperBlur: Float = 3f,
    ): ThemeDoc {
        var d = withFollowedWritten(doc, palette)
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
