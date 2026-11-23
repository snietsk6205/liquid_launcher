package dev.liquid.launcher.core.data

import java.time.Instant
import java.time.ZoneId

/**
 * 0.16.0 (D204): when an automatic wallpaper change is due, kept free of Android so it is tested.
 *
 * Changes are never made while the home screen is looked at: they happen when the screen turns off ([Moment.SCREEN_OFF],
 * since 0.4) or a little after the launcher went to the back for an app ([Moment.AWAY], 0.16.0), so the new picture is
 * ready when the home screen shows again. "Every unlock" and "once a day" keep to the screen turning off, as before.
 */
object RotationRules {
    enum class Moment { SCREEN_OFF, AWAY }

    /** Times of day in [text] ("07:00, 12:30"; commas, semicolons or spaces between) as minutes after midnight, sorted. */
    fun parseTimes(text: String): List<Int> =
        text.split(',', ';', ' ', '\n').mapNotNull { part ->
            val m = Regex("^(\\d{1,2})[:h.](\\d{2})$").find(part.trim()) ?: return@mapNotNull null
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h in 0..23 && min in 0..59) h * 60 + min else null
        }.distinct().sorted()

    /** The last of [times] (minutes after midnight) at or before [now], as a time; null without times. */
    fun lastSetTime(now: Long, times: List<Int>, zone: ZoneId): Long? {
        if (times.isEmpty()) return null
        val at = Instant.ofEpochMilli(now).atZone(zone)
        val minute = at.hour * 60 + at.minute
        val today = times.filter { it <= minute }
        val (date, m) = if (today.isNotEmpty()) at.toLocalDate() to today.last() else at.toLocalDate().minusDays(1) to times.last()
        return date.atTime(m / 60, m % 60).atZone(zone).toInstant().toEpochMilli()
    }

    /**
     * Whether a change is due at [moment]: "unlock" every time the screen turns off; "daily" when the screen turns off on
     * a new day; "interval" once [minutes] have passed since [last]; "times" once one of [times] came after [last];
     * "off" never. [last] 0: never changed.
     */
    fun due(mode: String, moment: Moment, last: Long, now: Long, minutes: Int, times: List<Int>, zone: ZoneId): Boolean = when (mode) {
        "unlock" -> moment == Moment.SCREEN_OFF
        "daily" -> moment == Moment.SCREEN_OFF && !sameDay(last, now, zone)
        "interval" -> last <= 0L || now - last >= minutes.coerceAtLeast(1) * 60_000L
        "times" -> lastSetTime(now, times, zone)?.let { last < it } ?: false
        else -> false
    }

    fun sameDay(a: Long, b: Long, zone: ZoneId): Boolean {
        if (a <= 0L) return false
        return Instant.ofEpochMilli(a).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
    }
}
