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
import androidx.compose.runtime.remember
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

    when (val screen = stack.last()) {
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
