package dev.liquid.launcher

import android.app.Application
import android.content.ComponentName
import android.util.Log
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.common.LogLevel
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.data.LauncherData
import dev.liquid.launcher.core.data.RedirectTarget
import dev.liquid.launcher.feature.home.HomePreviews
import dev.liquid.launcher.feature.home.HomeSectionPreview
import dev.liquid.launcher.feature.settings.SettingsPreviews
import dev.liquid.launcher.feature.redirect.RedirectHooks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The hand-written object graph: everything that lives as long as the process. */
class LauncherApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var wallpaper: WallpaperStore
        private set
    lateinit var data: LauncherData
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        DebugLog.sink = { entry ->
            val priority = if (entry.level == LogLevel.ERROR) Log.ERROR else Log.INFO
            Log.println(priority, LOGCAT_TAG, "${entry.tag}: ${entry.message}")
        }
        installCrashLogger()

        settings = SettingsStore.get(this)
        DebugLog.enabled = settings.get(LauncherSettings.LogEnabled)
        DebugLog.maxEntries = settings.get(LauncherSettings.LogSize)
        DebugLog.verbosity = when (settings.get(LauncherSettings.LogVerbosity)) {
            "events" -> LogLevel.EVENT
            "tree" -> LogLevel.TREE
            else -> LogLevel.DECISION
        }

        RedirectHooks.homeComponent = ComponentName(this, MainActivity::class.java)
        RedirectTarget.homeClassName = MainActivity::class.java.name
        SettingsPreviews.sections = HomePreviews.sections
        SettingsPreviews.provider = { sectionId -> HomeSectionPreview(sectionId) }

        wallpaper = WallpaperStore.get(this)
        data = LauncherData.get(this)
        appScope.launch {
            wallpaper.load()
            // Wallpapers of earlier versions (history, current) join the library once.
            data.wallpapers.migrate(wallpaper.legacyHistoryDir, wallpaper.currentFile)
        }
        data.apps.start()
        watchScreen()
    }

    /** Automatic wallpaper changes happen while the screen is off, ready for the next unlock. */
    private fun watchScreen() {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                if (intent.action != android.content.Intent.ACTION_SCREEN_OFF) return
                if (settings.get(LauncherSettings.WallpaperRotation) == "off") return
                val pending = goAsync()
                appScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                    try {
                        dev.liquid.launcher.feature.home.Wallpapers.onScreenOff(this@LauncherApp, data)
                    } catch (e: Throwable) {
                        DebugLog.error("Wallpaper", "automatic change failed: $e")
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
        registerReceiver(receiver, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF))
    }

    /** No crash reporting service: the last crash is written to a file the debug log can show. */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                java.io.File(filesDir, "last-crash.txt").writeText(Log.getStackTraceString(error))
            } catch (e: Throwable) {
                // Nothing more can be done here.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        const val LOGCAT_TAG = "LiquidLauncher"
    }
}
