package dev.liquid.launcher.feature.redirect

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.common.LogLevel
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.feature.redirect.engine.EngineConfig
import dev.liquid.launcher.feature.redirect.engine.EngineHost
import dev.liquid.launcher.feature.redirect.engine.EngineState
import dev.liquid.launcher.feature.redirect.engine.EngineTimer
import dev.liquid.launcher.feature.redirect.engine.Probe
import dev.liquid.launcher.feature.redirect.engine.RedirectEngine
import dev.liquid.launcher.feature.redirect.engine.ScreenOffMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.EnumMap

/**
 * Watches the system launcher (and nothing else) and brings Liquid Launcher to the front
 * when the system launcher's home screen would be shown.
 *
 * Battery: events are filtered to the system launcher's package, so the service is not woken
 * by any other app. While the engine is idle, the launcher's content events are dropped at once.
 */
class LauncherAccessibilityService : AccessibilityService(), EngineHost {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * The engine and every look at the system launcher's window run on this thread, never on
     * the UI thread: such a look waits for the system launcher's own UI thread and can take
     * tens of milliseconds while it animates.
     */
    private var engineThread: HandlerThread? = null
    private lateinit var engineHandler: Handler

    /**
     * Looks that may wait a long time (the full window list, a walk over a whole view tree for
     * the log) run here, so they can never hold up a redirect.
     */
    private var sideThread: HandlerThread? = null
    private lateinit var sideHandler: Handler

    /** Last known split-screen state, refreshed on [sideThread]. */
    @Volatile private var splitScreen = false
    private val timers = EnumMap<EngineTimer, Runnable>(EngineTimer::class.java)
    private val engine = RedirectEngine(this)
    private var scope: CoroutineScope? = null
    private lateinit var store: SettingsStore

    @Volatile private var systemLauncher = ""
    private var launcherOverride: String? = null
    private var autoHomeClasses: Set<String> = emptySet()
    private var userHomeClasses: List<String> = emptyList()
    private var detectConfig = DetectConfig("", emptyList(), emptyList(), emptyList())
    private var appliedPackage: String? = null
    private var receiversRegistered = false
    private var calibrator: Calibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var recheckAttempts = 0
    private var lastSlowLogAt = 0L
    private var lastFloatingLogAt = 0L

    /** When a home activity of the system launcher last reported a window change (engine thread). */
    private var lastHomeWindowEventAt = -1_000_000L

    /** Until when (uptime) the engine listens to what the system launcher does behind the launcher. */
    @Volatile private var afterglowUntil = 0L
    private var lastBehindLookAt = 0L
    private val launcherRecheck = Runnable { refreshSystemLauncher() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    DebugLog.event(TAG, "screen off")
                    // Keep the CPU awake until the delayed launch has run.
                    val hold = store.get(LauncherSettings.ScreenOffDelay) + 1500L
                    try {
                        wakeLock?.acquire(hold)
                    } catch (e: Exception) {
                        DebugLog.error(TAG, "wake lock: $e")
                    }
                    engine.onScreenOff()
                }

                Intent.ACTION_USER_PRESENT -> {
                    DebugLog.event(TAG, "unlocked")
                    refreshSystemLauncher()
                    engine.onUserPresent()
                }
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.data?.schemeSpecificPart == systemLauncher) {
                resolveSystemLauncher()
                applySettings()
            }
        }
    }

    // ---- lifecycle ----

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = SettingsStore.get(this)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LiquidLauncher:screenOff")
            .apply { setReferenceCounted(false) }

        val thread = HandlerThread("redirect-engine").apply { start() }
        engineThread = thread
        engineHandler = Handler(thread.looper)
        val side = HandlerThread("redirect-side").apply { start() }
        sideThread = side
        sideHandler = Handler(side.looper)

        // Broadcasts are delivered on the engine thread.
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            null, engineHandler, Context.RECEIVER_NOT_EXPORTED,
        )
        registerReceiver(
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            null, engineHandler, Context.RECEIVER_NOT_EXPORTED,
        )
        receiversRegistered = true

        RedirectStatus.attach(this)
        DebugLog.event(TAG, "service connected")

        val handler = engineHandler
        val newScope = MainScope()
        scope = newScope
        newScope.launch {
            // Emits the current values at once, then again on every change.
            store.values.collect { handler.post { applySettings() } }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (receiversRegistered) {
            receiversRegistered = false
            try {
                unregisterReceiver(screenReceiver)
                unregisterReceiver(packageReceiver)
            } catch (e: Exception) {
                // Already unregistered.
            }
            DebugLog.event(TAG, "service disconnected")
        }
        scope?.cancel()
        scope = null
        engineThread?.let { thread ->
            engineHandler.removeCallbacksAndMessages(null)
            engineHandler.post {
                calibrator?.cancel()
                calibrator = null
            }
            thread.quitSafely()
        }
        engineThread = null
        sideThread?.let { side ->
            sideHandler.removeCallbacksAndMessages(null)
            side.quitSafely()
        }
        sideThread = null
        RedirectStatus.detach(this)
    }

    override fun onInterrupt() = Unit

    /** Runs [block] on the engine thread, if the service is still connected. */
    private fun onEngine(block: () -> Unit) {
        if (engineThread != null) engineHandler.post(block)
    }

    // ---- configuration (engine thread) ----

    private fun applySettings() {
        DebugLog.enabled = store.get(LauncherSettings.LogEnabled)
        DebugLog.maxEntries = store.get(LauncherSettings.LogSize)
        DebugLog.verbosity = when (store.get(LauncherSettings.LogVerbosity)) {
            "events" -> LogLevel.EVENT
            "tree" -> LogLevel.TREE
            else -> LogLevel.DECISION
        }

        val override = store.get(LauncherSettings.SystemLauncherPackage)
        if (override != launcherOverride || systemLauncher.isEmpty()) {
            launcherOverride = override
            resolveSystemLauncher()
        }
        userHomeClasses = store.get(LauncherSettings.HomeClasses)
        RedirectStatus.homeClasses.value = if (userHomeClasses.isNotEmpty()) userHomeClasses.toSet() else autoHomeClasses

        fun full(id: String) = if (":id/" in id) id else "$systemLauncher:id/$id"
        val recentsIds = store.get(LauncherSettings.RecentsIds) + store.get(LauncherSettings.LearnedIds)
        val homeIds = store.get(LauncherSettings.HomeIds)
        detectConfig = DetectConfig(
            systemLauncher = systemLauncher,
            exactIds = recentsIds.map(::full).distinct(),
            substrings = store.get(LauncherSettings.RecentsIdSubstrings),
            homeIds = homeIds.map(::full).distinct(),
            drawerIds = store.get(LauncherSettings.DrawerIds).map(::full).distinct(),
        )

        // The learned counts only mean something for one launcher version and one set of ids.
        val statsKey = "${RedirectStatus.systemLauncherVersion.value}|${homeIds.joinToString(",")}"
        if (store.get(LauncherSettings.StatsKey) != statsKey) {
            STATS.forEach { store.reset(it) }
            store.set(LauncherSettings.StatsKey, statsKey)
        }

        engine.config = EngineConfig(
            enabled = store.get(LauncherSettings.RedirectEnabled),
            onHome = store.get(LauncherSettings.OnHome),
            onHomeDelayMs = store.get(LauncherSettings.OnHomeDelay).toLong(),
            onRecentsExit = store.get(LauncherSettings.OnRecentsExit),
            onRecentsExitDelayMs = store.get(LauncherSettings.OnRecentsExitDelay).toLong(),
            screenOffMode = when (store.get(LauncherSettings.ScreenOffMode)) {
                "home" -> ScreenOffMode.HOME
                "never" -> ScreenOffMode.NEVER
                else -> ScreenOffMode.ALWAYS
            },
            screenOffDelayMs = store.get(LauncherSettings.ScreenOffDelay).toLong(),
            unlockCheck = store.get(LauncherSettings.UnlockCheck),
            pollIntervalMs = store.get(LauncherSettings.PollInterval).toLong(),
            guardCount = store.get(LauncherSettings.GuardCount),
            guardWindowMs = store.get(LauncherSettings.GuardWindow) * 1000L,
            guardPauseMs = store.get(LauncherSettings.GuardPause) * 1000L,
            requireHomeSignature = store.get(LauncherSettings.RequireHomeScreen) && homeIds.isNotEmpty(),
            signatureFallbackMs = store.get(LauncherSettings.HomeScreenFallback).toLong(),
            fastOnHomeAnimation = store.get(LauncherSettings.FastOnHomeAnimation),
            fastDelayMs = store.get(LauncherSettings.FastDelay).toLong(),
            reopenOnHint = store.get(LauncherSettings.ReopenOnHint),
            reopenRecents = store.get(LauncherSettings.ReopenRecents),
            reopenWindowMs = store.get(LauncherSettings.ReopenWindow).toLong(),
        )
        updateServiceInfo()
    }

    private fun resolveSystemLauncher() {
        val override = store.get(LauncherSettings.SystemLauncherPackage)
        val detected = if (override.isBlank()) SystemLauncher.detectDefault(this) else null
        if (detected != null && detected != store.get(LauncherSettings.LastSystemLauncher)) {
            store.set(LauncherSettings.LastSystemLauncher, detected)
        }
        if (override.isBlank() && detected == null) scheduleLauncherRecheck()
        val resolved = SystemLauncher.resolve(this, override, store.get(LauncherSettings.LastSystemLauncher))
        if (resolved != systemLauncher) {
            systemLauncher = resolved
            DebugLog.decision(TAG, "system launcher: $resolved")
        }
        autoHomeClasses = SystemLauncher.homeClasses(this, systemLauncher)
        RedirectStatus.systemLauncher.value = systemLauncher
        checkSystemLauncherVersion()
    }

    /** A new version of the system launcher may have renamed the ids recents is recognised by. */
    private fun checkSystemLauncherVersion() {
        val version = SystemLauncher.version(this, systemLauncher)
        RedirectStatus.systemLauncherVersion.value = version
        if (version.isEmpty()) return
        val known = store.get(LauncherSettings.LastSystemLauncherVersion)
        if (known == version) return
        if (known.isNotEmpty()) {
            DebugLog.decision(TAG, "system launcher updated: $known -> $version; recents detection unconfirmed")
            store.set(LauncherSettings.DetectorVerified, false)
        }
        store.set(LauncherSettings.LastSystemLauncherVersion, version)
    }

    /**
     * Right after boot the default launcher can be a placeholder. Look again a little later,
     * and whenever the phone is unlocked or the launcher comes to the front.
     */
    private fun scheduleLauncherRecheck() {
        if (recheckAttempts >= MAX_RECHECKS) return
        recheckAttempts++
        engineHandler.removeCallbacks(launcherRecheck)
        engineHandler.postDelayed(launcherRecheck, RECHECK_MS)
    }

    private fun refreshSystemLauncher() {
        if (store.get(LauncherSettings.SystemLauncherPackage).isNotBlank()) return
        val detected = SystemLauncher.detectDefault(this)
        if (detected == null) {
            scheduleLauncherRecheck()
        } else if (detected != systemLauncher) {
            resolveSystemLauncher()
            applySettings()
        }
    }

    /**
     * Subscribes to the system launcher's window and content events, and to nothing else.
     * The subscription is fixed: changing it while a redirect is being decided would put a
     * system call in the way.
     *
     * Views that are "not important for accessibility" are deliberately left out: the system
     * launcher marks its home screen that way while a recents gesture is in progress, which
     * is exactly what the home screen check relies on.
     */
    private fun updateServiceInfo() {
        if (appliedPackage == systemLauncher) return
        val info = serviceInfo ?: return
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.packageNames = arrayOf(systemLauncher)
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 0
        val start = SystemClock.uptimeMillis()
        serviceInfo = info
        noteIfSlow("event subscription update", start)
        appliedPackage = systemLauncher
    }

    // ---- events (delivered on the UI thread, handled on the engine thread) ----

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() != systemLauncher) return
        RedirectStatus.lastEventWall = System.currentTimeMillis()
        // Just after an uncertain redirect the event itself is kept, to look at the window it came from.
        val listening = SystemClock.uptimeMillis() <= afterglowUntil
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString().orEmpty()
                val kept = if (listening) AccessibilityEvent(event) else null
                onEngine {
                    val home = isHomeClass(className)
                    DebugLog.event(TAG, "window: ${className.substringAfterLast('.')}${if (home) " (home)" else ""}")
                    if (engine.inAfterglow) {
                        val seen = recentsBehind(kept)
                        // A home window alone says nothing; any other window change is a hint.
                        engine.onActivityBehindLauncher(if (home) seen ?: false else seen)
                    }
                    if (home) {
                        lastHomeWindowEventAt = SystemClock.uptimeMillis()
                        refreshSplitScreen()
                        engine.onSystemLauncherWindow()
                    } else {
                        engine.onContentChanged()
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Dropped here while idle, so animations in the system launcher cost nothing.
                if (RedirectStatus.engineState.value != EngineState.IDLE) {
                    onEngine { engine.onContentChanged() }
                } else if (listening) {
                    val kept = AccessibilityEvent(event)
                    onEngine {
                        val now = SystemClock.uptimeMillis()
                        if (engine.inAfterglow && now - lastBehindLookAt >= BEHIND_LOOK_MS) {
                            lastBehindLookAt = now
                            if (recentsBehind(kept) == true) engine.onActivityBehindLauncher(true)
                        }
                    }
                }
            }
        }
    }

    private fun isHomeClass(className: String): Boolean = when {
        userHomeClasses.isNotEmpty() -> userHomeClasses.any { className == it || className.endsWith(".$it") }
        autoHomeClasses.isNotEmpty() -> className in autoHomeClasses
        else -> true
    }

    /**
     * Whether the window [event] came from shows recents: true or false when its views could be
     * read, null when they could not (the launcher covers it and the system gives nothing).
     */
    private fun recentsBehind(event: AccessibilityEvent?): Boolean? {
        event ?: return null
        val start = SystemClock.uptimeMillis()
        return try {
            val source = event.source ?: return null
            var root: AccessibilityNodeInfo = source.window?.root ?: source
            var depth = 0
            while (depth < 40) {
                root = root.parent ?: break
                depth++
            }
            if (root.packageName?.toString() != systemLauncher) null else RecentsDetector.probe(root, detectConfig).recents
        } catch (e: Exception) {
            null
        } finally {
            noteIfSlow("look behind the launcher", start)
        }
    }

    private fun activeRoot(): AccessibilityNodeInfo? = try {
        rootInActiveWindow
    } catch (e: Exception) {
        null
    }

    private fun toast(text: String) {
        mainHandler.post { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    }

    // ---- commands from the app (any thread) ----

    internal fun onLauncherResumed() = onEngine {
        engine.onLauncherResumed()
        refreshSystemLauncher()
    }

    internal fun pauseEngine(durationMs: Long, reason: String) = onEngine { engine.pause(durationMs, reason) }

    /**
     * 0.13.0 (D175): a picture of the screen (the service's screenshot ability, Android 11+), for the launcher's own
     * search screen only: the keyboard's picture that rises with the pill before the keyboard is ready. [onResult] gets
     * a software bitmap of the whole screen, or null (refused, too soon after the last one, or not allowed).
     */
    internal fun captureScreen(onResult: (android.graphics.Bitmap?) -> Unit) {
        try {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val buffer = screenshot.hardwareBuffer
                        val bitmap = try {
                            android.graphics.Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                ?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                        } catch (e: Exception) {
                            null
                        } finally {
                            buffer.close()
                        }
                        onResult(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        DebugLog.event(TAG, "screen picture refused (code $errorCode)")
                        onResult(null)
                    }
                },
            )
        } catch (e: Exception) {
            DebugLog.error(TAG, "screen picture failed: $e")
            onResult(null)
        }
    }

    internal fun resumeEngine() = onEngine { engine.resume() }

    internal fun startCalibration() = onEngine {
        calibrator?.cancel()
        engine.pause(60_000, "calibration")
        val c = Calibrator(
            handler = engineHandler,
            systemLauncher = systemLauncher,
            root = ::activeRoot,
            announce = ::toast,
            onFinished = { ids, failure ->
                calibrator = null
                if (ids != null) {
                    store.set(LauncherSettings.LearnedIds, ids)
                    store.set(LauncherSettings.DetectorVerified, true)
                    RedirectStatus.calibration.value = CalibrationState.Done(ids)
                    DebugLog.decision(TAG, "calibration learned: ${ids.joinToString { it.substringAfter(":id/") }}")
                    toast("Calibration done: ${ids.size} ids learned")
                } else {
                    RedirectStatus.calibration.value = CalibrationState.Failed(failure ?: "Failed")
                    DebugLog.error(TAG, "calibration failed: $failure")
                    toast("Calibration failed")
                }
                // Leave a moment to get out of recents before redirecting again.
                engine.pause(3000, "calibration finished")
            },
        )
        calibrator = c
        c.start()
    }

    internal fun cancelCalibration() = onEngine {
        calibrator?.cancel()
        calibrator = null
        RedirectStatus.calibration.value = CalibrationState.Idle
        engine.resume()
    }

    // ---- EngineHost (engine thread) ----

    override fun now(): Long = SystemClock.uptimeMillis()

    override fun schedule(timer: EngineTimer, delayMs: Long) {
        val runnable = timers.getOrPut(timer) { Runnable { engine.onTimer(timer) } }
        engineHandler.removeCallbacks(runnable)
        engineHandler.postDelayed(runnable, delayMs)
    }

    /** The display's full size (0.10.4): the going-home animation is told by the home screen being smaller than it. */
    private fun screenBounds(): android.graphics.Rect? = try {
        getSystemService(android.view.WindowManager::class.java)?.maximumWindowMetrics?.bounds
    } catch (e: Exception) {
        null
    }

    override fun cancel(timer: EngineTimer) {
        timers[timer]?.let { engineHandler.removeCallbacks(it) }
    }

    override fun probe(): Probe {
        val start = SystemClock.uptimeMillis()
        val screen = screenBounds()
        var result = RecentsDetector.probe(activeRoot(), detectConfig, screen)
        val watching = RedirectStatus.engineState.value != EngineState.IDLE ||
            start - lastHomeWindowEventAt <= FLOATING_LOOK_MS
        if (!result.isSystemLauncher && result.activePackage != packageName && watching) {
            // During a call the "active" window is often not the app in front: a picture-in-picture
            // call, a chat head, the dialer's floating call button or the status bar's call chip
            // was touched last, or the active window cannot be read at all. Then look for the
            // full-screen app window on top instead.
            val behind = systemLauncherUnderFloatingWindow(result.activePackage)
            if (behind != null) {
                val seen = RecentsDetector.probe(behind, detectConfig, screen)
                if (seen.isSystemLauncher) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastFloatingLogAt >= 5000) {
                        lastFloatingLogAt = now
                        DebugLog.decision(
                            TAG,
                            "active window is ${result.activePackage ?: "unreadable"}, a floating window; " +
                                "the system launcher is the app in front",
                        )
                    }
                    result = seen
                }
            }
        }
        noteIfSlow("window check", start)
        return result
    }

    /**
     * The system launcher's root when its window is the topmost full-screen application window
     * although the active window ([activePackage]) is something else; null otherwise. Windows
     * come front to back. Application windows that are picture-in-picture or clearly smaller
     * than the screen (floating calls, bubbles, pop-up windows) are skipped, as is everything
     * that is not an application window (overlays, the status bar, the keyboard).
     */
    private fun systemLauncherUnderFloatingWindow(activePackage: String?): AccessibilityNodeInfo? {
        if (systemLauncher.isEmpty()) return null
        return try {
            val screen = resources.displayMetrics
            val bounds = android.graphics.Rect()
            for (window in windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                if (window.isInPictureInPictureMode) continue
                window.getBoundsInScreen(bounds)
                val full = bounds.width() >= screen.widthPixels * 0.9f && bounds.height() >= screen.heightPixels * 0.7f
                if (!full) continue
                val root = window.root ?: return null
                // The first full-screen app window decides: the system launcher, or not.
                return if (root.packageName?.toString() == systemLauncher) root else null
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** A slow look at the system launcher delays the redirect, so it is worth seeing in the log. */
    private fun noteIfSlow(what: String, startedAt: Long) {
        val now = SystemClock.uptimeMillis()
        val took = now - startedAt
        if (took >= SLOW_MS && now - lastSlowLogAt >= 1000) {
            lastSlowLogAt = now
            DebugLog.decision(TAG, "slow $what: $took ms")
        }
    }

    /** Reads the window list on the side thread; the result is used by the next redirect. */
    private fun refreshSplitScreen() {
        if (sideThread == null || !store.get(LauncherSettings.ExcludeSplitScreen)) return
        sideHandler.post {
            val start = SystemClock.uptimeMillis()
            splitScreen = try {
                windows.any { it.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER }
            } catch (e: Exception) {
                false
            }
            val took = SystemClock.uptimeMillis() - start
            if (took >= 300) DebugLog.decision(TAG, "window list took $took ms (outside the redirect path)")
        }
    }

    /** Runs [block] on the side thread, if the service is still connected. */
    private fun onSide(block: () -> Unit) {
        if (sideThread != null) sideHandler.post(block)
    }

    override fun exclusionReason(): String? {
        val calls = store.get(LauncherSettings.ExcludeCalls)
        if (calls != "never") {
            val mode = try {
                getSystemService(AudioManager::class.java).mode
            } catch (e: Exception) {
                AudioManager.MODE_NORMAL
            }
            if (mode == AudioManager.MODE_RINGTONE) return "phone ringing"
            if (calls == "call" &&
                (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION)
            ) {
                return "call in progress"
            }
        }
        // Asking for the window list here once held a redirect up for three seconds; the
        // last value read on the side thread is used instead.
        if (store.get(LauncherSettings.ExcludeSplitScreen) && splitScreen) return "split screen"
        return null
    }

    override fun isLauncherResumed(): Boolean = RedirectStatus.isLauncherResumed(graceMs = 2000)

    override fun isLauncherInFront(): Boolean = RedirectStatus.isLauncherResumed()

    override fun launchLauncher(reason: String, triggerAt: Long) {
        val component = RedirectHooks.homeComponent
        if (component == null) {
            DebugLog.error(TAG, "no home activity registered")
            return
        }
        val now = SystemClock.uptimeMillis()
        RedirectStatus.onLaunchRequested(now, triggerAt)
        // 0.11.3 (D170): fading in, the launcher arrives over the system's home screen without its last picture (the
        // picture, then the screen fading in, blinked); at once, it arrives on its last picture when that is up to date.
        val fadeMs = if (store.get(LauncherSettings.SettleStyle) != "none") store.get(LauncherSettings.SettleDuration) else 0
        val arrival = when {
            fadeMs > 0 -> "fades in over $fadeMs ms"
            RedirectStatus.staleSnapshot -> "at once; its last picture is out of date (drawer, search or a menu was open): none"
            else -> "at once, on its last picture"
        }
        DebugLog.decision(TAG, "REDIRECT ($reason), ${now - triggerAt} ms after trigger; $arrival")
        if (DebugLog.verbosity == LogLevel.TREE) traceWindow("at redirect")
        try {
            val intent = Intent(Intent.ACTION_MAIN)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            if (RedirectStatus.staleSnapshot || fadeMs > 0) {
                // The system shows the task's last picture while the launcher comes to the front, unless
                // the intent "asks for something else" (ActivityRecord.allowTaskSnapshot: an intent with
                // extras that is not a plain launcher start). That picture can show the drawer or search
                // the launcher has closed since; and a launcher fading in must start from the system's home.
                intent.putExtra(EXTRA_FRESH, true)
            }
            startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
        } catch (e: Exception) {
            DebugLog.error(TAG, "launch failed: $e")
        }
    }

    override fun homeBounceExpected(): Boolean =
        dev.liquid.launcher.core.common.WallpaperTones.ownWriteActive && RedirectStatus.isLauncherResumed(graceMs = 1500)

    override fun onAfterglow(untilMs: Long) {
        afterglowUntil = untilMs
    }

    override fun reopenRecents() {
        store.set(LauncherSettings.StatsReopened, store.get(LauncherSettings.StatsReopened) + 1)
        // Give the launcher's own window a moment to settle before asking for recents.
        engineHandler.postDelayed({
            if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) DebugLog.error(TAG, "could not open recents")
        }, REOPEN_DELAY_MS)
    }

    override fun setTracking(on: Boolean) = Unit

    override fun onRecentsConfirmed() {
        if (!store.get(LauncherSettings.DetectorVerified)) {
            store.set(LauncherSettings.DetectorVerified, true)
        }
    }

    override fun onStateChanged(state: EngineState) {
        RedirectStatus.engineState.value = state
        if (state == EngineState.HOME && DebugLog.verbosity == LogLevel.TREE) traceWindow("home")
    }

    /**
     * Most detailed log level only: what the active window holds and where the home screen
     * views are. Runs on the side thread: walking another app's tree can take a second, and
     * in 0.2.0 that walk ran on the engine thread and delayed redirects.
     */
    private fun traceWindow(label: String) = onSide {
        val root = activeRoot()
        val pkg = root?.packageName?.toString() ?: "none"
        val ids = RecentsDetector.collectIds(root, limit = 300)
        DebugLog.tree(TAG, "$label: $pkg, ids: ${ids.joinToString { it.substringAfter(":id/") }}")
        if (pkg == systemLauncher && detectConfig.homeIds.isNotEmpty()) {
            DebugLog.tree(TAG, "$label: bounds: ${RecentsDetector.bounds(root, detectConfig.homeIds)}")
        }
        val list = try {
            windows.joinToString { w ->
                "${w.type}/${w.layer}${if (w.isActive) "a" else ""}${if (w.isFocused) "f" else ""} " +
                    (w.root?.packageName?.toString()?.substringAfterLast('.') ?: w.title ?: "?")
            }
        } catch (e: Exception) {
            "unavailable"
        }
        DebugLog.tree(TAG, "$label: windows: $list")
    }

    override fun onLaunchUnconfirmed() {
        RedirectStatus.launchBlocked.value = true
    }

    override fun onPausedChanged(pausedForMs: Long) {
        RedirectStatus.pausedUntilWall.value =
            if (pausedForMs > 0) System.currentTimeMillis() + pausedForMs else 0L
    }

    override fun onEpisode(recentsFollowed: Boolean, signatureSeen: Boolean, afterLaunch: Boolean) {
        if (detectConfig.homeIds.isEmpty()) return
        val counter = when {
            recentsFollowed && signatureSeen -> LauncherSettings.StatsRecentsSeen
            recentsFollowed -> LauncherSettings.StatsRecentsUnseen
            signatureSeen -> LauncherSettings.StatsHomeSeen
            else -> LauncherSettings.StatsHomeUnseen
        }
        store.set(counter, store.get(counter) + 1)
        if (recentsFollowed && afterLaunch) {
            store.set(LauncherSettings.StatsInterrupted, store.get(LauncherSettings.StatsInterrupted) + 1)
        }
    }

    override fun onSignatureFallback() {
        store.set(LauncherSettings.StatsFallbacks, store.get(LauncherSettings.StatsFallbacks) + 1)
    }

    override fun log(level: LogLevel, message: String) = DebugLog.log(level, TAG, message)

    private companion object {
        const val TAG = "Redirect"
        const val SLOW_MS = 40L
        const val REOPEN_DELAY_MS = 60L
        const val BEHIND_LOOK_MS = 60L

        /** Extra on the launch intent: do not show the task's old picture while coming to the front. */
        const val EXTRA_FRESH = "dev.liquid.launcher.FRESH"

        /** How long after the system launcher's window event the full window list may be asked for. */
        const val FLOATING_LOOK_MS = 3000L
        const val RECHECK_MS = 5000L
        const val MAX_RECHECKS = 24
        val STATS = listOf(
            LauncherSettings.StatsHomeSeen, LauncherSettings.StatsHomeUnseen,
            LauncherSettings.StatsRecentsSeen, LauncherSettings.StatsRecentsUnseen,
            LauncherSettings.StatsInterrupted, LauncherSettings.StatsFallbacks, LauncherSettings.StatsReopened,
        )
    }
}

/** Finds out which launcher the redirect should watch. */
object SystemLauncher {
    const val HONOR_HOME = "com.hihonor.android.launcher"

    /**
     * The phone's default launcher, or null while that cannot be told: no default chosen,
     * this app itself, or the placeholder the system shows before the first unlock.
     */
    fun detectDefault(context: Context): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pkg = try {
            context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
        return when (pkg) {
            null, "android", "com.android.settings", context.packageName -> null
            else -> pkg
        }
    }

    /** [override] if set, otherwise the default launcher, the last one seen, or HONOR Home. */
    fun resolve(context: Context, override: String, lastKnown: String): String =
        override.trim().ifEmpty { detectDefault(context) ?: lastKnown.ifEmpty { HONOR_HOME } }

    /** Class names of the launcher's home activities, including alias targets. */
    fun homeClasses(context: Context, pkg: String): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
        return try {
            context.packageManager
                .queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS)
                .flatMap { listOfNotNull(it.activityInfo?.name, it.activityInfo?.targetActivity) }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun version(context: Context, pkg: String): String = try {
        val info = context.packageManager.getPackageInfo(pkg, 0)
        "${info.versionName} (${info.longVersionCode})"
    } catch (e: Exception) {
        ""
    }
}
