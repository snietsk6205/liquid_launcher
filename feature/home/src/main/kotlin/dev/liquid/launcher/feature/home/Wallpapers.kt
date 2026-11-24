package dev.liquid.launcher.feature.home

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dev.liquid.launcher.core.common.CropSpec
import dev.liquid.launcher.core.common.ColourTiming
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.common.WallpaperTone
import dev.liquid.launcher.core.common.WallpaperTones
import dev.liquid.launcher.core.data.LauncherData
import dev.liquid.launcher.core.data.RotationRules
import dev.liquid.launcher.core.data.WallpaperLibraryState
import dev.liquid.launcher.core.data.WallpaperItem
import dev.liquid.launcher.core.data.WallpaperLibraryOps
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.settings.LauncherSettings
import kotlin.random.Random

/** A picture a wallpaper can be made from: one in the library, or one in a linked phone folder. */
sealed interface WallpaperPick {
    data class Library(val item: WallpaperItem) : WallpaperPick
    data class External(val uri: Uri, val folder: String?) : WallpaperPick
}

/**
 * A wallpaper ready to show, what it was made from, and its colours (worked out before it is
 * shown, so themed icons change in the same frame as the wallpaper).
 */
class PreparedWallpaper(
    val bitmap: Bitmap,
    val pick: WallpaperPick,
    val crop: CropSpec,
    val parallax: Float,
    val tone: WallpaperTone? = null,
    val palette: Pair<Int, Int>? = null,
)

/**
 * Choosing, placing and applying wallpapers: shared by the wallpaper screen, the wallpaper
 * strip in the home menu, saved layouts and automatic changes.
 */
object Wallpapers {
    fun defaultParallax(data: LauncherData): Float = data.settings.get(LauncherSettings.WallpaperParallax) / 100f

    /** When the system gets a wallpaper the launcher shows (setting "Hand the wallpaper to the system"). */
    fun systemWrite(data: LauncherData): WallpaperStore.SystemWrite = when (data.settings.get(LauncherSettings.WallpaperSystemWrite)) {
        "screenoff" -> WallpaperStore.SystemWrite.ScreenOff
        "never" -> WallpaperStore.SystemWrite.Never
        else -> WallpaperStore.SystemWrite.Soon
    }

    /** 0.16.0 (D202): the layout a new wallpaper goes to: the current one, when each layout has its own (and one is current). */
    fun ownLayout(data: LauncherData): String? {
        if (!data.settings.get(LauncherSettings.LayoutsOwnWallpaper)) return null
        return data.layouts.state.value.current?.takeIf { data.layouts.find(it) != null }
    }

    /** 0.16.0 (D202): the layout whose recents Recent shows: the current one, when each layout has its own. */
    fun recentsLayout(data: LauncherData): String? {
        if (!data.settings.get(LauncherSettings.LayoutsOwnRecents)) return null
        return data.layouts.state.value.current?.takeIf { data.layouts.find(it) != null }
    }

    /** Recent as the library and the strip show it (0.16.0: the current layout's own, when each has its own). */
    fun recent(data: LauncherData, state: WallpaperLibraryState = data.wallpapers.get()): List<WallpaperItem> =
        recentsLayout(data)?.let { WallpaperLibraryOps.recentFor(state, it) } ?: WallpaperLibraryOps.recent(state)

    /** 0.16.0 (D202): the current layout's collection, when each layout has its own and it has one. */
    fun layoutCollection(data: LauncherData): String? {
        if (!data.settings.get(LauncherSettings.LayoutsOwnCollection)) return null
        val layout = data.layouts.state.value.current?.let { data.layouts.find(it) } ?: return null
        return layout.collection?.takeIf { id -> data.wallpapers.get().collections.any { it.id == id } }
    }

    /**
     * 0.16.0 (D202): how the current layout has picture [id] placed, when it is that layout's own picture (the same picture
     * can sit differently in another layout); null otherwise.
     */
    fun layoutPlacement(data: LauncherData, id: String): Pair<CropSpec, Float>? {
        val layout = data.layouts.state.value.current?.let { data.layouts.find(it) } ?: return null
        if (layout.wallpaperId != id) return null
        val p = layout.placement ?: return null
        return CropSpec(p.cx, p.cy, p.zoom) to (p.parallax.takeIf { it >= 0f } ?: defaultParallax(data))
    }

    /** A picture was made the wallpaper: its use, its placement, and (0.16.0) the current layout's recents. */
    fun markUsed(data: LauncherData, id: String, crop: CropSpec?, parallax: Float? = null) {
        data.wallpapers.markUsed(id, crop, parallax)
        recentsLayout(data)?.let { data.wallpapers.pushRecent(it, id, data.settings.get(LauncherSettings.WallpaperHistory)) }
    }

    /** "Remove from recent": from the current layout's recents when each has its own (0.16.0), else from all. */
    fun forget(data: LauncherData, ids: Set<String>) {
        val layout = recentsLayout(data)
        if (layout != null) data.wallpapers.forgetFor(layout, ids) else data.wallpapers.forget(ids)
    }

    /**
     * 0.16.0 (D202): a layout just saved gets what each layout has: a collection named after it (holding its wallpaper's
     * picture) and, as its recents, [recents] (those of the moment).
     */
    fun setUpLayout(data: LauncherData, layoutId: String, recents: List<String>) {
        val layout = data.layouts.find(layoutId) ?: return
        if (data.settings.get(LauncherSettings.LayoutsOwnCollection) && layout.collection == null) {
            val collection = data.wallpapers.addCollection(layout.name)
            layout.wallpaperId?.let { data.wallpapers.addToCollection(setOf(it), collection.id) }
            data.layouts.setCollection(layout.id, collection.id)
        }
        if (data.settings.get(LauncherSettings.LayoutsOwnRecents)) data.wallpapers.setRecents(layoutId, recents)
    }

    /**
     * 0.16.0 (D202, D203): once, at the first start of 0.16.0; nothing a layout had is lost. The lock screen switch becomes
     * the three-way choice; the automatic changes (now part of each layout) are written into the layouts saved before with
     * today's values; layouts without a wallpaper of their own get the one on screen; every layout starts with today's
     * recents and a collection named after it.
     */
    suspend fun migrate0160(context: Context, data: LauncherData) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val settings = data.settings
        if (settings.get(LauncherSettings.Migrated0160)) return@withContext
        if (settings.get(LauncherSettings.WallpaperLockToo) && !settings.isChanged(LauncherSettings.WallpaperLockMode)) {
            settings.set(LauncherSettings.WallpaperLockMode, "same")
        }
        val carried = data.layouts.carryInto(settings.exportValues(LauncherSettings.rotationLayoutKeys))
        val layouts = data.layouts.state.value.layouts
        var given = 0
        val file = WallpaperStore.get(context).currentFile
        val current = data.wallpapers.get().current
        if (settings.get(LauncherSettings.LayoutsOwnWallpaper) && file != null) {
            for (layout in layouts.filter { it.wallpaper == null }) {
                val item = current?.let { data.wallpapers.find(it) }
                data.layouts.setWallpaper(layout.id, file, current, item?.let { placementOf(it.crop, it.parallax) })
                given++
            }
        }
        if (settings.get(LauncherSettings.LayoutsOwnRecents)) data.wallpapers.seedRecents(layouts.map { it.id }, settings.get(LauncherSettings.WallpaperHistory))
        var made = 0
        if (settings.get(LauncherSettings.LayoutsOwnCollection)) {
            for (layout in data.layouts.state.value.layouts.filter { it.collection == null }) {
                val collection = data.wallpapers.addCollection(layout.name)
                layout.wallpaperId?.let { data.wallpapers.addToCollection(setOf(it), collection.id) }
                data.layouts.setCollection(layout.id, collection.id)
                made++
            }
        }
        settings.set(LauncherSettings.Migrated0160, true)
        DebugLog.event(
            "Wallpaper",
            "0.16.0: ${layouts.size} layout(s): $given given the wallpaper on screen as their own, the automatic changes carried into " +
                "$carried, recents of their own, $made collection(s) made; lock screen: ${settings.get(LauncherSettings.WallpaperLockMode)}",
        )
    }

    /** The lock screen gets the home wallpaper too (0.16.0: "The same as the home screen", D203). */
    fun lockToo(data: LauncherData): Boolean = data.settings.get(LauncherSettings.WallpaperLockMode) == "same"

    /** The picture behind [pick], decoded for placing. */
    suspend fun source(context: Context, data: LauncherData, pick: WallpaperPick): Bitmap? {
        val store = WallpaperStore.get(context)
        return when (pick) {
            is WallpaperPick.Library -> store.decodeSource(data.wallpapers.fileOf(pick.item))
            is WallpaperPick.External -> store.decodeSource(pick.uri)
        }
    }

    /** Renders [pick] with its own placement (or [crop] / [parallax] when given). */
    suspend fun prepare(context: Context, data: LauncherData, pick: WallpaperPick, crop: CropSpec? = null, parallax: Float? = null): PreparedWallpaper? {
        val source = source(context, data, pick) ?: return null
        val item = (pick as? WallpaperPick.Library)?.item
        val placed = item?.let { layoutPlacement(data, it.id) }
        val useCrop = crop ?: placed?.first ?: item?.crop ?: CropSpec.Centre
        val useParallax = parallax ?: placed?.second ?: item?.parallax?.takeIf { it >= 0f } ?: defaultParallax(data)
        val bitmap = WallpaperStore.get(context).render(source, useCrop, useParallax)
        source.recycle()
        ColourTiming.mark("wallpaper decoded and placed")
        val tone = WallpaperTones.fromBitmap(bitmap)
        val palette = tone?.let { data.paletteFor(it.primary) }
        ColourTiming.mark("palette ready (from the picture)")
        return PreparedWallpaper(bitmap, pick, useCrop, useParallax, tone, palette)
    }

    /**
     * Writes a wallpaper that is already showing and records it in the library: pictures from
     * a phone folder are copied in when [keepFolderPicture] (picked by hand), not when an
     * automatic change used them.
     */
    suspend fun persist(
        context: Context,
        data: LauncherData,
        prepared: PreparedWallpaper,
        keepFolderPicture: Boolean = true,
        system: WallpaperStore.SystemWrite = systemWrite(data),
        /** 0.16.0 (D202): the layout this wallpaper becomes the own wallpaper of (null: none). */
        layout: String? = ownLayout(data),
        /** False when its use was already counted (the wallpaper screen counts it as it shows). */
        markUsed: Boolean = true,
    ): String? {
        val store = WallpaperStore.get(context)
        val error = store.persist(prepared.bitmap, lockToo(data), system = system)
        // A folder's picture picked by hand joins the library; one an automatic change used is kept as a saved copy
        // (0.16.0, D206: until then it was not kept at all), so recents, the layouts and Undo work while its folder cannot
        // be read (Island paused).
        val item = when (val pick = prepared.pick) {
            is WallpaperPick.Library -> pick.item.also { if (it.saved && keepFolderPicture) data.wallpapers.promote(it.id) }
            is WallpaperPick.External -> data.wallpapers.importUri(pick.uri, pick.folder, saved = !keepFolderPicture)
        }
        if (item != null) {
            if (markUsed) markUsed(data, item.id, prepared.crop, prepared.parallax)
        } else {
            data.wallpapers.clearCurrent()
        }
        pruneSaved(data)
        if (error == null && layout != null) {
            data.layouts.setWallpaper(layout, store.currentFile, item?.id, placementOf(prepared.crop, prepared.parallax))
        }
        return error
    }

    fun placementOf(crop: CropSpec, parallax: Float) = dev.liquid.launcher.core.data.LayoutPlacement(crop.cx, crop.cy, crop.zoom, parallax)

    /** 0.16.0 (D206): saved copies no layout's recents, the recents shown, the current picture or a layout needs, deleted. */
    fun pruneSaved(data: LauncherData) {
        val layouts = data.layouts.state.value.layouts.mapNotNull { it.wallpaperId }.toSet()
        data.wallpapers.pruneSaved(data.settings.get(LauncherSettings.WallpaperHistory), layouts)
    }

    /** 0.16.0 (D205): the linked folders the current layout uses (all of them when it chose none, as before). */
    fun layoutFolders(data: LauncherData): List<dev.liquid.launcher.core.data.LinkedFolder> {
        val all = data.wallpapers.get().folders
        val chosen = data.layouts.state.value.current?.let { data.layouts.find(it) }?.folders ?: return all
        return all.filter { it.uri in chosen }.ifEmpty { all }
    }

    /** The pictures automatic changes and "Shuffle" choose from. */
    suspend fun pool(data: LauncherData): List<WallpaperPick> {
        val state = data.wallpapers.get()
        val items = when (data.settings.get(LauncherSettings.WallpaperRotationPool)) {
            "recent" -> recent(data, state).take(data.settings.get(LauncherSettings.WallpaperHistory))
            "all" -> WallpaperLibraryOps.all(state)
            // 0.16.0 (D202): the pictures of the current layout's collection (none: its favourites, as before).
            "collection" -> layoutCollection(data)?.let { WallpaperLibraryOps.inCollection(state, it) }?.takeIf { it.isNotEmpty() }
                ?: WallpaperLibraryOps.favourites(state)
            // 0.16.0 (D205): the folders the current layout uses; a folder that cannot be read (Island paused) gives
            // its saved copies and the library's pictures from it instead.
            "folders" -> return layoutFolders(data).flatMap { folder ->
                val read = data.wallpapers.readFolder(folder)
                if (!read.unreachable) read.images.map { WallpaperPick.External(it.uri, folder.name) }
                else state.items.filter { it.folder == folder.name }.map { WallpaperPick.Library(it) }
            }
            else -> WallpaperLibraryOps.favourites(state)
        }
        return items.map { WallpaperPick.Library(it) }
    }

    /** The next picture from the pool, or null when there is nothing else to change to. */
    suspend fun next(data: LauncherData): WallpaperPick? {
        val pool = pool(data)
        if (pool.isEmpty()) return null
        val state = data.wallpapers.get()
        // In order: carry on after the current picture if it is in the pool.
        val currentIndex = pool.indexOfFirst { it is WallpaperPick.Library && it.item.id == state.current }
        val last = if (currentIndex >= 0) currentIndex else state.rotationIndex
        val index = WallpaperLibraryOps.nextInPool(pool.size, last, data.settings.get(LauncherSettings.WallpaperRotationShuffle)) { Random.nextInt(it) }
        if (index < 0) return null
        data.wallpapers.setRotation(index, System.currentTimeMillis())
        return pool[index]
    }

    /**
     * Automatic change, called when the screen turns off: every time ("unlock"), once a day, every few minutes or at set
     * times (0.16.0). The launcher is not on screen then, so the wallpaper changes without a transition.
     */
    suspend fun onScreenOff(context: Context, data: LauncherData) = rotateIfDue(context, data, RotationRules.Moment.SCREEN_OFF)

    /**
     * 0.16.0 (D204): an automatic change, if one is due at [moment] (the screen off, or the launcher gone to the back for
     * an app): never while the home screen is looked at. The current layout's own wallpaper changes with it.
     */
    suspend fun rotateIfDue(context: Context, data: LauncherData, moment: RotationRules.Moment) {
        val settings = data.settings
        val mode = settings.get(LauncherSettings.WallpaperRotation)
        if (mode == "off") return
        val due = RotationRules.due(
            mode, moment, data.wallpapers.get().lastRotation, System.currentTimeMillis(),
            settings.get(LauncherSettings.WallpaperRotationMinutes),
            RotationRules.parseTimes(settings.get(LauncherSettings.WallpaperRotationTimes)),
            java.time.ZoneId.systemDefault(),
        )
        if (!due) return
        val pick = next(data) ?: return
        val prepared = prepare(context, data, pick) ?: return
        WallpaperStore.get(context).show(prepared.bitmap, prepared.tone)
        // The screen off: nobody waits, so the system gets the picture right away. Away in an app: at the next screen off
        // (a hand-over makes MagicOS sluggish for seconds, D74). Never, when it never does.
        val system = when {
            systemWrite(data) == WallpaperStore.SystemWrite.Never -> WallpaperStore.SystemWrite.Never
            moment == RotationRules.Moment.SCREEN_OFF -> WallpaperStore.SystemWrite.Now
            else -> WallpaperStore.SystemWrite.ScreenOff
        }
        persist(context, data, prepared, keepFolderPicture = false, system = system)
        DebugLog.event("Wallpaper", "changed automatically ($mode, ${if (moment == RotationRules.Moment.SCREEN_OFF) "screen off" else "while away"})")
    }
}
