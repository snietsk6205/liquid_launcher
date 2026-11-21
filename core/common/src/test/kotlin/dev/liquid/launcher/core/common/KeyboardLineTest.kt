package dev.liquid.launcher.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLineTest {
    private val screen = 2388f
    private val nav = 48f

    @Test
    fun theFieldWaitsAtTheLowestRecentOpening() {
        assertEquals(933f, KeyboardLine.guess(listOf(1041, 933, 980), screen))
        assertEquals(screen * KeyboardLine.FIRST_GUESS_SHARE, KeyboardLine.guess(emptyList(), screen))
    }

    @Test
    fun theFieldRidesOnTheKeyboardAndIsNeverAboveIt() {
        // Med's log: the keyboard opened at 933 px; before it came the field waited there, above an empty gap.
        assertEquals(nav, KeyboardLine.ride(nav, 0f))
        for (step in 0..40) {
            val ime = 933f * step / 40f
            assertEquals(maxOf(nav, ime), KeyboardLine.ride(nav, ime))
        }
    }

    @Test
    fun theResultsArePlannedWhereTheFieldWillEnd() {
        // Not known yet: the lowest recent opening.
        assertEquals(933f, KeyboardLine.planned(nav, 0f, 0f, 0f, 933f))
        // The keyboard's own animation says where it goes.
        assertEquals(1041f, KeyboardLine.planned(nav, 300f, 1041f, 0f, 933f))
        // The launcher drives it: the height the system gave for it fully up wins.
        assertEquals(980f, KeyboardLine.planned(nav, 200f, 0f, 980f, 933f))
        // Never below where it is now.
        assertEquals(1100f, KeyboardLine.planned(nav, 1100f, 1041f, 0f, 933f))
    }

    @Test
    fun theKeyboardSlidesInStepAndALateOneCatchesUp() {
        // Ready from the start: exactly in step with search.
        for (p in listOf(0f, 0.2f, 0.36f, 0.8f, 1f)) assertEquals(p, KeyboardDrive.fraction(p, 10_000), 1e-6f)
        // A spring passing 1 keeps the keyboard fully up, never further.
        assertEquals(1f, KeyboardDrive.fraction(1.08f, 10_000), 1e-6f)
        // Ready when search was at 0.9: it rises over the catch-up time without jumping, then follows.
        assertEquals(0f, KeyboardDrive.fraction(0.9f, 0), 1e-6f)
        var last = 0f
        for (ms in 0..KeyboardDrive.CATCH_UP_MS step 15) {
            val f = KeyboardDrive.fraction(0.9f, ms)
            assertTrue("never goes down while catching up", f >= last)
            assertTrue("never ahead of search", f <= 0.9f)
            last = f
        }
        assertEquals(0.9f, KeyboardDrive.fraction(0.9f, KeyboardDrive.CATCH_UP_MS), 1e-6f)
        // Search closing again: the keyboard goes down with it.
        assertEquals(0.3f, KeyboardDrive.fraction(0.3f, 10_000), 1e-6f)
    }

    @Test
    fun theDriveEndsOnlyWhenSearchIsOpenStillAndCaughtUp() {
        assertTrue(KeyboardDrive.done(1f, open = true, moving = false, sinceReadyMs = 400))
        assertTrue(!KeyboardDrive.done(1f, open = true, moving = true, sinceReadyMs = 400))
        assertTrue(!KeyboardDrive.done(1f, open = false, moving = false, sinceReadyMs = 400))
        assertTrue(!KeyboardDrive.done(0.97f, open = true, moving = false, sinceReadyMs = 400))
        assertTrue(!KeyboardDrive.done(1f, open = true, moving = false, sinceReadyMs = 60))
    }

    @Test
    fun openingsAreKeptAndOnlyAShrinkChangesTheNewest() {
        var list = emptyList<Int>()
        for (h in listOf(1041, 1040, 933, 935, 936, 940, 941)) list = KeyboardLine.record(list, h, opening = true, screenHeight = screen)
        assertEquals(KeyboardLine.KEPT, list.size)
        assertEquals(listOf(1040, 933, 935, 936, 940, 941), list)
        // While it stays open: a strip that went away lowers the newest; one that came while typing does not raise it.
        assertEquals(listOf(1040, 933, 935, 936, 940, 900), KeyboardLine.record(list, 900, opening = false, screenHeight = screen))
        assertEquals(list, KeyboardLine.record(list, 1100, opening = false, screenHeight = screen))
        // Not a keyboard: ignored.
        assertEquals(list, KeyboardLine.record(list, 120, opening = true, screenHeight = screen))
    }
}
