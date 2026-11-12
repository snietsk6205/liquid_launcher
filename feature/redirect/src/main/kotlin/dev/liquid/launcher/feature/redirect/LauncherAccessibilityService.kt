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

    private val handler = Handler(Looper.getMainLooper())
    private val timers = EnumMap<EngineTimer, Runnable>(EngineTimer::class.java)
    private val engine = RedirectEngine(this)
    private var scope: CoroutineScope? = null
    private lateinit var store: SettingsStore

    private var systemLauncher = ""
    private var autoHomeClasses: Set<String> = emptySet()
    private var userHomeClasses: List<String> = emptyList()
    private var detectConfig = DetectConfig("", emptyList(), emptyList())
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
            if (intent.data?.schemeSpecificPart == systemLauncher) applySettings()
        }
    }

    // ---- lifecycle ----

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = SettingsStore.get(this)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LiquidLauncher:screenOff")
            .apply { setReferenceCounted(false) }

        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        registerReceiver(
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        receiversRegistered = true

        RedirectStatus.attach(this)
        DebugLog.event(TAG, "service connected")

        val newScope = MainScope()
        scope = newScope
        newScope.launch {
            // Emits the current values at once, then again on every change.
            store.values.collect { applySettings() }
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
        calibrator?.cancel()
        calibrator = null
        handler.removeCallbacksAndMessages(null)
        scope?.cancel()
        scope = null
        RedirectStatus.detach(this)
    }

    override fun onInterrupt() = Unit

    // ---- configuration ----

    private fun applySettings() {
        DebugLog.enabled = store.get(LauncherSettings.LogEnabled)
        DebugLog.maxEntries = store.get(LauncherSettings.LogSize)
        DebugLog.verbosity = when (store.get(LauncherSettings.LogVerbosity)) {
            "events" -> LogLevel.EVENT
            "tree" -> LogLevel.TREE
            else -> LogLevel.DECISION
        }

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
        userHomeClasses = store.get(LauncherSettings.HomeClasses)
        RedirectStatus.systemLauncher.value = systemLauncher
        RedirectStatus.homeClasses.value = if (userHomeClasses.isNotEmpty()) userHomeClasses.toSet() else autoHomeClasses
        checkSystemLauncherVersion()

        val ids = store.get(LauncherSettings.RecentsIds) + store.get(LauncherSettings.LearnedIds)
        detectConfig = DetectConfig(
            systemLauncher = systemLauncher,
            exactIds = ids.map { if (":id/" in it) it else "$systemLauncher:id/$it" }.distinct(),
            substrings = store.get(LauncherSettings.RecentsIdSubstrings),
        )

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
        )
        updateServiceInfo()
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
        handler.removeCallbacks(launcherRecheck)
        handler.postDelayed(launcherRecheck, RECHECK_MS)
    }

    private fun refreshSystemLauncher() {
        if (store.get(LauncherSettings.SystemLauncherPackage).isNotBlank()) return
        val detected = SystemLauncher.detectDefault(this)
        if (detected == null) {
            scheduleLauncherRecheck()
        } else if (detected != systemLauncher) {
            applySettings()
        }
    }

    /**
     * Subscribes to the system launcher's window and content events, and to nothing else.
     * The subscription is fixed: changing it while a redirect is being decided would put a
     * system call in the way.
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

    // ---- events ----

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() != systemLauncher) return
        RedirectStatus.lastEventWall = System.currentTimeMillis()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString().orEmpty()
                val home = isHomeClass(className)
                DebugLog.event(TAG, "window: ${className.substringAfterLast('.')}${if (home) " (home)" else ""}")
                if (home) engine.onSystemLauncherWindow() else engine.onContentChanged()
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> engine.onContentChanged()
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

    // ---- commands from the app ----

    internal fun onLauncherResumed() {
        engine.onLauncherResumed()
        refreshSystemLauncher()
    }

    internal fun pauseEngine(durationMs: Long, reason: String) = engine.pause(durationMs, reason)

    internal fun resumeEngine() = engine.resume()

    internal fun startCalibration() {
        calibrator?.cancel()
        engine.pause(60_000, "calibration")
        val c = Calibrator(
            systemLauncher = systemLauncher,
            root = ::activeRoot,
            announce = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() },
            onFinished = { ids, failure ->
                calibrator = null
                if (ids != null) {
                    store.set(LauncherSettings.LearnedIds, ids)
                    store.set(LauncherSettings.DetectorVerified, true)
                    RedirectStatus.calibration.value = CalibrationState.Done(ids)
                    DebugLog.decision(TAG, "calibration learned: ${ids.joinToString { it.substringAfter(":id/") }}")
                    Toast.makeText(this, "Calibration done: ${ids.size} ids learned", Toast.LENGTH_LONG).show()
                } else {
                    RedirectStatus.calibration.value = CalibrationState.Failed(failure ?: "Failed")
                    DebugLog.error(TAG, "calibration failed: $failure")
                    Toast.makeText(this, "Calibration failed", Toast.LENGTH_LONG).show()
                }
                // Leave a moment to get out of recents before redirecting again.
                engine.pause(3000, "calibration finished")
            },
        )
        calibrator = c
        c.start()
    }

    internal fun cancelCalibration() {
        calibrator?.cancel()
        calibrator = null
        RedirectStatus.calibration.value = CalibrationState.Idle
        engine.resume()
    }

    // ---- EngineHost ----

    override fun now(): Long = SystemClock.uptimeMillis()

    override fun schedule(timer: EngineTimer, delayMs: Long) {
        val runnable = timers.getOrPut(timer) { Runnable { engine.onTimer(timer) } }
        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, delayMs)
    }

    override fun cancel(timer: EngineTimer) {
        timers[timer]?.let { handler.removeCallbacks(it) }
    }

    override fun probe(): Probe {
        val start = SystemClock.uptimeMillis()
        val result = RecentsDetector.probe(activeRoot(), detectConfig)
        noteIfSlow("window check", start)
        return result
    }

    /** Anything slow on the main thread delays the redirect, so it is worth seeing in the log. */
    private fun noteIfSlow(what: String, startedAt: Long) {
        val now = SystemClock.uptimeMillis()
        val took = now - startedAt
        if (took >= SLOW_MS && now - lastSlowLogAt >= 1000) {
            lastSlowLogAt = now
            DebugLog.decision(TAG, "slow $what: $took ms")
        }
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
        if (store.get(LauncherSettings.ExcludeSplitScreen)) {
            val split = try {
                windows.any { it.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER }
            } catch (e: Exception) {
                false
            }
            if (split) return "split screen"
        }
        return null
    }

    override fun isLauncherResumed(): Boolean = RedirectStatus.isLauncherResumed(graceMs = 2000)

    override fun launchLauncher(reason: String, triggerAt: Long) {
        val component = RedirectHooks.homeComponent
        if (component == null) {
            DebugLog.error(TAG, "no home activity registered")
            return
        }
        val now = SystemClock.uptimeMillis()
        RedirectStatus.onLaunchRequested(now, triggerAt)
        DebugLog.decision(TAG, "REDIRECT ($reason), ${now - triggerAt} ms after trigger")
        try {
            val intent = Intent(Intent.ACTION_MAIN)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
        } catch (e: Exception) {
            DebugLog.error(TAG, "launch failed: $e")
        }
    }

    override fun setTracking(on: Boolean) = Unit

    override fun onRecentsConfirmed() {
        if (!store.get(LauncherSettings.DetectorVerified)) {
            store.set(LauncherSettings.DetectorVerified, true)
        }
    }

    override fun onStateChanged(state: EngineState) {
        RedirectStatus.engineState.value = state
        if (state == EngineState.HOME && DebugLog.verbosity == LogLevel.TREE) {
            val ids = RecentsDetector.collectIds(activeRoot(), limit = 300)
            DebugLog.tree(TAG, "home ids: ${ids.joinToString { it.substringAfter(":id/") }}")
        }
    }

    override fun onLaunchUnconfirmed() {
        RedirectStatus.launchBlocked.value = true
    }

    override fun onPausedChanged(pausedForMs: Long) {
        RedirectStatus.pausedUntilWall.value =
            if (pausedForMs > 0) System.currentTimeMillis() + pausedForMs else 0L
    }

    override fun log(level: LogLevel, message: String) = DebugLog.log(level, TAG, message)

    private companion object {
        const val TAG = "Redirect"
        const val SLOW_MS = 40L
        const val RECHECK_MS = 5000L
        const val MAX_RECHECKS = 24
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
