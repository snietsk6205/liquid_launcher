package dev.liquid.launcher.core.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.core.graphics.drawable.toBitmap
import dev.liquid.launcher.core.common.DebugLog
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/** An installed icon pack. */
data class IconPackInfo(val pkg: String, val label: String)

/** What an icon pack's `appfilter.xml` says, independent of Android for unit tests. */
data class AppFilter(
    /** "package/class" to drawable name. */
    val icons: Map<String, String>,
    val backs: List<String>,
    val mask: String?,
    val upon: String?,
    val scale: Float,
) {
    companion object {
        /**
         * "ComponentInfo{com.app/com.app.Main}" to "com.app/com.app.Main"; a class starting with
         * a dot is relative to the package. Null when the text is not a component.
         */
        fun component(raw: String): String? {
            val inner = raw.substringAfter("ComponentInfo{", "").substringBefore("}", "")
            val pkg = inner.substringBefore('/', "")
            val cls = inner.substringAfter('/', "")
            if (pkg.isBlank() || cls.isBlank()) return null
            return "$pkg/${if (cls.startsWith(".")) pkg + cls else cls}"
        }
    }
}

/** Builds an [AppFilter] while reading the XML; split out so parsing stays testable. */
class AppFilterBuilder {
    private val icons = HashMap<String, String>()
    private val backs = ArrayList<String>()
    private var mask: String? = null
    private var upon: String? = null
    private var scale = 1f

    fun element(name: String, attr: (String) -> String?) {
        when (name) {
            "item" -> {
                val component = attr("component")?.let(AppFilter::component) ?: return
                val drawable = attr("drawable")?.takeIf { it.isNotBlank() } ?: return
                icons.putIfAbsent(component, drawable)
            }
            "iconback" -> for (i in 1..9) attr("img$i")?.takeIf { it.isNotBlank() }?.let(backs::add)
            "iconmask" -> mask = attr("img1") ?: attr("img")
            "iconupon" -> upon = attr("img1") ?: attr("img")
            "scale" -> scale = attr("factor")?.toFloatOrNull()?.coerceIn(0.2f, 1.5f) ?: 1f
        }
    }

    fun build() = AppFilter(icons, backs, mask, upon, scale)
}

/** One loaded pack: finds icons for apps and draws the pack's frame around the rest. */
class IconPack internal constructor(
    val info: IconPackInfo,
    private val res: Resources,
    val filter: AppFilter,
    private val drawableNames: () -> List<String>,
) {
    private fun drawable(name: String): Drawable? = try {
        val id = res.getIdentifier(name, "drawable", info.pkg)
        if (id == 0) null else res.getDrawableForDensity(id, DisplayMetrics.DENSITY_XXXHIGH, null)
    } catch (e: Exception) {
        null
    }

    /** The pack's own drawing for [name], or null. */
    fun bitmap(name: String, sizePx: Int): Bitmap? = drawable(name)?.toBitmap(sizePx, sizePx)

    /** The pack's icon for [component], or null when the pack does not cover it. */
    fun iconFor(component: ComponentName, sizePx: Int): Bitmap? {
        val name = filter.icons["${component.packageName}/${component.className}"] ?: return null
        return bitmap(name, sizePx)
    }

    val canMask: Boolean get() = filter.backs.isNotEmpty() || filter.mask != null || filter.upon != null

    /**
     * An app the pack does not cover, fitted to the pack's look: its background, the app's icon
     * scaled down and cut by the mask, then the overlay. Picks the background by app so the
     * same app always gets the same one.
     */
    /** True when the pack draws a background behind icons it fits to its style. */
    val hasBack: Boolean get() = filter.backs.isNotEmpty()

    fun masked(original: Bitmap, key: String, sizePx: Int): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val full = Rect(0, 0, sizePx, sizePx)
        val back = filter.backs.takeIf { it.isNotEmpty() }?.let { it[Math.floorMod(key.hashCode(), it.size)] }
        back?.let { bitmap(it, sizePx) }?.let { canvas.drawBitmap(it, null, full, null) }

        val inner = (sizePx * filter.scale).toInt().coerceAtLeast(1)
        val layer = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val layerCanvas = Canvas(layer)
        val offset = (sizePx - inner) / 2
        layerCanvas.drawBitmap(original, null, Rect(offset, offset, offset + inner, offset + inner), Paint(Paint.FILTER_BITMAP_FLAG))
        filter.mask?.let { bitmap(it, sizePx) }?.let { mask ->
            // The mask's opaque parts cut the icon away.
            val paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
            layerCanvas.drawBitmap(mask, null, full, paint)
        }
        canvas.drawBitmap(layer, 0f, 0f, null)
        filter.upon?.let { bitmap(it, sizePx) }?.let { canvas.drawBitmap(it, null, full, null) }
        return out
    }

    /** Every icon the pack offers, for the icon picker. */
    fun allIcons(): List<String> = drawableNames()
}

/** Finds and loads icon packs (the format used by Nova, ADW and most packs on the Play Store). */
object IconPacks {
    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme",
    )

    fun installed(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, IconPackInfo>()
        for (action in ACTIONS) {
            val list = try {
                pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
            } catch (e: Exception) {
                emptyList()
            }
            for (info in list) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg in found) continue
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) {
                    pkg
                }
                found[pkg] = IconPackInfo(pkg, label)
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    fun load(context: Context, pkg: String): IconPack? {
        if (pkg.isBlank()) return null
        return try {
            val pm = context.packageManager
            val res = pm.getResourcesForApplication(pkg)
            val label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            val builder = AppFilterBuilder()
            readXml(res, pkg, "appfilter") { name, attr -> builder.element(name, attr) }
            val filter = builder.build()
            val names = lazy {
                val list = ArrayList<String>()
                readXml(res, pkg, "drawable") { name, attr -> if (name == "item") attr("drawable")?.let(list::add) }
                list.ifEmpty { filter.icons.values.distinct().sorted() }.distinct()
            }
            IconPack(IconPackInfo(pkg, label), res, filter) { names.value }
        } catch (e: Exception) {
            DebugLog.error("Icons", "could not load icon pack $pkg: $e")
            null
        }
    }

    /** Reads res/xml/[file].xml, or assets/[file].xml when the pack keeps it there. */
    private fun readXml(res: Resources, pkg: String, file: String, onElement: (String, (String) -> String?) -> Unit) {
        val id = res.getIdentifier(file, "xml", pkg)
        if (id != 0) {
            val parser = res.getXml(id)
            try {
                walk(parser, onElement)
            } finally {
                parser.close()
            }
            return
        }
        val stream: InputStream = try {
            res.assets.open("$file.xml")
        } catch (e: Exception) {
            return
        }
        stream.use {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(it, null)
            walk(parser, onElement)
        }
    }

    private fun walk(parser: XmlPullParser, onElement: (String, (String) -> String?) -> Unit) {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                onElement(parser.name) { attr -> parser.getAttributeValue(null, attr) }
            }
            event = parser.next()
        }
    }
}
