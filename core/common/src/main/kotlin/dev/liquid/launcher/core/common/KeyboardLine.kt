package dev.liquid.launcher.core.common

/**
 * The keyboard's height for search (0.11.2, Med: "never above"; 0.11.3: "the keyboard slides with the pill").
 *
 * Up to 0.11.1 the field flew to the height the keyboard last opened at before the keyboard was there; 0.11.2 made it wait
 * at the LOWEST recent opening. Med's log then showed the field waiting there above an empty gap for 200 to 724 ms
 * while the keyboard started. Since 0.11.3 the field rides on the keyboard ([ride]: never above it) and the launcher
 * slides the keyboard in itself, in step with search ([KeyboardDrive]). The recent openings are still kept: search
 * lays its results out above where the field will end up ([planned]), so they do not move while the keyboard slides
 * in. Heights are px from the bottom of the screen, navigation bar included.
 */
object KeyboardLine {
    /** How many recent openings are kept: enough to cover a keyboard that changes its look, few enough to follow a new one. */
    const val KEPT = 6

    /** Below this share of the screen a height is not a keyboard (a hardware keyboard's bar, a floating one). */
    const val MIN_SHARE = 0.12f

    /** The first time, before any opening was seen: low on purpose, so the results never wait above the field. */
    const val FIRST_GUESS_SHARE = 0.30f

    /** The height the keyboard will most likely open at: the lowest recent opening, or a low first guess. */
    fun guess(openings: List<Int>, screenHeight: Float): Float =
        openings.filter { it > 0 }.minOrNull()?.toFloat() ?: (screenHeight * FIRST_GUESS_SHARE)

    /** The bottom of the field before its gap, now: on the keyboard or the navigation bar, never above them. */
    fun ride(nav: Float, ime: Float): Float = maxOf(nav, ime)

    /**
     * Where the field will end up while search counts on the keyboard: the height the launcher drives it to ([shown],
     * once known), else the one its own animation is heading for ([target]), else [guess]; never below where it is
     * now. 0 for [shown] or [target] means not known.
     */
    fun planned(nav: Float, ime: Float, target: Float, shown: Float, guess: Float): Float {
        val coming = when {
            shown > nav + 1f -> shown
            target > nav + 1f -> target
            else -> guess
        }
        return maxOf(nav, ime, coming)
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
