package dev.liquid.launcher.feature.redirect.engine

import dev.liquid.launcher.core.common.LogLevel

enum class ScreenOffMode { ALWAYS, HOME, NEVER }

data class EngineConfig(
    val enabled: Boolean = true,
    val onHome: Boolean = true,
    val onHomeDelayMs: Long = 160,
    val onRecentsExit: Boolean = true,
    val onRecentsExitDelayMs: Long = 80,
    val screenOffMode: ScreenOffMode = ScreenOffMode.ALWAYS,
    val screenOffDelayMs: Long = 1000,
    val unlockCheck: Boolean = true,
    val pollIntervalMs: Long = 250,
    val guardCount: Int = 4,
    val guardWindowMs: Long = 3000,
    val guardPauseMs: Long = 30_000,
    /**
     * When the system launcher appears, wait until its home screen views are visible before
     * redirecting. While a recents gesture is in progress they are not, so this is a positive
     * sign of "really home" where a delay alone is only a guess.
     */
    val requireHomeSignature: Boolean = false,
    /** Redirect anyway after this long without the home screen views (ids renamed, odd layout). */
    val signatureFallbackMs: Long = 700,
    /** The home screen views have proven reliable on this phone: use [fastDelayMs] when they are visible. */
    val fastMode: Boolean = false,
    val fastDelayMs: Long = 30,
)

/** What is on screen right now, as far as the engine needs to know. */
data class Probe(
    /** Package of the active window, or null when it cannot be read (mid-transition). */
    val activePackage: String?,
    val isSystemLauncher: Boolean,
    val recents: Boolean,
    val detail: String = "",
    /** The system launcher's home screen views are visible. */
    val homeSignature: Boolean = false,
)

enum class EngineState {
    /** Some other app, our launcher, or unknown. Nothing is being watched. */
    IDLE,

    /** The system launcher is in front and is not showing recents. */
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

    /**
     * How one appearance of the system launcher ended: recents opened right after it
     * ([recentsFollowed]) or the redirect went through. [signatureSeen] says whether the home
     * screen views were visible at any point, [afterLaunch] whether a redirect had already been
     * sent (so recents was interrupted). The service keeps these counts to learn whether the
     * home screen views are a reliable sign on this phone.
     */
    fun onEpisode(recentsFollowed: Boolean, signatureSeen: Boolean, afterLaunch: Boolean)

    /** A redirect went out without the home screen views ever becoming visible. */
    fun onSignatureFallback()
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
    private var evaluateQueued = false
    private var watchFastUntil = 0L
    private var watchUntil = 0L
    private val recentLaunches = ArrayDeque<Long>()

    // One stay in the HOME state.
    private var enteredHomeAt = 0L
    private var baseDelayMs = 0L
    private var entryReason = ""
    private var fromIdle = false
    private var signatureSeen = false
    private var waitingLogged = false
    private var declined = false
    private var launchAttempts = 0
    private var nextAttemptAt = 0L
    private var lastExclusion: String? = null

    /** An appearance of the system launcher whose outcome has not been reported yet. */
    private var episodeOpen = false

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
        finishInFront()
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

            EngineTimer.REDIRECT -> evaluate()
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
            finishInFront()
            return
        }
        val probe = host.probe()
        if (!probe.isSystemLauncher) {
            // While idle there is nothing to leave; a watch after a window event keeps running.
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
            EngineState.IDLE -> beginHome(
                fromIdle = true, delayMs = config.onHomeDelayMs, triggerOn = config.onHome,
                reason = "system launcher appeared", probe = probe,
            )

            EngineState.RECENTS -> beginHome(
                fromIdle = false, delayMs = config.onRecentsExitDelayMs, triggerOn = config.onRecentsExit,
                reason = "left recents", probe = probe,
            )

            EngineState.HOME -> Unit
        }
        decide(probe)
    }

    private fun beginHome(fromIdle: Boolean, delayMs: Long, triggerOn: Boolean, reason: String, probe: Probe) {
        val wasIdle = state == EngineState.IDLE
        state = EngineState.HOME
        enteredHomeAt = host.now()
        baseDelayMs = delayMs
        entryReason = reason
        this.fromIdle = fromIdle
        signatureSeen = false
        waitingLogged = false
        declined = false
        launchAttempts = 0
        nextAttemptAt = 0L
        lastExclusion = null
        watchFastUntil = 0L
        watchUntil = 0L
        episodeOpen = fromIdle
        host.cancel(EngineTimer.REDIRECT)
        if (wasIdle) startTracking()
        host.onStateChanged(EngineState.HOME)

        if (!triggerOn) {
            declined = true
            host.log(LogLevel.DECISION, "$reason; not redirecting: this trigger is switched off")
            return
        }
        val wait = dueAt(probe) - enteredHomeAt
        host.log(
            LogLevel.DECISION,
            when {
                !needsSignature() -> "$reason, redirect in $wait ms"
                probe.homeSignature -> "$reason, home screen visible, redirect in $wait ms"
                else -> "$reason, home screen not visible yet: waiting for it (at most $wait ms)"
            },
        )
    }

    private fun needsSignature() = config.requireHomeSignature && fromIdle

    /** When the redirect for the current HOME stay is due, given what is on screen now. */
    private fun dueAt(probe: Probe): Long = enteredHomeAt + when {
        !needsSignature() -> baseDelayMs
        probe.homeSignature -> if (config.fastMode) minOf(config.fastDelayMs, baseDelayMs) else baseDelayMs
        else -> maxOf(baseDelayMs, config.signatureFallbackMs)
    }

    /** In HOME with the system launcher in front and recents not showing: launch now or later. */
    private fun decide(probe: Probe) {
        if (probe.homeSignature && !signatureSeen) {
            signatureSeen = true
            if (waitingLogged) host.log(LogLevel.DECISION, "home screen visible")
        }
        if (declined) return
        val now = host.now()
        val due = maxOf(dueAt(probe), nextAttemptAt)
        if (now < due) {
            if (needsSignature() && !probe.homeSignature && !waitingLogged && now >= enteredHomeAt + baseDelayMs) {
                waitingLogged = true
                host.log(LogLevel.DECISION, "waiting for the home screen to become visible")
            }
            host.schedule(EngineTimer.REDIRECT, due - now)
            return
        }
        val fallback = needsSignature() && !probe.homeSignature && launchAttempts == 0
        attemptLaunch(if (launchAttempts == 0) entryReason else "retry", fallback)
    }

    private fun enterRecents(probe: Probe) {
        if (state == EngineState.RECENTS) return
        val interrupted = state == EngineState.HOME && launchAttempts > 0
        if (episodeOpen && state == EngineState.HOME && host.now() - enteredHomeAt <= EPISODE_MS) {
            host.onEpisode(recentsFollowed = true, signatureSeen = signatureSeen, afterLaunch = interrupted)
        }
        episodeOpen = false
        host.cancel(EngineTimer.REDIRECT)
        val wasIdle = state == EngineState.IDLE
        state = EngineState.RECENTS
        watchFastUntil = 0L
        watchUntil = 0L
        if (wasIdle) startTracking()
        host.onStateChanged(EngineState.RECENTS)
        host.log(
            LogLevel.DECISION,
            "recents open (${probe.detail})" + if (interrupted) "; it opened just after the redirect was sent" else "",
        )
        host.onRecentsConfirmed()
    }

    private fun startTracking() {
        host.setTracking(true)
        host.schedule(EngineTimer.POLL, config.pollIntervalMs)
    }

    private fun attemptLaunch(reason: String, signatureFallback: Boolean) {
        val now = host.now()
        val exclusion = host.exclusionReason()
        if (exclusion != null) {
            if (exclusion != lastExclusion) host.log(LogLevel.DECISION, "not redirecting: $exclusion")
            lastExclusion = exclusion
            nextAttemptAt = now + RETRY_MS
            host.schedule(EngineTimer.REDIRECT, RETRY_MS)
            return
        }
        lastExclusion = null
        if (launchAttempts >= MAX_ATTEMPTS) {
            declined = true
            host.log(LogLevel.ERROR, "launcher did not come to the front after $MAX_ATTEMPTS attempts")
            host.onLaunchUnconfirmed()
            return
        }
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
        if (signatureFallback) {
            host.log(LogLevel.DECISION, "home screen views not seen after ${now - enteredHomeAt} ms, redirecting anyway")
            host.onSignatureFallback()
        }
        recentLaunches.addLast(now)
        launchAttempts++
        nextAttemptAt = now + RETRY_MS
        host.schedule(EngineTimer.REDIRECT, RETRY_MS)
        host.launchLauncher(reason, enteredHomeAt)
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

    /** The launcher reached the front: report how this appearance ended and stop watching. */
    private fun finishInFront() {
        if (episodeOpen && state == EngineState.HOME && launchAttempts > 0) {
            host.onEpisode(recentsFollowed = false, signatureSeen = signatureSeen, afterLaunch = true)
        }
        toIdle("launcher in front")
    }

    private fun toIdle(reason: String) {
        val changed = state != EngineState.IDLE
        state = EngineState.IDLE
        evaluateQueued = false
        episodeOpen = false
        declined = false
        launchAttempts = 0
        nextAttemptAt = 0L
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

        /** Recents opening within this time of the system launcher appearing counts as one gesture. */
        const val EPISODE_MS = 2000L
    }
}
