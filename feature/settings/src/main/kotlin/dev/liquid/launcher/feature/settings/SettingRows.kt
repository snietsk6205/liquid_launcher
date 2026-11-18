package dev.liquid.launcher.feature.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.alpha
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
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
        ActionPickerDialog(setting.title, current = action, pillActions = setting.pillActions, onDismiss = { open = false }) {
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
    val place = LocalRowContext.current
    val off = place.whyOff
    val context = androidx.compose.ui.platform.LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var explain by remember { mutableStateOf(false) }

    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        // W4: an option that does nothing now says why instead of changing.
                        if (off == null) onClick() else android.widget.Toast.makeText(context, off, android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onLongClick = { menu = true },
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .alpha(if (off == null) 1f else 0.45f)
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
                    if (off != null) {
                        Text(off, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.width(12.dp))
                    trailing()
                }
            }
            below?.invoke()
        }
        // W4: hold an option to reset it, have it explained, or copy its id (for a message to Claude, a backup diff…).
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (changed) "Reset to default" else "Already the default") },
                enabled = changed,
                onClick = {
                    store.reset(setting)
                    menu = false
                },
            )
            DropdownMenuItem(text = { Text("Explain") }, onClick = {
                explain = true
                menu = false
            })
            DropdownMenuItem(text = { Text("Copy id") }, onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Setting id", setting.key))
                android.widget.Toast.makeText(context, "Copied ${setting.key}", android.widget.Toast.LENGTH_SHORT).show()
                menu = false
            })
        }
    }

    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            title = { Text(setting.title) },
            text = { Text(explanation(setting, store), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { explain = false }) { Text("Close") } },
        )
    }
}

/** Explain: what an option does, where it lives, what it takes, what it needs, and its id. */
private fun explanation(setting: Setting<*>, store: SettingsStore): String {
    val info = dev.liquid.launcher.core.settings.SettingsMap.info(setting)
    return buildString {
        if (info.summary.isNotEmpty()) append(info.summary).append("\n\n")
        append("Where: ").append(dev.liquid.launcher.core.settings.SettingsMap.path(setting)).append('\n')
        append("Kind: ").append(info.type)
        if (info.values.isNotEmpty() && setting !is ChoiceSetting) append(", ").append(info.values)
        append('\n')
        val default = when (setting) {
            is ChoiceSetting -> setting.labelOf(setting.default)
            is BoolSetting -> if (setting.default) "on" else "off"
            is ColorSetting -> setting.default.ifEmpty { setting.autoLabel }
            else -> info.default.ifEmpty { "empty" }
        }
        append("Default: ").append(default).append('\n')
        dev.liquid.launcher.core.settings.SettingDependencies.whyOff(setting, store)?.let { append(it).append(" (now off)\n") }
            ?: info.dependsOn?.let { dep ->
                val parent = dev.liquid.launcher.core.settings.LauncherSettings.all.firstOrNull { it.key == dep.substringBefore('=') }
                if (parent != null) append("Needs: \u201C").append(parent.title).append("\u201D\n")
            }
        append(
            when (info.scope) {
                "layout" -> "Saved with each home layout"
                "phone" -> "A fact about this phone, left out of backups"
                else -> "The same in every layout"
            },
        ).append('\n')
        if (info.level == "advanced") append("Fine tuning (under Advanced)\n")
        append("Id: ").append(info.id)
    }
}

@Composable
private fun BoolRow(setting: BoolSetting, store: SettingsStore) {
    val value by store.observe(setting)
    RowFrame(
        setting, store, valueText = null,
        onClick = { store.set(setting, !value) },
        trailing = { Switch(checked = value, onCheckedChange = { store.set(setting, it) }, enabled = LocalRowContext.current.whyOff == null) },
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
                enabled = LocalRowContext.current.whyOff == null,
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
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
