package dev.liquid.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.togetherWith
import dev.liquid.launcher.core.settings.observe
import dev.liquid.launcher.core.designsystem.LauncherTheme
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.settings.SettingsStore

internal sealed interface Screen {
    data object Main : Screen
    data class Section(val id: String) : Screen
    /** W4: one of the seven entries. */
    data class Entry(val entry: dev.liquid.launcher.core.settings.SettingsEntry) : Screen
    /** W4: a page, on [tab], with [highlight] (an option's key) lit up. */
    data class Page(val id: String, val tab: dev.liquid.launcher.core.settings.SettingsTab? = null, val highlight: String? = null) : Screen
    data object Setup : Screen
    data object Calibration : Screen
    data object Diagnostics : Screen
    data object GlassBench : Screen
    data object GlassTune : Screen
    data object Log : Screen
    data object About : Screen
    data object Updates : Screen
    data object Changelog : Screen
    data object ThemeLab : Screen
}

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val store = SettingsStore.get(this)
        val wallpaper = WallpaperStore.get(this)
        val start = intent?.getStringExtra(EXTRA_SCREEN)
        setContent {
            LauncherTheme {
                SettingsApp(store, wallpaper, start, window, onExit = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_SETUP = "setup"
        const val SCREEN_DIAGNOSTICS = "diagnostics"
        const val SCREEN_LOG = "log"
        const val SCREEN_GLASS = "glass"
        const val SCREEN_UPDATES = "updates"
        const val SCREEN_THEME_LAB = "themeLab"
        const val SECTION_PREFIX = "section:"
        /** W4: "page:pill" opens a page of Settings (long press → Customise on the home screen). */
        const val PAGE_PREFIX = "page:"

        fun intent(context: Context, screen: String? = null): Intent =
            Intent(context, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_SCREEN, screen)
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
private fun SettingsApp(
    store: SettingsStore,
    wallpaper: WallpaperStore,
    start: String?,
    window: Window,
    onExit: () -> Unit,
) {
    val stack = remember {
        mutableStateListOf<Screen>(Screen.Main).apply {
            when (start) {
                SettingsActivity.SCREEN_SETUP -> add(Screen.Setup)
                SettingsActivity.SCREEN_DIAGNOSTICS -> add(Screen.Diagnostics)
                SettingsActivity.SCREEN_LOG -> add(Screen.Log)
                SettingsActivity.SCREEN_UPDATES -> add(Screen.Updates)
                SettingsActivity.SCREEN_THEME_LAB -> add(Screen.ThemeLab)
                SettingsActivity.SCREEN_GLASS -> {
                    add(Screen.Diagnostics)
                    add(Screen.GlassBench)
                }
                else -> if (start != null && start.startsWith(SettingsActivity.SECTION_PREFIX)) {
                    // W4: a 0.9 section (a link from the home screen, or a gesture set to open one) opens its page.
                    val section = start.removePrefix(SettingsActivity.SECTION_PREFIX)
                    add(pageForSection(section) ?: Screen.Section(section))
                } else if (start != null && start.startsWith(SettingsActivity.PAGE_PREFIX)) {
                    // "page:study" or "page:study:behaviour": a page, on a tab. 0.13.0: "page:pill:layout:pill.live.music" also
                    // lights up one option (a setting found by the launcher's search).
                    val parts = start.removePrefix(SettingsActivity.PAGE_PREFIX).split(':')
                    val tab = parts.getOrNull(1)
                    add(
                        Screen.Page(
                            parts[0],
                            dev.liquid.launcher.core.settings.SettingsTab.entries.firstOrNull { it.name.equals(tab, ignoreCase = true) },
                            parts.getOrNull(2)?.takeIf { it.isNotBlank() },
                        )
                    )
                }
            }
        }
    }
    val open: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onExit() }
    BackHandler(enabled = stack.size > 1, onBack = back)
    // A breadcrumb: back to that screen when it is on the way back, else to it from the first screen.
    val backTo: (Screen) -> Unit = { target ->
        val at = stack.indexOfLast { it == target }
        if (at >= 0) {
            while (stack.size > at + 1) stack.removeAt(stack.lastIndex)
        } else {
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
            if (target != Screen.Main) stack.add(target)
        }
    }
    val speed by store.observe(dev.liquid.launcher.core.settings.LauncherSettings.MotionSpeed)

    // 0.15.0 (D191): WM Keyboard's push. The page arriving comes the whole way in from the right while the one behind it
    // drifts a third of the way left (back: the same, played in reverse); both are opaque, so nothing fades. A row's icon
    // tile flies into the heading of the page it opens.
    androidx.compose.animation.SharedTransitionLayout {
        val shared = this
        androidx.compose.animation.AnimatedContent(
            targetState = Frame(stack.size, stack.last()),
            transitionSpec = {
                val ms = (NavTransitionMs * 100 / speed.coerceIn(50, 200))
                val spec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(ms, easing = NavTransitionEasing)
                val forward = targetState.depth >= initialState.depth
                val move = if (forward) {
                    androidx.compose.animation.slideInHorizontally(spec) { it } togetherWith
                        androidx.compose.animation.slideOutHorizontally(spec) { -it / 3 }
                } else {
                    androidx.compose.animation.slideInHorizontally(spec) { -it / 3 } togetherWith
                        androidx.compose.animation.slideOutHorizontally(spec) { it }
                }
                move.apply { targetContentZIndex = targetState.depth.toFloat() }
            },
            contentKey = { it.depth to it.screen },
            label = "settingsNav",
        ) { frame ->
            androidx.compose.runtime.CompositionLocalProvider(
                LocalSharedScope provides shared,
                LocalNavScope provides this,
                LocalScreenInfo provides screenInfo(frame.screen),
                LocalBackTo provides remember(frame.screen) { { depth: Int -> crumbTarget(frame.screen, depth)?.let(backTo) } },
            ) {
                ScreenContent(frame.screen, store, wallpaper, window, open, back)
            }
        }
    }
}

/** One screen of the back stack, and how deep it is (deeper: pushed, shallower: back). */
private data class Frame(val depth: Int, val screen: Screen)

/** The screen a breadcrumb names: 0 the first screen, 1 the entry, 2 the page itself. */
private fun crumbTarget(screen: Screen, depth: Int): Screen? = when (depth) {
    0 -> Screen.Main
    1 -> when (screen) {
        is Screen.Page -> dev.liquid.launcher.core.settings.SettingsMap.surface(screen.id)?.entry?.let { Screen.Entry(it) }
        is Screen.Section -> pageForSection(screen.id)?.let { p -> dev.liquid.launcher.core.settings.SettingsMap.surface(p.id)?.entry?.let { Screen.Entry(it) } }
        Screen.ThemeLab -> Screen.Entry(dev.liquid.launcher.core.settings.SettingsEntry.LOOK)
        is Screen.Entry, Screen.Main -> null
        else -> Screen.Entry(dev.liquid.launcher.core.settings.SettingsEntry.SYSTEM)
    }
    else -> null
}

/** Each screen's hue, icon, breadcrumbs and the key its tile flies by. */
private fun screenInfo(screen: Screen): ScreenInfo {
    val system = dev.liquid.launcher.core.settings.SettingsEntry.SYSTEM
    fun tool(icon: Int, title: String, key: String) = ScreenInfo(
        entryAccent(system), icon,
        listOf(Crumb("Settings", 0), Crumb(system.title, 1), Crumb(title, 2)), "tool:$key",
    )
    fun page(id: String): ScreenInfo {
        val surface = dev.liquid.launcher.core.settings.SettingsMap.surface(id) ?: return ScreenInfo()
        return ScreenInfo(
            entryAccent(surface.entry), surfaceIcon(surface.id, surface.entry),
            listOf(Crumb("Settings", 0), Crumb(surface.entry.title, 1), Crumb(surface.title, 2)), "page:${surface.id}",
        )
    }
    return when (screen) {
        Screen.Main -> ScreenInfo(null, null, emptyList(), null)
        is Screen.Entry -> ScreenInfo(
            entryAccent(screen.entry), entryIcon(screen.entry),
            listOf(Crumb("Settings", 0), Crumb(screen.entry.title, 1)), "entry:${screen.entry.name}",
        )
        is Screen.Page -> page(screen.id)
        is Screen.Section -> pageForSection(screen.id)?.let { page(it.id) } ?: ScreenInfo()
        Screen.Setup -> tool(dev.liquid.launcher.core.designsystem.LauncherIcons.CheckCircle, "Setup checklist", "setup")
        Screen.Calibration -> tool(dev.liquid.launcher.core.designsystem.LauncherIcons.Tune, "Calibration", "calibration")
        Screen.Diagnostics -> tool(dev.liquid.launcher.core.designsystem.LauncherIcons.Info, "Diagnostics", "diagnostics")
        Screen.GlassBench -> tool(dev.liquid.launcher.core.designsystem.LauncherIcons.Layers, "Glass benchmark", "glass")
        Screen.GlassTune -> ScreenInfo(
            entryAccent(dev.liquid.launcher.core.settings.SettingsEntry.LOOK), dev.liquid.launcher.core.designsystem.LauncherIcons.Layers,
            listOf(Crumb("Settings", 0), Crumb(dev.liquid.launcher.core.settings.SettingsEntry.LOOK.title, 1), Crumb("Tune the glass", 2)), "tool:glassTune",
        )
        Screen.Log -> tool(dev.liquid.launcher.core.designsystem.LauncherIcons.Code, "Debug log", "log")
        Screen.About -> tool(dev.liquid.launcher.core.designsystem.LauncherIcons.Info, "About", "about")
        Screen.Updates -> tool(dev.liquid.launcher.core.designsystem.LauncherIcons.ArrowDown, "Updates", "updates")
        Screen.Changelog -> tool(dev.liquid.launcher.core.designsystem.LauncherIcons.Pages, "All changes", "changelog")
        Screen.ThemeLab -> ScreenInfo(
            entryAccent(dev.liquid.launcher.core.settings.SettingsEntry.LOOK), dev.liquid.launcher.core.designsystem.LauncherIcons.Palette,
            listOf(Crumb("Settings", 0), Crumb(dev.liquid.launcher.core.settings.SettingsEntry.LOOK.title, 1), Crumb("Theme lab", 2)), "tool:themeLab",
        )
    }
}

@Composable
private fun ScreenContent(
    screen: Screen,
    store: SettingsStore,
    wallpaper: WallpaperStore,
    window: Window,
    open: (Screen) -> Unit,
    back: () -> Unit,
) {
    when (screen) {
        Screen.Main -> MainScreen(store, wallpaper, open, back)
        is Screen.Section -> pageForSection(screen.id).let { page ->
            PageScreen(store, page?.id ?: screen.id, page?.tab, null, open, back)
        }
        is Screen.Entry -> EntryScreen(store, wallpaper, screen.entry, open, back)
        is Screen.Page -> PageScreen(store, screen.id, screen.tab, screen.highlight, open, back)
        Screen.Setup -> SetupScreen(store, wallpaper, open, back)
        Screen.Calibration -> CalibrationScreen(store, back)
        Screen.Diagnostics -> DiagnosticsScreen(store, open, back)
        Screen.GlassBench -> GlassBenchScreen(store, window, wallpaper, measureFirst = true, onBack = back)
        Screen.GlassTune -> GlassBenchScreen(store, window, wallpaper, measureFirst = false, onBack = back)
        Screen.Log -> LogScreen(back)
        Screen.About -> AboutScreen(back)
        Screen.Updates -> UpdatesScreen(store, open, back)
        Screen.Changelog -> ChangelogScreen(back)
        Screen.ThemeLab -> SettingsPreviews.themeLab?.invoke(back) ?: ScreenScaffold(title = "Theme lab", onBack = back) {
            BodyText("The Theme lab is drawn by the home screen module, which this build did not register.", androidx.compose.ui.Modifier.padding(20.dp))
        }
    }
}
