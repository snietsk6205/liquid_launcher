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
    // W3 (0.10.0): the rest of WM Keyboard's options for a kind of control.
    /** A picture over the fill (a local file), and the one shown while pressed; null for none. */
    val texture: String? = null,
    val pressedTexture: String? = null,
    /** "crop", "stretch" or "tile". */
    val textureScale: String = "crop",
    val textureOpacity: Float = 1f,
    /** Height in dp; null keeps the launcher's (Settings → Buttons → Size). */
    val heightDp: Int? = null,
    /** Small extra text (counts); null: the text, fainter. */
    val hint: Long? = null,
    /** Whether the theme's material ([LiquidTheme.material]) dresses this kind (buttons and pills, as WM's keys). */
    val material: Boolean = false,
)

/** Sheets and panels (W3): WM Keyboard's board under the launcher's sheets. */
@Serializable
data class ThemeBoard(
    val background: Long,
    val gradient: ThemeGradient? = null,
    val text: Long,
    val secondaryText: Long,
    val divider: Long,
    /** A local picture behind sheets, its opacity and blur. */
    val image: String? = null,
    val imageOpacity: Float = 1f,
    val imageBlurDp: Float = 0f,
    val shape: String = "ROUNDED",
    val cornerDp: Int = 32,
    val glass: Boolean = true,
)

/** What controls look made of (WM Keyboard's KeyMaterialSpec, resolved over its kind's preset). */
@Serializable
data class ThemeMaterial(
    val kind: String,
    val bevelDp: Float,
    val topInsetDp: Float,
    val skirtShade: Float,
    val gloss: Float,
    val lightAngleDeg: Float,
    val texture: String,
    val textureOpacity: Float,
    val edge: String,
    val edgeColor: Long?,
    val edgeWidthDp: Float,
    val label: String,
    val labelColor: Long?,
    val iridescence: Float,
    val softShadow: Float,
) {
    val plain: Boolean
        get() = bevelDp == 0f && gloss == 0f && (texture == "NONE" || textureOpacity == 0f) && edge == "NONE" && label == "NONE" &&
            iridescence == 0f && softShadow == 0f
}

/** Light from the finger and controls that move under it (WM Keyboard's touch bloom). */
@Serializable
data class ThemeBloom(
    val color: Long?,
    val light: Float,
    val radiusDp: Float,
    val dip: Float,
    val wobble: Float,
    /** SINK, GROW, SHRINK, RISE, TILT, JELLY, POP, SQUISH, FADE; null: no motion. */
    val motion: String?,
)

/** A burst of particles from each press (WM Keyboard's key press effect). */
@Serializable
data class ThemeParticles(
    /** STARS, HEARTS, SPARKLE, CONFETTI, EMOJI, CUSTOM_IMAGE. */
    val kind: String,
    val emoji: String? = null,
    val images: List<String> = emptyList(),
    /** NATURAL, KEY_TEXT, ACCENT, GESTURE_TRAIL, CUSTOM, RANDOM. */
    val colorMode: String = "NATURAL",
    val color: Long,
    val intensity: Float = 1f,
    val size: Float = 1f,
    val speed: Float = 1f,
    val spread: Float = 0.5f,
    val gravity: Float = 1f,
    val durationMs: Int = 650,
)

@Serializable
data class ThemeOrb(val x: Float, val y: Float, val radius: Float)

/** Something moving over the home wallpaper, or a splash thrown by presses (WM Keyboard's board effect). */
@Serializable
data class ThemeEffect(
    val kind: String,
    val colors: List<Long>,
    val density: Float,
    val speed: Float,
    val intensity: Float,
    val scale: Float,
    val reactive: Float,
    val halfLifeMs: Int,
    val orbs: List<ThemeOrb> = emptyList(),
) {
    val onPress: Boolean get() = kind in ThemeEffect.splashKinds

    companion object {
        val splashKinds = setOf("PAINT", "INK", "LIGHT_POOL", "FLOOD", "PUFF", "GLINT", "POP", "SWOOSH", "SPECKS", "MELD")
    }
}

/** A sticker on sheets (WM Keyboard's decal): its centre and width as fractions of the sheet. */
@Serializable
data class ThemeDecal(val image: String, val x: Float, val y: Float, val scale: Float, val rotationDeg: Float, val opacity: Float)

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
    // W3 (0.10.0)
    val enabled: Boolean = true,
    /** Which kinds are glass: KEYS, MODIFIER_KEYS, ENTER_KEY, SPACE_BAR, BARS, TOOLS, CHIPS, CARDS, POPUPS (sheets), MENUS. */
    val surfaces: Set<String> = setOf("KEYS", "MODIFIER_KEYS", "ENTER_KEY", "SPACE_BAR", "BARS", "TOOLS", "CHIPS", "CARDS", "POPUPS", "MENUS"),
    /** AUTO, WALLPAPER, IMAGE or NONE. */
    val backdrop: String = "AUTO",
    /** How solid surfaces are where glass is not drawn. */
    val fallbackOpacity: Float = 0.92f,
) {
    val liquid: Boolean get() = style.equals("LIQUID", ignoreCase = true)

    /** Whether [surface] is glass. [backdrop] is about what sheets show behind them, not whether controls are glass. */
    fun on(surface: String): Boolean = enabled && surface in surfaces
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
    // W3 (0.10.0): the other kinds of control and the theme's effects. Null where a 0.9.13 theme had none.
    /** Secondary buttons (WM Keyboard's modifier keys); null: as [button]. */
    val modifier: ControlTheme? = null,
    /** The three pills (the space bar). */
    val space: ControlTheme? = null,
    val card: ControlTheme? = null,
    /** Menus and their rows (WM Keyboard's popups): fill, text, selected row. */
    val popup: ControlTheme? = null,
    val board: ThemeBoard? = null,
    val material: ThemeMaterial? = null,
    val bloom: ThemeBloom? = null,
    val particles: ThemeParticles? = null,
    val effect: ThemeEffect? = null,
    val decals: List<ThemeDecal> = emptyList(),
    /** The launcher's fonts by id ("" the launcher's), scale and bold labels. */
    val fontId: String = "",
    val fontScale: Float = 1f,
    val boldLabels: Boolean = false,
    val hintScale: Float = 1f,
    val gapScale: Float = 1f,
    val padScale: Float = 1f,
    val toolSizeDp: Int? = null,
    val menuRowDp: Int? = null,
    val trail: Long? = null,
    /** NONE, FLOW or HUE_CYCLE, and its speed. */
    val animation: String = "NONE",
    val animationSpeed: Float = 1f,
    /** "block", "accent" or "plain": what the main button of a screen is filled with. */
    val mainColor: String = "block",
    /** Blur of the home screen behind an open sheet, in dp; 0 for none. */
    val windowBlurDp: Int = 0,
    /** Each pill's own theme when the pills are individual (home, drawer, search), resolved. */
    val pills: Map<String, LiquidTheme> = emptyMap(),
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

    /** A 0.9.13 launcher theme (the "liquidTheme" format), or null. */
    fun decodeV1(obj: JsonObject): LiquidTheme? = runCatching { json.decodeFromJsonElement(LiquidTheme.serializer(), obj) }.getOrNull()

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
     * A WM Keyboard theme as a launcher theme, deriving what it leaves out the way WM Keyboard does (KbTheme). Since
     * 0.10.0 (W3) this is [ThemeResolve.resolve] on the theme as a document, which reads every option.
     */
    fun fromWmKeyboard(spec: JsonObject, fallbackName: String = "Imported theme"): LiquidTheme =
        ThemeResolve.resolve(ThemeDoc(spec), null, fallbackName)

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

    /** The opacity of [c], 0..1. */
    fun alpha(c: Long): Float = a(c)

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
