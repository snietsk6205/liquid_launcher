package dev.liquid.launcher.core.data

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Material You's colours from a seed, worked out the way Android works them out (0.11.0), so the launcher's guess of
 * the system's icon colours is the same as what the phone reports several seconds later (on Med's phone MagicOS took
 * 7 to 12 seconds after each wallpaper change, and the old guess, pale and deep tones by HSL, looked different).
 *
 * Android 13 and later build the "tonal spot" scheme: accent 1 is the seed's hue at chroma 36, neutral 1 its hue at
 * chroma 6; a shade like system_accent1_100 is tone 90, _700 tone 30, _800 tone 20. Hue, chroma and tone are CAM16 and
 * L* (HCT). The colour science below is a port of Google's material-color-utilities (Apache License 2.0): CAM16 in the
 * default viewing conditions, and the search for the colour of a hue, chroma and tone that Android 12's framework used.
 */
object MonetTones {
    /** Themed icons' colours for "System colours", as [ThemedColors] reads them from the system: plate and glyph. */
    fun iconColours(seed: Int, dark: Boolean): Pair<Int, Int> {
        val cam = Cam16.fromArgb(seed)
        val accent = { tone: Double -> fromHct(cam.hue, ACCENT1_CHROMA, tone) }
        val neutral = { tone: Double -> fromHct(cam.hue, NEUTRAL1_CHROMA, tone) }
        return if (dark) neutral(20.0) to accent(90.0) else accent(90.0) to accent(30.0)
    }

    /** Hue (degrees), chroma and tone (L*) of [argb]. */
    fun hct(argb: Int): Triple<Double, Double, Double> {
        val cam = Cam16.fromArgb(argb)
        return Triple(cam.hue, cam.chroma, lstarFromArgb(argb))
    }

    /** The colour of [hue], as much of [chroma] as fits, and exactly [tone]. */
    fun fromHct(hue: Double, chroma: Double, tone: Double): Int {
        if (chroma < 1.0 || tone.roundToInt() <= 0 || tone.roundToInt() >= 100) return argbFromLstar(tone)
        val h = ((hue % 360.0) + 360.0) % 360.0
        var high = chroma
        var mid = chroma
        var low = 0.0
        var first = true
        var answer: Cam16? = null
        while (abs(low - high) >= CHROMA_SEARCH_ENDPOINT) {
            val possible = findCamByJ(h, mid, tone)
            if (first) {
                if (possible != null) return possible.viewed()
                first = false
                mid = low + (high - low) / 2.0
                continue
            }
            if (possible == null) {
                high = mid
            } else {
                answer = possible
                low = mid
            }
            mid = low + (high - low) / 2.0
        }
        return answer?.viewed() ?: argbFromLstar(tone)
    }

    /** A CAM16 colour of [hue] and [chroma] whose clipped sRGB colour has tone [tone], or null. */
    private fun findCamByJ(hue: Double, chroma: Double, tone: Double): Cam16? {
        var low = 0.0
        var high = 100.0
        var bestDl = 1000.0
        var bestDe = 1000.0
        var best: Cam16? = null
        while (abs(low - high) > LIGHTNESS_SEARCH_ENDPOINT) {
            val mid = low + (high - low) / 2.0
            val clipped = Cam16.fromJch(mid, chroma, hue).viewed()
            val clippedLstar = lstarFromArgb(clipped)
            val dL = abs(tone - clippedLstar)
            if (dL < DL_MAX) {
                val cam = Cam16.fromArgb(clipped)
                val dE = cam.distance(Cam16.fromJch(cam.j, cam.chroma, hue))
                if (dE <= DE_MAX && dE <= bestDe) {
                    bestDl = dL
                    bestDe = dE
                    best = cam
                }
            }
            if (bestDl == 0.0 && bestDe == 0.0) break
            if (clippedLstar < tone) low = mid else high = mid
        }
        return best
    }

    private const val ACCENT1_CHROMA = 36.0
    private const val NEUTRAL1_CHROMA = 6.0
    private const val CHROMA_SEARCH_ENDPOINT = 0.4
    private const val DE_MAX = 1.0
    private const val DL_MAX = 0.2
    private const val LIGHTNESS_SEARCH_ENDPOINT = 0.01

    // ---- sRGB, XYZ and L* ----

    private fun linearized(component: Int): Double {
        val n = component / 255.0
        return if (n <= 0.040449936) n / 12.92 * 100.0 else ((n + 0.055) / 1.055).pow(2.4) * 100.0
    }

    private fun delinearized(component: Double): Int {
        val n = component / 100.0
        val d = if (n <= 0.0031308) n * 12.92 else 1.055 * n.pow(1.0 / 2.4) - 0.055
        return (d * 255.0).roundToInt().coerceIn(0, 255)
    }

    private fun argbFromLinear(r: Double, g: Double, b: Double): Int =
        (0xFF shl 24) or (delinearized(r) shl 16) or (delinearized(g) shl 8) or delinearized(b)

    internal fun argbFromXyz(x: Double, y: Double, z: Double): Int = argbFromLinear(
        3.2413774792388685 * x - 1.5376652402851851 * y - 0.49885366846268053 * z,
        -0.9691452513005321 * x + 1.8758853451067872 * y + 0.04156585616912061 * z,
        0.05562093689691305 * x - 0.20395524564742123 * y + 1.0571799111220335 * z,
    )

    private fun yFromArgb(argb: Int): Double =
        0.2126 * linearized((argb shr 16) and 0xFF) + 0.7152 * linearized((argb shr 8) and 0xFF) + 0.0722 * linearized(argb and 0xFF)

    private fun labF(t: Double): Double = if (t > EPSILON) cbrt(t) else (KAPPA * t + 16.0) / 116.0

    private fun labInvf(ft: Double): Double {
        val ft3 = ft * ft * ft
        return if (ft3 > EPSILON) ft3 else (116.0 * ft - 16.0) / KAPPA
    }

    fun lstarFromArgb(argb: Int): Double = 116.0 * labF(yFromArgb(argb) / 100.0) - 16.0

    internal fun yFromLstar(lstar: Double): Double = 100.0 * labInvf((lstar + 16.0) / 116.0)

    private fun argbFromLstar(lstar: Double): Int {
        val c = delinearized(yFromLstar(lstar))
        return (0xFF shl 24) or (c shl 16) or (c shl 8) or c
    }

    private const val EPSILON = 216.0 / 24389.0
    private const val KAPPA = 24389.0 / 27.0
}

/** CAM16 in the default viewing conditions (an average surround, a mid-grey background, D65 white). */
internal class Cam16 private constructor(
    val hue: Double,
    val chroma: Double,
    val j: Double,
    private val jstar: Double,
    private val astar: Double,
    private val bstar: Double,
) {
    /** How different two colours look (CAM16-UCS). */
    fun distance(other: Cam16): Double {
        val dJ = jstar - other.jstar
        val dA = astar - other.astar
        val dB = bstar - other.bstar
        return 1.41 * sqrt(dJ * dJ + dA * dA + dB * dB).pow(0.63)
    }

    /** This colour in sRGB (clipped to it). */
    fun viewed(): Int {
        val vc = Viewing
        val alpha = if (chroma == 0.0 || j == 0.0) 0.0 else chroma / sqrt(j / 100.0)
        val t = (alpha / (1.64 - 0.29.pow(vc.n)).pow(0.73)).pow(1.0 / 0.9)
        val hRad = Math.toRadians(hue)
        val eHue = 0.25 * (cos(hRad + 2.0) + 3.8)
        val ac = vc.aw * (j / 100.0).pow(1.0 / vc.c / vc.z)
        val p1 = eHue * (50000.0 / 13.0) * vc.nc * vc.ncb
        val p2 = ac / vc.nbb
        val hSin = sin(hRad)
        val hCos = cos(hRad)
        val gamma = 23.0 * (p2 + 0.305) * t / (23.0 * p1 + 11.0 * t * hCos + 108.0 * t * hSin)
        val a = gamma * hCos
        val b = gamma * hSin
        val rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0
        val gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0
        val bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0
        fun unadapt(c: Double): Double {
            val base = max(0.0, 27.13 * abs(c) / (400.0 - abs(c)))
            return sign(c) * (100.0 / vc.fl) * base.pow(1.0 / 0.42)
        }
        val rF = unadapt(rA) / vc.rgbD[0]
        val gF = unadapt(gA) / vc.rgbD[1]
        val bF = unadapt(bA) / vc.rgbD[2]
        val x = 1.86206786 * rF - 1.01125463 * gF + 0.14918677 * bF
        val y = 0.38752654 * rF + 0.62144744 * gF - 0.00897398 * bF
        val z = -0.01584150 * rF - 0.03412294 * gF + 1.04996444 * bF
        return MonetTones.argbFromXyz(x, y, z)
    }

    companion object {
        fun fromArgb(argb: Int): Cam16 {
            val vc = Viewing
            fun lin(c: Int): Double {
                val n = c / 255.0
                return if (n <= 0.040449936) n / 12.92 * 100.0 else ((n + 0.055) / 1.055).pow(2.4) * 100.0
            }
            val red = lin((argb shr 16) and 0xFF)
            val green = lin((argb shr 8) and 0xFF)
            val blue = lin(argb and 0xFF)
            val x = 0.41233895 * red + 0.35762064 * green + 0.18051042 * blue
            val y = 0.2126 * red + 0.7152 * green + 0.0722 * blue
            val z = 0.01932141 * red + 0.11916382 * green + 0.95034478 * blue
            val rC = 0.401288 * x + 0.650173 * y - 0.051461 * z
            val gC = -0.250268 * x + 1.204414 * y + 0.045854 * z
            val bC = -0.002079 * x + 0.048952 * y + 0.953127 * z
            fun adapt(c: Double, d: Double): Double {
                val f = (vc.fl * abs(d * c) / 100.0).pow(0.42)
                return sign(d * c) * 400.0 * f / (f + 27.13)
            }
            val rA = adapt(rC, vc.rgbD[0])
            val gA = adapt(gC, vc.rgbD[1])
            val bA = adapt(bC, vc.rgbD[2])
            val a = (11.0 * rA + -12.0 * gA + bA) / 11.0
            val b = (rA + gA - 2.0 * bA) / 9.0
            val u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0
            val p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0
            val degrees = Math.toDegrees(atan2(b, a))
            val hue = when {
                degrees < 0 -> degrees + 360.0
                degrees >= 360 -> degrees - 360.0
                else -> degrees
            }
            val ac = p2 * vc.nbb
            val j = 100.0 * (ac / vc.aw).pow(vc.c * vc.z)
            val huePrime = if (hue < 20.14) hue + 360 else hue
            val eHue = 0.25 * (cos(Math.toRadians(huePrime) + 2.0) + 3.8)
            val p1 = 50000.0 / 13.0 * eHue * vc.nc * vc.ncb
            val t = p1 * hypot(a, b) / (u + 0.305)
            val alpha = (1.64 - 0.29.pow(vc.n)).pow(0.73) * t.pow(0.9)
            val chroma = alpha * sqrt(j / 100.0)
            return fromJch(j, chroma, hue)
        }

        fun fromJch(j: Double, chroma: Double, hue: Double): Cam16 {
            val vc = Viewing
            val m = chroma * vc.fLRoot
            val hRad = Math.toRadians(hue)
            val jstar = (1.0 + 100.0 * 0.007) * j / (1.0 + 0.007 * j)
            val mstar = 1.0 / 0.0228 * ln1p(0.0228 * m)
            return Cam16(hue, chroma, j, jstar, mstar * cos(hRad), mstar * sin(hRad))
        }
    }
}

/** CAM16's default viewing conditions, as material-color-utilities and Android's framework make them. */
private object Viewing {
    val n: Double
    val aw: Double
    val nbb: Double
    val ncb: Double
    val c: Double
    val nc: Double
    val rgbD: DoubleArray
    val fl: Double
    val fLRoot: Double
    val z: Double

    init {
        val white = doubleArrayOf(95.047, 100.0, 108.883)
        val adaptingLuminance = 200.0 / Math.PI * MonetTones.yFromLstar(50.0) / 100.0
        val backgroundLstar = 50.0
        val surround = 2.0
        val rW = white[0] * 0.401288 + white[1] * 0.650173 + white[2] * -0.051461
        val gW = white[0] * -0.250268 + white[1] * 1.204414 + white[2] * 0.045854
        val bW = white[0] * -0.002079 + white[1] * 0.048952 + white[2] * 0.953127
        val f = 0.8 + surround / 10.0
        c = if (f >= 0.9) lerp(0.59, 0.69, (f - 0.9) * 10.0) else lerp(0.525, 0.59, (f - 0.8) * 10.0)
        var d = f * (1.0 - 1.0 / 3.6 * exp((-adaptingLuminance - 42.0) / 92.0))
        d = min(1.0, max(0.0, d))
        nc = f
        rgbD = doubleArrayOf(d * (100.0 / rW) + 1.0 - d, d * (100.0 / gW) + 1.0 - d, d * (100.0 / bW) + 1.0 - d)
        val k = 1.0 / (5.0 * adaptingLuminance + 1.0)
        val k4 = k * k * k * k
        val k4F = 1.0 - k4
        fl = k4 * adaptingLuminance + 0.1 * k4F * k4F * cbrt(5.0 * adaptingLuminance)
        n = MonetTones.yFromLstar(backgroundLstar) / white[1]
        z = 1.48 + sqrt(n)
        nbb = 0.725 / n.pow(0.2)
        ncb = nbb
        val factors = doubleArrayOf(
            (fl * rgbD[0] * rW / 100.0).pow(0.42),
            (fl * rgbD[1] * gW / 100.0).pow(0.42),
            (fl * rgbD[2] * bW / 100.0).pow(0.42),
        )
        val rgbA = DoubleArray(3) { 400.0 * factors[it] / (factors[it] + 27.13) }
        aw = (2.0 * rgbA[0] + rgbA[1] + 0.05 * rgbA[2]) * nbb
        fLRoot = fl.pow(0.25)
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
}
