package dev.liquid.launcher.core.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import dev.liquid.launcher.core.common.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 0.16.0 (D205): the work profile Island or Insular makes, as far as the launcher can see it from outside: whether one of
 * the two apps is installed (by package: Insular's F-Droid build first, Med's), whether a work profile exists, and
 * whether it is paused. The launcher cannot see inside: whether File Shuttle exists or is on is not known, and is
 * never assumed.
 */
object IslandProfile {
    /** Insular (Island's F-Droid build) and Island, with the name shown for each. */
    private val apps = listOf("com.oasisfeng.island.fdroid" to "Insular", "com.oasisfeng.island" to "Island")

    private val _changes = MutableStateFlow(0)

    /** Ticks when the work profile is paused, resumed or unlocked (set by the app's broadcast receiver). */
    val changes: StateFlow<Int> = _changes

    fun changed(what: String) {
        DebugLog.event("Wallpaper", "work profile $what")
        _changes.value++
    }

    /** "Insular" or "Island" when installed on this phone (the personal side), else null. */
    fun appName(context: Context): String? = apps.firstOrNull { (pkg, _) ->
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) {
            false
        }
    }?.second

    /** The other profile of this phone (the work profile), or null. */
    fun profile(context: Context): UserHandle? = try {
        context.getSystemService(LauncherApps::class.java)?.profiles?.firstOrNull { it != Process.myUserHandle() }
    } catch (e: Exception) {
        null
    }

    /** True while the work profile is paused (its apps and files are off). */
    fun paused(context: Context): Boolean {
        val profile = profile(context) ?: return false
        return try {
            context.getSystemService(UserManager::class.java)?.isQuietModeEnabled(profile) == true
        } catch (e: Exception) {
            false
        }
    }
}
