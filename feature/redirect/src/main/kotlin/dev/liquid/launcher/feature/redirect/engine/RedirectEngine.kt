package dev.liquid.launcher.feature.redirect.engine

import dev.liquid.launcher.core.common.LogLevel

enum class ScreenOffMode { ALWAYS, HOME, NEVER }

data class EngineConfig(
    val enabled: Boolean = true,
    val onHome: Boolean = true,
    val onHomeDelayMs: Long = 50,
    val onRecentsExit: Boolean = true,
    val onRecentsExitDelayMs: Long = 80,
    val screenOffMode: ScreenOffMode = ScreenOffMode.ALWAYS,
    val screenOffDelayMs: Long = 1000,
    val unlockCheck: Boolean = true,
    val pollIntervalMs: Long = 250,
    val guardCount: Int = 4,
    val guardWindowMs: Long = 3000,
    val guardPauseMs: Long = 30_000,
)

/** What is on screen right now, as far as the engine needs to know. */
data class Probe(
    /** Package of the active window, or null when it cannot be read (mid-transition). */
    val activePackage: String?,
    val isSystemLauncher: Boolean,
    val recents: Boolean,
    val detail: String = "",
)

enum class EngineState {
    /** Some other app, our launcher, or unknown. Nothing is being watched. */
    IDLE,

    /** The system launcher's home screen is in front. */
    HOME,

    /** The system launcher is showing recents. */
    RECENTS,
}

enum class EngineTimer { EVALUATE, POLL, REDIRECT, SCREEN_OFF }

/** Everything the engine needs from Android, so the logic itself runs in plain unit tests. */
interface EngineHost {
    /** Monotonic milliseconds. */
    fun now(): Long
    fun schedule(timer: EngineTimer, delayMs: Long)
    fun cancel(timer: EngineTimer)
    fun probe(): Probe

    /** A reason not to redirect right now (call, split screen), or null. */
    fun exclusionReason(): String?
    /** The launcher was in front until a moment ago (used to judge what a screen-off interrupted). */
    fun isLauncherResumed(): Boolean

    /** The launcher activity is resumed right now. */
    fun isLauncherInFront(): Boolean
    fun launchLauncher(reason: String, triggerAt: Long)

    /** The engine starts (true) or stops (false) watching the system launcher closely. */
    fun setTracking(on: Boolean)
    fun onRecentsConfirmed()
    fun onStateChanged(state: EngineState)

    /** Several launches in a row did not bring the launcher to the front. */
    fun onLaunchUnconfirmed()
    fun onPausedChanged(pausedForMs: Long)
    fun log(level: LogLevel, message: String)
}

/**
 * Decides when to bring the launcher to the front. Replaces the three MacroDroid macros:
 * system launcher appears and recents is not open; recents closes while the system launcher
 * stays in front; screen turns off.
 *
 * Single-threaded: every method must be called on the same thread.
 */
class RedirectEngine(private val host: EngineHost) {

    var config: EngineConfig = EngineConfig()
        set(value) {
            field = value
            if (!value.enabled) toIdle("redirect disabled")
        }

    var state: EngineState = EngineState.IDLE
        private set

    private var pausedUntil = 0L
    private var redirectPending = false
    private var pendingReason = ""
    private var triggerAt = 0L
    private var evaluateQueued = false
    private var lastLaunchAt = 0L
    private var launchAttempts = 0
    private var declined = false
    private var lastExclusion: String? = null
    private var watchFastUntil = 0L
    private var watchUntil = 0L
    private val recentLaunches = ArrayDeque<Long>()

    val isPaused: Boolean get() = host.now() < pausedUntil

    fun pause(durationMs: Long, reason: String) {
        pausedUntil = host.now() + durationMs
        host.log(LogLevel.DECISION, "paused for ${durationMs / 1000} s ($reason)")
        host.onPausedChanged(durationMs)
        toIdle("paused")
    }

    fun resume() {
        if (pausedUntil == 0L) return
        pausedUntil = 0L
        host.log(LogLevel.DECISION, "resumed")
        host.onPausedChanged(0)
    }

    /** A home activity of the system launcher reported a window change. */
    fun onSystemLauncherWindow() {
        evaluate()
        if (state == EngineState.IDLE && config.enabled && !isPaused) {
            // The event can arrive before the active window has switched (or while an app is
            // still shown live inside recents). Keep looking: often at first, then slowly.
            val now = host.now()
            watchFastUntil = now + WATCH_FAST_MS
            watchUntil = now + WATCH_TOTAL_MS
            host.schedule(EngineTimer.POLL, WATCH_POLL_MS)
        }
    }

    /** Something inside the system launcher changed. Many of these arrive during animations. */
    fun onContentChanged() {
        if (state == EngineState.IDLE || evaluateQueued) return
        evaluateQueued = true
        host.schedule(EngineTimer.EVALUATE, COALESCE_MS)
    }

    fun onLauncherResumed() {
        toIdle("launcher in front")
    }

    fun onScreenOff() {
        val wasHome = state != EngineState.IDLE || host.isLauncherResumed()
        toIdle("screen off")
        host.cancel(EngineTimer.SCREEN_OFF)
        if (!config.enabled || isPaused) return
        when (config.screenOffMode) {
            ScreenOffMode.NEVER -> return
            ScreenOffMode.HOME -> if (!wasHome) return
            ScreenOffMode.ALWAYS -> Unit
        }
        host.schedule(EngineTimer.SCREEN_OFF, config.screenOffDelayMs)
    }

    fun onUserPresent() {
        host.cancel(EngineTimer.SCREEN_OFF)
        if (config.unlockCheck) onSystemLauncherWindow()
    }

    fun onTimer(timer: EngineTimer) {
        when (timer) {
            EngineTimer.EVALUATE -> {
                evaluateQueued = false
                evaluate()
            }

            EngineTimer.POLL -> {
                evaluate()
                if (state != EngineState.IDLE) {
                    host.schedule(EngineTimer.POLL, config.pollIntervalMs)
                } else if (host.now() < watchFastUntil) {
                    host.schedule(EngineTimer.POLL, WATCH_POLL_MS)
                } else if (host.now() < watchUntil) {
                    host.schedule(EngineTimer.POLL, config.pollIntervalMs)
                }
            }

            EngineTimer.REDIRECT -> fire()
            EngineTimer.SCREEN_OFF -> fireScreenOff()
        }
    }

    private fun evaluate() {
        if (!config.enabled || isPaused) {
            toIdle(if (config.enabled) "paused" else "redirect disabled")
            return
        }
        if (host.isLauncherInFront()) {
            // A late event from the system launcher while we are already in front: the window
            // list can lag for a moment, so trust our own activity state.
            toIdle("launcher in front")
            return
        }
        val probe = host.probe()
        if (!probe.isSystemLauncher) {
            // While idle there is nothing to leave; a short watch after a window event keeps running.
            if (probe.activePackage != null && state != EngineState.IDLE) {
                toIdle("in front: ${probe.activePackage}")
            }
            return
        }
        if (probe.recents) {
            enterRecents(probe)
            return
        }
        when (state) {
            EngineState.IDLE -> {
                enter(EngineState.HOME)
                if (config.onHome) {
                    scheduleRedirect(config.onHomeDelayMs, "system launcher appeared")
                } else {
                    decline("trigger \"system launcher appears\" is off")
                }
            }

            EngineState.RECENTS -> {
                enter(EngineState.HOME)
                if (config.onRecentsExit) {
                    scheduleRedirect(config.onRecentsExitDelayMs, "left recents")
                } else {
                    decline("trigger \"leaving recents\" is off")
                }
            }

            EngineState.HOME -> {
                val retryDue = lastLaunchAt == 0L || host.now() - lastLaunchAt >= RETRY_MS
                if (!redirectPending && !declined && retryDue) {
                    attemptLaunch(if (launchAttempts == 0) pendingReason else "retry")
                }
            }
        }
    }

    private fun enterRecents(probe: Probe) {
        if (state == EngineState.RECENTS) return
        cancelRedirect()
        enter(EngineState.RECENTS)
        host.log(LogLevel.DECISION, "recents open (${probe.detail})")
        host.onRecentsConfirmed()
    }

    private fun enter(newState: EngineState) {
        val wasIdle = state == EngineState.IDLE
        state = newState
        declined = false
        launchAttempts = 0
        lastLaunchAt = 0L
        lastExclusion = null
        watchFastUntil = 0L
        watchUntil = 0L
        if (wasIdle) {
            host.setTracking(true)
            host.schedule(EngineTimer.POLL, config.pollIntervalMs)
        }
        host.onStateChanged(newState)
    }

    private fun scheduleRedirect(delayMs: Long, reason: String) {
        redirectPending = true
        pendingReason = reason
        triggerAt = host.now()
        host.log(LogLevel.DECISION, "$reason, redirect in $delayMs ms")
        host.schedule(EngineTimer.REDIRECT, delayMs)
    }

    private fun cancelRedirect() {
        if (!redirectPending) return
        redirectPending = false
        host.cancel(EngineTimer.REDIRECT)
        host.log(LogLevel.DECISION, "pending redirect cancelled")
    }

    private fun decline(reason: String) {
        declined = true
        host.log(LogLevel.DECISION, "not redirecting: $reason")
    }

    private fun fire() {
        if (!redirectPending) return
        redirectPending = false
        if (!config.enabled || isPaused || state == EngineState.IDLE) return
        if (host.isLauncherInFront()) {
            toIdle("launcher in front")
            return
        }
        val probe = host.probe()
        if (!probe.isSystemLauncher) {
            // Unknown window: the poll looks again. A different app: nothing to do.
            if (probe.activePackage != null) toIdle("in front: ${probe.activePackage}")
            return
        }
        if (probe.recents) {
            enterRecents(probe)
            return
        }
        attemptLaunch(pendingReason)
    }

    private fun attemptLaunch(reason: String) {
        val exclusion = host.exclusionReason()
        if (exclusion != null) {
            if (exclusion != lastExclusion) host.log(LogLevel.DECISION, "not redirecting: $exclusion")
            lastExclusion = exclusion
            lastLaunchAt = host.now()
            return
        }
        lastExclusion = null
        if (launchAttempts >= MAX_ATTEMPTS) {
            declined = true
            host.log(LogLevel.ERROR, "launcher did not come to the front after $MAX_ATTEMPTS attempts")
            host.onLaunchUnconfirmed()
            return
        }
        val now = host.now()
        while (recentLaunches.isNotEmpty() && now - recentLaunches.first() > config.guardWindowMs) {
            recentLaunches.removeFirst()
        }
        if (recentLaunches.size >= config.guardCount) {
            recentLaunches.clear()
            host.log(
                LogLevel.ERROR,
                "loop guard: ${config.guardCount} redirects within ${config.guardWindowMs / 1000} s",
            )
            pause(config.guardPauseMs, "loop guard")
            return
        }
        recentLaunches.addLast(now)
        launchAttempts++
        lastLaunchAt = now
        host.launchLauncher(reason, triggerAt)
    }

    private fun fireScreenOff() {
        if (!config.enabled || isPaused) return
        val exclusion = host.exclusionReason()
        if (exclusion != null) {
            host.log(LogLevel.DECISION, "screen off: not redirecting: $exclusion")
            return
        }
        host.launchLauncher("screen off", host.now())
    }

    private fun toIdle(reason: String) {
        val changed = state != EngineState.IDLE
        state = EngineState.IDLE
        redirectPending = false
        evaluateQueued = false
        declined = false
        launchAttempts = 0
        lastLaunchAt = 0L
        lastExclusion = null
        watchFastUntil = 0L
        watchUntil = 0L
        host.cancel(EngineTimer.EVALUATE)
        host.cancel(EngineTimer.POLL)
        host.cancel(EngineTimer.REDIRECT)
        if (changed) {
            host.setTracking(false)
            host.log(LogLevel.DECISION, "idle ($reason)")
            host.onStateChanged(EngineState.IDLE)
        }
    }

    companion object {
        /** Content-change events are merged into one check per frame. */
        const val COALESCE_MS = 16L

        /** Wait this long for a launch to be confirmed before trying again. */
        const val RETRY_MS = 1000L
        const val MAX_ATTEMPTS = 3

        /** After a window event that could not be matched to the active window yet. */
        const val WATCH_FAST_MS = 1500L
        const val WATCH_TOTAL_MS = 30_000L
        const val WATCH_POLL_MS = 80L
    }
}
