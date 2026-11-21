package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.ColorSetting

/** A clock being styled in the gallery: the widget draws [style] live until it is saved or dropped. */
@Stable
class ClockStudioState(
    val itemId: String,
    /** What the Clock settings give a clock without its own values. */
    val base: ClockStyle,
    initial: ClockStyle,
) {
    val before = initial
    var style by mutableStateOf(initial)
    var tab by mutableStateOf("text")

    /** Bumped when a font file was imported, so the font row lists it. */
    var fontsVersion by mutableIntStateOf(0)
}

private val Swatches = listOf(
    "" to "Auto", "accent" to "Accent", "tone" to "Wallpaper",
    "#FFFFFF" to "", "#16181C" to "", "#FF6B6B" to "", "#FFA94D" to "", "#FFD43B" to "", "#69DB7C" to "",
    "#38D9A9" to "", "#4DABF7" to "", "#748FFC" to "", "#B197FC" to "", "#F783AC" to "",
)

private val DateFormats = listOf(
    "EEEE d MMMM" to "Saturday 14 November",
    "EEE d MMM" to "Sat 14 Nov",
    "d MMMM yyyy" to "14 November 2026",
    "dd.MM.yyyy" to "14.11.2026",
    "EEEE" to "Saturday",
)

/**
 * The clock gallery: a glass sheet at the bottom of the home screen with the designs as live
 * miniatures and everything about the clock below them (font, weight, size, spacing,
 * alignment, colours, fill, plate, date, seconds, how digits change). The clock on the page
 * behind shows every change as it is made. Each clock keeps its own style.
 */
@Composable
internal fun ClockStudio(
    state: ClockStudioState,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    controller: HomeController,
) {
    val context = LocalContext.current
    val color = sheetContentColor(glass, lightBackground)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.enter()) }
    val style = state.style
    fun set(change: ClockStyle.() -> ClockStyle) {
        state.style = state.style.change()
    }
    val shape = RoundedCornerShape(32.dp)
    // The page behind stays visible (the clock on it is the preview) but does not take touches.
    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }) {
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
                .sheetSurface(backdrop, shape, glass, lightBackground)
                .bloomLight()
                .clip(shape)
                .boardPicture(shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Clock", modifier = Modifier.weight(1f).padding(start = 4.dp), style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
                StudioChip("Reset", false, color) { state.style = state.base }
                Spacer(Modifier.width(6.dp))
                StudioChip("Cancel", false, color) { controller.closeClockStudio(save = false) }
                Spacer(Modifier.width(6.dp))
                StudioChip("Done", true, color) { controller.closeClockStudio(save = true) }
            }
            Spacer(Modifier.height(10.dp))

            // The gallery: every design drawn live in this clock's own font and colours.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((id, name) in ClockStyle.Designs) {
                    val on = style.design == id
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(color.copy(alpha = if (on) 0.2f else 0.07f))
                            .then(if (on) Modifier.border(1.5.dp, color.copy(alpha = 0.8f), RoundedCornerShape(18.dp)) else Modifier)
                            .clickable { set { copy(design = id) } }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(width = 96.dp, height = 62.dp)) {
                            ClockFace(
                                // 0.10.2: Stacked shows its own default (a light tint of the wallpaper) until a colour is chosen.
                                style = style.copy(design = id, plate = "none", date = "off", size = 100, color = if (id == "glance") style.color else style.color.ifEmpty { ColorSetting.format(colorToArgb(color)) }, seconds = "off", change = "none", shadow = false),
                                contentColor = color,
                                contentShadow = null,
                                glass = glass,
                                lightBackground = lightBackground,
                                backdrop = null,
                                padding = 2.dp,
                            )
                        }
                        Text(name, style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((id, name) in listOf("text" to "Text", "colours" to "Colours", "surface" to "Surface", "details" to "Details")) {
                    StudioChip(name, state.tab == id, color) { state.tab = id }
                }
            }
            Spacer(Modifier.height(8.dp))

            Column(Modifier.fillMaxWidth().height(208.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.tab) {
                    "text" -> {
                        val fonts = remember(state.fontsVersion) { ClockFonts.System + ClockFonts.imported(context) }
                        ChipRow("Font", color) {
                            for ((id, name) in fonts) StudioChip(name, style.font == id, color) { set { copy(font = id) } }
                            StudioChip("+ Import a font file", false, color) {
                                controller.pickFont { uri ->
                                    val id = uri?.let { importFont(context, it) }
                                    if (id == null) {
                                        if (uri != null) controller.toast("That file is not a font the phone can use")
                                    } else {
                                        state.fontsVersion++
                                        set { copy(font = id) }
                                    }
                                }
                            }
                        }
                        StudioSlider("Weight", style.weight, 0, 900, 100, color, { if (it == 0) "Theme" else it.toString() }) { set { copy(weight = it) } }
                        StudioSlider("Size", style.size, 40, 160, 5, color, { "$it %" }) { set { copy(size = it) } }
                        StudioSlider("Spacing", style.spacing, -6, 30, 1, color, { it.toString() }) { set { copy(spacing = it) } }
                        ChipRow("Align", color) {
                            for ((id, name) in listOf("start" to "Left", "centre" to "Centre", "end" to "Right")) {
                                StudioChip(name, style.align == id, color) { set { copy(align = id) } }
                            }
                        }
                    }
                    "colours" -> {
                        SwatchRow("Colour", style.color, color) { set { copy(color = it) } }
                        SwatchRow(
                            when (style.design) {
                                "stacked", "flip" -> "Minutes"
                                "analog" -> "Second hand"
                                "words" -> "Hour"
                                "minimal" -> "Date"
                                else -> "Second colour"
                            },
                            style.color2, color,
                        ) { set { copy(color2 = it) } }
                        ChipRow("Fill", color) {
                            for ((id, name) in listOf("solid" to "Solid", "gradient" to "Gradient to the second colour", "glass" to "Glass")) {
                                StudioChip(name, style.fill == id, color) { set { copy(fill = id) } }
                            }
                        }
                        ChipRow("Shadow", color) {
                            StudioChip("On", style.shadow, color) { set { copy(shadow = true) } }
                            StudioChip("Off", !style.shadow, color) { set { copy(shadow = false) } }
                        }
                    }
                    "surface" -> {
                        ChipRow("Behind", color) {
                            for ((id, name) in listOf("none" to "Nothing", "tint" to "Tint", "frosted" to "Frosted glass", "glass" to "Liquid glass")) {
                                StudioChip(name, style.plate == id, color) { set { copy(plate = id) } }
                            }
                        }
                        if (style.plate != "none") {
                            StudioSlider("Corners", style.corner, 0, 60, 2, color, { "$it dp" }) { set { copy(corner = it) } }
                        }
                        Text(
                            "Liquid glass bends and blurs the wallpaper behind the clock. Blur, bending and tint of the clock's glass are in Settings → Liquid glass → Clock.",
                            style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.7f), fontSize = 12.sp),
                        )
                    }
                    else -> {
                        if (style.design != "analog" && style.design != "words") {
                            ChipRow("Hours", color) {
                                StudioChip("24", style.h24, color) { set { copy(h24 = true) } }
                                StudioChip("12", !style.h24, color) { set { copy(h24 = false) } }
                                StudioChip("Leading zero", style.leadingZero, color) { set { copy(leadingZero = !leadingZero) } }
                            }
                        }
                        if (style.design == "digital" || style.design == "minimal") {
                            ChipRow("Between", color) {
                                for ((id, name) in listOf(":" to ":", "·" to "·", " " to "Space", "" to "Nothing")) {
                                    StudioChip(name, style.separator == id, color) { set { copy(separator = id) } }
                                }
                            }
                        }
                        if (style.design == "digital") {
                            ChipRow("Seconds", color) {
                                StudioChip("Off", style.seconds != "digits", color) { set { copy(seconds = "off") } }
                                StudioChip("Shown", style.seconds == "digits", color) { set { copy(seconds = "digits") } }
                            }
                        }
                        if (style.design == "analog") {
                            ChipRow("Seconds", color) {
                                for ((id, name) in listOf("off" to "Off", "tick" to "Ticking", "sweep" to "Sweeping")) {
                                    StudioChip(name, style.seconds == id, color) { set { copy(seconds = id) } }
                                }
                            }
                            ChipRow("Dial", color) {
                                for ((id, name) in listOf("none" to "None", "ticks" to "Ticks", "dots" to "Dots", "numbers" to "Numbers")) {
                                    StudioChip(name, style.dial == id, color) { set { copy(dial = id) } }
                                }
                            }
                        }
                        if (style.design != "minimal") {
                            ChipRow("Date", color) {
                                for ((id, name) in listOf("off" to "Off", "above" to "Above", "below" to "Below")) {
                                    StudioChip(name, style.date == id, color) { set { copy(date = id) } }
                                }
                            }
                        }
                        if (style.date != "off" || style.design == "minimal") {
                            ChipRow("Format", color) {
                                for ((pattern, sample) in DateFormats) StudioChip(sample, style.dateFormat == pattern, color) { set { copy(dateFormat = pattern) } }
                            }
                        }
                        if (style.design != "analog") {
                            ChipRow("Change", color) {
                                for ((id, name) in listOf("roll" to "Roll", "fade" to "Fade", "none" to "Cut")) {
                                    StudioChip(name, style.change == id, color) { set { copy(change = id) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun colorToArgb(color: Color): Int =
    android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())

/** Copies a picked font file into the launcher; returns its font id, or null when the phone cannot load it. */
private fun importFont(context: android.content.Context, uri: android.net.Uri): String? = try {
    val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "font.ttf"
    val clean = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").let { if (it.lowercase().endsWith(".otf") || it.lowercase().endsWith(".ttf")) it else "$it.ttf" }
    val dir = java.io.File(context.filesDir, "fonts").apply { mkdirs() }
    val file = java.io.File(dir, clean)
    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
    try {
        android.graphics.Typeface.createFromFile(file)
        ClockFonts.invalidate()
        "file:$clean"
    } catch (e: Exception) {
        file.delete()
        null
    }
} catch (e: Exception) {
    DebugLog.error("Clock", "font import failed: $e")
    null
}

/** A chip of the gallery: a [GlassButton] in the look of Settings → Buttons (0.6.4); [on] when chosen. */
@Composable
private fun StudioChip(label: String, on: Boolean, color: Color, onClick: () -> Unit) {
    GlassButton(label, color, emphasis = if (on) ControlEmphasis.Selected else ControlEmphasis.Normal, onClick = onClick)
}

/** A label and a row of chips that scrolls sideways when it does not fit. */
@Composable
private fun ChipRow(label: String, color: Color, chips: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(66.dp), maxLines = 1, style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.8f), fontSize = 13.sp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
    }
}

@Composable
private fun StudioSlider(label: String, value: Int, min: Int, max: Int, step: Int, color: Color, text: (Int) -> String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(66.dp), maxLines = 1, style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.8f), fontSize = 13.sp))
        Slider(
            value = value.toFloat(),
            onValueChange = { raw -> onChange(((raw / step).toInt() * step).coerceIn(min, max)) },
            valueRange = min.toFloat()..max.toFloat(),
            modifier = Modifier.weight(1f).height(34.dp),
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color.copy(alpha = 0.8f), inactiveTrackColor = color.copy(alpha = 0.2f)),
        )
        Text(text(value), modifier = Modifier.width(58.dp).padding(start = 8.dp), maxLines = 1, style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 13.sp))
    }
}

/** Automatic, the accent, the wallpaper's tone, a row of fixed colours, and any hue from the slider underneath. */
@Composable
private fun SwatchRow(label: String, value: String, color: Color, onPick: (String) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.width(66.dp), maxLines = 1, style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.8f), fontSize = 13.sp))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                for ((id, name) in Swatches) {
                    if (name.isNotEmpty()) {
                        StudioChip(name, value == id, color) { onPick(id) }
                    } else {
                        val swatch = ColorSetting.parse(id)?.let { Color(it) } ?: Color.Gray
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .then(if (value.equals(id, ignoreCase = true)) Modifier.border(2.5.dp, color, CircleShape) else Modifier.border(1.dp, color.copy(alpha = 0.3f), CircleShape))
                                .clickable { onPick(id) }
                        )
                    }
                }
            }
        }
        // Any hue: the slider makes a clear, bright colour of it.
        val hue = remember(value) {
            val hsv = FloatArray(3)
            ColorSetting.parse(value)?.let { android.graphics.Color.colorToHSV(it, hsv) }
            hsv[0]
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Any hue", modifier = Modifier.width(66.dp), maxLines = 1, style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.55f), fontSize = 12.sp))
            Slider(
                value = hue,
                onValueChange = { h -> onPick(ColorSetting.format(android.graphics.Color.HSVToColor(floatArrayOf(h, 0.5f, 1f)))) },
                valueRange = 0f..359f,
                modifier = Modifier.weight(1f).height(30.dp),
                colors = SliderDefaults.colors(
                    thumbColor = if (value.startsWith("#")) (ColorSetting.parse(value)?.let { Color(it) } ?: color) else color,
                    activeTrackColor = color.copy(alpha = 0.35f), inactiveTrackColor = color.copy(alpha = 0.2f),
                ),
            )
        }
    }
}
