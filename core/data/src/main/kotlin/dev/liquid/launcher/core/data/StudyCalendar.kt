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
import dev.liquid.launcher.core.common.NoticeKind
import dev.liquid.launcher.core.common.Notices
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A calendar on the phone, for choosing which ones hold study blocks and tasks. */
data class StudyCalendarInfo(val id: Long, val name: String, val account: String, val color: Int, val writable: Boolean, val primary: Boolean = false)

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
            _homeAt.value = storedHome()
            // Not part of this job: a newer refresh must not cancel the write of the minutes.
            if (_focus.value != null) scope.launch(Dispatchers.IO) { finishFocusIfOver() }
        }
    }

    /**
     * True the first time [what] is asked for today. Remembered across restarts of the launcher,
     * so "tasks due today" is announced once a day and a block's start once (5.3).
     */
    fun firstToday(what: String): Boolean {
        val today = LocalDate.now().toString() + "|"
        val seen = prefs.getStringSet("announced", emptySet()).orEmpty().filter { it.startsWith(today) }.toMutableSet()
        if (!seen.add(today + what)) return false
        prefs.edit().putStringSet("announced", seen).apply()
        return true
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
                    CalendarContract.Calendars.OWNER_ACCOUNT,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    out += StudyCalendarInfo(
                        c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getInt(3),
                        c.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                        // An account's main calendar belongs to the account itself (what the provider's isPrimary is computed from).
                        primary = c.getString(5).orEmpty().equals(c.getString(2).orEmpty(), ignoreCase = true) ||
                            c.getString(1).orEmpty().equals(c.getString(2).orEmpty(), ignoreCase = true),
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
        // Classes (0.9.1): the calendars named in the setting, else the account's main calendar.
        val classWords = words(LauncherSettings.StudyClassCalendars)
        val classCalendars = all.filter { cal -> if (classWords.isEmpty()) cal.primary else classWords.any { it in cal.name.lowercase() } }
            .map { it.id }.toSet() - blockCalendars - taskCalendars
        val classes = ArrayList<StudyEvent>()
        val notes = ArrayList<StudyEvent>()

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
        val tomorrowBlocks = ArrayList<StudyEvent>()
        val tomorrowTasks = ArrayList<StudyEvent>()
        val local = localEdits()
        val stale = HashSet<String>()
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
                    val isClassCalendar = calendarId in classCalendars
                    if (!isBlockCalendar && !isClassCalendar && calendarId !in taskCalendars) continue
                    if (c.getInt(10) == CalendarContract.Events.STATUS_CANCELED) continue
                    val allDay = c.getInt(3) != 0
                    if (isClassCalendar) {
                        // Only today's timed events matter: when the last class ends.
                        val begin = c.getLong(1)
                        if (!allDay && Instant.ofEpochMilli(begin).atZone(zone).toLocalDate() == today) {
                            classes += StudyEvent(c.getLong(0), calendarId, begin, c.getLong(2), false, c.getString(4).orEmpty(), location = c.getString(6).orEmpty(), color = c.getInt(8))
                        }
                        continue
                    }
                    val fromCalendar = StudyEvent(
                        eventId = c.getLong(0), calendarId = calendarId, begin = c.getLong(1), end = c.getLong(2), allDay = allDay,
                        rawTitle = c.getString(4).orEmpty(), description = c.getString(5).orEmpty(), location = c.getString(6).orEmpty(),
                        color = c.getInt(8), recurring = !c.getString(9).isNullOrEmpty(),
                    )
                    // Ticks kept on the phone show until the event changes in the calendar.
                    val kept = local[fromCalendar.key]
                    if (kept != null && !StudyLocal.isCurrent(kept, fromCalendar.rawTitle, fromCalendar.description)) stale += fromCalendar.key
                    val event = StudyLocal.apply(fromCalendar, kept)
                    if (event.rawTitle.isBlank()) continue
                    // All-day events are stored from midnight to midnight UTC; timed ones in real time.
                    val firstDay = if (allDay) Instant.ofEpochMilli(event.begin).atZone(ZoneOffset.UTC).toLocalDate() else Instant.ofEpochMilli(event.begin).atZone(zone).toLocalDate()
                    val dayAfter = if (allDay) Instant.ofEpochMilli(event.end).atZone(ZoneOffset.UTC).toLocalDate() else Instant.ofEpochMilli(event.end - 1).atZone(zone).toLocalDate().plusDays(1)
                    val isToday = !today.isBefore(firstDay) && today.isBefore(dayAfter)
                    // A quick note ("Note: …") is for the assistant, not a task to tick.
                    if (event.rawTitle.trimStart().startsWith(StudyFormat.NOTE_PREFIX, ignoreCase = true)) {
                        if (isToday) notes += event
                        continue
                    }
                    when {
                        isBlockCalendar && !allDay -> when {
                            isToday -> blocks += event
                            firstDay == today.plusDays(1) -> tomorrowBlocks += event
                        }
                        // Everything else is a task: all-day events, and timed events of the task calendars.
                        isToday -> tasks += event
                        firstDay == today.plusDays(1) -> tomorrowTasks += event
                        !dayAfter.isAfter(today) && event.status == StudyStatus.Open -> overdue += event
                    }
                }
            }
        } catch (e: Exception) {
            DebugLog.error("Study", "reading the calendar failed: $e")
        }
        // Drop the phone's copies of events that changed in the calendar, and old ones.
        val oldest = System.currentTimeMillis() - (overdueDays + 2) * 24 * 60 * 60_000L
        if (local.keys.removeAll { it in stale || local[it]!!.savedAt < oldest }) saveLocal(local)
        return StudyDay(
            blocks = blocks.sortedBy { it.begin },
            tasks = tasks.sortedWith(compareBy({ it.status != StudyStatus.Open }, { it.begin }, { it.rawTitle })),
            overdue = overdue.sortedByDescending { it.begin },
            tomorrowBlocks = tomorrowBlocks.sortedBy { it.begin },
            tomorrowTasks = tomorrowTasks.sortedBy { it.rawTitle },
            waitingToSync = waitingToSync(blockCalendars + taskCalendars),
            classes = classes.sortedBy { it.begin },
            notes = notes,
        )
    }

    /** Study events changed on the phone that the sync has not sent yet (the calendar's "dirty" flag). */
    private fun waitingToSync(calendarIds: Set<Long>): Int {
        if (calendarIds.isEmpty()) return 0
        return try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events._ID),
                // A calendar of the phone alone ("local") never syncs, so its edits never "wait".
                CalendarContract.Events.DIRTY + " = 1 AND " + CalendarContract.Events.ACCOUNT_TYPE + " != '" + CalendarContract.ACCOUNT_TYPE_LOCAL + "' AND " +
                    CalendarContract.Events.CALENDAR_ID + " IN (" + calendarIds.joinToString(",") + ")",
                null, null,
            )?.use { it.count } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private var lastSyncRequest = 0L

    /**
     * Asks Android to sync the study calendars' accounts now, both ways (0.6.4): the phone's ticks
     * reach Google before the evening check-in reads them, and blocks moved or added elsewhere reach
     * the phone. On some phones (seen on MagicOS) the calendar otherwise syncs hours late. Any app
     * may ask; the sync itself is the system's. [force]: also within a few minutes of the last request.
     */
    fun requestSync(force: Boolean = false) {
        if (!enabled || !canRead) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSyncRequest < 5 * 60_000L) return
        lastSyncRequest = now
        scope.launch(Dispatchers.IO) {
            try {
                val blockWords = words(LauncherSettings.StudyBlockCalendars)
                val taskWords = words(LauncherSettings.StudyTaskCalendars)
                val accounts = HashSet<android.accounts.Account>()
                context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(0).orEmpty().lowercase()
                        val type = c.getString(2).orEmpty()
                        if (type == CalendarContract.ACCOUNT_TYPE_LOCAL || type.isEmpty()) continue
                        if ((blockWords + taskWords).any { it in name }) accounts += android.accounts.Account(c.getString(1).orEmpty(), type)
                    }
                }
                for (account in accounts) {
                    val extras = android.os.Bundle().apply {
                        putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true)
                        putBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                    }
                    android.content.ContentResolver.requestSync(account, CalendarContract.AUTHORITY, extras)
                }
                if (accounts.isNotEmpty()) DebugLog.event("Study", "calendar sync requested for ${accounts.size} account(s)")
            } catch (e: Exception) {
                DebugLog.error("Study", "could not request a calendar sync: $e")
            }
        }
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

    /** Adds [minutes] of focused time to the status line of the block [focus] ran on. */
    private suspend fun addFocus(focus: StudyFocus, blockEnd: Long, recurring: Boolean, minutes: Int): Boolean {
        val begin = focus.eventKey.substringAfter('@').toLongOrNull() ?: 0
        val event = _day.value.blocks.firstOrNull { it.key == focus.eventKey }
            // A block of an earlier day (the launcher was away when it ended): rebuilt from what was kept at the start.
            ?: StudyEvent(focus.eventId, 0, begin, blockEnd, false, "", recurring = recurring)
        return edit(event) { title, description ->
            val note = StudyFormat.note(description)
            title to StudyFormat.refreshed(description, note.copy(focusMinutes = note.focusMinutes + minutes))
        }
    }

    fun startFocus(event: StudyEvent) {
        val focus = StudyFocus(event.key, event.eventId, System.currentTimeMillis())
        _focus.value = focus
        prefs.edit().putString("focusKey", focus.eventKey).putLong("focusEvent", focus.eventId).putLong("focusStart", focus.startedAt)
            .putLong("focusEnd", event.end).putBoolean("focusRecurring", event.recurring).apply()
    }

    /** The end of the block the focus timer runs on (0 when unknown). */
    private fun focusEnd(focus: StudyFocus): Long =
        prefs.getLong("focusEnd", 0).takeIf { it > 0 } ?: _day.value.blocks.firstOrNull { it.key == focus.eventKey }?.end ?: 0

    /**
     * The focus timer runs to the end of its block (5.3): once the block is over, it stops by
     * itself and the minutes up to the block's end are written. Returns them, or null when the
     * timer is still running (or none is).
     */
    suspend fun finishFocusIfOver(now: Long = System.currentTimeMillis()): Int? {
        val focus = _focus.value ?: return null
        val end = focusEnd(focus)
        if (!StudyLocal.focusOver(end, now)) return null
        val minutes = stopFocus(until = end)
        Notices.post(NoticeKind.Study, "Focus ended with the block: $minutes min", if (minutes > 0) "Added to the block" else null)
        return minutes
    }

    /** Stops the focus timer and writes the minutes to its block. Returns the minutes focused. */
    suspend fun stopFocus(until: Long = System.currentTimeMillis()): Int {
        val running = _focus.value ?: return 0
        val end = focusEnd(running)
        val recurring = prefs.getBoolean("focusRecurring", false)
        // Taken atomically, so the minute tick and a refresh cannot both write the same minutes.
        val focus = _focus.getAndUpdate { if (it == running) null else it }.takeIf { it == running } ?: return 0
        prefs.edit().remove("focusKey").remove("focusEvent").remove("focusStart").remove("focusEnd").remove("focusRecurring").apply()
        val minutes = StudyLocal.focusMinutes(focus.startedAt, until, end)
        if (minutes > 0) addFocus(focus, end, recurring, minutes)
        return minutes
    }

    /**
     * Applies [change] to the event's current title and description (read fresh, so an edit
     * that arrived by sync a moment ago is not overwritten) and writes the result. One
     * occurrence of a repeating event becomes an exception of its series. When it may not be
     * written (no write permission, or writing back is off), the result is kept on the phone
     * ([StudyLocalEdit]); ticks kept that way are carried into the next write of the event.
     */
    private suspend fun edit(event: StudyEvent, change: (title: String, description: String) -> Pair<String, String>?): Boolean = withContext(Dispatchers.IO) {
        val write = settings.get(LauncherSettings.StudyWriteBack) && canWrite
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
            val local = localEdits()
            val kept = local[event.key]?.takeIf { StudyLocal.isCurrent(it, title, description) }
            val (newTitle, newDescription) = change(kept?.title ?: title, kept?.description ?: description) ?: return@withContext false
            if (newTitle == title && newDescription == description) {
                // The calendar already says this (for example an undo back to it): nothing to keep or write.
                if (local.remove(event.key) != null) {
                    saveLocal(local)
                    _day.value = load()
                }
                return@withContext true
            }
            if (!write) {
                local[event.key] = StudyLocalEdit(title, description, newTitle, newDescription, System.currentTimeMillis())
                saveLocal(local)
                DebugLog.event("Study", "calendar event ${event.eventId} kept on the phone (" + (if (canWrite) "writing back is off" else "no permission to write") + ")")
                _day.value = load()
                return@withContext true
            }
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
            if (ok) {
                if (local.remove(event.key) != null) saveLocal(local)
                _day.value = load()
                // Send it now rather than whenever the phone next syncs; ticks in a row are sent together.
                scheduleUpload()
            }
            ok
        } catch (e: Exception) {
            DebugLog.error("Study", "writing to the calendar failed: $e")
            false
        }
    }

    // ---- arrival, shift tonight, quick notes (0.9.1) ----

    private val _homeAt = MutableStateFlow(storedHome())

    /** When Med got home today (milliseconds), or null while that is not known. */
    val homeAt: StateFlow<Long?> = _homeAt

    private fun storedHome(): Long? =
        prefs.getLong("home:" + LocalDate.now(), 0L).takeIf { it > 0 }

    /** The name of the Wi-Fi network the phone is on, or null when it cannot be read (no location permission, location off, no Wi-Fi). */
    fun currentWifi(): String? = try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        @Suppress("DEPRECATION")
        val ssid = wifi.connectionInfo?.ssid?.trim('"')
        ssid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    } catch (e: Exception) {
        null
    }

    val canSeeWifi: Boolean get() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun charging(): Boolean = try {
        val battery = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        (battery?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    } catch (e: Exception) {
        false
    }

    /**
     * A sign that Med is home (A2), looked for only after today's last class: the home Wi-Fi ("wifi"),
     * or the charger plugged in at least 45 minutes after the last class ("charger"). Null when there is
     * none, on a day without classes, or once the arrival is known.
     */
    fun homeSign(now: Long = System.currentTimeMillis()): String? {
        if (!enabled || !settings.get(LauncherSettings.StudyArrival)) return null
        if (storedHome() != null) return null
        val lastClass = _day.value.lastClassEnd ?: return null
        if (now < lastClass) return null
        val home = settings.get(LauncherSettings.StudyHomeWifi).trim()
        if (home.isNotEmpty() && canSeeWifi && currentWifi().equals(home, ignoreCase = true)) return "wifi"
        if (settings.get(LauncherSettings.StudyArrivalCharger) && now >= lastClass + 45 * 60_000L && charging()) return "charger"
        return null
    }

    /**
     * Records the arrival ([how]: "wifi", "charger" or "manual") and writes `— launcher: home HH:MM` after the
     * `P2-BLOCK` line of tonight's first block (the first one not over yet). Returns the time written.
     */
    suspend fun markHome(at: Long = System.currentTimeMillis(), how: String): String {
        val text = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        prefs.edit().putLong("home:" + LocalDate.now(), at).apply()
        _homeAt.value = at
        DebugLog.event("Study", "home at $text ($how)")
        val first = _day.value.blocks.firstOrNull { it.end > at }
        if (first != null) edit(first) { title, description -> title to StudyFormat.withMark(description, StudyFormat.MARK_HOME, text) }
        return text
    }

    /** True once "shift tonight" was answered today (shifted, or kept as planned): it is not offered again. */
    fun shiftAnswered(): Boolean = prefs.getBoolean("shift:" + LocalDate.now(), false)

    fun answerShift() {
        prefs.edit().putBoolean("shift:" + LocalDate.now(), true).apply()
        _shiftTick.value++
    }

    private val _shiftTick = MutableStateFlow(0)

    /** Changes when "shift tonight" is answered, so the hub can hide its chip. */
    val shiftTick: StateFlow<Int> = _shiftTick

    /** Forgets today's arrival (the manual chip was a mistake). The mark already written stays. */
    fun forgetHome() {
        prefs.edit().remove("home:" + LocalDate.now()).apply()
        _homeAt.value = null
    }

    /** The latest time an evening may end today (Settings → Study companion), in milliseconds. */
    fun latestEnd(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() +
        (settings.get(LauncherSettings.StudyLatestEnd).toIntOrNull() ?: 1380) * 60_000L

    /**
     * Moves the blocks of [plan] (A3): new start and end, lengths kept (or cut for the shortened one), and
     * `— launcher: shifted +N min` after each block's `P2-BLOCK` line. Title marks and task lines are not
     * touched. Needs write access: a shift cannot be kept on the phone alone. Returns how many blocks moved.
     */
    suspend fun applyShift(plan: ShiftPlan): Int = withContext(Dispatchers.IO) {
        if (!(settings.get(LauncherSettings.StudyWriteBack) && canWrite)) return@withContext 0
        val resolver = context.contentResolver
        var moved = 0
        for (move in plan.moves) {
            try {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, move.event.eventId)
                var description = move.event.description
                resolver.query(uri, arrayOf(CalendarContract.Events.DESCRIPTION), null, null, null)?.use { c ->
                    if (c.moveToFirst()) description = c.getString(0).orEmpty()
                }
                val total = StudyFormat.shiftedBy(description) + plan.deltaMinutes
                val text = (if (total >= 0) "+$total" else "$total") + " min" +
                    if (move.shortenedMinutes > 0) " · shortened ${move.shortenedMinutes} min" else ""
                val values = ContentValues().apply {
                    put(CalendarContract.Events.DTSTART, move.begin)
                    put(CalendarContract.Events.DESCRIPTION, StudyFormat.withMark(description, StudyFormat.MARK_SHIFTED, text))
                }
                val ok = if (move.event.recurring) {
                    // One evening of a repeating block: an exception of the series, which keeps the series' length.
                    values.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, move.event.begin)
                    resolver.insert(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, move.event.eventId), values) != null
                } else {
                    values.put(CalendarContract.Events.DTEND, move.end)
                    resolver.update(uri, values, null, null) > 0
                }
                if (ok) moved++
                DebugLog.event("Study", "block ${move.event.eventId} " + if (ok) "shifted $text" else "could not be shifted")
            } catch (e: Exception) {
                DebugLog.error("Study", "shifting block ${move.event.eventId} failed: $e")
            }
        }
        if (moved > 0) {
            _day.value = load()
            scheduleUpload()
        }
        moved
    }

    /**
     * Quick capture (D5): an all-day event "Note: [text]" today in the first writable task calendar,
     * where the evening check-in reads it. Returns false when there is no such calendar or no write access.
     */
    suspend fun addNote(text: String): Boolean = withContext(Dispatchers.IO) {
        val note = text.trim()
        if (note.isEmpty() || !canWrite) return@withContext false
        try {
            val taskWords = words(LauncherSettings.StudyTaskCalendars)
            val calendar = calendars().firstOrNull { cal -> cal.writable && taskWords.any { it in cal.name.lowercase() } } ?: return@withContext false
            val start = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendar.id)
                put(CalendarContract.Events.TITLE, StudyFormat.NOTE_PREFIX + " " + note)
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, start + 24 * 60 * 60_000L)
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            }
            val ok = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
            DebugLog.event("Study", if (ok) "note added to ${calendar.name}" else "note could not be added")
            if (ok) {
                _day.value = load()
                scheduleUpload()
            }
            ok
        } catch (e: Exception) {
            DebugLog.error("Study", "adding a note failed: $e")
            false
        }
    }

    private var uploadJob: Job? = null

    /** Requests a sync a few seconds after the last write, so a run of ticks goes up in one sync. */
    private fun scheduleUpload() {
        uploadJob?.cancel()
        uploadJob = scope.launch {
            delay(4_000)
            requestSync(force = true)
        }
    }

    // ---- ticks kept on the phone ----

    private fun localEdits(): MutableMap<String, StudyLocalEdit> = StudyLocal.decode(prefs.getString("local", null))

    private fun saveLocal(edits: Map<String, StudyLocalEdit>) {
        prefs.edit().putString("local", StudyLocal.encode(edits)).apply()
    }
}
