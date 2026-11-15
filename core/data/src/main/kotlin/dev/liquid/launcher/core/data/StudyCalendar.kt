package dev.liquid.launcher.core.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A calendar on the phone, for choosing which ones hold study blocks and tasks. */
data class StudyCalendarInfo(val id: Long, val name: String, val account: String, val color: Int, val writable: Boolean)

/** A focus timer running on a block. */
data class StudyFocus(val eventKey: String, val eventId: Long, val startedAt: Long)

/**
 * The study companion's data: study blocks and tasks read from the phone's calendar, and the
 * three marks the launcher writes back (see [StudyFormat] and `docs/05-study-integration.md`).
 *
 * No account and no network: Android's calendar sync carries the events both ways. Nothing
 * runs in the background; the launcher refreshes when it comes to the front, when the
 * calendar changes while it is watching, and after its own edits.
 */
class StudyCalendar(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _day = MutableStateFlow(StudyDay())
    private val _focus = MutableStateFlow<StudyFocus?>(null)
    private val _loaded = MutableStateFlow(false)
    private val prefs = context.getSharedPreferences("study", Context.MODE_PRIVATE)
    private var refreshJob: Job? = null
    private var watching = false

    /** Today's blocks and tasks. Empty while the companion is off or the calendar cannot be read. */
    val day: StateFlow<StudyDay> = _day
    val focus: StateFlow<StudyFocus?> = _focus

    /** True once the calendar has been read at least once since the companion was switched on. */
    val loaded: StateFlow<Boolean> = _loaded

    val enabled: Boolean get() = settings.get(LauncherSettings.StudyEnabled)
    val canRead: Boolean get() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    val canWrite: Boolean get() = context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh(delayMs = 400)
    }

    init {
        val key = prefs.getString("focusKey", null)
        if (key != null) _focus.value = StudyFocus(key, prefs.getLong("focusEvent", 0), prefs.getLong("focusStart", 0))
    }

    /** Starts or stops following calendar changes (while the launcher is on screen). */
    fun watch(on: Boolean) {
        if (on == watching) return
        watching = on
        try {
            if (on && enabled && canRead) {
                context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            } else {
                context.contentResolver.unregisterContentObserver(observer)
                watching = false
            }
        } catch (e: Exception) {
            watching = false
        }
    }

    /** Reads the calendar again, off the main thread. Quick calls in a row are merged. */
    fun refresh(delayMs: Long = 0) {
        refreshJob?.cancel()
        refreshJob = scope.launch(Dispatchers.IO) {
            if (delayMs > 0) delay(delayMs)
            _day.value = if (enabled && canRead) load() else StudyDay()
            _loaded.value = enabled && canRead
        }
    }

    /** The phone's calendars, for the settings screen. */
    fun calendars(): List<StudyCalendarInfo> {
        if (!canRead) return emptyList()
        val out = ArrayList<StudyCalendarInfo>()
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    out += StudyCalendarInfo(
                        c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getInt(3),
                        c.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                    )
                }
            }
        } catch (e: Exception) {
            DebugLog.error("Study", "listing calendars failed: $e")
        }
        return out
    }

    private fun words(setting: dev.liquid.launcher.core.settings.TextSetting): List<String> =
        settings.get(setting).split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    private fun load(): StudyDay {
        val blockWords = words(LauncherSettings.StudyBlockCalendars)
        val taskWords = words(LauncherSettings.StudyTaskCalendars)
        val all = calendars()
        val blockCalendars = all.filter { cal -> blockWords.any { it in cal.name.lowercase() } }.map { it.id }.toSet()
        val taskCalendars = all.filter { cal -> taskWords.any { it in cal.name.lowercase() } }.map { it.id }.toSet() - blockCalendars
        if (blockCalendars.isEmpty() && taskCalendars.isEmpty()) return StudyDay()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val overdueDays = settings.get(LauncherSettings.StudyOverdueDays).toLong()
        val from = today.minusDays(overdueDays + 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val blocks = ArrayList<StudyEvent>()
        val tasks = ArrayList<StudyEvent>()
        val overdue = ArrayList<StudyEvent>()
        var tomorrow: StudyEvent? = null
        try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.TITLE, CalendarContract.Instances.DESCRIPTION,
                    CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.DISPLAY_COLOR,
                    CalendarContract.Instances.RRULE, CalendarContract.Instances.STATUS,
                ),
                null, null, CalendarContract.Instances.BEGIN + " ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val calendarId = c.getLong(7)
                    val isBlockCalendar = calendarId in blockCalendars
                    if (!isBlockCalendar && calendarId !in taskCalendars) continue
                    if (c.getInt(10) == CalendarContract.Events.STATUS_CANCELED) continue
                    val allDay = c.getInt(3) != 0
                    val event = StudyEvent(
                        eventId = c.getLong(0), calendarId = calendarId, begin = c.getLong(1), end = c.getLong(2), allDay = allDay,
                        rawTitle = c.getString(4).orEmpty(), description = c.getString(5).orEmpty(), location = c.getString(6).orEmpty(),
                        color = c.getInt(8), recurring = !c.getString(9).isNullOrEmpty(),
                    )
                    if (event.rawTitle.isBlank()) continue
                    // All-day events are stored from midnight to midnight UTC; timed ones in real time.
                    val firstDay = if (allDay) Instant.ofEpochMilli(event.begin).atZone(ZoneOffset.UTC).toLocalDate() else Instant.ofEpochMilli(event.begin).atZone(zone).toLocalDate()
                    val dayAfter = if (allDay) Instant.ofEpochMilli(event.end).atZone(ZoneOffset.UTC).toLocalDate() else Instant.ofEpochMilli(event.end - 1).atZone(zone).toLocalDate().plusDays(1)
                    val isToday = !today.isBefore(firstDay) && today.isBefore(dayAfter)
                    when {
                        isBlockCalendar && !allDay -> when {
                            isToday -> blocks += event
                            firstDay == today.plusDays(1) && tomorrow == null -> tomorrow = event
                        }
                        // Everything else is a task: all-day events, and timed events of the task calendars.
                        isToday -> tasks += event
                        !dayAfter.isAfter(today) && event.status == StudyStatus.Open -> overdue += event
                    }
                }
            }
        } catch (e: Exception) {
            DebugLog.error("Study", "reading the calendar failed: $e")
        }
        return StudyDay(
            blocks = blocks.sortedBy { it.begin },
            tasks = tasks.sortedWith(compareBy({ it.status != StudyStatus.Open }, { it.begin }, { it.rawTitle })),
            overdue = overdue.sortedByDescending { it.begin },
            tomorrow = tomorrow,
        )
    }

    // ---- writing back ----

    /** Ticks or unticks task [index] of [event]. Returns false when the calendar could not be changed. */
    suspend fun setTask(event: StudyEvent, index: Int, done: Boolean): Boolean = edit(event) { title, description ->
        val changed = StudyFormat.setTask(description, index, done) ?: return@edit null
        title to StudyFormat.refreshed(changed, StudyFormat.note(description))
    }

    /** Marks [event] done, skipped or open again. */
    suspend fun setStatus(event: StudyEvent, status: StudyStatus): Boolean = edit(event) { title, description ->
        val note = StudyFormat.note(description).copy(
            doneAt = if (status == StudyStatus.Done) LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm")) else null,
            skipped = status == StudyStatus.Skipped,
        )
        StudyFormat.withStatus(title, status) to StudyFormat.refreshed(description, note)
    }

    /** Adds [minutes] of focused time to [event]'s status line. */
    private suspend fun addFocus(eventId: Long, begin: Long, minutes: Int): Boolean {
        val event = _day.value.blocks.firstOrNull { it.eventId == eventId && it.begin == begin } ?: return false
        return edit(event) { title, description ->
            val note = StudyFormat.note(description)
            title to StudyFormat.refreshed(description, note.copy(focusMinutes = note.focusMinutes + minutes))
        }
    }

    fun startFocus(event: StudyEvent) {
        val focus = StudyFocus(event.key, event.eventId, System.currentTimeMillis())
        _focus.value = focus
        prefs.edit().putString("focusKey", focus.eventKey).putLong("focusEvent", focus.eventId).putLong("focusStart", focus.startedAt).apply()
    }

    /** Stops the focus timer and writes the minutes to its block. Returns the minutes focused. */
    suspend fun stopFocus(): Int {
        val focus = _focus.value ?: return 0
        _focus.value = null
        prefs.edit().remove("focusKey").remove("focusEvent").remove("focusStart").apply()
        val minutes = ((System.currentTimeMillis() - focus.startedAt + 30_000) / 60_000).toInt().coerceIn(0, 12 * 60)
        if (minutes > 0) addFocus(focus.eventId, focus.eventKey.substringAfter('@').toLongOrNull() ?: 0, minutes)
        return minutes
    }

    /**
     * Applies [change] to the event's current title and description (read fresh, so an edit
     * that arrived by sync a moment ago is not overwritten) and writes the result. One
     * occurrence of a repeating event becomes an exception of its series.
     */
    private suspend fun edit(event: StudyEvent, change: (title: String, description: String) -> Pair<String, String>?): Boolean = withContext(Dispatchers.IO) {
        if (!settings.get(LauncherSettings.StudyWriteBack) || !canWrite) return@withContext false
        try {
            val resolver = context.contentResolver
            val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
            var title = event.rawTitle
            var description = event.description
            resolver.query(eventUri, arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DESCRIPTION), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    title = c.getString(0).orEmpty()
                    description = c.getString(1).orEmpty()
                }
            }
            val (newTitle, newDescription) = change(title, description) ?: return@withContext false
            if (newTitle == title && newDescription == description) return@withContext true
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, newTitle)
                put(CalendarContract.Events.DESCRIPTION, newDescription)
            }
            val ok = if (event.recurring) {
                values.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, event.begin)
                resolver.insert(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, event.eventId), values) != null
            } else {
                resolver.update(eventUri, values, null, null) > 0
            }
            DebugLog.event("Study", "calendar event ${event.eventId} " + if (ok) "updated" else "could not be updated")
            if (ok) _day.value = load()
            ok
        } catch (e: Exception) {
            DebugLog.error("Study", "writing to the calendar failed: $e")
            false
        }
    }
}
