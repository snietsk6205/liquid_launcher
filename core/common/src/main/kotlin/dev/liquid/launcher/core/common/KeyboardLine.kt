package dev.liquid.launcher.core.common

/**
 * Where search's field waits for the keyboard (0.11.2, Med: "never above").
 *
 * Up to 0.11.1 the field flew to the height the keyboard last opened at before the keyboard was there; when the keyboard
 * then opened lower (a suggestion strip it no longer showed, a keyboard restarted after a wallpaper change), the field
 * had gone above it and came back down. Now it waits at the LOWEST height the keyboard opened at lately, and from there
 * only the keyboard itself pushes it up. Heights are px from the bottom of the screen, navigation bar included.
 */
object KeyboardLine {
    /** How many recent openings are kept: enough to cover a keyboard that changes its look, few enough to follow a new one. */
    const val KEPT = 6

    /** Below this share of the screen a height is not a keyboard (a hardware keyboard's bar, a floating one). */
    const val MIN_SHARE = 0.12f

    /** The first time, before any opening was seen: low on purpose, so the keyboard pushes the field up rather than down. */
    const val FIRST_GUESS_SHARE = 0.30f

    /** Where the field waits: the lowest recent opening, or a low first guess. */
    fun guess(openings: List<Int>, screenHeight: Float): Float =
        openings.filter { it > 0 }.minOrNull()?.toFloat() ?: (screenHeight * FIRST_GUESS_SHARE)

    /**
     * The bottom of the field before its margin, while search counts on the keyboard. [ime]: the keyboard's top now;
     * [target]: where it is going (at most [nav] while it is away or not yet coming); [guess]: from [guess].
     */
    fun line(nav: Float, ime: Float, target: Float, guess: Float): Float {
        if (target > nav + 1f && target < guess - 1f) {
            // A keyboard lower than any seen lately: the field comes down to it in step with its rise (eased at both
            // ends), the only case where it moves down, and rarer the more openings have been seen.
            val risen = ((ime - nav) / (target - nav)).coerceIn(0f, 1f)
            val eased = risen * risen * (3f - 2f * risen)
            return maxOf(nav, ime, guess + (target - guess) * eased)
        }
        // Otherwise the field waits at the guess and the keyboard pushes it up when it gets there.
        return maxOf(nav, ime, guess)
    }

    /**
     * [openings] after the keyboard settled at [px]: a new opening is added (the oldest dropped past [KEPT]); a later
     * settle while it stays open only ever lowers the newest entry (a strip that went away), never raises it (one
     * that came while typing). Heights under [MIN_SHARE] of [screenHeight] are ignored.
     */
    fun record(openings: List<Int>, px: Int, opening: Boolean, screenHeight: Float): List<Int> {
        if (px < screenHeight * MIN_SHARE) return openings
        if (!opening) {
            val last = openings.lastOrNull() ?: return listOf(px)
            return if (px < last) openings.dropLast(1) + px else openings
        }
        return (openings + px).takeLast(KEPT)
    }
}
