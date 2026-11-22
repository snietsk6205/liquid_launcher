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
import androidx.compose.foundation.layout.widthIn
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
        // W7: collapsed, the hub is at most half the screen; it springs to the full height as it expands.
        val collapsed = minOf(fraction, 0.5f)
        val f = collapsed + (fraction - collapsed) * controller.hubExpand.value
        val top = maxOf(status.getTop(density) + margin, bottom - h * f)
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
        // W7: pushing the collapsed hub's list up opens the whole day.
        if (source == NestedScrollSource.UserInput && available.y < -4f && !controller.hubExpanded && controller.hub.value > 0.95f) {
            controller.expandHub(true)
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
    // W7: the text over the hub: chosen, or light where the hub is dark or the dim behind it is strong.
    val ink by controller.data.settings.derive { get(LauncherSettings.StudyHubInk) }
    val dim by controller.data.settings.derive { get(LauncherSettings.StudyHubDim) / 100f }
    val themed = sheetContentColor(glass, lightBackground)
    val color = when (ink) {
        "light" -> Color.White
        "dark" -> Color(0xFF16181C)
        else -> if (dim >= 0.45f) Color.White else themed
    }
    val shape = remember(geometry) { RoundedCornerShape(HubCorner(geometry) { controller.hub.value }) }
    val layer = rememberLayerBackdrop()
    val scroll = rememberScrollState()
    val connection = remember(controller) { HubScroll(controller) }
    val drag = rememberDraggableState { delta -> controller.dragHubBy(delta) }
    Box(Modifier.fillMaxSize()) {
        // The home screen dims in step with the glass rising; a tap outside puts the hub back into the pill.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = controller.hubAmount }
                .background(Color.Black.copy(alpha = dim))
                .pointerInput(controller) { detectTapGestures { controller.closeHub() } },
        )
        Box(
            Modifier
                .hubBounds(geometry, controller)
                .sheetSurface(backdrop, shape, glass, lightBackground, exported = layer)
                .bloomLight()
                .clip(shape)
                .boardPicture(shape) { controller.hubAmount }
                .clickable(interactionSource = null, indication = null) { }
                .customiseOnLongPress(controller, "study", "the hub")
                .draggable(drag, Orientation.Vertical, onDragStopped = { velocity -> controller.settleHub(velocity) })
                .nestedScroll(connection),
        ) {
            // 0.11.0: no redraw on scroll: what scrolls draws without sampling (below), and the header and the bar keep
            // their place on the hub's glass, so their sample does not change while the day scrolls.
            ProvideControls(controller, glass, layer) {
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
private fun HubIcon(icon: Int, description: String, color: Color, size: Dp = 40.dp, onClick: () -> Unit) {
    // W3: a round button of the theme (WM Keyboard's tool circles).
    if (currentTheme != null) {
        GlassButton(null, color, Modifier.size(size), icon = icon, contentDescription = description, onClick = onClick)
        return
    }
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

/** The one-tap action the hub offers right where the thumb already is (it grew out of the pill under it). */
private class HubAction(val label: String, val icon: Int, val accent: Color?, val run: () -> Unit)

@Composable
private fun MainHubButton(action: HubAction) {
    // W3: the theme's main button, in the block's colour as before (Main button colour: the block's).
    if (currentTheme != null) {
        GlassButton(action.label, Color.White, Modifier.fillMaxWidth().height(52.dp), icon = action.icon, emphasis = ControlEmphasis.Main, accent = action.accent, onClick = action.run)
        return
    }
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

// ---- 0.14.0 (D186): Today, reimagined as one question and one button ----

/**
 * Med: "make it simple like the daily check-in, nothing complex … remove all the clutter, keep the note for tonight,
 * remove the timeline and all useless info, I don't want to be overwhelmed; keep Quiz me; add some recommended tasks".
 * Today is: what would you like to study (today's classes as chips, or typed), "Study with Claude" (a 45-minute session
 * on the pill, and Claude opens with the day as context, [HomeController.studyKickoff]), what is recommended
 * ([dev.liquid.launcher.core.data.Recommend]), the absences as plain numbers, Quiz me and the note for tonight.
 * No timeline, blocks, tasks, sync row or other chips any more (study blocks no longer go into the calendar).
 */
@Composable
private fun HubContent(controller: HomeController, color: Color, scroll: ScrollState, modifier: Modifier) {
    val study = controller.data.study
    val day by study.day.collectAsState()
    val loaded by study.loaded.collectAsState()
    val deck by study.deck.collectAsState()
    val focus by study.focus.collectAsState()
    val studied by study.studiedToday.collectAsState()
    val tick = rememberMinuteTick()
    val now = remember(tick, day, focus) { System.currentTimeMillis() }
    val today = remember(tick) { LocalDate.now() }
    val due = remember(deck, today) { dev.liquid.launcher.core.data.FlashcardDeck.due(deck, today).size }
    val headerExam = remember(day, now) { day.exams.firstOrNull { it.begin > now } }
    val recommended = remember(day, due, now, headerExam) {
        dev.liquid.launcher.core.data.Recommend.items(day.feed, day.exams, day.unconfirmedExams, due, now, headerExam?.key)
    }
    val tickStore by controller.data.settings.derive { get(LauncherSettings.StudyRecommendDone) }
    val ticked = remember(tickStore, today) { dev.liquid.launcher.core.data.Recommend.ticked(tickStore, today.toString()) }
    // Today's classes, each once, as chips; the picks and the typed text make what is studied.
    val classes = remember(day) {
        day.classes.filter { !it.allDay }.sortedBy { it.begin }.map { it.title.subject.ifBlank { it.rawTitle }.trim() }.filter { it.isNotEmpty() }.distinct()
    }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var typed by remember { mutableStateOf("") }
    val session = focus?.takeIf { it.isSession && it.until > now }

    Column(modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
        KickoffHeader(headerExam, day.unconfirmedExams, studied, now, color)
        if (!loaded) {
            Column(Modifier.weight(1f).padding(top = 14.dp)) {
                Text(
                    "The study companion is off, or it may not read the calendar yet.",
                    style = studyText(color, 13, alpha = 0.8f), modifier = Modifier.padding(bottom = 10.dp),
                )
                ActionChip("Switch it on", color, filled = true) { controller.setupStudy() }
            }
            KickoffBar(controller, color)
            return@Column
        }
        // Drawn without sampling the glass behind (0.10.4): nothing here redraws the hub's glass while it scrolls.
        WithoutBackdrop {
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                if (session != null) {
                    // A session runs: what, how long is left, and Finish.
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Studying" + (session.subject.takeIf { it.isNotBlank() }?.let { " $it" } ?: "") + " · " + studyDuration(session.until - now) + " left",
                            style = studyText(color, 14, FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        ActionChip("Finish", color, icon = LauncherIcons.Check) { controller.stopStudySession() }
                    }
                }
                Text(
                    "What would you like to study today?",
                    style = studyText(color, 18, FontWeight.SemiBold),
                    modifier = Modifier.padding(top = 16.dp, bottom = 10.dp),
                )
                if (classes.isNotEmpty()) {
                    Text("In class today", style = studyText(color, 12, alpha = 0.7f), modifier = Modifier.padding(bottom = 6.dp))
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy((6 * themeGap).dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        classes.forEach { c ->
                            ActionChip(c, color, filled = c in picked, icon = if (c in picked) LauncherIcons.Check else null) {
                                Haptics.tick(controller.view)
                                picked = if (c in picked) picked - c else picked + c
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                KickoffField(typed, { typed = it }, color, if (classes.isEmpty()) "A chapter, a TD, a project…" else "Or type it: a chapter, a TD…")
                Spacer(Modifier.height(12.dp))
                MainHubButton(
                    HubAction("Study with Claude", LauncherIcons.Sparkle, null) {
                        controller.studyKickoff((picked.toList() + listOfNotNull(typed.trim().ifEmpty { null })).joinToString(", "), recommended.filter { it.key !in ticked }.map { it.text })
                        picked = emptySet()
                        typed = ""
                    },
                )
                if (recommended.isNotEmpty()) {
                    SectionLabel("Recommended", color)
                    recommended.forEach { item ->
                        RecommendedRow(item, item.key in ticked, color,
                            onTick = { done ->
                                controller.data.settings.set(
                                    LauncherSettings.StudyRecommendDone,
                                    dev.liquid.launcher.core.data.Recommend.tick(tickStore, today.toString(), item.key, done),
                                )
                            },
                            onTap = {
                                when (item.kind) {
                                    dev.liquid.launcher.core.data.Recommend.Kind.Cards -> controller.openReview()
                                    // Picks it as what to study (the button then starts it).
                                    else -> typed = item.text
                                }
                            },
                        )
                    }
                }
                val absences = remember(day) { absenceCounts(day) }
                if (absences.isNotEmpty()) {
                    // Med: absences as numbers only (no meter, no limit), where he can find them.
                    Text(
                        "Absences · " + absences.joinToString("  ·  ") { "${it.first} ${it.second}" },
                        style = studyText(color, 12, alpha = 0.7f),
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        KickoffBar(controller, color)
    }
}

/** The handle, "Today" with the date and the minutes studied, and the next exam counted down on the right. */
@Composable
private fun KickoffHeader(exam: StudyEvent?, unconfirmed: Set<String>, studied: Int, now: Long, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp, bottom = 8.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.3f)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Today", style = studyText(color, 20, FontWeight.SemiBold), maxLines = 1)
                val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
                Text(
                    date + if (studied > 0) " · $studied min studied" else "",
                    style = studyText(color, 12, alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (exam != null) {
                val days = (exam.begin - now) / 86_400_000L
                if (days <= 30) {
                    val (count, unit) = examCountdown(exam.begin, now)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            (if (exam.key in unconfirmed) "≈" else "") + count + " " + unit,
                            style = studyText(if (days > 14) color else examColor(days), 15, FontWeight.SemiBold), maxLines = 1,
                        )
                        Text(exam.title.text.ifBlank { exam.rawTitle }, style = studyText(color, 11, alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
                    }
                }
            }
        }
    }
}

/** The field under the chips: what to study, typed. */
@Composable
private fun KickoffField(text: String, onChange: (String) -> Unit, color: Color, hint: String) {
    val (fieldBackground, fieldInk) = fieldStyle(color)
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .then(fieldBackground)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isEmpty()) Text(hint, style = studyText(fieldInk, 14, alpha = 0.6f), maxLines = 1)
        BasicTextField(
            value = text,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = HomeTheme.font, color = fieldInk, fontSize = 14.sp),
            cursorBrush = SolidColor(fieldInk),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "What to study" },
        )
    }
}

/**
 * A recommended task: a tick (Claude's tasks; kept for the day), its text (a tap picks it as what to study; the cards
 * open the quick review), an exam soon in amber, close in red.
 */
@Composable
private fun RecommendedRow(
    item: dev.liquid.launcher.core.data.Recommend.Item,
    done: Boolean,
    color: Color,
    onTick: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    val kind = item.kind
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        when (kind) {
            dev.liquid.launcher.core.data.Recommend.Kind.Claude -> Box(
                Modifier.clip(CircleShape).clickable(onClickLabel = if (done) "Not done" else "Done") { onTick(!done) }.padding(4.dp),
            ) { Tick(done, color) }
            dev.liquid.launcher.core.data.Recommend.Kind.Cards -> Box(Modifier.padding(4.dp)) { LauncherIcon(LauncherIcons.Layers, contentDescription = null, tint = color, size = 18.dp) }
            dev.liquid.launcher.core.data.Recommend.Kind.Exam -> Box(Modifier.padding(4.dp)) {
                LauncherIcon(LauncherIcons.Schedule, contentDescription = null, tint = if (item.urgent) LeaveRed else Color(0xFFFFB74D), size = 18.dp)
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            item.text,
            style = studyText(if (kind == dev.liquid.launcher.core.data.Recommend.Kind.Exam && item.urgent) LeaveRed else color, 14, alpha = if (done) 0.55f else 1f, struck = done),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onTap).padding(vertical = 4.dp),
        )
    }
}

/** The bottom: the prompt templates (Quiz me by default) and the note for tonight, or a question for Claude. */
@Composable
private fun KickoffBar(controller: HomeController, color: Color) {
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
        val now = System.currentTimeMillis()
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy((6 * themeGap).dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            templates.forEach { line ->
                val label = line.substringBefore('|').trim()
                val prompt = line.substringAfter('|', "").trim()
                if (label.isNotEmpty() && prompt.isNotEmpty()) {
                    ActionChip(label, color, icon = LauncherIcons.Sparkle) { controller.askTemplate(prompt, null) }
                }
            }
            // During or just after a class: its board, in its folder (only then).
            controller.classForPhoto(now)?.let { cls ->
                ActionChip("Board · ${cls.title.subject}", color, icon = LauncherIcons.Camera) { controller.boardPhoto() }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (fieldBackground, fieldInk) = fieldStyle(color)
            Row(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(50))
                    .then(fieldBackground)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("Note for tonight, or ask Claude…", style = studyText(fieldInk, 14, alpha = 0.6f), maxLines = 1)
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = HomeTheme.font, color = fieldInk, fontSize = 14.sp),
                        cursorBrush = SolidColor(fieldInk),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { saveNote() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Note or question" },
                    )
                }
                if (text.isNotBlank()) {
                    HubIcon(LauncherIcons.Add, "Save as a note for tonight", color, size = 38.dp) { saveNote() }
                }
            }
            Spacer(Modifier.width(8.dp))
            HubIcon(LauncherIcons.Sparkle, "Ask Claude", color, size = 46.dp) {
                val question = text.trim()
                if (question.isNotEmpty()) {
                    controller.askClaude(controller.withStudyHeader(question))
                    text = ""
                } else {
                    controller.openAskClaude()
                }
            }
        }
    }
}
