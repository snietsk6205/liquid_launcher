package dev.liquid.launcher.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.DocumentsContract
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.liquid.launcher.core.common.CropSpec
import dev.liquid.launcher.core.common.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest

/**
 * One picture in the wallpaper library: a copy kept by the launcher (so it works offline and
 * goes into backups), its placement, when it was last used and whether it is a favourite.
 * [id] comes from the picture's bytes, so adding the same picture twice keeps one entry.
 */
@Serializable
data class WallpaperItem(
    val id: String,
    /** File name under the library folder. */
    val file: String,
    val added: Long,
    /** When it was last made the wallpaper; 0 = never. */
    val lastUsed: Long = 0,
    val favourite: Boolean = false,
    val cx: Float = 0.5f,
    val cy: Float = 0.5f,
    val zoom: Float = 1f,
    /** Name of the phone folder it was taken from, if any. */
    val folder: String? = null,
    /** Parallax it was placed with (extra width over the screen's), or -1 for the default. */
    val parallax: Float = -1f,
    /** W8 (P8): this picture's own look on the home screen, or null for the one set for all. */
    val look: WallpaperLook? = null,
    /** 0.16.0: how many times it was made the wallpaper, for "Most used". Counted from 0.16.0 on. */
    val uses: Int = 0,
    /** 0.16.0: its main colour (ARGB, worked out from its thumbnail), for sorting and filtering by colour; null until known. */
    val colour: Int? = null,
    /** 0.16.0: its upright width and height (from its thumbnail; only the ratio matters), for the shape filter; 0 until known. */
    val w: Int = 0,
    val h: Int = 0,
    /** 0.16.0 (D198): ids of the collections it is in. Favourites stay [favourite] (shown as the first collection). */
    val collections: List<String> = emptyList(),
) {
    val crop: CropSpec get() = CropSpec(cx, cy, zoom)

    /** Its colour and shape are known (see [WallpaperLibrary.describeMissing]). */
    val described: Boolean get() = colour != null && w > 0 && h > 0
}

/** 0.16.0 (D198): a named group of library pictures (a picture can be in several). */
@Serializable
data class WallpaperCollection(val id: String, val name: String, val created: Long = 0)

/** 0.16.0: how the library's pictures are ordered. */
enum class LibrarySort(val id: String, val label: String) {
    /** Recent: last used first; elsewhere: last added first. */
    NEWEST("newest", "Newest"),
    USED("used", "Most used"),
    COLOUR("colour", "Colour");

    companion object {
        fun of(id: String): LibrarySort = entries.firstOrNull { it.id == id } ?: NEWEST
    }
}

/** 0.16.0: the colour a picture is filtered by: eight hues and the greys (black, white and washed-out pictures). */
enum class ColourFamily(val label: String, val swatch: Int) {
    RED("Red", 0xFFE53935.toInt()),
    ORANGE("Orange", 0xFFFB8C00.toInt()),
    YELLOW("Yellow", 0xFFFDD835.toInt()),
    GREEN("Green", 0xFF43A047.toInt()),
    TEAL("Teal", 0xFF00ACC1.toInt()),
    BLUE("Blue", 0xFF1E88E5.toInt()),
    PURPLE("Purple", 0xFF8E24AA.toInt()),
    PINK("Pink", 0xFFD81B60.toInt()),
    GREY("Grey", 0xFF9E9E9E.toInt()),
}

/** 0.16.0: the shape filter. */
enum class PictureShape(val label: String) { PORTRAIT("Portrait"), LANDSCAPE("Landscape"), SQUARE("Square") }

/**
 * W8 (P8): how one picture sits on the home screen: its blur and dim (-1: as set for all), its saturation (100 is the
 * picture's own) and a vignette darkening its corners (0: none).
 */
@Serializable
data class WallpaperLook(val blur: Int = -1, val dim: Int = -1, val saturation: Int = 100, val vignette: Int = 0)

/** A folder on the phone the library shows live (through the system file picker's permission). */
@Serializable
data class LinkedFolder(
    val uri: String,
    val name: String,
    /** Also show the pictures in its sub-folders. */
    val deep: Boolean = false,
)

@Serializable
data class WallpaperLibraryState(
    val items: List<WallpaperItem> = emptyList(),
    val folders: List<LinkedFolder> = emptyList(),
    /** The library item the current wallpaper was made from, or null. */
    val current: String? = null,
    /** Automatic changes: where in the pool the last one was, and when (ms). */
    val rotationIndex: Int = -1,
    val lastRotation: Long = 0,
    /** 0.16.0 (D198): the user's collections, in the order they were made. */
    val collections: List<WallpaperCollection> = emptyList(),
)

/** A picture inside a linked folder, not copied into the library yet. */
data class FolderImage(val uri: Uri, val name: String, val modified: Long)

/** The library's rules, kept free of Android so they can be tested. */
object WallpaperLibraryOps {
    /** Adds [item] unless a picture with the same id is there already (then that one is kept). */
    fun add(state: WallpaperLibraryState, item: WallpaperItem): WallpaperLibraryState =
        if (state.items.any { it.id == item.id }) state else state.copy(items = state.items + item)

    /** Recently used pictures, newest first. Each picture appears once however often it was used. */
    fun recent(state: WallpaperLibraryState): List<WallpaperItem> =
        state.items.filter { it.lastUsed > 0 }.sortedByDescending { it.lastUsed }

    fun favourites(state: WallpaperLibraryState): List<WallpaperItem> =
        state.items.filter { it.favourite }.sortedByDescending { maxOf(it.lastUsed, it.added) }

    /** Everything, newest addition first. */
    fun all(state: WallpaperLibraryState): List<WallpaperItem> = state.items.sortedByDescending { it.added }

    fun markUsed(state: WallpaperLibraryState, id: String, now: Long, crop: CropSpec?, parallax: Float? = null): WallpaperLibraryState = state.copy(
        items = state.items.map { item ->
            if (item.id != id) item else item.copy(
                lastUsed = now,
                uses = item.uses + 1,
                cx = crop?.cx ?: item.cx,
                cy = crop?.cy ?: item.cy,
                zoom = crop?.zoom ?: item.zoom,
                parallax = parallax ?: item.parallax,
            )
        },
        current = id,
    )

    fun setFavourite(state: WallpaperLibraryState, id: String, on: Boolean): WallpaperLibraryState =
        state.copy(items = state.items.map { if (it.id == id) it.copy(favourite = on) else it })

    /** W8 (P8): a picture's own look; null (or the plain look) goes back to the one set for all. */
    fun setLook(state: WallpaperLibraryState, id: String, look: WallpaperLook?): WallpaperLibraryState =
        state.copy(items = state.items.map { if (it.id == id) it.copy(look = look?.takeIf { l -> l != WallpaperLook() }) else it })

    /** Takes a picture out of the recent list without removing it from the library. */
    fun forget(state: WallpaperLibraryState, id: String): WallpaperLibraryState =
        state.copy(items = state.items.map { if (it.id == id) it.copy(lastUsed = 0) else it })

    fun remove(state: WallpaperLibraryState, id: String): WallpaperLibraryState =
        state.copy(items = state.items.filter { it.id != id }, current = state.current.takeIf { it != id })

    // ---- 0.16.0: several at once, collections, sorting and filtering ----

    fun setFavourite(state: WallpaperLibraryState, ids: Set<String>, on: Boolean): WallpaperLibraryState =
        state.copy(items = state.items.map { if (it.id in ids) it.copy(favourite = on) else it })

    fun forget(state: WallpaperLibraryState, ids: Set<String>): WallpaperLibraryState =
        state.copy(items = state.items.map { if (it.id in ids) it.copy(lastUsed = 0) else it })

    fun removeAll(state: WallpaperLibraryState, ids: Set<String>): WallpaperLibraryState =
        state.copy(items = state.items.filter { it.id !in ids }, current = state.current.takeIf { it !in ids })

    /** Puts back pictures [removeAll] took out (Undo), where they were, and the current one if it was among them. */
    fun restore(state: WallpaperLibraryState, items: List<WallpaperItem>, current: String?): WallpaperLibraryState {
        val known = state.items.map { it.id }.toSet()
        val back = items.filter { it.id !in known }
        return state.copy(
            items = state.items + back,
            current = if (state.current == null && current != null && items.any { it.id == current }) current else state.current,
        )
    }

    /** Puts one picture's record back as it was before an Apply (its placement, use count and look), for Undo. */
    fun restoreItem(state: WallpaperLibraryState, before: WallpaperItem): WallpaperLibraryState =
        state.copy(items = state.items.map { if (it.id == before.id) before.copy(favourite = it.favourite, collections = it.collections) else it })

    fun addCollection(state: WallpaperLibraryState, collection: WallpaperCollection): WallpaperLibraryState =
        if (state.collections.any { it.id == collection.id }) state else state.copy(collections = state.collections + collection)

    fun renameCollection(state: WallpaperLibraryState, id: String, name: String): WallpaperLibraryState =
        state.copy(collections = state.collections.map { if (it.id == id) it.copy(name = name) else it })

    /** Removes a collection; its pictures stay in the library. */
    fun removeCollection(state: WallpaperLibraryState, id: String): WallpaperLibraryState = state.copy(
        collections = state.collections.filter { it.id != id },
        items = state.items.map { if (id in it.collections) it.copy(collections = it.collections - id) else it },
    )

    fun addToCollection(state: WallpaperLibraryState, ids: Set<String>, collection: String): WallpaperLibraryState =
        if (state.collections.none { it.id == collection }) state
        else state.copy(items = state.items.map { if (it.id in ids && collection !in it.collections) it.copy(collections = it.collections + collection) else it })

    fun removeFromCollection(state: WallpaperLibraryState, ids: Set<String>, collection: String): WallpaperLibraryState =
        state.copy(items = state.items.map { if (it.id in ids && collection in it.collections) it.copy(collections = it.collections - collection) else it })

    /** The pictures of a collection, last added to the library first. */
    fun inCollection(state: WallpaperLibraryState, collection: String): List<WallpaperItem> =
        state.items.filter { collection in it.collections }.sortedByDescending { it.added }

    /** Records what [WallpaperLibrary.describeMissing] worked out for one picture. */
    fun describe(state: WallpaperLibraryState, id: String, colour: Int, w: Int, h: Int): WallpaperLibraryState =
        state.copy(items = state.items.map { if (it.id == id) it.copy(colour = colour, w = w, h = h) else it })

    /**
     * [items] in the order [sort] asks for. [recentList]: the list is "Recent", whose newest is the last used. Colour goes
     * round the hues from red, the greys after them, pictures not described yet last.
     */
    fun sorted(items: List<WallpaperItem>, sort: LibrarySort, recentList: Boolean = false): List<WallpaperItem> = when (sort) {
        LibrarySort.NEWEST -> if (recentList) items.sortedByDescending { it.lastUsed } else items.sortedByDescending { it.added }
        LibrarySort.USED -> items.sortedWith(compareByDescending<WallpaperItem> { it.uses }.thenByDescending { it.lastUsed }.thenByDescending { it.added })
        LibrarySort.COLOUR -> items.sortedWith(compareBy<WallpaperItem> { item -> item.colour?.let { colourKey(it) } ?: Float.MAX_VALUE }.thenByDescending { it.added })
    }

    /** Keeps the pictures of [family] and [shape] (null: any). Pictures not described yet only pass when both are null. */
    fun filtered(items: List<WallpaperItem>, family: ColourFamily?, shape: PictureShape?): List<WallpaperItem> {
        if (family == null && shape == null) return items
        return items.filter { item ->
            (family == null || item.colour?.let { familyOf(it) } == family) &&
                (shape == null || shapeOf(item.w, item.h) == shape)
        }
    }

    /** The colour family of [argb]: a grey when it is washed out or very dark, else the hue's. */
    fun familyOf(argb: Int): ColourFamily {
        val (hue, saturation, value) = hsv(argb)
        if (saturation < 0.2f || value < 0.16f) return ColourFamily.GREY
        return when {
            hue < 15f || hue >= 345f -> ColourFamily.RED
            hue < 45f -> ColourFamily.ORANGE
            hue < 70f -> ColourFamily.YELLOW
            hue < 160f -> ColourFamily.GREEN
            hue < 200f -> ColourFamily.TEAL
            hue < 255f -> ColourFamily.BLUE
            hue < 290f -> ColourFamily.PURPLE
            else -> ColourFamily.PINK
        }
    }

    fun shapeOf(w: Int, h: Int): PictureShape? {
        if (w <= 0 || h <= 0) return null
        val ratio = w.toFloat() / h
        return when {
            ratio > 1.05f -> PictureShape.LANDSCAPE
            ratio < 0.95f -> PictureShape.PORTRAIT
            else -> PictureShape.SQUARE
        }
    }

    /** Where a colour goes when sorting by colour: hues from red (0..360, starting 15° before red), greys after (by lightness). */
    private fun colourKey(argb: Int): Float {
        val (hue, _, value) = hsv(argb)
        if (familyOf(argb) == ColourFamily.GREY) return 1000f + (1f - value)
        return (hue + 15f) % 360f
    }

    /** Hue (0..360), saturation and value (0..1) of an ARGB colour. */
    private fun hsv(argb: Int): Triple<Float, Float, Float> {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val hue = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val saturation = if (max == 0f) 0f else d / max
        return Triple(hue, saturation, max)
    }

    /**
     * The next picture of [pool] for an automatic change: in order, or at random without
     * repeating the current one. Returns the index in [pool], or -1 when there is nothing to change to.
     */
    fun nextInPool(poolSize: Int, lastIndex: Int, shuffle: Boolean, random: (Int) -> Int): Int {
        if (poolSize <= 0) return -1
        if (poolSize == 1) return if (lastIndex == 0) -1 else 0
        if (!shuffle) return (lastIndex + 1).mod(poolSize)
        var pick = random(poolSize - 1)
        if (lastIndex in 0 until poolSize && pick >= lastIndex) pick++
        return pick
    }

    /** Short id from a picture's bytes. */
    fun idOf(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).take(10).joinToString("") { "%02x".format(it) }
}

/**
 * The wallpaper library: pictures copied into the launcher, recently used ones (without
 * duplicates), favourites and linked phone folders. Rendering and applying a wallpaper is
 * the wallpaper store's job; this only keeps the pictures and what is known about them.
 */
class WallpaperLibrary(
    private val context: Context,
    private val store: JsonStore<WallpaperLibraryState>,
    private val dir: File,
    private val thumbDir: File,
) {
    val state: StateFlow<WallpaperLibraryState> = store.value
    private val thumbs = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun get(): WallpaperLibraryState = store.get()

    fun find(id: String): WallpaperItem? = store.get().items.firstOrNull { it.id == id }

    fun fileOf(item: WallpaperItem): File = File(dir, item.file)

    /** Copies the picture at [uri] into the library (once: the same picture is found again by its bytes). */
    suspend fun importUri(uri: Uri, folder: String? = null): WallpaperItem? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            importBytes(bytes, extensionOf(context.contentResolver.getType(uri)), folder)
        } catch (e: Throwable) {
            DebugLog.error("Wallpaper", "import failed: $e")
            null
        }
    }

    /** Copies a picture file (an older wallpaper, a layout's) into the library. */
    suspend fun importFile(file: File, lastUsed: Long = 0): WallpaperItem? = withContext(Dispatchers.IO) {
        try {
            val item = importBytes(file.readBytes(), file.extension.ifBlank { "png" }, null) ?: return@withContext null
            if (lastUsed > 0 && item.lastUsed < lastUsed) {
                store.update { s -> s.copy(items = s.items.map { if (it.id == item.id) it.copy(lastUsed = lastUsed) else it }) }
            }
            find(item.id)
        } catch (e: Throwable) {
            DebugLog.error("Wallpaper", "import failed: $e")
            null
        }
    }

    private fun importBytes(bytes: ByteArray, extension: String, folder: String?): WallpaperItem? {
        val id = WallpaperLibraryOps.idOf(bytes)
        find(id)?.let { return it }
        dir.mkdirs()
        val name = "$id.$extension"
        File(dir, name).writeBytes(bytes)
        val item = WallpaperItem(id = id, file = name, added = System.currentTimeMillis(), folder = folder)
        store.update { WallpaperLibraryOps.add(it, item) }
        DebugLog.event("Wallpaper", "added to the library: $name (${bytes.size / 1024} KB)")
        return item
    }

    fun markUsed(id: String, crop: CropSpec?, parallax: Float? = null) =
        store.update { WallpaperLibraryOps.markUsed(it, id, System.currentTimeMillis(), crop, parallax) }

    fun setFavourite(id: String, on: Boolean) = store.update { WallpaperLibraryOps.setFavourite(it, id, on) }

    fun setLook(id: String, look: WallpaperLook?) = store.update { WallpaperLibraryOps.setLook(it, id, look) }

    fun forget(id: String) = store.update { WallpaperLibraryOps.forget(it, id) }

    fun remove(id: String) {
        val item = find(id) ?: return
        fileOf(item).delete()
        File(thumbDir, "$id.jpg").delete()
        thumbs.remove(id)
        store.update { WallpaperLibraryOps.remove(it, id) }
    }

    // ---- 0.16.0: several at once, collections, a delete that can be undone ----

    fun setFavourite(ids: Set<String>, on: Boolean) = store.update { WallpaperLibraryOps.setFavourite(it, ids, on) }

    fun forget(ids: Set<String>) = store.update { WallpaperLibraryOps.forget(it, ids) }

    /** Makes a new collection and returns it. */
    fun addCollection(name: String): WallpaperCollection {
        val now = System.currentTimeMillis()
        val collection = WallpaperCollection("c" + now.toString(36) + (0..999).random().toString(36), name.trim().ifEmpty { "Collection" }, now)
        store.update { WallpaperLibraryOps.addCollection(it, collection) }
        DebugLog.event("Wallpaper", "collection made: \"${collection.name}\"")
        return collection
    }

    fun renameCollection(id: String, name: String) = store.update { WallpaperLibraryOps.renameCollection(it, id, name.trim().ifEmpty { "Collection" }) }

    fun removeCollection(id: String) = store.update { WallpaperLibraryOps.removeCollection(it, id) }

    fun addToCollection(ids: Set<String>, collection: String) = store.update { WallpaperLibraryOps.addToCollection(it, ids, collection) }

    fun removeFromCollection(ids: Set<String>, collection: String) = store.update { WallpaperLibraryOps.removeFromCollection(it, ids, collection) }

    /** Pictures taken out of the library and kept aside until their Undo has gone ([delete], [restore], [purge]). */
    class Deleted internal constructor(val items: List<WallpaperItem>, internal val current: String?, internal val at: Long)

    /**
     * Takes [ids] out of the library. Their files wait in the cache (not in backups) until [purge], so [restore] can put
     * everything back; the current wallpaper on screen is not touched.
     */
    fun delete(ids: Set<String>): Deleted? {
        val state = store.get()
        val items = state.items.filter { it.id in ids }
        if (items.isEmpty()) return null
        trashDir.mkdirs()
        val now = System.currentTimeMillis()
        for (item in items) {
            val file = fileOf(item)
            val kept = File(trashDir, item.file)
            if (file.exists() && !file.renameTo(kept)) {
                runCatching { file.copyTo(kept, overwrite = true); file.delete() }
            }
            // Dated now: [purge] counts from the delete, not from when the picture was added.
            kept.setLastModified(now)
        }
        store.update { WallpaperLibraryOps.removeAll(it, ids) }
        DebugLog.event("Wallpaper", "${items.size} picture(s) taken out of the library (Undo for a while)")
        return Deleted(items, state.current, System.currentTimeMillis())
    }

    /** Undo of [delete]: the files and records come back as they were. */
    fun restore(deleted: Deleted) {
        dir.mkdirs()
        for (item in deleted.items) {
            val kept = File(trashDir, item.file)
            if (kept.exists() && !kept.renameTo(fileOf(item))) runCatching { kept.copyTo(fileOf(item), overwrite = true); kept.delete() }
        }
        val back = deleted.items.filter { fileOf(it).exists() }
        store.update { WallpaperLibraryOps.restore(it, back, deleted.current) }
        DebugLog.event("Wallpaper", "${back.size} picture(s) put back in the library")
    }

    /** Deletes for good the pictures [delete] kept aside more than [olderThanMs] ago (all of them with 0). */
    fun purge(olderThanMs: Long = 0) {
        val limit = System.currentTimeMillis() - olderThanMs
        trashDir.listFiles()?.filter { it.lastModified() <= limit || olderThanMs == 0L }?.forEach { file ->
            File(thumbDir, file.nameWithoutExtension + ".jpg").delete()
            thumbs.remove(file.nameWithoutExtension)
            file.delete()
        }
    }

    /** The record of a picture as it is now, before an Apply changes it (for its Undo). */
    fun snapshot(id: String?): WallpaperItem? = id?.let { find(it) }

    /** Undo of an Apply: [current] is the current picture again and [before] gets back its placement and use count. */
    fun undoApply(current: String?, before: WallpaperItem?) = store.update { state ->
        val restored = before?.let { WallpaperLibraryOps.restoreItem(state, it) } ?: state
        restored.copy(current = current?.takeIf { id -> restored.items.any { it.id == id } })
    }

    /**
     * Works out the colour and shape of pictures that do not have them yet (from their thumbnails, off the main thread),
     * for sorting and filtering. Pictures added before 0.16.0 get them the first time the library opens.
     */
    suspend fun describeMissing(limit: Int = 400) = withContext(Dispatchers.Default) {
        val missing = store.get().items.filter { !it.described }.take(limit)
        if (missing.isEmpty()) return@withContext
        val found = HashMap<String, Triple<Int, Int, Int>>()
        for (item in missing) {
            val thumb = thumbnail(item)?.asAndroidBitmap() ?: continue
            val colour = try {
                android.app.WallpaperColors.fromBitmap(thumb).primaryColor.toArgb()
            } catch (e: Throwable) {
                null
            } ?: continue
            found[item.id] = Triple(colour, thumb.width, thumb.height)
        }
        if (found.isEmpty()) return@withContext
        store.update { state ->
            found.entries.fold(state) { s, (id, d) -> WallpaperLibraryOps.describe(s, id, d.first, d.second, d.third) }
        }
        DebugLog.event("Wallpaper", "colour and shape worked out for ${found.size} picture(s)")
    }

    private val trashDir: File get() = File(thumbDir.parentFile, "wallpaper-trash")

    fun setRotation(index: Int, at: Long) = store.update { it.copy(rotationIndex = index, lastRotation = at) }

    fun clearCurrent() = store.update { it.copy(current = null) }

    // ---- linked folders ----

    /** Links a folder picked with the system's folder picker; keeps read access across restarts. */
    fun addFolder(uri: Uri): LinkedFolder? = try {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val name = DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').substringAfterLast('/').ifBlank { "Folder" }
        val folder = LinkedFolder(uri.toString(), name)
        store.update { s -> if (s.folders.any { it.uri == folder.uri }) s else s.copy(folders = s.folders + folder) }
        folder
    } catch (e: Throwable) {
        DebugLog.error("Wallpaper", "linking a folder failed: $e")
        null
    }

    fun removeFolder(uri: String) {
        try {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Throwable) {
            // Already gone.
        }
        store.update { s -> s.copy(folders = s.folders.filter { it.uri != uri }) }
    }

    /** Shows or hides the pictures in a linked folder's sub-folders. */
    fun setFolderDeep(uri: String, deep: Boolean) =
        store.update { s -> s.copy(folders = s.folders.map { if (it.uri == uri) it.copy(deep = deep) else it }) }

    /**
     * The pictures in a linked folder, newest first; with [LinkedFolder.deep] also those in its
     * sub-folders (at most [MAX_FOLDER_DEPTH] levels down and [MAX_FOLDER_IMAGES] pictures).
     */
    suspend fun folderImages(folder: LinkedFolder): List<FolderImage> = withContext(Dispatchers.IO) {
        try {
            val tree = Uri.parse(folder.uri)
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
            val result = ArrayList<FolderImage>()
            // Folders still to read, with how deep they are.
            val queue = ArrayDeque<Pair<String, Int>>()
            queue.addLast(DocumentsContract.getTreeDocumentId(tree) to 0)
            while (queue.isNotEmpty() && result.size < MAX_FOLDER_IMAGES) {
                val (documentId, depth) = queue.removeFirst()
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
                context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                    while (c.moveToNext() && result.size < MAX_FOLDER_IMAGES) {
                        val mime = c.getString(2) ?: continue
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (folder.deep && depth < MAX_FOLDER_DEPTH) queue.addLast(c.getString(0) to depth + 1)
                            continue
                        }
                        if (!mime.startsWith("image/")) continue
                        result += FolderImage(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)), c.getString(1) ?: "", c.getLong(3))
                    }
                }
            }
            result.sortedByDescending { it.modified }
        } catch (e: Throwable) {
            DebugLog.error("Wallpaper", "reading folder ${folder.name} failed: $e")
            emptyList()
        }
    }

    // ---- thumbnails ----

    suspend fun thumbnail(item: WallpaperItem): ImageBitmap? {
        thumbs.get(item.id)?.let { return it }
        return withContext(Dispatchers.IO) {
            val cached = File(thumbDir, "${item.id}.jpg")
            val bitmap = if (cached.exists()) BitmapFactory.decodeFile(cached.path) else decodeSmall(ImageDecoder.createSource(fileOf(item)))?.also { small ->
                thumbDir.mkdirs()
                runCatching { cached.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
            }
            bitmap?.asImageBitmap()?.also { thumbs.put(item.id, it) }
        }
    }

    suspend fun thumbnail(uri: Uri): ImageBitmap? {
        val key = uri.toString()
        thumbs.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            decodeSmall(ImageDecoder.createSource(context.contentResolver, uri))?.asImageBitmap()?.also { thumbs.put(key, it) }
        }
    }

    private fun decodeSmall(source: ImageDecoder.Source): Bitmap? = try {
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val sample = (minOf(info.size.width, info.size.height) / THUMB_PX).coerceAtLeast(1)
            decoder.setTargetSampleSize(Integer.highestOneBit(sample))
        }
    } catch (e: Throwable) {
        null
    }

    /**
     * Brings the wallpapers of earlier versions into the library: the history list and the
     * current wallpaper (as recently used), then removes the old history folder.
     */
    suspend fun migrate(historyDir: File, current: File?) {
        if (historyDir.exists()) {
            historyDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                importFile(file, lastUsed = file.lastModified())
            }
            historyDir.deleteRecursively()
        }
        if (current != null && current.exists() && store.get().current == null) {
            importFile(current, lastUsed = current.lastModified())?.let { item ->
                store.update { it.copy(current = item.id) }
            }
        }
    }

    private fun extensionOf(mime: String?): String = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        "image/gif" -> "gif"
        else -> "jpg"
    }

    private companion object {
        const val THUMB_PX = 280
        const val MAX_FOLDER_DEPTH = 6
        const val MAX_FOLDER_IMAGES = 3000
    }
}
