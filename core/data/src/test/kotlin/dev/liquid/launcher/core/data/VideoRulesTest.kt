package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRulesTest {
    private val spec = VideoSpec(file = "v.mp4", durationMs = 10_000, width = 1080, height = 1920)
    private val playing = VideoRules.Situation(homeInFront = true, screenOn = true, covered = false, batterySaver = false, battery = 80, charging = false, playedMs = 0, playedThrough = false)
    private val rules = VideoRules.Battery()

    @Test
    fun itPlaysOnlyOnTheVisibleHomeScreen() {
        assertEquals("", VideoRules.pauseReason(spec, playing, rules))
        assertEquals("the screen is off", VideoRules.pauseReason(spec, playing.copy(screenOn = false), rules))
        assertEquals("the home screen is not in front", VideoRules.pauseReason(spec, playing.copy(homeInFront = false), rules))
        assertEquals("the home screen is covered", VideoRules.pauseReason(spec, playing.copy(covered = true), rules))
    }

    @Test
    fun batteryRules() {
        assertEquals("battery saver", VideoRules.pauseReason(spec, playing.copy(batterySaver = true), rules))
        assertEquals("battery below 15 %", VideoRules.pauseReason(spec, playing.copy(battery = 10), rules))
        assertEquals("", VideoRules.pauseReason(spec, playing.copy(battery = -1), rules))
        assertEquals("not charging", VideoRules.pauseReason(spec, playing, rules.copy(onlyCharging = true)))
        assertEquals("", VideoRules.pauseReason(spec, playing.copy(charging = true), rules.copy(onlyCharging = true)))
        assertEquals("", VideoRules.pauseReason(spec, playing.copy(batterySaver = true), rules.copy(pauseInSaver = false)))
    }

    @Test
    fun whenItPlays() {
        val once = spec.copy(plays = "unlock")
        assertEquals("", VideoRules.pauseReason(once, playing, rules))
        assertEquals("played once since the unlock", VideoRules.pauseReason(once, playing.copy(playedThrough = true), rules))
        val few = spec.copy(plays = "seconds", seconds = 5)
        assertEquals("", VideoRules.pauseReason(few, playing.copy(playedMs = 4_999), rules))
        assertEquals("played its 5 s", VideoRules.pauseReason(few, playing.copy(playedMs = 5_000), rules))
    }

    @Test
    fun fillCoversAndPansFitShowsAll() {
        // A landscape video on a portrait screen: fill scales by height and the pan picks the part shown.
        val wide = spec.copy(width = 1920, height = 1080)
        val (scale, x, y) = VideoRules.place(wide, 1920, 1080, 1080f, 2400f)
        assertEquals(2400f / 1080f, scale, 0.001f)
        assertEquals(-(1920 * scale - 1080f) / 2f, x, 0.5f)
        assertEquals(0f, y, 0.5f)
        val (_, left, _) = VideoRules.place(wide.copy(panX = 0f), 1920, 1080, 1080f, 2400f)
        assertEquals(0f, left, 0.01f)
        val (fitScale, fx, fy) = VideoRules.place(wide.copy(fit = "fit"), 1920, 1080, 1080f, 2400f)
        assertEquals(1080f / 1920f, fitScale, 0.001f)
        assertEquals(0f, fx, 0.5f)
        assertEquals((2400f - 1080 * fitScale) / 2f, fy, 0.5f)
    }

    @Test
    fun clipStaysInsideTheVideo() {
        assertEquals(0L to 10_000L, VideoRules.clip(spec))
        assertEquals(2_000L to 6_000L, VideoRules.clip(spec.copy(startMs = 2_000, endMs = 6_000)))
        assertEquals(1_000L to 10_000L, VideoRules.clip(spec.copy(startMs = 1_000, endMs = 99_000)))
    }

    @Test
    fun theGlassBendsTheVideoUntilItCostsTooMuch() {
        assertTrue(VideoRules.glassBendsVideo("auto", null))
        assertTrue(VideoRules.glassBendsVideo("auto", 0.1f))
        assertFalse(VideoRules.glassBendsVideo("auto", 0.4f))
        assertTrue(VideoRules.glassBendsVideo("live", 0.9f))
        assertFalse(VideoRules.glassBendsVideo("still", 0f))
    }
}
