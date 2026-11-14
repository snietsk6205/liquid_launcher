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
 * Chooses each app's icon (its own, an icon pack's, one fitted to a pack, one the user picked)
 * and, with themed icons on, the [IconArt] it is drawn from. Nothing here is rendered in the
 * wallpaper's colours: every themed icon takes them when it is drawn, so a new wallpaper
 * recolours all icons in the same frame and this class does no work at all.
 *
 * Up to 0.5.0 icons fitted to a pack and work-profile icons were rendered per colour set and
 * arrived late, and icons drawn by a pack were never recoloured. Runs on the apps loading thread.
 */
internal class IconThemer(private val context: Context) {
    private val packs = HashMap<String, IconPack?>()
    private val cache = HashMap<String, ImageBitmap?>()
    private val arts = HashMap<String, IconArt>()
    private var lastConfig: IconConfig? = null
    private val sizePx = (AppsRepository.ICON_DP * context.resources.displayMetrics.density).toInt()

    fun pack(pkg: String): IconPack? = packs.getOrPut(pkg) { IconPacks.load(context, pkg) }

    /** Drops cached icons when the packs, the masking or the shape change. */
    fun prepare(config: IconConfig) {
        val last = lastConfig
        if (config == last) return
        if (config.packEpoch != last?.packEpoch) {
            packs.clear()
            cache.keys.removeAll { it.startsWith("pack|") }
            arts.keys.removeAll { it.startsWith("pack|") }
        }
        val sameIcons = last != null && config.homePack == last.homePack && config.drawerPack == last.drawerPack &&
            config.mask == last.mask && config.shape == last.shape
        if (!sameIcons) {
            cache.clear()
            arts.clear()
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
                val art = if (themed != null) arts.getOrPut("own|$override|${app.key}") { IconArt.of(own) } else null
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
     * The icon on one surface and, when [theme] is set, the art it is drawn from in the
     * wallpaper's colours: the pack's own icon (line icons tinted, others two-tone), the app's
     * monochrome glyph on the icon shape, or a two-tone version of the app's icon (fitted to
     * the pack when the pack asks for that).
     */
    private fun forSurface(app: AppEntry, packPkg: String, mask: Boolean, theme: ThemedIcons?): Pair<ImageBitmap?, IconArt?> {
        if (packPkg.isBlank() && theme == null) return app.icon to null
        val pack = if (packPkg.isBlank()) null else pack(packPkg)
        if (pack != null) {
            val key = "pack|$packPkg|${app.key}"
            val own = cached(key) { pack.iconFor(app.component, sizePx)?.let { badge(it, app) } }
            if (own != null) return own to if (theme != null && theme.packs) arts.getOrPut(key) { IconArt.of(own) } else null
        }
        val fit = pack != null && mask && pack.canMask
        val mono = app.monochrome
        val shape = lastConfig?.shape ?: "system"
        if (theme != null && mono != null && !fit) {
            val art = arts.getOrPut("mono|$shape|${app.key}") {
                IconArt.Mono(IconPlates.plate(shape, sizePx), mono, if (app.isWorkProfile) workBadge(app) else null)
            }
            return app.icon to art
        }
        val original = app.icon ?: return null to null
        val base = if (fit) {
            cached("fit|$packPkg|${app.key}") { pack!!.masked(original.asAndroidBitmap(), app.key, sizePx).asImageBitmap() } ?: original
        } else {
            original
        }
        // An app with a themed icon follows even when "apps without one" keep their colours.
        val follows = theme != null && (theme.tintUncovered || mono != null)
        val art = if (follows) arts.getOrPut("duo|$packPkg|${app.key}") { IconArt.of(base) } else null
        return base to art
    }

    /** The work profile's badge alone (on nothing), to draw over a themed icon; one per profile. */
    private fun workBadge(app: AppEntry): ImageBitmap? = cached("badge|${app.ref.profile}") {
        val empty = BitmapDrawable(context.resources, Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888))
        context.packageManager.getUserBadgedIcon(empty, app.user).toBitmap(sizePx, sizePx).asImageBitmap()
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
