package dev.liquid.launcher.feature.settings

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.settings.SettingsEntry

/*
 * 0.15.0 (D191, Med: "copy the design and animations of WM Keyboard's settings"): Settings in WM Keyboard's house style
 * (github.com/wasi-master/wmkeyboard, MIT licence, © 2026 Wasi Master; its app/SettingsUi.kt, SettingsMotion.kt and
 * MainActivity.kt's navigation), in the colours of the wallpaper (Material You, Med's choice):
 *
 * - every place has a hue of its own, worn by its icon tiles and, faintly, by its heading;
 * - a page opens with its name large beside its icon tile, under a strip of breadcrumbs, and the name moves into the
 *   bar as the page scrolls;
 * - rows sit on cards stacked into groups: 24 dp corners at the ends of a group, 6 dp where two cards meet, 3 dp apart;
 * - a page is pushed in from the right while the one behind drifts a third of the way left (340 ms, Material's
 *   emphasized-decelerate curve), and a row's icon tile flies into the heading of the page it opens;
 * - a page's rows come in one after the other, rising 6 dp.
 */

// ---- places: their hue and icon ----

/** The hue of each entry, after WM Keyboard's route colours (neighbouring entries never share one). */
internal fun entryAccent(entry: SettingsEntry): Color = when (entry) {
    SettingsEntry.HOME -> Color(0xFF42A5F5)
    SettingsEntry.DRAWER -> Color(0xFF5C6BC0)
    SettingsEntry.STUDY -> Color(0xFF66BB6A)
    SettingsEntry.LOOK -> Color(0xFFEC407A)
    SettingsEntry.WALLPAPER -> Color(0xFF00ACC1)
    SettingsEntry.MOTION -> Color(0xFF7E57C2)
    SettingsEntry.SYSTEM -> Color(0xFFFF7043)
}

internal fun entryIcon(entry: SettingsEntry): Int = when (entry) {
    SettingsEntry.HOME -> LauncherIcons.Home
    SettingsEntry.DRAWER -> LauncherIcons.Apps
    SettingsEntry.STUDY -> LauncherIcons.School
    SettingsEntry.LOOK -> LauncherIcons.Palette
    SettingsEntry.WALLPAPER -> LauncherIcons.Image
    SettingsEntry.MOTION -> LauncherIcons.Sparkle
    SettingsEntry.SYSTEM -> LauncherIcons.Settings
}

/** The icon of a page (one surface of the launcher), else its entry's. */
internal fun surfaceIcon(id: String, entry: SettingsEntry): Int = when (id) {
    "home" -> LauncherIcons.Pages
    "pill" -> LauncherIcons.Space
    "dock" -> LauncherIcons.Apps
    "folders" -> LauncherIcons.Folder
    "clock" -> LauncherIcons.Schedule
    "menus" -> LauncherIcons.Layers
    "gestures" -> LauncherIcons.Tune
    "drawer" -> LauncherIcons.Apps
    "tags" -> LauncherIcons.Label
    "index" -> LauncherIcons.Sort
    "search" -> LauncherIcons.Search
    "keyboard" -> LauncherIcons.Code
    "study" -> LauncherIcons.School
    "theme" -> LauncherIcons.Palette
    "icons" -> LauncherIcons.Star
    "glass" -> LauncherIcons.Layers
    "buttons" -> LauncherIcons.Tune
    "wallpaper" -> LauncherIcons.Image
    "layouts" -> LauncherIcons.Pages
    "motion" -> LauncherIcons.Sparkle
    "effects" -> LauncherIcons.Sparkle
    "moving" -> LauncherIcons.Shuffle
    "notices" -> LauncherIcons.Notifications
    "redirect" -> LauncherIcons.Recents
    "safety" -> LauncherIcons.Lock
    "backup" -> LauncherIcons.Save
    "log" -> LauncherIcons.Code
    else -> entryIcon(entry)
}

/** One step of the breadcrumbs: its name, and how deep in the back stack the screen it names is (tap: back there). */
internal data class Crumb(val label: String, val depth: Int)

/** What the screen being drawn is: its hue, its icon, the way to it, and the key its icon tile flies by. */
internal data class ScreenInfo(
    val accent: Color? = null,
    val icon: Int? = null,
    val crumbs: List<Crumb> = emptyList(),
    val flightKey: String? = null,
)

internal val LocalScreenInfo = staticCompositionLocalOf { ScreenInfo() }

/** Back to the screen [depth] deep (a breadcrumb). */
internal val LocalBackTo = staticCompositionLocalOf<(Int) -> Unit> { {} }

/** The hue rows on this screen wear: the screen's own, else the theme's primary. */
@Composable
internal fun screenAccent(): Color = LocalScreenInfo.current.accent ?: MaterialTheme.colorScheme.primary

/** The background is dark (the theme follows the system). */
@Composable
internal fun darkSettings(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

// ---- icon tiles ----

internal val IconTileSize = 40.dp
private val IconTileRadius = 13.dp

/**
 * A rounded tile tinted with [accent] behind an icon: WM Keyboard's most recognisable piece. Its glyph takes the accent,
 * a little darker on a light background so it reads.
 */
@Composable
internal fun IconTile(icon: Int, accent: Color, modifier: Modifier = Modifier, size: Dp = IconTileSize) {
    val dark = darkSettings()
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(IconTileRadius * (size / IconTileSize)))
            .background(accent.copy(alpha = if (dark) 0.24f else 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        LauncherIcon(icon, contentDescription = null, tint = if (dark) accent else lerp(accent, Color.Black, 0.28f), size = size * 0.55f)
    }
}

// ---- the heading's colours ----

/** The heading at the top of a page (it only warms towards the place's hue) and the bar it leaves when scrolled. */
internal data class BarTints(val expanded: Color, val collapsed: Color)

@Composable
internal fun barTints(accent: Color?): BarTints {
    val surface = MaterialTheme.colorScheme.surface
    val container = MaterialTheme.colorScheme.surfaceContainer
    if (accent == null) return BarTints(surface, container)
    val dark = darkSettings()
    return BarTints(
        expanded = lerp(surface, accent, if (dark) 0.07f else 0.05f),
        collapsed = lerp(container, accent, if (dark) 0.18f else 0.13f),
    )
}

// ---- grouped cards ----

/** The radius at the two ends of a group's stack of cards, and where one card meets the next. */
private val GroupEndCorner = 24.dp
private val GroupInnerCorner = 6.dp
private val GroupGap = 3.dp

/** Rows inside a group draw no card of their own: the group draws them. */
internal val LocalInGroup = compositionLocalOf { false }

/**
 * Rows stacked into one group, each on its own card (WM Keyboard's GroupCard): the ends of the stack rounded at 24 dp,
 * the cards between at 6 dp, 3 dp apart. A row with nothing to show takes no card and no gap. The cards are drawn by
 * the group itself, from where its rows were placed, so any row (an option, a link, an editor) can be part of one.
 */
@Composable
internal fun GroupCards(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val color = MaterialTheme.colorScheme.surfaceContainer
    val bounds = remember { ArrayList<Pair<Float, Float>>() }
    Layout(
        content = { CompositionLocalProvider(LocalInGroup provides true, content = content) },
        modifier = modifier
            .padding16()
            .drawBehind {
                val end = GroupEndCorner.toPx()
                val inner = GroupInnerCorner.toPx()
                val shown = bounds.filter { it.second > it.first }
                shown.forEachIndexed { i, (top, bottom) ->
                    val t = if (i == 0) end else inner
                    val b = if (i == shown.lastIndex) end else inner
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = 0f, top = top, right = size.width, bottom = bottom,
                                topLeftCornerRadius = CornerRadius(t), topRightCornerRadius = CornerRadius(t),
                                bottomRightCornerRadius = CornerRadius(b), bottomLeftCornerRadius = CornerRadius(b),
                            ),
                        )
                    }
                    drawPath(path, color)
                }
            },
    ) { measurables, constraints ->
        val gap = GroupGap.roundToPx()
        val loose = constraints.copy(minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        var y = 0
        val positions = IntArray(placeables.size)
        var first = true
        placeables.forEachIndexed { i, p ->
            if (p.height > 0 && !first) y += gap
            positions[i] = y
            if (p.height > 0) {
                y += p.height
                first = false
            }
        }
        bounds.clear()
        placeables.forEachIndexed { i, p -> bounds += positions[i].toFloat() to (positions[i] + p.height).toFloat() }
        layout(constraints.maxWidth, y) {
            placeables.forEachIndexed { i, p -> p.place(0, positions[i]) }
        }
    }
}

private fun Modifier.padding16(): Modifier = this.padding(horizontal = 16.dp)

/** A card of its own for a row that is not in a group: rounded all round, at the page's margins. */
@Composable
internal fun Modifier.loneCard(): Modifier {
    if (LocalInGroup.current) return this
    return this
        .padding(horizontal = 16.dp, vertical = 2.dp)
        .clip(RoundedCornerShape(GroupEndCorner))
        .background(MaterialTheme.colorScheme.surfaceContainer)
}

// ---- motion ----

/** A page push's length, and its curve: Material's emphasized decelerate (leaves fast, lands slowly). */
internal const val NavTransitionMs = 340
internal val NavTransitionEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
private val FlightBounds = BoundsTransform { _, _ -> tween(NavTransitionMs, easing = FastOutSlowInEasing) }

/**
 * One end of a flight between screens: a row's icon tile and the heading's of the page it opens. Nothing when the screens
 * are not animating (or outside the navigation).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.flight(key: String?): Modifier {
    if (key == null) return this
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalNavScope.current ?: return this
    return with(shared) {
        this@flight.sharedElement(rememberSharedContentState(key), anim, boundsTransform = FlightBounds, zIndexInOverlay = 1f)
    }
}

/** How long one row takes to settle in, the beat between two, and how many beats at most (the rest come together). */
private const val RowInMs = 220
private const val RowStepMs = 35
private const val RowMaxSteps = 8
private val RowRise = 6.dp

/**
 * A row arriving with its page: fades in and rises 6 dp, [index] beats after the first (WM Keyboard's StaggerIn). Read in
 * the draw phase; plays once, when the row is first composed.
 */
@Composable
internal fun Modifier.staggerIn(index: Int): Modifier {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        shown.animateTo(1f, tween(RowInMs, delayMillis = RowStepMs * index.coerceIn(0, RowMaxSteps), easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        val t = shown.value
        alpha = t
        translationY = (1f - t) * RowRise.toPx()
    }
}

