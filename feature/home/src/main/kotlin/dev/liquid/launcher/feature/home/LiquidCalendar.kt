package dev.liquid.launcher.feature.home

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.common.Notice
import dev.liquid.launcher.core.common.NoticeKind
import dev.liquid.launcher.core.common.Notices
import dev.liquid.launcher.core.data.Hijri
import dev.liquid.launcher.core.data.Holiday
import dev.liquid.launcher.core.data.PhoneCalendars
import dev.liquid.launcher.core.data.PhoneEvent
import dev.liquid.launcher.core.data.SchoolDate
import dev.liquid.launcher.core.data.SchoolYear
import dev.liquid.launcher.core.data.TunisiaHolidays
import dev.liquid.launcher.core.designsystem.GlassQuality
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.derive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayStyle
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.roundToInt

/*
 * 0.16.0 (D209–D212): the liquid calendar. A tap on the clock's date and one glass surface grows out of the date text
 * (its bounds, on a spring that can be taken at any moment), width first and then height, the date's capsule turning
 * into the sheet's corners. Its content is laid out once at the open size, anchored at the top, so the month comes out of
 * the date first. Pulling it down anywhere (or past the top of a list) shrinks it back into the date under the finger;
 * Back does the same. Four views: the month, a week strip, the agenda and the year as a heatmap; the day picked wears a
 * liquid blob that flows from day to day. Events come from the phone's calendars (read only, asked the first time it
 * opens); Quick add opens the calendar app's own screen. Holidays in Tunisia and the school year (built in, indicative,
 * editable) are on their days; a school date can be pinned under the clock.
 */

/** What opened the calendar (D209). Today only the clock's date; the pill, a gesture or a widget can be added later. */
sealed interface CalendarOpener {
    /** What it grows from and shrinks back into (root px); null: the top of the screen. */
    val origin: Rect?

    /** For the debug log. */
    val name: String

    data class ClockDate(val itemId: String, override val origin: Rect?) : CalendarOpener {
        override val name: String get() = "clock's date"
    }
}

/** The calendar's views, in the order of its tabs. */
internal val CalendarViews = listOf("month" to "Month", "week" to "Week", "agenda" to "Agenda", "year" to "Year")

/** What the calendar shows: its view, the day picked, the month and year shown. Kept while it is closed. */
@Stable
class CalendarUi {
    var view by mutableStateOf("month")
    var selected by mutableStateOf(LocalDate.now())
    var month by mutableStateOf(YearMonth.now())
    var year by mutableIntStateOf(LocalDate.now().year)

    /** Bumped when the calendars may (or may not) be read now. */
    var allowedTick by mutableIntStateOf(0)

    /** The school date or holiday (by id) whose actions are open in the day's agenda. */
    var acting by mutableStateOf<String?>(null)

    fun reset(view: String) {
        val today = LocalDate.now()
        this.view = view.takeIf { v -> CalendarViews.any { it.first == v } } ?: "month"
        select(today)
    }

    fun select(day: LocalDate) {
        selected = day
        month = YearMonth.from(day)
        year = day.year
        acting = null
    }

    /** The month [by] months away; the day picked becomes today in this month, else the 1st. */
    fun shiftMonth(by: Long) {
        val next = month.plusMonths(by)
        val today = LocalDate.now()
        select(if (YearMonth.from(today) == next) today else next.atDay(1))
    }
}

private fun calLerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** The calendar's rectangles, from the screen, the insets, its size and its progress. */
@Stable
private class CalendarGeometry(
    private val controller: HomeController,
    private val density: Density,
    private val nav: WindowInsets,
    private val status: WindowInsets,
    private val size: String,
    private val cornerDp: Int,
) {
    private fun px(dp: Float) = dp * density.density

    /** The open sheet: compact under the date, the lower part of the screen, or all of it. */
    fun target(): Rect {
        val w = controller.screenWidth
        val h = controller.screenHeight
        val margin = px(10f)
        val top = status.getTop(density) + margin
        val bottom = h - nav.getBottom(density) - margin
        return when (size) {
            "full" -> Rect(margin, top, w - margin, bottom)
            "compact" -> {
                val height = minOf(h * 0.5f, px(460f)).coerceAtMost(bottom - top)
                val wanted = origin().bottom + px(8f)
                val y = wanted.coerceIn(top, bottom - height)
                Rect(margin, y, w - margin, y + height)
            }
            else -> {
                val height = (h * 0.62f).coerceAtMost(bottom - top)
                Rect(margin, bottom - height, w - margin, bottom)
            }
        }
    }

    /** What it grows from: the date it was opened from, else a capsule near the top. */
    fun origin(): Rect {
        controller.calendarOpener?.origin?.takeIf { it.width > 1f && it.height > 1f }?.let { return it }
        val w = controller.screenWidth
        val top = status.getTop(density) + px(48f)
        return Rect(w / 2f - px(70f), top, w / 2f + px(70f), top + px(28f))
    }

    /** The surface at progress [p]: the width reaches the sheet's a little before the height does. */
    fun at(p: Float): Rect {
        val o = origin()
        val t = target()
        val across = if (p >= 1f) p else (p * 1.3f).coerceAtMost(1f)
        return Rect(
            calLerp(o.left, t.left, across).coerceAtLeast(0f),
            calLerp(o.top, t.top, p).coerceAtLeast(0f),
            calLerp(o.right, t.right, across).coerceAtMost(controller.screenWidth),
            calLerp(o.bottom, t.bottom, p).coerceAtMost(controller.screenHeight),
        )
    }

    /** Corner radius: the date's capsule turning into the sheet's corners. */
    fun corner(p: Float, height: Float): Float =
        calLerp(origin().height / 2f, px(cornerDp.toFloat()), p.coerceIn(0f, 1f)).coerceIn(0f, height / 2f)
}

private class CalendarCorner(private val geometry: CalendarGeometry, private val progress: () -> Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float = geometry.corner(progress(), shapeSize.height)
}

/** Places the surface at its current rectangle (the parent fills the screen). */
private fun Modifier.calendarBounds(geometry: CalendarGeometry, controller: HomeController): Modifier = layout { measurable, constraints ->
    val r = geometry.at(controller.calendar.value)
    controller.calendarTravel = (geometry.target().height - geometry.origin().height).coerceAtLeast(1f)
    val placeable = measurable.measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(r.left.roundToInt(), r.top.roundToInt()) }
}

/** Lays the content out at the open size, centred and anchored at the top: the month comes out of the date first. */
private fun Modifier.calendarContent(geometry: CalendarGeometry): Modifier = layout { measurable, constraints ->
    val t = geometry.target()
    val placeable = measurable.measure(Constraints.fixed(t.width.roundToInt().coerceAtLeast(1), t.height.roundToInt().coerceAtLeast(1)))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place((constraints.maxWidth - placeable.width) / 2, 0) }
}

/** A list at its top and the finger still pulling down: the calendar follows; pushing up gives it back first. */
private class CalendarScroll(private val controller: HomeController) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y < 0f && controller.calendarDragging) {
            return Offset(0f, controller.dragCalendarBy(available.y))
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 0f) return Offset(0f, controller.dragCalendarBy(available.y))
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!controller.calendarDragging) return Velocity.Zero
        controller.settleCalendar(available.y)
        return available
    }
}

/** The calendar over the home screen: composed while it is open and while it shrinks back into the date. */
@Composable
internal fun LiquidCalendar(glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop, controller: HomeController) {
    val density = LocalDensity.current
    val nav = WindowInsets.navigationBars
    val status = WindowInsets.statusBars
    val store = controller.data.settings
    val size by store.derive { get(LauncherSettings.CalendarSize) }
    val corner by store.derive { get(LauncherSettings.CalendarCorner) }
    val tier by store.derive { get(LauncherSettings.CalendarGlass) }
    val dim by store.derive { get(LauncherSettings.CalendarDim) / 100f }
    val geometry = remember(controller, density, nav, status, size, corner) { CalendarGeometry(controller, density, nav, status, size, corner) }
    val shape = remember(geometry) { RoundedCornerShape(CalendarCorner(geometry) { controller.calendar.value }) }
    val color = if (dim >= 0.45f) Color.White else sheetContentColor(glass, lightBackground)
    val layer = rememberLayerBackdrop()
    val connection = remember(controller) { CalendarScroll(controller) }
    val drag = rememberDraggableState { delta -> controller.dragCalendarBy(delta) }
    val surface = when (tier) {
        "auto" -> Modifier.sheetSurface(backdrop, shape, glass, lightBackground, exported = layer)
        else -> {
            val quality = when (tier) {
                "full" -> if (android.os.Build.VERSION.SDK_INT >= 33) GlassQuality.Full else GlassQuality.Frosted
                "frosted" -> GlassQuality.Frosted
                else -> GlassQuality.Tint
            }
            Modifier.liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground, large = true).copy(quality = quality), layer)
        }
    }
    Box(Modifier.fillMaxSize()) {
        // The home screen dims as the glass grows; a tap outside puts the calendar back into the date.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = controller.calendarAmount }
                .background(Color.Black.copy(alpha = dim))
                .pointerInput(controller) { detectTapGestures { controller.closeCalendar() } },
        )
        Box(
            Modifier
                .calendarBounds(geometry, controller)
                .then(surface)
                .bloomLight()
                .clip(shape)
                .boardPicture(shape) { controller.calendarAmount }
                .clickable(interactionSource = null, indication = null) { }
                .customiseOnLongPress(controller, "calendar", "the calendar")
                .draggable(drag, Orientation.Vertical, onDragStopped = { velocity -> controller.settleCalendar(velocity) })
                .nestedScroll(connection)
                .semantics { contentDescription = "Calendar" },
        ) {
            ProvideControls(controller, glass, layer) {
                CalendarBody(
                    controller, color,
                    Modifier
                        .calendarContent(geometry)
                        .graphicsLayer {
                            // The content arrives once the glass has started to grow and leaves before it is the date again.
                            val p = controller.calendar.value
                            alpha = ((p - 0.2f) / 0.5f).coerceIn(0f, 1f)
                            translationY = -(1f - p.coerceIn(0f, 1f)) * 14.dp.toPx()
                        },
                )
            }
        }
    }
}

private fun calText(color: Color, size: Float, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = size.sp, fontWeight = weight)

private val monthTitle = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
private val dayTitle = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
private val shortDay = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val clockTime = DateTimeFormatter.ofPattern("H:mm", Locale.getDefault())

/** The events loaded, by day, and the days they were read for. */
private class CalendarEvents(val byDay: Map<LocalDate, List<PhoneEvent>>, val from: LocalDate, val until: LocalDate)

@Composable
private fun CalendarBody(controller: HomeController, color: Color, modifier: Modifier) {
    val ui = controller.calendarUi
    val context = LocalContext.current
    val store = controller.data.settings
    val look by store.derive { calendarLook(widget = false) }
    val notesKey by store.derive { get(LauncherSettings.CalendarSchoolYear) + "|" + get(LauncherSettings.CalendarHolidayChanges) }
    val tick = rememberMinuteTick()
    val today = remember(tick) { LocalDate.now() }
    // Read again when the launcher comes back (an event added with Quick add) and every quarter of an hour.
    val focused = LocalWindowInfo.current.isWindowFocused
    val allowed = remember(ui.allowedTick, focused) { PhoneCalendars.canRead(context) }
    // Read by months, so a tap on a day reads nothing: the month shown (and the one picked), and two months after for the
    // agenda's sixty days.
    val firstMonth = minOf(ui.month, YearMonth.from(ui.selected))
    val lastMonth = maxOf(ui.month, YearMonth.from(ui.selected))
    val from = look.weekOf(firstMonth.atDay(1))
    val until = lastMonth.plusMonths(3).atDay(1)
    val events by produceState(CalendarEvents(emptyMap(), from, from), from, until, look.hidden, look.colours, allowed, focused, tick / 900_000) {
        value = withContext(Dispatchers.IO) {
            CalendarEvents(PhoneCalendars.byDay(PhoneCalendars.events(context, from, until, look.hidden, look.colours), from, until), from, until)
        }
    }
    val notes = remember(look, notesKey, from, until) { dayNotes(store, look, from, until) }
    val colours = remember(events) { events.byDay.mapValues { (_, list) -> list.map { it.color }.distinct().take(3) } }
    val scroll = rememberScrollState()
    Column(modifier.padding(horizontal = 14.dp).padding(top = 12.dp, bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ViewTabs(ui.view, color, Modifier.weight(1f)) {
                if (ui.view != it) Haptics.tick(controller.view)
                ui.view = it
                ui.acting = null
            }
            Spacer(Modifier.width(8.dp))
            GlassButton(null, color, Modifier.size(38.dp), icon = LauncherIcons.Add, contentDescription = "Add an event on ${ui.selected.format(dayTitle)}") {
                quickAdd(controller, ui.selected)
            }
        }
        Spacer(Modifier.height(8.dp))
        val pick: (LocalDate) -> Unit = { day ->
            if (day != ui.selected) Haptics.tick(controller.view)
            ui.selected = day
            ui.acting = null
        }
        when (ui.view) {
            "week" -> {
                WeekHeader(ui, look, color)
                WeekStrip(look.weekOf(ui.selected), ui.selected, today, look, colours, notes, color, pick) { by -> ui.select(ui.selected.plusDays(by)) }
                Spacer(Modifier.height(6.dp))
                DayAgenda(ui.selected, today, look, events.byDay[ui.selected].orEmpty(), notes[ui.selected], allowed, color, controller, scroll, Modifier.weight(1f))
            }
            "agenda" -> AgendaList(ui, today, look, events, notes, allowed, color, controller, Modifier.weight(1f))
            "year" -> YearView(ui, today, look, allowed, focused, color, controller, Modifier.weight(1f))
            else -> {
                MonthHeader(ui, today, color)
                val cell = when (look.density) {
                    "compact" -> 36.dp
                    "roomy" -> 52.dp
                    else -> 44.dp
                }
                AnimatedContent(
                    targetState = ui.month,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(spring(dampingRatio = 0.82f, stiffness = MotionScale.stiffness(420f))) { it / 3 * dir } + fadeIn()) togetherWith
                            (slideOutHorizontally(spring(dampingRatio = 1f, stiffness = MotionScale.stiffness(520f))) { -it / 3 * dir } + fadeOut())
                    },
                    label = "calendarMonth",
                ) { m ->
                    MonthDays(
                        m, today, look, colours, notes, color, selected = ui.selected, onDay = pick,
                        modifier = Modifier.fillMaxWidth().swipeBy { ui.shiftMonth(it) }, cellHeight = cell,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = 0.12f)))
                Spacer(Modifier.height(6.dp))
                DayAgenda(ui.selected, today, look, events.byDay[ui.selected].orEmpty(), notes[ui.selected], allowed, color, controller, scroll, Modifier.weight(1f))
            }
        }
    }
}

/** A horizontal swipe: [by] -1 (to the right: back) or 1 (to the left: on). Taps and vertical pulls pass. */
private fun Modifier.swipeBy(by: (Long) -> Unit): Modifier = pointerInput(Unit) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            val enough = 56.dp.toPx()
            if (total > enough) by(-1) else if (total < -enough) by(1)
        },
    ) { change, dx ->
        change.consume()
        total += dx
    }
}

@Composable
private fun ViewTabs(view: String, color: Color, modifier: Modifier, choose: (String) -> Unit) {
    val accent = HomeTheme.accent
    Row(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.08f))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((id, label) in CalendarViews) {
            val on = id == view
            val fill by animateColorAsState(if (on) accent.copy(alpha = 0.32f) else Color.Transparent, spring(dampingRatio = 1f, stiffness = MotionScale.stiffness(600f)), label = "tab")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
                    .background(fill)
                    .squishClickable { choose(id) }
                    .semantics { contentDescription = "$label view" + if (on) ", shown" else "" },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = calText(color.copy(alpha = if (on) 1f else 0.7f), 13f, if (on) FontWeight.SemiBold else FontWeight.Medium), maxLines = 1)
            }
        }
    }
}

@Composable
private fun RoundArrow(color: Color, back: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.1f))
            .squishClickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        LauncherIcon(LauncherIcons.ChevronRight, contentDescription = null, tint = color, size = 18.dp, modifier = Modifier.graphicsLayer { rotationZ = if (back) 180f else 0f })
    }
}

@Composable
private fun MonthHeader(ui: CalendarUi, today: LocalDate, color: Color) {
    val away = ui.month != YearMonth.from(today)
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            ui.month.atDay(1).format(monthTitle).replaceFirstChar { it.uppercase() },
            style = calText(color, 19f, FontWeight.SemiBold),
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            maxLines = 1,
        )
        if (away || ui.selected != today) {
            Text(
                "Today", style = calText(HomeTheme.accent, 13f, FontWeight.SemiBold),
                modifier = Modifier.clip(RoundedCornerShape(50)).squishClickable { ui.select(today) }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        RoundArrow(color, back = true, description = "Previous month") { ui.shiftMonth(-1) }
        Spacer(Modifier.width(6.dp))
        RoundArrow(color, back = false, description = "Next month") { ui.shiftMonth(1) }
    }
}

@Composable
private fun WeekHeader(ui: CalendarUi, look: CalendarLook, color: Color) {
    val start = look.weekOf(ui.selected)
    val end = start.plusDays(6)
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(start.format(shortDay) + " – " + end.format(shortDay), style = calText(color, 18f, FontWeight.SemiBold), maxLines = 1)
            if (look.weekNumbers) {
                Text("Week " + start.plusDays(3).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR), style = calText(color.copy(alpha = 0.6f), 12f, FontWeight.Medium))
            }
        }
        if (today.isBefore(start) || today.isAfter(end) || ui.selected != today) {
            Text(
                "Today", style = calText(HomeTheme.accent, 13f, FontWeight.SemiBold),
                modifier = Modifier.clip(RoundedCornerShape(50)).squishClickable { ui.select(today) }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        RoundArrow(color, back = true, description = "Previous week") { ui.select(ui.selected.minusDays(7)) }
        Spacer(Modifier.width(6.dp))
        RoundArrow(color, back = false, description = "Next week") { ui.select(ui.selected.plusDays(7)) }
    }
}

/** The week of [start]: each day's initial, number and marks, the blob on the day picked; a swipe moves a week. */
@Composable
private fun WeekStrip(
    start: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    look: CalendarLook,
    colours: Map<LocalDate, List<Int>>,
    notes: Map<LocalDate, DayNotes>,
    color: Color,
    pick: (LocalDate) -> Unit,
    move: (Long) -> Unit,
) {
    val accent = HomeTheme.accent
    BoxWithConstraints(Modifier.fillMaxWidth().height(70.dp).swipeBy { move(it * 7) }) {
        val cellW = constraints.maxWidth / 7f
        val cellH = constraints.maxHeight.toFloat()
        val index = ((selected.toEpochDay() - start.toEpochDay()).toInt()).takeIf { it in 0..6 }
        val top = with(LocalDensity.current) { 18.dp.toPx() }
        val blob = rememberBlob(index?.let { Offset(cellW * (it + 0.5f), top + (cellH - top) / 2f) })
        Row(Modifier.fillMaxSize().drawBehind { blob.draw(this, minOf(cellW, cellH - top) * 0.46f, accent) }) {
            for (i in 0 until 7) {
                val date = start.plusDays(i.toLong())
                Column(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        date.dayOfWeek.getDisplayName(DayStyle.SHORT, Locale.getDefault()),
                        style = calText(color.copy(alpha = 0.55f), 11f, FontWeight.Medium), maxLines = 1,
                        modifier = Modifier.height(18.dp),
                    )
                    Box(Modifier.fillMaxSize()) {
                        DayCell(date, date == today, look, colours[date].orEmpty(), notes[date], color, accent, pick)
                    }
                }
            }
        }
    }
}

/** The day picked: its name (and Hijri date), then its holidays, school dates and events. */
@Composable
private fun DayAgenda(
    day: LocalDate,
    today: LocalDate,
    look: CalendarLook,
    events: List<PhoneEvent>,
    notes: DayNotes?,
    allowed: Boolean,
    color: Color,
    controller: HomeController,
    scroll: ScrollState,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth().verticalScroll(scroll)) {
        DayTitle(day, today, look, color)
        DayRows(day, today, events, notes, allowed, color, controller)
    }
}

@Composable
private fun DayTitle(day: LocalDate, today: LocalDate, look: CalendarLook, color: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(day.format(dayTitle).replaceFirstChar { it.uppercase() }, style = calText(color, 15f, FontWeight.SemiBold), maxLines = 1)
        val when_ = when (day) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> null
        }
        if (when_ != null) {
            Spacer(Modifier.width(8.dp))
            Text(when_, style = calText(HomeTheme.accent, 13f, FontWeight.SemiBold))
        }
    }
    if (look.hijri) {
        Hijri.of(day, look.hijriShift)?.let { h ->
            Text("≈ " + Hijri.format(h), style = calText(color.copy(alpha = 0.6f), 12.5f), modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun DayRows(
    day: LocalDate,
    today: LocalDate,
    events: List<PhoneEvent>,
    notes: DayNotes?,
    allowed: Boolean,
    color: Color,
    controller: HomeController,
) {
    val ui = controller.calendarUi
    for (h in notes?.holidays.orEmpty()) HolidayRow(h, ui.acting == h.id, color, controller)
    for (d in notes?.school.orEmpty()) SchoolRow(d, today, ui.acting == d.id, color, controller)
    for (e in events) EventRow(e, day, color, controller)
    if (events.isEmpty() && notes?.isEmpty != false) {
        Text(
            if (allowed) "Nothing on this day" else "Holidays and the school year only",
            style = calText(color.copy(alpha = 0.55f), 13.5f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }
    if (!allowed) {
        Spacer(Modifier.height(4.dp))
        GlassButton("Show my events", color, icon = LauncherIcons.Calendar) {
            controller.askCalendarRead { ui.allowedTick++ }
        }
    }
}

@Composable
private fun RowShell(color: Color, mark: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(mark))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun ActionChips(color: Color, actions: List<Pair<String, () -> Unit>>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 18.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, run) in actions) {
            Text(
                label, style = calText(color, 13f, FontWeight.Medium), maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.1f))
                    .squishClickable(onClick = run)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun HolidayRow(h: Holiday, acting: Boolean, color: Color, controller: HomeController) {
    val ui = controller.calendarUi
    val store = controller.data.settings
    RowShell(color, HolidayInk, onClick = { ui.acting = if (acting) null else h.id }) {
        Column {
            Text(h.label, style = calText(color, 15f, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (h.islamic && !h.confirmed) "Holiday in Tunisia · the moon decides: tap to set the announced day" else "Holiday in Tunisia",
                style = calText(color.copy(alpha = 0.6f), 12f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (acting) {
        fun save(changes: Map<String, String>) = store.set(LauncherSettings.CalendarHolidayChanges, TunisiaHolidays.encodeChanges(changes))
        val changes = TunisiaHolidays.parseChanges(store.get(LauncherSettings.CalendarHolidayChanges))
        val actions = ArrayList<Pair<String, () -> Unit>>()
        if (h.islamic) {
            actions += "A day earlier" to { save(TunisiaHolidays.move(changes, h, -1)); ui.acting = null }
            actions += "A day later" to { save(TunisiaHolidays.move(changes, h, 1)); ui.acting = null }
        }
        actions += "Hide" to {
            save(TunisiaHolidays.hide(changes, h))
            ui.acting = null
            Notices.post(
                Notice(
                    NoticeKind.Info, "${h.name} hidden", "Settings › Calendar › Holidays and school year puts it back",
                    buttonLabel = "Undo", button = { save(changes) }, showMs = UNDO_MS, holds = true,
                ),
            )
        }
        if (h.id in changes) actions += "Back to the tables" to { save(TunisiaHolidays.reset(changes, h.id)); ui.acting = null }
        ActionChips(color, actions)
    }
}

@Composable
private fun SchoolRow(d: SchoolDate, today: LocalDate, acting: Boolean, color: Color, controller: HomeController) {
    val ui = controller.calendarUi
    val store = controller.data.settings
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            RowShell(color, schoolInk(d), onClick = { ui.acting = if (acting) null else d.id }) {
                Column {
                    Text((if (d.approx) "≈ " else "") + d.title.ifBlank { d.label }, style = calText(color, 15f, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(SchoolYear.countdown(d, today), "school year, indicative").joinToString(" · "),
                        style = calText(color.copy(alpha = 0.6f), 12f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .squishClickable {
                    val list = SchoolYear.parse(store.get(LauncherSettings.CalendarSchoolYear))
                    store.set(LauncherSettings.CalendarSchoolYear, SchoolYear.encode(SchoolYear.togglePin(list, d.id)))
                    Haptics.tick(controller.view)
                }
                .semantics { contentDescription = if (d.pinned) "Unpin ${d.label} from the clock" else "Pin ${d.label} under the clock" },
            contentAlignment = Alignment.Center,
        ) {
            LauncherIcon(LauncherIcons.Pin, contentDescription = null, tint = if (d.pinned) HomeTheme.accent else color.copy(alpha = 0.4f), size = 20.dp)
        }
    }
    if (acting) {
        val before = store.get(LauncherSettings.CalendarSchoolYear)
        val list = SchoolYear.parse(before)
        fun save(next: List<SchoolDate>) = store.set(LauncherSettings.CalendarSchoolYear, SchoolYear.encode(next))
        ActionChips(
            color,
            listOf(
                // The day picked follows the date, its actions still open for another step.
                "A day earlier" to {
                    save(SchoolYear.move(list, d.id, -1))
                    d.startDate?.minusDays(1)?.let { ui.selected = it; ui.month = YearMonth.from(it) }
                },
                "A day later" to {
                    save(SchoolYear.move(list, d.id, 1))
                    d.startDate?.plusDays(1)?.let { ui.selected = it; ui.month = YearMonth.from(it) }
                },
                "Edit" to { controller.openSettingsAt("page:calendarDays:more") },
                "Delete" to {
                    save(SchoolYear.remove(list, d.id))
                    ui.acting = null
                    Notices.post(
                        Notice(
                            NoticeKind.Info, "${d.label} deleted", null,
                            buttonLabel = "Undo", button = { store.set(LauncherSettings.CalendarSchoolYear, before) }, showMs = UNDO_MS, holds = true,
                        ),
                    )
                },
            ),
        )
    }
}

@Composable
private fun EventRow(e: PhoneEvent, day: LocalDate, color: Color, controller: HomeController) {
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val running = !e.allDay && e.begin <= now && e.end > now
    val (first, last) = e.days(zone)
    val time = when {
        e.allDay -> if (first == last) "All day" else "All day · until " + last.format(shortDay)
        first != day && last != day -> "All day"
        first != day -> "Until " + Instant.ofEpochMilli(e.end).atZone(zone).format(clockTime)
        last != day -> "From " + Instant.ofEpochMilli(e.begin).atZone(zone).format(clockTime)
        else -> Instant.ofEpochMilli(e.begin).atZone(zone).format(clockTime) + " – " + Instant.ofEpochMilli(e.end).atZone(zone).format(clockTime)
    }
    RowShell(color, Color(e.color).copy(alpha = 1f), onClick = {
        val view = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
        controller.startFromCalendar(view)
    }) {
        Column {
            Text(e.title, style = calText(color, 15f, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (running) "Now · " else "") + time + if (e.location.isNotBlank()) " · " + e.location.lineSequence().first() else "",
                style = calText(if (running) HomeTheme.accent else color.copy(alpha = 0.6f), 12f, if (running) FontWeight.SemiBold else FontWeight.Normal),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The days from the one picked, with what is on each; the school year's countdowns above. */
@Composable
private fun AgendaList(
    ui: CalendarUi,
    today: LocalDate,
    look: CalendarLook,
    events: CalendarEvents,
    notes: Map<LocalDate, DayNotes>,
    allowed: Boolean,
    color: Color,
    controller: HomeController,
    modifier: Modifier,
) {
    val store = controller.data.settings
    val schoolText by store.derive { get(LauncherSettings.CalendarSchoolYear) }
    val upcoming = remember(schoolText, today, look.school) { if (look.school) SchoolYear.upcoming(SchoolYear.parse(schoolText), today).take(10) else emptyList() }
    Column(modifier) {
        if (upcoming.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in upcoming) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(schoolInk(d).copy(alpha = 0.16f))
                            .border(1.dp, schoolInk(d).copy(alpha = 0.45f), RoundedCornerShape(50))
                            .squishClickable {
                                d.startDate?.let { ui.select(if (it.isBefore(today)) today else it) }
                                ui.view = "month"
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (d.pinned) {
                            LauncherIcon(LauncherIcons.Pin, contentDescription = "Pinned", tint = color, size = 14.dp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(SchoolYear.countdown(d, today) ?: d.label, style = calText(color, 13f, FontWeight.Medium), maxLines = 1)
                    }
                }
            }
        }
        val start = ui.selected
        val days = remember(events, notes, start) {
            (0L until 60L).map { start.plusDays(it) }.filter { day -> events.byDay[day].orEmpty().isNotEmpty() || notes[day]?.isEmpty == false }
        }
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
            if (days.isEmpty()) {
                DayTitle(start, today, look, color)
                DayRows(start, today, emptyList(), null, allowed, color, controller)
            }
            for (day in days) {
                DayTitle(day, today, look, color)
                DayRows(day, today, events.byDay[day].orEmpty(), notes[day], true, color, controller)
                Spacer(Modifier.height(6.dp))
            }
            if (!allowed && days.isNotEmpty()) {
                GlassButton("Show my events", color, icon = LauncherIcons.Calendar) { controller.askCalendarRead { ui.allowedTick++ } }
            }
        }
    }
}

/** The year as a heatmap: each day a square, darker with more events; holidays ringed, school dates dotted. */
@Composable
private fun YearView(
    ui: CalendarUi,
    today: LocalDate,
    look: CalendarLook,
    allowed: Boolean,
    focused: Boolean,
    color: Color,
    controller: HomeController,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val store = controller.data.settings
    val year = ui.year
    val first = LocalDate.of(year, 1, 1)
    val last = LocalDate.of(year, 12, 31)
    val counts by produceState(emptyMap<LocalDate, Int>(), year, look.hidden, allowed, focused) {
        value = withContext(Dispatchers.IO) {
            val start = System.nanoTime()
            val events = PhoneCalendars.events(context, first, last.plusDays(1), look.hidden)
            PhoneCalendars.byDay(events, first, last.plusDays(1)).mapValues { it.value.size }.also {
                DebugLog.event("Calendar", "year $year: ${events.size} events read in ${(System.nanoTime() - start) / 1_000_000} ms")
            }
        }
    }
    val notesKey by store.derive { get(LauncherSettings.CalendarSchoolYear) + "|" + get(LauncherSettings.CalendarHolidayChanges) }
    val notes = remember(look, notesKey, year) { dayNotes(store, look, first, last) }
    val accent = HomeTheme.accent
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(year.toString(), style = calText(color, 19f, FontWeight.SemiBold), modifier = Modifier.weight(1f).padding(start = 4.dp))
            if (year != today.year) {
                Text(
                    "This year", style = calText(accent, 13f, FontWeight.SemiBold),
                    modifier = Modifier.clip(RoundedCornerShape(50)).squishClickable { ui.year = today.year }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            RoundArrow(color, back = true, description = "Previous year") { ui.year = year - 1 }
            Spacer(Modifier.width(6.dp))
            RoundArrow(color, back = false, description = "Next year") { ui.year = year + 1 }
        }
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).swipeBy { ui.year = year + it.toInt() }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in 0 until 4) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (col in 0 until 3) {
                        val month = YearMonth.of(year, row * 3 + col + 1)
                        Column(Modifier.weight(1f)) {
                            Text(
                                month.month.getDisplayName(DayStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.uppercase() },
                                style = calText(if (month == YearMonth.from(today)) accent else color.copy(alpha = 0.8f), 12.5f, FontWeight.SemiBold),
                                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                            )
                            MiniMonth(month, today, ui.selected, look, counts, notes, color, accent) { day ->
                                ui.select(day)
                                ui.view = "month"
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 4.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Fewer", style = calText(color.copy(alpha = 0.55f), 11f))
                for (a in listOf(0.08f, 0.3f, 0.5f, 0.7f, 0.92f)) {
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(if (a < 0.1f) color.copy(alpha = a) else accent.copy(alpha = a)))
                }
                Spacer(Modifier.width(4.dp))
                Text("more events", style = calText(color.copy(alpha = 0.55f), 11f))
            }
        }
    }
}

/** One month of the heatmap, drawn in one pass; a tap opens the day in the month view. */
@Composable
private fun MiniMonth(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    look: CalendarLook,
    counts: Map<LocalDate, Int>,
    notes: Map<LocalDate, DayNotes>,
    color: Color,
    accent: Color,
    open: (LocalDate) -> Unit,
) {
    val lead = look.lead(month)
    val length = month.lengthOfMonth()
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(7f / 6f)
            .pointerInput(month, lead) {
                detectTapGestures { p ->
                    val cell = size.width / 7f
                    val index = (p.y / cell).toInt() * 7 + (p.x / cell).toInt()
                    val day = index - lead + 1
                    if (day in 1..length) open(month.atDay(day))
                }
            },
    ) {
        val cell = size.width / 7f
        val gap = cell * 0.14f
        val r = CornerRadius(cell * 0.18f)
        for (day in 1..length) {
            val i = lead + day - 1
            val date = month.atDay(day)
            val topLeft = Offset((i % 7) * cell + gap / 2f, (i / 7) * cell + gap / 2f)
            val box = Size(cell - gap, cell - gap)
            val n = counts[date] ?: 0
            val fill = when {
                n <= 0 -> color.copy(alpha = 0.08f)
                n == 1 -> accent.copy(alpha = 0.3f)
                n == 2 -> accent.copy(alpha = 0.5f)
                n <= 4 -> accent.copy(alpha = 0.7f)
                else -> accent.copy(alpha = 0.92f)
            }
            drawRoundRect(fill, topLeft, box, r)
            val note = notes[date]
            if (note?.holidays?.isNotEmpty() == true) drawRoundRect(HolidayInk, topLeft, box, r, style = Stroke(cell * 0.1f))
            note?.school?.firstOrNull()?.let { drawCircle(schoolInk(it), cell * 0.13f, topLeft + Offset(box.width * 0.78f, box.height * 0.22f)) }
            if (date == today) drawRoundRect(color, topLeft, box, r, style = Stroke(cell * 0.12f))
            if (date == selected) drawRoundRect(accent, topLeft - Offset(gap / 3f, gap / 3f), Size(box.width + gap * 2f / 3f, box.height + gap * 2f / 3f), r, style = Stroke(cell * 0.09f))
        }
    }
}

/** Quick add: the calendar app's own "new event" screen on [day] (no permission to write is needed). */
private fun quickAdd(controller: HomeController, day: LocalDate) {
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.now()
    val start = if (day == now.toLocalDate()) now.plusHours(1).withMinute(0).withSecond(0).withNano(0) else day.atTime(9, 0)
    val begin = start.atZone(zone).toInstant().toEpochMilli()
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 3_600_000L)
    DebugLog.event("Calendar", "quick add on $day")
    controller.startFromCalendar(intent)
}
