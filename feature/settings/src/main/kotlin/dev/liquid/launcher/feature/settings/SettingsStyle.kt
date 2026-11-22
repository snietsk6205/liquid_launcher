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
import dev.liquid.launcher.core.designsystem.kitFlight
import dev.liquid.launcher.core.designsystem.kitLoneCard
import dev.liquid.launcher.core.designsystem.kitStaggerIn

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

/** 0.15.2 (D193): the kit lives in core/designsystem now (the Theme lab uses it too); these are its names here. */
internal typealias Crumb = dev.liquid.launcher.core.designsystem.KitCrumb
internal typealias ScreenInfo = dev.liquid.launcher.core.designsystem.KitScreenInfo
internal val LocalScreenInfo = dev.liquid.launcher.core.designsystem.LocalKitScreen
internal val LocalBackTo = dev.liquid.launcher.core.designsystem.LocalKitBackTo
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
internal val LocalSharedScope = dev.liquid.launcher.core.designsystem.LocalKitShared
internal val LocalNavScope = dev.liquid.launcher.core.designsystem.LocalKitNav
internal val LocalInGroup = dev.liquid.launcher.core.designsystem.LocalKitInGroup
internal const val NavTransitionMs = dev.liquid.launcher.core.designsystem.KitNavMs
internal val NavTransitionEasing = dev.liquid.launcher.core.designsystem.KitNavEasing

@Composable
internal fun screenAccent(): Color = dev.liquid.launcher.core.designsystem.kitAccent()

@Composable
internal fun darkSettings(): Boolean = dev.liquid.launcher.core.designsystem.kitDark()

@Composable
internal fun IconTile(icon: Int, accent: Color, modifier: Modifier = Modifier, size: Dp = dev.liquid.launcher.core.designsystem.KitTileSize) =
    dev.liquid.launcher.core.designsystem.KitTile(icon, accent, modifier, size)

@Composable
internal fun GroupCards(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    dev.liquid.launcher.core.designsystem.KitGroup(modifier, content)

@Composable
internal fun Modifier.loneCard(): Modifier = kitLoneCard()

@Composable
internal fun Modifier.flight(key: String?): Modifier = kitFlight(key)

@Composable
internal fun Modifier.staggerIn(index: Int): Modifier = kitStaggerIn(index)
