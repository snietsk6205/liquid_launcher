package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.liquid.launcher.core.data.Holiday
import dev.liquid.launcher.core.data.PhoneCalendars
import dev.liquid.launcher.core.data.SchoolDate
import dev.liquid.launcher.core.data.SchoolYear
import dev.liquid.launcher.core.data.TunisiaHolidays
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DayStyle
import java.time.temporal.IsoFields
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * 0.16.0 (D209–D211): what the calendar widget and the calendar that grows out of the clock's date share: how a month is
 * drawn (one component, [MonthDays]), the look it is drawn with ([CalendarLook]: the calendar's settings, or the
 * widget's own unless it shares them), the holidays and school dates of each day ([DayNotes]) and the liquid blob that
 * marks the day picked.
 */

/** How a month is drawn: the calendar's settings, or the widget's own ([calendarLook]). */
internal data class CalendarLook(
    val firstDay: DayOfWeek,
    val weekNumbers: Boolean,
    /** "dots" or "bars". */
    val marks: String,
    /** "filled", "ring", "text" or "glow". */
    val today: String,
    /** "compact", "normal" or "roomy". */
    val density: String,
    val holidays: Boolean,
    val school: Boolean,
    val hijri: Boolean,
    val hijriShift: Int,
    /** Calendars left out, and calendars in a colour of their own (empty: as the phone has them). */
    val hidden: Set<Long>,
    val colours: Map<Long, Int>,
) {
    /** The weekday names' order. */
    fun weekDays(): List<DayOfWeek> = (0 until 7).map { firstDay.plus(it.toLong()) }

    /** Columns before the 1st of [month]. */
    fun lead(month: YearMonth): Int = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7

    fun weeks(month: YearMonth): Int = (lead(month) + month.lengthOfMonth() + 6) / 7

    /** The first day of the week [day] is in. */
    fun weekOf(day: LocalDate): LocalDate = day.minusDays(((day.dayOfWeek.value - firstDay.value + 7) % 7).toLong())
}

/**
 * The look of the calendar ([widget] false) or of the Calendar widget: the calendar's own settings when it shares them,
 * else the widget's (whose defaults draw it as before 0.16.0). The week's first day is the calendar's for both (Med).
 */
internal fun SettingsStore.calendarLook(widget: Boolean): CalendarLook {
    val first = if (get(LauncherSettings.CalendarMondayFirst)) DayOfWeek.MONDAY else WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val shift = get(LauncherSettings.CalendarHijriShift)
    if (widget && !get(LauncherSettings.CalendarShareWidget)) {
        return CalendarLook(
            first, get(LauncherSettings.CalendarWidgetWeekNumbers), get(LauncherSettings.CalendarWidgetMarks), get(LauncherSettings.CalendarWidgetToday),
            "normal", get(LauncherSettings.CalendarWidgetHolidays), get(LauncherSettings.CalendarWidgetSchool), false, shift, emptySet(), emptyMap(),
        )
    }
    return CalendarLook(
        first, get(LauncherSettings.CalendarWeekNumbers), get(LauncherSettings.CalendarMarks), get(LauncherSettings.CalendarToday),
        if (widget) "normal" else get(LauncherSettings.CalendarDensity),
        get(LauncherSettings.CalendarHolidays), get(LauncherSettings.CalendarSchool), get(LauncherSettings.CalendarHijri), shift,
        PhoneCalendars.parseHidden(get(LauncherSettings.CalendarHidden)), PhoneCalendars.parseColours(get(LauncherSettings.CalendarColours)),
    )
}

/** The holidays and school dates of one day. */
internal class DayNotes(val holidays: List<Holiday>, val school: List<SchoolDate>) {
    val isEmpty: Boolean get() = holidays.isEmpty() && school.isEmpty()
}

/** The holidays and school dates from [from] to [to] (both included), as [look] shows them. */
internal fun dayNotes(store: SettingsStore, look: CalendarLook, from: LocalDate, to: LocalDate): Map<LocalDate, DayNotes> {
    if (!look.holidays && !look.school) return emptyMap()
    val holidays = if (look.holidays) {
        TunisiaHolidays.between(from, to, look.hijriShift, TunisiaHolidays.parseChanges(store.get(LauncherSettings.CalendarHolidayChanges))).groupBy { it.date }
    } else emptyMap()
    val school = HashMap<LocalDate, MutableList<SchoolDate>>()
    if (look.school) {
        for (d in SchoolYear.sorted(SchoolYear.parse(store.get(LauncherSettings.CalendarSchoolYear)))) {
            val s = d.startDate ?: continue
            val e = d.endDate ?: s
            var day = if (s.isBefore(from)) from else s
            var guard = 0
            while (!day.isAfter(e) && !day.isAfter(to) && guard++ < 370) {
                school.getOrPut(day) { ArrayList() }.add(d)
                day = day.plusDays(1)
            }
        }
    }
    return (holidays.keys + school.keys).associateWith { DayNotes(holidays[it].orEmpty(), school[it].orEmpty()) }
}

/** A holiday's colour on the days (warm, readable on light and dark glass). */
internal val HolidayInk = Color(0xFFFFB74D)

/** A school date's mark: exams and retakes red, breaks green, the rest violet. */
internal fun schoolInk(d: SchoolDate): Color = when (d.kind) {
    "exam", "retake" -> Color(0xFFEF5350)
    "break" -> Color(0xFF66BB6A)
    else -> Color(0xFFAB8CFF)
}

private fun dayText(color: Color, size: Float, weight: FontWeight) = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = size.sp, fontWeight = weight)

/**
 * A month (0.16.0, D211): the weekday initials, optional week numbers, and each day with today's highlight, its events'
 * dots or colour bars, its holiday colour and school mark. [selected] wears the liquid blob, which flows from day to
 * day; [onDay] answers a tap. [cellHeight] null: the weeks share the height given. Used by the widget and the calendar.
 */
@Composable
internal fun MonthDays(
    month: YearMonth,
    today: LocalDate,
    look: CalendarLook,
    colours: Map<LocalDate, List<Int>>,
    notes: Map<LocalDate, DayNotes>,
    color: Color,
    selected: LocalDate?,
    onDay: ((LocalDate) -> Unit)?,
    modifier: Modifier = Modifier,
    cellHeight: Dp? = null,
) {
    val lead = look.lead(month)
    val weeks = look.weeks(month)
    val accent = HomeTheme.accent
    val numberColumn = if (look.weekNumbers) 22.dp else 0.dp
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            if (look.weekNumbers) Box(Modifier.width(numberColumn))
            for (d in look.weekDays()) {
                Text(
                    d.getDisplayName(DayStyle.NARROW, Locale.getDefault()),
                    style = dayText(color.copy(alpha = 0.55f), 11f, FontWeight.Medium),
                    modifier = Modifier.weight(1f).padding(bottom = 2.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().then(if (cellHeight == null) Modifier.weight(1f) else Modifier.height(cellHeight * weeks))) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val columnPx = with(androidx.compose.ui.platform.LocalDensity.current) { numberColumn.toPx() }
            val cellW = (widthPx - columnPx) / 7f
            val cellH = heightPx / weeks
            // The blob sits behind the day picked (only when it is in this month).
            val index = selected?.takeIf { YearMonth.from(it) == month }?.let { lead + it.dayOfMonth - 1 }
            val target = index?.let { Offset(columnPx + cellW * (it % 7 + 0.5f), cellH * (it / 7 + 0.5f)) }
            val blob = rememberBlob(target)
            Column(
                Modifier
                    .fillMaxSize()
                    .drawBehind { blob.draw(this, minOf(cellW, cellH) * 0.44f, accent) },
            ) {
                for (w in 0 until weeks) {
                    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (look.weekNumbers) {
                            val firstOfRow = month.atDay(1).plusDays((w * 7 - lead).toLong())
                            Text(
                                firstOfRow.plusDays(3).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString(),
                                style = dayText(color.copy(alpha = 0.4f), 10f, FontWeight.Medium),
                                modifier = Modifier.width(numberColumn),
                                textAlign = TextAlign.Center,
                            )
                        }
                        for (d in 0 until 7) {
                            val day = w * 7 + d - lead + 1
                            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (day in 1..month.lengthOfMonth()) {
                                    val date = month.atDay(day)
                                    DayCell(date, date == today, look, colours[date].orEmpty(), notes[date], color, accent, onDay)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One day: today's highlight (as [CalendarLook.today] says), its number, its marks. */
@Composable
internal fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    look: CalendarLook,
    dots: List<Int>,
    notes: DayNotes?,
    color: Color,
    accent: Color,
    onDay: ((LocalDate) -> Unit)?,
    compact: Boolean = false,
) {
    // Today swells in like a drop when the month appears or the day changes (as the widget has since D117).
    val lit by animateFloatAsState(if (isToday) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = MotionScale.stiffness(300f)), label = "today")
    val past = date.isBefore(LocalDate.now())
    val holiday = notes?.holidays?.firstOrNull()
    val school = notes?.school?.firstOrNull()
    val filled = look.today == "filled"
    Box(
        Modifier
            .fillMaxSize()
            .then(if (onDay != null) Modifier.squishClickable { onDay(date) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (lit > 0.001f && look.today != "text") {
            Box(
                Modifier
                    .fillMaxSize(if (compact) 0.9f else 0.82f)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = lit
                        scaleY = lit
                        alpha = lit.coerceIn(0f, 1f)
                    }
                    .then(
                        when (look.today) {
                            "ring" -> Modifier.border(1.6.dp, accent.copy(alpha = 0.95f), CircleShape)
                            "glow" -> Modifier.background(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0f))), CircleShape)
                            else -> Modifier.background(accent.copy(alpha = 0.88f), CircleShape)
                        },
                    ),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // A school date: a short cap over the number, in the date's colour (no room taken while school dates are off).
            if (look.school) {
                Box(
                    Modifier
                        .width(12.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (school != null) schoolInk(school) else Color.Transparent),
                )
            }
            val ink = when {
                isToday && filled -> readableOn(accent)
                isToday && look.today != "glow" -> accent
                holiday != null -> HolidayInk
                else -> color.copy(alpha = if (past) 0.5f else 0.95f)
            }
            Text(
                date.dayOfMonth.toString(),
                style = dayText(ink, if (compact) 12f else 13f, if (isToday || holiday != null) FontWeight.Bold else FontWeight.Normal),
            )
            Marks(dots, look.marks, if (isToday && filled) readableOn(accent) else null)
        }
    }
}

/** A day's events: up to three dots, or a bar split in their colours. [over] replaces their colours (on today's fill). */
@Composable
internal fun Marks(colours: List<Int>, style: String, over: Color?) {
    if (style == "bars") {
        Row(Modifier.height(5.dp).width(18.dp), verticalAlignment = Alignment.CenterVertically) {
            for ((i, c) in colours.take(3).withIndex()) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .padding(start = if (i > 0) 1.dp else 0.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(over ?: Color(c).copy(alpha = 1f)),
                )
            }
        }
        return
    }
    Row(Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        for (c in colours.take(3)) {
            Box(Modifier.size(4.dp).background(over ?: Color(c).copy(alpha = 1f), CircleShape))
        }
    }
}

// ---- the liquid blob (D211) ----

/**
 * The day picked wears a drop of the accent colour. When another day is tapped it flows there: its head springs ahead,
 * its tail follows on a softer spring, and the two are joined by a waist, so it stretches like a drop and settles round.
 * Interruptible: a new tap takes it from wherever it is, at its speed.
 */
@Stable
internal class Blob {
    val head = Animatable(Offset.Zero, Offset.VectorConverter)
    val tail = Animatable(Offset.Zero, Offset.VectorConverter)

    /** It has been put on a day once (before that it does not fly in from the corner). */
    var placed by androidx.compose.runtime.mutableStateOf(false)

    /** Its opacity (read while drawing, so a fade redraws without recomposing the month). */
    var visible: androidx.compose.runtime.State<Float>? = null

    fun draw(scope: DrawScope, radius: Float, accent: Color) {
        val v = visible?.value ?: 1f
        if (!placed || v <= 0.01f || radius <= 1f) return
        val path = blobPath(head.value, tail.value, radius)
        scope.drawPath(path, accent.copy(alpha = 0.26f * v))
        scope.drawPath(path, accent.copy(alpha = 0.6f * v), style = Stroke(width = 1.2f * scope.density))
    }
}

/** The drop's outline: the head, the tail (smaller as it stretches) and the waist between them, as one shape. */
internal fun blobPath(head: Offset, tail: Offset, radius: Float): Path {
    val d = (tail - head).getDistance()
    val r1 = radius
    val r2 = radius * (1f - (d / (radius * 5f)).coerceIn(0f, 0.45f))
    val headPath = Path().apply { addOval(androidx.compose.ui.geometry.Rect(head, r1)) }
    if (d < 0.5f || d <= kotlin.math.abs(r1 - r2) + 0.5f) return headPath
    val tailPath = Path().apply { addOval(androidx.compose.ui.geometry.Rect(tail, r2)) }
    val theta = atan2(tail.y - head.y, tail.x - head.x)
    val alpha = acos(((r1 - r2) / d).coerceIn(-1f, 1f))
    fun on(c: Offset, r: Float, a: Float) = Offset(c.x + r * cos(a), c.y + r * sin(a))
    val p1 = on(head, r1, theta + alpha)
    val p2 = on(head, r1, theta - alpha)
    val q1 = on(tail, r2, theta + alpha)
    val q2 = on(tail, r2, theta - alpha)
    val middle = (head + tail) / 2f
    // The waist: each side pulled towards the middle line, more as the drop stretches.
    val pinch = (d / (radius * 6f)).coerceIn(0f, 0.3f)
    fun waist(a: Offset, b: Offset): Offset {
        val m = (a + b) / 2f
        return m + (middle - m) * pinch
    }
    val bridge = Path().apply {
        moveTo(p1.x, p1.y)
        val c1 = waist(p1, q1)
        quadraticTo(c1.x, c1.y, q1.x, q1.y)
        lineTo(q2.x, q2.y)
        val c2 = waist(q2, p2)
        quadraticTo(c2.x, c2.y, p2.x, p2.y)
        close()
    }
    val joined = Path()
    joined.op(headPath, bridge, PathOperation.Union)
    val all = Path()
    all.op(joined, tailPath, PathOperation.Union)
    return all
}

/** A blob that flows to [target] (null: it fades where it is). */
@Composable
internal fun rememberBlob(target: Offset?): Blob {
    val blob = remember { Blob() }
    blob.visible = animateFloatAsState(if (target != null) 1f else 0f, spring(dampingRatio = 1f, stiffness = MotionScale.stiffness(500f)), label = "blobShown")
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        if (!blob.placed) {
            blob.head.snapTo(target)
            blob.tail.snapTo(target)
            blob.placed = true
            return@LaunchedEffect
        }
        // Its frames are counted apart by the frame meter (only where nothing else is being measured).
        val measured = FrameCost.activity == null || FrameCost.activity == CalendarBlobFlowing
        if (measured) FrameCost.activity = CalendarBlobFlowing
        try {
            kotlinx.coroutines.coroutineScope {
                launch { blob.head.animateTo(target, spring(dampingRatio = 0.66f, stiffness = MotionScale.stiffness(520f))) }
                launch { blob.tail.animateTo(target, spring(dampingRatio = 0.85f, stiffness = MotionScale.stiffness(190f))) }
            }
        } finally {
            if (measured && FrameCost.activity == CalendarBlobFlowing) FrameCost.activity = null
        }
    }
    return blob
}
