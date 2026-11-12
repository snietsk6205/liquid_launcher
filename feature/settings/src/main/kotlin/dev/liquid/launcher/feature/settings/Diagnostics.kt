package dev.liquid.launcher.feature.settings

import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.UserManager
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import dev.liquid.launcher.core.designsystem.GlassQuality
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.feature.redirect.RedirectStatus
import dev.liquid.launcher.feature.redirect.SystemLauncher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class DiagStatus(val mark: String) { OK("OK"), WARN("WARN"), FAIL("FAIL"), INFO("INFO") }

internal data class DiagItem(val title: String, val status: DiagStatus, val detail: String)

internal data class GlassResult(
    val quality: GlassQuality,
    val frames: Int,
    val seconds: Float,
    val averageMs: Float,
    val p95Ms: Float,
    val slowPercent: Float,
    val budgetMs: Float,
) {
    val fps: Float get() = if (seconds > 0f) frames / seconds else 0f

    fun line(): String =
        "${quality.name}: ${"%.0f".format(Locale.US, fps)} fps, " +
            "avg ${"%.1f".format(Locale.US, averageMs)} ms, p95 ${"%.1f".format(Locale.US, p95Ms)} ms, " +
            "${"%.0f".format(Locale.US, slowPercent)}% over ${"%.1f".format(Locale.US, budgetMs)} ms"
}

/** Results kept for the lifetime of the process so the report can include the benchmark. */
internal object DiagnosticsState {
    val items = mutableStateOf<List<DiagItem>>(emptyList())
    val glass = mutableStateOf<List<GlassResult>>(emptyList())
}

/** The device behaviours the launcher's architecture depends on, checked on this phone. */
internal object Diagnostics {

    fun run(context: Context, store: SettingsStore): List<DiagItem> {
        val items = ArrayList<DiagItem>()
        fun add(title: String, status: DiagStatus, detail: String) {
            items += DiagItem(title, status, detail)
        }

        fun check(title: String, block: () -> Pair<DiagStatus, String>) {
            try {
                val (status, detail) = block()
                add(title, status, detail)
            } catch (e: Throwable) {
                add(title, DiagStatus.FAIL, "${e.javaClass.simpleName}: ${e.message}")
            }
        }

        val pm = context.packageManager

        check("Device") {
            DiagStatus.INFO to "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} " +
                "(API ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}"
        }

        check("Display") {
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            val rates = display.supportedModes.map { it.refreshRate.toInt() }.distinct().sorted()
            DiagStatus.INFO to "${bounds.width()} x ${bounds.height()}, density ${context.resources.displayMetrics.density}, " +
                "now ${display.refreshRate.toInt()} Hz, supported ${rates.joinToString("/")} Hz"
        }

        check("App") {
            val info = pm.getPackageInfo(context.packageName, 0)
            val installer = try {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } catch (e: Exception) {
                null
            }
            DiagStatus.INFO to "${info.versionName} (build ${info.longVersionCode}), installed by ${installer ?: "unknown"}"
        }

        check("System launcher") {
            val resolved = SystemLauncher.resolve(
                context,
                store.get(LauncherSettings.SystemLauncherPackage),
                store.get(LauncherSettings.LastSystemLauncher),
            )
            val version = SystemLauncher.version(context, resolved)
            val classes = SystemLauncher.homeClasses(context, resolved)
            val status = when {
                version.isEmpty() -> DiagStatus.FAIL
                classes.isEmpty() -> DiagStatus.WARN
                else -> DiagStatus.OK
            }
            status to "$resolved ${version.ifEmpty { "(not installed?)" }}\n" +
                "home activities: ${classes.joinToString { it.substringAfterLast('.') }.ifEmpty { "none found" }}"
        }

        check("Redirect service") {
            val enabled = SetupChecks.accessibilityEnabled(context)
            val connected = RedirectStatus.connected.value
            val status = if (enabled && connected) DiagStatus.OK else DiagStatus.FAIL
            status to "enabled in settings: ${yesNo(enabled)}, running: ${yesNo(connected)}, " +
                "switched on: ${yesNo(store.get(LauncherSettings.RedirectEnabled))}, " +
                "state: ${RedirectStatus.engineState.value.name.lowercase()}"
        }

        check("Redirects") {
            val requested = RedirectStatus.launchesRequested.get()
            val confirmed = RedirectStatus.launchesConfirmed.get()
            val blocked = RedirectStatus.launchBlocked.value
            val status = when {
                blocked -> DiagStatus.FAIL
                requested == 0 -> DiagStatus.WARN
                confirmed < requested - 1 -> DiagStatus.WARN
                else -> DiagStatus.OK
            }
            val note = when {
                blocked -> "\nThe launcher did not reach the front. Grant \"Display over other apps\" in the checklist."
                requested == 0 -> "\nNo redirect yet since the app started. Go home from an app a few times, then run again."
                else -> ""
            }
            status to "$confirmed confirmed of $requested requested\n" +
                "launch to first frame: ${RedirectStatus.launchLatency.summary()}\n" +
                "trigger to first frame: ${RedirectStatus.totalLatency.summary()}$note"
        }

        check("Recents detection") {
            val verified = store.get(LauncherSettings.DetectorVerified)
            val learned = store.get(LauncherSettings.LearnedIds)
            (if (verified) DiagStatus.OK else DiagStatus.WARN) to
                (if (verified) "recents was recognised" else "recents not seen yet: open recents once, then run again") +
                "\nids: ${store.get(LauncherSettings.RecentsIds).joinToString()}" +
                (if (learned.isNotEmpty()) "\nlearned: ${learned.joinToString { it.substringAfter(":id/") }}" else "")
        }

        check("Battery") {
            val ok = SetupChecks.batteryUnrestricted(context)
            (if (ok) DiagStatus.OK else DiagStatus.WARN) to
                if (ok) "not battery-optimised" else "battery optimisation is on: the service may be stopped"
        }

        check("Display over other apps") {
            DiagStatus.INFO to if (SetupChecks.overlayAllowed(context)) "granted" else "not granted (only a fallback)"
        }

        check("Wallpaper") {
            val wm = WallpaperManager.getInstance(context)
            val live = wm.wallpaperInfo?.packageName
            val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            val read = try {
                val d = wm.drawable
                if (d == null) "returned nothing" else "returned ${d.intrinsicWidth} x ${d.intrinsicHeight}"
            } catch (e: Throwable) {
                "refused (${e.javaClass.simpleName})"
            }
            DiagStatus.INFO to "managed by launcher: ${yesNo(WallpaperStore.get(context).hasManagedWallpaper)}\n" +
                "live wallpaper: ${live ?: "no"}\n" +
                "colors available: ${yesNo(colors != null)}\n" +
                "reading the system wallpaper: $read\n" +
                "all-files access: ${yesNo(Environment.isExternalStorageManager())}"
        }

        check("Window blur") {
            val enabled = context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
            DiagStatus.INFO to "cross-window blur: ${if (enabled) "available" else "not available"}"
        }

        check("Profiles and apps") {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            val userManager = context.getSystemService(UserManager::class.java)
            val me = Process.myUserHandle()
            val lines = launcherApps.profiles.map { user ->
                val count = launcherApps.getActivityList(null, user).size
                if (user == me) {
                    "personal: $count apps"
                } else {
                    "work profile: $count apps, paused: ${yesNo(userManager.isQuietModeEnabled(user))}"
                }
            }
            val personal = launcherApps.getActivityList(null, me).size
            (if (personal > 0) DiagStatus.OK else DiagStatus.FAIL) to lines.joinToString("\n") +
                "\nshortcut access (expected: no): ${yesNo(launcherApps.hasShortcutHostPermission())}"
        }

        check("App widgets") {
            if (!pm.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)) {
                return@check DiagStatus.FAIL to "this device does not support app widgets"
            }
            val manager = AppWidgetManager.getInstance(context)
            val providers = manager.installedProviders
            val first = providers.firstOrNull()
                ?: return@check DiagStatus.WARN to "no widget providers installed"
            val host = AppWidgetHost(context, WIDGET_HOST_ID)
            val id = host.allocateAppWidgetId()
            val direct = try {
                manager.bindAppWidgetIdIfAllowed(id, first.provider)
            } finally {
                host.deleteAppWidgetId(id)
            }
            DiagStatus.OK to "${providers.size} widget providers; widget ids can be allocated\n" +
                "binding without a dialog: ${yesNo(direct)} (\"no\" is normal: the system asks once per app)"
        }

        check("Glass") {
            val results = DiagnosticsState.glass.value
            if (results.isEmpty()) {
                DiagStatus.WARN to "benchmark not run yet"
            } else {
                val full = results.firstOrNull { it.quality == GlassQuality.Full }
                val status = if (full != null && full.slowPercent > 10f) DiagStatus.WARN else DiagStatus.OK
                status to results.joinToString("\n") { it.line() }
            }
        }

        return items
    }

    fun report(context: Context, items: List<DiagItem>): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        return buildString {
            append("Liquid Launcher diagnostics, ").append(stamp).append('\n')
            items.forEach { item ->
                append("\n[").append(item.status.mark).append("] ").append(item.title).append('\n')
                item.detail.lines().forEach { append("    ").append(it).append('\n') }
            }
        }
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"

    private const val WIDGET_HOST_ID = 0x4C4C
}
