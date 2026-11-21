@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.core.graphics

object ColorUtils {
    @JvmStatic fun calculateLuminance(color: Int): Double = error("stub")
    @JvmStatic fun calculateContrast(foreground: Int, background: Int): Double = error("stub")
    @JvmStatic fun colorToHSL(color: Int, outHsl: FloatArray) {}
    @JvmStatic fun HSLToColor(hsl: FloatArray): Int = error("stub")
    @JvmStatic fun blendARGB(color1: Int, color2: Int, ratio: Float): Int = error("stub")
    @JvmStatic fun setAlphaComponent(color: Int, alpha: Int): Int = error("stub")
    @JvmStatic fun compositeColors(foreground: Int, background: Int): Int = error("stub")
}
