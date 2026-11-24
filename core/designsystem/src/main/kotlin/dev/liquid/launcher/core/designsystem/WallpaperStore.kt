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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The launcher-managed wallpaper: one image (as wide as the screen, or wider for parallax) that
 * is both set as the system wallpaper and kept as a file, so the launcher can draw it itself
 * and glass can refract it. The pictures it is made from live in the wallpaper library.
 *
 * A new wallpaper shows at once ([show]); writing the file and handing it to the system
 * ([persist]) happen afterwards, so a switch never waits for the slow parts.
 *
 * Handing a picture to the system is the expensive part on MagicOS: it takes 3 to 7 seconds,
 * the phone is sluggish meanwhile and the system launcher can come to the front by itself.
 * So there is one writer: the newest picture wins, a picture the system already has is not
 * written again, and the write waits for a quiet moment (or for the screen to turn off).
 */
class WallpaperStore private constructor(private val context: Context) {
    private val dir = File(context.filesDir, "wallpaper")
    private val file = File(dir, "home.jpg")
    private val legacy = File(dir, "home.png")
    private val _image = MutableStateFlow<ImageBitmap?>(null)
    private val prefs = context.getSharedPreferences("wallpaper_system", Context.MODE_PRIVATE)
    private val writerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val writeLock = kotlinx.coroutines.sync.Mutex()
    private var writerJob: kotlinx.coroutines.Job? = null

    /** Counts pictures shown, so a finished system write knows whether it wrote the picture still on screen. */
    private val shown = java.util.concurrent.atomic.AtomicInteger()

    /** The [shown] count of the picture in [file]. */
    @Volatile
    private var fileGeneration = 0

    /** When the system gets a picture the launcher shows. */
    enum class SystemWrite {
        /** Right away (automatic changes while the screen is off). */
        Now,

        /** After a few quiet seconds; a newer picture in that time replaces it. */
        Soon,

        /** When the screen next turns off ([flushSystemWrite]). */
        ScreenOff,

        /** Not at all: the system keeps its own wallpaper. */
        Never,
    }

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
        } else if (bitmap != null && WallpaperTones.paletteOutdated) {
            // 0.10.2: the palette gains the picture's average colour (for readable text on see-through controls).
            val tone = WallpaperTones.tone.value
            val palette = WallpaperTones.paletteOf(bitmap)
            if (tone != null && palette != null) WallpaperTones.set(context, tone.copy(palette = palette))
        }
        // A write that was still waiting when the process ended.
        if (prefs.getBoolean(KEY_PENDING, false) && prefs.getString(KEY_MODE, "") == SystemWrite.Soon.name) scheduleSystemWrite(SOON_MS)
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
        shown.incrementAndGet()
        (tone?.copy(systemId = -1) ?: WallpaperTones.fromBitmap(bitmap, -1))?.let { WallpaperTones.set(context, it) }
        _image.value = bitmap.asImageBitmap()
    }

    /**
     * Stores [bitmap] as the launcher's wallpaper file (copying [sourceFile] instead of encoding
     * again when given) and asks for it to become the system wallpaper, and the lock screen's
     * with [lockToo], as [system] says. Returns null or what went wrong.
     */
    suspend fun persist(
        bitmap: Bitmap,
        lockToo: Boolean,
        sourceFile: File? = null,
        system: SystemWrite = SystemWrite.Soon,
    ): String? = withContext(Dispatchers.IO) {
        val generation = shown.get()
        try {
            writeLock.lock()
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
                fileGeneration = generation
            } finally {
                writeLock.unlock()
            }
            // Until the system has this picture, remember which one it has now.
            WallpaperTones.rebase(context)
            prefs.edit().putBoolean(KEY_PENDING, system != SystemWrite.Never).putBoolean(KEY_LOCK, lockToo).putString(KEY_MODE, system.name).apply()
            when (system) {
                SystemWrite.Now -> writeSystem()
                SystemWrite.Soon -> scheduleSystemWrite(SOON_MS)
                SystemWrite.ScreenOff, SystemWrite.Never -> writerJob?.cancel()
            }
            null
        } catch (e: Throwable) {
            DebugLog.error("Wallpaper", "set failed: $e")
            e.message ?: e.toString()
        }
    }

    private fun scheduleSystemWrite(delayMs: Long) {
        writerJob?.cancel()
        // 0.16.0 (D200): while an Undo is offered, the hand-over waits for it to go (an Undo would cost a second one).
        val wait = maxOf(delayMs, holdUntil - android.os.SystemClock.uptimeMillis())
        writerJob = writerScope.launch {
            kotlinx.coroutines.delay(wait)
            writeSystem()
        }
    }

    /** 0.16.0 (D200): until when (uptime ms) a hand-over to the system waits. */
    @Volatile
    private var holdUntil = 0L

    /**
     * 0.16.0 (D200): no hand-over to the system for [ms] from now, while the change can still be undone. A hand-over
     * already waiting waits longer; one "at screen off" or "never" is not affected.
     */
    fun holdSystemWrite(ms: Long) {
        holdUntil = android.os.SystemClock.uptimeMillis() + ms
        if (writerJob?.isActive == true) scheduleSystemWrite(SOON_MS)
    }

    /** The Undo has gone (or was used): a hand-over still waiting goes ahead as it would have. */
    fun releaseSystemWrite() {
        holdUntil = 0L
        if (writerJob?.isActive == true && prefs.getBoolean(KEY_PENDING, false)) scheduleSystemWrite(SOON_MS)
    }

    /**
     * 0.16.0 (D200): copies the wallpaper as it is now (the finished picture, placement included) to [target], so an
     * Undo can bring it back after a new one was written over it. False when there is none.
     */
    suspend fun copyCurrent(target: File): Boolean = withContext(Dispatchers.IO) {
        writeLock.lock()
        try {
            val source = currentFile ?: return@withContext false
            target.parentFile?.mkdirs()
            val temp = File(target.path + ".tmp")
            source.copyTo(temp, overwrite = true)
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
            true
        } catch (e: Throwable) {
            DebugLog.error("Wallpaper", "could not keep the wallpaper for Undo: $e")
            false
        } finally {
            writeLock.unlock()
        }
    }

    /** The screen turned off: a good moment for a write that was waiting (nobody is looking). */
    fun flushSystemWrite() {
        if (!prefs.getBoolean(KEY_PENDING, false)) return
        writerJob?.cancel()
        writerJob = writerScope.launch { writeSystem() }
    }

    /**
     * Hands the wallpaper file to the system, unless the system already has exactly this
     * picture. One at a time; a write that started is always finished and recorded.
     */
    private suspend fun writeSystem() = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        writeLock.lock()
        try {
            if (!prefs.getBoolean(KEY_PENDING, false)) return@withContext
            val source = currentFile ?: return@withContext
            val lockToo = prefs.getBoolean(KEY_LOCK, false)
            val key = "${source.length()}:${digest(source)}:$lockToo"
            val generation = fileGeneration
            if (key == prefs.getString(KEY_SYSTEM, null)) {
                prefs.edit().putBoolean(KEY_PENDING, false).apply()
                DebugLog.event("Wallpaper", "the system already has this wallpaper: not set again")
                WallpaperTones.adopt(context, WallpaperTones.systemId(context), generation == shown.get())
                return@withContext
            }
            val started = android.os.SystemClock.uptimeMillis()
            WallpaperTones.beginOwnWrite()
            var id = -1
            try {
                val flags = WallpaperManager.FLAG_SYSTEM or if (lockToo) WallpaperManager.FLAG_LOCK else 0
                // The file as it is: handing over a bitmap makes the framework encode it as PNG first.
                id = source.inputStream().use { WallpaperManager.getInstance(context).setStream(it, null, true, flags) }
                prefs.edit().putBoolean(KEY_PENDING, false).putString(KEY_SYSTEM, key).apply()
            } catch (e: Throwable) {
                DebugLog.error("Wallpaper", "system wallpaper not set: $e")
            } finally {
                val systemId = if (id > 0) id else WallpaperTones.systemId(context)
                WallpaperTones.endOwnWrite(context, systemId, generation == shown.get())
                DebugLog.event(
                    "Wallpaper",
                    "managed wallpaper set, id $systemId, took ${android.os.SystemClock.uptimeMillis() - started} ms${if (lockToo) ", lock screen too" else ""}",
                )
                dev.liquid.launcher.core.common.ColourTiming.mark("system wallpaper set")
            }
        } finally {
            writeLock.unlock()
        }
    }

    private fun digest(source: File): String = try {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                md.update(buffer, 0, n)
            }
        }
        md.digest().take(8).joinToString("") { "%02x".format(it) }
    } catch (e: Throwable) {
        source.lastModified().toString()
    }

    /**
     * 0.16.0 (D203): makes [bitmap] the lock screen's own wallpaper; the home screen's is left as it is. A hand-over like
     * the home one (MagicOS takes seconds and can bring the system launcher forward), so it runs off the main thread and
     * counts as the launcher's own write. Returns null or what went wrong.
     */
    suspend fun setLockScreen(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        writeLock.lock()
        val started = android.os.SystemClock.uptimeMillis()
        WallpaperTones.beginOwnWrite()
        try {
            val bytes = java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
            WallpaperManager.getInstance(context).setStream(java.io.ByteArrayInputStream(bytes), null, true, WallpaperManager.FLAG_LOCK)
            DebugLog.event("Wallpaper", "lock screen wallpaper set (its own picture), took ${android.os.SystemClock.uptimeMillis() - started} ms")
            null
        } catch (e: Throwable) {
            DebugLog.error("Wallpaper", "lock screen wallpaper not set: $e")
            e.message ?: e.toString()
        } finally {
            WallpaperTones.endOwnWrite(context, WallpaperTones.systemId(context), false)
            writeLock.unlock()
        }
    }

    /** [show] and [persist] in one go. */
    suspend fun apply(bitmap: Bitmap, lockToo: Boolean, sourceFile: File? = null, system: SystemWrite = SystemWrite.Soon): String? {
        withContext(Dispatchers.Main.immediate) { show(bitmap) }
        return persist(bitmap, lockToo, sourceFile, system)
    }

    /** Sets a finished wallpaper file (a layout's) as the wallpaper. */
    suspend fun setFromFile(source: File, lockToo: Boolean = false, system: SystemWrite = SystemWrite.Soon): String? {
        val bitmap = decodeFile(source) ?: return "Could not read the image"
        return apply(bitmap, lockToo, source, system)
    }

    /** Stops drawing the wallpaper in the launcher. The system wallpaper is left as it is. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        writerJob?.cancel()
        prefs.edit().clear().apply()
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

        /** Quiet time before a picture goes to the system; quick switches in that time cost one write. */
        const val SOON_MS = 3000L
        private const val KEY_PENDING = "pending"
        private const val KEY_LOCK = "lock"
        private const val KEY_MODE = "mode"
        private const val KEY_SYSTEM = "systemKey"

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
