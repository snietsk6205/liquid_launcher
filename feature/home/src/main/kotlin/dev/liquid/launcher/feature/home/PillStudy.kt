package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.AppRef
import dev.liquid.launcher.core.data.IconShapes
import dev.liquid.launcher.core.data.StudyDay
import dev.liquid.launcher.core.data.StudyStatus
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** How the pill's study item looks (0.9.1): Settings → Pill → Study item. */
@Immutable
data class StudyItemLook(
    /** "glyph", "app", "picture", "circle" or "text". */
    val look: String = "glyph",
    val glyph: String = "school",
    /** The app whose icon is shown (an AppRef key), for "app". */
    val app: String = "",
    /** A picture file, for "picture". */
    val picture: String = "",
    /** "none" or one of the icon shapes. */
    val frame: String = "none",
    val sizeDp: Int = 24,
    /** Null: the block now or next. */
    val tint: Color? = null,
    /** "segments", "continuous", "block" or "none". */
    val ring: String = "segments",
)

private fun glyphIcon(id: String): Int = when (id) {
    "schedule" -> LauncherIcons.Schedule
    "calendar" -> LauncherIcons.Calendar
    "sparkle" -> LauncherIcons.Sparkle
    "chat" -> LauncherIcons.Chat
    "star" -> LauncherIcons.Star
    "heart" -> LauncherIcons.Heart
    "check" -> LauncherIcons.Check
    "code" -> LauncherIcons.Code
    "layers" -> LauncherIcons.Layers
    else -> LauncherIcons.School
}

/** A Compose shape for a frame id (the icon shapes), or null for no frame. */
private fun frameShape(id: String): Shape? = when (id) {
    "none" -> null
    "circle" -> CircleShape
    else -> GenericShape { size, _ ->
        IconShapes.path(id, size.minDimension)?.let { addPath(it.asComposePath()) } ?: addOval(Rect(0f, 0f, size.width, size.height))
    }
}

/**
 * The pill's study item: what [look] says, with the day's progress as a ring around it (C2), in the colour
 * of the block now or next unless a colour is chosen. [withText] adds the block's name (the "Study block" item).
 */
@Composable
internal fun StudyPillItem(controller: HomeController, look: StudyItemLook, color: Color, apps: Map<AppRef, AppEntry>, style: TextStyle, withText: Boolean) {
    val study = controller.data.study
    val loaded by study.loaded.collectAsState()
    val day by study.day.collectAsState()
    val tick = rememberMinuteTick()
    val now = remember(tick, day) { System.currentTimeMillis() }
    val glance = remember(loaded, day, now) { if (loaded) studyGlance(day, now) else null }
    val block = when (glance) {
        is StudyGlance.Running -> glance.block
        is StudyGlance.Next -> glance.block
        is StudyGlance.Unmarked -> glance.block
        is StudyGlance.Tomorrow -> glance.block
        else -> null
    }
    val accent = look.tint ?: block?.let { eventColor(it) } ?: HomeTheme.accent
    if (look.look == "text") {
        // The 0.6.3 item: the glance's ring and a few words.
        Row(Modifier.widthIn(max = 170.dp), verticalAlignment = Alignment.CenterVertically) {
            if (glance != null) StudyRing(glance, color, (look.sizeDp * 0.8f).dp) else LauncherIcon(LauncherIcons.School, contentDescription = "Today", tint = color, size = look.sizeDp.dp)
            if (glance != null) {
                Spacer(Modifier.width(6.dp))
                Text(glanceText(glance, short = true), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
    val item = look.sizeDp.dp
    val showRing = loaded && look.ring != "none" && look.look != "circle"
    val stroke = 2.5.dp
    val outer = if (showRing) item + 10.dp else item
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(outer), contentAlignment = Alignment.Center) {
            if (showRing) StudyItemRing(look.ring, day, now, color, accent, outer, stroke)
            StudyItemFace(look, accent, color, apps, day, Modifier.size(item))
        }
        if (withText && glance != null) {
            Spacer(Modifier.width(6.dp))
            Text(glanceText(glance, short = true), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp))
        }
    }
}

@Composable
private fun StudyItemFace(look: StudyItemLook, accent: Color, color: Color, apps: Map<AppRef, AppEntry>, day: StudyDay, modifier: Modifier) {
    val shape = remember(look.frame) { frameShape(look.frame) }
    val framed = shape != null && look.look == "glyph"
    Box(
        modifier.then(if (framed && shape != null) Modifier.clip(shape).background(accent.copy(alpha = 0.9f)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when (look.look) {
            "app" -> {
                val app = remember(look.app, apps) { AppRef.parse(look.app)?.let { apps[it] } }
                if (app != null) {
                    AppIcon(app, true, Modifier.fillMaxSize().then(if (shape != null) Modifier.clip(shape) else Modifier), "Today")
                } else {
                    LauncherIcon(glyphIcon(look.glyph), contentDescription = "Today", tint = color, size = look.sizeDp.dp)
                }
            }
            "picture" -> {
                val picture = rememberPicture(look.picture)
                if (picture != null) {
                    Image(
                        picture, contentDescription = "Today", contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(shape ?: CircleShape),
                    )
                } else {
                    LauncherIcon(glyphIcon(look.glyph), contentDescription = "Today", tint = color, size = look.sizeDp.dp)
                }
            }
            "circle" -> LiquidCircle(day, accent, color, Modifier.fillMaxSize())
            else -> LauncherIcon(
                glyphIcon(look.glyph), contentDescription = "Today",
                tint = if (framed) readableOn(accent) else color,
                size = if (framed) look.sizeDp.dp * 0.62f else look.sizeDp.dp,
            )
        }
    }
}

/** A picture file as an image, decoded off the main thread; null while loading or when it cannot be read. */
@Composable
private fun rememberPicture(path: String): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = if (path.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    return bitmap
}

/** The ring around the study item: the day by block (C2), the day as one arc, or the block now. */
@Composable
private fun StudyItemRing(style: String, day: StudyDay, now: Long, color: Color, accent: Color, size: Dp, stroke: Dp) {
    if (style == "segments") {
        DayRing(day, now, color, size, showCount = false, strokeFraction = stroke / size)
        return
    }
    val total = day.blocks.size
    val target = when (style) {
        "continuous" -> if (total == 0) 0f else day.blocksDone.toFloat() / total
        else -> day.current(now)?.let { b -> ((now - b.begin).toFloat() / (b.end - b.begin).coerceAtLeast(1)).coerceIn(0f, 1f) } ?: 0f
    }
    val shown by animateFloatAsState(target, spring(dampingRatio = 0.8f, stiffness = 200f), label = "studyItemRing")
    Canvas(Modifier.size(size)) {
        val w = stroke.toPx()
        val topLeft = Offset(w / 2f, w / 2f)
        val arc = Size(this.size.width - w, this.size.height - w)
        drawArc(color.copy(alpha = 0.18f), -90f, 360f, false, topLeft, arc, style = Stroke(w))
        if (shown > 0.001f) drawArc(accent, -90f, 360f * shown, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/**
 * A circle of liquid that rises as the day's blocks are done; it sloshes when the level changes and settles
 * flat-ish, with no animation running while nothing changes.
 */
@Composable
private fun LiquidCircle(day: StudyDay, accent: Color, color: Color, modifier: Modifier) {
    val total = day.blocks.size
    val target = if (total == 0) 0f else day.blocks.count { it.status == StudyStatus.Done }.toFloat() / total
    val level by animateFloatAsState(target, spring(dampingRatio = 0.35f, stiffness = 90f), label = "liquidLevel")
    Canvas(modifier) {
        val r = this.size.minDimension / 2f
        val c = this.center
        val clip = Path().apply { addOval(Rect(c, r)) }
        clipPath(clip) {
            drawCircle(color.copy(alpha = 0.12f), r, c)
            if (level > 0.001f) {
                val slosh = (abs(target - level) * 0.7f).coerceAtMost(0.2f)
                val amp = this.size.height * (0.03f + slosh)
                val y0 = this.size.height * (1f - level.coerceIn(0f, 1.05f))
                val wave = Path().apply {
                    moveTo(0f, this@Canvas.size.height)
                    lineTo(0f, y0)
                    val steps = 24
                    for (i in 0..steps) {
                        val x = this@Canvas.size.width * i / steps
                        val y = y0 + amp * sin(i.toFloat() / steps * 2f * PI.toFloat() * 1.25f + level * 9f)
                        lineTo(x, y)
                    }
                    lineTo(this@Canvas.size.width, this@Canvas.size.height)
                    close()
                }
                drawPath(wave, accent)
            }
        }
        drawCircle(color.copy(alpha = 0.55f), r - 0.75.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
    }
}
