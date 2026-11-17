package dev.liquid.launcher.feature.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.data.PillSlot
import dev.liquid.launcher.core.designsystem.GlassQuality
import dev.liquid.launcher.core.designsystem.GlassStyle
import dev.liquid.launcher.core.settings.LauncherSettings as S
import dev.liquid.launcher.core.settings.SettingsStore

/*
 * Plain snapshots of the settings each part of the home screen uses. Composables read them
 * through SettingsStore.derive, so a change elsewhere in the settings never recomposes them.
 */

data class GridConfig(
    val columns: Int,
    val rows: Int,
    val iconFraction: Float,
    val labels: Boolean,
    val labelSizeSp: Int,
    val marginSideDp: Int,
    val marginTopDp: Int,
    val marginBottomDp: Int,
)

data class ClockConfig(val h24: Boolean, val scale: Float, val showDate: Boolean, val glass: Boolean)

data class GestureConfig(
    val swipeUp: LauncherAction,
    val swipeDown: LauncherAction,
    val doubleTap: LauncherAction,
    val longPress: LauncherAction,
    val pinchIn: LauncherAction = LauncherAction.None,
    val pinchOut: LauncherAction = LauncherAction.None,
    val twoUp: LauncherAction = LauncherAction.None,
    val twoDown: LauncherAction = LauncherAction.None,
    val edgeLeftUp: LauncherAction = LauncherAction.None,
    val edgeLeftDown: LauncherAction = LauncherAction.None,
    val edgeRightUp: LauncherAction = LauncherAction.None,
    val edgeRightDown: LauncherAction = LauncherAction.None,
    val edgeWidthDp: Int = 24,
) {
    val anyTwoFinger: Boolean get() = listOf(pinchIn, pinchOut, twoUp, twoDown).any { it != LauncherAction.None }
}

/** Haptics, opening apps and search, and the speed of the launcher's own animations. */
data class MotionConfig(
    val haptics: Boolean = true,
    val strong: Boolean = false,
    val drawerTick: Boolean = true,
    val pickUpTick: Boolean = true,
    /** "reveal", "scale", "fade" or "system". */
    val launch: String = "reveal",
    /** How search opens: "lift" (D107), "space" (like the drawer, D99), "zoom", "depth" or "fade". */
    val searchTransition: String = "lift",
    /** How presses feel: "squishy", "jelly", "sink", "grow", "shrink", "rise", "tilt", "pop", "fade", "bouncy", "gentle" or "off" (D108, D112). */
    val press: String = "squishy",
    val pressDepth: Float = 0.5f,
    val pressWobble: Float = 0.5f,
    /** Light from the finger on a pressed thing, 0..1 (D112). */
    val pressLight: Float = 0.35f,
    /** 1 = normal; 2 = twice as fast. */
    val speed: Float = 1f,
    /** "calm", "liquid" or "lively" (see Motion). */
    val style: String = "liquid",
)

fun SettingsStore.motionConfig() = MotionConfig(
    haptics = get(S.HapticsEnabled),
    strong = get(S.HapticsStrength) == "strong",
    drawerTick = get(S.HapticDrawer),
    pickUpTick = get(S.HapticPickUp),
    launch = get(S.LaunchAnimation),
    searchTransition = get(S.SearchTransition),
    press = get(S.PressFeel),
    pressDepth = get(S.PressDepth) / 100f,
    pressWobble = get(S.PressWobble) / 100f,
    pressLight = get(S.TouchLight) / 100f,
    speed = get(S.MotionSpeed) / 100f,
    style = get(S.MotionStyle),
)

data class PillConfig(
    val enabled: Boolean,
    val slots: List<PillSlot>,
    val top: Boolean,
    val offsetDp: Int,
    val widthFraction: Float,
    val heightDp: Int,
    val cornerPercent: Int,
    val iconSizeDp: Int,
    val hint: String,
    val tap: LauncherAction,
    val longPress: LauncherAction,
    val swipeUp: LauncherAction,
    val swipeDown: LauncherAction,
    /** How the study item looks (0.9.1). */
    val study: StudyItemLook = StudyItemLook(),
    /** Each item's own gestures, by item code (0.9.1). */
    val slotGestures: Map<String, dev.liquid.launcher.core.data.SlotGestures> = emptyMap(),
)

data class DrawerConfig(
    val columns: Int,
    val iconSizeDp: Int,
    val labels: Boolean,
    val fromBottom: Boolean,
    val autoKeyboard: Boolean,
    val sort: String,
    val work: String,
    val closeOnLaunch: Boolean,
    val dim: Float,
    /** "top", "bottom", "search" (inside the search pill) or "hidden". */
    val tabsPosition: String,
    /** "pill", "chips" or "text". */
    val tabsStyle: String,
    /** "small", "medium" or "large". */
    val tabsSize: String,
    /** "auto", "on" or "off". */
    val tabsIcons: String,
    val tabsButtons: List<String>,
    val tabMenu: List<String>,
    val tabsInSearch: Boolean,
    /** Tag ids shown as sections at the top of the main list. */
    val topSections: List<String>,
    /** Space left and right of the apps, the same on both sides. */
    val sideMarginDp: Int = 8,
) {
    val tabsTop: Boolean get() = tabsPosition == "top"
    val tabsBottom: Boolean get() = tabsPosition == "bottom"
    val tabsInPill: Boolean get() = tabsPosition == "search"
}

/** How folders look and behave. */
data class FolderConfig(
    /** "grid", "stack" or "first". */
    val preview: String = "grid",
    /** "tint", "glass", "accent" or "none". */
    val background: String = "tint",
    /** "icons", "rounded", "circle" or "squircle". */
    val shape: String = "icons",
    val iconShape: String = "system",
    val tapFirst: Boolean = false,
    val columns: Int = 4,
)

fun SettingsStore.folderConfig() = FolderConfig(
    preview = get(S.FolderPreview),
    background = get(S.FolderBackground),
    shape = get(S.FolderShape),
    iconShape = get(S.ThemeIconShape),
    tapFirst = get(S.FolderTapFirst),
    columns = get(S.FolderColumns),
)

/** The drawer's letter index. */
data class IndexConfig(
    /** "always", "scroll" or "touch". */
    val visibility: String,
    /** "right", "left" or "both": the edges the letters can be pulled from. */
    val side: String,
    /** How much of the column the curve spans on each side of the finger, 0.2..1. */
    val reach: Float,
    /** The first and last letter stay on the edge. */
    val pinned: Boolean,
    val letterSp: Int,
    val spacingDp: Int,
    /** "glass", "solid" or "none". */
    val bubble: String,
    /** "beside" (beyond the letters), "above" or "finger": where the bubble sits. */
    val bubblePlace: String = "beside",
    /** How far the curve carries the letters past the finger, towards the middle (dp, 0.6.4). */
    val letterGapDp: Int = 64,
    /** Space between the bent letters and the bubble (dp, 0.6.4). */
    val bubbleGapDp: Int = 32,
    /** "light", "strong" or "off". */
    val haptics: String,
    /** Time constant of the list catching up with the finger, in milliseconds. */
    val followMs: Int,
    /** How far the string follows the finger inwards, 0..1 (1 = the letter comes to the finger). */
    val track: Float,
    /** Stiffness of the column's spring. */
    val stiffness: Float,
    /** Delay before the letters hide after the finger lifts or scrolling stops, ms. */
    val hideMs: Int,
    /** The held letter's apps stay bright, others fade. */
    val focus: Boolean,
)

/** How the drawer opens and scrolls. */
data class DrawerMotion(
    /** "space", "depth", "slide" or "fade". */
    val transition: String,
    /** Fraction of the screen height the finger travels from closed to open. */
    val travel: Float = 0.36f,
    val pillMorph: Boolean,
    val stiffness: Float,
    val damping: Float,
    /** Fraction of the screen height the drawer rises by (depth). */
    val rise: Float,
    /** Fraction the home screen shrinks by. */
    val homeScale: Float,
    val homeBlurDp: Int,
    /** Blur the whole home screen live in every frame (heavy) instead of melting the wallpaper into a blurred copy. */
    val liveBlur: Boolean = false,
    /** "glass", "line" or "off". */
    val scrollbar: String,
    val bubble: Boolean,
    /** "elastic", "curve" or "none". */
    val scrollEffect: String,
    val strength: Float,
    val stretch: Boolean,
) {
    val slide: Boolean get() = transition == "slide"
}

data class SurfaceValues(val blurDp: Int, val refraction: Int, val tint: Int)

/** Everything needed to draw any glass surface. */
data class GlassConfig(
    val quality: GlassQuality,
    val tintMode: String,
    val surfaces: Map<String, SurfaceValues>,
    /** Theme glass colour; null tints with white or black. */
    val tintColor: Color? = null,
    /**
     * Quality of large sheets (folders, lists, the wallpaper screen). The glass benchmark may
     * lower it to frosted; pills, chips, menus and the clock always keep [quality].
     */
    val largeQuality: GlassQuality = quality,
    /** Edge light of every glass surface, 0..1 (0.6.4). */
    val rim: Float = 0f,
    /** What every glass surface adds on top of blur, refraction and tint (0.8.0, D110). */
    val extras: GlassExtras = GlassExtras(),
) {
    private fun values(surface: String) = surfaces[surface] ?: surfaces.getValue(S.GlassDefault.id)

    /** True when the surface's tint is light, so its content should be dark. */
    fun lightGlass(surface: String, lightBackground: Boolean): Boolean {
        val strong = values(surface).tint >= 45
        return when (tintMode) {
            "light" -> strong || lightBackground
            "dark" -> !strong && lightBackground
            else -> lightBackground
        }
    }

    /** [large]: a sheet that covers a good part of the screen (see [largeQuality]). */
    fun style(surface: String, lightBackground: Boolean, large: Boolean = false): GlassStyle {
        val v = values(surface)
        val light = when (tintMode) {
            "light" -> true
            "dark" -> false
            else -> lightBackground
        }
        val base = tintColor ?: if (light) Color.White else Color.Black
        return extras.applyTo(GlassStyle(if (large) largeQuality else quality, v.blurDp.dp, v.refraction / 100f, base.copy(alpha = v.tint / 100f), rim))
    }
}

/** The 0.8.0 glass controls shared by every surface and button (Settings → Liquid glass, D110). */
data class GlassExtras(
    val saturation: Float = 1.5f,
    val brightness: Float = 0f,
    val depth: Boolean = true,
    val dispersion: Boolean = true,
    val glow: Float = 0.2f,
    val shadow: Float = 1f,
    val rimColor: Color = Color.White,
    val lightAngle: Float = 45f,
) {
    fun applyTo(style: GlassStyle): GlassStyle = style.copy(
        saturation = saturation, brightness = brightness, depth = depth, dispersion = dispersion,
        glow = glow, shadow = shadow, rimColor = rimColor, lightAngle = lightAngle,
    )
}

fun SettingsStore.glassExtras() = GlassExtras(
    saturation = get(S.GlassSaturation) / 100f,
    brightness = get(S.GlassBrightness) / 100f,
    depth = get(S.GlassDepth),
    dispersion = get(S.GlassDispersion),
    glow = get(S.GlassGlow) / 100f,
    shadow = get(S.GlassShadow) / 100f,
    rimColor = dev.liquid.launcher.core.settings.ColorSetting.parse(get(S.GlassRimColour))?.let { Color(it) } ?: Color.White,
    lightAngle = get(S.GlassLightAngle).toFloat(),
)

fun SettingsStore.gridConfig() = GridConfig(
    columns = get(S.HomeColumns),
    rows = get(S.HomeRows),
    iconFraction = get(S.HomeIconSize) / 100f,
    labels = get(S.HomeLabels),
    labelSizeSp = get(S.HomeLabelSize),
    marginSideDp = get(S.HomeMarginSide),
    marginTopDp = get(S.HomeMarginTop),
    marginBottomDp = get(S.HomeMarginBottom),
)

fun SettingsStore.clockConfig() = ClockConfig(
    h24 = get(S.Clock24h),
    scale = get(S.ClockSize) / 100f,
    showDate = get(S.ClockDate),
    glass = get(S.ClockGlass),
)

fun SettingsStore.gestureConfig() = GestureConfig(
    swipeUp = LauncherAction.decode(get(S.GestureSwipeUp)),
    swipeDown = LauncherAction.decode(get(S.GestureSwipeDown)),
    doubleTap = LauncherAction.decode(get(S.GestureDoubleTap)),
    longPress = LauncherAction.decode(get(S.GestureLongPress)),
    pinchIn = LauncherAction.decode(get(S.GesturePinchIn)),
    pinchOut = LauncherAction.decode(get(S.GesturePinchOut)),
    twoUp = LauncherAction.decode(get(S.GestureTwoUp)),
    twoDown = LauncherAction.decode(get(S.GestureTwoDown)),
    edgeLeftUp = LauncherAction.decode(get(S.GestureEdgeLeftUp)),
    edgeLeftDown = LauncherAction.decode(get(S.GestureEdgeLeftDown)),
    edgeRightUp = LauncherAction.decode(get(S.GestureEdgeRightUp)),
    edgeRightDown = LauncherAction.decode(get(S.GestureEdgeRightDown)),
    edgeWidthDp = get(S.GestureEdgeWidth),
)

fun SettingsStore.pillConfig() = PillConfig(
    enabled = get(S.PillEnabled),
    slots = PillSlot.decodeAll(get(S.PillSlots)),
    top = get(S.PillPosition) == "top",
    offsetDp = get(S.PillOffset),
    widthFraction = get(S.PillWidth) / 100f,
    heightDp = get(S.PillHeight),
    cornerPercent = get(S.PillCorner),
    iconSizeDp = get(S.PillIconSize),
    hint = get(S.PillHint),
    tap = LauncherAction.decode(get(S.PillTap)),
    longPress = LauncherAction.decode(get(S.PillLongPress)),
    swipeUp = LauncherAction.decode(get(S.PillSwipeUp)),
    swipeDown = LauncherAction.decode(get(S.PillSwipeDown)),
    study = StudyItemLook(
        look = get(S.PillStudyLook),
        glyph = get(S.PillStudyGlyph),
        app = get(S.PillStudyApp),
        picture = get(S.PillStudyPicture),
        frame = get(S.PillStudyFrame),
        sizeDp = get(S.PillStudySize),
        tint = dev.liquid.launcher.core.settings.ColorSetting.parse(get(S.PillStudyTint))?.let { androidx.compose.ui.graphics.Color(it) },
        ring = get(S.PillStudyRing),
    ),
    slotGestures = dev.liquid.launcher.core.data.SlotGestures.decodeAll(get(S.PillSlotGestures)),
)

fun SettingsStore.drawerConfig() = DrawerConfig(
    columns = get(S.DrawerColumns),
    iconSizeDp = get(S.DrawerIconSize),
    labels = get(S.DrawerLabels),
    fromBottom = get(S.DrawerFromBottom),
    autoKeyboard = get(S.DrawerAutoKeyboard),
    sort = get(S.DrawerSort),
    work = get(S.DrawerWork),
    closeOnLaunch = get(S.DrawerCloseOnLaunch),
    dim = get(S.DrawerDim) / 100f,
    tabsPosition = get(S.DrawerTabsPosition),
    tabsStyle = get(S.DrawerTabsStyle),
    tabsSize = get(S.DrawerTabsSize),
    tabsIcons = get(S.DrawerTabsIcons),
    tabsButtons = get(S.DrawerTabsButtons),
    tabMenu = get(S.DrawerTabMenu),
    tabsInSearch = get(S.DrawerTabsInSearch),
    topSections = get(S.DrawerTopSections),
    sideMarginDp = get(S.DrawerSideMargin),
)

fun SettingsStore.indexConfig() = IndexConfig(
    visibility = get(S.IndexVisibility),
    side = get(S.IndexSide),
    reach = get(S.IndexReach) / 100f,
    pinned = get(S.IndexEnds) == "pinned",
    letterSp = get(S.IndexLetterSize),
    spacingDp = get(S.IndexSpacing),
    bubble = get(S.IndexBubble),
    bubblePlace = get(S.IndexBubblePlace),
    letterGapDp = get(S.IndexLetterGap),
    bubbleGapDp = get(S.IndexBubbleGap),
    haptics = get(S.IndexHaptics),
    followMs = get(S.IndexFollow),
    track = get(S.IndexTrack) / 100f,
    stiffness = when (get(S.IndexSpring)) {
        "soft" -> 220f
        "snappy" -> 1100f
        else -> 520f
    },
    hideMs = get(S.IndexHideDelay),
    focus = get(S.IndexFocus),
)

fun SettingsStore.drawerMotion(): DrawerMotion {
    val speed = get(S.DrawerSpeed) / 100f
    return DrawerMotion(
        transition = get(S.DrawerTransition),
        // A sheet stays under the finger; the other transitions finish within a comfortable thumb stroke.
        travel = if (get(S.DrawerTransition) == "slide") 1f else get(S.DrawerTravel) / 100f,
        pillMorph = get(S.DrawerPillMorph),
        // 100 % settles in about a quarter of a second; the scale is quadratic so both ends feel different.
        stiffness = 560f * speed * speed,
        damping = 1f - get(S.DrawerBounce) / 100f,
        rise = get(S.DrawerRise) / 100f,
        homeScale = get(S.DrawerHomeScale) / 100f,
        homeBlurDp = get(S.DrawerHomeBlur),
        liveBlur = get(S.DrawerBlurLive),
        scrollbar = get(S.DrawerScrollbar),
        bubble = get(S.DrawerScrollBubble),
        scrollEffect = get(S.DrawerScrollEffect),
        strength = get(S.DrawerScrollStrength) / 100f,
        stretch = get(S.DrawerOverscroll) != "none",
    )
}

fun SettingsStore.glassConfig(): GlassConfig {
    // The user's choice decides how glass looks. Up to 0.5.0 the benchmark's result capped every
    // surface, so one benchmark run that measured "tint" turned all liquid glass into flat fills.
    // Still glass costs nothing per frame, and small glass is cheap while it moves; the benchmark
    // now only takes the refraction off large sheets.
    val requested = GlassQuality.fromSetting(get(S.GlassQuality))
    val cap = GlassQuality.fromSetting(get(S.GlassCap))
    val capped = get(S.GlassAuto) && cap != GlassQuality.Full
    // A device that draws full glass at under 45 % of its refresh rate (no GPU: an emulator)
    // cannot show it at all; there the cap applies to everything, as before.
    val hopeless = capped && get(S.GlassFullPercent) in 1..44
    val quality = if (hopeless) requested.cappedAt(cap) else requested
    val large = if (capped && quality == GlassQuality.Full) GlassQuality.Frosted else quality
    fun values(surface: S.GlassSurface) = SurfaceValues(get(surface.blur), get(surface.refraction), get(surface.tint))
    val default = values(S.GlassDefault)
    val surfaces = HashMap<String, SurfaceValues>()
    surfaces[S.GlassDefault.id] = default
    for (surface in S.glassSurfaces) {
        val custom = surface.custom
        surfaces[surface.id] = if (custom != null && get(custom)) values(surface) else default
    }
    return GlassConfig(quality, get(S.GlassTintMode), surfaces, dev.liquid.launcher.core.settings.ColorSetting.parse(get(S.ThemeGlassColor))?.let { Color(it) }, large, get(S.GlassRim) / 100f, glassExtras())
}

data class SearchConfig(
    val engine: String,
    val keyboard: Boolean,
    val dim: Float,
    val blurDp: Int,
    /** "keyboard", "bottom" or "top" (0.7.2, D107). */
    val field: String = "keyboard",
    /** How far the home screen falls back as search opens (Lift, Zoom), 0..1. */
    val homeScale: Float = 0.1f,
    /** "questions", "always", "first" or "off" (D109). */
    val claude: String = "questions",
    val clipboard: Boolean = true,
)

fun SettingsStore.searchConfig() = SearchConfig(
    engine = get(S.SearchEngine),
    keyboard = get(S.SearchOpenKeyboard),
    dim = get(S.SearchDim) / 100f,
    blurDp = get(S.SearchBlur),
    field = get(S.SearchField),
    homeScale = get(S.SearchHomeScale) / 100f,
    claude = get(S.SearchClaude),
    clipboard = get(S.SearchClipboard),
)

/** Wallpaper treatment on the home screen. */
data class WallpaperLook(val homeBlurDp: Int, val homeDim: Float)

fun SettingsStore.wallpaperLook() = WallpaperLook(get(S.WallpaperHomeBlur), get(S.WallpaperHomeDim) / 100f)

/** Glass surface ids, matching LauncherSettings.GlassSurface.id. */
object Surfaces {
    val Pill = S.GlassPill.id
    val Drawer = S.GlassDrawer.id
    val Menu = S.GlassMenu.id
    val Clock = S.GlassClock.id
    val Default = S.GlassDefault.id
}
