package dev.liquid.launcher.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class DialMathTest {
    private val turn = (2 * PI).toFloat()

    @Test
    fun anglesGrowClockwiseOnTheScreen() {
        // y grows downwards: straight below the middle is a quarter turn clockwise from three o'clock.
        assertEquals((PI / 2).toFloat(), DialMath.angle(0f, 10f, 0f, 0f), 1e-5f)
        assertEquals(0f, DialMath.angle(10f, 0f, 0f, 0f), 1e-5f)
    }

    @Test
    fun theShortestTurnCrossesNineOClockWithoutAJump() {
        val justAbove = (PI - 0.1).toFloat()
        val justBelow = (-PI + 0.1).toFloat()
        assertEquals(0.2f, DialMath.turn(justAbove, justBelow), 1e-4f)
        assertEquals(-0.2f, DialMath.turn(justBelow, justAbove), 1e-4f)
    }

    @Test
    fun oneTurnOfTheKnobGoesThroughTheAlphabet() {
        // 26 letters, one turn for all of them: a 26th of a turn is one letter.
        assertEquals(1f, DialMath.knob(0f, turn / 26f, 26, 1f), 1e-4f)
        assertEquals(25f, DialMath.knob(0f, turn * 2f, 26, 1f), 1e-4f)
        // Two turns for the alphabet: half the speed.
        assertEquals(0.5f, DialMath.knob(0f, turn / 26f, 26, 2f), 1e-4f)
    }

    @Test
    fun theKnobStopsAtTheEndsAndTurnsBackAtOnce() {
        val atStart = DialMath.knob(0f, -turn, 26, 1f)
        assertEquals(0f, atStart, 0f)
        assertEquals(1f, DialMath.knob(atStart, turn / 26f, 26, 1f), 1e-4f)
    }

    @Test
    fun theClockFaceHasTheFirstLetterAtTwelve() {
        val twelve = (-PI / 2).toFloat()
        assertEquals(0f, DialMath.clock(twelve, 26), 1e-4f)
        // Three o'clock is a quarter of the way round.
        assertEquals(6.5f, DialMath.clock(0f, 26), 1e-4f)
        // Just before twelve is still the first letter, not past the last.
        assertEquals(0f, DialMath.clock(twelve - 0.01f, 26), 1e-4f)
        // A little further back is the last letter.
        assertEquals(25f, DialMath.clock(twelve - turn / 26f, 26), 1e-3f)
        // Each letter's own mark lies where the face reads that letter.
        assertEquals(13f, DialMath.clock(DialMath.clockAngle(13f, 26), 26), 1e-4f)
    }
}
