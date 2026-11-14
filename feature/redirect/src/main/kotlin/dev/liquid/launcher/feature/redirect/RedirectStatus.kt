package dev.liquid.launcher.feature.redirect

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.common.LatencyStats
import dev.liquid.launcher.feature.redirect.engine.EngineState
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

/** Set once by the application so the service knows which activity to bring forward. */
object RedirectHooks {
    @Volatile
    var homeComponent: ComponentName? = null
}

sealed interface CalibrationState {
    data object Idle : CalibrationState
    data class Running(val step: Int, val message: String) : CalibrationState
    data class Done(val ids: List<String>) : CalibrationState
    data class Failed(val reason: String) : CalibrationState
}

/**
 * Live state of the redirect engine, shared inside the process between the accessibility
 * service, the launcher activity and the settings screens.
 */
object RedirectStatus {
    val connected = MutableStateFlow(false)
    val engineState = MutableStateFlow(EngineState.IDLE)

    /** Wall-clock time until which the engine is paused, or 0. */
    val pausedUntilWall = MutableStateFlow(0L)
    val systemLauncher = MutableStateFlow("")
    val systemLauncherVersion = MutableStateFlow("")
    val homeClasses = MutableStateFlow<Set<String>>(emptySet())

    /** True after several launches in a row that never reached the front. */
    val launchBlocked = MutableStateFlow(false)
    val calibration = MutableStateFlow<CalibrationState>(CalibrationState.Idle)

    val launchesRequested = AtomicInteger()
    val launchesConfirmed = AtomicInteger()

    /** startActivity to first launcher frame. */
    val launchLatency = LatencyStats()

    /** Trigger (window event) to first launcher frame, including the configured delay. */
    val totalLatency = LatencyStats()

    @Volatile var lastEventWall = 0L
        internal set

    @Volatile private var launcherResumed = false
    @Volatile private var lastPausedAt = 0L
    @Volatile private var pendingLaunchAt = 0L
    @Volatile private var pendingTriggerAt = 0L
    private var serviceRef: WeakReference<LauncherAccessibilityService>? = null

    private val service: LauncherAccessibilityService? get() = serviceRef?.get()

    internal fun attach(service: LauncherAccessibilityService) {
        serviceRef = WeakReference(service)
        connected.value = true
    }

    internal fun detach(service: LauncherAccessibilityService) {
        if (serviceRef?.get() === service) serviceRef = null
        connected.value = false
        engineState.value = EngineState.IDLE
    }

    internal fun onLaunchRequested(now: Long, triggerAt: Long) {
        pendingLaunchAt = now
        pendingTriggerAt = triggerAt
        launchesRequested.incrementAndGet()
    }

    /** True while the launcher activity is resumed, or was until [graceMs] ago. */
    fun isLauncherResumed(graceMs: Long = 0): Boolean =
        launcherResumed || (graceMs > 0 && SystemClock.uptimeMillis() - lastPausedAt <= graceMs)

    // ---- called by the launcher activity ----

    fun onLauncherResumed() {
        launcherResumed = true
        service?.onLauncherResumed()
    }

    fun onLauncherPaused() {
        launcherResumed = false
        lastPausedAt = SystemClock.uptimeMillis()
    }

    /** Call when the first frame after a resume has been drawn. */
    fun onLauncherFrame() {
        val launchAt = pendingLaunchAt
        if (launchAt == 0L) return
        pendingLaunchAt = 0L
        val now = SystemClock.uptimeMillis()
        val launchMs = now - launchAt
        val totalMs = now - pendingTriggerAt
        // Longer than this was not a redirect hand-over (for example the screen was off).
        if (launchMs in 0..3000) {
            launchLatency.add(launchMs)
            totalLatency.add(totalMs)
            DebugLog.decision("Redirect", "launcher drawn $launchMs ms after launch, $totalMs ms after trigger")
        }
        launchesConfirmed.incrementAndGet()
        launchBlocked.value = false
    }

    // ---- commands ----

    fun pause(durationMs: Long, reason: String) {
        service?.pauseEngine(durationMs, reason)
    }

    fun resume() {
        service?.resumeEngine()
    }

    /** Opens the system launcher on purpose and keeps the redirect off for a while. */
    fun openSystemLauncher(context: Context, pauseMs: Long) {
        service?.pauseEngine(pauseMs, "system launcher opened on purpose")
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        systemLauncher.value.takeIf { it.isNotEmpty() }?.let { intent.setPackage(it) }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            DebugLog.error("Redirect", "could not open system launcher: $e")
        }
    }

    fun startCalibration(): Boolean {
        val s = service ?: return false
        s.startCalibration()
        return true
    }

    fun cancelCalibration() {
        service?.cancelCalibration()
    }

    /** One of AccessibilityService.GLOBAL_ACTION_*. Returns false when the service is not connected. */
    fun performGlobalAction(action: Int): Boolean = service?.performGlobalAction(action) ?: false
}
