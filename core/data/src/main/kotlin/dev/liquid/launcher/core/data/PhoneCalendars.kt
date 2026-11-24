package dev.liquid.launcher.core.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import dev.liquid.launcher.core.common.DebugLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** One of the phone's calendars (an account's calendar), with its own colour. */
data class PhoneCalendar(val id: Long, val name: String, val account: String, val color: Int, val visible: Boolean)

/** One occurrence of an event, coloured as the calendar shows it ([color]: the event's, the calendar's, or yours). */
data class PhoneEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String = "",
) {
    /** The days it covers: all-day events are stored at UTC midnight, the others in the phone's time zone. */
    fun days(zone: ZoneId): Pair<LocalDate, LocalDate> {
        val z = if (allDay) ZoneOffset.UTC else zone
        val first = Instant.ofEpochMilli(begin).atZone(z).toLocalDate()
        val last = Instant.ofEpochMilli((end - 1).coerceAtLeast(begin)).atZone(z).toLocalDate()
        return first to if (last.isBefore(first)) first else last
    }
}

/**
 * 0.16.0 (D209): the phone's calendars for the calendar that grows out of the clock's date, its settings and (when it
 * shares them) the Calendar widget. Reads only (READ_CALENDAR); adding an event goes through the calendar app.
 */
object PhoneCalendars {
    fun canRead(context: Context): Boolean = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(context: Context): List<PhoneCalendar> {
        if (!canRead(context)) return emptyList()
        val out = ArrayList<PhoneCalendar>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.VISIBLE,
        )
        try {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, "${CalendarContract.Calendars.ACCOUNT_NAME} ASC")?.use { c ->
                while (c.moveToNext()) {
                    out += PhoneCalendar(c.getLong(0), c.getString(1).orEmpty().ifBlank { "Calendar" }, c.getString(2).orEmpty(), c.getInt(3), c.getInt(4) == 1)
                }
            }
        } catch (e: Exception) {
            DebugLog.error("Calendar", "reading the calendars failed: $e")
        }
        return out
    }

    /**
     * The events from [from] to [until] (excluded) of the calendars the phone shows, but those in [hidden], soonest first;
     * a calendar in [colours] takes that colour.
     */
    fun events(context: Context, from: LocalDate, until: LocalDate, hidden: Set<Long> = emptySet(), colours: Map<Long, Int> = emptyMap()): List<PhoneEvent> {
        if (!canRead(context) || !until.isAfter(from)) return emptyList()
        val zone = ZoneId.systemDefault()
        // A day either side: all-day events sit at UTC midnight.
        val start = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = until.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR, CalendarContract.Instances.EVENT_LOCATION,
        )
        val out = ArrayList<PhoneEvent>()
        try {
            context.contentResolver.query(uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val calendar = c.getLong(1)
                    if (calendar in hidden) continue
                    val event = PhoneEvent(
                        c.getLong(0), calendar, c.getString(2).orEmpty().ifBlank { "(No title)" }, c.getLong(3), c.getLong(4),
                        c.getInt(5) == 1, colours[calendar] ?: c.getInt(6), c.getString(7).orEmpty(),
                    )
                    val (first, last) = event.days(zone)
                    if (last.isBefore(from) || !first.isBefore(until)) continue
                    out += event
                }
            }
        } catch (e: Exception) {
            DebugLog.error("Calendar", "reading events failed: $e")
        }
        return out
    }

    /** [events] by day, from [from] to [until] (excluded): an event over several days is on each of them. */
    fun byDay(events: List<PhoneEvent>, from: LocalDate, until: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<PhoneEvent>> {
        val out = HashMap<LocalDate, MutableList<PhoneEvent>>()
        for (e in events) {
            val (first, last) = e.days(zone)
            var day = if (first.isBefore(from)) from else first
            var guard = 0
            while (!day.isAfter(last) && day.isBefore(until) && guard++ < 400) {
                out.getOrPut(day) { ArrayList() }.add(e)
                day = day.plusDays(1)
            }
        }
        // All-day events first, then by time.
        return out.mapValues { (_, list) -> list.sortedWith(compareBy({ !it.allDay }, { it.begin }, { it.title })) }
    }

    /** "id=#RRGGBB" lines (Settings › Calendar › Calendars and colours) as calendar id → colour. */
    fun parseColours(lines: List<String>): Map<Long, Int> = lines.mapNotNull { line ->
        val id = line.substringBefore('=').trim().toLongOrNull() ?: return@mapNotNull null
        val hex = line.substringAfter('=', "").trim().removePrefix("#")
        if (hex.length != 6) return@mapNotNull null
        val rgb = hex.toLongOrNull(16) ?: return@mapNotNull null
        id to (0xFF000000 or rgb).toInt()
    }.toMap()

    fun encodeColours(colours: Map<Long, Int>): List<String> = colours.entries.sortedBy { it.key }.map { (id, c) -> "$id=#%06X".format(c and 0xFFFFFF) }

    fun parseHidden(lines: List<String>): Set<Long> = lines.mapNotNull { it.trim().toLongOrNull() }.toSet()
}
