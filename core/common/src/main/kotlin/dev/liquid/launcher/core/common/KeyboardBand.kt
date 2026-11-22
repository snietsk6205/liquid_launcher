package dev.liquid.launcher.core.common

import kotlin.math.roundToInt

/**
 * 0.13.1 (D180): the part of the launcher's wallpaper that lies behind the keyboard, handed to WM Keyboard as its
 * background (it cannot read the wallpaper's picture itself on Android 13 and later). The launcher's wallpaper is a
 * canvas as tall as the screen and as wide or wider (parallax); the screen shows a window of it, and the keyboard covers
 * that window's bottom rows.
 */
object KeyboardBand {
    /** A rectangle in canvas pixels. */
    data class Rect(val left: Int, val top: Int, val width: Int, val height: Int)

    /**
     * The rows of a [canvasW] x [canvasH] wallpaper behind a keyboard [keyboardPx] tall on a [screenW] x [screenH]
     * screen. The canvas is scaled to the screen's height; across a canvas wider than the screen the window is its
     * middle (the pages slide it, the keyboard keeps one picture). Null when nothing sensible can be cut.
     */
    fun rect(canvasW: Int, canvasH: Int, screenW: Int, screenH: Int, keyboardPx: Int): Rect? {
        if (canvasW <= 0 || canvasH <= 0 || screenW <= 0 || screenH <= 0 || keyboardPx <= 0) return null
        val scale = canvasH.toFloat() / screenH
        val width = (screenW * scale).roundToInt().coerceIn(1, canvasW)
        val height = (keyboardPx.coerceAtMost(screenH) * scale).roundToInt().coerceIn(1, canvasH)
        return Rect((canvasW - width) / 2, canvasH - height, width, height)
    }

    /** How dark the picture is made for [look] ("search": search's own dim, [searchDimPercent]; "home": a little). */
    fun dim(look: String, searchDimPercent: Int): Float = when (look) {
        "home" -> HOME_DIM
        else -> (searchDimPercent / 100f).coerceIn(0f, 0.9f)
    }

    /** How far the picture is blurred for [look], in px ("search": search's own blur, [searchBlurPx]; "home": not at all). */
    fun blur(look: String, searchBlurPx: Float): Float = when (look) {
        "home" -> 0f
        else -> searchBlurPx.coerceAtLeast(0f)
    }

    /** The sharp picture is darkened this much, so the keys' letters stay readable on a bright wallpaper. */
    const val HOME_DIM = 0.22f
}
