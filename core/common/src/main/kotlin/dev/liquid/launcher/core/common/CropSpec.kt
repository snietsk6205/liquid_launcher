package dev.liquid.launcher.core.common

import kotlin.math.max

/** A rectangle in picture pixels. */
data class Frame(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * Where a wallpaper sits in its picture: the centre of the shown part as fractions of the
 * picture, and the zoom over "just covers the canvas" (1 = the whole short side fits).
 */
data class CropSpec(val cx: Float = 0.5f, val cy: Float = 0.5f, val zoom: Float = 1f) {

    /**
     * The part of a [srcW] × [srcH] picture shown on a canvas of [canvasW] × [canvasH]. The
     * centre is kept inside the picture, so the canvas is always covered.
     */
    fun frame(srcW: Int, srcH: Int, canvasW: Float, canvasH: Float): Frame {
        if (srcW <= 0 || srcH <= 0 || canvasW <= 0f || canvasH <= 0f) return Frame(0f, 0f, srcW.toFloat(), srcH.toFloat())
        val scale = max(canvasW / srcW, canvasH / srcH) * zoom.coerceIn(1f, MAX_ZOOM)
        val w = (canvasW / scale).coerceAtMost(srcW.toFloat())
        val h = (canvasH / scale).coerceAtMost(srcH.toFloat())
        val left = (cx * srcW - w / 2f).coerceIn(0f, srcW - w)
        val top = (cy * srcH - h / 2f).coerceIn(0f, srcH - h)
        return Frame(left, top, w, h)
    }

    /**
     * This crop after a pinch or drag on the canvas: [pan] in canvas pixels (content follows
     * the finger), [zoomBy] the pinch factor. The result is clamped like [frame].
     */
    fun moved(srcW: Int, srcH: Int, canvasW: Float, canvasH: Float, panX: Float, panY: Float, zoomBy: Float): CropSpec {
        val newZoom = (zoom * zoomBy).coerceIn(1f, MAX_ZOOM)
        val before = frame(srcW, srcH, canvasW, canvasH)
        val pxPerCanvas = before.width / canvasW
        val centreX = before.left + before.width / 2f - panX * pxPerCanvas
        val centreY = before.top + before.height / 2f - panY * pxPerCanvas
        val next = CropSpec(centreX / srcW, centreY / srcH, newZoom)
        // Clamp the centre so the stored values match what is shown.
        val f = next.frame(srcW, srcH, canvasW, canvasH)
        return CropSpec((f.left + f.width / 2f) / srcW, (f.top + f.height / 2f) / srcH, newZoom)
    }

    companion object {
        const val MAX_ZOOM = 5f
        val Centre = CropSpec()
    }
}
