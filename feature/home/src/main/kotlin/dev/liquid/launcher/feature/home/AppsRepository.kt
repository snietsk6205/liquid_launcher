package dev.liquid.launcher.feature.home

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.liquid.launcher.core.common.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Collator

data class AppEntry(
    val key: String,
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    val icon: ImageBitmap?,
    val isWorkProfile: Boolean,
)

/**
 * Launchable apps of the personal and work profile. This simple version loads every icon up
 * front; Phase 1B replaces it with the icon-pack pipeline and a disk cache.
 */
class AppsRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    private var job: Job? = null
    private var started = false

    val apps: StateFlow<List<AppEntry>> = _apps

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
        override fun onPackagesAvailable(names: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(names: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
    }

    fun start() {
        if (started) return
        started = true
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    fun reload() {
        job?.cancel()
        job = scope.launch {
            try {
                val iconPx = (ICON_DP * context.resources.displayMetrics.density).toInt()
                val own = context.packageName
                val myUser = Process.myUserHandle()
                val collator = Collator.getInstance()
                val result = ArrayList<AppEntry>()
                for (user in launcherApps.profiles) {
                    for (info in launcherApps.getActivityList(null, user)) {
                        ensureActive()
                        if (info.componentName.packageName == own) continue
                        val icon = try {
                            info.getBadgedIcon(0).toBitmap(iconPx, iconPx).asImageBitmap()
                        } catch (e: Throwable) {
                            null
                        }
                        result += AppEntry(
                            key = "${info.componentName.flattenToShortString()}#${user.hashCode()}",
                            component = info.componentName,
                            user = user,
                            label = info.label?.toString().orEmpty(),
                            icon = icon,
                            isWorkProfile = user != myUser,
                        )
                    }
                }
                result.sortWith { a, b -> collator.compare(a.label, b.label) }
                _apps.value = result
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                DebugLog.error("Apps", "loading apps failed: $e")
            }
        }
    }

    fun launch(entry: AppEntry, sourceBounds: Rect?, options: Bundle?) {
        try {
            launcherApps.startMainActivity(entry.component, entry.user, sourceBounds, options)
        } catch (e: Exception) {
            DebugLog.error("Apps", "launch failed for ${entry.component.packageName}: $e")
        }
    }

    fun showAppInfo(entry: AppEntry, sourceBounds: Rect?) {
        try {
            launcherApps.startAppDetailsActivity(entry.component, entry.user, sourceBounds, null)
        } catch (e: Exception) {
            DebugLog.error("Apps", "app info failed: $e")
        }
    }

    private companion object {
        const val ICON_DP = 52
    }
}
