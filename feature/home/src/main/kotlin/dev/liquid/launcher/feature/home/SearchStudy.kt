package dev.liquid.launcher.feature.home

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.HomeItem
import dev.liquid.launcher.core.data.StudyDay
import dev.liquid.launcher.core.data.StudyEvent
import dev.liquid.launcher.core.data.StudyStatus
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.derive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The search page with nothing typed (0.9.3): study first. Cards in the order chosen in Settings → Search,
 * stacked from the field upwards so the last one sits under the thumb. The stack scrolls from its bottom:
 * what is nearest the field is always whole, and a widget is never taller than the space above the
 * keyboard, so it always fits once scrolled to (the calendar used to be cut off below its fifth week).
 */

/** Text and lines of the search page's cards: the theme's card text (W3), else white. */
private val Ink: Color
    get() = ThemeInk.card

private fun searchText(size: Int, weight: FontWeight = FontWeight.Normal, alpha: Float = 1f, color: Color = Ink) =
    TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = alpha), fontSize = size.sp, fontWeight = weight)

@Composable
internal fun SearchPage(
    controller: HomeController,
    cards: List<String>,
    widgets: List<HomeItem>,
    env: PageEnv?,
    suggestions: List<AppEntry>,
    transition: String,
    modifier: Modifier,
    openApp: (AppEntry) -> Unit,
) {
    val study = controller.data.study
    val loaded by study.loaded.collectAsState()
    val day by study.day.collectAsState()
    val tick = rememberMinuteTick()
    val now = remember(tick, day) { System.currentTimeMillis() }
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val room = maxHeight
        val scroll = rememberScrollState()
        Column(
            Modifier
                .fillMaxWidth()
                // 0.10.2: what is scrolled past the top fades out under a soft edge instead of being cut.
                .fadingTop(scroll)
                // From the bottom: the cards nearest the field show first, the rest are a scroll up away.
                .verticalScroll(scroll, reverseScrolling = true)
                .padding(top = 6.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (id in cards) {
                when (id) {
                    // 0.10.2: one calm line; the calendar month (the page's widgets) and the exam countdown one tap away.
                    "glance" -> {
                        if (controller.searchGlanceOpen) {
                            if ("widgets" !in cards) Column { SearchWidgets(controller, widgets, env, room) }
                            if (loaded && "exam" !in cards) ExamCountdown(controller, day.exams, day.unconfirmedExams, now)
                        }
                        GlanceLine(controller, day, loaded, now)
                    }
                    "widgets" -> Column { SearchWidgets(controller, widgets, env, room) }
                    "timeline" -> if (loaded) DayStrip(controller, day, now)
                    "exam" -> if (loaded) ExamCountdown(controller, day.exams, day.unconfirmedExams, now)
                    "annales" -> if (loaded) AnnalesCard(controller, day, now)
                    "absences" -> if (loaded) AbsenceCard(day)
                    "grades" -> if (loaded) GradeCard(day)
                    "flashcard" -> if (loaded) FlashcardCard(controller, day)
                    "review" -> if (loaded) ReviewCard(controller, day, now)
                    "next" -> if (loaded) NextBlockCard(controller, day, now)
                    "commands" -> if (loaded) CommandChips(controller)
                    "claude" -> ClaudeChips(controller, if (loaded) heroBlock(day, now) else null)
                    "apps" -> if (suggestions.isNotEmpty()) SuggestionRow(controller, suggestions, transition, openApp)
                }
            }
        }
    }
}

/**
 * C4 (0.9.19): in the week before an exam, your past papers, one folder per module (Settings → Study companion →
 * Annales); the module of the next exam first and open. A tap on a paper opens it in your PDF app.
 */
@Composable
private fun AnnalesCard(controller: HomeController, day: StudyDay, now: Long) {
    val folder by controller.data.settings.derive { get(LauncherSettings.StudyAnnales) }
    if (folder.isBlank()) return
    val next = day.exams.firstOrNull { it.end > now } ?: return
    val left = next.begin - now
    if (left > 7L * 24 * 3_600_000L) return
    val context = androidx.compose.ui.platform.LocalContext.current
    var modules by remember(folder) { mutableStateOf<List<dev.liquid.launcher.core.data.Annales.Module>?>(null) }
    androidx.compose.runtime.LaunchedEffect(folder) {
        modules = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            dev.liquid.launcher.core.data.Annales.read(context, android.net.Uri.parse(folder))
        }
    }
    val list = modules ?: return
    if (list.isEmpty()) return
    val first = dev.liquid.launcher.core.data.Annales.matching(list, next.title.subject)
    val ordered = if (first == null) list else listOf(first) + list.filter { it != first }
    var open by remember(folder, first) { mutableStateOf(first?.name) }
    val days = (left / 86_400_000L).toInt()
    SearchPane(onClick = null) {
        Column {
            Text(
                "Annales · ${next.title.subject} " + when {
                    left <= 0 -> "today"
                    days == 0 -> "in ${left / 3_600_000L} h"
                    days == 1 -> "tomorrow"
                    else -> "in $days days"
                },
                style = searchText(12, FontWeight.Medium, alpha = 0.7f),
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ordered.forEach { m ->
                    GlassButton(
                        "${m.name} ${m.docs.size}", Ink,
                        emphasis = if (open == m.name) ControlEmphasis.Selected else ControlEmphasis.Normal,
                    ) { open = if (open == m.name) null else m.name }
                }
            }
            val shown = ordered.firstOrNull { it.name == open }
            shown?.docs?.take(6)?.forEach { doc ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .squishClickable(delayMs = 40L) {
                            val view = Intent(Intent.ACTION_VIEW).setDataAndType(doc.uri, "application/pdf")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(view) }.onFailure {
                                controller.notice(dev.liquid.launcher.core.common.NoticeKind.Info, "No app here opens PDFs")
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LauncherIcon(LauncherIcons.Folder, contentDescription = null, tint = Ink.copy(alpha = 0.8f), size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(doc.name, style = searchText(14), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val more = (shown?.docs?.size ?: 0) - 6
            if (more > 0) Text("and $more more in the folder", style = searchText(12, alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** The scrolling stack's top edge fades out while there is more above it (0.10.2: nothing looks cut). */
private fun Modifier.fadingTop(scroll: androidx.compose.foundation.ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        // Reverse scrolling: the stack starts at its bottom, so there is more above while not scrolled to the end.
        if (scroll.value < scroll.maxValue) {
            val edge = 28.dp.toPx().coerceAtMost(size.height / 3f)
            drawRect(
                Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = edge),
                size = Size(size.width, edge),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }
    }

/**
 * 0.10.2: "Today · 24 days to Checkpoint 2" on one line (the date when no exam is coming); a tap opens the calendar
 * month (the search page's widgets) and the exam countdown above it, a second tap folds them.
 */
@Composable
private fun GlanceLine(controller: HomeController, day: StudyDay, loaded: Boolean, now: Long) {
    val exam = if (loaded) day.exams.firstOrNull { it.begin > now } else null
    val text = "Today · " + if (exam != null) {
        val (count, unit) = examCountdown(exam.begin, now)
        val name = exam.rawTitle.trim().substringBefore(" (").trim().ifEmpty { "the exam" }
        if (unit.isEmpty()) "$name now" else "$count $unit to $name"
    } else {
        java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
    }
    val open = controller.searchGlanceOpen
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .squishClickable {
                Haptics.tick(controller.view)
                controller.searchGlanceOpen = !open
            }
            .cardFill(0.07f)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherIcon(LauncherIcons.Calendar, contentDescription = null, tint = Ink.copy(alpha = 0.8f), size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            // Amber from 14 days, red from 3, as the exam countdown; plain before.
            style = searchText(13, FontWeight.Medium, color = exam?.let { e -> ((e.begin - now) / 86_400_000L).takeIf { it <= 14 }?.let { examColor(it) } } ?: Ink),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "glance")
        LauncherIcon(
            LauncherIcons.ArrowUp, contentDescription = if (open) "Fold the calendar and exams" else "Calendar and exams",
            tint = Ink.copy(alpha = 0.7f), size = 18.dp, modifier = Modifier.graphicsLayer { rotationZ = turn },
        )
    }
}

/** A card of the search page: a faint pane on the dimmed wallpaper; a tap may grow the study hub out of it. */
@Composable
private fun SearchPane(onClick: ((Rect?) -> Unit)?, content: @Composable () -> Unit) {
    val bounds = remember { arrayOfNulls<Rect>(1) }
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds[0] = it.boundsInRoot() }
            .then(if (onClick != null) Modifier.squishClickable(delayMs = 40L) { onClick(bounds[0]) } else Modifier)
            .clip(cardOutline())
            .cardFill(0.09f)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) { content() }
}

// ---- next block ----

@Composable
private fun NextBlockCard(controller: HomeController, day: StudyDay, now: Long) {
    if (day.blocks.isEmpty()) return
    val hero = heroBlock(day, now)
    SearchPane(onClick = { from -> controller.openToday(from) }) {
        if (hero == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DayRing(day, now, Ink, 34.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("The day's blocks are done", style = searchText(15, FontWeight.SemiBold))
                    Text("${day.blocksDone}/${day.blocks.size} done · tell Claude how it went", style = searchText(12, alpha = 0.7f))
                }
                GlassButton("Check-in", Ink, icon = LauncherIcons.Chat) {
                    controller.closeSearch(immediately = true)
                    controller.eveningCheckin()
                }
            }
            return@SearchPane
        }
        val accent = eventColor(hero)
        val running = now >= hero.begin && now < hero.end
        val tasks = hero.tasks
        val nextStep = tasks.firstOrNull { !it.done }
        Column(Modifier.animateContentSize(spring(dampingRatio = 0.86f, stiffness = MotionScale.stiffness(520f)))) {
            Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                // 0.10.2: today's progress (blocks done of the day's, in their colours) beside the block now or next.
                DayRing(day, now, Ink, 34.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            running -> "Now · " + studyDuration(hero.end - now) + " left"
                            hero.begin > now -> "Next · " + studyClock(hero.begin) + ", in " + studyDuration(hero.begin - now)
                            else -> "Ended at " + studyClock(hero.end) + " · not marked"
                        } + if (tasks.isNotEmpty()) " · ${tasks.count { it.done }}/${tasks.size} steps" else "",
                        style = searchText(12, FontWeight.Medium, alpha = 0.72f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(hero.title.text, style = searchText(16, FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                when {
                    running && hero.status == StudyStatus.Open -> {
                        val focus by controller.data.study.focus.collectAsState()
                        val focusing = focus?.eventKey == hero.key
                        GlassButton(if (focusing) "Stop" else "Focus", Ink, icon = LauncherIcons.Schedule, emphasis = ControlEmphasis.Main, accent = accent) {
                            controller.studyFocus(hero)
                        }
                    }
                    hero.end <= now -> GlassButton("Done", Ink, icon = LauncherIcons.Check, emphasis = ControlEmphasis.Main, accent = accent) {
                        controller.studyStatus(hero, StudyStatus.Done)
                    }
                    hero.begin - now <= 45 * 60_000L -> GlassButton("Start", Ink, icon = LauncherIcons.Schedule, emphasis = ControlEmphasis.Main, accent = accent) {
                        controller.studyFocus(hero)
                    }
                }
            }
            // The next step, tickable from here: the most likely tap after "what am I doing now".
            if (nextStep != null && (running || hero.end <= now)) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.padding(start = 45.dp)) {
                    TaskRow(nextStep.text, false, Ink, true) { controller.studyTick(hero, nextStep.index, true) }
                }
            }
        }
    }
}

// ---- today's timeline (B4, on one line) ----

@Composable
private fun DayStrip(controller: HomeController, day: StudyDay, now: Long) {
    val events = remember(day) { (day.classes + day.blocks).sortedBy { it.begin } }
    if (day.blocks.isEmpty()) return
    val start = minOf(events.first().begin, now)
    val end = maxOf(events.maxOf { it.end }, now)
    val span = (end - start).coerceAtLeast(1).toFloat()
    val accent = HomeTheme.accent
    val focus = remember(day, now) { heroBlock(day, now) }
    SearchPane(onClick = { from -> controller.openToday(from) }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today", style = searchText(13, FontWeight.SemiBold))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${day.blocksDone}/${day.blocks.size} blocks" + if (day.classes.isNotEmpty()) " · ${day.classes.size} classes" else "",
                    style = searchText(12, alpha = 0.6f), modifier = Modifier.weight(1f),
                )
                if (focus != null) {
                    Text(focus.title.subject, style = searchText(12, FontWeight.Medium, alpha = 0.85f, color = eventColor(focus)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                val h = 8.dp.toPx()
                val y = (size.height - h) / 2f
                val r = CornerRadius(h / 2f, h / 2f)
                fun x(t: Long) = (t - start) / span * size.width
                drawRoundRect(Ink.copy(alpha = 0.12f), Offset(0f, y), Size(size.width, h), r)
                for (c in day.classes) {
                    drawRoundRect(Ink.copy(alpha = 0.32f), Offset(x(c.begin), y), Size((x(c.end) - x(c.begin)).coerceAtLeast(2f), h), r)
                }
                for (b in day.blocks) {
                    val color = eventColor(b)
                    val left = x(b.begin)
                    val width = (x(b.end) - left).coerceAtLeast(3f)
                    when {
                        b.status == StudyStatus.Done -> drawRoundRect(color, Offset(left, y), Size(width, h), r)
                        b.status == StudyStatus.Skipped -> drawRoundRect(Ink.copy(alpha = 0.2f), Offset(left, y), Size(width, h), r)
                        b.end <= now -> drawRoundRect(color, Offset(left, y), Size(width, h), r, style = Stroke(1.5.dp.toPx()))
                        now >= b.begin -> {
                            drawRoundRect(color.copy(alpha = 0.4f), Offset(left, y), Size(width, h), r)
                            drawRoundRect(color, Offset(left, y), Size((x(now) - left).coerceAtLeast(3f), h), r)
                        }
                        else -> drawRoundRect(color.copy(alpha = 0.55f), Offset(left, y), Size(width, h), r)
                    }
                }
                val nx = x(now).coerceIn(3.dp.toPx(), size.width - 3.dp.toPx())
                drawCircle(accent, 5.dp.toPx(), Offset(nx, size.height / 2f))
                drawCircle(Ink, 2.5.dp.toPx(), Offset(nx, size.height / 2f))
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Text(studyClock(start), style = searchText(11, alpha = 0.55f), modifier = Modifier.weight(1f))
                Text(studyClock(end), style = searchText(11, alpha = 0.55f))
            }
        }
    }
}

// ---- exam countdown (B1) ----

/** Grey above 14 days, amber from 14 to 4, red at 3 or less (B1). */
internal fun examColor(daysLeft: Long): Color = when {
    daysLeft > 14 -> Ink.copy(alpha = 0.6f)
    daysLeft > 3 -> Color(0xFFFFB74D)
    else -> Color(0xFFFF6B6B)
}

/** "51 days", "4 days", or in hours at 3 days or less ("62 h"); "now" once it has begun. */
internal fun examCountdown(begin: Long, now: Long): Pair<String, String> {
    val ms = begin - now
    if (ms <= 0) return "now" to ""
    val hours = ms / 3_600_000L
    val days = ms / 86_400_000L
    return if (days <= 3) "${hours.coerceAtLeast(1)}" to "h" else "$days" to if (days == 1L) "day" else "days"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExamCountdown(controller: HomeController, exams: List<StudyEvent>, unconfirmed: Set<String>, now: Long) {
    val first = exams.firstOrNull() ?: return
    var open by remember { mutableStateOf(false) }
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }
    fun date(e: StudyEvent) = Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()).format(dayFormat) +
        if (e.allDay) "" else " · " + studyClock(e.begin)
    fun openEvent(e: StudyEvent) = controller.start(examIntent(e))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(cardOutline())
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = { if (open) open = false else openEvent(first) },
                // Long press: the next dates (B1).
                onLongClick = {
                    Haptics.tick(controller.view)
                    open = !open
                },
            )
            .cardFill(0.09f)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .animateContentSize(spring(dampingRatio = 0.84f, stiffness = MotionScale.stiffness(480f))),
    ) {
        Column {
            ExamRow(first, now, big = true, date(first), first.key in unconfirmed)
            if (open) {
                exams.drop(1).take(3).forEach { e ->
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.squishClickable { openEvent(e) }) { ExamRow(e, now, big = false, date(e), e.key in unconfirmed) }
                }
                if (exams.size == 1) Text("No other exam in your calendar in the next six months", style = searchText(11, alpha = 0.55f), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun ExamRow(exam: StudyEvent, now: Long, big: Boolean, date: String, unconfirmed: Boolean) {
    val days = (exam.begin - now) / 86_400_000L
    val color = examColor(days)
    val (count, unit) = examCountdown(exam.begin, now)
    // B1: "≈" while launcher-data says the date is not confirmed.
    val number = if (unconfirmed) "≈$count" else count
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(if (big) 64.dp else 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(number, style = searchText(if (big) 28 else 20, FontWeight.SemiBold, color = color))
            if (unit.isNotEmpty()) Text(unit, style = searchText(11, FontWeight.Medium, color = color))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(exam.rawTitle.trim(), style = searchText(if (big) 15 else 13, FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                date + (if (unconfirmed) "  ·  date not confirmed" else "") + if (big) "  ·  hold for the next ones" else "",
                style = searchText(11, alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** An exam's event in the calendar app; an exam from launcher-data (no event) opens the calendar on its day. */
internal fun examIntent(e: StudyEvent): Intent =
    if (e.eventId > 0) {
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.eventId))
    } else {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, e.begin) }.build()
        Intent(Intent.ACTION_VIEW, uri)
    }

// ---- absence meter (B2), grades (E2), flashcard (E4): from launcher-data ----

private val Green = Color(0xFF66D19E)
private val Amber = Color(0xFFFFB74D)

private fun bandColor(band: String): Color = when (band) {
    "green" -> Green
    "amber" -> Amber
    "red" -> LeaveRed
    else -> Ink.copy(alpha = 0.45f)
}

/**
 * B2: one liquid tube per module, filled to absences ÷ (limit × sessions), green below half, amber to 0.8, red
 * above; a permanent grey UNVERIFIED badge while the rule is not confirmed, and never a "safe". Without a session
 * count only the number of absences is shown. Without launcher-data, the launcher's own "Note: absent" records.
 */
@Composable
private fun AbsenceCard(day: StudyDay) {
    val feedAbsences = day.feed?.absences
    val levels = remember(feedAbsences) { feedAbsences?.let { dev.liquid.launcher.core.data.AbsenceMeter.levels(it) }.orEmpty() }
    if (levels.isEmpty() && day.absences.isEmpty()) return
    SearchPane(onClick = null) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Absences", style = searchText(13, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                if (feedAbsences?.ruleConfirmed != true) {
                    Text(
                        "UNVERIFIED",
                        style = searchText(10, FontWeight.SemiBold, alpha = 0.75f),
                        modifier = Modifier.clip(RoundedCornerShape(50)).cardFill(0.16f).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (levels.isNotEmpty()) {
                for (level in levels) AbsenceTube(level, (feedAbsences?.limit ?: 0.2))
            } else {
                for ((module, n) in day.absences.entries.sortedByDescending { it.value }) {
                    Row {
                        Text(module, style = searchText(13), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (n == 1) "1 absence" else "$n absences", style = searchText(12, alpha = 0.7f))
                    }
                }
                Text("Counted from your \"Note: absent\" records; launcher-data adds the limit per module.", style = searchText(11, alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun AbsenceTube(level: dev.liquid.launcher.core.data.AbsenceLevel, limit: Double) {
    val color = bandColor(level.band)
    val fill by androidx.compose.animation.core.animateFloatAsState(
        (level.fill ?: 0.0).toFloat().coerceIn(0f, 1.2f), spring(dampingRatio = 0.6f, stiffness = MotionScale.stiffness(120f)), label = "tube",
    )
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(level.module, style = searchText(13, FontWeight.Medium), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (level.total != null) "${level.absent} of ${level.total} · limit ${(limit * 100).toInt()} %" else "${level.absent} absences · sessions unknown",
                style = searchText(11, alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val r = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(Ink.copy(alpha = 0.12f), cornerRadius = r)
            if (level.fill != null) {
                val w = (size.width * fill.coerceAtMost(1f)).coerceAtLeast(size.height)
                drawRoundRect(color.copy(alpha = 0.9f), size = Size(w, size.height), cornerRadius = r)
                // The liquid's light: a brighter line along its top.
                drawRoundRect(Color.White.copy(alpha = 0.28f), topLeft = Offset(size.height / 3f, 2.dp.toPx()), size = Size((w - size.height * 2f / 3f).coerceAtLeast(0f), 2.dp.toPx()), cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()))
            }
            drawRoundRect(Ink.copy(alpha = 0.3f), cornerRadius = r, style = Stroke(1.dp.toPx()))
        }
    }
}

/** E2: the weighted average, only once every mark has its coefficient. */
@Composable
private fun GradeCard(day: StudyDay) {
    val grades = day.feed?.grades.orEmpty()
    val average = remember(grades) { dev.liquid.launcher.core.data.Grades.average(grades) } ?: return
    val marks = grades.count { it.mark != null }
    SearchPane(onClick = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%.1f".format(Locale.getDefault(), average), style = searchText(26, FontWeight.SemiBold, color = if (average >= 10.0) Green else Amber))
            Text(" /20", style = searchText(13, alpha = 0.6f))
            Spacer(Modifier.width(12.dp))
            Text("Weighted average · " + (if (marks == 1) "1 mark" else "$marks marks"), style = searchText(13, alpha = 0.8f))
        }
    }
}

/**
 * E4: the deck's first card due (0.12.0, D173: launcher-data's flashcards kept with repeats), face down. A tap turns it
 * over to the question, another to the answer, each turn a spring around the vertical axis; then right or wrong (wrong
 * comes back tomorrow and writes a note for the check-in, right waits longer each time). The header says how many more
 * are due; at the end, "All done for today". Formulas Claude writes in LaTeX are drawn ([MathText]).
 */
@Composable
private fun FlashcardCard(controller: HomeController, @Suppress("UNUSED_PARAMETER") day: StudyDay) {
    val deck by controller.data.study.deck.collectAsState()
    if (deck.cards.isEmpty()) return
    val tick = rememberMinuteTick()
    val today = remember(tick) { java.time.LocalDate.now() }
    val due = remember(deck, today) { dev.liquid.launcher.core.data.FlashcardDeck.due(deck, today) }
    val card = due.firstOrNull()
    if (card == null) {
        val next = remember(deck, today) { dev.liquid.launcher.core.data.FlashcardDeck.next(deck, today) }
        Box(Modifier.fillMaxWidth().clip(cardOutline()).background(Ink.copy(alpha = 0.09f)).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Column {
                Text("Flashcards", style = searchText(11, FontWeight.Medium, alpha = 0.65f))
                Spacer(Modifier.height(4.dp))
                Text("All done for today", style = searchText(16, FontWeight.SemiBold))
                next?.let { (date, count) ->
                    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
                    val whenText = if (days == 1L) "tomorrow" else "in $days days"
                    Spacer(Modifier.height(2.dp))
                    Text(if (count == 1) "Next: 1 card $whenText" else "Next: $count cards $whenText", style = searchText(12, alpha = 0.6f))
                }
            }
        }
        return
    }
    val more = due.size - 1
    var side by remember(card.id, card.due) { mutableStateOf(0) }
    val turn by androidx.compose.animation.core.animateFloatAsState(
        side * 180f, spring(dampingRatio = 0.72f, stiffness = MotionScale.stiffness(260f)), label = "flip",
    )
    // The face shown is the one turned towards you: the content swaps at the half turn and is mirrored back.
    val shown = ((turn + 90f) / 180f).toInt().coerceIn(0, 2)
    Box(
        Modifier
            .fillMaxWidth()
            .flipAround(turn)
            .clip(cardOutline())
            .background(Ink.copy(alpha = if (shown == 0) 0.14f else 0.09f))
            .squishClickable(delayMs = 40L) { if (side < 2) side++ }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(Modifier.readableWhenFlipped(turn)) {
            val left = if (more > 0) "$more more due today" else "the last one today"
            Text(card.module.ifBlank { "Flashcard" } + " · " + left, style = searchText(11, FontWeight.Medium, alpha = 0.65f))
            Spacer(Modifier.height(4.dp))
            when (shown) {
                0 -> Text("Tap to turn it over", style = searchText(16, FontWeight.SemiBold))
                1 -> MathText(card.q, searchText(15, FontWeight.Medium))
                else -> {
                    MathText(card.a, searchText(15, FontWeight.Medium))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassButton("Wrong", Ink, icon = LauncherIcons.Close) { controller.answerCard(card, right = false) }
                        GlassButton("Right", Ink, icon = LauncherIcons.Check, emphasis = ControlEmphasis.Main) { controller.answerCard(card, right = true) }
                    }
                }
            }
        }
    }
}

/** Turns the card around the vertical axis by [degrees] (a little perspective). */
private fun Modifier.flipAround(degrees: Float): Modifier = this.then(
    Modifier.graphicsLayer {
        rotationY = degrees
        cameraDistance = 14f * density
    }
)

/** Mirrors the content back on odd half turns, so the side facing you reads the right way round. */
private fun Modifier.readableWhenFlipped(degrees: Float): Modifier = this.then(
    Modifier.graphicsLayer {
        val half = ((degrees + 90f) / 180f).toInt()
        if (half % 2 == 1) rotationY = 180f
    }
)

/** E5, on Sundays: the week in four numbers; a tap goes through it with Claude. */
@Composable
private fun ReviewCard(controller: HomeController, day: StudyDay, now: Long) {
    val week = day.week ?: return
    val exam = day.exams.firstOrNull()
    val daysToExam = exam?.let { ((it.begin - now) / 86_400_000L).coerceAtLeast(0) }
    SearchPane(onClick = {
        controller.closeSearch(immediately = true)
        val prompt = buildString {
            append("Weekly review (Sunday). This week: ${week.blocksDone} of ${week.blocksPlanned} study blocks done, ")
            append("${week.focusMinutes} min of focus timer, ${week.absences} absences noted.")
            if (exam != null) append(" Next exam: ${exam.rawTitle.trim()} in $daysToExam days.")
            append(" Ask me what went well and what did not, then propose next week's plan without touching fixed dates.")
        }
        controller.askClaude(controller.withStudyHeader(prompt))
    }) {
        Column {
            Text("Week review", style = searchText(13, FontWeight.SemiBold))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ReviewNumber("${week.blocksDone}/${week.blocksPlanned}", "blocks")
                ReviewNumber("${week.focusMinutes}", "min focus")
                ReviewNumber("${week.absences}", if (week.absences == 1) "absence" else "absences")
                if (daysToExam != null) ReviewNumber("$daysToExam", "days to exam", examColor(daysToExam))
            }
            Spacer(Modifier.height(6.dp))
            Text("Tap to go through the week with Claude", style = searchText(11, alpha = 0.6f))
        }
    }
}

@Composable
private fun ReviewNumber(value: String, label: String, color: Color = Ink) {
    Column {
        Text(value, style = searchText(20, FontWeight.SemiBold, color = color))
        Text(label, style = searchText(11, alpha = 0.65f))
    }
}

// ---- study commands (D3) and Claude ----

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CommandChips(controller: HomeController) {
    // 0.10.2: the chips wrap onto a second line instead of running off the edge.
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((command, label) in StudyCommands.chips) {
            GlassButton(label, Ink, icon = StudyCommands.icon(command)) {
                Haptics.tick(controller.view)
                controller.searchQuery = command
            }
        }
    }
}

/**
 * 0.10.2: one "Ask Claude" chip. Your prompt templates (D2) are under it: its arrow shows them (wrapping, never cut),
 * and typing "?" lists them too; the chip itself switches the field to Claude.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ClaudeChips(controller: HomeController, hero: StudyEvent?) {
    val claudeOn by controller.data.settings.derive { get(LauncherSettings.SearchClaude) != "off" }
    if (!claudeOn) return
    val templates by controller.data.settings.derive { get(LauncherSettings.StudyTemplates) }
    val prompts = remember(templates) { claudePrompts(templates) }
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = 0.86f, stiffness = MotionScale.stiffness(520f))),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GlassButton("Ask Claude", Ink, icon = LauncherIcons.Sparkle, emphasis = ControlEmphasis.Main) {
            Haptics.tick(controller.view)
            controller.askClaudeMode = true
        }
        if (prompts.isNotEmpty()) {
            GlassButton(null, Ink, icon = if (open) LauncherIcons.ArrowDown else LauncherIcons.ArrowUp, contentDescription = if (open) "Hide your prompts" else "Your prompts") {
                Haptics.tick(controller.view)
                open = !open
            }
        }
        if (open) {
            for ((label, prompt) in prompts) {
                GlassButton(label, Ink) {
                    controller.closeSearch(immediately = true)
                    controller.askTemplate(prompt, hero)
                }
            }
        }
    }
}

/** Your prompt templates ("Label | prompt" lines) as label and prompt. */
internal fun claudePrompts(templates: List<String>): List<Pair<String, String>> = templates.mapNotNull { line ->
    val label = line.substringBefore('|').trim()
    val prompt = line.substringAfter('|', "").trim()
    if (label.isEmpty() || prompt.isEmpty()) null else label to prompt
}

/** The study commands of search (D3). */
internal object StudyCommands {
    val chips = listOf("!next" to "!next", "!exam" to "!exam", "!plan" to "!plan", "!abs" to "!abs", "? " to "? Claude")

    fun icon(command: String): Int = when (command.trim()) {
        "!next" -> LauncherIcons.Schedule
        "!exam" -> LauncherIcons.School
        "!plan" -> LauncherIcons.Calendar
        "!abs" -> LauncherIcons.Info
        else -> LauncherIcons.Sparkle
    }

    /** The command [text] starts with ("next", "exam", "plan", "abs"), or null. */
    fun parse(text: String): String? {
        val t = text.trim().lowercase(Locale.ROOT)
        if (!t.startsWith("!")) return null
        val word = t.drop(1).substringBefore(' ')
        return word.takeIf { it in setOf("next", "exam", "exams", "plan", "abs") }?.let { if (it == "exams") "exam" else it }
    }

    /** What each command shows, for the list offered while "!" is typed (0.10.2). */
    val about = listOf(
        "!next" to "The block now or next and its steps",
        "!exam" to "Your next exams and how far away they are",
        "!plan" to "Open your study plan",
        "!abs" to "Absences per module against the limit",
    )

    /**
     * The commands [text] could still become while it is typed ("!" alone: all of them; "!e": !exam), or null when it
     * is not the start of one (a web bang such as "!w" stays a bang).
     */
    fun started(text: String): List<Pair<String, String>>? {
        val t = text.trim().lowercase(Locale.ROOT)
        if (!t.startsWith("!") || ' ' in t) return null
        return about.filter { it.first.startsWith(t) }.takeIf { it.isNotEmpty() }
    }

    /** The question after "?" ("" while nothing follows), or null when [text] does not start with "?". */
    fun question(text: String): String? = text.trimStart().takeIf { it.startsWith("?") }?.drop(1)?.trim()
}

/** A row in search's results for a study command. */
internal data class StudyRow(val key: String, val title: String, val detail: String, val icon: Int, val color: Color? = null)

/** Rows for a study command (D3). [act] is what a tap on one does. */
internal fun studyRows(command: String, day: StudyDay, now: Long, loaded: Boolean): List<StudyRow> {
    if (!loaded) return listOf(StudyRow("off", "The study companion is off", "Switch it on in Settings → Study companion", LauncherIcons.Info))
    return when (command) {
        "next" -> {
            val hero = heroBlock(day, now)
            val later = day.blocks.filter { it.begin > now && it.status == StudyStatus.Open && it.key != hero?.key }.take(3)
            val rows = ArrayList<StudyRow>()
            if (hero != null) {
                val running = now >= hero.begin && now < hero.end
                rows += StudyRow(
                    "block:" + hero.key, hero.title.text,
                    when {
                        running -> "Now · " + studyDuration(hero.end - now) + " left"
                        hero.begin > now -> "Next · " + studyClock(hero.begin) + ", in " + studyDuration(hero.begin - now)
                        else -> "Ended at " + studyClock(hero.end) + " · not marked"
                    } + (if (hero.tasks.isNotEmpty()) " · ${hero.tasks.count { it.done }}/${hero.tasks.size} steps" else ""),
                    LauncherIcons.Schedule, eventColor(hero),
                )
            }
            later.forEach { rows += StudyRow("block:" + it.key, it.title.text, studyClock(it.begin) + " – " + studyClock(it.end), LauncherIcons.Schedule, eventColor(it)) }
            if (rows.isEmpty()) {
                day.tomorrow?.let { rows += StudyRow("block:" + it.key, it.title.text, "Tomorrow " + studyClock(it.begin), LauncherIcons.Schedule, eventColor(it)) }
            }
            rows.ifEmpty { listOf(StudyRow("today", "Nothing left today", "Open Today", LauncherIcons.School)) }
        }
        "exam" -> day.exams.take(4).map { e ->
            val (n, unit) = examCountdown(e.begin, now)
            val date = Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
            val about = if (e.key in day.unconfirmedExams) "≈ " else ""
            StudyRow("exam:" + e.eventId + ":" + e.begin, e.rawTitle.trim(), "${about}in $n $unit · $date" + if (about.isNotEmpty()) " · not confirmed" else "", LauncherIcons.School, examColor((e.begin - now) / 86_400_000L))
        }.ifEmpty { listOf(StudyRow("none", "No exam found", "Events named DS, Examen, Partiel or Contrôle in your calendar in the next six months", LauncherIcons.Info)) }
        "plan" -> listOf(
            StudyRow("plan", "Open my study plan", "The plan link from Settings → Study companion", LauncherIcons.Calendar),
            StudyRow("today", "Today", "${day.blocksDone}/${day.blocks.size} blocks done", LauncherIcons.School),
            StudyRow("checkin", "Evening check-in", "Today's log to Claude", LauncherIcons.Chat),
        )
        "abs" -> absenceRows(day)
        else -> emptyList()
    }
}

/** !abs: the tubes of launcher-data when there are some, else the launcher's own records. */
private fun absenceRows(day: StudyDay): List<StudyRow> {
    val absences = day.feed?.absences
    return if (absences != null && absences.modules.isNotEmpty()) {
            dev.liquid.launcher.core.data.AbsenceMeter.levels(absences).map { level ->
                val detail = if (level.total != null) {
                    "${level.absent} of ${level.total} sessions · ${(level.fill!! * 100).toInt()} % of the limit"
                } else {
                    "${level.absent} absences · sessions unknown"
                }
                StudyRow("abs:" + level.module, level.module, detail + if (!absences.ruleConfirmed) " · UNVERIFIED" else "", LauncherIcons.Info, if (level.fill != null) bandColor(level.band) else null)
            }
        } else if (day.absences.isEmpty()) {
            listOf(StudyRow("none", "No absence recorded", "Each \"Note: absent <module> <time>\" in your task calendar counts here · UNVERIFIED", LauncherIcons.Info))
        } else {
            day.absences.entries.sortedByDescending { it.value }.map { (module, n) ->
                StudyRow("abs:$module", module, (if (n == 1) "1 absence" else "$n absences") + " recorded · UNVERIFIED (the rule is not confirmed)", LauncherIcons.Info)
            }
        }
}
