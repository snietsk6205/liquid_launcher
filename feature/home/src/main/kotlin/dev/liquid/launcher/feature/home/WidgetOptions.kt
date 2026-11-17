package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.HomeItem
import dev.liquid.launcher.core.designsystem.liquidGlass

/**
 * P7 (0.9.22): how one of the launcher's own widgets looks, from its options (set in its "Customise" sheet in edit
 * mode): size, weight, colour, font, and which parts show.
 */
internal data class WidgetLook(val scale: Float, val weight: FontWeight, val accent: Boolean, val clockFont: Boolean, private val options: Map<String, String>) {
    /** A part of the widget ([key]) shown or not; [default] when never set. */
    fun shows(key: String, default: Boolean): Boolean = options[key]?.let { it == "on" } ?: default

    fun color(text: Color): Color = if (accent) HomeTheme.accent else text

    fun font(): FontFamily? = if (clockFont) HomeTheme.clockFont ?: HomeTheme.font else HomeTheme.font

    companion object {
        fun of(item: HomeItem?): WidgetLook {
            val o = item?.options.orEmpty()
            return WidgetLook(
                scale = when (o["size"]) {
                    "s" -> 0.8f
                    "l" -> 1.25f
                    else -> 1f
                },
                weight = when (o["weight"]) {
                    "light" -> FontWeight.Light
                    "bold" -> FontWeight.Bold
                    "regular" -> FontWeight.Normal
                    else -> FontWeight.Medium
                },
                accent = o["tint"] == "accent",
                clockFont = o["font"] != "text",
                options = o,
            )
        }
    }
}

/** The widgets that have this sheet, and the parts each can show or hide (option key, label, shown by default). */
internal val WidgetParts: Map<String, List<Triple<String, String, Boolean>>> = mapOf(
    "date" to listOf(Triple("weekday", "Weekday", true), Triple("month", "Month", true), Triple("year", "Year", false)),
    "battery" to listOf(Triple("ring", "Ring", true), Triple("percent", "Percentage", true), Triple("charging", "\"charging\"", true)),
    "calendar" to listOf(Triple("grid", "Month", true), Triple("agenda", "Next events", true)),
)

/**
 * The "Customise" sheet of a date or battery widget: every change is written to the widget at once, so it changes
 * behind the sheet as you choose.
 */
@Composable
internal fun WidgetOptionsSheet(item: HomeItem, glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop, controller: HomeController) {
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    val shape = RoundedCornerShape(30.dp)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.enter()) }
    val o = item.options.orEmpty()
    fun set(key: String, value: String?) = controller.setItemOption(item.id, key, value)
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controller.widgetOptions = null },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp)
                .graphicsLayer {
                    alpha = appear.value.coerceIn(0f, 1f)
                    translationY = (1f - appear.value) * 40.dp.toPx()
                }
                .liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground, large = true))
                .clip(shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(vertical = 16.dp),
        ) {
            ProvideControls(controller, glass, null) {
                val calendar = item.widget == "calendar"
                Text(
                    when (item.widget) {
                        "battery" -> "Battery widget"
                        "calendar" -> "Calendar widget"
                        else -> "Date widget"
                    },
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
                if (!calendar) {
                    OptionRow("Size", color, listOf("s" to "Small", null to "Medium", "l" to "Large"), o["size"]) { set("size", it) }
                    OptionRow("Weight", color, listOf("light" to "Light", "regular" to "Regular", null to "Medium", "bold" to "Bold"), o["weight"]) { set("weight", it) }
                }
                OptionRow("Colour", color, listOf(null to "Text", "accent" to "Accent"), o["tint"]) { set("tint", it) }
                if (!calendar) {
                    OptionRow("Font", color, listOf(null to "The clock's", "text" to "The launcher's"), o["font"]) { set("font", it) }
                } else {
                    OptionRow("Card", color, listOf(null to "As for all", "glass" to "Glass card", "none" to "On the wallpaper"), o["card"]) { set("card", it) }
                    OptionRow("Corners", color, listOf("s" to "Small", null to "Medium", "l" to "Large"), o["corner"]) { set("corner", it) }
                }
                val parts = WidgetParts[item.widget].orEmpty()
                if (parts.isNotEmpty()) {
                    Text(
                        "Shows",
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp),
                        style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.7f), fontSize = 13.sp),
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        parts.forEach { (key, label, default) ->
                            val on = o[key]?.let { it == "on" } ?: default
                            GlassButton(label, color, emphasis = if (on) ControlEmphasis.Selected else ControlEmphasis.Normal) {
                                set(key, if (on == default) (if (on) "off" else "on") else null)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    GlassButton("Done", color, emphasis = ControlEmphasis.Main) { controller.widgetOptions = null }
                }
            }
        }
    }
}

/** A label and its choices, the chosen one selected; null is the default. */
@Composable
private fun OptionRow(label: String, color: Color, choices: List<Pair<String?, String>>, value: String?, onPick: (String?) -> Unit) {
    Text(
        label,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp),
        style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.7f), fontSize = 13.sp),
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        choices.forEach { (key, name) ->
            GlassButton(name, color, emphasis = if (value == key) ControlEmphasis.Selected else ControlEmphasis.Normal) { onPick(key) }
        }
    }
}
