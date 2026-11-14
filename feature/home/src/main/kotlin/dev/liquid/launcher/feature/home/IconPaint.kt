package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.ColorUtils
import dev.liquid.launcher.core.common.ColourTiming
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.IconArt
import dev.liquid.launcher.core.data.ThemedColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The colours themed icons are drawn in right now. Icons read them while drawing, so setting
 * new colours redraws every icon in the next frame and renders nothing.
 *
 * Three sources: the data layer (any change, eased over a moment), a change of the whole
 * screen (a wallpaper or layout switch: set in the same frame as the new wallpaper), and the
 * wallpaper screen trying a picture out (until it closes).
 */
object IconPaint {
    var background by mutableIntStateOf(0)
        private set
    var foreground by mutableIntStateOf(0)
        private set
    var ready by mutableStateOf(false)
        private set
    var duotone by mutableStateOf<ColorFilter?>(null)
        private set

    /** Counts finished colour changes, for the "icons redrawn" timing line. */
    var settled by mutableIntStateOf(0)
        private set

    private var base: Pair<Int, Int>? = null
    private var preview: Pair<Int, Int>? = null
    private var job: Job? = null

    private fun show(colours: Pair<Int, Int>?) {
        if (colours == null) {
            ready = false
            return
        }
        if (ready && background == colours.first && foreground == colours.second) return
        background = colours.first
        foreground = colours.second
        duotone = ColorFilter.colorMatrix(ColorMatrix(ThemedColors.duotoneMatrix(colours.first, colours.second)))
        ready = true
    }

    /** First colours, before anything is drawn. */
    fun init(colours: Pair<Int, Int>?) {
        if (base == null && !ready) {
            base = colours
            show(colours)
        }
    }

    /** Colours from the data layer: eased in, unless the wallpaper screen is trying something out. */
    fun fromData(colours: Pair<Int, Int>?, scope: CoroutineScope) {
        base = colours
        if (preview == null) to(colours, scope)
    }

    /** Colours of a wallpaper that appears in this very frame (switches): no easing, no wait. */
    fun now(colours: Pair<Int, Int>?) {
        if (colours == null) return
        base = colours
        preview = null
        job?.cancel()
        show(colours)
        settled++
    }

    /** The wallpaper screen is trying a picture out: its colours, at once. */
    fun preview(colours: Pair<Int, Int>?) {
        if (colours == null) return
        preview = colours
        job?.cancel()
        show(colours)
        settled++
    }

    /** The wallpaper screen closed: back to the data layer's colours. */
    fun endPreview(scope: CoroutineScope) {
        if (preview == null) return
        preview = null
        to(base, scope)
    }

    private fun to(target: Pair<Int, Int>?, scope: CoroutineScope) {
        job?.cancel()
        if (target == null || !ready) {
            show(target)
            if (target != null) settled++
            return
        }
        val from = background to foreground
        if (from == target) return
        job = scope.launch {
            animate(0f, 1f, animationSpec = tween(260)) { v, _ ->
                show(ColorUtils.blendARGB(from.first, target.first, v) to ColorUtils.blendARGB(from.second, target.second, v))
            }
            settled++
        }
    }
}

/** An app's icon on the home screen ([home]) or in the drawer, themed at draw time if it has art. */
@Composable
fun AppIcon(app: AppEntry?, home: Boolean, modifier: Modifier, description: String?) {
    if (app == null) {
        AppIconImage(null, modifier, description)
        return
    }
    ArtIcon(if (home) app.homeIcon else app.icon, if (home) app.homeArt else app.art, modifier, description)
}

/**
 * Draws [art] in the current [IconPaint] colours, or [icon] as it is. Two small bitmap draws
 * (shape and glyph masks) or one colour-filtered draw; the colours are read while drawing.
 */
@Composable
fun ArtIcon(icon: ImageBitmap?, art: IconArt?, modifier: Modifier, description: String?) {
    if (art == null) {
        AppIconImage(icon, modifier, description)
        return
    }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val rect = remember { android.graphics.Rect() }
    Spacer(
        modifier
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .drawBehind {
                val w = size.width.roundToInt()
                val h = size.height.roundToInt()
                if (!IconPaint.ready) {
                    icon?.let { drawImage(it, dstSize = IntSize(w, h), filterQuality = FilterQuality.Medium) }
                    return@drawBehind
                }
                when (art) {
                    is IconArt.Mono -> drawIntoCanvas { canvas ->
                        rect.set(0, 0, w, h)
                        paint.color = IconPaint.background
                        canvas.nativeCanvas.drawBitmap(art.plate, null, rect, paint)
                        paint.color = IconPaint.foreground
                        canvas.nativeCanvas.drawBitmap(art.glyph, null, rect, paint)
                    }
                    is IconArt.Duotone -> drawImage(art.source, dstSize = IntSize(w, h), colorFilter = IconPaint.duotone, filterQuality = FilterQuality.Medium)
                }
            }
    )
}

/** Writes the "icons redrawn" timing line one frame after each finished colour change. */
@Composable
internal fun IconPaintTiming() {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { IconPaint.settled }.collect {
            androidx.compose.runtime.withFrameNanos { }
            ColourTiming.mark("icons redrawn in the new colours")
        }
    }
}
