package dev.liquid.launcher.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.ControlTheme
import dev.liquid.launcher.core.data.LiquidTheme
import dev.liquid.launcher.core.data.ThemeColors
import dev.liquid.launcher.core.data.ThemeGradient
import dev.liquid.launcher.core.data.LiquidThemes
import dev.liquid.launcher.core.data.ThemeApply
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The theme of buttons and chips (P4, 0.9.13), at the top of Settings → Buttons: which theme is in use, its colours
 * at a glance, and import (the launcher's theme files, WM Keyboard themes and backups), export and back to your
 * keyboard theme. The buttons in the preview above show every change at once.
 */
@Composable
internal fun ControlThemeCard(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode by store.observe(LauncherSettings.ThemeControls)
    val json by store.observe(LauncherSettings.ThemeJson)
    val theme = remember(json) { ThemeApply.current(store) }
    var message by remember { mutableStateOf<String?>(null) }

    val importTheme = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBefore('.')?.takeIf { it.isNotBlank() } ?: "Imported theme"
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            val imported = text?.let { LiquidThemes.decode(it, fallbackName = name) }
            if (imported == null) {
                message = "That file is not a theme (the launcher's or WM Keyboard's)"
            } else {
                ThemeApply.use(store, imported)
                message = "Using \"${imported.name}\"; its accent and glass colour are in Settings → Theme"
            }
        }
    }
    val exportTheme = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(LiquidThemes.encode(theme).encodeToByteArray()) } != null
                }.getOrDefault(false)
            }
            message = if (ok) "Theme saved" else "Saving failed"
        }
    }

    InfoCard {
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        if (mode != "theme") {
            BodyText("Off: buttons use the launcher's own look (the options below). Switch \"Buttons and chips\" to Your theme to use it.")
        }
        Text(theme.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
        theme.source?.let { BodyText(it) }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Swatch(theme.button.fill, theme.button.text, "Aa")
            Swatch(theme.button.pressed, theme.button.pressedText ?: theme.button.text, "Aa")
            Swatch(theme.chip.fill, theme.chip.text, "Aa")
            Swatch(theme.chip.active, theme.chip.activeText, "Aa")
            Swatch(theme.tool.fill, theme.tool.text, "●")
            Swatch(theme.accent, 0xFF000000, "")
        }
        BodyText(
            "Button, pressed, chip, chip on, round button, accent. Glass: ${theme.frost.style.lowercase()}, " +
                "rim ${(theme.frost.rim * 100).toInt()} %" + if (theme.frost.liquid) ", bend ${(theme.frost.refraction * 100).toInt()} %" else "",
            Modifier.padding(top = 6.dp),
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importTheme.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) { Text("Import…") }
            OutlinedButton(onClick = { exportTheme.launch(theme.name.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "theme" } + ".liquidtheme.json") }) { Text("Export…") }
        }
        if (theme != LiquidThemes.Keyboard || mode != "theme") {
            TextButton(onClick = {
                ThemeApply.use(store, LiquidThemes.Keyboard)
                message = "Back to your keyboard theme (Nice liquid)"
            }) { Text("Back to your keyboard theme") }
        }
        BodyText(
            "Import reads the launcher's theme files and WM Keyboard themes and backups. The theme's accent and glass " +
                "colour go to Settings → Theme, where you can still change them.",
            Modifier.padding(top = 4.dp),
        )
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)) }
    }
}

/** A colour of the theme with a sample of its text. */
@Composable
private fun Swatch(fill: Long, text: Long, sample: String) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(width = 40.dp, height = 28.dp)
            .background(Color(0xFF2A2A2E), shape)
            .background(Color(fill), shape)
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (sample.isNotEmpty()) Text(sample, color = Color(text), style = MaterialTheme.typography.labelMedium)
    }
}

/** The surfaces of a theme the editor changes, with their names. */
private enum class ThemePart(val title: String) { Button("Buttons"), Main("Main button"), Chip("Chips"), Tool("Round buttons") }

private fun LiquidTheme.part(part: ThemePart): ControlTheme = when (part) {
    ThemePart.Button -> button
    ThemePart.Main -> main
    ThemePart.Chip -> chip
    ThemePart.Tool -> tool
}

private fun LiquidTheme.withPart(part: ThemePart, kind: ControlTheme): LiquidTheme = when (part) {
    ThemePart.Button -> copy(button = kind)
    ThemePart.Main -> copy(main = kind)
    ThemePart.Chip -> copy(chip = kind)
    ThemePart.Tool -> copy(tool = kind)
}

/** Sheens to choose from, as WM Keyboard's frost presets lay them over keys (light at the top fading down). */
private val Sheens: List<Pair<String, ThemeGradient?>> = listOf(
    "None" to null,
    "Soft" to ThemeGradient(listOf(0x2EFFFFFFL, 0x00FFFFFFL), "LINEAR", 90f),
    "Bright" to ThemeGradient(listOf(0x40FFFFFFL, 0x00FFFFFFL), "LINEAR", 90f),
    "Smoked" to ThemeGradient(listOf(0x14FFFFFFL, 0x26000000L), "LINEAR", 90f),
)

/**
 * The theme, surface by surface (P4, 0.9.14): every value of the theme of buttons and chips, written as it changes,
 * so the buttons in the preview at the top follow. Colours have their opacity: a see-through fill over glass is frosted.
 */
@Composable
internal fun ControlThemeEditor(store: SettingsStore) {
    val json by store.observe(LauncherSettings.ThemeJson)
    val theme = remember(json) { ThemeApply.current(store) }
    var open by remember { mutableStateOf(false) }
    var part by remember { mutableStateOf(ThemePart.Button) }
    NavRow(
        if (open) "Edit the theme" else "Edit the theme…",
        "Colours, opacity, border, shape, corners, glass, shadow and glow of buttons, the main button, chips and round " +
            "buttons, and the frost under them",
    ) { open = !open }
    if (!open) return
    fun save(t: LiquidTheme) = ThemeApply.save(store, t)
    val kind = theme.part(part)
    fun edit(k: ControlTheme) = save(theme.withPart(part, k))
    InfoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
            ThemePart.entries.forEach { p ->
                FilterChip(selected = p == part, onClick = { part = p }, label = { Text(p.title) })
            }
        }
        if (part == ThemePart.Main) {
            BodyText("Used when \"Main button colour\" below is \"Like the other buttons\"; otherwise the main button takes the block's or the accent colour.")
        }
        ColourLine("Fill", kind.fill) { edit(kind.copy(fill = it)) }
        ColourLine("Text", kind.text) { edit(kind.copy(text = it)) }
        ColourLine("Pressed", kind.pressed) { edit(kind.copy(pressed = it)) }
        ColourLine("Text when pressed", kind.pressedText ?: kind.text, reset = if (kind.pressedText != null) ({ edit(kind.copy(pressedText = null)) }) else null) {
            edit(kind.copy(pressedText = it))
        }
        ColourLine("Selected", kind.active) { edit(kind.copy(active = it)) }
        ColourLine("Text when selected", kind.activeText) { edit(kind.copy(activeText = it)) }
        ColourLine("Border", kind.border ?: 0x00FFFFFFL, reset = if (kind.border != null) ({ edit(kind.copy(border = null, borderWidthDp = 0f)) }) else null) {
            edit(kind.copy(border = it, borderWidthDp = if (kind.borderWidthDp <= 0f) 1f else kind.borderWidthDp))
        }
        if (kind.border != null) {
            ValueSlider("Border width", kind.borderWidthDp, 0f, 4f, 0.5f, "dp") { edit(kind.copy(borderWidthDp = it)) }
        }
        Text("Shape", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
        val shapes = if (part == ThemePart.Tool) listOf("CIRCLE", "PILL", "ROUNDED", "SQUIRCLE", "CUT", "SHARP") else listOf("PILL", "ROUNDED", "SQUIRCLE", "CUT", "SHARP")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
            shapes.forEach { s ->
                FilterChip(selected = kind.shape == s, onClick = { edit(kind.copy(shape = s)) }, label = { Text(s.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }
        if (kind.shape == "ROUNDED" || kind.shape == "SQUIRCLE" || kind.shape == "CUT") {
            ValueSlider("Corner radius", kind.cornerDp.toFloat(), 0f, 28f, 1f, "dp") { edit(kind.copy(cornerDp = it.toInt())) }
        }
        Text("Sheen", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
            Sheens.forEach { (name, g) ->
                FilterChip(selected = kind.gradient == g, onClick = { edit(kind.copy(gradient = g)) }, label = { Text(name) })
            }
            if (kind.gradient != null && Sheens.none { it.second == kind.gradient }) {
                FilterChip(selected = true, onClick = {}, label = { Text("The theme's own") })
            }
        }
        SwitchLine("Glass under it", kind.glass) { edit(kind.copy(glass = it)) }
        ValueSlider("Shadow", kind.shadowDp, 0f, 8f, 0.5f, "dp") { edit(kind.copy(shadowDp = it)) }
        ValueSlider("Glow", kind.glowDp, 0f, 16f, 1f, "dp") { edit(kind.copy(glowDp = it)) }
        if (kind.glowDp > 0f) {
            ColourLine("Glow colour", kind.glowColor ?: theme.accent, reset = if (kind.glowColor != null) ({ edit(kind.copy(glowColor = null)) }) else null) {
                edit(kind.copy(glowColor = it))
            }
        }
    }
    InfoCard {
        Text("Frost under the controls", style = MaterialTheme.typography.titleMedium)
        BodyText("Drawn where a control sits on glass (sheets, the hub, cards), for every surface with \"Glass under it\".")
        val f = theme.frost
        fun frost(n: dev.liquid.launcher.core.data.ThemeFrost) = save(theme.copy(frost = n))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            FilterChip(selected = !f.liquid, onClick = { frost(f.copy(style = "FROSTED")) }, label = { Text("Frosted") })
            FilterChip(selected = f.liquid, onClick = { frost(f.copy(style = "LIQUID")) }, label = { Text("Liquid: the edge bends") })
        }
        ValueSlider("Blur", f.blurDp, 0f, 25f, 1f, "dp") { frost(f.copy(blurDp = it)) }
        ValueSlider("Colour", f.saturation, 0.5f, 2f, 0.05f, "×") { frost(f.copy(saturation = it)) }
        ValueSlider("Brightness", f.brightness, -0.3f, 0.3f, 0.02f, "") { frost(f.copy(brightness = it)) }
        ValueSlider("Rim of light", f.rim, 0f, 1f, 0.02f, "") { frost(f.copy(rim = it)) }
        ColourLine("Rim colour", f.rimColor ?: 0xFFFFFFFF, reset = if (f.rimColor != null) ({ frost(f.copy(rimColor = null)) }) else null) { frost(f.copy(rimColor = it)) }
        ValueSlider("Grain", f.grain, 0f, 0.25f, 0.01f, "") { frost(f.copy(grain = it)) }
        if (f.liquid) {
            ValueSlider("Bend", f.refraction, 0f, 1f, 0.05f, "") { frost(f.copy(refraction = it)) }
            ValueSlider("Rainbow", f.dispersion, 0f, 1f, 0.05f, "") { frost(f.copy(dispersion = it)) }
        }
    }
    InfoCard {
        Text("Colours of the launcher", style = MaterialTheme.typography.titleMedium)
        BodyText("Written to Settings → Theme when changed here.")
        ColourLine("Accent", theme.accent) { a ->
            val t = theme.copy(accent = ThemeColors.opaque(a))
            save(t)
            ThemeApply.colours(store, t)
        }
        ColourLine("Glass tint", theme.glassTint) { g ->
            val t = theme.copy(glassTint = ThemeColors.opaque(g))
            save(t)
            ThemeApply.colours(store, t)
        }
    }
}

/** A labelled slider for a theme value, written as it moves, in [step]s. */
@Composable
private fun ValueSlider(label: String, value: Float, min: Float, max: Float, step: Float, unit: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            val shown = if (step >= 1f) value.roundToInt().toString() else String.format(java.util.Locale.US, if (step >= 0.1f) "%.1f" else "%.2f", value)
            Text(shown + if (unit.isEmpty()) "" else " $unit", style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = { v ->
                val snapped = (min + ((v - min) / step).roundToInt() * step).coerceIn(min, max)
                if (kotlin.math.abs(snapped - value) > step / 4f) onChange(snapped)
            },
            valueRange = min..max,
        )
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A theme colour: its swatch (over a checkerboard of dark and light, so the opacity shows) and the picker on tap. */
@Composable
private fun ColourLine(label: String, argb: Long, reset: (() -> Unit)? = null, onChange: (Long) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clickable { picking = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(hexOf(argb), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
        Swatch(argb, 0x00000000L, "")
    }
    if (picking) ArgbPicker(label, argb, reset, onDismiss = { picking = false }, onChange = onChange)
}

private fun hexOf(argb: Long): String = "#%08X".format(argb and 0xFFFFFFFFL)

/**
 * Hue, saturation, brightness and opacity, written as they move (the preview follows); Cancel puts the colour back.
 * [reset] offers "Theme's default" where the value can be left out.
 */
@Composable
private fun ArgbPicker(title: String, start: Long, reset: (() -> Unit)?, onDismiss: () -> Unit, onChange: (Long) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV((start or 0xFF000000).toInt(), it) } }
    var hue by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1]) }
    var bright by remember { mutableStateOf(hsv[2]) }
    var alpha by remember { mutableStateOf(((start ushr 24) and 0xFF) / 255f) }
    fun current(): Long {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, bright)).toLong() and 0xFFFFFF
        return ((alpha * 255f + 0.5f).toLong().coerceIn(0, 255) shl 24) or rgb
    }
    fun push() = onChange(current())
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Swatch(current(), 0xFFE9E9EE, "Aa")
                    Spacer(Modifier.width(10.dp))
                    Text(hexOf(current()), style = MaterialTheme.typography.bodyMedium)
                }
                Text("Hue", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                Slider(value = hue, onValueChange = { hue = it; push() }, valueRange = 0f..360f)
                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                Slider(value = sat, onValueChange = { sat = it; push() }, valueRange = 0f..1f)
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                Slider(value = bright, onValueChange = { bright = it; push() }, valueRange = 0f..1f)
                Text("Opacity ${(alpha * 100).roundToInt()} %", style = MaterialTheme.typography.labelMedium)
                Slider(value = alpha, onValueChange = { alpha = it; push() }, valueRange = 0f..1f)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            Row {
                if (reset != null) TextButton(onClick = { reset(); onDismiss() }) { Text("Theme's default") }
                TextButton(onClick = { onChange(start); onDismiss() }) { Text("Cancel") }
            }
        },
    )
}
