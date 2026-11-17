package dev.liquid.launcher.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * The launcher's theme (P4, 0.9.13): how buttons, the main button, chips and round tool buttons look, and the glass
 * under them. The model follows WM Keyboard's themes (MIT, github.com/wasi-master/wmkeyboard: ThemeSpec, FrostSpec
 * and how KbTheme derives the colours a theme leaves out), reduced to the surfaces a launcher has: keys become
 * buttons, the enter key the main button, chips stay chips, toolbar circles become round icon buttons. Colours are
 * ARGB like WM Keyboard's, so alpha is first-class: a translucent fill over glass reads as frosted.
 */

/** A multi-stop gradient laid over a fill, as WM Keyboard's GradientSpec: ARGB stops, LINEAR / RADIAL / SWEEP. */
@Serializable
data class ThemeGradient(val colors: List<Long> = emptyList(), val type: String = "LINEAR", val angleDeg: Float = 45f)

/** One kind of control, at rest, pressed and switched on. */
@Serializable
data class ControlTheme(
    val fill: Long,
    val text: Long,
    val pressed: Long,
    /** The text while pressed; null keeps [text]. */
    val pressedText: Long? = null,
    /** Switched on or selected (a chip that is on, the current choice). */
    val active: Long,
    val activeText: Long,
    /** A sheen over the fill, or null. */
    val gradient: ThemeGradient? = null,
    val border: Long? = null,
    val borderWidthDp: Float = 0f,
    /** A soft coloured light around the control, its radius in dp; 0 for none. */
    val glowDp: Float = 0f,
    /** The glow's colour; null uses the accent. */
    val glowColor: Long? = null,
    /** A shadow under the control, in dp of elevation; 0 for none. */
    val shadowDp: Float = 0f,
    val shadowColor: Long? = null,
    /** PILL, ROUNDED, SQUIRCLE, CUT, SHARP or CIRCLE. */
    val shape: String = "PILL",
    /** Corner radius of ROUNDED, SQUIRCLE and CUT. */
    val cornerDp: Int = 12,
    /** The glass ([LiquidTheme.frost]) under the fill; off draws the fill alone. */
    val glass: Boolean = true,
)

/** The glass under the controls, as WM Keyboard's frost. */
@Serializable
data class ThemeFrost(
    /** FROSTED: a blurred pane; LIQUID: the edge also bends, splits colours and catches the light. */
    val style: String = "FROSTED",
    val blurDp: Float = 14f,
    val saturation: Float = 1.25f,
    val brightness: Float = 0f,
    /** The rim of light, 0..1: light on the edge facing the light, dark on the other. */
    val rim: Float = 0.5f,
    val rimColor: Long? = null,
    /** Grain over the pane, 0..0.25 as in WM Keyboard. */
    val grain: Float = 0.06f,
    /** LIQUID only: how far the edge bends, 0..1, and how far colours split there, 0..1. */
    val refraction: Float = 0.6f,
    val dispersion: Float = 0.25f,
) {
    val liquid: Boolean get() = style.equals("LIQUID", ignoreCase = true)
}

@Serializable
data class LiquidTheme(
    /** The format, so a later launcher can read an older file. */
    val liquidTheme: Int = 1,
    val name: String,
    val dark: Boolean = true,
    /** The launcher's accent while this theme is applied (written to Settings → Theme → Accent). */
    val accent: Long,
    /** The colour glass surfaces are tinted with (written to Settings → Theme → Glass tint colour). */
    val glassTint: Long,
    val button: ControlTheme,
    val main: ControlTheme,
    val chip: ControlTheme,
    val tool: ControlTheme,
    val frost: ThemeFrost = ThemeFrost(),
    /** Where it came from, e.g. "WM Keyboard: wallpaper stoped". */
    val source: String? = null,
)

object LiquidThemes {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** Your WM Keyboard theme ("nice liquid", docs/reference/nice_liquid.wmtheme.json), imported: the default. */
    val Keyboard: LiquidTheme = LiquidTheme(
        name = "Nice liquid",
        dark = true,
        accent = 0xFFD182AF,
        glassTint = 0xFF3C323E,
        button = ControlTheme(
            fill = 0x1C3C323E, text = 0xFFE9E9EE, pressed = 0xFF723F61,
            active = 0xFF723F61, activeText = 0xFFE9E9EE, shape = "PILL", cornerDp = 12,
        ),
        main = ControlTheme(
            fill = 0x1C3C323E, text = 0xFFE9E9EE, pressed = 0xFF723F61,
            active = 0xFF723F61, activeText = 0xFFE9E9EE, shape = "PILL", cornerDp = 12,
        ),
        chip = ControlTheme(
            fill = 0xFF322B35, text = 0xFFE9E9EE, pressed = 0xFF723F61,
            active = 0xFF723F61, activeText = 0xFFE9E9EE, shape = "ROUNDED", cornerDp = 12,
        ),
        tool = ControlTheme(
            fill = 0x433D323F, text = 0xA6E9E9EE, pressed = 0xFF723F61,
            active = 0xFF723F61, activeText = 0xFF838385, shape = "PILL", cornerDp = 12,
        ),
        frost = ThemeFrost(style = "FROSTED", blurDp = 9f, saturation = 1.25f, brightness = 0f, rim = 0.86f, grain = 0f, refraction = 0.6f, dispersion = 0.25f),
        source = "WM Keyboard: wallpaper stoped",
    )

    fun encode(theme: LiquidTheme): String = json.encodeToString(LiquidTheme.serializer(), theme)

    /**
     * A theme from [text]: the launcher's own format, or a WM Keyboard theme or backup (recognised by its
     * `keyBackground`). Null when it is neither. [fallbackName] names a WM theme that has no name.
     */
    fun decode(text: String, fallbackName: String = "Imported theme"): LiquidTheme? {
        val root = runCatching { json.parseToJsonElement(text.trim()) }.getOrNull() ?: return null
        val obj = when (root) {
            is JsonObject -> root
            // A WM Keyboard export of several themes: the first.
            is JsonArray -> root.firstOrNull() as? JsonObject
            else -> null
        } ?: return null
        return when {
            obj.containsKey("liquidTheme") -> runCatching { json.decodeFromJsonElement(LiquidTheme.serializer(), obj) }.getOrNull()
            obj.containsKey("keyBackground") -> fromWmKeyboard(obj, fallbackName)
            // A WM Keyboard backup that wraps its themes.
            obj["themes"] is JsonArray -> ((obj["themes"] as JsonArray).firstOrNull() as? JsonObject)?.let { fromWmKeyboard(it, fallbackName) }
            obj["customThemes"] is JsonArray -> ((obj["customThemes"] as JsonArray).firstOrNull() as? JsonObject)?.let { fromWmKeyboard(it, fallbackName) }
            else -> null
        }
    }

    /**
     * A WM Keyboard theme as a launcher theme, deriving what it leaves out the way WM Keyboard does (KbTheme): the
     * pressed key from the key and its text, the active chip from the active tool circle, then the pressed key,
     * texts that must read on their fill from the first legible candidate.
     */
    fun fromWmKeyboard(spec: JsonObject, fallbackName: String = "Imported theme"): LiquidTheme {
        fun long(key: String): Long? = (spec[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
        fun float(key: String): Float? = (spec[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull
        fun int(key: String): Int? = (spec[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
        fun string(key: String): String? = (spec[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content
        fun bool(key: String): Boolean? = (spec[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull
        fun gradient(key: String): ThemeGradient? {
            val g = spec[key] as? JsonObject ?: return null
            val colors = (g["colors"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }.orEmpty()
            if (colors.size < 2) return null
            return ThemeGradient(
                colors,
                (g["type"] as? JsonPrimitive)?.content ?: "LINEAR",
                (g["angleDeg"] as? JsonPrimitive)?.floatOrNull ?: 45f,
            )
        }

        val dark = bool("dark") ?: true
        val board = long("boardBackground") ?: 0xFF17181C
        val key = long("keyBackground") ?: 0xFF303338
        val keyText = long("keyText") ?: 0xFFE9E9EE
        val accent = long("accent") ?: 0xFF8AB4F8
        val pressed = long("pressedKeyBackground") ?: ThemeColors.lerp(key, keyText, 0.25f)
        val stripText = long("suggestionText") ?: ThemeColors.legibleOn(board, listOf(keyText))
        val secondary = ThemeColors.withAlpha(stripText, 0.65f)
        val toolActive = long("toolCircleActiveBackground") ?: pressed
        val chipActive = long("chipActiveBackground") ?: toolActive
        val modifier = long("modifierKeyBackground") ?: 0xFF222428
        val enter = long("enterKeyBackground") ?: 0xFF4C8DF6
        val enterText = long("enterKeyText") ?: 0xFF0B1220
        val keyShape = shapeName(string("keyShape"), "ROUNDED", allowCircle = false)
        val keyCorner = int("keyCornerRadiusDp") ?: 12
        val frostObj = spec["frost"] as? JsonObject
        val frostOn = frostObj != null && ((frostObj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true)
        val surfaces = if (!frostOn) emptySet() else
            (frostObj!!["surfaces"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.uppercase() }?.toSet()
                ?: setOf("KEYS", "MODIFIER_KEYS", "SPACE_BAR", "BARS", "TOOLS", "CHIPS", "CARDS", "POPUPS", "MENUS")
        fun frostFloat(name: String, default: Float) = (frostObj?.get(name) as? JsonPrimitive)?.floatOrNull ?: default
        val frost = ThemeFrost(
            style = (frostObj?.get("style") as? JsonPrimitive)?.takeIf { it.isString }?.content?.uppercase() ?: "FROSTED",
            blurDp = frostFloat("backdropBlur", 14f).coerceIn(0f, 25f),
            saturation = frostFloat("saturation", 1.25f).coerceIn(0.5f, 2f),
            brightness = frostFloat("brightness", 0f).coerceIn(-0.3f, 0.3f),
            rim = frostFloat("rim", 0.5f).coerceIn(0f, 1f),
            rimColor = (frostObj?.get("rimColor") as? JsonPrimitive)?.longOrNull,
            grain = frostFloat("grain", 0.06f).coerceIn(0f, 0.25f),
            refraction = frostFloat("refraction", 0.6f).coerceIn(0f, 1f),
            dispersion = frostFloat("dispersion", 0.25f).coerceIn(0f, 1f),
        )
        val button = ControlTheme(
            fill = key, text = keyText, pressed = pressed, pressedText = long("pressedKeyText"),
            active = chipActive, activeText = long("chipActiveText") ?: ThemeColors.legibleOn(chipActive, listOf(accent, keyText)),
            gradient = gradient("keyGradient"), border = long("keyBorderColor"), borderWidthDp = float("keyBorderWidthDp") ?: 0f,
            shadowDp = (float("keyElevationDp") ?: 0f).coerceIn(0f, 8f), shadowColor = long("keyShadowColor"),
            shape = keyShape, cornerDp = keyCorner, glass = "KEYS" in surfaces,
        )
        val main = ControlTheme(
            // WM Keyboard presses every key, the enter key included, in the pressed key colour.
            fill = enter, text = enterText, pressed = pressed, pressedText = long("pressedEnterKeyText"),
            active = chipActive, activeText = button.activeText,
            border = long("keyBorderColor"), borderWidthDp = float("keyBorderWidthDp") ?: 0f,
            shape = keyShape, cornerDp = int("actionKeyCornerRadiusDp") ?: keyCorner, glass = "ENTER_KEY" in surfaces,
        )
        val chip = ControlTheme(
            fill = long("chipBackground") ?: modifier,
            text = long("chipText") ?: long("modifierKeyText") ?: keyText,
            pressed = pressed, active = chipActive,
            activeText = long("chipActiveText") ?: ThemeColors.legibleOn(chipActive, listOf(accent, keyText)),
            border = long("chipBorderColor"), borderWidthDp = float("chipBorderWidthDp") ?: 0f,
            shape = shapeName(string("chipShape"), "ROUNDED", allowCircle = false), cornerDp = int("chipCornerRadiusDp") ?: 12,
            glass = "CHIPS" in surfaces,
        )
        val tool = ControlTheme(
            fill = long("toolCircleBackground") ?: ThemeColors.blendOver(keyText, board, 0.14f),
            text = long("toolbarIcon") ?: secondary,
            pressed = toolActive, active = toolActive,
            activeText = long("toolCircleActiveIcon") ?: ThemeColors.legibleOn(toolActive, listOf(accent, keyText)),
            border = long("toolBorderColor"), borderWidthDp = float("toolBorderWidthDp") ?: 0f,
            shadowDp = (float("toolElevationDp") ?: 0f).coerceIn(0f, 8f),
            shape = shapeName(string("toolShape"), "CIRCLE", allowCircle = true), cornerDp = int("toolCircleRadiusDp") ?: 12,
            glass = "TOOLS" in surfaces,
        )
        val name = string("name")?.takeIf { it.isNotBlank() }
        return LiquidTheme(
            name = name ?: fallbackName,
            dark = dark,
            accent = accent,
            glassTint = ThemeColors.opaque(key),
            button = button,
            main = main,
            chip = chip,
            tool = tool,
            frost = frost,
            source = "WM Keyboard: " + (name ?: "unnamed theme"),
        )
    }

    /**
     * A WM Keyboard shape as one the launcher draws: the four plain outlines and the pill pass through, a circle
     * where allowed (round tool buttons), every decorative one (arch, leaf, slant, hexagon, scallop, ticket) as
     * rounded, as WM Keyboard itself does for containers. NONE (a key without a face) draws as [default].
     */
    fun shapeName(name: String?, default: String, allowCircle: Boolean): String = when (name?.uppercase()) {
        "PILL", "ROUNDED", "SQUIRCLE", "CUT", "SHARP" -> name.uppercase()
        "CIRCLE" -> if (allowCircle) "CIRCLE" else "PILL"
        null, "NONE" -> default
        else -> "ROUNDED"
    }
}

/** The colour arithmetic WM Keyboard's theme resolution uses, on ARGB longs. */
object ThemeColors {
    private fun a(c: Long) = ((c ushr 24) and 0xFF).toFloat() / 255f
    private fun r(c: Long) = ((c ushr 16) and 0xFF).toFloat() / 255f
    private fun g(c: Long) = ((c ushr 8) and 0xFF).toFloat() / 255f
    private fun b(c: Long) = (c and 0xFF).toFloat() / 255f

    private fun argb(a: Float, r: Float, g: Float, b: Float): Long {
        fun ch(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
        return (ch(a) shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    fun opaque(c: Long): Long = c or 0xFF000000

    fun withAlpha(c: Long, alpha: Float): Long = argb(alpha, r(c), g(c), b(c))

    private fun linear(v: Float): Float = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    private fun gamma(v: Float): Float = if (v <= 0.0031308f) v * 12.92f else (1.055f * v.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f)

    /** WCAG relative luminance, as Compose's Color.luminance(). */
    fun luminance(c: Long): Float = 0.2126f * linear(r(c)) + 0.7152f * linear(g(c)) + 0.0722f * linear(b(c))

    fun contrast(x: Long, y: Long): Float {
        val lx = luminance(x) + 0.05f
        val ly = luminance(y) + 0.05f
        return max(lx, ly) / min(lx, ly)
    }

    /** The first of [candidates] that reads on [background] (≥ [minRatio]), else black or white. */
    fun legibleOn(background: Long, candidates: List<Long>, minRatio: Float = 3f): Long =
        candidates.firstOrNull { contrast(it, background) >= minRatio }
            ?: if (luminance(background) > 0.5f) 0xFF000000 else 0xFFFFFFFF

    /** [top] at [alpha] flattened over [base]. */
    fun blendOver(top: Long, base: Long, alpha: Float): Long {
        val ta = alpha.coerceIn(0f, 1f)
        val ba = a(base)
        val outA = ta + ba * (1f - ta)
        if (outA <= 0f) return 0L
        fun mix(t: Float, s: Float) = (t * ta + s * ba * (1f - ta)) / outA
        return argb(outA, mix(r(top), r(base)), mix(g(top), g(base)), mix(b(top), b(base)))
    }

    /** From [x] to [y] by [t], in the Oklab colour space as Compose's lerp, alpha linearly. */
    fun lerp(x: Long, y: Long, t: Float): Long {
        val p = oklab(x)
        val q = oklab(y)
        val l = p[0] + (q[0] - p[0]) * t
        val aa = p[1] + (q[1] - p[1]) * t
        val bb = p[2] + (q[2] - p[2]) * t
        val rgb = fromOklab(l, aa, bb)
        return argb(a(x) + (a(y) - a(x)) * t, rgb[0], rgb[1], rgb[2])
    }

    private fun oklab(c: Long): FloatArray {
        val lr = linear(r(c))
        val lg = linear(g(c))
        val lb = linear(b(c))
        val l = cbrt(0.4122214708f * lr + 0.5363325363f * lg + 0.0514459929f * lb)
        val m = cbrt(0.2119034982f * lr + 0.6806995451f * lg + 0.1073969566f * lb)
        val s = cbrt(0.0883024619f * lr + 0.2817188376f * lg + 0.6299787005f * lb)
        return floatArrayOf(
            0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
            1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
            0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
        )
    }

    private fun fromOklab(l: Float, a: Float, b: Float): FloatArray {
        val l1 = l + 0.3963377774f * a + 0.2158037573f * b
        val m1 = l - 0.1055613458f * a - 0.0638541728f * b
        val s1 = l - 0.0894841775f * a - 1.2914855480f * b
        val l3 = l1 * l1 * l1
        val m3 = m1 * m1 * m1
        val s3 = s1 * s1 * s1
        val r = 4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3
        val g = -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3
        val bl = -0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3
        return floatArrayOf(gamma(r.coerceIn(0f, 1f)), gamma(g.coerceIn(0f, 1f)), gamma(bl.coerceIn(0f, 1f)))
    }
}
