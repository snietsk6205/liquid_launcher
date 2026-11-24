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

    /** For work that outlives a screen (the home screen's camera answer saves the photo here, 0.11.2). */
    internal val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        DebugLog.sink = { entry ->
            val priority = if (entry.level == LogLevel.ERROR) Log.ERROR else Log.INFO
            Log.println(priority, LOGCAT_TAG, "${entry.tag}: ${entry.message}")
        }
        installCrashLogger()

        settings = SettingsStore.get(this)
        // P4 (0.9.13): your keyboard theme's accent and glass colour, once, where you never chose your own.
        dev.liquid.launcher.core.data.ThemeApply.firstRun(settings)
        // W3 (0.10.0): the theme becomes a WM Keyboard document, once (the fixed blue accent unless you chose one).
        dev.liquid.launcher.core.data.ThemeStore.migrate010(settings)
        // 0.10.2: the pills lose 0.10.1's dark fill, and your keyboard theme in the wallpaper's colours becomes the
        // default, once (the look before stays as the lab's preset "Before 0.10.2").
        dev.liquid.launcher.core.data.ThemeStore.migrate0102(settings)
        // 0.11.0: the letters hide until used (the letter circle brings them out), once.
        dev.liquid.launcher.core.settings.SettingsMigrations.migrate0110(settings)
        // 0.11.1: the letter circle becomes the first letter area, where it was, once.
        dev.liquid.launcher.core.settings.SettingsMigrations.migrate0111(settings)
        // 0.14.0: Quiz me alone in Today, where the old five templates were never changed, once.
        dev.liquid.launcher.core.settings.SettingsMigrations.migrate0140(settings)
        // W5: the daily backup job, if it is on.
        runCatching { dev.liquid.launcher.core.data.AutoBackup.schedule(this) }
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
        SettingsPreviews.themeLab = { onBack -> dev.liquid.launcher.feature.home.ThemeLab(onBack) }

        wallpaper = WallpaperStore.get(this)
        data = LauncherData.get(this)
        // 0.11.2: a board photo whose camera answer never came (the launcher was restarted meanwhile) is saved now.
        appScope.launch {
            dev.liquid.launcher.core.data.BoardPhotos.sweep(this@LauncherApp, settings)?.let { module ->
                dev.liquid.launcher.core.common.Notices.post(dev.liquid.launcher.core.common.NoticeKind.Study, "Board photo: $module", "Pictures/ESSAI/$module")
            }
        }
        appScope.launch {
            wallpaper.load()
            // Wallpapers of earlier versions (history, current) join the library once.
            data.wallpapers.migrate(wallpaper.legacyHistoryDir, wallpaper.currentFile)
            // 0.16.0 (D202, D203): each layout's own wallpaper, recents and collection; the lock screen's choice. Once.
            dev.liquid.launcher.feature.home.Wallpapers.migrate0160(this@LauncherApp, data)
        }
        data.apps.start()
        watchScreen()
        // 0.13.1 (D180): the wallpaper behind WM Keyboard's keys follows every wallpaper the launcher shows (when on).
        dev.liquid.launcher.feature.settings.KeyboardWallpaper.start(this, settings, appScope)
        // A wallpaper set in another app should show: the launcher stops drawing its own copy
        // (it stays in the library), so picture and colours match again.
        appScope.launch {
            dev.liquid.launcher.core.common.WallpaperTones.outsideChanges.collect { count ->
                if (count > 0) {
                    wallpaper.clear()
                    data.wallpapers.clearCurrent()
                }
            }
        }
    }

    /** Automatic wallpaper changes happen while the screen is off, ready for the next unlock. */
    private fun watchScreen() {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                if (intent.action != android.content.Intent.ACTION_SCREEN_OFF) return
                // A wallpaper still waiting to be handed to the system goes now: nobody is looking.
                wallpaper.flushSystemWrite()
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
        // 0.16.0 (D205): Island's or Insular's work profile paused, back or unlocked: folders of it are read again.
        val profile = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                when (intent.action) {
                    android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE -> dev.liquid.launcher.core.data.IslandProfile.changed("back (not paused)")
                    android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE -> dev.liquid.launcher.core.data.IslandProfile.changed("paused")
                    android.content.Intent.ACTION_MANAGED_PROFILE_UNLOCKED -> dev.liquid.launcher.core.data.IslandProfile.changed("unlocked")
                }
            }
        }
        registerReceiver(
            profile,
            android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                addAction(android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                addAction(android.content.Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            },
        )
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
