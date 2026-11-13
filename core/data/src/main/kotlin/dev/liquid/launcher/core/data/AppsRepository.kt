package dev.liquid.launcher.core.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.liquid.launcher.core.common.DebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator

data class AppEntry(
    val ref: AppRef,
    val component: ComponentName,
    val user: UserHandle,
    /** The label the app ships with. */
    val systemLabel: String,
    /** The label to show: the user's own if set. */
    val label: String,
    val icon: ImageBitmap?,
    val isWorkProfile: Boolean,
    val hidden: Boolean,
    val installedAt: Long,
    /** Ids of the tags given to this app. */
    val tags: List<String> = emptyList(),
    /** Icon on the home screen; differs from [icon] when home and drawer use different packs. */
    val homeIcon: ImageBitmap? = icon,
    /** The user picked this app's icon by hand. */
    val customIcon: Boolean = false,
) {
    val key: String get() = ref.key
}

/**
 * Launchable apps of the personal and work profile, merged with what the user changed about
 * them. Icons are rendered once, in the background, at the size the launcher draws them.
 */
class AppsRepository(
    private val context: Context,
    private val meta: JsonStore<Map<String, AppMeta>>,
    private val usage: UsageStore,
    scope: CoroutineScope,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val loadScope = CoroutineScope(scope.coroutineContext + Dispatchers.Default)
    private val installed = MutableStateFlow<List<AppEntry>>(emptyList())
    private val _loaded = MutableStateFlow(false)
    private var job: Job? = null
    private var started = false

    /** True once the first load has finished. */
    val loaded: StateFlow<Boolean> = _loaded

    /** Icon packs in use; set from the settings by [LauncherData]. */
    val iconConfig = MutableStateFlow(IconConfig())
    private val icons = IconThemer(context)

    /** Every launchable app, sorted by label, including hidden ones (see [AppEntry.hidden]). */
    val apps: StateFlow<List<AppEntry>> = combine(installed, meta.value, iconConfig) { list, changes, config ->
        val collator = Collator.getInstance()
        icons.prepare(config)
        list.map { app ->
            val change = changes[app.key]
            val themed = icons.apply(app, config, change?.icon)
            if (change == null) themed else themed.copy(label = change.label ?: app.systemLabel, hidden = change.hidden, tags = change.tags)
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }.stateIn(loadScope, SharingStarted.Eagerly, emptyList())

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
        job = loadScope.launch {
            try {
                val iconPx = (ICON_DP * context.resources.displayMetrics.density).toInt()
                val homeActivity = RedirectTarget.homeClassName
                val myUser = Process.myUserHandle()
                val result = ArrayList<AppEntry>()
                for (user in launcherApps.profiles) {
                    val serial = userManager.getSerialNumberForUser(user)
                    for (info in launcherApps.getActivityList(null, user)) {
                        ensureActive()
                        val component = info.componentName
                        // The launcher's own home screen is not something to launch from itself.
                        if (component.packageName == context.packageName && component.className == homeActivity) continue
                        val icon = try {
                            info.getBadgedIcon(0).toBitmap(iconPx, iconPx).asImageBitmap()
                        } catch (e: Throwable) {
                            null
                        }
                        val label = info.label?.toString().orEmpty().ifEmpty { component.packageName }
                        result += AppEntry(
                            ref = AppRef(component.packageName, component.className, serial),
                            component = component,
                            user = user,
                            systemLabel = label,
                            label = label,
                            icon = icon,
                            isWorkProfile = user != myUser,
                            hidden = false,
                            installedAt = info.firstInstallTime,
                        )
                    }
                }
                installed.value = result
                _loaded.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                DebugLog.error("Apps", "loading apps failed: $e")
            }
        }
    }

    fun find(ref: AppRef): AppEntry? = apps.value.firstOrNull { it.ref == ref }

    fun launch(entry: AppEntry, sourceBounds: Rect? = null, options: Bundle? = null): Boolean = try {
        LaunchClock.mark()
        launcherApps.startMainActivity(entry.component, entry.user, sourceBounds, options)
        usage.record(entry.key)
        true
    } catch (e: Exception) {
        DebugLog.error("Apps", "launch failed for ${entry.component.packageName}: $e")
        false
    }

    fun showAppInfo(entry: AppEntry, sourceBounds: Rect? = null) {
        try {
            launcherApps.startAppDetailsActivity(entry.component, entry.user, sourceBounds, null)
        } catch (e: Exception) {
            DebugLog.error("Apps", "app info failed: $e")
        }
    }

    fun uninstall(entry: AppEntry) {
        try {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.component.packageName}"))
                .putExtra(Intent.EXTRA_USER, entry.user)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            DebugLog.error("Apps", "uninstall failed: $e")
        }
    }

    /** Sets the user's own label; null or blank goes back to the app's own. */
    fun rename(ref: AppRef, label: String?) = changeMeta(ref) { it.copy(label = label?.trim()?.ifEmpty { null }) }

    fun setHidden(ref: AppRef, hidden: Boolean) = changeMeta(ref) { it.copy(hidden = hidden) }

    /** [icon] is "iconPackPackage|drawableName", or null for the pack's or the app's own icon. */
    fun setIcon(ref: AppRef, icon: String?) = changeMeta(ref) { it.copy(icon = icon) }

    fun setTag(ref: AppRef, tagId: String, on: Boolean) = changeMeta(ref) { meta ->
        meta.copy(tags = if (on) (meta.tags + tagId).distinct() else meta.tags - tagId)
    }

    private fun changeMeta(ref: AppRef, change: (AppMeta) -> AppMeta) {
        meta.update { all ->
            val updated = change(all[ref.key] ?: AppMeta())
            if (updated == AppMeta()) all - ref.key else all + (ref.key to updated)
        }
    }

    internal companion object {
        const val ICON_DP = 56
    }
}

/** When the launcher last started another app, to tell "Home pressed" from "came back from an app". */
object LaunchClock {
    @Volatile
    var last: Long = 0L
        private set

    fun mark() {
        last = android.os.SystemClock.uptimeMillis()
    }
}

/** Set once by the application: the class name of the launcher's own home activity. */
object RedirectTarget {
    @Volatile
    var homeClassName: String = ""
}
