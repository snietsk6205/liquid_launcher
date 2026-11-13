package dev.liquid.launcher.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Which icon packs are in use. Empty package = the apps' own icons. */
data class IconConfig(
    val homePack: String = "",
    val drawerPack: String = "",
    /** Fit icons the pack does not cover to the pack's shape. */
    val mask: Boolean = true,
)

/**
 * Turns the apps' own icons into themed ones. Results are cached per pack and app, so a
 * rename or a new tag does not redraw every icon. Runs on the apps loading thread.
 */
internal class IconThemer(private val context: Context) {
    private val packs = HashMap<String, IconPack?>()
    private val cache = HashMap<String, ImageBitmap?>()
    private var lastConfig: IconConfig? = null
    private val sizePx = (AppsRepository.ICON_DP * context.resources.displayMetrics.density).toInt()

    fun pack(pkg: String): IconPack? = packs.getOrPut(pkg) { IconPacks.load(context, pkg) }

    /** Drops cached icons when the packs or the masking change. */
    fun prepare(config: IconConfig) {
        if (config != lastConfig) {
            cache.clear()
            lastConfig = config
        }
    }

    fun apply(app: AppEntry, config: IconConfig, override: String?): AppEntry {
        if (override != null) {
            val own = cached("own|$override|${app.key}") { forOverride(app, override) }
            if (own != null) return app.copy(icon = own, homeIcon = own, customIcon = true)
        }
        if (config.homePack.isBlank() && config.drawerPack.isBlank()) return app
        val drawer = themed(app, config.drawerPack, config.mask)
        val home = if (config.homePack == config.drawerPack) drawer else themed(app, config.homePack, config.mask)
        return app.copy(icon = drawer, homeIcon = home)
    }

    private fun themed(app: AppEntry, packPkg: String, mask: Boolean): ImageBitmap? {
        if (packPkg.isBlank()) return app.icon
        return cached("$packPkg|${app.key}") {
            val pack = pack(packPkg) ?: return@cached app.icon
            pack.iconFor(app.component, sizePx)?.let { return@cached badge(it, app) }
            val original = app.icon ?: return@cached null
            if (mask && pack.canMask) pack.masked(original.asAndroidBitmap(), app.key, sizePx).asImageBitmap() else original
        }
    }

    private fun forOverride(app: AppEntry, override: String): ImageBitmap? {
        val pkg = override.substringBefore('|')
        val name = override.substringAfter('|', "")
        if (name.isEmpty()) return null
        return pack(pkg)?.bitmap(name, sizePx)?.let { badge(it, app) }
    }

    /** Work profile apps keep their briefcase badge on pack icons. */
    private fun badge(bitmap: Bitmap, app: AppEntry): ImageBitmap {
        if (!app.isWorkProfile) return bitmap.asImageBitmap()
        return try {
            val badged = context.packageManager.getUserBadgedIcon(BitmapDrawable(context.resources, bitmap), app.user)
            badged.toBitmap(sizePx, sizePx).asImageBitmap()
        } catch (e: Exception) {
            bitmap.asImageBitmap()
        }
    }

    private inline fun cached(key: String, make: () -> ImageBitmap?): ImageBitmap? {
        if (cache.containsKey(key)) return cache[key]
        val value = try {
            make()
        } catch (e: Throwable) {
            null
        }
        cache[key] = value
        return value
    }
}
