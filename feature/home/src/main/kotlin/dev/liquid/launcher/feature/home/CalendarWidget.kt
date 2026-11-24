package dev.liquid.launcher.feature.home

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.liquid.launcher.core.data.HomeItem
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.derive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** One event of the calendar widget. */
internal data class CalEvent(val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean, val color: Int)

/** A month of the calendar widget: the colours of the events on each day, and the next events from now. */
internal data class CalMonth(val month: YearMonth, val days: Map<LocalDate, List<Int>>, val next: List<CalEvent>, val allowed: Boolean)

private fun canRead(context: Context) = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

/**
 * Reads [month] and the next two weeks from the phone's visible calendars. 0.16.0: without the calendars [hidden] (and
 * in the [colours] chosen) when the widget shares the calendar's settings; both empty otherwise, as before.
 */
private fun readMonth(context: Context, month: YearMonth, now: Long, hidden: Set<Long> = emptySet(), colours: Map<Long, Int> = emptyMap()): CalMonth {
    if (!canRead(context)) return CalMonth(month, emptyMap(), emptyList(), allowed = false)
    val zone = ZoneId.systemDefault()
    val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val from = minOf(start, now)
    val to = maxOf(end, now + 14L * 86_400_000L)
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, from)
        ContentUris.appendId(it, to)
    }.build()
    val days = HashMap<LocalDate, MutableList<Int>>()
    val next = ArrayList<CalEvent>()
    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
        CalendarContract.Instances.CALENDAR_ID,
    )
    runCatching {
        context.contentResolver.query(uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val calendar = c.getLong(6)
                if (calendar in hidden) continue
                val allDay = c.getInt(4) == 1
                val event = CalEvent(c.getLong(0), c.getString(1).orEmpty().ifBlank { "(No title)" }, c.getLong(2), c.getLong(3), allDay, colours[calendar] ?: c.getInt(5))
                // All-day events are stored at UTC midnight.
                val dayZone = if (allDay) ZoneOffset.UTC else zone
                var day = Instant.ofEpochMilli(event.begin).atZone(dayZone).toLocalDate()
                val last = Instant.ofEpochMilli((event.end - 1).coerceAtLeast(event.begin)).atZone(dayZone).toLocalDate()
                var guard = 0
                while (!day.isAfter(last) && guard++ < 42) {
                    if (YearMonth.from(day) == month) days.getOrPut(day) { ArrayList() }.let { if (it.size < 3) it.add(event.color) }
                    day = day.plusDays(1)
                }
                if (event.end > now && next.size < 4) next.add(event)
            }
        }
    }
    return CalMonth(month, days, next, allowed = true)
}

/**
 * The calendar on liquid glass (0.8.1, D117): the month with today lit and a dot for each event,
 * swipe-free month arrows, and the next events below when there is room. On a short widget, today
 * and the next events only. Fits on a page or on the search screen. 0.16.0 (D211): its month is the
 * calendar's own component ([MonthDays]).
 */
@Composable
internal fun CalendarWidget(item: HomeItem, env: PageEnv, controller: HomeController) {
    val context = LocalContext.current
    val tick = rememberMinuteTick()
    val today = remember(tick) { LocalDate.now() }
    var offset by remember(item.id) { mutableIntStateOf(0) }
    var allowedTick by remember { mutableIntStateOf(0) }
    val month = YearMonth.from(today).plusMonths(offset.toLong())
    // 0.16.0 (D211): the month is the calendar's component, drawn with the widget's own look, or the calendar's when it
    // shares it (Settings › Calendar › Calendar widget). A tap on a day still opens the calendar app.
    val store = controller.data.settings
    val calLook by store.derive { calendarLook(widget = true) }
    val notesKey by store.derive { get(dev.liquid.launcher.core.settings.LauncherSettings.CalendarSchoolYear) + "|" + get(dev.liquid.launcher.core.settings.LauncherSettings.CalendarHolidayChanges) }
    val notes = remember(calLook, notesKey, month) { dayNotes(store, calLook, month.atDay(1), month.atEndOfMonth()) }
    // Read again every quarter of an hour, when the month changes or once the calendar may be read.
    val data by produceState<CalMonth?>(null, month, tick / 900_000, allowedTick, calLook.hidden, calLook.colours) {
        value = withContext(Dispatchers.IO) { readMonth(context, month, System.currentTimeMillis(), calLook.hidden, calLook.colours) }
    }
    // P7 (0.9.23): this widget's own options (its Customise sheet in edit mode): card, corners, colour, parts.
    val look = WidgetLook.of(item)
    val shape = RoundedCornerShape(
        when (item.options?.get("corner")) {
            "s" -> 18.dp
            "l" -> 36.dp
            else -> 28.dp
        }
    )
    val style = env.glass.style(Surfaces.Clock, env.lightBackground)
    // No box by default (P7, 0.9.10): on the wallpaper in the clock's colour; "Liquid glass" puts it back on a card.
    val boxedForAll by controller.data.settings.derive { get(dev.liquid.launcher.core.settings.LauncherSettings.WidgetBackground) == "glass" }
    val boxed = when (item.options?.get("card")) {
        "glass" -> true
        "none" -> false
        else -> boxedForAll
    }
    val color = look.color(
        when {
            !boxed -> env.contentColor
            env.glass.lightGlass(Surfaces.Clock, env.lightBackground) -> Color(0xFF16181C)
            else -> Color.White
        }
    )
    val live = env.interactive && !env.editMode
    Box(
        Modifier
            .fillMaxSize()
            .padding(4.dp)
            .then(if (boxed) Modifier.liquidGlass(env.wallpaperBackdrop, shape, style) else Modifier)
            .clip(shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        val shown = data
        if (shown != null && !shown.allowed) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())), style = text(color, 17f, FontWeight.SemiBold))
                Spacer(Modifier.height(10.dp))
                GlassButton("Show my events", color, icon = LauncherIcons.Calendar, enabled = live) {
                    controller.askCalendar { allowedTick++ }
                }
            }
            return@Box
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val roomy = maxHeight > 300.dp && look.shows("agenda", true)
            if (maxHeight < 190.dp || !look.shows("grid", true)) {
                CompactCalendar(today, shown, color, live, controller)
            } else {
                Column(Modifier.fillMaxSize()) {
                    MonthHeader(month, offset != 0, color, live, onMove = { offset += it }, onToday = { offset = 0 })
                    Spacer(Modifier.height(6.dp))
                    AnimatedContent(
                        targetState = month,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(spring(dampingRatio = 0.82f, stiffness = MotionScale.stiffness(420f))) { it / 3 * dir } + fadeIn()) togetherWith
                                (slideOutHorizontally(spring(dampingRatio = 1f, stiffness = MotionScale.stiffness(520f))) { -it / 3 * dir } + fadeOut())
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        label = "month",
                    ) { m ->
                        MonthDays(
                            m, today, calLook, shown?.takeIf { it.month == m }?.days.orEmpty(), if (m == month) notes else emptyMap(), color,
                            selected = null, onDay = if (live) { day -> controller.start(dayIntent(day)) } else null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (roomy) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = 0.14f)))
                        Spacer(Modifier.height(6.dp))
                        NextEvents(shown?.next.orEmpty(), 2, color, live, controller)
                    }
                }
            }
        }
    }
}

private fun text(color: Color, size: Float, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = size.sp, fontWeight = weight)

@Composable
private fun MonthHeader(month: YearMonth, away: Boolean, color: Color, live: Boolean, onMove: (Int) -> Unit, onToday: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            month.month.getDisplayName(DayStyle.FULL, Locale.getDefault()).replaceFirstChar { it.uppercase() } + if (month.year != YearMonth.now().year) " ${month.year}" else "",
            style = text(color, 18f, FontWeight.SemiBold),
            modifier = Modifier.weight(1f).then(if (live && away) Modifier.squishClickable(onClick = onToday) else Modifier),
            maxLines = 1,
        )
        if (away) {
            Text("Today", style = text(HomeTheme.accent, 13f, FontWeight.Medium), modifier = Modifier.then(if (live) Modifier.squishClickable(onClick = onToday) else Modifier).padding(horizontal = 8.dp))
        }
        Arrow(color, live, back = true) { onMove(-1) }
        Spacer(Modifier.width(4.dp))
        Arrow(color, live, back = false) { onMove(1) }
    }
}

@Composable
private fun Arrow(color: Color, live: Boolean, back: Boolean, onClick: () -> Unit) {
    // W3: the theme's round button (turned round for "previous").
    if (currentTheme != null) {
        GlassButton(
            null, color, Modifier.size(30.dp).graphicsLayer { rotationZ = if (back) 180f else 0f },
            icon = LauncherIcons.ChevronRight, contentDescription = if (back) "Previous month" else "Next month", enabled = live,
        ) { if (live) onClick() }
        return
    }
    Box(
        Modifier
            .size(30.dp)
            .then(if (live) Modifier.squishClickable(onClick = onClick) else Modifier)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.1f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        LauncherIcon(
            LauncherIcons.ChevronRight, contentDescription = if (back) "Previous month" else "Next month", tint = color, size = 18.dp,
            modifier = Modifier.graphicsLayer { rotationZ = if (back) 180f else 0f },
        )
    }
}

@Composable
private fun CompactCalendar(today: LocalDate, data: CalMonth?, color: Color, live: Boolean, controller: HomeController) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.width(64.dp).then(if (live) Modifier.squishClickable { controller.start(dayIntent(today)) } else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(today.dayOfWeek.getDisplayName(DayStyle.SHORT, Locale.getDefault()).uppercase(), style = text(HomeTheme.accent, 12f, FontWeight.SemiBold))
            Text(today.dayOfMonth.toString(), style = text(color, 30f, FontWeight.Light))
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(1.dp).fillMaxSize(0.7f).background(color.copy(alpha = 0.14f)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            NextEvents(data?.next.orEmpty(), 2, color, live, controller)
        }
    }
}

private val eventTime = DateTimeFormatter.ofPattern("H:mm", Locale.getDefault())

@Composable
private fun NextEvents(events: List<CalEvent>, max: Int, color: Color, live: Boolean, controller: HomeController) {
    if (events.isEmpty()) {
        Text("Nothing coming up", style = text(color.copy(alpha = 0.6f), 13f))
        return
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (e in events.take(max)) {
            val day = Instant.ofEpochMilli(e.begin).atZone(if (e.allDay) ZoneOffset.UTC else zone).toLocalDate()
            val now = System.currentTimeMillis()
            val running = !e.allDay && e.begin <= now && e.end > now
            val whenText = when {
                running -> "Now"
                e.allDay && day == today -> "Today"
                e.allDay -> day.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()))
                day == today -> Instant.ofEpochMilli(e.begin).atZone(zone).format(eventTime)
                day == today.plusDays(1) -> "Tomorrow " + Instant.ofEpochMilli(e.begin).atZone(zone).format(eventTime)
                else -> Instant.ofEpochMilli(e.begin).atZone(zone).format(DateTimeFormatter.ofPattern("EEE H:mm", Locale.getDefault()))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (live) Modifier.squishClickable { controller.start(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))) } else Modifier)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color).copy(alpha = 1f)))
                Spacer(Modifier.width(8.dp))
                Text(e.title, style = text(color, 14f, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Text(whenText, style = text(if (running) HomeTheme.accent else color.copy(alpha = 0.6f), 12f, if (running) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1)
            }
        }
    }
}

/** Opens the phone's calendar at [date]. */
private fun dayIntent(date: LocalDate): Intent {
    val millis = date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, millis) }.build()
    return Intent(Intent.ACTION_VIEW, uri)
}
