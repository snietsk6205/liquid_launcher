package dev.liquid.launcher

import android.app.Application
import android.content.ComponentName
import android.util.Log
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.common.LogLevel
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.feature.home.AppsRepository
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
    lateinit var apps: AppsRepository
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

        wallpaper = WallpaperStore.get(this)
        apps = AppsRepository(this)
        appScope.launch { wallpaper.load() }
        apps.start()
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
