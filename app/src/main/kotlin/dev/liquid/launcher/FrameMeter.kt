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

    // 0.11.1: where the time goes, so a slow screen points at its cause: composing and animating (Compose recomposes
    // in the frame's animation step), measuring and placing, recording the drawing, and handing it to the GPU.
    private var animNs = 0L
    private var layoutNs = 0L
    private var drawNs = 0L
    private var syncNs = 0L
    private var label = ""
    private var minFrames = 120

    override fun onFrameMetricsAvailable(window: Window, metrics: FrameMetrics, dropCount: Int) {
        val now = FrameCost.label
        if (now != label) {
            flush()
            label = now
            minFrames = FrameCost.minFrames
        }
        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val deadline = metrics.getMetric(FrameMetrics.DEADLINE).takeIf { it > 0 } ?: 16_666_667L
        frames++
        totalNs += total
        gpuNs += metrics.getMetric(FrameMetrics.GPU_DURATION)
        animNs += metrics.getMetric(FrameMetrics.ANIMATION_DURATION) + metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION)
        layoutNs += metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
        drawNs += metrics.getMetric(FrameMetrics.DRAW_DURATION)
        syncNs += metrics.getMetric(FrameMetrics.SYNC_DURATION) + metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)
        if (total > worstNs) worstNs = total
        if (total > deadline) late++
        late += dropCount
        if (frames >= 600) flush()
    }

    /** Writes the frames counted so far (when there were enough to say something) and starts again. */
    fun flush() {
        if (frames >= minFrames) {
            val avg = totalNs / frames / 1e6
            val gpu = gpuNs / frames / 1e6
            fun ms(ns: Long) = ns / frames / 1e6
            DebugLog.event(
                "Frames",
                "%d drawn, %d late (%.1f %%), %.1f ms on average (GPU %.1f ms; input and composing %.1f, layout %.1f, draw %.1f, sync %.1f), worst %.1f ms; %s".format(
                    frames, late, late * 100f / frames, avg, gpu, ms(animNs), ms(layoutNs), ms(drawNs), ms(syncNs), worstNs / 1e6,
                    label.ifEmpty { "no moving background" },
                ),
            )
        }
        frames = 0
        late = 0
        totalNs = 0
        gpuNs = 0
        worstNs = 0
        animNs = 0
        layoutNs = 0
        drawNs = 0
        syncNs = 0
    }
}
