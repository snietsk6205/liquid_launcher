package dev.liquid.launcher.core.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.UserManager
import dev.liquid.launcher.core.common.DebugLog

/**
 * Other apps' widgets (Phase 3, 0.7.0, D100): the launcher's [AppWidgetHost], the list of widgets
 * the phone offers, and the steps to add one (an id, the system's permission to bind it, the
 * widget's own set-up screen). A placed widget is a [HomeItem] of kind WIDGET with widget = "app"
 * and its id in `options["appWidgetId"]` (see `docs/04-widgets.md`).
 *
 * Widget ids belong to this phone: a layout restored elsewhere shows "not available" in their place.
 */
class AppWidgets(private val context: Context) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host: AppWidgetHost = AppWidgetHost(context, HOST_ID)
    private var listening = false

    /** The host receives widget updates only while listening: from onStart to onStop of the home screen. */
    fun listen(on: Boolean) {
        if (on == listening) return
        try {
            if (on) host.startListening() else host.stopListening()
            listening = on
        } catch (e: Exception) {
            DebugLog.error("Widgets", "host " + (if (on) "start" else "stop") + " failed: $e")
        }
    }

    /** Every widget the phone offers, the personal profile first, then the work profile. */
    fun providers(): List<AppWidgetProviderInfo> = try {
        val users = (context.getSystemService(Context.USER_SERVICE) as UserManager).userProfiles
        users.flatMap { manager.getInstalledProvidersForProfile(it) }
    } catch (e: Exception) {
        DebugLog.error("Widgets", "listing widgets failed: $e")
        emptyList()
    }

    fun info(id: Int): AppWidgetProviderInfo? = try {
        manager.getAppWidgetInfo(id)
    } catch (e: Exception) {
        null
    }

    fun allocate(): Int = host.allocateAppWidgetId()

    fun delete(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (e: Exception) {
            DebugLog.error("Widgets", "deleting widget $id failed: $e")
        }
    }

    /** Binds without asking when the user once allowed it for this launcher; false: the system must ask. */
    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean = try {
        manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
    } catch (e: Exception) {
        false
    }

    /** The system's "allow this launcher to add widgets" dialog for [info]. */
    fun bindIntent(id: Int, info: AppWidgetProviderInfo): Intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)

    /** True when the widget has a set-up screen that must run before it is placed. */
    fun needsSetUp(info: AppWidgetProviderInfo): Boolean =
        info.configure != null && (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) == 0

    /**
     * Gives back ids no layout uses any more (removed widgets, cancelled additions). Run at start,
     * not on every change, so undo can still bring a removed widget back during a session.
     */
    fun cleanUp(inUse: Set<Int>) {
        val ids = try {
            host.appWidgetIds
        } catch (e: Exception) {
            return
        }
        val unused = ids.filter { it !in inUse }
        for (id in unused) delete(id)
        if (unused.isNotEmpty()) DebugLog.event("Widgets", "gave back ${unused.size} unused widget id(s)")
    }

    companion object {
        /** The launcher's host id; any constant, fixed so ids survive restarts. */
        const val HOST_ID = 0x4C4C

        /** The widget id an item holds, or null. */
        fun idOf(item: HomeItem): Int? = if (item.kind == ItemKind.WIDGET && item.widget == "app") item.options?.get("appWidgetId")?.toIntOrNull() else null

        /** Widget ids used by [layout] (pages, dock and the search screen). */
        fun idsIn(layout: HomeLayout): Set<Int> =
            (layout.pages.flatMap { it.items } + layout.dock + layout.search).mapNotNull { idOf(it) }.toSet()

        /**
         * A widget's size in cells: the provider's own cell size when it gives one (Android 12+),
         * else its minimum size in pixels over the cell size, at least 1 and at most the grid.
         */
        fun cells(info: AppWidgetProviderInfo, cellWidthPx: Float, cellHeightPx: Float, columns: Int, rows: Int): Pair<Int, Int> =
            cells(info.targetCellWidth, info.targetCellHeight, info.minWidth, info.minHeight, cellWidthPx, cellHeightPx, columns, rows)

        fun cells(targetW: Int, targetH: Int, minWidthPx: Int, minHeightPx: Int, cellWidthPx: Float, cellHeightPx: Float, columns: Int, rows: Int): Pair<Int, Int> {
            val w = targetW.takeIf { it > 0 } ?: kotlin.math.ceil(minWidthPx / cellWidthPx.coerceAtLeast(1f)).toInt()
            val h = targetH.takeIf { it > 0 } ?: kotlin.math.ceil(minHeightPx / cellHeightPx.coerceAtLeast(1f)).toInt()
            return w.coerceIn(1, columns.coerceAtLeast(1)) to h.coerceIn(1, rows.coerceAtLeast(1))
        }
    }
}
