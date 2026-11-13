package dev.liquid.launcher.feature.redirect.engine

import dev.liquid.launcher.core.common.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SYSTEM = "com.hihonor.android.launcher"
private const val OTHER = "com.example.app"

private class FakeHost : EngineHost {
    var time = 0L
    val timers = mutableMapOf<EngineTimer, Long>()
    var probe = Probe(OTHER, isSystemLauncher = false, recents = false)
    var exclusion: String? = null
    var launcherResumed = false
    val launches = mutableListOf<String>()
    var trackingOn = false
    var recentsConfirmed = 0
    var unconfirmed = 0
    var pausedForMs = 0L
    val logs = mutableListOf<String>()
    lateinit var engine: RedirectEngine

    override fun now() = time
    val scheduleCalls = mutableMapOf<EngineTimer, Int>()
    override fun schedule(timer: EngineTimer, delayMs: Long) {
        timers[timer] = time + delayMs
        scheduleCalls[timer] = (scheduleCalls[timer] ?: 0) + 1
    }
    override fun cancel(timer: EngineTimer) { timers.remove(timer) }
    override fun probe() = probe
    /** How long the exclusion check blocks (it asks the system for the window list). */
    var exclusionTakesMs = 0L
    override fun exclusionReason(): String? {
        time += exclusionTakesMs
        return exclusion
    }
    override fun isLauncherResumed() = launcherResumed
    var launcherInFront = false
    override fun isLauncherInFront() = launcherInFront
    override fun launchLauncher(reason: String, triggerAt: Long) { launches += reason }
    var reopened = 0
    override fun reopenRecents() { reopened++ }
    override fun setTracking(on: Boolean) { trackingOn = on }
    override fun onRecentsConfirmed() { recentsConfirmed++ }
    override fun onStateChanged(state: EngineState) = Unit
    override fun onLaunchUnconfirmed() { unconfirmed++ }
    override fun onPausedChanged(pausedForMs: Long) { this.pausedForMs = pausedForMs }

    /** "recents"/"home", then "+sig" when the home screen was seen, then "+late" for recents after a launch. */
    val episodes = mutableListOf<String>()
    var fallbacks = 0
    override fun onEpisode(recentsFollowed: Boolean, signatureSeen: Boolean, afterLaunch: Boolean) {
        episodes += (if (recentsFollowed) "recents" else "home") +
            (if (signatureSeen) "+sig" else "") +
            (if (recentsFollowed && afterLaunch) "+late" else "")
    }
    override fun onSignatureFallback() { fallbacks++ }
    override fun log(level: LogLevel, message: String) { logs += message }

    /** Moves the clock forward, running timers in order as they come due. */
    fun advance(ms: Long) {
        val end = time + ms
        while (true) {
            val next = timers.minByOrNull { it.value } ?: break
            if (next.value > end) break
            time = maxOf(time, next.value)
            timers.remove(next.key)
            engine.onTimer(next.key)
        }
        time = end
    }

    fun showHome(signature: Boolean = false, drawer: Boolean = false) {
        show(Probe(SYSTEM, isSystemLauncher = true, recents = false, homeSignature = signature, drawerSignature = drawer))
    }
    fun showRecents() { show(Probe(SYSTEM, isSystemLauncher = true, recents = true, detail = "id")) }
    fun showOtherApp() { show(Probe(OTHER, isSystemLauncher = false, recents = false)) }
    fun showUnknown() { show(Probe(null, isSystemLauncher = false, recents = false)) }

    private fun show(newProbe: Probe) {
        probe = newProbe
        launcherInFront = false
    }

    /** What the real launcher activity does when it reaches the front. */
    fun confirmLaunch() {
        probe = Probe("dev.liquid.launcher", isSystemLauncher = false, recents = false)
        launcherInFront = true
        engine.onLauncherResumed()
    }
}

/** The delays the older tests were written against; the signature tests set their own. */
private val LEGACY = EngineConfig(onHomeDelayMs = 50)

private fun setup(config: EngineConfig = LEGACY): FakeHost {
    val host = FakeHost()
    host.engine = RedirectEngine(host).also { it.config = config }
    return host
}

class RedirectEngineTest {

    @Test
    fun homeAppearing_redirectsAfterDelay() {
        val host = setup()
        host.showHome()
        host.engine.onSystemLauncherWindow()
        assertEquals(EngineState.HOME, host.engine.state)
        assertTrue(host.trackingOn)
        host.advance(49)
        assertTrue(host.launches.isEmpty())
        host.advance(1)
        assertEquals(listOf("system launcher appeared"), host.launches)
        host.confirmLaunch()
        assertEquals(EngineState.IDLE, host.engine.state)
        assertFalse(host.trackingOn)
        host.advance(5000)
        assertEquals(1, host.launches.size)
    }

    @Test
    fun lateSystemLauncherEvent_afterTheLauncherIsInFront_isIgnored() {
        val host = setup()
        host.showHome()
        host.engine.onSystemLauncherWindow()
        host.advance(50)
        assertEquals(1, host.launches.size)
        host.confirmLaunch()
        // The window list still reports the system launcher for a moment.
        host.probe = Probe(SYSTEM, isSystemLauncher = true, recents = false)
        host.engine.onSystemLauncherWindow()
        host.advance(5000)
        assertEquals(1, host.launches.size)
        assertEquals(EngineState.IDLE, host.engine.state)
    }

    @Test
    fun recentsOpen_neverRedirects() {
        val host = setup()
        host.showRecents()
        host.engine.onSystemLauncherWindow()
        assertEquals(EngineState.RECENTS, host.engine.state)
        assertEquals(1, host.recentsConfirmed)
        host.advance(10_000)
        assertTrue(host.launches.isEmpty())
        assertEquals(EngineState.RECENTS, host.engine.state)
    }

    @Test
    fun recentsAppearingDuringDelay_cancelsRedirect() {
        val host = setup()
        host.showHome()
        host.engine.onSystemLauncherWindow()
        host.advance(20)
        host.showRecents()
        host.advance(200)
        assertTrue(host.launches.isEmpty())
        assertEquals(EngineState.RECENTS, host.engine.state)
    }

    @Test
    fun leavingRecentsToHome_redirectsAfterRecentsDelay() {
        val host = setup()
        host.showRecents()
        host.engine.onSystemLauncherWindow()
        host.advance(1000)
        host.showHome()
        host.engine.onContentChanged()
        host.advance(RedirectEngine.COALESCE_MS)
        assertEquals(EngineState.HOME, host.engine.state)
        host.advance(79)
        assertTrue(host.launches.isEmpty())
        host.advance(1)
        assertEquals(listOf("left recents"), host.launches)
    }

    @Test
    fun leavingRecentsWithoutEvents_isCaughtByPoll() {
        val host = setup()
        host.showRecents()
        host.engine.onSystemLauncherWindow()
        host.advance(1000)
        host.showHome()
        host.advance(250 + 80)
        assertEquals(listOf("left recents"), host.launches)
    }

    @Test
    fun pickingAnAppFromRecents_goesIdleWithoutRedirect() {
        val host = setup()
        host.showRecents()
        host.engine.onSystemLauncherWindow()
        host.advance(500)
        host.showOtherApp()
        host.advance(500)
        assertEquals(EngineState.IDLE, host.engine.state)
        assertTrue(host.launches.isEmpty())
        assertFalse(host.trackingOn)
        assertTrue(host.timers.isEmpty())
    }

    @Test
    fun eventBeforeWindowSwitch_isCaughtByWatch() {
        val host = setup()
        host.showOtherApp()
        host.engine.onSystemLauncherWindow()
        assertEquals(EngineState.IDLE, host.engine.state)
        host.advance(100)
        host.showHome()
        host.advance(200)
        assertEquals(1, host.launches.size)
    }

    @Test
    fun homeAppearingLongAfterTheEvent_isStillCaughtBySlowWatch() {
        val host = setup()
        host.showOtherApp()
        host.engine.onSystemLauncherWindow()
        host.advance(10_000)
        assertTrue(host.launches.isEmpty())
        host.showHome()
        host.advance(400)
        assertEquals(1, host.launches.size)
    }

    @Test
    fun recentsAppearingLongAfterTheEvent_isRecognisedAndNotRedirected() {
        val host = setup()
        host.showOtherApp()
        host.engine.onSystemLauncherWindow()
        host.advance(5_000)
        host.showRecents()
        host.advance(5_000)
        assertEquals(EngineState.RECENTS, host.engine.state)
        assertTrue(host.launches.isEmpty())
    }

    @Test
    fun watchStopsAfterAWhile() {
        val host = setup()
        host.showOtherApp()
        host.engine.onSystemLauncherWindow()
        host.advance(RedirectEngine.WATCH_TOTAL_MS + 1000)
        assertTrue(host.timers.isEmpty())
        host.showHome()
        host.advance(5000)
        assertTrue(host.launches.isEmpty())
    }

    @Test
    fun unknownWindowAtFireTime_launchesOncePollSeesHome() {
        val host = setup()
        host.showHome()
        host.engine.onSystemLauncherWindow()
        host.showUnknown()
        host.advance(60)
        assertTrue(host.launches.isEmpty())
        host.showHome()
        host.advance(300)
        assertEquals(1, host.launches.size)
    }

    @Test
    fun disabled_doesNothing() {
        val host = setup(LEGACY.copy(enabled = false))
        host.showHome()
        host.engine.onSystemLauncherWindow()
        host.advance(5000)
        assertTrue(host.launches.isEmpty())
        assertEquals(EngineState.IDLE, host.engine.state)
        assertTrue(host.timers.isEmpty())
    }

    @Test
    fun homeTriggerOff_staysOnSystemLauncher_butRecentsExitStillWorks() {
        val host = setup(LEGACY.copy(onHome = false))
        host.showHome()
        host.engine.onSystemLauncherWindow()
        host.advance(3000)
        assertTrue(host.launches.isEmpty())
        host.showRecents()
        host.advance(300)
        host.showHome()
        host.advance(400)
        assertEquals(listOf("left recents"), host.launches)
    }

    @Test
    fun exclusion_blocksUntilItClears() {
        val host = setup()
        host.exclusion = "call in progress"
        host.showHome()
        host.engine.onSystemLauncherWindow()
        host.advance(3000)
        assertTrue(host.launches.isEmpty())
        assertEquals(1, host.logs.count { it == "not redirecting: call in progress" })
        host.exclusion = null
        host.advance(1000)
        assertEquals(1, host.launches.size)
    }

    @Test
    fun unconfirmedLaunch_retriesThenGivesUp() {
        val host = setup()
        host.showHome()
        host.engine.onSystemLauncherWindow()
        host.advance(10_000)
        assertEquals(RedirectEngine.MAX_ATTEMPTS, host.launches.size)
        assertEquals(1, host.unconfirmed)
    }

    @Test
    fun loopGuard_pausesAfterTooManyRedirects() {
        val host = setup()
        repeat(4) {
            host.showHome()
            host.engine.onSystemLauncherWindow()
            host.advance(50)
            host.confirmLaunch()
            host.advance(100)
        }
        assertEquals(4, host.launches.size)
        host.showHome()
        host.engine.onSystemLauncherWindow()
        host.advance(50)
        assertEquals(4, host.launches.size)
        assertTrue(host.engine.isPaused)
        assertEquals(30_000L, host.pausedForMs)

        // Still paused: nothing happens.
        host.engine.onSystemLauncherWindow()
        host.advance(1000)
        assertEquals(4, host.launches.size)

        // After the pause the next event redirects again.
        host.advance(30_000)
        assertFalse(host.engine.isPaused)
        host.engine.onSystemLauncherWindow()
        host.advance(50)
        assertEquals(5, host.launches.size)
    }

    @Test
    fun slowRedirects_doNotTripTheLoopGuard() {
        val host = setup()
        repeat(10) {
            host.showHome()
            host.engine.onSystemLauncherWindow()
            host.advance(50)
            host.confirmLaunch()
            host.advance(2000)
        }
        assertEquals(10, host.launches.size)
        assertFalse(host.engine.isPaused)
    }

    @Test
    fun manualPause_andResume() {
        val host = setup()
        host.engine.pause(60_000, "open system launcher")
        host.showHome()
        host.engine.onSystemLauncherWindow()
        host.advance(5000)
        assertTrue(host.launches.isEmpty())
        host.engine.resume()
        host.engine.onSystemLauncherWindow()
        host.advance(50)
        assertEquals(1, host.launches.size)
    }

    @Test
    fun screenOff_always_launchesAfterDelay() {
        val host = setup()
        host.showOtherApp()
        host.engine.onScreenOff()
        host.advance(999)
        assertTrue(host.launches.isEmpty())
        host.advance(1)
        assertEquals(listOf("screen off"), host.launches)
    }

    @Test
    fun screenOff_homeOnly_skipsWhenAnAppWasInFront() {
        val host = setup(LEGACY.copy(screenOffMode = ScreenOffMode.HOME))
        host.showOtherApp()
        host.engine.onScreenOff()
        host.advance(5000)
        assertTrue(host.launches.isEmpty())

        host.launcherResumed = true
        host.engine.onScreenOff()
        host.advance(1000)
        assertEquals(1, host.launches.size)
    }

    @Test
    fun screenOff_never() {
        val host = setup(LEGACY.copy(screenOffMode = ScreenOffMode.NEVER))
        host.engine.onScreenOff()
        host.advance(5000)
        assertTrue(host.launches.isEmpty())
    }

    @Test
    fun unlockingBeforeTheScreenOffDelay_cancelsIt() {
        val host = setup()
        host.showOtherApp()
        host.engine.onScreenOff()
        host.advance(300)
        host.engine.onUserPresent()
        host.advance(5000)
        assertTrue(host.launches.isEmpty())
    }

    @Test
    fun unlockOntoSystemLauncher_redirects() {
        val host = setup()
        host.showHome()
        host.engine.onUserPresent()
        host.advance(50)
        assertEquals(1, host.launches.size)
    }

    @Test
    fun contentChangesAreCoalesced() {
        val host = setup()
        host.showRecents()
        host.engine.onSystemLauncherWindow()
        repeat(50) { host.engine.onContentChanged() }
        assertEquals(1, host.scheduleCalls[EngineTimer.EVALUATE])
    }

    @Test
    fun contentChangesWhileIdle_areIgnored() {
        val host = setup()
        host.showHome()
        host.engine.onContentChanged()
        host.advance(1000)
        assertTrue(host.launches.isEmpty())
        assertTrue(host.timers.isEmpty())
    }

    // ---- home screen signature ----

    private val signatureConfig = EngineConfig(onHomeDelayMs = 160, requireHomeSignature = true)

    @Test
    fun signatureVisibleAtOnce_redirectsAfterTheNormalDelay() {
        val host = setup(signatureConfig)
        host.showHome(signature = true)
        host.engine.onSystemLauncherWindow()
        host.advance(159)
        assertTrue(host.launches.isEmpty())
        host.advance(1)
        assertEquals(1, host.launches.size)
        host.confirmLaunch()
        assertEquals(listOf("home+sig"), host.episodes)
        assertEquals(0, host.fallbacks)
    }

    @Test
    fun signatureMissing_waitsPastTheDelay_thenRedirectsAsSoonAsItShows() {
        val host = setup(signatureConfig)
        host.showHome(signature = false)
        host.engine.onSystemLauncherWindow()
        host.advance(400)
        assertTrue(host.launches.isEmpty())
        host.showHome(signature = true)
        host.engine.onContentChanged()
        host.advance(RedirectEngine.COALESCE_MS)
        assertEquals(1, host.launches.size)
        assertEquals(0, host.fallbacks)
    }

    @Test
    fun signatureMissing_thenRecents_neverRedirects() {
        val host = setup(signatureConfig)
        host.showHome(signature = false)
        host.engine.onSystemLauncherWindow()
        host.advance(230)
        host.showRecents()
        host.advance(5000)
        assertTrue(host.launches.isEmpty())
        assertEquals(EngineState.RECENTS, host.engine.state)
        assertEquals(listOf("recents"), host.episodes)
    }

    @Test
    fun signatureNeverShows_fallsBackAfterAWhile() {
        val host = setup(signatureConfig)
        host.showHome(signature = false)
        host.engine.onSystemLauncherWindow()
        host.advance(699)
        assertTrue(host.launches.isEmpty())
        host.advance(1)
        assertEquals(1, host.launches.size)
        assertEquals(1, host.fallbacks)
        host.confirmLaunch()
        assertEquals(listOf("home"), host.episodes)
    }

    @Test
    fun fastMode_redirectsQuicklyWhenTheHomeScreenIsVisible() {
        val host = setup(signatureConfig.copy(fastMode = true, fastDelayMs = 30))
        host.showHome(signature = true)
        host.engine.onSystemLauncherWindow()
        host.advance(29)
        assertTrue(host.launches.isEmpty())
        host.advance(1)
        assertEquals(1, host.launches.size)
    }

    @Test
    fun fastMode_stillWaitsWhileTheHomeScreenIsNotVisible() {
        val host = setup(signatureConfig.copy(fastMode = true, fastDelayMs = 30))
        host.showHome(signature = false)
        host.engine.onSystemLauncherWindow()
        host.advance(300)
        assertTrue(host.launches.isEmpty())
        host.showRecents()
        host.advance(1000)
        assertTrue(host.launches.isEmpty())
    }

    @Test
    fun recentsRightAfterARedirect_isReportedAsInterrupted() {
        val host = setup(signatureConfig)
        host.showHome(signature = true)
        host.engine.onSystemLauncherWindow()
        host.advance(160)
        assertEquals(1, host.launches.size)
        host.showRecents()
        host.advance(100)
        assertEquals(listOf("recents+sig+late"), host.episodes)
        host.confirmLaunch()
        assertEquals(listOf("recents+sig+late"), host.episodes)
    }

    @Test
    fun leavingRecents_waitsForTheHomeScreen_withoutFallback() {
        val host = setup(signatureConfig)
        host.showRecents()
        host.engine.onSystemLauncherWindow()
        host.advance(1000)
        // Recents closed, but what shows is not the home screen (an app-lock screen, a dialog).
        host.showHome(signature = false)
        host.engine.onContentChanged()
        host.advance(5000)
        assertTrue(host.launches.isEmpty())
        host.showHome(signature = true)
        host.engine.onContentChanged()
        host.advance(RedirectEngine.COALESCE_MS)
        assertEquals(listOf("left recents"), host.launches)
        host.confirmLaunch()
        assertTrue(host.episodes.isEmpty())
    }

    @Test
    fun leavingRecentsIntoTheSystemDrawer_redirects() {
        val host = setup(signatureConfig)
        host.showRecents()
        host.engine.onSystemLauncherWindow()
        host.advance(500)
        host.showHome(drawer = true)
        host.engine.onContentChanged()
        host.advance(RedirectEngine.COALESCE_MS + 80)
        assertEquals(listOf("left recents"), host.launches)
    }

    @Test
    fun leavingRecentsIntoAnApp_neverRedirects() {
        val host = setup(signatureConfig)
        host.showRecents()
        host.engine.onSystemLauncherWindow()
        host.advance(500)
        // The recents closing animation still reports the system launcher for a moment.
        host.showHome(signature = false)
        host.engine.onContentChanged()
        host.advance(60)
        host.showOtherApp()
        host.advance(2000)
        assertTrue(host.launches.isEmpty())
        assertEquals(EngineState.IDLE, host.engine.state)
    }

    @Test
    fun leavingRecents_withoutTheHomeScreenCheck_behavesAsBefore() {
        val host = setup(signatureConfig.copy(requireHomeSignature = false))
        host.showRecents()
        host.engine.onSystemLauncherWindow()
        host.advance(500)
        host.showHome(signature = false)
        host.engine.onContentChanged()
        host.advance(RedirectEngine.COALESCE_MS + 80)
        assertEquals(listOf("left recents"), host.launches)
    }

    @Test
    fun drawerVisible_countsAsHomeWhenTheSystemLauncherAppears() {
        val host = setup(signatureConfig)
        host.showHome(drawer = true)
        host.engine.onSystemLauncherWindow()
        host.advance(160)
        assertEquals(1, host.launches.size)
        assertEquals(0, host.fallbacks)
    }

    // ---- recents interrupted by a redirect ----

    private fun interrupt(host: FakeHost, recentsAfterMs: Long) {
        val before = host.launches.size
        host.showHome(signature = true)
        host.engine.onSystemLauncherWindow()
        host.advance(160)
        assertEquals(before + 1, host.launches.size)
        host.showRecents()
        host.engine.onContentChanged()
        host.advance(recentsAfterMs)
    }

    @Test
    fun interruptedRecents_isReopenedOnceTheLauncherCoversIt() {
        val host = setup(signatureConfig)
        interrupt(host, recentsAfterMs = 120)
        assertEquals(EngineState.RECENTS, host.engine.state)
        assertEquals(0, host.reopened)
        host.confirmLaunch()
        assertEquals(1, host.reopened)
        assertEquals(EngineState.IDLE, host.engine.state)
    }

    @Test
    fun interruptedRecents_thatStaysOnTop_isLeftAlone() {
        val host = setup(signatureConfig)
        interrupt(host, recentsAfterMs = 120)
        host.advance(3000)
        assertEquals(0, host.reopened)
        assertEquals(EngineState.RECENTS, host.engine.state)
        // The launcher reaching the front much later is not the interruption any more.
        host.confirmLaunch()
        assertEquals(0, host.reopened)
    }

    @Test
    fun recentsLongAfterTheRedirect_isNotReopened() {
        val late = setup(signatureConfig)
        late.showHome(signature = true)
        late.engine.onSystemLauncherWindow()
        late.advance(160 + 700)
        late.showRecents()
        late.engine.onContentChanged()
        late.advance(RedirectEngine.COALESCE_MS)
        late.confirmLaunch()
        assertEquals(0, late.reopened)
    }

    @Test
    fun reopen_happensAtMostOnceInAShortTime() {
        val host = setup(signatureConfig)
        interrupt(host, recentsAfterMs = 100)
        host.confirmLaunch()
        assertEquals(1, host.reopened)
        host.advance(1500)
        interrupt(host, recentsAfterMs = 100)
        host.confirmLaunch()
        assertEquals(1, host.reopened)
    }

    @Test
    fun reopen_canBeSwitchedOff() {
        val host = setup(signatureConfig.copy(reopenRecents = false))
        interrupt(host, recentsAfterMs = 100)
        host.confirmLaunch()
        assertEquals(0, host.reopened)
    }

    // ---- a blocked service thread ----

    @Test
    fun slowExclusionCheck_doesNotCauseAnImmediateSecondLaunch() {
        val host = setup(signatureConfig)
        host.exclusionTakesMs = 3000
        host.showHome(signature = true)
        host.engine.onSystemLauncherWindow()
        host.advance(160)
        assertEquals(1, host.launches.size)
        host.exclusionTakesMs = 0
        host.engine.onContentChanged()
        host.advance(RedirectEngine.COALESCE_MS)
        assertEquals(1, host.launches.size)
        assertTrue(host.logs.any { it.startsWith("exclusion check took") })
    }

    @Test
    fun signatureDisappearingAgain_goesBackToWaiting() {
        val host = setup(signatureConfig)
        host.showHome(signature = true)
        host.engine.onSystemLauncherWindow()
        host.advance(100)
        host.showHome(signature = false)
        host.engine.onContentChanged()
        host.advance(300)
        assertTrue(host.launches.isEmpty())
    }
}
