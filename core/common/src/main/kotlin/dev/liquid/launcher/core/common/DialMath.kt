package dev.liquid.launcher.core.common

import kotlin.math.PI
import kotlin.math.atan2

/**
 * The letter circle's arithmetic (0.11.0), kept apart from its drawing so it can be tested. Angles are radians on the
 * screen, where y grows downwards: 0 is three o'clock and they grow clockwise. Positions are in letters: 0 is the
 * first, fractions lie between two letters (the string bends there and the list glides there).
 */
object DialMath {
    private const val TWO_PI = (2 * PI).toFloat()
    private const val HALF_PI = (PI / 2).toFloat()

    /** Angle of the point ([x], [y]) around the middle ([cx], [cy]). */
    fun angle(x: Float, y: Float, cx: Float, cy: Float): Float = atan2(y - cy, x - cx)

    /** The shortest turn from angle [from] to angle [to], in (-π, π]: positive is clockwise. */
    fun turn(from: Float, to: Float): Float {
        var d = to - from
        while (d > PI) d -= TWO_PI
        while (d <= -PI) d += TWO_PI
        return d
    }

    /**
     * Like a knob: the position after turning by [radians] from [position], with [count] letters and [turns] full turns
     * for the whole alphabet. Clockwise goes on through the alphabet. It stops at both ends, and turning back moves at
     * once, without a dead stretch past the end.
     */
    fun knob(position: Float, radians: Float, count: Int, turns: Float): Float {
        if (count <= 1) return 0f
        val perLetter = turns.coerceAtLeast(0.1f) * TWO_PI / count
        return (position + radians / perLetter).coerceIn(0f, (count - 1).toFloat())
    }

    /**
     * Like a clock face: the position at [angle], the first letter at twelve o'clock and the others round clockwise
     * with equal room, the last just before twelve.
     */
    fun clock(angle: Float, count: Int): Float {
        if (count <= 1) return 0f
        var fromTop = angle + HALF_PI
        while (fromTop < 0f) fromTop += TWO_PI
        while (fromTop >= TWO_PI) fromTop -= TWO_PI
        var p = fromTop / TWO_PI * count
        // Just before twelve is the first letter's half of its slot, not past the last one.
        if (p > count - 0.5f) p -= count
        return p.coerceIn(0f, (count - 1).toFloat())
    }

    /** Where letter [position] sits on a clock face of [count] letters, as an angle (for drawing marks). */
    fun clockAngle(position: Float, count: Int): Float = if (count <= 0) -HALF_PI else position / count * TWO_PI - HALF_PI
}
