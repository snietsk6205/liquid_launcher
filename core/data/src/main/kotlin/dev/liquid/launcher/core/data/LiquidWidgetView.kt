package dev.liquid.launcher.core.data

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.View
import android.widget.RemoteViews

/**
 * Another app's widget as the launcher hosts it (P7, 0.9.17): after every update the app sends, and whenever its
 * views change (rows of a list arriving), [restyle] runs over its views so the launcher's look is kept on them.
 * With no [restyle] it is a plain widget.
 */
class LiquidWidgetView(context: Context) : AppWidgetHostView(context) {
    var restyle: ((View) -> Unit)? = null
        set(value) {
            field = value
            schedule()
        }

    private var pending = false

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        schedule()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // A child changed (a list's rows arrive later than the widget): look again, once per frame at most.
        schedule()
    }

    private fun schedule() {
        if (pending || restyle == null) return
        pending = true
        post {
            pending = false
            try {
                restyle?.invoke(this)
            } catch (e: Exception) {
                dev.liquid.launcher.core.common.DebugLog.error("Widgets", "restyling failed: $e")
            }
        }
    }
}
