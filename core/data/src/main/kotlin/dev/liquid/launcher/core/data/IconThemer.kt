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
 * Turns the apps' own icons into themed ones. Themed icons are not rendered in colour: they
 * come out as [IconArt] (an alpha mask on the icon shape, or the full-colour icon for a
 * two-tone filter) and take the palette's colours when drawn, so a new wallpaper costs a
 * redraw, not a render. Only icons fitted to a pack or badged for the work profile are still
 * rendered in colour (cached for the last few colour sets). Runs on the apps loading thread.
 */
internal class IconThemer(private val context: Context) {
    private val packs = HashMap<String, IconPack?>()
    private val cache = HashMap<String, ImageBitmap?>()
    private val arts = HashMap<String, IconArt>()
    private var lastConfig: IconConfig? = null
    private val sizePx = (AppsRepository.ICON_DP * context.resources.displayMetrics.density).toInt()

    fun pack(pkg: String): IconPack? = packs.getOrPut(pkg) { IconPacks.load(context, pkg) }

    /** Theme keys of the last few colour sets, newest last: their rendered icons stay cached. */
    private val recentThemes = ArrayDeque<String>()

    /**
     * Drops cached icons when the packs, the masking or the shape change. A new colour set
     * only touches the few icons rendered in colour; it keeps those of the last
     * [KEEP_THEMES] sets, so switching back to an earlier wallpaper costs nothing.
     */
    fun prepare(config: IconConfig) {
        val last = lastConfig
        if (config == last) return
        if (config.packEpoch != last?.packEpoch) {
            packs.clear()
            cache.keys.removeAll { it.startsWith("pack|") || it.startsWith("baked|") }
        }
        val sameIcons = last != null && config.homePack == last.homePack && config.drawerPack == last.drawerPack &&
            config.mask == last.mask && config.shape == last.shape
        if (!sameIcons) {
            cache.clear()
            arts.clear()
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

    /**
     * The app with its icons for this configuration. [override] is the user's own icon for it;
     * [keepColours] keeps its icon out of the wallpaper colours.
     */
    fun apply(app: AppEntry, config: IconConfig, override: String?, keepColours: Boolean = false): AppEntry {
        val themed = config.themed?.takeIf { !keepColours }
        if (override != null) {
            val own = cached("own|$override|${app.key}") { forOverride(app, override) }
            if (own != null) {
                // An icon chosen for this app follows the wallpaper too, as a two-tone version of itself.
                val art = if (themed != null) arts.getOrPut("own|$override|${app.key}") { IconArt.Duotone(own) } else null
                return app.copy(
                    icon = own, homeIcon = own, customIcon = true,
                    art = art.takeIf { themed?.drawer == true }, homeArt = art.takeIf { themed?.home == true },
                )
            }
        }
        if (config.homePack.isBlank() && config.drawerPack.isBlank() && themed == null) return app
        val drawerTheme = themed?.takeIf { it.drawer }
        val homeTheme = themed?.takeIf { it.home }
        val (drawer, drawerArt) = forSurface(app, config.drawerPack, config.mask, drawerTheme)
        val (home, homeArt) = if (config.homePack == config.drawerPack && homeTheme == drawerTheme) {
            drawer to drawerArt
        } else {
            forSurface(app, config.homePack, config.mask, homeTheme)
        }
        return app.copy(icon = drawer, homeIcon = home, art = drawerArt, homeArt = homeArt)
    }

    /**
     * The icon on one surface: the pack's own icon if it has one (never recoloured), otherwise
     * the app's icon, themed in the palette if [theme] is set, and fitted to the pack.
     */
    private fun forSurface(app: AppEntry, packPkg: String, mask: Boolean, theme: ThemedIcons?): Pair<ImageBitmap?, IconArt?> {
        if (packPkg.isBlank() && theme == null) return app.icon to null
        val pack = if (packPkg.isBlank()) null else pack(packPkg)
        if (pack != null) {
            val own = cached("pack|$packPkg|${app.key}") { pack.iconFor(app.component, sizePx)?.let { badge(it, app) } }
            if (own != null) return own to null
        }
        val fit = pack != null && mask && pack.canMask
        val mono = app.monochrome
        if (theme != null && mono != null) {
            if (!fit && !app.isWorkProfile) {
                val art = arts.getOrPut("mono|${lastConfig?.shape}|${app.key}") { IconArt.Mono(IconPlates.plate(lastConfig?.shape ?: "system", sizePx), mono) }
                return app.icon to art
            }
            // Fitted to a pack, or with the work badge: rendered in colour, cached per colour set.
            val baked = cached("baked|$packPkg|${theme.key}|${app.key}") {
                if (fit && pack!!.hasBack) {
                    badge(pack.masked(ThemedColors.glyph(mono, sizePx, theme.foreground), app.key, sizePx), app)
                } else {
                    val shaped = ThemedColors.themed(mono, sizePx, theme.background, theme.foreground, lastConfig?.shape ?: "system")
                    badge(if (fit) pack!!.masked(shaped, app.key, sizePx) else shaped, app)
                }
            }
            return baked to null
        }
        val original = app.icon ?: return null to null
        val base = if (fit) {
            cached("fit|$packPkg|${app.key}") { pack!!.masked(original.asAndroidBitmap(), app.key, sizePx).asImageBitmap() } ?: original
        } else {
            original
        }
        val art = if (theme != null && theme.tintUncovered) arts.getOrPut("duo|$packPkg|${app.key}") { IconArt.Duotone(base) } else null
        return base to art
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
