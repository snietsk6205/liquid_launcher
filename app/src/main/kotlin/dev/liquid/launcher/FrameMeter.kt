package dev.liquid.launcher

import android.view.FrameMetrics
import android.view.Window
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.feature.home.FrameCost

/**
 * What drawing the home screen costs on this phone (W3, 0.10.1): Android reports every frame it drew, with how long
 * it took and whether it missed its deadline. While frames keep coming (the moving wallpaper, a theme animation,
 * scrolling), one line every ~10 s goes to the debug log with what was on screen, so the cost of a moving
 * background or a texture is a number from the phone itself, not a guess.
 */
internal class FrameMeter : Window.OnFrameMetricsAvailableListener {
    private var frames = 0
    private var late = 0
    private var totalNs = 0L
    private var gpuNs = 0L
    private var worstNs = 0L
    private var label = ""

    override fun onFrameMetricsAvailable(window: Window, metrics: FrameMetrics, dropCount: Int) {
        val now = FrameCost.label
        if (now != label) {
            flush()
            label = now
        }
        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val deadline = metrics.getMetric(FrameMetrics.DEADLINE).takeIf { it > 0 } ?: 16_666_667L
        frames++
        totalNs += total
        gpuNs += metrics.getMetric(FrameMetrics.GPU_DURATION)
        if (total > worstNs) worstNs = total
        if (total > deadline) late++
        late += dropCount
        if (frames >= 600) flush()
    }

    /** Writes the frames counted so far (when there were enough to say something) and starts again. */
    fun flush() {
        if (frames >= 120) {
            val avg = totalNs / frames / 1e6
            val gpu = gpuNs / frames / 1e6
            DebugLog.event(
                "Frames",
                "%d drawn, %d late (%.1f %%), %.1f ms on average (GPU %.1f ms), worst %.1f ms; %s".format(
                    frames, late, late * 100f / frames, avg, gpu, worstNs / 1e6, label.ifEmpty { "no moving background" },
                ),
            )
        }
        frames = 0
        late = 0
        totalNs = 0
        gpuNs = 0
        worstNs = 0
    }
}
