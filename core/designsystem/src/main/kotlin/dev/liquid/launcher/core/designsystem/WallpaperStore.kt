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
import dev.liquid.launcher.core.common.CropSpec
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.common.WallpaperTones
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The launcher-managed wallpaper: one image (as wide as the screen, or wider for parallax) that
 * is both set as the system wallpaper and kept as a file, so the launcher can draw it itself
 * and glass can refract it. The pictures it is made from live in the wallpaper library.
 *
 * A new wallpaper shows at once ([show]); writing the file and handing it to the system
 * ([persist]) happen afterwards, so a switch never waits for the slow parts.
 */
class WallpaperStore private constructor(private val context: Context) {
    private val dir = File(context.filesDir, "wallpaper")
    private val file = File(dir, "home.jpg")
    private val legacy = File(dir, "home.png")
    private val _image = MutableStateFlow<ImageBitmap?>(null)

    /** The managed wallpaper, or null when the system wallpaper shows through instead. */
    val image: StateFlow<ImageBitmap?> = _image

    /** Where earlier versions kept their wallpaper history (moved into the library on first start). */
    val legacyHistoryDir: File get() = File(dir, "history")

    val hasManagedWallpaper: Boolean get() = currentFile != null

    /** The current wallpaper's file, for saving it with a layout. */
    val currentFile: File? get() = file.takeIf { it.exists() } ?: legacy.takeIf { it.exists() }

    suspend fun load() = withContext(Dispatchers.IO) {
        val source = currentFile
        if (source == null) {
            _image.value = null
            return@withContext
        }
        val bitmap = decodeFile(source)
        _image.value = bitmap?.asImageBitmap()
        // Earlier versions did not keep the colours: take them from the image once.
        WallpaperTones.load(context)
        if (bitmap != null && WallpaperTones.tone.value == null) {
            WallpaperTones.fromBitmap(bitmap, WallpaperTones.systemId(context))?.let { WallpaperTones.set(context, it) }
        }
    }

    /** Decodes a picture for placing (no larger than [MAX_SOURCE] on its long side, turned upright). */
    suspend fun decodeSource(uri: Uri): Bitmap? = decodeSource { ImageDecoder.createSource(context.contentResolver, uri) }

    suspend fun decodeSource(source: File): Bitmap? = decodeSource { ImageDecoder.createSource(source) }

    private suspend fun decodeSource(make: () -> ImageDecoder.Source): Bitmap? = withContext(Dispatchers.IO) {
        try {
            ImageDecoder.decodeBitmap(make()) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val long = maxOf(info.size.width, info.size.height)
                if (long > MAX_SOURCE) {
                    val scale = MAX_SOURCE.toFloat() / long
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
            }
        } catch (e: Throwable) {
            DebugLog.error("Wallpaper", "decode failed: $e")
            null
        }
    }

    /** Decodes a finished wallpaper file (the current one, a layout's) as it is. */
    suspend fun decodeFile(source: File): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            BitmapFactory.decodeFile(source.path, options)
        } catch (e: Throwable) {
            DebugLog.error("Wallpaper", "load failed: $e")
            null
        }
    }

    /**
     * The wallpaper made from [source]: the part [crop] picks, on a canvas the screen's height
     * and (1 + [parallax]) times its width, so it can slide with the pages.
     */
    suspend fun render(source: Bitmap, crop: CropSpec, parallax: Float): Bitmap = withContext(Dispatchers.Default) {
        val (screenW, screenH) = screen()
        val canvasW = (screenW * (1f + parallax.coerceIn(0f, 1f))).roundToInt()
        val frame = crop.frame(source.width, source.height, canvasW.toFloat(), screenH.toFloat())
        val result = Bitmap.createBitmap(canvasW, screenH, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(
            source,
            Rect(frame.left.roundToInt(), frame.top.roundToInt(), frame.right.roundToInt(), frame.bottom.roundToInt()),
            Rect(0, 0, canvasW, screenH),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        result
    }

    /**
     * Shows [bitmap] as the wallpaper right away: the home screen draws it and themed icons,
     * accent and text colour follow its colours at once. Nothing is written yet.
     */
    fun show(bitmap: Bitmap, tone: dev.liquid.launcher.core.common.WallpaperTone? = null) {
        (tone?.copy(systemId = -1) ?: WallpaperTones.fromBitmap(bitmap, -1))?.let { WallpaperTones.set(context, it) }
        _image.value = bitmap.asImageBitmap()
    }

    /**
     * Stores [bitmap] (copying [sourceFile] instead of encoding again when given) and sets it as
     * the system wallpaper, and the lock screen's with [lockToo]. Returns null or what went wrong.
     */
    suspend fun persist(bitmap: Bitmap, lockToo: Boolean, sourceFile: File? = null): String? = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            val temp = File(dir, "home.tmp")
            if (sourceFile != null && sourceFile.exists() && sourceFile.canonicalPath != file.canonicalPath) {
                sourceFile.copyTo(temp, overwrite = true)
            } else {
                temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            }
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
            legacy.delete()
            val flags = WallpaperManager.FLAG_SYSTEM or if (lockToo) WallpaperManager.FLAG_LOCK else 0
            val id = WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, flags)
            val systemId = if (id > 0) id else WallpaperTones.systemId(context)
            WallpaperTones.tone.value?.let { WallpaperTones.set(context, it.copy(systemId = systemId)) }
            DebugLog.event("Wallpaper", "managed wallpaper set, ${bitmap.width}x${bitmap.height}, id $systemId${if (lockToo) ", lock screen too" else ""}")
            dev.liquid.launcher.core.common.ColourTiming.mark("system wallpaper set")
            null
        } catch (e: Throwable) {
            DebugLog.error("Wallpaper", "set failed: $e")
            e.message ?: e.toString()
        }
    }

    /** [show] and [persist] in one go. */
    suspend fun apply(bitmap: Bitmap, lockToo: Boolean, sourceFile: File? = null): String? {
        withContext(Dispatchers.Main.immediate) { show(bitmap) }
        return persist(bitmap, lockToo, sourceFile)
    }

    /** Sets a finished wallpaper file (a layout's) as the wallpaper. */
    suspend fun setFromFile(source: File, lockToo: Boolean = false): String? {
        val bitmap = decodeFile(source) ?: return "Could not read the image"
        return apply(bitmap, lockToo, source)
    }

    /** Stops drawing the wallpaper in the launcher. The system wallpaper is left as it is. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        legacy.delete()
        _image.value = null
        WallpaperTones.clear(context)
    }

    /** The screen size in pixels. */
    fun screen(): Pair<Int, Int> {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        return bounds.width() to bounds.height()
    }

    companion object {
        /** Long side of a picture decoded for placing: enough to zoom in about 1.5x on a 1080 × 2400 screen. */
        const val MAX_SOURCE = 3600

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
