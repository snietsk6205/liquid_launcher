package dev.liquid.launcher.feature.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.feature.redirect.RedirectStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 0.13.0 (D175, Med: "the pill goes up with the keyboard, no delay, as smooth as iOS"): a picture of the keyboard as it
 * last stood fully up on the search screen. A third-party keyboard needs 60 to 390 ms to be ready (its window is drawn
 * first; median 140 ms in Med's log), so from the first frame of the swipe this picture rises with the pill, on the same
 * spring, and the real keyboard takes its place, at the same height, once it is ready. Taken through the redirect
 * service's screenshot ability on the launcher's own search screen only, cut down to the keyboard's rows at once, and
 * kept in the app's cache so it survives a restart.
 */
internal object KeyboardPicture {
    var image by mutableStateOf<ImageBitmap?>(null)
        private set

    /** The keyboard's height in the picture (px, from the bottom of the screen, navigation bar included); 0: none. */
    var height by mutableStateOf(0)
        private set

    private var takenAt = 0L
    private var loaded = false

    private fun file(context: Context) = File(context.cacheDir, "keyboard-picture.webp")

    /** The picture kept from before a restart, once. */
    suspend fun load(context: Context) {
        if (loaded) return
        loaded = true
        val f = file(context)
        if (!f.exists()) return
        val bitmap = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() } ?: return
        if (image == null) {
            image = bitmap.asImageBitmap()
            height = bitmap.height
        }
    }

    /**
     * Takes the keyboard's picture now that it stands [keyboardPx] tall on a screen [screenHeight] px high (at most once
     * every 8 s). A refusal leaves the last picture in place.
     */
    fun take(context: Context, keyboardPx: Int, screenHeight: Int) {
        val now = SystemClock.uptimeMillis()
        if (keyboardPx <= 0 || now - takenAt < 8_000) return
        takenAt = now
        RedirectStatus.captureScreen { full ->
            if (full == null) return@captureScreen
            try {
                val rows = keyboardPx.coerceAtMost(full.height)
                // The screenshot is the screen at its own size; the keyboard is its bottom rows.
                val scaledRows = (rows * full.height / screenHeight.coerceAtLeast(1)).coerceIn(1, full.height)
                val cut = Bitmap.createBitmap(full, 0, full.height - scaledRows, full.width, scaledRows)
                if (cut !== full) full.recycle()
                image = cut.asImageBitmap()
                height = keyboardPx
                DebugLog.event("Home", "search: the keyboard's picture was taken (${cut.width} x ${cut.height})")
                Thread {
                    runCatching { file(context).outputStream().use { cut.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, it) } }
                }.start()
            } catch (e: Exception) {
                DebugLog.error("Home", "search: the keyboard's picture could not be cut: $e")
            }
        }
    }
}
