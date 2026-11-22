package dev.liquid.launcher.feature.home

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import dev.liquid.launcher.core.data.FlashcardDeck
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.data.PillSlot
import dev.liquid.launcher.core.data.StudyDay
import dev.liquid.launcher.core.data.StudyFocus
import dev.liquid.launcher.core.data.StudyStatus
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Settings → Pill → Layout, the live item's options (0.13.0, D178). */
@Immutable
data class LiveLook(
    val focus: Boolean = true,
    val classes: Boolean = true,
    val leadMinutes: Int = 60,
    val music: Boolean = true,
    val cards: Boolean = true,
    val shape: Boolean = true,
)

/** What the pill's live item shows. [key] tells two of them apart (a new key is "something happened"). */
internal sealed interface LiveNow {
    val key: String

    /** A focus timer on [subject]: counts down to [end] (the block's end), else up from [started]. */
    data class Focus(val subject: String, val started: Long, val end: Long?, val color: Color?) : LiveNow {
        override val key: String get() = "focus"
    }

    data class Running(val id: String, val subject: String, val minutesLeft: Int, val progress: Float, val color: Color?) : LiveNow {
        override val key: String get() = "run:$id"
    }

    data class Next(val id: String, val subject: String, val minutesUntil: Int, val color: Color?) : LiveNow {
        override val key: String get() = "next:$id"
    }

    data class Music(val track: NowPlaying.Track) : LiveNow {
        override val key: String get() = "music:" + track.pkg + (if (track.playing) "" else ":paused")
    }

    data class Cards(val due: Int) : LiveNow {
        override val key: String get() = "cards"
    }
}

/** A paused track stays this long on the pill (uptime ms), then the item moves on. */
private const val PausedStays = 10 * 60_000L

/**
 * What the live item shows at [now] (wall clock) and [uptime], in this order: the focus timer; a class or block in a
 * quarter of an hour or less (it beats music: you may have to go); what is playing; the block or class running; the
 * next one within [LiveLook.leadMinutes]; a track paused a moment ago; the flashcards due. Blocks and classes are
 * left to the study item when the pill has one with its text ([withBlocks] false).
 */
internal fun liveNow(
    look: LiveLook,
    day: StudyDay,
    loaded: Boolean,
    focus: StudyFocus?,
    track: NowPlaying.Track?,
    due: Int,
    now: Long,
    uptime: Long,
    withBlocks: Boolean,
): LiveNow? {
    if (look.focus && focus != null) {
        val block = day.blocks.firstOrNull { it.key == focus.eventKey }
        // 0.14.0 (D184): a study session has no block: its own subject and end.
        val end = block?.end ?: focus.until.takeIf { it > 0 }
        return LiveNow.Focus(block?.title?.subject?.ifBlank { null } ?: focus.subject.ifBlank { "Focus" }, focus.startedAt, end?.takeIf { it > now }, block?.let { eventColor(it) })
    }
    var running: LiveNow.Running? = null
    var next: LiveNow.Next? = null
    if (look.classes && loaded && withBlocks) {
        val events = (day.blocks.filter { it.status == StudyStatus.Open } + day.classes).filter { !it.allDay }.distinctBy { it.key }
        events.filter { now >= it.begin && now < it.end }.maxByOrNull { it.begin }?.let { e ->
            val length = (e.end - e.begin).coerceAtLeast(1)
            running = LiveNow.Running(e.key, e.title.subject.ifBlank { e.rawTitle }, ((e.end - now + 59_999) / 60_000).toInt(), ((now - e.begin).toFloat() / length).coerceIn(0f, 1f), eventColor(e))
        }
        events.filter { it.begin > now }.minByOrNull { it.begin }?.let { e ->
            val minutes = ((e.begin - now + 59_999) / 60_000).toInt()
            if (minutes <= look.leadMinutes) next = LiveNow.Next(e.key, e.title.subject.ifBlank { e.rawTitle }, minutes, eventColor(e))
        }
    }
    val soon = next
    if (soon != null && soon.minutesUntil <= 15) return soon
    val music = track?.takeIf { look.music && (it.playing || uptime - it.changedAt < PausedStays) }
    if (music != null && music.playing) return LiveNow.Music(music)
    running?.let { return it }
    next?.let { return it }
    if (music != null) return LiveNow.Music(music)
    if (look.cards && due > 0) return LiveNow.Cards(due)
    return null
}

/**
 * 0.13.0 (D178, Med: "the pill shows a focus timer, a countdown to your next class or what's playing, and changes shape
 * when something happens"; "a due-cards count on the pill; long-press it for a quick review sheet"). One capsule inside
 * the pill: its content slides and its width springs to the new content when something changes, and the pill itself
 * swells and settles ([HomeController.popPill]). The flashcards due ride along as a small count on the capsule when it
 * shows something else; a tap or a long press on them opens the quick review. Takes no room while nothing is live.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RowScope.LivePillItem(controller: HomeController, config: PillConfig, color: Color, style: TextStyle, interactive: Boolean) {
    val look = config.live
    val study = controller.data.study
    val loaded by study.loaded.collectAsState()
    val day by study.day.collectAsState()
    val focus by study.focus.collectAsState()
    val deck by study.deck.collectAsState()
    val tick = rememberMinuteTick()
    val today = remember(tick) { LocalDate.now() }
    val due = remember(deck, today, look.cards) { if (look.cards) FlashcardDeck.due(deck, today).size else 0 }
    val track by rememberNowPlaying(look.music && controller.live)
    val withBlocks = PillSlot.Study !in config.slots
    val real = remember(look, day, loaded, focus, track, due, tick, withBlocks) {
        liveNow(look, day, loaded, focus, track, due, System.currentTimeMillis(), SystemClock.uptimeMillis(), withBlocks)
    }
    // A settings preview shows each kind in turn, so the options (and the change of shape) are seen working.
    val shown = if (controller.live) real else rememberLiveDemo(look)
    val badge = if (controller.live && look.cards && due > 0 && shown !is LiveNow.Cards) due else 0

    // Something happened: the pill swells and settles (not for what was already there when the home screen came, nor
    // for the study day still loading in its first seconds).
    val composedAt = remember { SystemClock.uptimeMillis() }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(shown?.key) {
        val settled = SystemClock.uptimeMillis() - composedAt > 2000
        if (started && settled && look.shape && controller.morph() < 0.01f) controller.popPill()
        started = true
    }

    val context = LocalContext.current
    // 0.13.1: while the study hub, search or the drawer covers the pill, its seconds wait (they tick by the minute): each
    // second redrew the home screen under the glass, and so all the glass over it (the hub ran at 100 to 112 ms a frame
    // in Med's log, 88 to 92 % of them late). A boolean, so the pill does not recompose on every frame of those motions.
    val covered by androidx.compose.runtime.remember(controller) {
        androidx.compose.runtime.derivedStateOf { controller.hubAmount > 0.02f || controller.morph() > 0.02f }
    }
    val inset = (config.heightDp * 0.17f).coerceIn(5f, 12f).dp
    val accentTarget = when (shown) {
        is LiveNow.Focus -> shown.color ?: HomeTheme.accent
        is LiveNow.Running -> shown.color ?: HomeTheme.accent
        is LiveNow.Next -> shown.color ?: HomeTheme.accent
        is LiveNow.Cards -> HomeTheme.accent
        else -> color
    }
    val accent by animateColorAsState(accentTarget, spring(dampingRatio = 1f, stiffness = MotionScale.stiffness(300f)), label = "liveAccent")

    AnimatedVisibility(
        visible = shown != null || badge > 0,
        enter = fadeIn(tween(200)) + expandHorizontally(spring(dampingRatio = 0.8f, stiffness = MotionScale.stiffness(380f))),
        exit = fadeOut(tween(160)) + shrinkHorizontally(spring(dampingRatio = 1f, stiffness = MotionScale.stiffness(420f))),
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .padding(vertical = inset)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (shown is LiveNow.Music) 0.10f else 0.2f))
                .pillItemPress(
                    PillSlot.Live, config, controller, interactive,
                    onLongClick = when (shown) {
                        is LiveNow.Cards, null -> ({ controller.openReview() })
                        else -> null
                    },
                ) {
                    when (shown) {
                        is LiveNow.Music -> NowPlaying.open(context) { pkg ->
                            context.packageManager.getLaunchIntentForPackage(pkg)?.let { controller.start(it) }
                        }
                        is LiveNow.Cards, null -> controller.openReview()
                        else -> controller.run(LauncherAction.Today)
                    }
                }
                .padding(start = 9.dp, end = if (badge > 0) 3.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val text = style.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
            AnimatedContent(
                targetState = shown,
                contentKey = { it?.key },
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 50)) +
                        slideInVertically(spring(dampingRatio = 0.8f, stiffness = MotionScale.stiffness(420f))) { it / 2 } +
                        scaleIn(spring(dampingRatio = 0.7f, stiffness = MotionScale.stiffness(420f)), initialScale = 0.85f)) togetherWith
                        (fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 2 }) using
                        SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.78f, stiffness = MotionScale.stiffness(380f)) }
                },
                label = "live",
            ) { now -> LiveFace(now, color, accent, text, interactive, covered) }
            if (badge > 0) {
                Spacer(Modifier.width(6.dp))
                CardsBadge(badge, interactive) { controller.openReview() }
            }
        }
    }
}

@Composable
private fun LiveFace(now: LiveNow?, color: Color, accent: Color, text: TextStyle, interactive: Boolean, covered: Boolean = false) {
    when (now) {
        null -> Box(Modifier.size(0.dp))
        is LiveNow.Focus -> Row(verticalAlignment = Alignment.CenterVertically) {
            // Every second in sight; by the minute while covered ([covered]), and at once again when uncovered.
            val clock by produceState(System.currentTimeMillis(), covered) {
                value = System.currentTimeMillis()
                while (true) {
                    val t = System.currentTimeMillis()
                    delay(if (covered) 60_000 - t % 60_000 + 5 else 1000 - t % 1000 + 5)
                    value = System.currentTimeMillis()
                }
            }
            val end = now.end
            val progress = if (end != null && end > now.started) ((clock - now.started).toFloat() / (end - now.started)).coerceIn(0f, 1f) else null
            LiveRing(progress ?: 1f, accent, color, 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(if (end != null) clockText(end - clock) else clockText(clock - now.started), style = text, maxLines = 1)
        }
        is LiveNow.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
            LiveRing(now.progress, accent, color, 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(now.subject, style = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 92.dp))
            Text(" · " + minutesText(now.minutesLeft), style = text.copy(color = text.color.copy(alpha = 0.75f)), maxLines = 1)
        }
        is LiveNow.Next -> Row(verticalAlignment = Alignment.CenterVertically) {
            LauncherIcon(LauncherIcons.Schedule, contentDescription = null, tint = accent, size = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(now.subject, style = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 92.dp))
            Text(" in " + minutesText(now.minutesUntil), style = text.copy(color = text.color.copy(alpha = 0.75f)), maxLines = 1)
        }
        is LiveNow.Music -> Row(verticalAlignment = Alignment.CenterVertically) {
            val art = now.track.art
            if (art != null) {
                Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)))
            } else {
                LauncherIcon(LauncherIcons.Music, contentDescription = null, tint = color, size = 18.dp)
            }
            Spacer(Modifier.width(7.dp))
            Text(now.track.title, style = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
            PlayPause(now.track.playing, color, interactive) { NowPlaying.toggle() }
        }
        is LiveNow.Cards -> Row(verticalAlignment = Alignment.CenterVertically) {
            LauncherIcon(LauncherIcons.Layers, contentDescription = null, tint = accent, size = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(if (now.due == 1) "1 card" else "${now.due} cards", style = text, maxLines = 1)
        }
    }
}

/** "24:13", or "1:02:03" past an hour. */
private fun clockText(millis: Long): String {
    val s = (millis.coerceAtLeast(0) + 999) / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** "12 min", "1 h", "1 h 05". */
private fun minutesText(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    return when {
        m < 60 -> "$m min"
        m % 60 == 0 -> "${m / 60} h"
        else -> "${m / 60} h %02d".format(m % 60)
    }
}

/** A small ring filling with [progress] in [accent] over a faint track. */
@Composable
private fun LiveRing(progress: Float, accent: Color, track: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = 2.4.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(track.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(accent, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Play or pause, drawn (two bars or a rounded triangle), with its own tap. */
@Composable
private fun PlayPause(playing: Boolean, color: Color, interactive: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxHeight()
            .then(if (interactive) Modifier.squishClickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = if (playing) "Pause" else "Play" }
            .padding(start = 8.dp, end = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val w = size.width
            val h = size.height
            if (playing) {
                val bar = w * 0.28f
                drawRoundRect(color, Offset(w * 0.12f, 0f), Size(bar, h), androidx.compose.ui.geometry.CornerRadius(bar / 2))
                drawRoundRect(color, Offset(w * 0.88f - bar, 0f), Size(bar, h), androidx.compose.ui.geometry.CornerRadius(bar / 2))
            } else {
                val path = Path().apply {
                    moveTo(w * 0.18f, 0f)
                    lineTo(w * 0.95f, h / 2f)
                    lineTo(w * 0.18f, h)
                    close()
                }
                drawPath(path, color)
            }
        }
    }
}

/** The flashcards due, as a small count; a tap or a long press opens the quick review. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardsBadge(count: Int, interactive: Boolean, onOpen: () -> Unit) {
    Box(
        Modifier
            .fillMaxHeight()
            .then(if (interactive) Modifier.combinedClickable(onClick = onOpen, onLongClick = onOpen) else Modifier)
            .semantics { contentDescription = "$count flashcards due: open the quick review" }
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .widthIn(min = 20.dp)
                .clip(CircleShape)
                .background(HomeTheme.accent)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (count > 99) "99+" else count.toString(),
                style = TextStyle(fontFamily = HomeTheme.font, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

/** For a settings preview: each kind the options allow, in turn, every 2.6 s. */
@Composable
private fun rememberLiveDemo(look: LiveLook): LiveNow? {
    val kinds = remember(look) {
        buildList {
            if (look.focus) add(0)
            if (look.classes) add(1)
            if (look.music) add(2)
            if (look.cards) add(3)
        }
    }
    if (kinds.isEmpty()) return null
    val step by produceState(0, kinds) {
        while (true) {
            delay(2600)
            value++
        }
    }
    val start = remember { System.currentTimeMillis() }
    return when (kinds[step % kinds.size]) {
        0 -> LiveNow.Focus("Focus", start - 6 * 60_000L, start + 24 * 60_000L, null)
        1 -> LiveNow.Next("demo", "Analyse", 12, null)
        2 -> LiveNow.Music(NowPlaying.Track("Your song", "", true, "", null, 0L))
        else -> LiveNow.Cards(3)
    }
}
