package dev.liquid.launcher.core.designsystem

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.view.WindowManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.liquid.launcher.core.common.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The launcher-managed wallpaper: one screen-sized image that is both set as the system
 * wallpaper and kept as a file, so the launcher can draw it itself and glass can refract it.
 * Earlier wallpapers are kept in a short history for switching back with one tap.
 */
class WallpaperStore private constructor(private val context: Context) {
    private val file = File(context.filesDir, "wallpaper/home.png")
    private val historyDir = File(context.filesDir, "wallpaper/history")
    private val _image = MutableStateFlow<ImageBitmap?>(null)
    private val _history = MutableStateFlow<List<File>>(emptyList())

    /** The managed wallpaper, or null when the system wallpaper shows through instead. */
    val image: StateFlow<ImageBitmap?> = _image

    /** Earlier wallpapers, newest first. */
    val history: StateFlow<List<File>> = _history

    val hasManagedWallpaper: Boolean get() = file.exists()

    /** The current wallpaper's file, for saving it with a layout. */
    val currentFile: File? get() = file.takeIf { it.exists() }

    suspend fun load() = withContext(Dispatchers.IO) {
        refreshHistory()
        if (!file.exists()) {
            _image.value = null
            return@withContext
        }
        _image.value = decodeFile(file)?.asImageBitmap()
    }

    /** Decodes [uri] cropped to the screen, without applying it (for previews). */
    suspend fun decode(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val (targetW, targetH) = screen()
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // Decode no larger than needed to cover the screen.
                val scale = maxOf(targetW.toFloat() / info.size.width, targetH.toFloat() / info.size.height)
                if (scale < 1f) {
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(targetW),
                        (info.size.height * scale).toInt().coerceAtLeast(targetH),
                    )
                }
            }
            centerCrop(decoded, targetW, targetH)
        } catch (e: Throwable) {
            DebugLog.error("Wallpaper", "decode failed: $e")
            null
        }
    }

    suspend fun decodeFile(source: File): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            BitmapFactory.decodeFile(source.path, options)?.let { bitmap ->
                val (w, h) = screen()
                if (bitmap.width == w && bitmap.height == h) bitmap else centerCrop(bitmap, w, h)
            }
        } catch (e: Throwable) {
            DebugLog.error("Wallpaper", "load failed: $e")
            null
        }
    }

    /** A small version of [source] for a history thumbnail. */
    suspend fun thumbnail(source: File): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap()
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Makes [bitmap] the wallpaper: the current one goes into the history (keeping
     * [keepHistory] files), the new one is stored and set as the system wallpaper (and the lock
     * screen's with [lockToo]). Returns null on success or a message describing the failure.
     */
    suspend fun apply(bitmap: Bitmap, lockToo: Boolean, keepHistory: Int = 12): String? = withContext(Dispatchers.IO) {
        try {
            if (file.exists() && keepHistory > 0) {
                historyDir.mkdirs()
                file.copyTo(File(historyDir, "${System.currentTimeMillis()}.png"), overwrite = true)
            }
            trimHistory(keepHistory)
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            _image.value = bitmap.asImageBitmap()
            val flags = WallpaperManager.FLAG_SYSTEM or if (lockToo) WallpaperManager.FLAG_LOCK else 0
            WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, flags)
            DebugLog.event("Wallpaper", "managed wallpaper set, ${bitmap.width}x${bitmap.height}${if (lockToo) ", lock screen too" else ""}")
            null
        } catch (e: Throwable) {
            DebugLog.error("Wallpaper", "set failed: $e")
            e.message ?: e.toString()
        }
    }

    /** Crops [uri] to the screen, stores it and sets it as the system home wallpaper. */
    suspend fun setFromUri(uri: Uri, lockToo: Boolean = false): String? {
        val bitmap = decode(uri) ?: return "Could not read the image"
        return apply(bitmap, lockToo)
    }

    /** Sets a stored image (a layout's or a history entry) as the wallpaper. */
    suspend fun setFromFile(source: File, lockToo: Boolean = false): String? {
        val bitmap = decodeFile(source) ?: return "Could not read the image"
        return apply(bitmap, lockToo)
    }

    fun removeFromHistory(entry: File) {
        entry.delete()
        refreshHistory()
    }

    /** Stops drawing the wallpaper in the launcher. The system wallpaper is left as it is. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        _image.value = null
    }

    private fun trimHistory(keep: Int) {
        val files = historyDir.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
        files.drop(keep.coerceAtLeast(0)).forEach { it.delete() }
        refreshHistory()
    }

    private fun refreshHistory() {
        _history.value = historyDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
    }

    private fun screen(): Pair<Int, Int> {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        return bounds.width() to bounds.height()
    }

    private fun centerCrop(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val scale = maxOf(targetW.toFloat() / source.width, targetH.toFloat() / source.height)
        val srcW = (targetW / scale).toInt().coerceAtMost(source.width)
        val srcH = (targetH / scale).toInt().coerceAtMost(source.height)
        val left = (source.width - srcW) / 2
        val top = (source.height - srcH) / 2
        val result = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(
            source,
            Rect(left, top, left + srcW, top + srcH),
            Rect(0, 0, targetW, targetH),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return result
    }

    companion object {
        @Volatile
        private var instance: WallpaperStore? = null

        fun get(context: Context): WallpaperStore =
            instance ?: synchronized(this) {
                instance ?: WallpaperStore(context.applicationContext).also { instance = it }
            }
    }
}

/** A blurred copy of a wallpaper, made once on the CPU so the home screen draws it for free. */
object WallpaperBlur {
    /** Blurs [source] by roughly [radiusPx] screen pixels; works on a quarter-size copy. */
    fun blur(source: Bitmap, radiusPx: Float): Bitmap {
        val factor = 4
        val w = (source.width / factor).coerceAtLeast(1)
        val h = (source.height / factor).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        val radius = (radiusPx / factor).toInt().coerceIn(1, 60)
        // Three box blurs approximate a Gaussian.
        repeat(3) {
            boxBlurHorizontal(pixels, w, h, radius)
            boxBlurVertical(pixels, w, h, radius)
        }
        small.setPixels(pixels, 0, w, 0, 0, w, h)
        return small
    }

    private fun boxBlurHorizontal(p: IntArray, w: Int, h: Int, r: Int) {
        val row = IntArray(w)
        val div = 2 * r + 1
        for (y in 0 until h) {
            val o = y * w
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val c = p[o + i.coerceIn(0, w - 1)]
                a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            for (x in 0 until w) {
                row[x] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
                val out = p[o + (x - r).coerceIn(0, w - 1)]
                val inn = p[o + (x + r + 1).coerceIn(0, w - 1)]
                a += (inn ushr 24) - (out ushr 24)
                rr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
            System.arraycopy(row, 0, p, o, w)
        }
    }

    private fun boxBlurVertical(p: IntArray, w: Int, h: Int, r: Int) {
        val col = IntArray(h)
        val div = 2 * r + 1
        for (x in 0 until w) {
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val c = p[i.coerceIn(0, h - 1) * w + x]
                a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            for (y in 0 until h) {
                col[y] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
                val out = p[(y - r).coerceIn(0, h - 1) * w + x]
                val inn = p[(y + r + 1).coerceIn(0, h - 1) * w + x]
                a += (inn ushr 24) - (out ushr 24)
                rr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
            for (y in 0 until h) p[y * w + x] = col[y]
        }
    }
}
