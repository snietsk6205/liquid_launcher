package dev.liquid.launcher.feature.home

import dev.liquid.launcher.core.data.ThemeResolve
import dev.liquid.launcher.core.data.ThemeDocs
import dev.liquid.launcher.core.data.ThemeDoc
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.blur
import dev.liquid.launcher.core.data.ControlTheme
import dev.liquid.launcher.core.data.LiquidTheme
import dev.liquid.launcher.core.data.LiquidThemes
import dev.liquid.launcher.core.designsystem.GlassEngine
import dev.liquid.launcher.core.designsystem.LiquidShader
import dev.liquid.launcher.core.designsystem.GlassQuality
import dev.liquid.launcher.core.designsystem.GlassStyle
import dev.liquid.launcher.core.designsystem.realGlass
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.derive
import dev.liquid.launcher.core.settings.LauncherSettings as S

/**
 * How buttons look (0.6.4, D95), from Settings → Buttons. One look for the buttons of Today, the
 * Now card, the layouts sheet and the clock gallery, so they read as one family.
 */
@Immutable
data class ControlLook(
    /** "glass", "crystal", "tint", "outline" or "solid". */
    val style: String = "glass",
    /** 0..50 % of the height. */
    val cornerPercent: Int = 50,
    val heightDp: Int = 34,
    /** How much white the button's glass carries, 0..1. */
    val tint: Float = 0.16f,
    /** Edge light, 0..1. */
    val rim: Float = 0.7f,
    /** Bend of a liquid glass button, 0..1. */
    val refraction: Float = 0.6f,
    /** Colour of the main button: "block", "accent" or "plain". */
    val main: String = "block",
    val squish: Boolean = true,
)

fun SettingsStore.controlLook() = ControlLook(
    style = get(S.ButtonStyle),
    cornerPercent = get(S.ButtonShape),
    heightDp = when (get(S.ButtonSize)) {
        "small" -> 28
        "large" -> 42
        else -> 34
    },
    tint = get(S.ButtonTint) / 100f,
    rim = get(S.ButtonRim) / 100f,
    refraction = get(S.ButtonRefraction) / 100f,
    main = get(S.ButtonMain),
    squish = get(S.ButtonSquish),
)

/**
 * Where buttons sit: their look, the glass settings, and the backdrop a liquid glass button bends
 * (normally the glass sheet or card around it, exported by that sheet, so a button refracts the
 * sheet like glass on glass). [redraw] is read while drawing, so a button inside a scrolling column
 * samples its new spot as the column scrolls.
 */
internal class ControlEnv(
    val look: ControlLook,
    val glass: GlassConfig,
    val backdrop: Backdrop?,
    val redraw: (() -> Any?)? = null,
    /** P4 (0.9.13): the theme of buttons and chips; null draws the launcher's own look (Settings → Buttons). */
    val theme: LiquidTheme? = null,
)

/**
 * The theme (W3, 0.10.0): the theme document in Settings, resolved with the wallpaper's colours (for colours set to
 * follow it), or null for the launcher's own look (Settings → Buttons). Resolved once per change of either.
 */
@Composable
internal fun rememberControlTheme(settings: SettingsStore): LiquidTheme? {
    val json by settings.derive { if (get(S.ThemeControls) != "theme") null else get(S.ThemeJson) }
    val tone by dev.liquid.launcher.core.common.WallpaperTones.tone.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val palette = remember(tone) {
        tone?.roles ?: runCatching { dev.liquid.launcher.core.common.WallpaperPalette.fromSeed(dev.liquid.launcher.core.data.ThemedColors.seed(context)) }.getOrNull()
    }
    return remember(json, palette) { json?.let { ThemeCache.of(it, palette) } }
}

private object ThemeCache {
    private var key: Pair<String, dev.liquid.launcher.core.common.WallpaperPalette?>? = null
    private var theme: LiquidTheme = ThemeResolve.resolve(ThemeDocs.Default)

    @Synchronized
    fun of(json: String, palette: dev.liquid.launcher.core.common.WallpaperPalette?): LiquidTheme {
        val k = json to palette
        if (k != key) {
            val doc = if (json.isBlank()) ThemeDocs.Default else ThemeDoc.parse(json) ?: ThemeDocs.Default
            theme = ThemeResolve.resolve(doc, palette)
            key = k
        }
        return theme
    }
}

/** The rounded outline closest to a themed control's, for the glass shader. CIRCLE is a pill: a round button is as wide as it is high. */
internal fun themeShape(kind: ControlTheme): CornerBasedShape = glassOutline(kind.shape, kind.cornerDp)

/** The real outline of a themed control (W3): any of WM Keyboard's shapes, arch to ticket. */
internal fun themeOutlineOf(kind: ControlTheme): androidx.compose.ui.graphics.Shape = themeOutline(kind.shape, kind.cornerDp)

/** The kind of control [emphasis] and [tool] ask for in [theme]. */
internal fun themeKind(theme: LiquidTheme, look: ControlLook, emphasis: ControlEmphasis, tool: Boolean): ControlTheme = when {
    tool -> theme.tool
    emphasis == ControlEmphasis.Main && theme.mainColor == "plain" -> theme.main
    emphasis == ControlEmphasis.Main -> theme.main
    emphasis == ControlEmphasis.Selected -> theme.chip
    // W3: Cancel, Edit, Shuffle… are WM Keyboard's modifier keys.
    emphasis == ControlEmphasis.Secondary -> theme.modifier ?: theme.button
    else -> theme.button
}

/**
 * A control drawn by the theme (P4, 0.9.13): its fill (the pressed or selected one when pressed or on, eased by a
 * spring that never jumps), a sheen, border, shadow and glow, and, where the theme makes it glass and a backdrop is
 * at hand, WM Keyboard's frost under the fill: the backdrop blurred and coloured, with the rim of light on the edge
 * facing the light and a shadow on the other (the real glass shader, thin), and for a LIQUID theme the bend too.
 * The main button keeps its block or accent colour unless "Main button colour" is "Like the other buttons".
 */
@Composable
internal fun themedSurface(
    env: ControlEnv,
    theme: LiquidTheme,
    kind: ControlTheme,
    shape: CornerBasedShape,
    emphasis: ControlEmphasis,
    accent: Color?,
    pressed: Boolean,
    outline: androidx.compose.ui.graphics.Shape = themeOutlineOf(kind),
): ControlSurface {
    val main: Color? = when {
        emphasis != ControlEmphasis.Main || theme.mainColor == "plain" -> null
        theme.mainColor == "accent" || accent == null -> HomeTheme.accent
        else -> accent
    }
    val selected = emphasis == ControlEmphasis.Selected
    val targetFill = when {
        main != null -> if (pressed) androidx.compose.ui.graphics.lerp(main, Color(kind.pressed), 0.35f) else main.copy(alpha = 0.86f)
        selected -> Color(kind.active)
        pressed -> Color(kind.pressed)
        else -> Color(kind.fill)
    }
    val targetContent = when {
        main != null -> readableOn(main)
        selected -> Color(kind.activeText)
        pressed -> Color(kind.pressedText ?: kind.text)
        else -> Color(kind.text)
    }
    val fill by androidx.compose.animation.animateColorAsState(targetFill, spring(stiffness = MotionScale.stiffness(900f)), label = "themeFill")
    val content by androidx.compose.animation.animateColorAsState(targetContent, spring(stiffness = MotionScale.stiffness(900f)), label = "themeText")
    // W3: the press as a number, for a material's face sinking into its skirt and a key without a face showing one.
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = MotionScale.stiffness(1300f)), label = "themePress")
    val faceless = kind.shape == "NONE" && !selected && main == null
    val material = theme.material?.takeIf { kind.material && dev.liquid.launcher.core.designsystem.GlassTest.variant != dev.liquid.launcher.core.designsystem.GlassTest.Variant.NoMaterial }
    val image = rememberThemeImage(kind.texture)
    val pressedImage = rememberThemeImage(kind.pressedTexture)
    val accentColour = HomeTheme.accent
    val sheen = kind.gradient?.takeIf { it.colors.size >= 2 }?.let { g ->
        val colors = g.colors.map { Color(it) }
        when (g.type.uppercase()) {
            "RADIAL" -> Brush.radialGradient(colors)
            "SWEEP" -> Brush.sweepGradient(colors)
            else -> AngleGradient(colors, g.angleDeg)
        }
    }
    var modifier: Modifier = Modifier
    if (kind.shadowDp > 0.01f && !faceless) {
        val c = kind.shadowColor?.let { Color(it) } ?: Color.Black
        modifier = modifier.shadow(kind.shadowDp.dp, outline, clip = false, ambientColor = c, spotColor = c)
    }
    if (kind.glowDp > 0.01f && !faceless) {
        val c = kind.glowColor?.let { Color(it) } ?: HomeTheme.accent
        modifier = modifier.shadow(kind.glowDp.dp, outline, clip = false, ambientColor = c, spotColor = c)
    }
    // The face: the material's, else the fill in the control's outline; its sheen; its picture (the pressed one while held).
    val drawFace: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
        if (!faceless || press > 0.01f) {
            val f = if (faceless) fill.copy(alpha = fill.alpha * press) else fill
            if (material != null) drawMaterialFace(material, f, content, accentColour, outline, press)
            else drawPath(outlinePath(outline), f)
            if (sheen != null) drawPath(outlinePath(outline), sheen)
            val picture = if (press > 0.5f && pressedImage != null) pressedImage else image
            if (picture != null) drawTexture(picture, kind.textureScale, kind.textureOpacity, outline)
        }
    }
    val backdrop = env.backdrop
    val frost = theme.frost
    modifier = if (kind.glass && !faceless && backdrop != null && env.glass.quality != GlassQuality.Tint) {
        val full = env.glass.quality == GlassQuality.Full
        val x = env.glass.extras
        val rimColor = frost.rimColor?.let { Color(it) } ?: Color.White
        // The frost as the real glass: no bend for FROSTED (a hairline rim and its shadow), the edge bent for LIQUID.
        val style = GlassStyle(
            quality = GlassQuality.Full, blur = 0.dp, refraction = if (frost.liquid) frost.refraction else 0f, tint = Color.Transparent,
            dispersion = frost.liquid && frost.dispersion > 0.01f, depth = false, rimColor = rimColor, lightAngle = x.lightAngle,
            engine = GlassEngine.Real, bevel = if (frost.liquid) 12.dp else 2.5.dp, thickness = 2f,
            dispersionAmount = frost.dispersion, specular = frost.rim, shade = frost.rim * 0.8f, grain = (frost.grain * 4f).coerceAtMost(1f),
        )
        val redraw = env.redraw
        (if (redraw != null) modifier.drawBehind { redraw() } else modifier).drawBackdrop(
            backdrop = backdrop,
            shape = { outline },
            effects = {
                colorControls(brightness = frost.brightness, contrast = 1f, saturation = frost.saturation)
                if (frost.blurDp > 0.01f) blur(frost.blurDp.dp.toPx())
                if (full) realGlass(shape, style)
            },
            // Without the shader (or below Full quality) the rim is the library's hairline.
            highlight = if (frost.rim > 0.01f && !(full && LiquidShader.available)) ({
                Highlight.Default.copy(alpha = frost.rim, style = HighlightStyle.Default(color = rimColor, angle = x.lightAngle))
            }) else null,
            shadow = null,
            onDrawSurface = { drawFace() },
        )
    } else {
        // Where glass is not drawn, a see-through fill is made more solid (the theme's opacity without glass).
        modifier.drawBehind {
            if (kind.glass && frost.enabled && fill.alpha < frost.fallbackOpacity && !faceless) {
                drawPath(outlinePath(outline), fill.copy(alpha = (frost.fallbackOpacity - fill.alpha).coerceAtLeast(0f) * 0.5f))
            }
            drawFace()
        }
    }
    val border = kind.border
    if (border != null && kind.borderWidthDp > 0.01f && !faceless) {
        modifier = modifier.border(kind.borderWidthDp.dp, Color(border), outline)
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    return ControlSurface(modifier, content, labelShadow(material, content, accentColour, density), material?.label == "DOTS")
}

/** A linear gradient across whatever it fills, along [angleDeg] (90° runs top to bottom), as WM Keyboard's sheens. */
private class AngleGradient(private val colors: List<Color>, private val angleDeg: Float) : androidx.compose.ui.graphics.ShaderBrush() {
    override fun createShader(size: androidx.compose.ui.geometry.Size): androidx.compose.ui.graphics.Shader {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = kotlin.math.cos(rad).toFloat()
        val dy = kotlin.math.sin(rad).toFloat()
        val half = (kotlin.math.abs(dx) * size.width + kotlin.math.abs(dy) * size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        return androidx.compose.ui.graphics.LinearGradientShader(Offset(cx - dx * half, cy - dy * half), Offset(cx + dx * half, cy + dy * half), colors)
    }

    override fun equals(other: Any?): Boolean = other is AngleGradient && other.colors == colors && other.angleDeg == angleDeg

    override fun hashCode(): Int = colors.hashCode() * 31 + angleDeg.hashCode()
}

/**
 * A chip in the theme (P4, 0.9.13): the theme's chip fill, or its selected fill when [on]; [trailing] is a count
 * after the label. Used by the launcher's chip rows when a theme is on.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ThemeChip(
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 34.dp,
    icon: Int? = null,
    trailing: String? = null,
    /** False when a sliding selection behind the chips draws the "on" fill (the tag strip): only the text changes. */
    ownFill: Boolean = true,
    /** False for chips set on a glass row of their own (the tag strip): no fill at rest, only the text. */
    restFill: Boolean = true,
    /** The text of such a bare chip: the colour that reads on that glass (the theme's chip text was made for its fill). */
    restColor: Color? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)?,
) {
    val env = LocalControls.current ?: return
    val theme = env.theme ?: return
    val kind = theme.chip
    val shape = themeShape(kind)
    val outline = themeOutlineOf(kind)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val surface = when {
        on && !ownFill -> ControlSurface(Modifier, Color(kind.activeText))
        !on && !restFill -> ControlSurface(Modifier, restColor ?: Color(kind.text))
        else -> themedSurface(env, theme, kind, shape, if (on) ControlEmphasis.Selected else ControlEmphasis.Normal, null, pressed, outline)
    }
    val h = kind.heightDp?.dp ?: height
    Row(
        modifier
            .height(h)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick)
                } else Modifier
            )
            .themePress(interaction, theme, env.look)
            .then(surface.modifier)
            .clip(outline)
            .padding(horizontal = (14f * theme.padScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            LauncherIcon(icon, contentDescription = null, tint = surface.content, size = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, maxLines = 1, modifier = Modifier.dotMatrix(surface.dots), style = themeTextStyle(theme, surface, 13f, bold = on))
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                trailing,
                style = themeTextStyle(theme, surface, 12f * theme.hintScale, bold = false).copy(color = kind.hint?.let { Color(it) } ?: surface.content.copy(alpha = 0.7f)),
            )
        }
    }
}

/** The text of a themed control (W3): the theme's font, size and bold labels, and its material's label effect. */
internal fun themeTextStyle(theme: LiquidTheme?, surface: ControlSurface, size: Float, bold: Boolean): TextStyle {
    val font = theme?.fontId?.takeIf { it.isNotBlank() }?.let { HomeTheme.fontFor(it) } ?: HomeTheme.font
    val weight = when {
        theme?.boldLabels == true -> FontWeight.Bold
        bold -> FontWeight.SemiBold
        else -> FontWeight.Medium
    }
    return TextStyle(fontFamily = font, color = surface.content, fontSize = (size * (theme?.fontScale ?: 1f)).sp, fontWeight = weight, shadow = surface.labelShadow)
}

/** How a control moves under the finger: the theme's key motion (W3), else the launcher's squish (Settings → Buttons). */
@Composable
internal fun Modifier.themePress(interaction: androidx.compose.foundation.interaction.InteractionSource, theme: LiquidTheme?, look: ControlLook): Modifier {
    val bloom = theme?.bloom
    val motion = bloom?.motion
    // W3: the theme's particles burst from the press too.
    val base = if (theme?.particles != null) particlesOn(interaction) else this
    return when {
        motion != null && bloom.dip > 0.01f -> base.pressMotion(interaction, motion, bloom.dip, bloom.wobble)
        theme == null && look.squish -> base.squishOn(interaction)
        theme != null && bloom == null && look.squish -> base.squishOn(interaction)
        else -> base
    }
}

internal val LocalControls = staticCompositionLocalOf<ControlEnv?> { null }

internal enum class ControlEmphasis {
    Normal,

    /** The button that matters most where it sits: filled with the block's (or the accent) colour. */
    Main,

    /** A chip that is switched on. */
    Selected,

    /** W3: a secondary button (Cancel, Edit, Shuffle…), WM Keyboard's modifier keys. */
    Secondary,
}

/**
 * The surface of a button and the colour of what is written on it; W3: the shadow its material's label effect casts,
 * and whether the label is drawn as dots.
 */
internal class ControlSurface(val modifier: Modifier, val content: Color, val labelShadow: androidx.compose.ui.graphics.Shadow? = null, val dots: Boolean = false)

/**
 * The surface for a control of [shape] whose content would be [color] on the sheet around it.
 * [accent]: the colour of a [ControlEmphasis.Main] control (a block's colour), else the theme's accent.
 */
internal fun controlSurface(env: ControlEnv?, shape: CornerBasedShape, color: Color, emphasis: ControlEmphasis, accent: Color? = null): ControlSurface {
    val look = env?.look ?: ControlLook()
    val main: Color? = when {
        emphasis != ControlEmphasis.Main || look.main == "plain" -> null
        look.main == "accent" || accent == null -> HomeTheme.accent
        else -> accent
    }
    // Light content means a dark sheet: the button is a lighter pane on it. Dark content means a
    // light sheet: the button carries more white so it still stands out.
    val onDark = color.luminance() > 0.5f
    val selected = emphasis == ControlEmphasis.Selected
    val white = ((if (onDark) look.tint else look.tint * 2.4f) * (if (selected) 2f else 1f)).coerceIn(0f, 0.85f)
    val fill = main?.copy(alpha = 0.82f) ?: Color.White.copy(alpha = white)
    val onMain = main?.let { readableOn(it) }
    fun crystal(): ControlSurface {
        val top = fill.copy(alpha = (fill.alpha * 1.45f).coerceAtMost(0.95f))
        val bottom = fill.copy(alpha = fill.alpha * 0.6f)
        val rim = look.rim
        val light = env?.glass?.extras?.rimColor ?: Color.White
        val edge = Brush.linearGradient(
            0f to light.copy(alpha = 0.85f * rim),
            0.45f to light.copy(alpha = 0.1f * rim),
            1f to light.copy(alpha = 0.4f * rim),
            start = Offset.Zero, end = Offset.Infinite,
        )
        return ControlSurface(
            Modifier
                .background(Brush.verticalGradient(listOf(top, bottom)), shape)
                .then(if (rim > 0.01f) Modifier.border(BorderStroke(1.dp, edge), shape) else Modifier),
            onMain ?: color,
        )
    }
    return when (look.style) {
        "tint" -> ControlSurface(Modifier.background(main?.copy(alpha = 0.9f) ?: color.copy(alpha = (look.tint * 0.75f).coerceIn(0.04f, 0.6f) * if (selected) 2f else 1f), shape), onMain ?: color)
        "outline" -> ControlSurface(
            Modifier
                .background(main?.copy(alpha = 0.22f) ?: if (selected) color.copy(alpha = 0.16f) else Color.Transparent, shape)
                .border(1.2.dp, (main ?: color).copy(alpha = if (main != null || selected) 0.9f else 0.5f), shape),
            color,
        )
        "solid" -> {
            val solid = main ?: if (selected) HomeTheme.accent else color.copy(alpha = 0.9f)
            ControlSurface(Modifier.background(solid, shape), readableOn(solid))
        }
        "crystal" -> crystal()
        else -> {
            val backdrop = env?.backdrop
            if (backdrop == null || env.glass.quality == GlassQuality.Tint) return crystal()
            val full = env.glass.quality == GlassQuality.Full
            val refraction = if (full) look.refraction else 0f
            val rim = look.rim
            val redraw = env.redraw
            val x = env.glass.extras
            // P4: the button's real glass, with the button's own bend and a narrower edge than a sheet's.
            val real = x.applyTo(GlassStyle(GlassQuality.Full, 0.dp, look.refraction, Color.Transparent))
            ControlSurface(
                (if (redraw != null) Modifier.drawBehind { redraw() } else Modifier).drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        if (x.saturation == 1.5f && x.brightness == 0f) vibrancy()
                        else colorControls(brightness = x.brightness, contrast = 1f, saturation = x.saturation)
                        if (full && !realGlass(shape, real, bevelCap = (8f + 8f * refraction).dp.toPx()) && refraction > 0f) {
                            lens(
                                refractionHeight = minOf(size.minDimension / 2f, (8f + 8f * refraction).dp.toPx()),
                                refractionAmount = (6f + 18f * refraction).dp.toPx(),
                                depthEffect = x.depth,
                                chromaticAberration = x.dispersion,
                            )
                        }
                    },
                    highlight = if (rim > 0.01f) ({ Highlight.Default.copy(alpha = rim, style = HighlightStyle.Default(color = x.rimColor, angle = x.lightAngle)) }) else null,
                    onDrawSurface = { drawRect(fill) },
                ),
                onMain ?: color,
            )
        }
    }
}

/**
 * A button in the launcher's own sheets and cards: liquid glass by default, styled by Settings →
 * Buttons, with a small squish under the finger. [text] and/or [icon].
 */
@Composable
internal fun GlassButton(
    text: String?,
    color: Color,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    emphasis: ControlEmphasis = ControlEmphasis.Normal,
    accent: Color? = null,
    contentDescription: String? = text,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val env = LocalControls.current
    val look = env?.look ?: ControlLook()
    val theme = env?.theme
    // P4: a button with an icon and no text is a round tool button in the theme.
    val kind = theme?.let { themeKind(it, look, emphasis, tool = text == null && icon != null) }
    val shape = if (kind != null) themeShape(kind) else RoundedCornerShape(percent = look.cornerPercent)
    val outline: androidx.compose.ui.graphics.Shape = if (kind != null) themeOutlineOf(kind) else shape
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Without a theme, a secondary button is drawn as a normal one.
    val plainEmphasis = if (emphasis == ControlEmphasis.Secondary) ControlEmphasis.Normal else emphasis
    val surface = if (env != null && theme != null && kind != null) {
        themedSurface(env, theme, kind, shape, emphasis, accent, pressed, outline)
    } else {
        controlSurface(env, shape, color, plainEmphasis, accent)
    }
    // W3: the theme's height of buttons (and size of round ones), else Settings → Buttons → Size.
    val h = kind?.heightDp ?: look.heightDp
    val pad = theme?.padScale ?: 1f
    Row(
        modifier
            .height(h.dp)
            .widthIn(min = h.dp)
            // The touch is read outside the squash; the glass, its content and its edge squish together (D108).
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .themePress(interaction, theme, look)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .then(surface.modifier)
            .clip(outline)
            .padding(horizontal = if (text == null) 0.dp else (h * 0.38f * pad).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (icon != null) LauncherIcon(icon, contentDescription = if (text == null) contentDescription else null, tint = surface.content, size = (h * 0.5f).dp)
        if (icon != null && text != null) Spacer(Modifier.width(6.dp))
        if (text != null) {
            Text(
                text,
                modifier = Modifier.dotMatrix(surface.dots),
                style = themeTextStyle(theme, surface, (h * 0.37f).coerceIn(11f, 15f), bold = emphasis == ControlEmphasis.Main),
                maxLines = 1,
            )
        }
    }
}

/**
 * Gives the buttons in [content] their look and the glass they bend: [backdrop] is normally the
 * sheet or card around them (its exported layer); null draws liquid glass buttons as crystal.
 */
@Composable
internal fun ProvideControls(
    controller: HomeController,
    glass: GlassConfig,
    backdrop: Backdrop?,
    redraw: (() -> Any?)? = null,
    content: @Composable () -> Unit,
) {
    val look by controller.data.settings.derive { controlLook() }
    val theme = rememberControlTheme(controller.data.settings)
    val env = remember(look, glass, backdrop, redraw, theme) { ControlEnv(look, glass, backdrop, redraw, theme) }
    androidx.compose.runtime.CompositionLocalProvider(LocalControls provides env, content = content)
}

/** The same look without a backdrop: for long rows of chips, drawn as crystal so scrolling stays smooth. */
@Composable
internal fun WithoutBackdrop(content: @Composable () -> Unit) {
    val env = LocalControls.current
    val plain = remember(env) { env?.let { ControlEnv(it.look, it.glass, null, theme = it.theme) } }
    androidx.compose.runtime.CompositionLocalProvider(LocalControls provides plain, content = content)
}
