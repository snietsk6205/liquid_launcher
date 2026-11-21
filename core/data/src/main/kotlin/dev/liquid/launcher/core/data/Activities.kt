package dev.liquid.launcher.core.data

import android.content.Context
import android.content.pm.PackageManager

/** One screen (activity) of an app that other apps may open. */
data class AppActivity(val pkg: String, val cls: String, val label: String) {
    /** The class name without the package, for telling screens with the same label apart. */
    val shortName: String get() = cls.removePrefix(pkg).trimStart('.').substringAfterLast('$')
}

object Activities {
    /** The exported, enabled screens of [pkg], the app's launch screen first, then by label. */
    fun of(context: Context, pkg: String): List<AppActivity> = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
        val launch = pm.getLaunchIntentForPackage(pkg)?.component?.className
        val appLabel = info.applicationInfo?.loadLabel(pm)?.toString().orEmpty()
        info.activities.orEmpty()
            .filter { it.exported && it.enabled && it.permission == null }
            .map { activity ->
                val label = activity.loadLabel(pm)?.toString().orEmpty()
                AppActivity(pkg, activity.name, if (label.isBlank() || label == appLabel) activity.name.substringAfterLast('.') else label)
            }
            .sortedWith(compareBy<AppActivity> { it.cls != launch }.thenBy { it.label.lowercase() })
    } catch (e: Exception) {
        emptyList()
    }
}
