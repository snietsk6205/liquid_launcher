package dev.liquid.launcher.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.data.StudyDay
import dev.liquid.launcher.core.data.StudyEvent
import dev.liquid.launcher.core.data.StudyShift
import dev.liquid.launcher.core.data.StudyStatus
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.derive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The study hub (0.9.1): Today, grown out of the pill.
 *
 * One glass surface whose bounds move from the pill (or whatever opened it: the clock line) to the
 * open panel with a spring, width leading and height following, the corners going from a capsule to
 * the panel's. Its content is laid out once at the open size and anchored at the bottom, so the
 * thumb's row (the main action, the note field, Claude) comes out of the pill first and the rest is
 * uncovered as the glass rises. The finger can take it at any moment: pulling down anywhere outside
 * the list, or past the list's top, shrinks it back into the pill; letting go springs from where it is,
 * with the finger's speed. Everything is set while laying out, so a frame only re-measures this surface.
 */

private fun hubLerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** The hub's rectangles, from the screen, the insets (read while laying out) and the hub's progress. */
@Stable
internal class HubGeometry(
    private val controller: HomeController,
    private val density: Density,
    private val nav: WindowInsets,
    private val ime: WindowInsets,
    private val status: WindowInsets,
    private val fraction: Float,
) {
    /** The open panel: above the navigation bar and the keyboard, as tall as the setting asks and the screen allows. */
    fun target(): Rect {
        val w = controller.screenWidth
        val h = controller.screenHeight
        val margin = with(density) { 10.dp.toPx() }
        val bottom = h - maxOf(nav.getBottom(density), ime.getBottom(density)) - margin
        val top = maxOf(status.getTop(density) + margin, bottom - h * fraction)
        return Rect(margin, top, w - margin, bottom)
    }

    /** What the hub grows from: what was tapped, the pill, or the pill's usual place. */
    fun origin(): Rect {
        controller.hubOrigin?.takeIf { it.width > 1f && it.height > 1f }?.let { return it }
        val w = controller.screenWidth
        val h = controller.screenHeight
        val pw = with(density) { 220.dp.toPx() }
        val ph = with(density) { 54.dp.toPx() }
        val bottom = h - nav.getBottom(density) - with(density) { 20.dp.toPx() }
        return Rect((w - pw) / 2f, bottom - ph, (w + pw) / 2f, bottom)
    }

    /**
     * The surface at progress [p]. The width reaches the panel's a little before the height does (a drop
     * spreading, then rising); past 1, while the spring overshoots, both grow a touch beyond and settle back.
     */
    fun at(p: Float): Rect {
        val o = origin()
        val t = target()
        val across = if (p >= 1f) p else (p * 1.3f).coerceAtMost(1f)
        return Rect(
            hubLerp(o.left, t.left, across).coerceAtLeast(0f),
            hubLerp(o.top, t.top, p).coerceAtLeast(0f),
            hubLerp(o.right, t.right, across).coerceAtMost(controller.screenWidth),
            hubLerp(o.bottom, t.bottom, p).coerceAtMost(controller.screenHeight),
        )
    }

    /** Corner radius: the origin's capsule turning into the panel's 30 dp. */
    fun corner(p: Float, height: Float): Float {
        val start = origin().height / 2f
        val end = with(density) { 30.dp.toPx() }
        return hubLerp(start, end, p.coerceIn(0f, 1f)).coerceIn(0f, height / 2f)
    }
}

private class HubCorner(private val geometry: HubGeometry, private val progress: () -> Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float = geometry.corner(progress(), shapeSize.height)
}

/** Places the surface at its current rectangle (the parent fills the screen). */
private fun Modifier.hubBounds(geometry: HubGeometry, controller: HomeController): Modifier = layout { measurable, constraints ->
    val r = geometry.at(controller.hub.value)
    controller.hubTravel = (geometry.target().height - geometry.origin().height).coerceAtLeast(1f)
    val placeable = measurable.measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(r.left.roundToInt(), r.top.roundToInt())
    }
}

/** Lays the content out at the open size, centred and anchored at the bottom of the (smaller) surface. */
private fun Modifier.hubContent(geometry: HubGeometry): Modifier = layout { measurable, constraints ->
    val t = geometry.target()
    val placeable = measurable.measure(Constraints.fixed(t.width.roundToInt().coerceAtLeast(1), t.height.roundToInt().coerceAtLeast(1)))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place((constraints.maxWidth - placeable.width) / 2, constraints.maxHeight - placeable.height)
    }
}

/** The list at its top and the finger still pulling down: the hub follows; pushing up gives it back first. */
private class HubScroll(private val controller: HomeController) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y < 0f && controller.hubDragging) {
            return Offset(0f, controller.dragHubBy(available.y))
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 0f) {
            return Offset(0f, controller.dragHubBy(available.y))
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!controller.hubDragging) return Velocity.Zero
        controller.settleHub(available.y)
        return available
    }
}

@Composable
internal fun StudyHub(glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop, controller: HomeController) {
    val density = LocalDensity.current
    val nav = WindowInsets.navigationBars
    val ime = WindowInsets.ime
    val status = WindowInsets.statusBars
    val fraction by controller.data.settings.derive { get(LauncherSettings.StudyHubSize) / 100f }
    val geometry = remember(controller, density, nav, ime, status, fraction) { HubGeometry(controller, density, nav, ime, status, fraction) }
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    val shape = remember(geometry) { RoundedCornerShape(HubCorner(geometry) { controller.hub.value }) }
    val layer = rememberLayerBackdrop()
    val scroll = rememberScrollState()
    val redraw = remember(scroll) { { scroll.value } }
    val connection = remember(controller) { HubScroll(controller) }
    val drag = rememberDraggableState { delta -> controller.dragHubBy(delta) }
    Box(Modifier.fillMaxSize()) {
        // The home screen dims in step with the glass rising; a tap outside puts the hub back into the pill.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = controller.hubAmount }
                .background(Color.Black.copy(alpha = 0.26f))
                .pointerInput(controller) { detectTapGestures { controller.closeHub() } },
        )
        Box(
            Modifier
                .hubBounds(geometry, controller)
                .liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground, large = true), exported = layer)
                .clip(shape)
                .clickable(interactionSource = null, indication = null) { }
                .draggable(drag, Orientation.Vertical, onDragStopped = { velocity -> controller.settleHub(velocity) })
                .nestedScroll(connection),
        ) {
            ProvideControls(controller, glass, layer, redraw) {
                HubContent(
                    controller, color, scroll,
                    Modifier
                        .hubContent(geometry)
                        .graphicsLayer {
                            // The content arrives after the glass has started to grow and leaves before it is a pill again.
                            val p = controller.hub.value
                            alpha = ((p - 0.22f) / 0.5f).coerceIn(0f, 1f)
                            translationY = (1f - p.coerceIn(0f, 1f)) * 18.dp.toPx()
                        },
                )
            }
        }
    }
}

/** The block the hub is about: the one running (even once marked), else the next open one, else one left unmarked. */
internal fun heroBlock(day: StudyDay, now: Long): StudyEvent? =
    day.current(now) ?: day.blocks.firstOrNull { it.begin > now && it.status == StudyStatus.Open } ?: day.unmarked(now).firstOrNull()

@Composable
private fun HubContent(controller: HomeController, color: Color, scroll: ScrollState, modifier: Modifier) {
    val study = controller.data.study
    val day by study.day.collectAsState()
    val loaded by study.loaded.collectAsState()
    val focus by study.focus.collectAsState()
    val homeAt by study.homeAt.collectAsState()
    val shiftTick by study.shiftTick.collectAsState()
    val tick = rememberMinuteTick()
    val now = remember(tick, day, homeAt, shiftTick) { System.currentTimeMillis() }
    val prefs by controller.data.settings.derive {
        TodayPrefs(get(LauncherSettings.StudyBlockCards), get(LauncherSettings.StudyButtonSide) != "start", get(LauncherSettings.StudyTomorrow))
    }
    val hero = remember(day, now) { heroBlock(day, now) }
    Column(modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp)) {
        HubHeader(controller, day, now, color)
        if (!loaded) {
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                Text(
                    "The study companion is off, or it may not read the calendar yet.",
                    style = studyText(color, 13, alpha = 0.8f), modifier = Modifier.padding(bottom = 10.dp),
                )
                ActionChip("Switch it on", color, filled = true) { controller.setupStudy() }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                AttendanceCallout(controller, day, now, color)
                ArrivalCallout(controller, day, now, homeAt, color)
                if (day.isEmpty && day.classes.isEmpty()) {
                    Text(
                        "Nothing in your study and task calendars today. Blocks come from calendars named like \"${controller.data.settings.get(LauncherSettings.StudyBlockCalendars)}\", " +
                            "tasks from \"${controller.data.settings.get(LauncherSettings.StudyTaskCalendars)}\" (Settings → Study companion).",
                        style = studyText(color, 13, alpha = 0.8f), modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                if (hero != null) {
                    SectionLabel(heroLabel(hero, now), color)
                    BlockCard(hero, now, prefs, focus, color, controller, startOpen = true)
                }
                Timeline(controller, day, now, hero, prefs, focus, color)
                if (day.overdue.isNotEmpty()) {
                    SectionLabel("Still open from earlier", color)
                    day.overdue.forEach { task ->
                        TaskRow(task.title.text, false, color, true) { controller.studyStatus(task, StudyStatus.Done) }
                    }
                }
                if (day.tasks.isNotEmpty()) SectionLabel("Tasks", color)
                day.tasks.forEach { task ->
                    val done = task.status != StudyStatus.Open
                    TaskRow(task.title.text, done, color, true) { controller.studyStatus(task, if (done) StudyStatus.Open else StudyStatus.Done) }
                }
                if (day.notes.isNotEmpty()) {
                    SectionLabel("Notes for tonight's check-in", color)
                    day.notes.forEach { note ->
                        Text(
                            "• " + note.rawTitle.trim().removePrefix(dev.liquid.launcher.core.data.StudyFormat.NOTE_PREFIX).trim(),
                            style = studyText(color, 13, alpha = 0.85f), modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                if (prefs.tomorrow != "off" && (day.tomorrowBlocks.isNotEmpty() || day.tomorrowTasks.isNotEmpty())) {
                    TomorrowSection(day, prefs, color)
                }
                if (day.waitingToSync > 0) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (day.waitingToSync == 1) "1 change" else "${day.waitingToSync} changes") + " not sent to Google yet",
                            style = studyText(color, 11, alpha = 0.75f), modifier = Modifier.weight(1f),
                        )
                        ActionChip("Sync now", color) {
                            study.requestSync(force = true)
                            controller.toast("Asked the phone to sync the calendar")
                        }
                    }
                }
                if (!study.canWrite) {
                    Text(
                        "Ticks are not written to the calendar yet (no permission), so your assistant cannot see them.",
                        style = studyText(color, 11, alpha = 0.7f), modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        HubBar(controller, day, now, hero, homeAt, focus?.eventKey, color, loaded)
    }
}

private fun heroLabel(block: StudyEvent, now: Long): String = when {
    now >= block.begin && now < block.end -> "Now · " + studyDuration(block.end - now) + " left"
    block.begin > now -> "Next · in " + studyDuration(block.begin - now)
    else -> "Ended without a mark"
}

/** Handle, day ring, date and progress; Tell Claude and the plan as icons. */
@Composable
private fun HubHeader(controller: HomeController, day: StudyDay, now: Long, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 8.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.3f)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            DayRing(day, now, color, 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Today", style = studyText(color, 18, FontWeight.SemiBold))
                val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
                val progress = if (day.blocks.isEmpty()) "" else " · ${day.blocksDone}/${day.blocks.size} blocks"
                Text(date + progress, style = studyText(color, 12, alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HubIcon(LauncherIcons.Chat, "Tell Claude", color) { controller.run(LauncherAction.TellClaude) }
            Spacer(Modifier.width(6.dp))
            HubIcon(LauncherIcons.Calendar, "Plan", color) { controller.run(LauncherAction.StudyPlan) }
        }
    }
}

@Composable
private fun HubIcon(icon: Int, description: String, color: Color, size: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.1f))
            .squishClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LauncherIcon(icon, contentDescription = description, tint = color, size = size * 0.5f)
    }
}

/**
 * The day as a ring (C2): one arc per block in the block's colour once it is done, the running one filling
 * with the time gone, the ones to come faint, a skipped one grey, and a gap where a block ended unmarked.
 */
@Composable
internal fun DayRing(day: StudyDay, now: Long, color: Color, size: Dp, showCount: Boolean = true, strokeFraction: Float = 0.13f) {
    val blocks = day.blocks
    // The ring pops a little each time a block is ticked off.
    val done = day.blocksDone
    val pop by animateFloatAsState(done.toFloat(), spring(dampingRatio = 0.45f, stiffness = MotionScale.stiffness(500f)), label = "dayRing")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1f + 0.08f * (done - pop).coerceIn(-1f, 1f)
                    scaleX = s
                    scaleY = s
                },
        ) {
            val stroke = this.size.minDimension * strokeFraction
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            val n = blocks.size
            if (n == 0) {
                drawCircle(color.copy(alpha = 0.2f), radius = (this.size.minDimension - stroke) / 2f, style = Stroke(stroke))
                return@Canvas
            }
            val step = 360f / n
            val gap = if (n > 1) 16f else 0f
            val sweep = step - gap
            val round = Stroke(stroke, cap = if (n > 1) StrokeCap.Round else StrokeCap.Butt)
            blocks.forEachIndexed { i, block ->
                val start = -90f + i * step + gap / 2f
                val accent = eventColor(block)
                val running = now >= block.begin && now < block.end
                when {
                    block.status == StudyStatus.Done -> drawArc(accent, start, sweep, false, topLeft, arc, style = round)
                    block.status == StudyStatus.Skipped -> drawArc(color.copy(alpha = 0.25f), start, sweep, false, topLeft, arc, style = round)
                    block.end <= now -> Unit
                    running -> {
                        drawArc(color.copy(alpha = 0.16f), start, sweep, false, topLeft, arc, style = round)
                        val gone = ((now - block.begin).toFloat() / (block.end - block.begin).coerceAtLeast(1)).coerceIn(0.02f, 1f)
                        drawArc(accent, start, sweep * gone, false, topLeft, arc, style = round)
                    }
                    else -> drawArc(color.copy(alpha = 0.16f), start, sweep, false, topLeft, arc, style = round)
                }
            }
        }
        if (showCount && blocks.isNotEmpty()) {
            Text(
                "$done/${blocks.size}",
                style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = (size.value * 0.27f).sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/**
 * Today's timeline (B4): classes in grey, the study blocks on a rail in their colours, a line at the time
 * now when it falls between them. The block the hub shows above is only a row here; tapping another
 * block opens it in place with its steps and buttons.
 */
@Composable
private fun Timeline(
    controller: HomeController,
    day: StudyDay,
    now: Long,
    hero: StudyEvent?,
    prefs: TodayPrefs,
    focus: dev.liquid.launcher.core.data.StudyFocus?,
    color: Color,
) {
    val rows = remember(day) { (day.classes.map { it to true } + day.blocks.map { it to false }).sortedBy { it.first.begin } }
    if (rows.isEmpty() || (rows.size == 1 && hero != null && day.classes.isEmpty())) return
    var open by remember { mutableStateOf<String?>(null) }
    SectionLabel("Timeline", color)
    val anyRunning = day.blocks.any { now >= it.begin && now < it.end }
    var nowShown = anyRunning
    Column(Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = 0.86f, stiffness = MotionScale.stiffness(520f)))) {
        rows.forEach { (event, isClass) ->
            if (!nowShown && event.begin > now) {
                NowLine(now, color)
                nowShown = true
            }
            if (isClass) {
                ClassRow(event, now, color)
            } else {
                val isHero = hero?.key == event.key
                TimelineRow(event, now, color, isHero) { if (!isHero) open = if (open == event.key) null else event.key }
                if (open == event.key && !isHero) {
                    Box(Modifier.padding(start = 60.dp)) { BlockCard(event, now, prefs, focus, color, controller, startOpen = true) }
                }
            }
        }
    }
}

@Composable
private fun NowLine(now: Long, color: Color) {
    val accent = HomeTheme.accent
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(studyClock(now), modifier = Modifier.width(42.dp), style = studyText(accent, 11, FontWeight.SemiBold))
        Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(7.dp).clip(CircleShape).background(accent)) }
        Box(Modifier.weight(1f).height(1.5.dp).background(accent.copy(alpha = 0.7f)))
    }
}

@Composable
private fun ClassRow(event: StudyEvent, now: Long, color: Color) {
    val past = event.end <= now
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(studyClock(event.begin), modifier = Modifier.width(42.dp), style = studyText(color, 11, alpha = 0.5f))
        Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(color.copy(alpha = if (past) 0.25f else 0.45f)))
        }
        Text(
            event.rawTitle.ifBlank { "Class" } + " · until " + studyClock(event.end),
            style = studyText(color, 12, alpha = if (past) 0.45f else 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun TimelineRow(block: StudyEvent, now: Long, color: Color, isHero: Boolean, onTap: () -> Unit) {
    val accent = eventColor(block)
    val running = now >= block.begin && now < block.end
    val missed = block.end <= now && block.status == StudyStatus.Open
    val tasks = block.tasks
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isHero) color.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(interactionSource = null, indication = null, onClick = onTap)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(studyClock(block.begin), modifier = Modifier.width(42.dp).padding(top = 1.dp), style = studyText(color, 12, alpha = 0.75f))
        Canvas(Modifier.width(18.dp).fillMaxHeight()) {
            val cx = this.size.width / 2f
            val dot = 9.dp.toPx()
            drawLine(color.copy(alpha = 0.16f), Offset(cx, 0f), Offset(cx, this.size.height), strokeWidth = 2.dp.toPx())
            if (running) {
                val gone = ((now - block.begin).toFloat() / (block.end - block.begin).coerceAtLeast(1)).coerceIn(0f, 1f)
                drawLine(accent, Offset(cx, dot), Offset(cx, dot + (this.size.height - dot) * gone), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
            val r = 5.dp.toPx()
            when {
                block.status == StudyStatus.Done -> drawCircle(accent, r, Offset(cx, dot))
                block.status == StudyStatus.Skipped -> drawCircle(color.copy(alpha = 0.3f), r * 0.8f, Offset(cx, dot))
                missed -> drawCircle(accent, r, Offset(cx, dot), style = Stroke(1.6.dp.toPx()))
                running -> drawCircle(accent, r * 1.2f, Offset(cx, dot))
                else -> drawCircle(accent.copy(alpha = 0.6f), r * 0.85f, Offset(cx, dot))
            }
        }
        Column(Modifier.weight(1f).padding(start = 6.dp, bottom = 6.dp)) {
            Text(
                block.title.text,
                style = studyText(
                    color, 14, if (running) FontWeight.SemiBold else FontWeight.Medium,
                    alpha = if (block.status != StudyStatus.Open) 0.6f else 1f, struck = block.status == StudyStatus.Skipped,
                ),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val parts = ArrayList<String>()
            parts += "until " + studyClock(block.end)
            if (tasks.isNotEmpty()) parts += "${tasks.count { it.done }}/${tasks.size} steps"
            when {
                block.status == StudyStatus.Done -> parts += "✓ done"
                block.status == StudyStatus.Skipped -> parts += "skipped"
                missed -> parts += "not marked: tap"
                running -> parts += studyDuration(block.end - now) + " left"
            }
            val shifted = dev.liquid.launcher.core.data.StudyFormat.shiftedBy(block.description)
            if (shifted != 0) parts += "moved " + (if (shifted > 0) "+" else "") + shifted + " min"
            Text(parts.joinToString(" · "), style = studyText(color, 11, alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Getting home (A2) and the evening (A3): "I'm home" once the last class is over, then, if home later than
 * planned, "Home 19:20 · shift tonight +30 min?", or the shortening of the lowest-priority block when the
 * evening would run past its latest end. Answered once a day.
 */
@Composable
private fun ArrivalCallout(controller: HomeController, day: StudyDay, now: Long, homeAt: Long?, color: Color) {
    val study = controller.data.study
    val arrival by controller.data.settings.derive { get(LauncherSettings.StudyArrival) }
    val homeWifi by controller.data.settings.derive { get(LauncherSettings.StudyHomeWifi) }
    val lead by controller.data.settings.derive { get(LauncherSettings.StudyShiftLead) }
    if (!arrival) return
    val firstOpen = day.blocks.firstOrNull { it.end > now && it.status == StudyStatus.Open }
    if (homeAt == null) {
        val lastClass = day.lastClassEnd
        // On the way home: after the last class, or on a day without classes once tonight's first block is near.
        val travelling = when {
            lastClass != null -> now >= lastClass
            firstOpen != null -> firstOpen.begin - now in 0..(2 * 60 * 60_000L)
            else -> false
        }
        if (!travelling || firstOpen == null) return
        HubCallout(color) {
            Text("On the way home?", style = studyText(color, 14, FontWeight.SemiBold))
            // A4: what the check-in planned for the trip ("Pendant le trajet : …" in tonight's first block).
            firstOpen.notes.lines().firstOrNull { it.trim().startsWith("Pendant le trajet", ignoreCase = true) }?.let { trip ->
                Text(trip.trim(), style = studyText(color, 13), modifier = Modifier.padding(vertical = 4.dp))
            }
            Text(
                "Tonight starts with ${firstOpen.title.subject} at ${studyClock(firstOpen.begin)}." +
                    if (homeWifi.isBlank()) " Set your home Wi-Fi and it is noticed by itself." else "",
                style = studyText(color, 12, alpha = 0.75f),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                if (homeWifi.isBlank()) ActionChip("This Wi-Fi is home", color, icon = LauncherIcons.Home) { controller.rememberHomeWifi() }
                ActionChip("I'm home", color, filled = true, icon = LauncherIcons.Home) { controller.markHomeNow() }
            }
        }
        return
    }
    if (study.shiftAnswered()) return
    val delta = StudyShift.deltaFor(day.blocks, homeAt, lead)
    if (delta <= 0) return
    val latest = study.latestEnd()
    val plain = StudyShift.plan(day.blocks, homeAt, delta, latest, shorten = false)
    if (plain.moves.isEmpty()) return
    val cut = if (plain.endsLate) StudyShift.plan(day.blocks, homeAt, delta, latest, shorten = true) else null
    val victim = cut?.shortened
    HubCallout(color) {
        Text("Home ${studyClock(homeAt)} · shift tonight +$delta min?", style = studyText(color, 14, FontWeight.SemiBold))
        Text(
            if (cut != null && victim != null) {
                "Moved as planned it would end at ${studyClock(plain.lastEnd)}. Shortening ${victim.title.subject} by ${cut.shortenedMinutes} min ends at ${studyClock(cut.lastEnd)}."
            } else {
                "${plain.moves.size} blocks you have not started move together; their lengths stay. Ends at ${studyClock(plain.lastEnd)}."
            },
            style = studyText(color, 12, alpha = 0.75f),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
            ActionChip("Keep", color) { controller.keepEvening() }
            if (cut != null && victim != null) {
                ActionChip("Shift only", color) { controller.applyShift(plain) }
                ActionChip("Shorten ${victim.title.subject}", color, filled = true) { controller.applyShift(cut) }
            } else {
                ActionChip("Shift +$delta min", color, filled = true) { controller.applyShift(plain) }
            }
        }
    }
}

/** B3 in the hub: the class that just ended, present or absent. */
@Composable
private fun AttendanceCallout(controller: HomeController, day: StudyDay, now: Long, color: Color) {
    val ask by controller.data.settings.derive { get(LauncherSettings.StudyAttendance) }
    val answered by controller.data.study.attendance.collectAsState()
    if (!ask) return
    val due = attendanceDue(day, now, answered) ?: return
    HubCallout(color) {
        Text("${due.title.subject} ${studyClock(due.begin)} · were you there?", style = studyText(color, 14, FontWeight.SemiBold))
        Text("Absent writes a note for tonight's check-in; !abs in search counts them.", style = studyText(color, 12, alpha = 0.75f))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
            ActionChip("Absent", color, icon = LauncherIcons.Close) { controller.answerAttendance(due, false) }
            ActionChip("Present", color, filled = true, icon = LauncherIcons.Check) { controller.answerAttendance(due, true) }
        }
    }
}

@Composable
private fun HubCallout(color: Color, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(HomeTheme.accent.copy(alpha = 0.16f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) { content() }
}

/** The one-tap action the hub offers right where the thumb already is (it grew out of the pill under it). */
private class HubAction(val label: String, val icon: Int, val accent: Color?, val run: () -> Unit)

private fun mainAction(controller: HomeController, day: StudyDay, now: Long, hero: StudyEvent?, homeAt: Long?, focusKey: String?): HubAction? {
    val settings = controller.data.settings
    val firstOpen = day.blocks.firstOrNull { it.end > now && it.status == StudyStatus.Open }
    val lastClass = day.lastClassEnd
    if (settings.get(LauncherSettings.StudyArrival) && homeAt == null && lastClass != null && now >= lastClass && firstOpen != null) {
        return HubAction("I'm home", LauncherIcons.Home, null) { controller.markHomeNow() }
    }
    if (hero != null && hero.status == StudyStatus.Open) {
        val accent = eventColor(hero)
        val running = now >= hero.begin && now < hero.end
        return when {
            running && focusKey == hero.key -> HubAction("Mark ${hero.title.subject} done", LauncherIcons.Check, accent) { controller.studyStatus(hero, StudyStatus.Done) }
            running -> HubAction("Focus · ${studyDuration(hero.end - now)} left", LauncherIcons.Schedule, accent) { controller.studyFocus(hero) }
            hero.end <= now -> HubAction("Mark ${hero.title.subject} done", LauncherIcons.Check, accent) { controller.studyStatus(hero, StudyStatus.Done) }
            hero.begin - now <= 45 * 60_000L -> HubAction("Start ${hero.title.subject}", LauncherIcons.Schedule, accent) { controller.studyFocus(hero) }
            else -> HubAction("Ask Claude about ${hero.title.subject}", LauncherIcons.Sparkle, accent) { controller.askTemplate("Help me prepare this block.", hero) }
        }
    }
    // Nothing left to do today, or it is late: the evening check-in (D4).
    val late = java.time.LocalTime.now().isAfter(java.time.LocalTime.of(21, 30))
    if (firstOpen == null || late) return HubAction("Evening check-in", LauncherIcons.Chat, null) { controller.eveningCheckin() }
    return null
}

/** Templates, the note-or-question field, Claude, and the main action: the part the thumb reaches. */
@Composable
private fun HubBar(
    controller: HomeController,
    day: StudyDay,
    now: Long,
    hero: StudyEvent?,
    homeAt: Long?,
    focusKey: String?,
    color: Color,
    loaded: Boolean,
) {
    val templates by controller.data.settings.derive { get(LauncherSettings.StudyTemplates) }
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(controller.hubNoteFocus) {
        if (controller.hubNoteFocus) {
            kotlinx.coroutines.delay(220)
            runCatching { focus.requestFocus() }
            keyboard?.show()
            controller.hubNoteFocus = false
        }
    }
    fun saveNote() {
        val note = text
        if (note.isBlank()) return
        controller.addStudyNote(note) { ok -> if (ok && text == note) text = "" }
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (loaded) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                controller.classForPhoto(now)?.let { cls ->
                    ActionChip("Board · ${cls.title.subject}", color, icon = LauncherIcons.Camera) { controller.boardPhoto() }
                }
                ActionChip("Evening check-in", color, icon = LauncherIcons.Chat) { controller.eveningCheckin() }
                templates.forEach { line ->
                    val label = line.substringBefore('|').trim()
                    val prompt = line.substringAfter('|', "").trim()
                    if (label.isNotEmpty() && prompt.isNotEmpty()) {
                        ActionChip(label, color, icon = LauncherIcons.Sparkle) { controller.askTemplate(prompt, hero) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.1f))
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("Note for tonight, or ask Claude…", style = studyText(color, 14, alpha = 0.5f), maxLines = 1)
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 14.sp),
                        cursorBrush = SolidColor(color),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { saveNote() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .semantics { contentDescription = "Note or question" },
                    )
                }
                if (text.isNotBlank()) {
                    HubIcon(LauncherIcons.Add, "Save as a note for tonight's check-in", color, size = 38.dp) { saveNote() }
                }
            }
            Spacer(Modifier.width(8.dp))
            HubIcon(LauncherIcons.Sparkle, "Ask Claude", color, size = 46.dp) {
                val question = text.trim()
                when {
                    question.isNotEmpty() -> {
                        controller.askClaude(controller.withStudyHeader(question))
                        text = ""
                    }
                    hero != null -> controller.askTemplate("Help me with this block.", hero)
                    else -> controller.openAskClaude()
                }
            }
        }
        val action = if (loaded) mainAction(controller, day, now, hero, homeAt, focusKey) else null
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            MainHubButton(action)
        }
    }
}

@Composable
private fun MainHubButton(action: HubAction) {
    val fill = action.accent ?: HomeTheme.accent
    val ink = readableOn(fill)
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .squishClickable(onClick = action.run)
            .clip(RoundedCornerShape(50))
            .background(fill.copy(alpha = 0.92f))
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherIcon(action.icon, contentDescription = null, tint = ink, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(action.label, style = studyText(ink, 15, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
