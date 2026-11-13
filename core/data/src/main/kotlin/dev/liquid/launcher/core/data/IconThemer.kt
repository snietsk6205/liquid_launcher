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
    /** Themed icons in the wallpaper's colours, or null when off. */
    val themed: ThemedIcons? = null,
    /** Changes when the wallpaper does, so packs that use system colours are loaded again. */
    val packEpoch: Int = 0,
    /** Icon shape for themed icons ([IconShapes] id). */
    val shape: String = "system",
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

    /** Theme keys of the last few colour sets, newest last: their icons stay cached. */
    private val recentThemes = ArrayDeque<String>()

    /**
     * Drops cached icons when the packs, the masking or the shape change. A new colour set
     * keeps the icons of the last [KEEP_THEMES] sets, so switching back to an earlier
     * wallpaper (a layout's, for example) recolours instantly.
     */
    fun prepare(config: IconConfig) {
        val last = lastConfig
        if (config == last) return
        if (config.packEpoch != last?.packEpoch) packs.clear()
        val sameIcons = last != null && config.homePack == last.homePack && config.drawerPack == last.drawerPack &&
            config.mask == last.mask && config.shape == last.shape
        if (!sameIcons) {
            cache.clear()
            recentThemes.clear()
        }
        val key = config.themed?.key ?: "null"
        recentThemes.remove(key)
        recentThemes.addLast(key)
        while (recentThemes.size > KEEP_THEMES) {
            val old = "|" + recentThemes.removeFirst() + "|"
            cache.keys.removeAll { old in it }
        }
        lastConfig = config
    }

    fun apply(app: AppEntry, config: IconConfig, override: String?): AppEntry {
        if (override != null) {
            val own = cached("own|$override|${app.key}") { forOverride(app, override) }
            if (own != null) return app.copy(icon = own, homeIcon = own, customIcon = true)
        }
        val themed = config.themed
        if (config.homePack.isBlank() && config.drawerPack.isBlank() && themed == null) return app
        val drawerTheme = themed?.takeIf { it.drawer }
        val homeTheme = themed?.takeIf { it.home }
        val drawer = forSurface(app, config.drawerPack, config.mask, drawerTheme)
        val home = if (config.homePack == config.drawerPack && homeTheme == drawerTheme) drawer else forSurface(app, config.homePack, config.mask, homeTheme)
        return app.copy(icon = drawer, homeIcon = home)
    }

    /**
     * The icon on one surface: the pack's own icon if it has one (never recoloured), otherwise
     * the app's icon, themed in the wallpaper colours if [theme] is set, then fitted to the pack.
     */
    private fun forSurface(app: AppEntry, packPkg: String, mask: Boolean, theme: ThemedIcons?): ImageBitmap? {
        if (packPkg.isBlank() && theme == null) return app.icon
        return cached("$packPkg|${theme?.key}|${app.key}") {
            val pack = if (packPkg.isBlank()) null else pack(packPkg)
            pack?.iconFor(app.component, sizePx)?.let { return@cached badge(it, app) }
            val fit = pack != null && mask && pack.canMask
            val mono = app.monochrome
            if (theme != null && mono != null) {
                // A pack with a background of its own gets just the glyph on that background.
                return@cached if (fit && pack!!.hasBack) {
                    badge(pack.masked(ThemedColors.glyph(mono, sizePx, theme.foreground), app.key, sizePx), app)
                } else {
                    val shaped = ThemedColors.themed(mono, sizePx, theme.background, theme.foreground, lastConfig?.shape ?: "system")
                    badge(if (fit) pack!!.masked(shaped, app.key, sizePx) else shaped, app)
                }
            }
            val original = app.icon ?: return@cached null
            var bitmap = original.asAndroidBitmap()
            if (theme != null && theme.tintUncovered) bitmap = ThemedColors.duotone(bitmap, theme.background, theme.foreground)
            if (fit) bitmap = pack!!.masked(bitmap, app.key, sizePx)
            if (bitmap === original.asAndroidBitmap()) original else bitmap.asImageBitmap()
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

    private companion object {
        const val KEEP_THEMES = 4
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
