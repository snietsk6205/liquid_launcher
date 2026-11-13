package dev.liquid.launcher.feature.settings

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.border

import dev.liquid.launcher.core.settings.ColorSetting

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.settings.ActionSetting
import dev.liquid.launcher.core.settings.BoolSetting
import dev.liquid.launcher.core.settings.ChoiceSetting
import dev.liquid.launcher.core.settings.IntSetting
import dev.liquid.launcher.core.settings.Setting
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.StringListSetting
import dev.liquid.launcher.core.settings.TextSetting
import dev.liquid.launcher.core.settings.observe
import kotlin.math.roundToInt

/** Renders any setting from the registry. Long-press offers "reset to default". */
@Composable
internal fun SettingRow(setting: Setting<*>, store: SettingsStore) {
    when (setting) {
        is BoolSetting -> BoolRow(setting, store)
        is IntSetting -> IntRow(setting, store)
        is ChoiceSetting -> ChoiceRow(setting, store)
        is TextSetting -> TextRow(setting, store)
        is StringListSetting -> ListRow(setting, store)
        is ActionSetting -> ActionRow(setting, store)
        is ColorSetting -> ColorRow(setting, store)
    }
}

/** Colour: automatic, a swatch, or hue / saturation / brightness sliders and hex. */
@Composable
private fun ColorRow(setting: ColorSetting, store: SettingsStore) {
    val value by store.observe(setting)
    var open by remember { mutableStateOf(false) }
    val parsed = ColorSetting.parse(value)
    RowFrame(setting, store, valueText = if (parsed == null) setting.autoLabel else value.uppercase(), onClick = { open = true })
    if (open) {
        val start = parsed ?: 0xFF6A7BA8.toInt()
        val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(start, it) } }
        var hue by remember { mutableStateOf(hsv[0]) }
        var sat by remember { mutableStateOf(hsv[1]) }
        var bright by remember { mutableStateOf(hsv[2]) }
        val current = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, bright))
        val swatches = listOf(
            0xFFFFFFFF, 0xFF16181C, 0xFF8AB4F8, 0xFF7C4DFF, 0xFFFF8A65, 0xFFFFD54F,
            0xFF81C784, 0xFF4DD0E1, 0xFFF06292, 0xFFB0BEC5, 0xFF5C6BC0, 0xFFA1887F,
        ).map { it.toInt() }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(setting.title) },
            text = {
                Column {
                    Box(
                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(androidx.compose.ui.graphics.Color(current))
                    )
                    Spacer(Modifier.height(10.dp))
                    for (row in swatches.chunked(6)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            for (swatch in row) {
                                Box(
                                    Modifier.size(34.dp).clip(RoundedCornerShape(50))
                                        .background(androidx.compose.ui.graphics.Color(swatch))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                                        .clickable {
                                            val out = FloatArray(3)
                                            android.graphics.Color.colorToHSV(swatch, out)
                                            hue = out[0]; sat = out[1]; bright = out[2]
                                        }
                                )
                            }
                        }
                    }
                    Text("Hue", style = MaterialTheme.typography.labelMedium)
                    Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                    Text("Saturation", style = MaterialTheme.typography.labelMedium)
                    Slider(value = sat, onValueChange = { sat = it }, valueRange = 0f..1f)
                    Text("Brightness", style = MaterialTheme.typography.labelMedium)
                    Slider(value = bright, onValueChange = { bright = it }, valueRange = 0f..1f)
                    Text(ColorSetting.format(current), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.set(setting, ColorSetting.format(current))
                    open = false
                }) { Text("Use") }
            },
            dismissButton = {
                TextButton(onClick = {
                    store.reset(setting)
                    open = false
                }) { Text("Automatic") }
            },
        )
    }
}

@Composable
private fun ActionRow(setting: ActionSetting, store: SettingsStore) {
    val value by store.observe(setting)
    val action = LauncherAction.decode(value)
    var open by remember { mutableStateOf(false) }
    RowFrame(setting, store, valueText = actionLabel(action), onClick = { open = true })
    if (open) {
        ActionPickerDialog(setting.title, current = action, onDismiss = { open = false }) {
            store.set(setting, it.encode())
            open = false
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowFrame(
    setting: Setting<*>,
    store: SettingsStore,
    valueText: String?,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    below: @Composable (() -> Unit)? = null,
) {
    val values by store.values.collectAsStateCompat()
    val changed = values.containsKey(setting.key)
    var askReset by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { if (changed) askReset = true })
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(setting.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                    if (changed) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }
                if (setting.summary.isNotEmpty()) {
                    Text(
                        setting.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (valueText != null) {
                    Text(
                        valueText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        below?.invoke()
    }

    if (askReset) {
        AlertDialog(
            onDismissRequest = { askReset = false },
            title = { Text(setting.title) },
            text = { Text("Reset this option to its default?") },
            confirmButton = {
                TextButton(onClick = {
                    store.reset(setting)
                    askReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { askReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BoolRow(setting: BoolSetting, store: SettingsStore) {
    val value by store.observe(setting)
    RowFrame(
        setting, store, valueText = null,
        onClick = { store.set(setting, !value) },
        trailing = { Switch(checked = value, onCheckedChange = { store.set(setting, it) }) },
    )
}

@Composable
private fun IntRow(setting: IntSetting, store: SettingsStore) {
    val stored by store.observe(setting)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(stored.toFloat()) }
    val shown = if (dragging) snap(dragValue, setting) else stored
    val unit = if (setting.unit.isEmpty()) "" else " ${setting.unit}"
    RowFrame(
        setting, store, valueText = "$shown$unit",
        onClick = {},
        below = {
            Slider(
                value = if (dragging) dragValue else stored.toFloat(),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    store.set(setting, snap(dragValue, setting))
                    dragging = false
                },
                valueRange = setting.min.toFloat()..setting.max.toFloat(),
            )
        },
    )
}

private fun snap(value: Float, setting: IntSetting): Int {
    val steps = ((value - setting.min) / setting.step).roundToInt()
    return (setting.min + steps * setting.step).coerceIn(setting.min, setting.max)
}

@Composable
private fun ChoiceRow(setting: ChoiceSetting, store: SettingsStore) {
    val value by store.observe(setting)
    var open by remember { mutableStateOf(false) }
    RowFrame(setting, store, valueText = setting.labelOf(value), onClick = { open = true })
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(setting.title) },
            text = {
                Column {
                    setting.options.forEach { (optionValue, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    store.set(setting, optionValue)
                                    open = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = optionValue == value, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun TextRow(setting: TextSetting, store: SettingsStore) {
    val value by store.observe(setting)
    var open by remember { mutableStateOf(false) }
    RowFrame(
        setting, store,
        valueText = value.ifEmpty { setting.placeholder },
        onClick = { open = true },
    )
    if (open) {
        var draft by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(setting.title) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text(setting.placeholder) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.set(setting, draft.trim())
                    open = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ListRow(setting: StringListSetting, store: SettingsStore) {
    val value by store.observe(setting)
    var open by remember { mutableStateOf(false) }
    RowFrame(
        setting, store,
        valueText = if (value.isEmpty()) "None" else value.joinToString(", ") { it.substringAfter(":id/") },
        onClick = { open = true },
    )
    if (open) {
        var draft by remember { mutableStateOf(value.joinToString("\n")) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(setting.title) },
            text = {
                Column {
                    Text("One entry per line", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        minLines = 4,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.set(setting, draft.lines().map { it.trim() }.filter { it.isNotEmpty() })
                    open = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}
