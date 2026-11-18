package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import dev.liquid.launcher.core.data.ControlTheme
import dev.liquid.launcher.core.data.LiquidTheme
import dev.liquid.launcher.core.data.ThemeFrost
import dev.liquid.launcher.core.designsystem.GlassEngine
import dev.liquid.launcher.core.designsystem.GlassQuality
import dev.liquid.launcher.core.designsystem.GlassStyle
import dev.liquid.launcher.core.designsystem.liquidGlass
import kotlin.math.max

/*
 * W3 (0.10.0): the launcher's surfaces drawn by the theme, as WM Keyboard's parts map to them (spec 3.2):
 *  - sheets and panels (hub, wallpaper, tags, layouts…) are the keyboard's board: its colour or gradient over its
 *    frost, its picture with opacity and blur, its stickers, its animation;
 *  - menus are its popups: their colour, text and selected row over the frost of MENUS;
 *  - the three pills are its space bar (each its own theme when the pills are individual);
 *  - cards are its cards.
 * Without a theme (Settings → Buttons → the launcher's own look) everything is the launcher's glass, as before.
 */

/** The theme in use for the controls of this screen, or null for the launcher's own look. */
internal val currentTheme: LiquidTheme?
    @Composable get() = LocalControls.current?.theme

/** The glass of a theme surface: the frost's blur, colour, brightness, rim and grain, bent for a liquid frost. */
internal fun frostStyle(frost: ThemeFrost, glass: GlassConfig, tint: Color, large: Boolean): GlassStyle {
    val x = glass.extras
    return GlassStyle(
        quality = if (glass.quality == GlassQuality.Full) GlassQuality.Full else GlassQuality.Frosted,
        blur = frost.blurDp.dp,
        refraction = if (frost.liquid) frost.refraction else 0f,
        tint = tint,
        rim = frost.rim,
        saturation = frost.saturation,
        brightness = frost.brightness,
        dispersion = frost.liquid && frost.dispersion > 0.01f,
        rimColor = frost.rimColor?.let { Color(it) } ?: Color.White,
        lightAngle = x.lightAngle,
        shadow = if (large) 1f else 0.6f,
        engine = GlassEngine.Real,
        bevel = if (frost.liquid) (if (large) 18.dp else 12.dp) else 3.dp,
        thickness = 2f,
        dispersionAmount = frost.dispersion,
        specular = frost.rim,
        shade = frost.rim * 0.6f,
        grain = (frost.grain * 4f).coerceAtMost(1f),
    )
}

/** The theme's animation as a phase 0..1 (a gradient flowing, colours turning), or 0 when it has none. */
@Composable
private fun themeAnimationPhase(theme: LiquidTheme): Float {
    if (theme.animation == "NONE") return 0f
    val ms = (if (theme.animation == "FLOW") 9000 else 24000) / theme.animationSpeed
    val t = rememberInfiniteTransition(label = "themeAnimation")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(ms.toInt().coerceAtLeast(800), easing = LinearEasing), RepeatMode.Restart), label = "phase")
    return phase
}

private fun hueShift(c: Color, turns: Float): Color {
    if (turns == 0f) return c
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv)
    hsv[0] = (hsv[0] + turns * 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv)).copy(alpha = c.alpha)
}

// ---- sheets and panels: the board ----

/** The text colour on sheets: the theme's (worked out against the board), else light or dark from the glass. */
@Composable
internal fun sheetContentColor(glass: GlassConfig, lightBackground: Boolean): Color =
    currentTheme?.board?.let { Color(it.text) } ?: if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White

/** The outline of sheets: the theme's sheet shape and corners, else [fallback]. */
@Composable
internal fun sheetOutline(fallback: CornerBasedShape): Pair<CornerBasedShape, Shape> {
    val board = currentTheme?.board ?: return fallback to fallback
    return glassOutline(board.shape, board.cornerDp) to themeOutline(board.shape, board.cornerDp)
}

/**
 * A sheet's surface. Themed: the board's colour (or gradient) over the frost when sheets are glass and something is
 * behind the glass, else the colour made solid (the theme's opacity without glass). Not themed: the launcher's glass.
 */
@Composable
internal fun Modifier.sheetSurface(
    backdrop: Backdrop,
    shape: CornerBasedShape,
    glass: GlassConfig,
    lightBackground: Boolean,
    outline: Shape = shape,
    exported: LayerBackdrop? = null,
): Modifier {
    val theme = currentTheme
    val board = theme?.board ?: return liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground, large = true), exported)
    val frost = theme.frost
    val phase = themeAnimationPhase(theme)
    val turns = if (theme.animation == "HUE_CYCLE") phase else 0f
    val fill = hueShift(Color(board.background), turns)
    val sample = board.glass && frost.enabled && frost.backdrop != "NONE" && frost.backdrop != "IMAGE" && glass.quality != GlassQuality.Tint
    var m = if (sample) {
        liquidGlass(backdrop, shape, frostStyle(frost, glass, fill, large = true), exported)
    } else {
        clip(outline).background(fill.copy(alpha = max(fill.alpha, frost.fallbackOpacity)))
    }
    board.gradient?.let { g ->
        val colors = g.colors.map { hueShift(Color(it), turns) }
        val angle = g.angleDeg + if (theme.animation == "FLOW") phase * 360f else 0f
        m = m.drawBehind {
            val rad = Math.toRadians(angle.toDouble())
            val dx = kotlin.math.cos(rad).toFloat()
            val dy = kotlin.math.sin(rad).toFloat()
            val half = (kotlin.math.abs(dx) * size.width + kotlin.math.abs(dy) * size.height) / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val brush = when (g.type.uppercase()) {
                "RADIAL" -> Brush.radialGradient(colors, c)
                "SWEEP" -> Brush.sweepGradient(colors, c)
                else -> Brush.linearGradient(colors, c - Offset(dx * half, dy * half), c + Offset(dx * half, dy * half))
            }
            drawPath(outlinePath(outline), brush)
        }
    }
    return m
}

/**
 * The board's picture and stickers as a modifier, for sheets that are a Column (the hub, the studios, folders, the
 * tag list…): drawn over the glass, under the content, clipped to [shape]; [amount] fades them (the hub while it
 * grows out of the pill). One layer holds the blurred picture, recorded only when the size or the picture changes.
 */
@Composable
internal fun Modifier.boardPicture(shape: Shape, amount: () -> Float = { 1f }): Modifier {
    val theme = currentTheme ?: return this
    val board = theme.board ?: return this
    val image = rememberThemeImage(board.image, 1080)
    val decals = theme.decals.map { it to rememberThemeImage(it.image, 512) }
    val picture = image?.takeIf { board.imageOpacity > 0.01f }
    if (picture == null && decals.none { it.second != null }) return this
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    return drawWithContent {
        val a = amount().coerceIn(0f, 1f)
        if (a > 0.01f) {
            val clip = androidx.compose.ui.graphics.Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
            clipPath(clip) {
                if (picture != null) {
                    val r = board.imageBlurDp.dp.toPx()
                    layer.renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
                    layer.alpha = board.imageOpacity * a
                    layer.record {
                        val scale = max(size.width / picture.width, size.height / picture.height)
                        val w = picture.width * scale
                        val h = picture.height * scale
                        drawImage(
                            picture,
                            dstOffset = IntOffset(((size.width - w) / 2f).toInt(), ((size.height - h) / 2f).toInt()),
                            dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
                        )
                    }
                    drawLayer(layer)
                }
                for ((decal, sticker) in decals) {
                    sticker ?: continue
                    val w = size.width * decal.scale
                    val h = w * sticker.height / sticker.width.coerceAtLeast(1)
                    val c = Offset(size.width * decal.x, size.height * decal.y)
                    rotate(decal.rotationDeg, c) {
                        drawImage(
                            sticker,
                            dstOffset = IntOffset((c.x - w / 2f).toInt(), (c.y - h / 2f).toInt()),
                            dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
                            alpha = decal.opacity * a,
                        )
                    }
                }
            }
        }
        drawContent()
    }
}

/** W3: the vertical padding of a menu row (its icon and text are 20 dp), so the row is as tall as the theme says. */
internal val menuRowPadding: androidx.compose.ui.unit.Dp
    @Composable get() = currentTheme?.menuRowDp?.let { ((it - 20) / 2f).coerceAtLeast(4f).dp } ?: 12.dp

/** W3: a gap between buttons in a row, scaled by the theme's "Gaps". */
internal val themeGap: Float
    @Composable get() = currentTheme?.gapScale ?: 1f

/** The board's picture (opacity, blur) and its stickers, behind a sheet's content; nothing without them. */
@Composable
internal fun BoardPicture(modifier: Modifier) {
    val theme = currentTheme ?: return
    val board = theme.board ?: return
    val image = rememberThemeImage(board.image, 1080)
    val decals = theme.decals.map { it to rememberThemeImage(it.image, 512) }
    if (image == null && decals.none { it.second != null }) return
    if (image != null && board.imageOpacity > 0.01f) {
        Canvas(
            modifier.graphicsLayer {
                val r = board.imageBlurDp.dp.toPx()
                if (r > 0.5f) renderEffect = BlurEffect(r, r, TileMode.Clamp)
                clip = true
            }
        ) {
            val scale = max(size.width / image.width, size.height / image.height)
            val w = image.width * scale
            val h = image.height * scale
            drawImage(
                image,
                dstOffset = IntOffset(((size.width - w) / 2f).toInt(), ((size.height - h) / 2f).toInt()),
                dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
                alpha = board.imageOpacity,
            )
        }
    }
    for ((decal, picture) in decals) {
        picture ?: continue
        Canvas(modifier) {
            val w = size.width * decal.scale
            val h = w * picture.height / picture.width.coerceAtLeast(1)
            val c = Offset(size.width * decal.x, size.height * decal.y)
            rotate(decal.rotationDeg, c) {
                drawImage(
                    picture,
                    dstOffset = IntOffset((c.x - w / 2f).toInt(), (c.y - h / 2f).toInt()),
                    dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
                    alpha = decal.opacity,
                )
            }
        }
    }
}

// ---- menus: the popups ----

/** The text colour of menus: the theme's popup text, else light or dark from the glass. */
@Composable
internal fun menuContentColor(glass: GlassConfig, lightBackground: Boolean): Color =
    currentTheme?.popup?.let { Color(it.text) } ?: if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White

/** A menu's surface: the theme's popup colour, border and shadow over the frost of MENUS; else the launcher's glass. */
@Composable
internal fun Modifier.menuSurface(backdrop: Backdrop, shape: CornerBasedShape, glass: GlassConfig, lightBackground: Boolean): Modifier {
    val theme = currentTheme
    val popup = theme?.popup ?: return liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground))
    return controlPanel(popup, theme.frost, "MENUS", backdrop, shape, glass, large = false)
}

/** The theme's outline for menus (shape and corner radius), else [fallback]. */
@Composable
internal fun menuOutline(fallback: CornerBasedShape): CornerBasedShape {
    val popup = currentTheme?.popup ?: return fallback
    return glassOutline(popup.shape, popup.cornerDp)
}

// ---- cards ----

@Composable
internal fun cardContentColor(fallback: Color): Color = currentTheme?.card?.let { Color(it.text) } ?: fallback

/** A card's surface: the theme's card colour, border and shadow over the frost of CARDS; null when not themed. */
@Composable
internal fun themedCardSurface(backdrop: Backdrop?, glass: GlassConfig): Pair<Modifier, CornerBasedShape>? {
    val theme = currentTheme ?: return null
    val card = theme.card ?: return null
    val shape = glassOutline(card.shape, card.cornerDp)
    return Modifier.controlPanel(card, theme.frost, "CARDS", backdrop, shape, glass, large = true) to shape
}

// ---- the three pills: the space bar ----

/**
 * The space bar's look for [which] pill: its own theme when the pills are individual, else the theme's; the search bar
 * with its own light tint when the theme gives it one (0.10.2).
 */
@Composable
internal fun pillTheme(which: String): ControlTheme? {
    val theme = currentTheme ?: return null
    theme.pills[which]?.let { return it.space }
    if (which == dev.liquid.launcher.core.data.ThemePill.SEARCH) theme.search?.let { return it }
    return theme.space
}

@Composable
internal fun pillFrost(which: String): ThemeFrost? {
    val theme = currentTheme ?: return null
    return (theme.pills[which] ?: theme).frost
}

/**
 * The text colour on [which] pill, else [fallback]. [lightBehind] says what the pill sits on where that is not the
 * wallpaper itself (the drawer's dimmed page, the search page): a see-through pill whose text the theme keeps readable
 * then takes the theme's dark or light ink for that background (0.10.2).
 */
@Composable
internal fun pillContentColor(which: String, fallback: Color, lightBehind: Boolean? = null): Color {
    val pill = pillTheme(which) ?: return fallback
    val theme = currentTheme
    if (lightBehind != null && theme != null && theme.adaptive && dev.liquid.launcher.core.data.ThemeColors.alpha(pill.fill) < 0.55f &&
        theme.pills[which] == null
    ) {
        val fill = Color(pill.fill)
        val ink = Color(if (lightBehind) theme.inkDark else theme.inkLight)
        val text = Color(pill.text)
        // Keep the theme's text when it already reads on that background.
        val behind = if (lightBehind) Color(0xFFE6E6EA) else Color(0xFF1C1D22)
        val bg = androidx.compose.ui.graphics.lerp(behind, fill.copy(alpha = 1f), fill.alpha)
        return if (contrastOf(text, bg) >= 3f) text else ink
    }
    return Color(pill.text)
}

private fun contrastOf(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return max(la, lb) / kotlin.math.min(la, lb)
}

/** [which] pill's surface: the space bar's colour, picture, border and shadow over its frost; else the launcher's glass. */
@Composable
internal fun Modifier.pillSurface(which: String, backdrop: Backdrop, shape: CornerBasedShape, fallback: GlassStyle): Modifier {
    val space = pillTheme(which) ?: return liquidGlass(backdrop, shape, fallback)
    val frost = pillFrost(which) ?: return liquidGlass(backdrop, shape, fallback)
    val env = LocalControls.current ?: return liquidGlass(backdrop, shape, fallback)
    // W3: the theme's material (wood, metal, stitched leather…) on the pills as on the buttons: the face is drawn over
    // clear glass instead of the glass being tinted.
    val material = currentTheme?.material?.takeIf { space.material && !it.plain }
    var m = controlPanel(space, frost, "SPACE_BAR", backdrop, shape, env.glass, large = false, face = material != null)
    if (material != null) {
        val glassOn = frost.on("SPACE_BAR") && env.glass.quality != GlassQuality.Tint
        val fill = Color(space.fill).let { if (glassOn) it else it.copy(alpha = max(it.alpha, frost.fallbackOpacity)) }
        val text = Color(space.text)
        val accent = HomeTheme.accent
        m = m.drawBehind { drawMaterialFace(material, fill, text, accent, shape, 0f) }
    }
    val picture = rememberThemeImage(space.texture)
    if (picture != null) m = m.drawBehind { drawTexture(picture, space.textureScale, space.textureOpacity, shape) }
    return m
}

/**
 * A panel in a kind's colours (menus, cards, pills): its shadow, its colour over the frost of [surface] where that is
 * glass and something is behind, else the colour made solid, and its border.
 */
private fun Modifier.controlPanel(kind: ControlTheme, frost: ThemeFrost, surface: String, backdrop: Backdrop?, shape: CornerBasedShape, glass: GlassConfig, large: Boolean, face: Boolean = false): Modifier {
    var m = this
    if (kind.shadowDp > 0.01f) {
        val c = kind.shadowColor?.let { Color(it) } ?: Color.Black
        m = m.shadow(kind.shadowDp.dp, shape, clip = false, ambientColor = c, spotColor = c)
    }
    val fill = Color(kind.fill)
    m = if (frost.on(surface) && backdrop != null && glass.quality != GlassQuality.Tint) {
        m.liquidGlass(backdrop, shape, frostStyle(frost, glass, if (face) Color.Transparent else fill, large))
    } else if (face) {
        m
    } else {
        m.clip(shape).background(fill.copy(alpha = max(fill.alpha, frost.fallbackOpacity)))
    }
    val border = kind.border
    if (border != null && kind.borderWidthDp > 0.01f) m = m.border(kind.borderWidthDp.dp, Color(border), shape)
    return m
}

/**
 * The pill that becomes the drawer's field (one element, 0.7.1): the home pill's colour blended into the drawer
 * pill's as [progress] goes from 0 to 1, read while drawing, so the two looks flow into each other without a jump.
 */
@Composable
internal fun Modifier.morphPillSurface(progress: () -> Float, backdrop: Backdrop, shape: CornerBasedShape, fallback: GlassStyle): Modifier {
    val home = pillTheme(dev.liquid.launcher.core.data.ThemePill.HOME) ?: return liquidGlass(backdrop, shape, fallback)
    val drawer = pillTheme(dev.liquid.launcher.core.data.ThemePill.DRAWER) ?: home
    val frost = pillFrost(dev.liquid.launcher.core.data.ThemePill.HOME) ?: return liquidGlass(backdrop, shape, fallback)
    val env = LocalControls.current ?: return liquidGlass(backdrop, shape, fallback)
    val homeFill = Color(home.fill)
    val drawerFill = Color(drawer.fill)
    val glassOn = frost.on("SPACE_BAR") && env.glass.quality != GlassQuality.Tint
    val base = if (glassOn) liquidGlass(backdrop, shape, frostStyle(frost, env.glass, Color.Transparent, large = false)) else this
    val material = currentTheme?.material?.takeIf { home.material && !it.plain }
    val text = Color(home.text)
    val accent = HomeTheme.accent
    return base.drawBehind {
        val blended = androidx.compose.ui.graphics.lerp(homeFill, drawerFill, progress().coerceIn(0f, 1f))
        val fill = if (glassOn) blended else blended.copy(alpha = max(blended.alpha, frost.fallbackOpacity))
        if (material != null) drawMaterialFace(material, fill, text, accent, shape, 0f) else drawPath(outlinePath(shape), fill)
    }
}

/** The outline of cards: the theme's card shape and corners, else rounded 22 dp. */
@Composable
internal fun cardOutline(): Shape {
    val card = currentTheme?.card ?: return androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
    return themeOutline(card.shape, card.cornerDp)
}

/**
 * A card's fill in a scrolling page (no glass per card there, D8): the theme's card colour at its own opacity, with its
 * border; else the launcher's faint pane at [defaultAlpha].
 */
@Composable
internal fun Modifier.cardFill(defaultAlpha: Float): Modifier {
    val card = currentTheme?.card ?: return background(Color.White.copy(alpha = defaultAlpha))
    val shape = themeOutline(card.shape, card.cornerDp)
    var m = background(Color(card.fill))
    val border = card.border
    if (border != null && card.borderWidthDp > 0.01f) m = m.border(card.borderWidthDp.dp, Color(border), shape)
    return m
}

/**
 * Colours of the theme that drawing code reads outside composition (card text in helpers and draw lambdas), as
 * snapshot state like [HomeTheme.accent]: set by the home screen whenever the theme changes.
 */
internal object ThemeInk {
    var card by androidx.compose.runtime.mutableStateOf(Color.White)

    fun update(theme: LiquidTheme?) {
        card = theme?.card?.let { Color(it.text) } ?: Color.White
    }
}

/**
 * The background of a text field (the hub's note, a sheet's search): the theme's button colour made readable without
 * glass, and its border; else a faint pane of [fallback]. The text colour to use with it comes second.
 */
@Composable
internal fun fieldStyle(fallback: Color): Pair<Modifier, Color> {
    val theme = currentTheme ?: return Modifier.background(fallback.copy(alpha = 0.1f), androidx.compose.foundation.shape.RoundedCornerShape(50)) to fallback
    val key = theme.button
    val shape = themeOutline(key.shape, key.cornerDp)
    val fill = Color(key.fill)
    val solid = fill.copy(alpha = max(fill.alpha, if (theme.frost.enabled) theme.frost.fallbackOpacity * 0.55f else fill.alpha))
    var m = Modifier.background(solid, shape)
    val border = key.border
    if (border != null && key.borderWidthDp > 0.01f) m = m.border(key.borderWidthDp.dp, Color(border), shape)
    return m to Color(key.text)
}

/** A round check (multi-select) in the theme's chip colours: filled and ticked when [on]. */
@Composable
internal fun ThemeCheck(on: Boolean, fallbackInk: Color, size: androidx.compose.ui.unit.Dp = 22.dp) {
    val theme = currentTheme
    val chip = theme?.chip
    val fill = if (chip != null) Color(if (on) chip.active else chip.fill) else if (on) HomeTheme.accent else Color.Transparent
    val ink = if (chip != null) Color(if (on) chip.activeText else chip.text) else readableOn(HomeTheme.accent)
    val ring = if (chip != null) Color(chip.text).copy(alpha = if (on) 0f else 0.45f) else fallbackInk.copy(alpha = if (on) 0f else 0.45f)
    androidx.compose.foundation.layout.Box(
        Modifier
            .androidxSize(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(fill.copy(alpha = if (on || chip != null) fill.alpha.coerceAtLeast(0.35f) else 0f))
            .border(1.5.dp, ring, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        if (on) dev.liquid.launcher.core.designsystem.LauncherIcon(dev.liquid.launcher.core.designsystem.LauncherIcons.Check, contentDescription = null, tint = ink, size = size * 0.66f)
    }
}

/** A round icon toggle (pin, favourite) in the theme's round-button colours: active colours when [on]. */
@Composable
internal fun ThemeToggleIcon(icon: Int, on: Boolean, fallbackInk: Color, description: String, onClick: () -> Unit) {
    val tool = currentTheme?.tool
    val tint = if (tool != null) Color(if (on) tool.activeText else tool.text) else if (on) HomeTheme.accent else fallbackInk.copy(alpha = 0.35f)
    androidx.compose.foundation.layout.Box(
        Modifier
            .androidxSize(44.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        if (tool != null) {
            androidx.compose.foundation.layout.Box(
                Modifier.androidxSize(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(if (on) tool.active else tool.fill))
            )
        }
        dev.liquid.launcher.core.designsystem.LauncherIcon(icon, contentDescription = description, tint = tint, size = 18.dp)
    }
}

private fun Modifier.androidxSize(size: androidx.compose.ui.unit.Dp): Modifier = this.then(Modifier.size(size))
