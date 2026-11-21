package dev.liquid.launcher.core.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.net.Uri
import android.util.DisplayMetrics
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.liquid.launcher.core.common.DebugLog
import org.xmlpull.v1.XmlPullParser

/** One shortcut an app declares in its manifest (`android.app.shortcuts`). */
data class StaticShortcut(
    val id: String,
    val label: String,
    val icon: ImageBitmap?,
    val intent: Intent,
)

/**
 * Reads the shortcuts an app declares in its manifest. Only the default launcher may read
 * an app's dynamic shortcuts, so these static ones are what a non-default launcher can offer.
 * Some point at activities the app does not export; opening those fails and says so.
 */
object StaticShortcuts {
    private const val ANDROID = "http://schemas.android.com/apk/res/android"
    private const val META = "android.app.shortcuts"

    fun load(context: Context, component: ComponentName): List<StaticShortcut> = try {
        val pm = context.packageManager
        val info = pm.getActivityInfo(component, PackageManager.GET_META_DATA)
        val xmlId = info.metaData?.getInt(META) ?: 0
        if (xmlId == 0) {
            emptyList()
        } else {
            val res = pm.getResourcesForApplication(component.packageName)
            val iconPx = (40 * context.resources.displayMetrics.density).toInt()
            res.getXml(xmlId).use { parse(it, res, component.packageName, iconPx) }
        }
    } catch (e: Exception) {
        DebugLog.error("Apps", "shortcuts of ${component.packageName}: $e")
        emptyList()
    }

    private fun parse(parser: XmlResourceParser, res: Resources, pkg: String, iconPx: Int): List<StaticShortcut> {
        val result = ArrayList<StaticShortcut>()
        var id: String? = null
        var label: String? = null
        var iconRes = 0
        var enabled = true
        var intent: Intent? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "shortcut" -> {
                        id = parser.getAttributeValue(ANDROID, "shortcutId")
                        label = text(parser, res, "shortcutShortLabel") ?: text(parser, res, "shortcutLongLabel")
                        iconRes = parser.getAttributeResourceValue(ANDROID, "icon", 0)
                        enabled = parser.getAttributeBooleanValue(ANDROID, "enabled", true)
                        intent = null
                    }
                    // Several intents form a back stack; the last one is what opens.
                    "intent" -> intent = Intent(parser.getAttributeValue(ANDROID, "action") ?: Intent.ACTION_VIEW).apply {
                        val target = parser.getAttributeValue(ANDROID, "targetPackage") ?: pkg
                        parser.getAttributeValue(ANDROID, "targetClass")?.let { setClassName(target, it) }
                        parser.getAttributeValue(ANDROID, "data")?.let { data = Uri.parse(it) }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "shortcut") {
                    val shortcutIntent = intent
                    if (enabled && id != null && label != null && shortcutIntent != null) {
                        val icon = if (iconRes == 0) null else try {
                            res.getDrawableForDensity(iconRes, DisplayMetrics.DENSITY_XXHIGH, null)?.toBitmap(iconPx, iconPx)?.asImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                        result += StaticShortcut(id, label, icon, shortcutIntent)
                    }
                    id = null
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun text(parser: XmlResourceParser, res: Resources, attr: String): String? {
        val id = parser.getAttributeResourceValue(ANDROID, attr, 0)
        if (id != 0) return try {
            res.getString(id)
        } catch (e: Exception) {
            null
        }
        return parser.getAttributeValue(ANDROID, attr)
    }
}
