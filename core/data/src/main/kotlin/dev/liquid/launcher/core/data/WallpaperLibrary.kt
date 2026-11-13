package dev.liquid.launcher.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.DocumentsContract
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
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
) {
    val crop: CropSpec get() = CropSpec(cx, cy, zoom)
}

/** A folder on the phone the library shows live (through the system file picker's permission). */
@Serializable
data class LinkedFolder(val uri: String, val name: String)

@Serializable
data class WallpaperLibraryState(
    val items: List<WallpaperItem> = emptyList(),
    val folders: List<LinkedFolder> = emptyList(),
    /** The library item the current wallpaper was made from, or null. */
    val current: String? = null,
    /** Automatic changes: where in the pool the last one was, and when (ms). */
    val rotationIndex: Int = -1,
    val lastRotation: Long = 0,
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

    /** Takes a picture out of the recent list without removing it from the library. */
    fun forget(state: WallpaperLibraryState, id: String): WallpaperLibraryState =
        state.copy(items = state.items.map { if (it.id == id) it.copy(lastUsed = 0) else it })

    fun remove(state: WallpaperLibraryState, id: String): WallpaperLibraryState =
        state.copy(items = state.items.filter { it.id != id }, current = state.current.takeIf { it != id })

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

    fun forget(id: String) = store.update { WallpaperLibraryOps.forget(it, id) }

    fun remove(id: String) {
        val item = find(id) ?: return
        fileOf(item).delete()
        File(thumbDir, "$id.jpg").delete()
        thumbs.remove(id)
        store.update { WallpaperLibraryOps.remove(it, id) }
    }

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

    /** The pictures in a linked folder (not its sub-folders), newest first. */
    suspend fun folderImages(folder: LinkedFolder): List<FolderImage> = withContext(Dispatchers.IO) {
        try {
            val tree = Uri.parse(folder.uri)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
            val result = ArrayList<FolderImage>()
            context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val mime = c.getString(2) ?: continue
                    if (!mime.startsWith("image/")) continue
                    result += FolderImage(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)), c.getString(1) ?: "", c.getLong(3))
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
    }
}
