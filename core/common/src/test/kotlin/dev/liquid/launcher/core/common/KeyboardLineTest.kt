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
    fun aTallerKeyboardPushesTheFieldUpAndItNeverComesDown() {
        val guess = 933f
        val target = 1041f
        var last = 0f
        // The keyboard rises from the bottom to 1041 px: the field waits at 933 px, then rides on the keyboard.
        for (step in 0..40) {
            val ime = target * step / 40f
            val line = KeyboardLine.line(nav, ime, target, guess)
            assertTrue("never below the guess", line >= guess)
            assertTrue("never above the keyboard once it is higher than the guess", ime < guess || line == ime)
            assertTrue("never moves down", line >= last)
            last = line
        }
        assertEquals(target, last)
    }

    @Test
    fun beforeTheKeyboardComesTheFieldWaitsAtTheGuess() {
        assertEquals(933f, KeyboardLine.line(nav, 0f, 0f, 933f))
        assertEquals(933f, KeyboardLine.line(nav, 0f, nav, 933f))
    }

    @Test
    fun aKeyboardLowerThanEverSeenTakesTheFieldDownInStep() {
        val guess = 1041f
        val target = 933f
        assertEquals(guess, KeyboardLine.line(nav, 0f, target, guess))
        assertEquals(target, KeyboardLine.line(nav, target, target, guess))
        val half = KeyboardLine.line(nav, (nav + target) / 2f, target, guess)
        assertTrue(half < guess && half > target)
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
