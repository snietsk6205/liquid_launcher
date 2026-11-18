package dev.liquid.launcher.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import dev.liquid.launcher.core.data.StudyShift
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.liquid.launcher.core.common.NoticeKind
import dev.liquid.launcher.core.common.Notices
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.data.StudyDay
import dev.liquid.launcher.core.data.StudyEvent
import dev.liquid.launcher.core.data.StudyStatus
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.derive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A built-in widget type: its id, name, a line about it, and its default size in cells (width 0 = the whole row). */
internal data class WidgetType(val type: String, val name: String, val about: String, val w: Int, val h: Int)

/** Built-in widgets (Phase 3; 0.7.0 adds date, battery and the next block). */
internal val WidgetTypes = listOf(
    WidgetType("clock", "Clock", "Six designs, each with its own font, colours and glass", 0, 3),
    WidgetType("date", "Date", "The day and date in the clock's font", 0, 1),
    WidgetType("next", "Study block", "The block now or next, as under the clock", 0, 1),
    WidgetType("now", "Now card", "The study block running, its tasks, quick actions", 0, 3),
    WidgetType("today", "Today", "Blocks and tasks of the day", 0, 4),
    WidgetType("calendar", "Calendar", "The month on liquid glass with your events and what comes next; fits in search too", 0, 4),
    WidgetType("battery", "Battery", "Level as a ring, charging state", 2, 2),
)

private val clockTime = DateTimeFormatter.ofPattern("H:mm", Locale.getDefault())

private fun time(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(clockTime)

private fun minutes(millis: Long): String {
    val m = ((millis + 59_999) / 60_000).coerceAtLeast(0)
    return if (m >= 90) "${m / 60} h ${m % 60} min" else "$m min"
}

/** [time] and [minutes] for the study hub (StudyHub.kt). */
internal fun studyClock(millis: Long): String = time(millis)

internal fun studyDuration(millis: Long): String = minutes(millis)

/** [label] for the study hub. */
@Composable
internal fun studyText(color: Color, size: Int, weight: FontWeight = FontWeight.Normal, alpha: Float = 1f, struck: Boolean = false) =
    label(color, size, weight, alpha, struck)

/** The block's own colour when it has one that stands out, else the accent. */
internal fun eventColor(event: StudyEvent): Color = if (event.color != 0) Color(event.color or 0xFF000000.toInt()) else HomeTheme.accent

/** What a study widget needs from its page. */
internal class StudySurface(
    val glass: GlassConfig,
    val lightBackground: Boolean,
    val backdrop: Backdrop?,
    val interactive: Boolean,
    /** On a glass card (true) or straight on the wallpaper like the clock (false, the default since 0.9.10). */
    val boxed: Boolean = true,
) {
    val color: Color get() = when {
        !boxed -> if (lightBackground) Color(0xFF16181C) else Color.White
        glass.lightGlass(Surfaces.Menu, lightBackground) -> Color(0xFF16181C)
        else -> Color.White
    }
}

/** The glass card every study widget sits on; its buttons are glass on that glass (0.6.4). */
@Composable
private fun StudyCard(surface: StudySurface, controller: HomeController, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val layer = rememberLayerBackdrop()
    Box(
        Modifier
            .fillMaxSize()
            .padding(6.dp)
            .then(
                when {
                    // No box: on the wallpaper, as the clock is (P7, 0.9.10).
                    !surface.boxed -> Modifier
                    surface.backdrop != null -> Modifier.liquidGlass(surface.backdrop, shape, surface.glass.style(Surfaces.Menu, surface.lightBackground), exported = layer)
                    else -> Modifier.background(surface.color.copy(alpha = 0.12f), shape)
                }
            )
            .clip(shape)
            .then(if (onClick != null && surface.interactive) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // Unboxed, the buttons sample the wallpaper itself.
        ProvideControls(controller, surface.glass, if (!surface.boxed) surface.backdrop else if (surface.backdrop != null) layer else null) { content() }
    }
}

@Composable
private fun label(color: Color, size: Int, weight: FontWeight = FontWeight.Normal, alpha: Float = 1f, struck: Boolean = false) = TextStyle(
    fontFamily = HomeTheme.font, color = color.copy(alpha = alpha), fontSize = size.sp, fontWeight = weight,
    textDecoration = if (struck) TextDecoration.LineThrough else null,
)

/** Shown by every study widget until the companion is on and may read the calendar. */
@Composable
private fun StudySetup(surface: StudySurface, controller: HomeController) {
    StudyCard(surface, controller, onClick = { controller.setupStudy() }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text("Study companion", style = label(surface.color, 15, FontWeight.SemiBold))
            Text(
                "Shows the study blocks and tasks from your calendar. Tap to switch it on and allow calendar access.",
                style = label(surface.color, 12, alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A round tick box; ticking fills it and the check pops in with a small overshoot (0.7.0). */
@Composable
internal fun Tick(done: Boolean, color: Color, size: Dp = 18.dp) {
    val fill by animateFloatAsState(if (done) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = MotionScale.stiffness(700f)), label = "tickFill")
    val pop by animateFloatAsState(if (done) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = MotionScale.stiffness(600f)), label = "tickPop")
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                val s = 1f + 0.12f * (pop - fill).coerceIn(-1f, 1f)
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f + (0.9f - 0.14f) * fill)),
        contentAlignment = Alignment.Center,
    ) {
        if (done || fill > 0.01f) {
            LauncherIcon(
                LauncherIcons.Check, contentDescription = if (done) "Done" else null, tint = readableOn(color), size = size * 0.72f,
                modifier = Modifier.graphicsLayer {
                    scaleX = pop.coerceAtLeast(0f)
                    scaleY = pop.coerceAtLeast(0f)
                    alpha = fill
                },
            )
        }
    }
}

@Composable
internal fun TaskRow(text: String, done: Boolean, color: Color, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle) else Modifier)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tick(done, color)
        Spacer(Modifier.width(9.dp))
        Text(text, style = label(color, 13, alpha = if (done) 0.55f else 1f, struck = done), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A study button: a [GlassButton]; [filled] makes it the main one, in [accent] (the block's colour) when given. */
@Composable
internal fun ActionChip(text: String, color: Color, filled: Boolean = false, accent: Color? = null, icon: Int? = null, onClick: () -> Unit) {
    GlassButton(text, color, icon = icon, emphasis = if (filled) ControlEmphasis.Main else ControlEmphasis.Normal, accent = accent, onClick = onClick)
}

/**
 * The Now card: the block running now with the time left, its tasks to tick, and the quick
 * actions; between blocks the next one with a countdown; with nothing planned, one line.
 */
@Composable
internal fun NowCard(controller: HomeController, surface: StudySurface) {
    val study = controller.data.study
    val day by study.day.collectAsState()
    val loaded by study.loaded.collectAsState()
    val focus by study.focus.collectAsState()
    val tick = rememberMinuteTick()
    if (!loaded) {
        StudySetup(surface, controller)
        return
    }
    val now = remember(tick, day) { System.currentTimeMillis() }
    val color = surface.color
    val current = day.current(now)
    val next = day.next(now)
    StudyCard(surface, controller, onClick = { controller.run(LauncherAction.Today) }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // How many task lines fit under the header and above the buttons.
            val room = ((maxHeight - 96.dp) / 25.dp).toInt().coerceIn(0, 8)
            Column(Modifier.fillMaxSize()) {
                if (current != null) {
                    val accent = eventColor(current)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            current.title.text, modifier = Modifier.weight(1f),
                            style = label(color, 15, FontWeight.SemiBold, struck = current.status != StudyStatus.Open), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(if (current.status == StudyStatus.Open) minutes(current.end - now) + " left" else "done", style = label(color, 13, FontWeight.Medium))
                    }
                    Spacer(Modifier.height(6.dp))
                    // Time gone, as a bar in the block's colour.
                    val gone = ((now - current.begin).toFloat() / (current.end - current.begin).coerceAtLeast(1)).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f))) {
                        Box(Modifier.fillMaxWidth(gone).height(4.dp).clip(RoundedCornerShape(50)).background(accent))
                    }
                    Text("${time(current.begin)} – ${time(current.end)}", style = label(color, 11, alpha = 0.7f), modifier = Modifier.padding(top = 3.dp))
                    val tasks = current.tasks
                    Column(Modifier.weight(1f)) {
                        // Open tasks first, so what is left to do stays in view.
                        tasks.sortedBy { it.done }.take(room).forEach { task ->
                            TaskRow(task.text, task.done, color, surface.interactive) { controller.studyTick(current, task.index, !task.done) }
                        }
                        if (tasks.size > room && room > 0) Text("+ ${tasks.size - room} more", style = label(color, 11, alpha = 0.6f))
                    }
                    if (surface.interactive) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy((6 * themeGap).dp)) {
                            if (current.status == StudyStatus.Open) {
                                ActionChip("Done", color, filled = true, accent = accent) { controller.studyStatus(current, StudyStatus.Done) }
                                ActionChip("Skip", color) { controller.studyStatus(current, StudyStatus.Skipped) }
                            } else {
                                ActionChip("Undo", color) { controller.studyStatus(current, StudyStatus.Open) }
                            }
                            val focusing = focus?.eventKey == current.key
                            ActionChip(if (focusing) "Stop focus · " + minutes(now - (focus?.startedAt ?: now)) else "Focus", color, filled = focusing) { controller.studyFocus(current) }
                            ActionChip("Tell Claude", color) { controller.run(LauncherAction.TellClaude) }
                            ActionChip("Plan", color) { controller.run(LauncherAction.StudyPlan) }
                        }
                    }
                } else {
                    Text(
                        when {
                            next != null -> "Next: ${next.title.text}"
                            day.blocks.isNotEmpty() -> "Blocks done for today"
                            else -> "No study blocks today"
                        },
                        style = label(color, 15, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            next != null -> "${time(next.begin)} – ${time(next.end)} · in ${minutes(next.begin - now)}"
                            day.tomorrow != null -> "Tomorrow: ${day.tomorrow?.title?.text} at ${day.tomorrow?.let { time(it.begin) }}"
                            else -> LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
                        },
                        style = label(color, 12, alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Column(Modifier.weight(1f).padding(top = 4.dp)) {
                        val open = day.overdue + day.tasks.filter { it.status == StudyStatus.Open }
                        open.take(room).forEach { task ->
                            TaskRow(task.title.text, false, color, surface.interactive) { controller.studyStatus(task, StudyStatus.Done) }
                        }
                        if (open.size > room && room > 0) Text("+ ${open.size - room} more", style = label(color, 11, alpha = 0.6f))
                    }
                    if (surface.interactive) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy((6 * themeGap).dp)) {
                            ActionChip("Today", color) { controller.run(LauncherAction.Today) }
                            ActionChip("Tell Claude", color) { controller.run(LauncherAction.TellClaude) }
                            ActionChip("Plan", color) { controller.run(LauncherAction.StudyPlan) }
                        }
                    }
                }
            }
        }
    }
}

/** The day at a glance as a widget: blocks in time order, then tasks. Tap opens the Today panel. */
@Composable
internal fun TodayWidget(controller: HomeController, surface: StudySurface) {
    val study = controller.data.study
    val day by study.day.collectAsState()
    val loaded by study.loaded.collectAsState()
    val tick = rememberMinuteTick()
    if (!loaded) {
        StudySetup(surface, controller)
        return
    }
    val now = remember(tick, day) { System.currentTimeMillis() }
    val color = surface.color
    StudyCard(surface, controller, onClick = { controller.run(LauncherAction.Today) }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val room = ((maxHeight - 24.dp) / 25.dp).toInt().coerceIn(1, 14)
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Today", modifier = Modifier.weight(1f), style = label(color, 14, FontWeight.SemiBold))
                    val open = day.openTasks
                    Text(if (open > 0) "$open to do" else "all done", style = label(color, 12, alpha = 0.7f))
                }
                val rows = day.blocks.map { it to true } + (day.overdue + day.tasks).map { it to false }
                if (rows.isEmpty()) Text("Nothing planned", style = label(color, 12, alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
                rows.take(room).forEach { (event, isBlock) ->
                    val done = event.status != StudyStatus.Open
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isBlock) {
                            val running = now >= event.begin && now < event.end
                            Text(time(event.begin), modifier = Modifier.width(42.dp), style = label(color, 12, if (running) FontWeight.Bold else FontWeight.Normal, alpha = if (running) 1f else 0.7f))
                            Box(Modifier.size(7.dp).clip(CircleShape).background(eventColor(event)))
                            Spacer(Modifier.width(7.dp))
                        } else {
                            Box(
                                if (surface.interactive) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    controller.studyStatus(event, if (done) StudyStatus.Open else StudyStatus.Done)
                                } else Modifier
                            ) { Tick(done, color, 16.dp) }
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(event.title.text, style = label(color, 13, alpha = if (done) 0.55f else 1f, struck = done), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (rows.size > room) Text("+ ${rows.size - room} more", style = label(color, 11, alpha = 0.6f))
            }
        }
    }
}

/** How the Today panel shows blocks and tomorrow (Settings → Study companion, 0.6.4). */
internal data class TodayPrefs(val cards: String, val buttonsEnd: Boolean, val tomorrow: String)

/** A thin glass rim for the panes inside the Today panel, as bright as the buttons' edge light. */
@Composable
private fun paneRim(color: Color): Modifier {
    val rim = LocalControls.current?.look?.rim ?: 0.7f
    if (rim < 0.01f) return Modifier
    val edge = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.45f * rim),
        0.5f to Color.White.copy(alpha = 0.05f * rim),
        1f to Color.White.copy(alpha = 0.22f * rim),
    )
    return Modifier.border(BorderStroke(0.8.dp, edge), RoundedCornerShape(20.dp))
}

/**
 * One block of today: a tap on the title opens or closes it ([TodayPrefs.cards]). Open, it shows
 * every task and the notes in full; the buttons line up on [TodayPrefs.buttonsEnd]'s side with
 * the main one nearest that edge.
 */
@Composable
internal fun BlockCard(
    block: StudyEvent, now: Long, prefs: TodayPrefs, focus: dev.liquid.launcher.core.data.StudyFocus?, color: Color, controller: HomeController,
    startOpen: Boolean = false,
) {
    val running = now >= block.begin && now < block.end
    val done = block.status != StudyStatus.Open
    val accent = eventColor(block)
    val notes = block.notes
    val canOpen = prefs.cards != "full"
    var open by remember(block.key, prefs.cards, startOpen) { mutableStateOf(startOpen || prefs.cards == "full" || (prefs.cards == "compact" && running)) }
    val showBody = open || prefs.cards == "preview"
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(color.copy(alpha = if (running) 0.14f else 0.06f))
            .then(paneRim(color))
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = MotionScale.stiffness(520f)))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (canOpen) Modifier.clickable(interactionSource = null, indication = null) { open = !open } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(
                block.title.text, modifier = Modifier.weight(1f),
                style = label(color, 14, FontWeight.SemiBold, alpha = if (done) 0.6f else 1f, struck = block.status == StudyStatus.Skipped),
                maxLines = if (open) 4 else 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    block.status == StudyStatus.Done -> "✓"
                    block.status == StudyStatus.Skipped -> "skipped"
                    running -> minutes(block.end - now) + " left"
                    else -> ""
                },
                style = label(color, 12, FontWeight.Medium),
            )
            if (canOpen) {
                val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
                Spacer(Modifier.width(4.dp))
                LauncherIcon(LauncherIcons.ArrowDown, contentDescription = if (open) "Close" else "Open", tint = color.copy(alpha = 0.7f), size = 18.dp, modifier = Modifier.graphicsLayer { rotationZ = turn })
            }
        }
        val tasks = block.tasks
        val doneTasks = tasks.count { it.done }
        Text(
            "${time(block.begin)} – ${time(block.end)}" + if (tasks.isNotEmpty()) " · $doneTasks/${tasks.size} steps" else "",
            style = label(color, 11, alpha = 0.7f), modifier = Modifier.padding(start = 17.dp),
        )
        if (running && !done) {
            val gone = ((now - block.begin).toFloat() / (block.end - block.begin).coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(Modifier.padding(start = 17.dp, top = 4.dp, bottom = 2.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f))) {
                Box(Modifier.fillMaxWidth(gone).height(3.dp).clip(RoundedCornerShape(50)).background(accent))
            }
        }
        if (showBody) {
            tasks.forEach { task ->
                TaskRow(task.text, task.done, color, true) { controller.studyTick(block, task.index, !task.done) }
            }
            if (notes.isNotBlank()) {
                Text(
                    notes, style = label(color, 12, alpha = 0.78f),
                    maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .then(if (canOpen) Modifier.clickable(interactionSource = null, indication = null) { open = !open } else Modifier),
                )
            }
            val buttons = ArrayList<@Composable () -> Unit>()
            if (block.status == StudyStatus.Open) {
                buttons += { ActionChip("Done", color, filled = running, accent = accent) { controller.studyStatus(block, StudyStatus.Done) } }
                buttons += { ActionChip("Skip", color) { controller.studyStatus(block, StudyStatus.Skipped) } }
            } else {
                buttons += { ActionChip("Undo", color) { controller.studyStatus(block, StudyStatus.Open) } }
            }
            if ((running && block.status == StudyStatus.Open) || focus?.eventKey == block.key) {
                val focusing = focus?.eventKey == block.key
                buttons += {
                    ActionChip(if (focusing) "Stop focus · " + minutes(now - (focus?.startedAt ?: now)) else "Focus", color, filled = focusing, accent = accent) { controller.studyFocus(block) }
                }
            }
            block.link?.let { link -> buttons += { ActionChip("Open link", color, icon = LauncherIcons.Web) { controller.openStudyLink(link) } } }
            // C3: a block that ended without being done: ask Claude where to put it, pre-filled.
            if (block.status != StudyStatus.Done && block.end <= now) {
                buttons += {
                    ActionChip("Reschedule", color, icon = LauncherIcons.Sparkle) {
                        controller.askTemplate(
                            "I could not do this block today. Suggest where to fit it in the next days without touching fixed dates, " +
                                "and say what to drop if the week is full.",
                            block,
                        )
                    }
                }
            }
            // Help with this block from Claude: its title, steps and notes as the start of a chat (0.7.2, D109).
            if (block.status == StudyStatus.Open && controller.data.settings.get(dev.liquid.launcher.core.settings.LauncherSettings.SearchClaude) != "off") {
                buttons += { ActionChip("Ask Claude", color, icon = LauncherIcons.Sparkle) { controller.askClaudeAbout(block) } }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy((6 * themeGap).dp, if (prefs.buttonsEnd) Alignment.End else Alignment.Start),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The main button sits nearest the edge the buttons line up on: under the thumb on the right.
                (if (prefs.buttonsEnd) buttons.asReversed() else buttons).forEach { it() }
            }
        }
    }
}

/** Tomorrow: the first block, opening to all of tomorrow's blocks and tasks; each block opens to its steps and notes. */
@Composable
internal fun TomorrowSection(day: StudyDay, prefs: TodayPrefs, color: Color) {
    var open by remember(prefs.tomorrow) { mutableStateOf(prefs.tomorrow == "all") }
    val blocks = day.tomorrowBlocks
    val more = blocks.size - 1 + day.tomorrowTasks.size
    Column(Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = 0.85f, stiffness = MotionScale.stiffness(520f)))) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 2.dp)
                .clickable(interactionSource = null, indication = null) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Tomorrow · " + LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("EEEE d", Locale.getDefault())),
                style = label(color, 12, FontWeight.SemiBold, alpha = 0.7f), modifier = Modifier.weight(1f),
            )
            if (!open && more > 0) Text("+$more", style = label(color, 11, alpha = 0.6f))
            val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
            LauncherIcon(LauncherIcons.ArrowDown, contentDescription = if (open) "Close tomorrow" else "Open tomorrow", tint = color.copy(alpha = 0.6f), size = 18.dp, modifier = Modifier.graphicsLayer { rotationZ = turn })
        }
        if (!open) {
            blocks.firstOrNull()?.let { first ->
                Text(
                    "${time(first.begin)}  ${first.title.text}", style = label(color, 13, alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(interactionSource = null, indication = null) { open = true },
                )
            } ?: Text("${day.tomorrowTasks.size} tasks", style = label(color, 13, alpha = 0.85f))
        } else {
            blocks.forEach { block -> TomorrowBlock(block, color) }
            day.tomorrowTasks.forEach { task ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tick(false, color, 16.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(task.title.text, style = label(color, 13, alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A block of tomorrow, read only: time and title; a tap shows its steps and notes. */
@Composable
private fun TomorrowBlock(block: StudyEvent, color: Color) {
    var open by remember(block.key) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = if (open) 0.07f else 0f))
            .clickable(interactionSource = null, indication = null) { open = !open }
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = MotionScale.stiffness(520f)))
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(time(block.begin), modifier = Modifier.width(44.dp), style = label(color, 12, alpha = 0.75f))
            Box(Modifier.size(7.dp).clip(CircleShape).background(eventColor(block)))
            Spacer(Modifier.width(7.dp))
            Text(block.title.text, modifier = Modifier.weight(1f), style = label(color, 13, alpha = 0.9f), maxLines = if (open) 3 else 1, overflow = TextOverflow.Ellipsis)
        }
        if (open) {
            Text("${time(block.begin)} – ${time(block.end)}", style = label(color, 11, alpha = 0.65f), modifier = Modifier.padding(start = 58.dp))
            block.tasks.forEach { task ->
                Row(Modifier.padding(start = 44.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tick(task.done, color, 14.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(task.text, style = label(color, 12, alpha = 0.85f))
                }
            }
            if (block.notes.isNotBlank()) Text(block.notes, style = label(color, 12, alpha = 0.72f), modifier = Modifier.padding(start = 44.dp, top = 3.dp))
        }
    }
}

@Composable
internal fun SectionLabel(text: String, color: Color) {
    Text(text, style = label(color, 12, FontWeight.SemiBold, alpha = 0.7f), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

/** What "Tell Claude" shares: the day as planned and as done, ready to add a note to. */
internal fun studyReport(day: StudyDay): String {
    val out = StringBuilder()
    out.append("Study log, ").append(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))).append("\n")
    fun mark(status: StudyStatus) = when (status) {
        StudyStatus.Done -> "✓"
        StudyStatus.Skipped -> "✗"
        StudyStatus.Open -> "☐"
    }
    if (day.blocks.isNotEmpty()) out.append("\nBlocks:\n")
    for (block in day.blocks) {
        val tasks = block.tasks
        out.append(mark(block.status)).append(' ').append(block.title.text).append(" (").append(time(block.begin)).append("–").append(time(block.end)).append(")")
        if (tasks.isNotEmpty()) out.append(", ").append(tasks.count { it.done }).append("/").append(tasks.size).append(" tasks")
        if (block.note.focusMinutes > 0) out.append(", focus ").append(block.note.focusMinutes).append(" min")
        out.append("\n")
        for (task in tasks) out.append("   ").append(if (task.done) "☑ " else "☐ ").append(task.text).append("\n")
    }
    if (day.tasks.isNotEmpty()) out.append("\nTasks:\n")
    for (task in day.tasks) out.append(mark(task.status)).append(' ').append(task.title.text).append("\n")
    if (day.overdue.isNotEmpty()) out.append("\nStill open from earlier:\n")
    for (task in day.overdue) out.append("☐ ").append(task.title.text).append("\n")
    out.append("\nNote: ")
    return out.toString()
}

/**
 * Glass notices for the study companion: a block starting, a block about to end, and once a
 * day the number of tasks. Only looks at the clock while the home screen is composed. Each one
 * is shown once ([StudyCalendar.firstToday] remembers it across restarts of the launcher).
 * The same minute tick stops a focus timer whose block has ended.
 */
@Composable
internal fun StudyNotices(controller: HomeController) {
    val study = controller.data.study
    val day by study.day.collectAsState()
    val loaded by study.loaded.collectAsState()
    val tick = rememberMinuteTick()
    LaunchedEffect(tick, day, loaded) {
        if (!loaded) return@LaunchedEffect
        study.finishFocusIfOver()
        val now = System.currentTimeMillis()
        val lead = controller.data.settings.get(LauncherSettings.StudyNoticeLead) * 60_000L
        val current = day.current(now)
        if (current != null && current.status == StudyStatus.Open) {
            if (now - current.begin < 3 * 60_000 && study.firstToday("start:" + current.key)) {
                val tasks = current.tasks.size
                Notices.post(NoticeKind.Study, "Now: ${current.title.text}", "${time(current.begin)} – ${time(current.end)}" + if (tasks > 0) " · $tasks tasks" else "", LauncherAction.Today.encode())
            }
            if (lead > 0 && current.end - now in 1..lead && study.firstToday("end:" + current.key)) {
                Notices.post(NoticeKind.Study, "${minutes(current.end - now)} left: ${current.title.subject}", "Tap to tick off what you did", LauncherAction.Today.encode())
            }
        }
        // A2: home after the last class (Wi-Fi, charger). The manual chip is in the hub.
        val sign = study.homeSign(now)
        if (sign != null) {
            val at = study.markHome(now, sign)
            val delta = StudyShift.deltaFor(day.blocks, now, controller.data.settings.get(LauncherSettings.StudyShiftLead))
            Notices.post(
                NoticeKind.Study, "Home $at",
                if (delta > 0) "Shift tonight +$delta min? Tap to decide" else "Noted on tonight's first block",
                LauncherAction.Today.encode(),
            )
        }
        if (day.openTasks > 0 && study.firstToday("tasks")) {
            val overdue = day.overdue.size
            Notices.post(NoticeKind.Task, "${day.openTasks} tasks to do today", if (overdue > 0) "$overdue of them from earlier days" else null, LauncherAction.Today.encode())
        }
    }
}

// ---- the study glance: the current block on the clock and in the pill, without a page of its own (0.6.3, D92) ----

/** What the clock line and the pill's study item say about the day at one moment. */
internal sealed interface StudyGlance {
    /** A block is running and not marked yet. */
    data class Running(val block: StudyEvent, val minutesLeft: Int, val progress: Float) : StudyGlance

    /** The next unmarked block today. */
    data class Next(val block: StudyEvent, val minutesUntil: Int) : StudyGlance

    /** A block of today has ended and is neither done nor skipped ([count] such blocks in all). 0.6.4. */
    data class Unmarked(val block: StudyEvent, val count: Int) : StudyGlance

    /** No block left today, but [open] tasks (today's and overdue) are not ticked. */
    data class Tasks(val open: Int) : StudyGlance

    /** Nothing left today: tomorrow's first block. 0.6.4. */
    data class Tomorrow(val block: StudyEvent) : StudyGlance

    /** Morning (A6, 0.9.4): leave by [leaveAt] for the day's first class; [minutesLeft] may be negative (late). */
    data class LeaveBy(val firstClass: StudyEvent, val leaveAt: Long, val minutesLeft: Int) : StudyGlance

    /** A class ended a moment ago (B3, 0.9.4): was Med there? */
    data class Attendance(val cls: StudyEvent) : StudyGlance

    /** Just home (A5, 0.9.4): rest until [restEnd], then [first]. [progress] is the rest gone, 0..1. */
    data class Rest(val first: StudyEvent, val restEnd: Long, val progress: Float) : StudyGlance

    /** Wind-down (D4, 0.9.4): the evening check-in comes first. */
    data class WindDown(val done: Int, val total: Int) : StudyGlance
}

/** The class whose attendance to ask about (B3): ended 5 min to 3 h ago and not answered yet, the latest first. */
internal fun attendanceDue(day: StudyDay, now: Long, answered: Set<String>): StudyEvent? =
    day.classes.filter { it.end + 5 * 60_000L <= now && now - it.end <= 3 * 60 * 60_000L && it.key !in answered }.maxByOrNull { it.end }

/**
 * The glance for [day] at [now] (0.6.3, D92; 0.6.4, D93). In order: the block running; the next block
 * when it starts within the hour; a block that ended without Done or Skip; the next block later today;
 * the tasks left; tomorrow's first block ([withTomorrow]). Null only when there is nothing at all, so
 * the line under the clock does not vanish just because the day's blocks are over.
 */
internal fun studyGlance(
    day: StudyDay,
    now: Long,
    withTomorrow: Boolean = true,
    /** Morning commute in minutes for "Leave by" (A6); 0: off. */
    commuteMinutes: Int = 0,
    /** Classes already answered for "present?" (B3); null: off. */
    answered: Set<String>? = null,
    /** When Med got home today (A5), and how long to rest then; 0: no rest. */
    homeAt: Long? = null,
    restMinutes: Int = 0,
    /** It is wind-down time and the check-in was not sent yet (D4). */
    windDown: Boolean = false,
): StudyGlance? {
    if (commuteMinutes > 0) {
        val first = day.classes.minByOrNull { it.begin }
        if (first != null) {
            val leaveAt = first.begin - commuteMinutes * 60_000L
            // From an hour before leaving until a quarter of an hour after (by then you are on the way).
            if (now >= leaveAt - 60 * 60_000L && now <= leaveAt + 15 * 60_000L && now < first.begin) {
                return StudyGlance.LeaveBy(first, leaveAt, Math.floorDiv(leaveAt - now, 60_000L).toInt())
            }
        }
    }
    val running = day.blocks.lastOrNull { now >= it.begin && now < it.end && it.status == StudyStatus.Open }
    if (running != null) {
        val length = (running.end - running.begin).coerceAtLeast(1)
        return StudyGlance.Running(
            block = running,
            minutesLeft = ((running.end - now + 59_999) / 60_000).toInt(),
            progress = ((now - running.begin).toFloat() / length).coerceIn(0f, 1f),
        )
    }
    if (answered != null) attendanceDue(day, now, answered)?.let { return StudyGlance.Attendance(it) }
    if (homeAt != null && restMinutes > 0 && now >= homeAt) {
        val restEnd = homeAt + restMinutes * 60_000L
        val first = day.blocks.firstOrNull { it.end > now && it.status == StudyStatus.Open && it.begin > now }
        if (now < restEnd && first != null) {
            return StudyGlance.Rest(first, restEnd, ((now - homeAt).toFloat() / (restEnd - homeAt)).coerceIn(0f, 1f))
        }
    }
    if (windDown) return StudyGlance.WindDown(day.blocksDone, day.blocks.size)
    val next = day.blocks.firstOrNull { it.begin > now && it.status == StudyStatus.Open }
    val nextGlance = next?.let { StudyGlance.Next(it, ((it.begin - now + 59_999) / 60_000).toInt()) }
    if (nextGlance != null && nextGlance.minutesUntil <= 60) return nextGlance
    val unmarked = day.unmarked(now)
    if (unmarked.isNotEmpty()) return StudyGlance.Unmarked(unmarked.first(), unmarked.size)
    if (nextGlance != null) return nextGlance
    if (day.openTasks > 0) return StudyGlance.Tasks(day.openTasks)
    if (withTomorrow) day.tomorrow?.let { return StudyGlance.Tomorrow(it) }
    return null
}

/** The glance as one short line. [short] drops words for the pill. */
internal fun glanceText(glance: StudyGlance, short: Boolean = false): String = when (glance) {
    is StudyGlance.Running -> glance.block.title.subject + " · " + minutes(glance.minutesLeft * 60_000L) + if (short) "" else " left"
    is StudyGlance.Next -> when {
        glance.minutesUntil <= 60 -> (if (short) "" else "Next: ") + glance.block.title.subject + " in " + minutes(glance.minutesUntil * 60_000L)
        else -> (if (short) "" else "Next: ") + glance.block.title.subject + " at " + time(glance.block.begin)
    }
    is StudyGlance.Unmarked -> when {
        short -> "Mark " + glance.block.title.subject
        glance.count > 1 -> glance.block.title.subject + " + ${glance.count - 1} · not marked yet"
        else -> glance.block.title.subject + " · not marked yet"
    }
    is StudyGlance.Tasks -> if (short) "${glance.open}" else if (glance.open == 1) "1 task left today" else "${glance.open} tasks left today"
    is StudyGlance.Tomorrow -> if (short) "Tomorrow " + time(glance.block.begin) else "Tomorrow " + time(glance.block.begin) + " · " + glance.block.title.subject
    is StudyGlance.LeaveBy -> when {
        glance.minutesLeft > 0 && short -> "Leave " + time(glance.leaveAt)
        glance.minutesLeft > 0 -> "Leave by " + time(glance.leaveAt) + " · " + glance.firstClass.title.subject + " at " + time(glance.firstClass.begin)
        short -> "Leave now"
        else -> "Leave now · " + glance.firstClass.title.subject + " at " + time(glance.firstClass.begin)
    }
    is StudyGlance.Attendance -> if (short) "Present?" else glance.cls.title.subject + " " + time(glance.cls.begin) + " · present?"
    is StudyGlance.Rest -> if (short) "Rest " + minutes(glance.restEnd - System.currentTimeMillis().coerceAtMost(glance.restEnd)) else
        "Rest until " + time(glance.restEnd) + " · then " + glance.first.title.subject
    is StudyGlance.WindDown -> if (short) "Check-in" else "Evening check-in" + if (glance.total > 0) " · ${glance.done}/${glance.total} blocks done" else ""
}

/** The glance now, or null when the study companion is off, cannot read the calendar, or has nothing to say. */
@Composable
internal fun rememberStudyGlance(controller: HomeController): StudyGlance? {
    val study = controller.data.study
    val loaded by study.loaded.collectAsState()
    val day by study.day.collectAsState()
    val tick = rememberMinuteTick()
    val withTomorrow by controller.data.settings.derive { get(LauncherSettings.StudyClockLine) != "today" }
    val commute by controller.data.settings.derive { if (get(LauncherSettings.StudyLeaveBy)) get(LauncherSettings.StudyCommute) else 0 }
    val askAttendance by controller.data.settings.derive { get(LauncherSettings.StudyAttendance) }
    val answered by study.attendance.collectAsState()
    val homeAt by study.homeAt.collectAsState()
    val rest by controller.data.settings.derive { get(LauncherSettings.StudyRest) }
    val windDownAt by controller.data.settings.derive { get(LauncherSettings.StudyWindDown).toIntOrNull() }
    val checkedIn by study.checkedIn.collectAsState()
    return remember(loaded, day, tick, withTomorrow, commute, askAttendance, answered, homeAt, rest, windDownAt, checkedIn) {
        val now = System.currentTimeMillis()
        if (loaded) {
            studyGlance(
                day, now, withTomorrow, commute, if (askAttendance) answered else null,
                homeAt = homeAt, restMinutes = rest, windDown = !checkedIn && isWindDown(windDownAt),
            )
        } else {
            null
        }
    }
}

/**
 * A dot in the block's colour with the time gone as a ring around it (running); a plain dot for the
 * next block; a full ring for a block that ended unmarked; a faint dot for tomorrow; a ring for tasks.
 */
@Composable
internal fun StudyRing(glance: StudyGlance, color: Color, size: Dp) {
    val accent = when (glance) {
        is StudyGlance.Running -> eventColor(glance.block)
        is StudyGlance.Next -> eventColor(glance.block)
        is StudyGlance.Unmarked -> eventColor(glance.block)
        is StudyGlance.Tomorrow -> eventColor(glance.block)
        is StudyGlance.Tasks -> color
        // Red from 10 minutes before leaving (A6).
        is StudyGlance.LeaveBy -> if (glance.minutesLeft <= 10) LeaveRed else color
        is StudyGlance.Attendance -> color
        is StudyGlance.Rest -> eventColor(glance.first)
        is StudyGlance.WindDown -> color
    }
    // The ring grows smoothly from one minute to the next instead of in steps.
    val shownProgress by animateFloatAsState(if (glance is StudyGlance.Running) glance.progress else 0f, androidx.compose.animation.core.tween(700), label = "ring")
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.14f
        val radius = (this.size.minDimension - stroke) / 2f
        when (glance) {
            is StudyGlance.Running -> {
                drawCircle(color.copy(alpha = 0.22f), radius = radius, style = Stroke(stroke))
                drawArc(
                    accent, startAngle = -90f, sweepAngle = 360f * shownProgress, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                    style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
                drawCircle(accent, radius = radius * 0.42f)
            }
            is StudyGlance.Unmarked -> {
                drawCircle(accent, radius = radius, style = Stroke(stroke))
                drawCircle(accent.copy(alpha = 0.35f), radius = radius * 0.5f)
            }
            is StudyGlance.Tasks -> drawCircle(accent.copy(alpha = 0.8f), radius = radius, style = Stroke(stroke))
            is StudyGlance.Tomorrow -> drawCircle(accent.copy(alpha = 0.5f), radius = radius * 0.5f)
            is StudyGlance.Next -> drawCircle(accent, radius = radius * 0.55f)
            is StudyGlance.LeaveBy -> {
                // The hour before leaving, running out.
                val left = (glance.minutesLeft / 60f).coerceIn(0f, 1f)
                drawCircle(color.copy(alpha = 0.22f), radius = radius, style = Stroke(stroke))
                drawArc(
                    accent, startAngle = -90f, sweepAngle = 360f * left, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                    style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
                if (glance.minutesLeft <= 10) drawCircle(accent, radius = radius * 0.45f)
            }
            is StudyGlance.Attendance -> drawCircle(accent.copy(alpha = 0.85f), radius = radius, style = Stroke(stroke))
            is StudyGlance.Rest -> {
                // The rest filling up, softly.
                drawCircle(color.copy(alpha = 0.18f), radius = radius, style = Stroke(stroke))
                drawArc(
                    accent.copy(alpha = 0.75f), startAngle = -90f, sweepAngle = 360f * glance.progress, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                    style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            is StudyGlance.WindDown -> {
                val f = if (glance.total > 0) glance.done.toFloat() / glance.total else 0f
                drawCircle(color.copy(alpha = 0.22f), radius = radius, style = Stroke(stroke))
                if (f > 0f) drawArc(
                    accent, startAngle = -90f, sweepAngle = 360f * f, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                    style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * The line under a clock widget: the block running now with a ring for the time gone, else the
 * next block, else the tasks left. Tap opens Today (ticks, Done, Skip, Focus). No long press of its own:
 * holding the clock widget already means "edit and move it". Uses the clock's colour and shadow so it
 * reads as part of the clock.
 */
@Composable
internal fun StudyLine(
    glance: StudyGlance,
    controller: HomeController,
    color: Color,
    shadow: androidx.compose.ui.graphics.Shadow?,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    val focus by controller.data.study.focus.collectAsState()
    val focusing = glance is StudyGlance.Running && focus?.eventKey == glance.block.key
    // A new kind of line (the block starts, ends, the next one comes) slides in; the minutes just change (0.7.0).
    androidx.compose.animation.AnimatedContent(
        targetState = glance,
        contentKey = { glanceKey(it) },
        transitionSpec = {
            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 60)) +
                androidx.compose.animation.slideInVertically(spring(dampingRatio = 0.85f, stiffness = MotionScale.stiffness(380f))) { it / 2 }) togetherWith
                (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)) +
                    androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(200)) { -it / 2 })
        },
        modifier = modifier,
        label = "clockLine",
    ) { shown -> StudyLineRow(shown, controller, color, shadow, interactive, focusing && shown is StudyGlance.Running) }
}

/** What makes two glances "the same line" for the animation: their kind and block. */
private fun glanceKey(glance: StudyGlance): String = when (glance) {
    is StudyGlance.Running -> "run:" + glance.block.key
    is StudyGlance.Next -> "next:" + glance.block.key
    is StudyGlance.Unmarked -> "unmarked:" + glance.block.key
    is StudyGlance.Tasks -> "tasks"
    is StudyGlance.Tomorrow -> "tomorrow:" + glance.block.key
    is StudyGlance.LeaveBy -> "leave:" + glance.firstClass.key
    is StudyGlance.Attendance -> "attendance:" + glance.cls.key
    is StudyGlance.Rest -> "rest:" + glance.first.key
    is StudyGlance.WindDown -> "winddown"
}

/** True from [startMinute] (minutes after midnight; null: never) until five in the morning (D4). */
internal fun isWindDown(startMinute: Int?, time: java.time.LocalTime = java.time.LocalTime.now()): Boolean {
    if (startMinute == null) return false
    val m = time.hour * 60 + time.minute
    return m >= startMinute || m < 5 * 60
}

/** The red of "leave now" (A6) and of an exam three days away (B1). */
internal val LeaveRed = Color(0xFFFF6B6B)

@Composable
private fun StudyLineRow(
    glance: StudyGlance,
    controller: HomeController,
    color: Color,
    shadow: androidx.compose.ui.graphics.Shadow?,
    interactive: Boolean,
    focusing: Boolean,
) {
    val bounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    Row(
        Modifier
            .height(30.dp)
            .onGloballyPositioned { bounds[0] = it.boundsInRoot() }
            .clip(RoundedCornerShape(50))
            .then(
                if (interactive) Modifier.clickable(
                    interactionSource = null,
                    indication = null,
                    // The hub grows out of the line itself (0.9.1).
                    onClick = { controller.openToday(bounds[0]) },
                ) else Modifier
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudyRing(glance, color, 15.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            glanceText(glance) + if (focusing) " · focus" else "",
            style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium, shadow = shadow),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        // One action on the line itself (0.9.1): no need to open anything for the most likely next tap.
        val wanted by controller.data.settings.derive { get(LauncherSettings.StudyClockAction) }
        val block = when (glance) {
            is StudyGlance.Running -> glance.block
            is StudyGlance.Unmarked -> glance.block
            is StudyGlance.Next -> glance.block
            else -> null
        }
        val action: Triple<Int, String, () -> Unit>? = when {
            !interactive || block == null || wanted == "none" -> null
            wanted == "claude" -> Triple(LauncherIcons.Sparkle, "Ask Claude about this block") { controller.askClaudeAbout(block) }
            glance is StudyGlance.Unmarked && wanted != "focus" -> Triple(LauncherIcons.Check, "Mark the block done") { controller.studyStatus(block, StudyStatus.Done) }
            glance is StudyGlance.Running && wanted == "done" -> Triple(LauncherIcons.Check, "Mark the block done") { controller.studyStatus(block, StudyStatus.Done) }
            glance is StudyGlance.Running -> Triple(LauncherIcons.Schedule, if (focusing) "Stop the focus timer" else "Start the focus timer") { controller.studyFocus(block) }
            // A5: after the rest, the block is one tap away (it starts the focus timer).
            glance is StudyGlance.Next && glance.minutesUntil <= 45 -> Triple(LauncherIcons.Schedule, "Start ${block.title.subject} now") { controller.studyFocus(block) }
            else -> null
        }
        if (glance is StudyGlance.WindDown && interactive) {
            Spacer(Modifier.width(8.dp))
            LineButton(LauncherIcons.Chat, "Send the evening check-in", color, strong = false) { controller.eveningCheckin() }
        }
        if (glance is StudyGlance.Attendance && interactive) {
            // B3: ✓ present, ✗ absent (a "Note: absent …" for tonight's check-in).
            Spacer(Modifier.width(8.dp))
            for ((present, icon) in listOf(true to LauncherIcons.Check, false to LauncherIcons.Close)) {
                Box(Modifier.padding(start = 4.dp)) {
                    LineButton(icon, if (present) "I was there" else "I was absent", color, strong = false) { controller.answerAttendance(glance.cls, present) }
                }
            }
        }
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            LineButton(action.first, action.second, color, strong = focusing, onClick = action.third)
        }
    }
}

/** A round button on the line under the clock: the theme's round button (W3), else a faint circle. */
@Composable
private fun LineButton(icon: Int, description: String, color: Color, strong: Boolean, onClick: () -> Unit) {
    if (currentTheme != null) {
        GlassButton(null, color, Modifier.size(30.dp), icon = icon, contentDescription = description, onClick = onClick)
        return
    }
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (strong) 0.34f else 0.18f))
            .squishClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LauncherIcon(icon, contentDescription = description, tint = color, size = 16.dp)
    }
}
