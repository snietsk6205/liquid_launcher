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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.data.Organisation
import dev.liquid.launcher.core.data.OrganisationOps
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
            "Coming next: change icon, swipe actions, move to folder, and app shortcuts.",
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

/**
 * Drawer tabs (reorder, rename, remove, add from a tag) and the tags themselves (rename,
 * delete). Apps get tags from their long-press menu.
 */
@Composable
internal fun TabsAndTagsEditor() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val data = remember { dev.liquid.launcher.core.data.LauncherData.get(context) }
    val org by data.organisation.value.collectAsState()
    val apps by data.apps.apps.collectAsState()
    var renaming by remember { mutableStateOf<Pair<String, String>?>(null) } // kind:id to current name
    var addingTab by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf(false) }

    fun sourceLabel(source: String): String = when {
        source == "personal" -> "All apps (main list)"
        source == "work" -> "Work profile apps"
        source == "untagged" -> "Apps without a tag"
        source.startsWith("tag:") -> "Tag: " + (org.tag(source.removePrefix("tag:"))?.name ?: "deleted")
        else -> source
    }

    InfoCard {
        Text("Drawer tabs", style = MaterialTheme.typography.titleMedium)
        BodyText("Shown in the glass pill in this order. Hold a tab in the drawer to come back here.")
        org.tabs.forEachIndexed { index, tab ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { renaming = "tab:${tab.id}" to tab.name }) {
                    Text(tab.name, style = MaterialTheme.typography.bodyLarge)
                    Text(sourceLabel(tab.source), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { data.organisation.update { OrganisationOps.moveTab(it, index, index - 1) } }, enabled = index > 0) {
                    LauncherIcon(LauncherIcons.ArrowUp, contentDescription = "Move up", size = 20.dp)
                }
                IconButton(onClick = { data.organisation.update { OrganisationOps.moveTab(it, index, index + 1) } }, enabled = index < org.tabs.lastIndex) {
                    LauncherIcon(LauncherIcons.ArrowDown, contentDescription = "Move down", size = 20.dp)
                }
                IconButton(onClick = { data.organisation.update { OrganisationOps.removeTab(it, tab.id) } }, enabled = tab.source != "personal") {
                    LauncherIcon(LauncherIcons.Close, contentDescription = "Remove tab", size = 20.dp)
                }
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { addingTab = true }) { Text("Add tab") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { data.organisation.update { it.copy(tabs = Organisation.DEFAULT_TABS) } }) { Text("Reset tabs") }
        }
    }

    InfoCard {
        Text("Tags", style = MaterialTheme.typography.titleMedium)
        BodyText("Give apps tags from their long-press menu (Tags). Search the drawer with #name to list a tag's apps.")
        if (org.tags.isEmpty()) BodyText("No tags yet.", Modifier.padding(top = 8.dp))
        for (tag in org.tags) {
            val count = apps.count { tag.id in it.tags }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { renaming = "tag:${tag.id}" to tag.name }) {
                    Text(tag.name, style = MaterialTheme.typography.bodyLarge)
                    Text(if (count == 1) "1 app" else "$count apps", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { data.organisation.update { OrganisationOps.deleteTag(it, tag.id) } }) {
                    LauncherIcon(LauncherIcons.Delete, contentDescription = "Delete tag", size = 20.dp)
                }
            }
        }
        OutlinedButton(onClick = { newTag = true }, modifier = Modifier.padding(top = 8.dp)) { Text("New tag") }
    }

    renaming?.let { (key, current) ->
        NameDialog("Rename", current, onDismiss = { renaming = null }) { name ->
            val (kind, id) = key.split(":", limit = 2)
            data.organisation.update {
                if (kind == "tab") OrganisationOps.renameTab(it, id, name) else OrganisationOps.renameTag(it, id, name)
            }
            renaming = null
        }
    }
    if (newTag) {
        NameDialog("New tag", "", onDismiss = { newTag = false }) { name ->
            data.organisation.tagNamed(name)
            newTag = false
        }
    }
    if (addingTab) {
        val options = buildList {
            for (tag in org.tags) add(Triple(tag.name, "tag:${tag.id}", "Apps tagged ${tag.name}"))
            add(Triple("Other", "untagged", "Apps without a tag"))
            add(Triple("Work", "work", "Work profile apps"))
        }.filter { (_, source, _) -> org.tabs.none { it.source == source } }
        AlertDialog(
            onDismissRequest = { addingTab = false },
            title = { Text("Add a drawer tab") },
            text = {
                Column {
                    if (options.isEmpty()) BodyText("Every tag already has a tab. Create a tag first.")
                    for ((name, source, description) in options) {
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                data.organisation.update { OrganisationOps.addTab(it, name, source) }
                                addingTab = false
                            }.padding(vertical = 10.dp)
                        ) {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addingTab = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { androidx.compose.material3.OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

