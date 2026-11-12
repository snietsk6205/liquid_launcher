package dev.liquid.launcher.feature.redirect

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import dev.liquid.launcher.core.common.DebugLog

/**
 * Learns which view ids exist only while recents is open: snapshot the system launcher's home
 * screen, wait for the user to open recents, and keep the ids that appeared.
 */
internal class Calibrator(
    private val systemLauncher: String,
    private val root: () -> AccessibilityNodeInfo?,
    private val announce: (String) -> Unit,
    private val onFinished: (ids: List<String>?, failure: String?) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var phase = Phase.WAIT_HOME
    private var startedAt = 0L
    private var homeSeenAt = 0L
    private var baseline: Set<String> = emptySet()
    private var lastDiff: Set<String>? = null

    private enum class Phase { WAIT_HOME, SETTLE, WAIT_RECENTS }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            step()
            if (running) handler.postDelayed(this, TICK_MS)
        }
    }

    fun start() {
        running = true
        phase = Phase.WAIT_HOME
        startedAt = SystemClock.uptimeMillis()
        report(1, "Go to the home screen now (Home button or swipe up) and wait")
        handler.postDelayed(tick, TICK_MS)
    }

    fun cancel() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun report(step: Int, message: String) {
        RedirectStatus.calibration.value = CalibrationState.Running(step, message)
        announce(message)
        DebugLog.decision(TAG, "calibration: $message")
    }

    private fun step() {
        val now = SystemClock.uptimeMillis()
        if (now - startedAt > TIMEOUT_MS) {
            finish(null, "Timed out. Recents was not seen within ${TIMEOUT_MS / 1000} seconds")
            return
        }
        val node = root()
        val onSystemLauncher = node?.packageName?.toString() == systemLauncher
        when (phase) {
            Phase.WAIT_HOME -> if (onSystemLauncher) {
                phase = Phase.SETTLE
                homeSeenAt = now
            }

            Phase.SETTLE -> if (!onSystemLauncher) {
                phase = Phase.WAIT_HOME
            } else if (now - homeSeenAt >= SETTLE_MS) {
                baseline = RecentsDetector.collectIds(node)
                DebugLog.tree(TAG, "calibration home ids: ${baseline.joinToString { it.substringAfter(":id/") }}")
                phase = Phase.WAIT_RECENTS
                report(2, "Now open recents and wait a moment")
            }

            Phase.WAIT_RECENTS -> if (onSystemLauncher) {
                val diff = RecentsDetector.collectIds(node) - baseline
                if (diff.isNotEmpty() && diff == lastDiff) {
                    DebugLog.tree(TAG, "calibration recents-only ids: ${diff.joinToString { it.substringAfter(":id/") }}")
                    finish(choose(diff), null)
                } else {
                    lastDiff = diff
                }
            }
        }
    }

    /** Prefer ids that are clearly about recents; generic ids could also show up elsewhere. */
    private fun choose(diff: Set<String>): List<String> {
        val own = diff.filter { it.startsWith("$systemLauncher:id/") }.ifEmpty { diff.toList() }
        val typical = own.filter { id ->
            val short = id.substringAfter(":id/").lowercase()
            HINTS.any { it in short }
        }
        return typical.ifEmpty { own }.sortedBy { it.length }.take(MAX_IDS)
    }

    private fun finish(ids: List<String>?, failure: String?) {
        cancel()
        onFinished(ids, failure)
    }

    private companion object {
        const val TAG = "Redirect"
        const val TICK_MS = 300L
        const val SETTLE_MS = 1500L
        const val TIMEOUT_MS = 45_000L
        const val MAX_IDS = 6
        val HINTS = listOf("overview", "recent", "task", "clear_all")
    }
}
