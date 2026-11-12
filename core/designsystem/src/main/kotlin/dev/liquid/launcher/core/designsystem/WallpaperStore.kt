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
 */
class WallpaperStore private constructor(private val context: Context) {
    private val file = File(context.filesDir, "wallpaper/home.png")
    private val _image = MutableStateFlow<ImageBitmap?>(null)

    /** The managed wallpaper, or null when the system wallpaper shows through instead. */
    val image: StateFlow<ImageBitmap?> = _image

    val hasManagedWallpaper: Boolean get() = file.exists()

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            _image.value = null
            return@withContext
        }
        try {
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            _image.value = BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
        } catch (e: Throwable) {
            DebugLog.error("Wallpaper", "load failed: $e")
            _image.value = null
        }
    }

    /**
     * Crops [uri] to the screen, stores it and sets it as the system home wallpaper.
     * Returns null on success or a message describing the failure.
     */
    suspend fun setFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            val targetW = bounds.width()
            val targetH = bounds.height()
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
            val cropped = centerCrop(decoded, targetW, targetH)
            file.parentFile?.mkdirs()
            file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
            WallpaperManager.getInstance(context).setBitmap(cropped, null, true, WallpaperManager.FLAG_SYSTEM)
            _image.value = cropped.asImageBitmap()
            DebugLog.event("Wallpaper", "managed wallpaper set, ${targetW}x$targetH")
            null
        } catch (e: Throwable) {
            DebugLog.error("Wallpaper", "set failed: $e")
            e.message ?: e.toString()
        }
    }

    /** Stops drawing the wallpaper in the launcher. The system wallpaper is left as it is. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        _image.value = null
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
