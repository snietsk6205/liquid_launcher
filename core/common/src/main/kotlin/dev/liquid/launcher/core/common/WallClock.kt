package dev.liquid.launcher.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The home screen's one time source (0.11.3, D168). Every clock, date and timer of the home screen reads [tick]; it
 * is set from the wall clock when the screen turns on, at unlock, when the launcher starts or resumes, on the system's
 * minute tick and when the time or the time zone is set. A delay to the next minute is kept only as a backup: a delay
 * counts uptime, which stops while the phone sleeps, so after the screen was off an old minute stayed on screen until
 * the rest of the delay had run (Med: the clock was wrong for some seconds after unlocking).
 *
 * Free of Android classes so it is tested on the JVM; the app wires the broadcasts and the backup to [refresh].
 */
object WallClock {

    /** What the clocks show: the wall time read at the last change of minute. [serial] counts the changes. */
    data class Tick(val millis: Long, val serial: Long)

    private val state = MutableStateFlow(Tick(System.currentTimeMillis(), 0))

    /** The current tick; a new value only when the minute changed (either way), or the time or the time zone was set. */
    val tick: StateFlow<Tick> = state

    /** Lateness under this is the normal delivery of a minute tick, not worth a log line. */
    const val LOG_AFTER_MS = 1_500L

    /**
     * Reads the wall clock ([now]) and publishes a new tick when its minute is not the one on screen, or always when
     * [force] (the time or the time zone was set: the same minute can read differently). When the screen showed a
     * minute that had already ended, and [visible] (the screen is on), one line says how long it had been wrong and
     * what caught it. Returns how long, in ms, the shown minute had been out of date (0 when it was current).
     */
    fun refresh(reason: String, now: Long = System.currentTimeMillis(), force: Boolean = false, visible: Boolean = true): Long {
        val shown = state.value
        val late = lateness(shown.millis, now)
        val changed = Math.floorDiv(now, MINUTE) != Math.floorDiv(shown.millis, MINUTE)
        if (!changed && !force) return 0L
        state.value = Tick(now, shown.serial + 1)
        if (late >= LOG_AFTER_MS && visible) {
            DebugLog.event("Clock", "the shown minute was ${describe(late)} behind (${hhmm(shown.millis)} until ${hhmm(now)}); caught by $reason")
        }
        return late
    }

    /** Resets the clock (tests). */
    fun reset(now: Long) {
        state.value = Tick(now, 0)
    }

    /**
     * How long the minute of [shownAt] has been over at [now], in ms: 0 while [now] is still in that minute (or
     * before it, after the clock was set back). This is how long a clock showing that minute was wrong.
     */
    fun lateness(shownAt: Long, now: Long): Long {
        val nextMinute = Math.floorDiv(shownAt, MINUTE) * MINUTE + MINUTE
        return if (now >= nextMinute) now - nextMinute else 0L
    }

    /** Uptime to wait for the backup refresh: just past the start of the next minute. */
    fun untilNextMinute(now: Long): Long = MINUTE - Math.floorMod(now, MINUTE) + BACKUP_MARGIN_MS

    /** "450 ms", "12 s", "3 min 05 s", "2 h 27 min". */
    fun describe(ms: Long): String = when {
        ms < 1_000 -> "$ms ms"
        ms < MINUTE -> "${ms / 1_000} s"
        ms < 3_600_000 -> "${ms / MINUTE} min %02d s".format(Locale.US, (ms % MINUTE) / 1_000)
        else -> "${ms / 3_600_000} h ${(ms % 3_600_000) / MINUTE} min"
    }

    private fun hhmm(millis: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))

    private const val MINUTE = 60_000L
    private const val BACKUP_MARGIN_MS = 25L
}
