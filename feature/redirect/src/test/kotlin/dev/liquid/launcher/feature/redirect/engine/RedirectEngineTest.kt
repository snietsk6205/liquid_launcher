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
    var tracking = false
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
    override fun exclusionReason() = exclusion
    override fun isLauncherResumed() = launcherResumed
    override fun launchLauncher(reason: String, triggerAt: Long) { launches += reason }
    override fun setTracking(on: Boolean) { tracking = on }
    override fun onRecentsConfirmed() { recentsConfirmed++ }
    override fun onStateChanged(state: EngineState) = Unit
    override fun onLaunchUnconfirmed() { unconfirmed++ }
    override fun onPausedChanged(pausedForMs: Long) { this.pausedForMs = pausedForMs }
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

    fun showHome() { probe = Probe(SYSTEM, isSystemLauncher = true, recents = false) }
    fun showRecents() { probe = Probe(SYSTEM, isSystemLauncher = true, recents = true, detail = "id") }
    fun showOtherApp() { probe = Probe(OTHER, isSystemLauncher = false, recents = false) }
    fun showUnknown() { probe = Probe(null, isSystemLauncher = false, recents = false) }

    /** What the real launcher activity does when it reaches the front. */
    fun confirmLaunch() {
        probe = Probe("dev.liquid.launcher", isSystemLauncher = false, recents = false)
        engine.onLauncherResumed()
    }
}

private fun setup(config: EngineConfig = EngineConfig()): FakeHost {
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
        assertTrue(host.tracking)
        host.advance(49)
        assertTrue(host.launches.isEmpty())
        host.advance(1)
        assertEquals(listOf("system launcher appeared"), host.launches)
        host.confirmLaunch()
        assertEquals(EngineState.IDLE, host.engine.state)
        assertFalse(host.tracking)
        host.advance(5000)
        assertEquals(1, host.launches.size)
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
        assertFalse(host.tracking)
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
    fun watchStopsAfterAWhile() {
        val host = setup()
        host.showOtherApp()
        host.engine.onSystemLauncherWindow()
        host.advance(RedirectEngine.WATCH_MS + 500)
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
        val host = setup(EngineConfig(enabled = false))
        host.showHome()
        host.engine.onSystemLauncherWindow()
        host.advance(5000)
        assertTrue(host.launches.isEmpty())
        assertEquals(EngineState.IDLE, host.engine.state)
        assertTrue(host.timers.isEmpty())
    }

    @Test
    fun homeTriggerOff_staysOnSystemLauncher_butRecentsExitStillWorks() {
        val host = setup(EngineConfig(onHome = false))
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
        val host = setup(EngineConfig(screenOffMode = ScreenOffMode.HOME))
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
        val host = setup(EngineConfig(screenOffMode = ScreenOffMode.NEVER))
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
}
