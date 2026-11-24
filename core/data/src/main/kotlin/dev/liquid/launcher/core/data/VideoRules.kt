package dev.liquid.launcher.core.data

/**
 * 0.16.0 (D207): when a video wallpaper plays and how it sits on the screen, kept free of Android so it is tested. The
 * launcher only plays it while its home screen is in front, the screen is on and nothing covers it, and never against
 * the battery rules; after that, the wallpaper's own "plays" choice.
 */
object VideoRules {
    /** What the phone and the launcher are doing, as far as playing goes. */
    data class Situation(
        val homeInFront: Boolean,
        val screenOn: Boolean,
        /** The drawer, search or another full sheet covers the home screen. */
        val covered: Boolean,
        val batterySaver: Boolean,
        /** 0..100, or -1 when unknown. */
        val battery: Int,
        val charging: Boolean,
        /** Since the last unlock: ms played, and whether it played to its end once. */
        val playedMs: Long,
        val playedThrough: Boolean,
    )

    /** The battery rules (Settings › Wallpaper and layouts › Video). */
    data class Battery(val pauseInSaver: Boolean = true, val pauseBelow: Int = 15, val onlyCharging: Boolean = false)

    /** Why it does not play now ("" = it plays). */
    fun pauseReason(spec: VideoSpec, now: Situation, battery: Battery): String = when {
        !now.screenOn -> "the screen is off"
        !now.homeInFront -> "the home screen is not in front"
        now.covered -> "the home screen is covered"
        battery.pauseInSaver && now.batterySaver -> "battery saver"
        battery.pauseBelow > 0 && now.battery in 0 until battery.pauseBelow -> "battery below ${battery.pauseBelow} %"
        battery.onlyCharging && !now.charging -> "not charging"
        spec.plays == "unlock" && now.playedThrough -> "played once since the unlock"
        spec.plays == "seconds" && now.playedMs >= spec.seconds * 1000L -> "played its ${spec.seconds} s"
        else -> ""
    }

    /**
     * Where a video of [videoW] × [videoH] goes on a canvas of [w] × [h]: its scale and its top-left offset. "fill" covers
     * the canvas and [VideoSpec.panX]/[VideoSpec.panY] choose the part shown; "fit" shows all of it, centred.
     */
    fun place(spec: VideoSpec, videoW: Int, videoH: Int, w: Float, h: Float): Triple<Float, Float, Float> {
        if (videoW <= 0 || videoH <= 0 || w <= 0f || h <= 0f) return Triple(1f, 0f, 0f)
        val fill = spec.fit != "fit"
        val scale = if (fill) maxOf(w / videoW, h / videoH) else minOf(w / videoW, h / videoH)
        val sw = videoW * scale
        val sh = videoH * scale
        val x = if (fill) -(sw - w) * spec.panX.coerceIn(0f, 1f) else (w - sw) / 2f
        val y = if (fill) -(sh - h) * spec.panY.coerceIn(0f, 1f) else (h - sh) / 2f
        return Triple(scale, x, y)
    }

    /** The part played, clamped to the video (ms): start and end. */
    fun clip(spec: VideoSpec): Pair<Long, Long> {
        val duration = spec.durationMs.coerceAtLeast(0)
        val start = spec.startMs.coerceIn(0, (duration - 200).coerceAtLeast(0))
        val end = if (spec.endMs <= 0 || duration in 1 until spec.endMs) duration else spec.endMs.coerceAtLeast(start + 200)
        return start to end
    }

    /** The speeds offered (0.25× to 2×). */
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    /**
     * The glass over a playing video (D207): with "auto", it bends the video until the frames measured show it costs too
     * much: more than [maxLateShare] of the frames late; then a still frame, refreshed every few seconds.
     */
    fun glassBendsVideo(choice: String, measuredLateShare: Float?, maxLateShare: Float = 0.25f): Boolean = when (choice) {
        "live" -> true
        "still" -> false
        else -> measuredLateShare == null || measuredLateShare <= maxLateShare
    }
}
