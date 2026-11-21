package dev.liquid.launcher.core.data

/**
 * P8 (0.9.21): a layout switched to for a while ("for 1 hour") and the way back. Pure decisions; the home screen
 * checks [due] on its minute tick.
 */
object LayoutTimer {
    /** The layout to go back to now, or null: not yet, no timer, or the layout was changed by hand meanwhile. */
    fun due(now: Long, until: Long, timed: String?, back: String?, current: String?): String? = when {
        until <= 0L || timed.isNullOrBlank() || back.isNullOrBlank() -> null
        now < until -> null
        current != timed -> null
        else -> back
    }

    /** True when the timer no longer matters (it rang, or another layout is on): it can be cleared. */
    fun stale(now: Long, until: Long, timed: String?, current: String?): Boolean =
        until > 0L && (now >= until || current != timed)
}
