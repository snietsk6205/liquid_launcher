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
        val reliable = signatureReliable()
        RedirectStatus.signatureReliable.value = reliable

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
            fastMode = store.get(LauncherSettings.FastWhenProven) && reliable,
            fastDelayMs = store.get(LauncherSettings.FastDelay).toLong(),
            reopenRecents = store.get(LauncherSettings.ReopenRecents),
            reopenWindowMs = store.get(LauncherSettings.ReopenWindow).toLong(),
        )
        updateServiceInfo()
    }

    /**
     * True once this phone has shown, often enough, that recents never opens while the home
     * screen views are visible. Only then is the short delay used.
     */
    private fun signatureReliable(): Boolean {
        val homeSeen = store.get(LauncherSettings.StatsHomeSeen)
        val recentsSeen = store.get(LauncherSettings.StatsRecentsSeen)
        val recentsUnseen = store.get(LauncherSettings.StatsRecentsUnseen)
        return homeSeen >= 10 && recentsUnseen >= 5 && recentsSeen * 20 <= recentsUnseen
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
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString().orEmpty()
                onEngine {
                    val home = isHomeClass(className)
                    DebugLog.event(TAG, "window: ${className.substringAfterLast('.')}${if (home) " (home)" else ""}")
                    if (home) {
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
                }
            }
        }
    }

    private fun isHomeClass(className: String): Boolean = when {
        userHomeClasses.isNotEmpty() -> userHomeClasses.any { className == it || className.endsWith(".$it") }
        autoHomeClasses.isNotEmpty() -> className in autoHomeClasses
        else -> true
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

    override fun cancel(timer: EngineTimer) {
        timers[timer]?.let { engineHandler.removeCallbacks(it) }
    }

    override fun probe(): Probe {
        val start = SystemClock.uptimeMillis()
        val result = RecentsDetector.probe(activeRoot(), detectConfig)
        noteIfSlow("window check", start)
        return result
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
        if (store.get(LauncherSettings.ExcludeInCall)) {
            val mode = getSystemService(AudioManager::class.java).mode
            if (mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION ||
                mode == AudioManager.MODE_RINGTONE
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
        DebugLog.decision(TAG, "REDIRECT ($reason), ${now - triggerAt} ms after trigger")
        if (DebugLog.verbosity == LogLevel.TREE) traceWindow("at redirect")
        try {
            val intent = Intent(Intent.ACTION_MAIN)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
        } catch (e: Exception) {
            DebugLog.error(TAG, "launch failed: $e")
        }
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
