package dev.liquid.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import dev.liquid.launcher.core.designsystem.LauncherTheme
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.settings.SettingsStore

internal sealed interface Screen {
    data object Main : Screen
    data class Section(val id: String) : Screen
    data object Setup : Screen
    data object Calibration : Screen
    data object Diagnostics : Screen
    data object GlassBench : Screen
    data object Log : Screen
    data object About : Screen
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
        const val SECTION_PREFIX = "section:"

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
                SettingsActivity.SCREEN_GLASS -> {
                    add(Screen.Diagnostics)
                    add(Screen.GlassBench)
                }
                else -> if (start != null && start.startsWith(SettingsActivity.SECTION_PREFIX)) {
                    add(Screen.Section(start.removePrefix(SettingsActivity.SECTION_PREFIX)))
                }
            }
        }
    }
    val open: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onExit() }
    BackHandler(enabled = stack.size > 1, onBack = back)

    when (val screen = stack.last()) {
        Screen.Main -> MainScreen(store, wallpaper, open, back)
        is Screen.Section -> SectionScreen(store, screen.id, back)
        Screen.Setup -> SetupScreen(store, wallpaper, open, back)
        Screen.Calibration -> CalibrationScreen(store, back)
        Screen.Diagnostics -> DiagnosticsScreen(store, open, back)
        Screen.GlassBench -> GlassBenchScreen(store, window, wallpaper, back)
        Screen.Log -> LogScreen(back)
        Screen.About -> AboutScreen(back)
    }
}
