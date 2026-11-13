package dev.liquid.launcher.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.data.MenuEntry
import dev.liquid.launcher.core.data.PillSlot
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe

@Composable
private fun slotLabel(slot: PillSlot): String = when (slot) {
    PillSlot.Search -> "Search field"
    PillSlot.Date -> "Date"
    PillSlot.Time -> "Time"
    PillSlot.Spacer -> "Flexible space"
    is PillSlot.Action -> actionLabel(slot.action)
    is PillSlot.App -> actionLabel(LauncherAction.LaunchApp(slot.ref))
}

/** Order, add and remove the pill's items. */
@Composable
internal fun PillItemsEditor(store: SettingsStore) {
    val raw by store.observe(LauncherSettings.PillSlots)
    val slots = remember(raw) { PillSlot.decodeAll(raw) }
    var adding by remember { mutableStateOf(false) }
    var pickAction by remember { mutableStateOf(false) }
    var pickApp by remember { mutableStateOf(false) }

    fun save(list: List<PillSlot>) = store.set(LauncherSettings.PillSlots, list.map { it.encode() })

    InfoCard {
        Text("Items", style = MaterialTheme.typography.titleMedium)
        BodyText("Shown left to right. Tap and long-press on the pill outside an item use the gestures below.")
        if (slots.isEmpty()) BodyText("No items: the pill is an empty glass bar.", Modifier.padding(top = 8.dp))
        slots.forEachIndexed { index, slot ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}.  ${slotLabel(slot)}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { save(slots.move(index, -1)) }, enabled = index > 0) {
                    LauncherIcon(LauncherIcons.ArrowUp, contentDescription = "Move left", size = 20.dp)
                }
                IconButton(onClick = { save(slots.move(index, 1)) }, enabled = index < slots.lastIndex) {
                    LauncherIcon(LauncherIcons.ArrowDown, contentDescription = "Move right", size = 20.dp)
                }
                IconButton(onClick = { save(slots.filterIndexed { i, _ -> i != index }) }) {
                    LauncherIcon(LauncherIcons.Close, contentDescription = "Remove", size = 20.dp)
                }
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { adding = true }) { Text("Add item") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { store.reset(LauncherSettings.PillSlots) }) { Text("Reset items") }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add to the pill") },
            text = {
                Column {
                    listOf(
                        "Search field" to { save(slots + PillSlot.Search) },
                        "Date" to { save(slots + PillSlot.Date) },
                        "Time" to { save(slots + PillSlot.Time) },
                        "Flexible space" to { save(slots + PillSlot.Spacer) },
                        "Action button…" to { pickAction = true },
                        "App…" to { pickApp = true },
                    ).forEach { (label, onClick) ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable {
                                adding = false
                                onClick()
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
    if (pickAction) {
        ActionPickerDialog("Button action", current = null, includeNone = false, onDismiss = { pickAction = false }) { action ->
            pickAction = false
            save(slots + if (action is LauncherAction.LaunchApp) PillSlot.App(action.ref) else PillSlot.Action(action))
        }
    }
    if (pickApp) {
        AppPickerDialog("Add which app?", onDismiss = { pickApp = false }) { app ->
            pickApp = false
            save(slots + PillSlot.App(app.ref))
        }
    }
}

/** Choose which long-press menu entries appear and in what order. */
@Composable
internal fun MenuEntriesEditor(store: SettingsStore) {
    val ids by store.observe(LauncherSettings.MenuEntries)
    val enabled = remember(ids) { MenuEntry.fromIds(ids) }
    val disabled = MenuEntry.entries.filter { it !in enabled }
    fun save(list: List<MenuEntry>) = store.set(LauncherSettings.MenuEntries, list.map { it.id })

    InfoCard {
        Text("Entries", style = MaterialTheme.typography.titleMedium)
        BodyText("Ticked entries appear in the menu, in this order.")
        enabled.forEachIndexed { index, entry ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = true, onCheckedChange = { save(enabled - entry) })
                Text(entry.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { save(enabled.move(index, -1)) }, enabled = index > 0) {
                    LauncherIcon(LauncherIcons.ArrowUp, contentDescription = "Move up", size = 20.dp)
                }
                IconButton(onClick = { save(enabled.move(index, 1)) }, enabled = index < enabled.lastIndex) {
                    LauncherIcon(LauncherIcons.ArrowDown, contentDescription = "Move down", size = 20.dp)
                }
            }
        }
        disabled.forEach { entry ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = false, onCheckedChange = { save(enabled + entry) })
                Text(entry.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
        }
        BodyText(
            "Coming next: change icon, swipe actions, move to folder or page, tags, and app shortcuts.",
            Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = { store.reset(LauncherSettings.MenuEntries) }) { Text("Reset entries") }
    }
}

private fun <T> List<T>.move(index: Int, by: Int): List<T> {
    val target = index + by
    if (target !in indices) return this
    val list = toMutableList()
    val item = list.removeAt(index)
    list.add(target, item)
    return list
}
