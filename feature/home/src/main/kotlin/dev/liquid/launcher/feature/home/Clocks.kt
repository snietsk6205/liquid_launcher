package dev.liquid.launcher.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.designsystem.GlassQuality
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.ColorSetting
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * How one clock looks. Every clock widget carries its own style (in [dev.liquid.launcher.core.data.HomeItem.options]),
 * edited in the clock gallery on the home screen. Values that were never set come from the
 * Clock settings and the theme, so a clock placed before 0.7 looks as it did.
 */
data class ClockStyle(
    /** "digital", "stacked", "minimal", "analog", "words" or "flip". */
    val design: String = "digital",
    /** "theme" (the theme's clock font) or a font id (see [ClockFonts]). */
    val font: String = "theme",
    /** 100..900, or 0 for the theme's clock weight. */
    val weight: Int = 0,
    /** Per cent of the size that fills the widget. */
    val size: Int = 100,
    /** "start", "centre" or "end". */
    val align: String = "centre",
    val h24: Boolean = true,
    val leadingZero: Boolean = true,
    /** Between hours and minutes: ":", "·", " " or "" (digital). */
    val separator: String = ":",
    /** "off", "digits" (digital), "tick" or "sweep" (analog second hand). */
    val seconds: String = "off",
    /** "off", "above" or "below". */
    val date: String = "off",
    val dateFormat: String = "EEEE d MMMM",
    /** Letter spacing in hundredths of an em. */
    val spacing: Int = 0,
    /** "" (automatic), "accent", "tone" (the wallpaper's tone) or "#RRGGBB". */
    val color: String = "",
    /** Minutes (stacked), the second hand, highlighted words and the gradient's end. */
    val color2: String = "accent",
    /** "solid", "gradient" or "glass" (clear at the top, fading down, like a lit pane). */
    val fill: String = "solid",
    /** "none", "tint", "frosted" or "glass". */
    val plate: String = "none",
    val corner: Int = 28,
    val shadow: Boolean = true,
    /** Analog: "none", "ticks", "dots" or "numbers". */
    val dial: String = "ticks",
    /** How digits change: "none", "fade" or "roll". */
    val change: String = "roll",
) {
    /** Only what differs from [base], as text values. */
    fun encode(base: ClockStyle): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        fun put(key: String, value: Any, default: Any) {
            if (value != default) out[key] = value.toString()
        }
        put("design", design, base.design)
        put("font", font, base.font)
        put("weight", weight, base.weight)
        put("size", size, base.size)
        put("align", align, base.align)
        put("h24", h24, base.h24)
        put("zero", leadingZero, base.leadingZero)
        put("sep", separator, base.separator)
        put("seconds", seconds, base.seconds)
        put("date", date, base.date)
        put("dateFormat", dateFormat, base.dateFormat)
        put("spacing", spacing, base.spacing)
        put("color", color, base.color)
        put("color2", color2, base.color2)
        put("fill", fill, base.fill)
        put("plate", plate, base.plate)
        put("corner", corner, base.corner)
        put("shadow", shadow, base.shadow)
        put("dial", dial, base.dial)
        put("change", change, base.change)
        return out
    }

    companion object {
        val Designs = listOf(
            "digital" to "Digital", "stacked" to "Stacked", "minimal" to "Minimal",
            "analog" to "Analog", "words" to "Words", "flip" to "Flip",
        )

        /** The style the Clock settings describe: what a clock without its own values uses. */
        fun fromSettings(config: ClockConfig) = ClockStyle(
            h24 = config.h24,
            size = kotlin.math.round(config.scale * 100).toInt(),
            date = if (config.showDate) "below" else "off",
            plate = if (config.glass) "glass" else "none",
        )

        fun decode(options: Map<String, String>?, base: ClockStyle): ClockStyle {
            if (options.isNullOrEmpty()) return base
            fun text(key: String, default: String) = options[key] ?: default
            fun int(key: String, default: Int) = options[key]?.toIntOrNull() ?: default
            fun bool(key: String, default: Boolean) = options[key]?.toBooleanStrictOrNull() ?: default
            return ClockStyle(
                design = text("design", base.design),
                font = text("font", base.font),
                weight = int("weight", base.weight),
                size = int("size", base.size).coerceIn(20, 200),
                align = text("align", base.align),
                h24 = bool("h24", base.h24),
                leadingZero = bool("zero", base.leadingZero),
                separator = text("sep", base.separator),
                seconds = text("seconds", base.seconds),
                date = text("date", base.date),
                dateFormat = text("dateFormat", base.dateFormat),
                spacing = int("spacing", base.spacing),
                color = text("color", base.color),
                color2 = text("color2", base.color2),
                fill = text("fill", base.fill),
                plate = text("plate", base.plate),
                corner = int("corner", base.corner).coerceIn(0, 60),
                shadow = bool("shadow", base.shadow),
                dial = text("dial", base.dial),
                change = text("change", base.change),
            )
        }
    }
}

/** The fonts a clock can use: the phone's own families, and every font file imported into the launcher. */
object ClockFonts {
    val System = listOf(
        "theme" to "Theme", "default" to "Sans", "thin" to "Thin", "light" to "Light", "medium" to "Medium", "black" to "Black",
        "condensed" to "Condensed", "condensedLight" to "Condensed light", "serif" to "Serif", "mono" to "Mono",
        "serifMono" to "Serif mono", "casual" to "Casual", "cursive" to "Cursive", "smallcaps" to "Small caps",
    )

    private val cache = HashMap<String, FontFamily?>()

    /** Imported font files, as "file:<name>" ids with their names. */
    fun imported(context: android.content.Context): List<Pair<String, String>> =
        java.io.File(context.filesDir, "fonts").listFiles()
            ?.filter { it.isFile && (it.extension.lowercase() == "ttf" || it.extension.lowercase() == "otf") }
            ?.sortedBy { it.name }
            ?.map { "file:${it.name}" to it.nameWithoutExtension }
            .orEmpty()

    fun family(context: android.content.Context, id: String): FontFamily? {
        if (id == "theme") return HomeTheme.clockFont
        return synchronized(cache) {
            cache.getOrPut(id) {
                fun named(name: String) = FontFamily(android.graphics.Typeface.create(name, android.graphics.Typeface.NORMAL))
                when {
                    id.startsWith("file:") -> try {
                        val file = java.io.File(java.io.File(context.filesDir, "fonts"), id.removePrefix("file:"))
                        if (file.exists()) FontFamily(androidx.compose.ui.text.font.Font(file)) else null
                    } catch (e: Exception) {
                        null
                    }
                    id == "thin" -> named("sans-serif-thin")
                    id == "light" -> named("sans-serif-light")
                    id == "medium" -> named("sans-serif-medium")
                    id == "black" -> named("sans-serif-black")
                    id == "condensed" -> named("sans-serif-condensed")
                    id == "condensedLight" -> named("sans-serif-condensed-light")
                    id == "serif" -> FontFamily.Serif
                    id == "mono" -> FontFamily.Monospace
                    id == "serifMono" -> named("serif-monospace")
                    id == "casual" -> named("casual")
                    id == "cursive" -> FontFamily.Cursive
                    id == "smallcaps" -> named("sans-serif-smallcaps")
                    else -> null
                }
            }
        }
    }

    /** A font file was added or removed. */
    fun invalidate() = synchronized(cache) { cache.clear() }
}

/** Current time, updated each second. */
@Composable
private fun rememberSecondTick(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000 - value % 1000 + 5)
        }
    }
    return now
}

/** Current time on every frame, for a sweeping second hand. */
@Composable
private fun rememberFrameTime(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            withFrameMillis { }
            value = System.currentTimeMillis()
        }
    }
    return now
}

private fun parseColor(value: String, auto: Color, tone: Color): Color = when (value) {
    "" -> auto
    "accent" -> HomeTheme.accent
    "tone" -> tone
    else -> ColorSetting.parse(value)?.let { Color(it) } ?: auto
}

/** Everything a face needs to draw text in the clock's style. */
private class ClockInk(
    val family: FontFamily?,
    val weight: FontWeight,
    val primary: Color,
    val secondary: Color,
    val shadow: Shadow?,
    val spacing: Float,
    val fill: String,
) {
    fun brush(color: Color): Brush = when (fill) {
        "gradient" -> Brush.verticalGradient(listOf(color, secondary))
        "glass" -> Brush.verticalGradient(0f to color, 0.55f to color.copy(alpha = color.alpha * 0.72f), 1f to color.copy(alpha = color.alpha * 0.38f))
        else -> SolidColor(color)
    }

    fun style(size: TextUnit, color: Color = primary, lineHeight: Float = 1f): TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size,
        brush = brush(color),
        shadow = shadow,
        letterSpacing = spacing.em,
        lineHeight = lineHeight.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        // Digits of equal width, so the clock does not shift as the time changes.
        fontFeatureSettings = "tnum",
    )

    /** The same metrics in a plain colour, for measuring and for drawing on a canvas. */
    fun plain(size: TextUnit, color: Color = primary): TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size,
        color = color,
        letterSpacing = spacing.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        fontFeatureSettings = "tnum",
    )
}

/**
 * A clock in [style], filling the space it is given. Shared by the home screen's clock
 * widgets and the gallery's previews.
 */
@Composable
fun ClockFace(
    style: ClockStyle,
    contentColor: Color,
    contentShadow: Shadow?,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    padding: Dp = 6.dp,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val plateShape = RoundedCornerShape(style.corner.dp)
    val onGlass = style.plate == "glass" || style.plate == "frosted"
    val auto = when {
        onGlass -> if (glass.lightGlass(Surfaces.Clock, lightBackground)) Color(0xFF16181C) else Color.White
        else -> contentColor
    }
    // The icons' colours are read only by a clock that uses them ("tone"). They ease over 260 ms on every
    // wallpaper change; reading them here made every clock recompose and re-measure on each frame of that
    // easing, competing with the icons' own redraw (0.6.3, D90).
    val usesTone = style.color == "tone" || style.color2 == "tone"
    val tone = if (usesTone && IconPaint.ready) Color(if (lightBackground) minOfLuminance(IconPaint.background, IconPaint.foreground) else maxOfLuminance(IconPaint.background, IconPaint.foreground)) else HomeTheme.accent
    val ink = ClockInk(
        family = ClockFonts.family(context, style.font),
        weight = if (style.weight in 100..900) FontWeight(style.weight) else HomeTheme.clockWeight,
        primary = parseColor(style.color, auto, tone),
        secondary = parseColor(style.color2, HomeTheme.accent, tone),
        shadow = if (style.shadow && !onGlass && style.fill != "glass") contentShadow else null,
        spacing = style.spacing / 100f,
        fill = style.fill,
    )
    val plate = when (style.plate) {
        "tint" -> Modifier.background(ink.primary.copy(alpha = 0.14f), plateShape)
        "frosted" -> if (backdrop != null) {
            Modifier.liquidGlass(backdrop, plateShape, glass.style(Surfaces.Clock, lightBackground).let { it.copy(quality = if (it.quality == GlassQuality.Tint) it.quality else GlassQuality.Frosted) })
        } else Modifier.background(ink.primary.copy(alpha = 0.14f), plateShape)
        "glass" -> if (backdrop != null) {
            Modifier.liquidGlass(backdrop, plateShape, glass.style(Surfaces.Clock, lightBackground))
        } else Modifier.background(ink.primary.copy(alpha = 0.14f), plateShape)
        else -> Modifier
    }
    val horizontal = when (style.align) {
        "start" -> Alignment.Start
        "end" -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .padding(padding)
            .then(plate)
            .clip(plateShape)
            .padding(if (style.plate == "none") 0.dp else 10.dp),
        contentAlignment = when (style.align) {
            "start" -> Alignment.CenterStart
            "end" -> Alignment.CenterEnd
            else -> Alignment.Center
        },
    ) {
        // Read here: inside the column below, the box's own scope is out of reach.
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        val wantsSeconds = style.seconds != "off" && (style.design == "digital" || style.design == "analog")
        val now = when {
            style.design == "analog" && style.seconds == "sweep" -> rememberFrameTime()
            wantsSeconds -> rememberSecondTick()
            else -> rememberMinuteTick()
        }
        val time = remember(now / 1000) { LocalTime.now() }
        val date = if (style.date != "off" && style.design != "minimal") dateText(style.dateFormat, now) else null
        // The date line takes a share of the height; the face gets the rest.
        val dateShare = if (date != null) 0.2f else 0f
        val faceHeight = boxHeight * (1f - dateShare)
        Column(horizontalAlignment = horizontal, verticalArrangement = Arrangement.Center) {
            if (date != null && style.date == "above") DateLine(date, ink, boxWidth, boxHeight * dateShare)
            Box(Modifier.size(boxWidth, faceHeight), contentAlignment = when (style.align) {
                "start" -> Alignment.CenterStart
                "end" -> Alignment.CenterEnd
                else -> Alignment.Center
            }) {
                when (style.design) {
                    "stacked" -> StackedFace(style, ink, time, boxWidth, faceHeight, horizontal)
                    "minimal" -> MinimalFace(style, ink, time, now, boxWidth, faceHeight, horizontal)
                    "analog" -> AnalogFace(style, ink, time, now)
                    "words" -> WordsFace(style, ink, time, boxWidth, faceHeight, horizontal)
                    "flip" -> FlipFace(style, ink, time, boxWidth, faceHeight)
                    else -> DigitalFace(style, ink, time, boxWidth, faceHeight)
                }
            }
            if (date != null && style.date == "below") DateLine(date, ink, boxWidth, boxHeight * dateShare)
        }
    }
}

private fun minOfLuminance(a: Int, b: Int): Int =
    if (androidx.core.graphics.ColorUtils.calculateLuminance(a) < androidx.core.graphics.ColorUtils.calculateLuminance(b)) a else b

private fun maxOfLuminance(a: Int, b: Int): Int =
    if (androidx.core.graphics.ColorUtils.calculateLuminance(a) >= androidx.core.graphics.ColorUtils.calculateLuminance(b)) a else b

private fun dateText(pattern: String, now: Long): String = try {
    // [now] only keys the recomposition; the date is today's.
    LocalDate.now().format(DateTimeFormatter.ofPattern(pattern.ifBlank { "EEEE d MMMM" }, Locale.getDefault())).also { now.hashCode() }
} catch (e: Exception) {
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
}

private fun hours(style: ClockStyle, time: LocalTime): String {
    val h = if (style.h24) time.hour else ((time.hour + 11) % 12 + 1)
    return if (style.leadingZero) "%02d".format(h) else h.toString()
}

private fun minutes(time: LocalTime) = "%02d".format(time.minute)

/**
 * The largest font size at which [template] fits [width] × [height], times the style's size.
 * Measured with the widest digits, so the size does not change as the time does.
 */
@Composable
private fun fitSize(template: String, ink: ClockInk, width: Dp, height: Dp, percent: Int, lines: Int = 1): TextUnit {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val density = LocalDensity.current
    return remember(template, ink.family, ink.weight, ink.spacing, width, height, percent, lines, density) {
        val reference = 100f
        val layout = measurer.measure(template, ink.plain(reference.sp))
        val w = layout.size.width.coerceAtLeast(1)
        val h = layout.size.height.coerceAtLeast(1)
        val scale = with(density) { minOf(width.toPx() / w, height.toPx() / h) }
        (reference * scale * 0.96f * percent / 100f).coerceAtLeast(4f).sp
    }
}

/** One part of the time; rolls or fades when it changes. */
@Composable
private fun Changing(text: String, style: ClockStyle, textStyle: TextStyle) {
    if (style.change == "none") {
        Text(text, style = textStyle, maxLines = 1, softWrap = false)
        return
    }
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            val transform: ContentTransform = if (style.change == "roll") {
                (slideInVertically(tween(MotionScale.ms(320))) { it / 2 } + fadeIn(tween(MotionScale.ms(240)))) togetherWith
                    (slideOutVertically(tween(MotionScale.ms(320))) { -it / 2 } + fadeOut(tween(MotionScale.ms(180))))
            } else {
                fadeIn(tween(MotionScale.ms(260))) togetherWith fadeOut(tween(MotionScale.ms(260)))
            }
            transform.using(SizeTransform(clip = false))
        },
        label = "clock",
    ) { value ->
        Text(value, style = textStyle, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun DigitalFace(style: ClockStyle, ink: ClockInk, time: LocalTime, width: Dp, height: Dp) {
    val showSeconds = style.seconds == "digits"
    val template = (if (style.leadingZero || style.h24) "88" else "8") + style.separator + "88" + if (showSeconds) style.separator + "88" else ""
    val size = fitSize(template, ink, width, height, style.size)
    val text = ink.style(size)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Changing(hours(style, time), style, text)
        if (style.separator.isNotEmpty()) Text(style.separator, style = text.copy(brush = ink.brush(ink.primary.copy(alpha = ink.primary.alpha * 0.8f))), maxLines = 1)
        Changing(minutes(time), style, text)
        if (showSeconds) {
            if (style.separator.isNotEmpty()) Text(style.separator, style = text.copy(brush = ink.brush(ink.primary.copy(alpha = ink.primary.alpha * 0.8f))), maxLines = 1)
            Changing("%02d".format(time.second), style, text.copy(brush = ink.brush(ink.secondary)))
        }
    }
}

@Composable
private fun StackedFace(style: ClockStyle, ink: ClockInk, time: LocalTime, width: Dp, height: Dp, horizontal: Alignment.Horizontal) {
    // Two lines, set tight: each gets a little more than half the height.
    val size = fitSize("88", ink, width, height * 0.56f, style.size)
    Column(horizontalAlignment = horizontal, verticalArrangement = Arrangement.Center) {
        Changing(hours(style, time), style, ink.style(size, lineHeight = 0.9f))
        Changing(minutes(time), style, ink.style(size, ink.secondary, lineHeight = 0.9f))
    }
}

@Composable
private fun MinimalFace(style: ClockStyle, ink: ClockInk, time: LocalTime, now: Long, width: Dp, height: Dp, horizontal: Alignment.Horizontal) {
    val size = fitSize("88:88", ink, width * 0.62f, height * 0.6f, style.size)
    val date = dateText(style.dateFormat, now)
    val dateSize = fitSize(date, ink, width, height * 0.26f, 100)
    Column(horizontalAlignment = horizontal, verticalArrangement = Arrangement.Center) {
        Changing(hours(style, time) + style.separator.ifEmpty { ":" } + minutes(time), style, ink.style(size))
        Text(date, style = ink.style(minOf(dateSize.value, size.value * 0.42f).sp, ink.secondary), maxLines = 1)
    }
}

@Composable
private fun DateLine(date: String, ink: ClockInk, width: Dp, height: Dp) {
    val size = fitSize(date, ink, width * 0.9f, height * 0.8f, 100)
    Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
        Text(date, style = ink.style(size, ink.primary.copy(alpha = ink.primary.alpha * 0.85f)).copy(fontWeight = FontWeight.Normal), maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AnalogFace(style: ClockStyle, ink: ClockInk, time: LocalTime, now: Long) {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val scale = style.size / 100f
    Canvas(Modifier.fillMaxSize()) {
        val radius = minOf(size.width, size.height) / 2f * 0.94f * scale.coerceAtMost(1.06f)
        val centre = Offset(size.width / 2f, size.height / 2f)
        val stroke = (radius * 0.035f).coerceAtLeast(1.5f) * (ink.weight.weight / 400f).coerceIn(0.5f, 2.2f)
        fun at(angle: Float, r: Float) = Offset(centre.x + sin(angle) * r, centre.y - cos(angle) * r)
        when (style.dial) {
            "ticks" -> for (i in 0 until 60) {
                val major = i % 5 == 0
                val a = (i / 60f * 2f * PI).toFloat()
                drawLine(
                    ink.primary.copy(alpha = ink.primary.alpha * if (major) 0.9f else 0.35f),
                    at(a, radius * if (major) 0.86f else 0.93f), at(a, radius),
                    strokeWidth = if (major) stroke * 0.8f else stroke * 0.4f, cap = StrokeCap.Round,
                )
            }
            "dots" -> for (i in 0 until 12) {
                val a = (i / 12f * 2f * PI).toFloat()
                drawCircle(ink.primary.copy(alpha = ink.primary.alpha * if (i % 3 == 0) 0.95f else 0.5f), radius = stroke * if (i % 3 == 0) 1.1f else 0.7f, center = at(a, radius * 0.92f))
            }
            "numbers" -> for (i in 1..12) {
                val a = (i / 12f * 2f * PI).toFloat()
                val layout = measurer.measure(i.toString(), ink.plain((radius * 0.2f / density).sp))
                val p = at(a, radius * 0.84f)
                drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
            }
        }
        val seconds = time.second + if (style.seconds == "sweep") (now % 1000) / 1000f else 0f
        val minute = time.minute + seconds / 60f
        val hour = (time.hour % 12) + minute / 60f
        val hourAngle = (hour / 12f * 2f * PI).toFloat()
        val minuteAngle = (minute / 60f * 2f * PI).toFloat()
        drawLine(ink.primary, at(hourAngle + PI.toFloat(), radius * 0.08f), at(hourAngle, radius * 0.52f), strokeWidth = stroke * 1.9f, cap = StrokeCap.Round)
        drawLine(ink.primary, at(minuteAngle + PI.toFloat(), radius * 0.08f), at(minuteAngle, radius * 0.8f), strokeWidth = stroke * 1.25f, cap = StrokeCap.Round)
        if (style.seconds != "off") {
            val secondAngle = (seconds / 60f * 2f * PI).toFloat()
            drawLine(ink.secondary, at(secondAngle + PI.toFloat(), radius * 0.14f), at(secondAngle, radius * 0.86f), strokeWidth = stroke * 0.55f, cap = StrokeCap.Round)
        }
        drawCircle(if (style.seconds != "off") ink.secondary else ink.primary, radius = stroke * 1.5f, center = centre)
    }
}

private val NumberWords = listOf(
    "twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven",
)

/** The time in words, to the nearest five minutes: the lines and which of them carry the hour. */
internal fun timeInWords(hour: Int, minute: Int): List<String> {
    val rounded = ((minute + 2) / 5 * 5)
    val h = if (rounded > 30) hour + 1 else hour
    val name = NumberWords[h % 12]
    return when (rounded % 60) {
        0 -> listOf(name, "o'clock")
        5 -> listOf("five past", name)
        10 -> listOf("ten past", name)
        15 -> listOf("quarter past", name)
        20 -> listOf("twenty past", name)
        25 -> listOf("twenty-five", "past $name")
        30 -> listOf("half past", name)
        35 -> listOf("twenty-five", "to $name")
        40 -> listOf("twenty to", name)
        45 -> listOf("quarter to", name)
        50 -> listOf("ten to", name)
        else -> listOf("five to", name)
    }
}

@Composable
private fun WordsFace(style: ClockStyle, ink: ClockInk, time: LocalTime, width: Dp, height: Dp, horizontal: Alignment.Horizontal) {
    val lines = remember(time.hour, time.minute) { timeInWords(time.hour, time.minute) }
    // Sized for the longest wording, so the size stays put through the hour.
    val size = fitSize("quarter past", ink, width, height * 0.46f, style.size)
    Column(horizontalAlignment = horizontal, verticalArrangement = Arrangement.Center) {
        lines.forEachIndexed { i, line ->
            Changing(line, style, ink.style(size, if (i == lines.lastIndex) ink.secondary else ink.primary, lineHeight = 1.05f))
        }
    }
}

@Composable
private fun FlipFace(style: ClockStyle, ink: ClockInk, time: LocalTime, width: Dp, height: Dp) {
    val gap = 8.dp
    val cardWidth = (width - gap) / 2 * (style.size / 100f).coerceAtMost(1f)
    val cardHeight = minOf(height, cardWidth * 1.15f)
    val size = fitSize("88", ink, cardWidth * 0.82f, cardHeight * 0.8f, 100)
    val shape = RoundedCornerShape((style.corner * 0.6f).dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        for ((i, part) in listOf(hours(style, time), minutes(time)).withIndex()) {
            if (i == 1) Spacer(Modifier.width(gap))
            Box(
                Modifier
                    .size(cardWidth, cardHeight)
                    .background(ink.primary.copy(alpha = 0.16f), shape)
                    .clip(shape),
                contentAlignment = Alignment.Center,
            ) {
                Changing(part, style.copy(change = if (style.change == "none") "none" else "roll"), ink.style(size, if (i == 1) ink.secondary else ink.primary))
                // The split of a flap display.
                Box(Modifier.size(cardWidth, 1.5.dp).background(Color.Black.copy(alpha = 0.28f)))
            }
        }
    }
}
