package dev.liquid.launcher.feature.home

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
import dev.liquid.launcher.core.designsystem.GlassQuality
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
)

internal val LocalControls = staticCompositionLocalOf<ControlEnv?> { null }

internal enum class ControlEmphasis {
    Normal,

    /** The button that matters most where it sits: filled with the block's (or the accent) colour. */
    Main,

    /** A chip that is switched on. */
    Selected,
}

/** The surface of a button and the colour of what is written on it. */
internal class ControlSurface(val modifier: Modifier, val content: Color)

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
            val refraction = if (env.glass.quality == GlassQuality.Full) look.refraction else 0f
            val rim = look.rim
            val redraw = env.redraw
            val x = env.glass.extras
            ControlSurface(
                (if (redraw != null) Modifier.drawBehind { redraw() } else Modifier).drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        if (x.saturation == 1.5f && x.brightness == 0f) vibrancy()
                        else colorControls(brightness = x.brightness, contrast = 1f, saturation = x.saturation)
                        if (refraction > 0f) {
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
    val shape = RoundedCornerShape(percent = look.cornerPercent)
    val interaction = remember { MutableInteractionSource() }
    val surface = controlSurface(env, shape, color, emphasis, accent)
    val h = look.heightDp
    Row(
        modifier
            .height(h.dp)
            .widthIn(min = h.dp)
            // The touch is read outside the squash; the glass, its content and its edge squish together (D108).
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .then(if (look.squish) Modifier.squishOn(interaction) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .then(surface.modifier)
            .clip(shape)
            .padding(horizontal = if (text == null) 0.dp else (h * 0.38f).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (icon != null) LauncherIcon(icon, contentDescription = if (text == null) contentDescription else null, tint = surface.content, size = (h * 0.5f).dp)
        if (icon != null && text != null) Spacer(Modifier.width(6.dp))
        if (text != null) {
            Text(
                text,
                style = TextStyle(
                    fontFamily = HomeTheme.font, color = surface.content, fontSize = (h * 0.37f).coerceIn(11f, 15f).sp,
                    fontWeight = if (emphasis == ControlEmphasis.Main) FontWeight.SemiBold else FontWeight.Medium,
                ),
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
    val env = remember(look, glass, backdrop, redraw) { ControlEnv(look, glass, backdrop, redraw) }
    androidx.compose.runtime.CompositionLocalProvider(LocalControls provides env, content = content)
}

/** The same look without a backdrop: for long rows of chips, drawn as crystal so scrolling stays smooth. */
@Composable
internal fun WithoutBackdrop(content: @Composable () -> Unit) {
    val env = LocalControls.current
    val plain = remember(env) { env?.let { ControlEnv(it.look, it.glass, null) } }
    androidx.compose.runtime.CompositionLocalProvider(LocalControls provides plain, content = content)
}
