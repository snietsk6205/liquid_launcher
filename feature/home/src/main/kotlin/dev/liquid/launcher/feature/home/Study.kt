package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Built-in widgets beyond the clock: their type, name and default size in cells (width 0 = the whole row). */
internal val WidgetTypes = listOf(
    Triple("clock", "Clock", 3),
    Triple("now", "Now: the study block running, its tasks, quick actions", 3),
    Triple("today", "Today: blocks and tasks of the day", 4),
)

private val clockTime = DateTimeFormatter.ofPattern("H:mm", Locale.getDefault())

private fun time(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(clockTime)

private fun minutes(millis: Long): String {
    val m = ((millis + 59_999) / 60_000).coerceAtLeast(0)
    return if (m >= 90) "${m / 60} h ${m % 60} min" else "$m min"
}

/** The block's own colour when it has one that stands out, else the accent. */
private fun eventColor(event: StudyEvent): Color = if (event.color != 0) Color(event.color or 0xFF000000.toInt()) else HomeTheme.accent

/** What a study widget needs from its page. */
internal class StudySurface(val glass: GlassConfig, val lightBackground: Boolean, val backdrop: Backdrop?, val interactive: Boolean) {
    val color: Color get() = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
}

/** The glass card every study widget sits on. */
@Composable
private fun StudyCard(surface: StudySurface, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .fillMaxSize()
            .padding(6.dp)
            .then(
                if (surface.backdrop != null) Modifier.liquidGlass(surface.backdrop, shape, surface.glass.style(Surfaces.Menu, surface.lightBackground))
                else Modifier.background(surface.color.copy(alpha = 0.12f), shape)
            )
            .clip(shape)
            .then(if (onClick != null && surface.interactive) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        content()
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
    StudyCard(surface, onClick = { controller.setupStudy() }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text("Study companion", style = label(surface.color, 15, FontWeight.SemiBold))
            Text(
                "Shows the study blocks and tasks from your calendar. Tap to switch it on and allow calendar access.",
                style = label(surface.color, 12, alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A round tick box. */
@Composable
private fun Tick(done: Boolean, color: Color, size: Dp = 18.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (done) color.copy(alpha = 0.9f) else color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        if (done) LauncherIcon(LauncherIcons.Check, contentDescription = "Done", tint = readableOn(color), size = size * 0.72f)
    }
}

@Composable
private fun TaskRow(text: String, done: Boolean, color: Color, enabled: Boolean, onToggle: () -> Unit) {
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

@Composable
private fun ActionChip(text: String, color: Color, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(if (filled) color.copy(alpha = 0.9f) else color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = label(if (filled) readableOn(color) else color, 12, FontWeight.Medium), maxLines = 1)
    }
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
    StudyCard(surface, onClick = { controller.run(LauncherAction.Today) }) {
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
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (current.status == StudyStatus.Open) {
                                ActionChip("Done", color, filled = true) { controller.studyStatus(current, StudyStatus.Done) }
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
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
    StudyCard(surface, onClick = { controller.run(LauncherAction.Today) }) {
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

/**
 * The Today panel: overdue tasks, today's blocks with their tasks, notes and links, today's
 * tasks, tomorrow's first block, and the quick actions.
 */
@Composable
internal fun TodaySheet(glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop, controller: HomeController) {
    val study = controller.data.study
    val day by study.day.collectAsState()
    val loaded by study.loaded.collectAsState()
    val focus by study.focus.collectAsState()
    val tick = rememberMinuteTick()
    val now = remember(tick, day) { System.currentTimeMillis() }
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.enter()) }
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controller.todaySheet = false }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp)
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 60.dp.toPx()
                }
                .liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground, large = true))
                .clip(shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Today", style = label(color, 18, FontWeight.SemiBold))
                    Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())), style = label(color, 12, alpha = 0.7f))
                }
                ActionChip("Tell Claude", color) { controller.run(LauncherAction.TellClaude) }
                Spacer(Modifier.width(6.dp))
                ActionChip("Plan", color) { controller.run(LauncherAction.StudyPlan) }
            }
            Spacer(Modifier.height(8.dp))
            if (!loaded) {
                Text(
                    "The study companion is off, or it may not read the calendar yet.",
                    style = label(color, 13, alpha = 0.8f), modifier = Modifier.padding(vertical = 8.dp),
                )
                ActionChip("Switch it on", color, filled = true) { controller.setupStudy() }
                return@Column
            }
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (day.isEmpty) {
                    Text(
                        "Nothing in your study and task calendars today. Blocks come from calendars named like \"${controller.data.settings.get(LauncherSettings.StudyBlockCalendars)}\", " +
                            "tasks from \"${controller.data.settings.get(LauncherSettings.StudyTaskCalendars)}\" (Settings → Study companion).",
                        style = label(color, 13, alpha = 0.8f),
                    )
                }
                if (day.overdue.isNotEmpty()) {
                    SectionLabel("Still open from earlier", color)
                    day.overdue.forEach { task ->
                        TaskRow(task.title.text, false, color, true) { controller.studyStatus(task, StudyStatus.Done) }
                    }
                }
                if (day.blocks.isNotEmpty()) SectionLabel("Blocks", color)
                day.blocks.forEach { block ->
                    val running = now >= block.begin && now < block.end
                    val done = block.status != StudyStatus.Open
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(color.copy(alpha = if (running) 0.14f else 0.06f))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(eventColor(block)))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                block.title.text, modifier = Modifier.weight(1f),
                                style = label(color, 14, FontWeight.SemiBold, alpha = if (done) 0.6f else 1f, struck = block.status == StudyStatus.Skipped),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
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
                        }
                        Text("${time(block.begin)} – ${time(block.end)}", style = label(color, 11, alpha = 0.7f), modifier = Modifier.padding(start = 17.dp))
                        block.tasks.forEach { task ->
                            TaskRow(task.text, task.done, color, true) { controller.studyTick(block, task.index, !task.done) }
                        }
                        val notes = block.notes
                        if (notes.isNotBlank()) {
                            Text(notes, style = label(color, 12, alpha = 0.75f), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                        }
                        Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (block.status == StudyStatus.Open) {
                                ActionChip("Done", color, filled = running) { controller.studyStatus(block, StudyStatus.Done) }
                                ActionChip("Skip", color) { controller.studyStatus(block, StudyStatus.Skipped) }
                            } else {
                                ActionChip("Undo", color) { controller.studyStatus(block, StudyStatus.Open) }
                            }
                            if (running || focus?.eventKey == block.key) {
                                val focusing = focus?.eventKey == block.key
                                ActionChip(if (focusing) "Stop focus · " + minutes(now - (focus?.startedAt ?: now)) else "Focus", color, filled = focusing) { controller.studyFocus(block) }
                            }
                            block.link?.let { link -> ActionChip("Open link", color) { controller.openUrl(link) } }
                        }
                    }
                }
                if (day.tasks.isNotEmpty()) SectionLabel("Tasks", color)
                day.tasks.forEach { task ->
                    val done = task.status != StudyStatus.Open
                    TaskRow(task.title.text, done, color, true) { controller.studyStatus(task, if (done) StudyStatus.Open else StudyStatus.Done) }
                }
                day.tomorrow?.let { first ->
                    SectionLabel("Tomorrow", color)
                    Text("${time(first.begin)}  ${first.title.text}", style = label(color, 13, alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!study.canWrite) {
                Text(
                    "Ticks are not written to the calendar yet (no permission), so your assistant cannot see them.",
                    style = label(color, 11, alpha = 0.7f), modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(text, style = label(color, 12, FontWeight.SemiBold, alpha = 0.7f), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

/** The list of built-in widgets to add to the current page. */
@Composable
internal fun WidgetPicker(glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop, controller: HomeController) {
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.enter()) }
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controller.widgetPicker = false }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 84.dp)
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 40.dp.toPx()
                }
                .liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground))
                .clip(shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Text("Add a widget to this page", style = label(color, 13, FontWeight.SemiBold, alpha = 0.75f), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            for ((type, name, _) in WidgetTypes) {
                Text(
                    name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            controller.widgetPicker = false
                            controller.addWidget(type)
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    style = label(color, 15),
                )
            }
            Text(
                "Widgets of other apps are the next step of Phase 3.",
                style = label(color, 11, alpha = 0.6f), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
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

    /** No block left today, but [open] tasks (today's and overdue) are not ticked. */
    data class Tasks(val open: Int) : StudyGlance
}

/** The glance for [day] at [now]; null when there is nothing to say (no block left, no open task). */
internal fun studyGlance(day: StudyDay, now: Long): StudyGlance? {
    val running = day.blocks.lastOrNull { now >= it.begin && now < it.end && it.status == StudyStatus.Open }
    if (running != null) {
        val length = (running.end - running.begin).coerceAtLeast(1)
        return StudyGlance.Running(
            block = running,
            minutesLeft = ((running.end - now + 59_999) / 60_000).toInt(),
            progress = ((now - running.begin).toFloat() / length).coerceIn(0f, 1f),
        )
    }
    val next = day.blocks.firstOrNull { it.begin > now && it.status == StudyStatus.Open }
    if (next != null) return StudyGlance.Next(next, ((next.begin - now + 59_999) / 60_000).toInt())
    return if (day.openTasks > 0) StudyGlance.Tasks(day.openTasks) else null
}

/** The glance as one short line. [short] drops words for the pill. */
internal fun glanceText(glance: StudyGlance, short: Boolean = false): String = when (glance) {
    is StudyGlance.Running -> glance.block.title.subject + " · " + minutes(glance.minutesLeft * 60_000L) + if (short) "" else " left"
    is StudyGlance.Next -> when {
        glance.minutesUntil <= 60 -> (if (short) "" else "Next: ") + glance.block.title.subject + " in " + minutes(glance.minutesUntil * 60_000L)
        else -> (if (short) "" else "Next: ") + glance.block.title.subject + " at " + time(glance.block.begin)
    }
    is StudyGlance.Tasks -> if (short) "${glance.open}" else if (glance.open == 1) "1 task left today" else "${glance.open} tasks left today"
}

/** The glance now, or null when the study companion is off, cannot read the calendar, or has nothing to say. */
@Composable
internal fun rememberStudyGlance(controller: HomeController): StudyGlance? {
    val study = controller.data.study
    val loaded by study.loaded.collectAsState()
    val day by study.day.collectAsState()
    val tick = rememberMinuteTick()
    return remember(loaded, day, tick) { if (loaded) studyGlance(day, System.currentTimeMillis()) else null }
}

/** A dot in the block's colour with the time gone as a ring around it (a plain dot for a block not started). */
@Composable
private fun StudyRing(glance: StudyGlance, color: Color, size: Dp) {
    val accent = when (glance) {
        is StudyGlance.Running -> eventColor(glance.block)
        is StudyGlance.Next -> eventColor(glance.block)
        is StudyGlance.Tasks -> color
    }
    val progress = (glance as? StudyGlance.Running)?.progress
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.14f
        val radius = (this.size.minDimension - stroke) / 2f
        if (progress != null) {
            drawCircle(color.copy(alpha = 0.22f), radius = radius, style = Stroke(stroke))
            drawArc(
                accent, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            drawCircle(accent, radius = radius * 0.42f)
        } else if (glance is StudyGlance.Tasks) {
            drawCircle(accent.copy(alpha = 0.8f), radius = radius, style = Stroke(stroke))
        } else {
            drawCircle(accent, radius = radius * 0.55f)
        }
    }
}

/**
 * The line under a clock widget: the block running now with a ring for the time gone, else the
 * next block, else the tasks left. Tap opens Today (ticks, Done, Skip, Focus); a long press starts
 * or stops the focus timer. Uses the clock's colour and shadow so it reads as part of the clock.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    Row(
        modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .then(
                if (interactive) Modifier.combinedClickable(
                    interactionSource = null,
                    indication = null,
                    onClick = { controller.run(LauncherAction.Today) },
                    onLongClick = { controller.run(LauncherAction.FocusTimer) },
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
        )
    }
}

/** The pill's study item: ring and a few words, or the school icon when there is nothing to show. */
@Composable
internal fun PillStudy(controller: HomeController, color: Color, style: TextStyle, iconSize: Dp) {
    val glance = rememberStudyGlance(controller)
    if (glance == null || glance is StudyGlance.Tasks) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LauncherIcon(LauncherIcons.School, contentDescription = "Today", tint = color, size = iconSize)
            if (glance is StudyGlance.Tasks) {
                Spacer(Modifier.width(4.dp))
                Text(glanceText(glance, short = true), style = style, maxLines = 1)
            }
        }
        return
    }
    Row(Modifier.widthIn(max = 170.dp), verticalAlignment = Alignment.CenterVertically) {
        StudyRing(glance, color, (iconSize.value * 0.8f).dp)
        Spacer(Modifier.width(6.dp))
        Text(glanceText(glance, short = true), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
