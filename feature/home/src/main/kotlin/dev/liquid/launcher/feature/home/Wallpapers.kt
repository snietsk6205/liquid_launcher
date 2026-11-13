package dev.liquid.launcher.feature.home

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dev.liquid.launcher.core.common.CropSpec
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.data.LauncherData
import dev.liquid.launcher.core.data.WallpaperItem
import dev.liquid.launcher.core.data.WallpaperLibraryOps
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.settings.LauncherSettings
import java.util.Calendar
import kotlin.random.Random

/** A picture a wallpaper can be made from: one in the library, or one in a linked phone folder. */
sealed interface WallpaperPick {
    data class Library(val item: WallpaperItem) : WallpaperPick
    data class External(val uri: Uri, val folder: String?) : WallpaperPick
}

/** A wallpaper ready to show, and what it was made from. */
class PreparedWallpaper(val bitmap: Bitmap, val pick: WallpaperPick, val crop: CropSpec, val parallax: Float)

/**
 * Choosing, placing and applying wallpapers: shared by the wallpaper screen, the wallpaper
 * strip in the home menu, saved layouts and automatic changes.
 */
object Wallpapers {
    fun defaultParallax(data: LauncherData): Float = data.settings.get(LauncherSettings.WallpaperParallax) / 100f

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
        val useCrop = crop ?: item?.crop ?: CropSpec.Centre
        val useParallax = parallax ?: item?.parallax?.takeIf { it >= 0f } ?: defaultParallax(data)
        val bitmap = WallpaperStore.get(context).render(source, useCrop, useParallax)
        source.recycle()
        return PreparedWallpaper(bitmap, pick, useCrop, useParallax)
    }

    /**
     * Writes a wallpaper that is already showing and records it in the library: pictures from
     * a phone folder are copied in when [keepFolderPicture] (picked by hand), not when an
     * automatic change used them.
     */
    suspend fun persist(context: Context, data: LauncherData, prepared: PreparedWallpaper, keepFolderPicture: Boolean = true): String? {
        val error = WallpaperStore.get(context).persist(prepared.bitmap, data.settings.get(LauncherSettings.WallpaperLockToo))
        val item = when (val pick = prepared.pick) {
            is WallpaperPick.Library -> pick.item
            is WallpaperPick.External -> if (keepFolderPicture) data.wallpapers.importUri(pick.uri, pick.folder) else null
        }
        if (item != null) data.wallpapers.markUsed(item.id, prepared.crop, prepared.parallax) else data.wallpapers.clearCurrent()
        return error
    }

    /** The pictures automatic changes and "Shuffle" choose from. */
    suspend fun pool(data: LauncherData): List<WallpaperPick> {
        val state = data.wallpapers.get()
        val items = when (data.settings.get(LauncherSettings.WallpaperRotationPool)) {
            "recent" -> WallpaperLibraryOps.recent(state).take(data.settings.get(LauncherSettings.WallpaperHistory))
            "all" -> WallpaperLibraryOps.all(state)
            "folders" -> return state.folders.flatMap { folder ->
                data.wallpapers.folderImages(folder).map { WallpaperPick.External(it.uri, folder.name) }
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
     * Automatic change, called when the screen turns off: every time ("unlock") or once a day.
     * The launcher is not on screen then, so the wallpaper changes without a transition.
     */
    suspend fun onScreenOff(context: Context, data: LauncherData) {
        val mode = data.settings.get(LauncherSettings.WallpaperRotation)
        if (mode == "off") return
        if (mode == "daily" && sameDay(data.wallpapers.get().lastRotation, System.currentTimeMillis())) return
        val pick = next(data) ?: return
        val prepared = prepare(context, data, pick) ?: return
        WallpaperStore.get(context).show(prepared.bitmap)
        persist(context, data, prepared, keepFolderPicture = false)
        DebugLog.event("Wallpaper", "changed automatically ($mode)")
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        if (a <= 0L) return false
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }
}
