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
    val searchDepth: Boolean = true,
    /** 1 = normal; 2 = twice as fast. */
    val speed: Float = 1f,
)

fun SettingsStore.motionConfig() = MotionConfig(
    haptics = get(S.HapticsEnabled),
    strong = get(S.HapticsStrength) == "strong",
    drawerTick = get(S.HapticDrawer),
    pickUpTick = get(S.HapticPickUp),
    launch = get(S.LaunchAnimation),
    searchDepth = get(S.SearchTransition) == "depth",
    speed = get(S.MotionSpeed) / 100f,
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
    val right: Boolean,
    val curve: Float,
    val letterSp: Int,
    val spacingDp: Int,
    /** "glass", "solid" or "none". */
    val bubble: String,
    /** "light", "strong" or "off". */
    val haptics: String,
    /** Time constant of the list catching up with the finger, in milliseconds. */
    val followMs: Int,
    /** How far the letter column slides towards the finger, 0..1. */
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
    /** "depth", "slide" or "fade". */
    val transition: String,
    val pillMorph: Boolean,
    val stiffness: Float,
    val damping: Float,
    /** Fraction of the screen height the drawer rises by (depth). */
    val rise: Float,
    /** Fraction the home screen shrinks by. */
    val homeScale: Float,
    val homeBlurDp: Int,
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

    fun style(surface: String, lightBackground: Boolean): GlassStyle {
        val v = values(surface)
        val light = when (tintMode) {
            "light" -> true
            "dark" -> false
            else -> lightBackground
        }
        val base = tintColor ?: if (light) Color.White else Color.Black
        return GlassStyle(quality, v.blurDp.dp, v.refraction / 100f, base.copy(alpha = v.tint / 100f))
    }
}

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
    right = get(S.IndexSide) != "left",
    curve = get(S.IndexCurve) / 100f,
    letterSp = get(S.IndexLetterSize),
    spacingDp = get(S.IndexSpacing),
    bubble = get(S.IndexBubble),
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
        pillMorph = get(S.DrawerPillMorph),
        // 100 % is a little softer than Spring.StiffnessMediumLow; the scale is quadratic so both ends feel different.
        stiffness = 380f * speed * speed,
        damping = 1f - get(S.DrawerBounce) / 100f,
        rise = get(S.DrawerRise) / 100f,
        homeScale = get(S.DrawerHomeScale) / 100f,
        homeBlurDp = get(S.DrawerHomeBlur),
        scrollbar = get(S.DrawerScrollbar),
        bubble = get(S.DrawerScrollBubble),
        scrollEffect = get(S.DrawerScrollEffect),
        strength = get(S.DrawerScrollStrength) / 100f,
        stretch = get(S.DrawerOverscroll) != "none",
    )
}

fun SettingsStore.glassConfig(): GlassConfig {
    val requested = GlassQuality.fromSetting(get(S.GlassQuality))
    val quality = if (get(S.GlassAuto)) requested.cappedAt(GlassQuality.fromSetting(get(S.GlassCap))) else requested
    fun values(surface: S.GlassSurface) = SurfaceValues(get(surface.blur), get(surface.refraction), get(surface.tint))
    val default = values(S.GlassDefault)
    val surfaces = HashMap<String, SurfaceValues>()
    surfaces[S.GlassDefault.id] = default
    for (surface in S.glassSurfaces) {
        val custom = surface.custom
        surfaces[surface.id] = if (custom != null && get(custom)) values(surface) else default
    }
    return GlassConfig(quality, get(S.GlassTintMode), surfaces, dev.liquid.launcher.core.settings.ColorSetting.parse(get(S.ThemeGlassColor))?.let { Color(it) })
}

data class SearchConfig(val engine: String, val keyboard: Boolean, val dim: Float, val blurDp: Int)

fun SettingsStore.searchConfig() = SearchConfig(get(S.SearchEngine), get(S.SearchOpenKeyboard), get(S.SearchDim) / 100f, get(S.SearchBlur))

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
