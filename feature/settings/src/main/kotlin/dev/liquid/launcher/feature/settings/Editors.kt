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
            "App shortcuts are the ones an app declares in its manifest; only the default launcher can show an app's other shortcuts (for example a recent chat).",
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
    val store = data.settings
    val topSections by store.observe(LauncherSettings.DrawerTopSections)
    val buttons by store.observe(LauncherSettings.DrawerTabsButtons)
    val tabMenu by store.observe(LauncherSettings.DrawerTabMenu)

    InfoCard {
        Text("Drawer tabs", style = MaterialTheme.typography.titleMedium)
        BodyText("Shown in the glass pill in this order. Hold a tab in the drawer to come back here.")
        org.tabs.forEachIndexed { index, tab ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { renaming = "tab:${tab.id}" to tab.name }) {
                    Text(tab.name, style = MaterialTheme.typography.bodyLarge)
                    Text(tab.sources.joinToString(" + ") { sourceLabel(it) }, style = MaterialTheme.typography.bodySmall)
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

    val pinned by store.observe(LauncherSettings.TagPinned)
    InfoCard {
        Text("Pinned tag chips", style = MaterialTheme.typography.titleMedium)
        BodyText(
            "In the drawer, a row of tag chips filters the apps in one tap (hold a chip to add more tags; \"All apps\" clears). " +
                "Pinned tags come first, in this order; the rest follow by use. Swipe the search field sideways to step through them."
        )
        val pinnedTags = pinned.mapNotNull { org.tag(it) }
        if (pinnedTags.isEmpty()) BodyText("Nothing pinned yet: tap the star next to a tag below, or in the drawer's full tag list.", Modifier.padding(top = 8.dp))
        pinnedTags.forEachIndexed { index, tag ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tag.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { store.set(LauncherSettings.TagPinned, pinned.toMutableList().apply { add(index - 1, removeAt(index)) }) }, enabled = index > 0) {
                    LauncherIcon(LauncherIcons.ArrowUp, contentDescription = "Move up", size = 20.dp)
                }
                IconButton(onClick = { store.set(LauncherSettings.TagPinned, pinned.toMutableList().apply { add(index + 1, removeAt(index)) }) }, enabled = index < pinnedTags.lastIndex) {
                    LauncherIcon(LauncherIcons.ArrowDown, contentDescription = "Move down", size = 20.dp)
                }
                IconButton(onClick = { store.set(LauncherSettings.TagPinned, pinned - tag.id) }) {
                    LauncherIcon(LauncherIcons.Close, contentDescription = "Unpin", size = 20.dp)
                }
            }
        }
    }

    InfoCard {
        Text("Tags", style = MaterialTheme.typography.titleMedium)
        BodyText("Give apps tags from their long-press menu (Tags). The star pins a tag to the front of the drawer's tag chips.")
        if (org.tags.isEmpty()) BodyText("No tags yet.", Modifier.padding(top = 8.dp))
        for (tag in org.tags) {
            val count = apps.count { tag.id in it.tags }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { renaming = "tag:${tag.id}" to tag.name }) {
                    Text(tag.name, style = MaterialTheme.typography.bodyLarge)
                    Text(if (count == 1) "1 app" else "$count apps", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { store.set(LauncherSettings.TagPinned, if (tag.id in pinned) pinned - tag.id else pinned + tag.id) }) {
                    LauncherIcon(
                        LauncherIcons.Star, contentDescription = if (tag.id in pinned) "Unpin" else "Pin", size = 20.dp,
                        tint = if (tag.id in pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                }
                IconButton(onClick = {
                    data.organisation.update { OrganisationOps.deleteTag(it, tag.id) }
                    store.set(LauncherSettings.TagPinned, pinned - tag.id)
                }) {
                    LauncherIcon(LauncherIcons.Delete, contentDescription = "Delete tag", size = 20.dp)
                }
            }
        }
        OutlinedButton(onClick = { newTag = true }, modifier = Modifier.padding(top = 8.dp)) { Text("New tag") }
    }

    InfoCard {
        Text("Sections at the top of the main list", style = MaterialTheme.typography.titleMedium)
        BodyText("A ticked tag's apps also appear in their own section above all apps, with an entry in the scroll index (a star for a tag called Favourites).")
        if (org.tags.isEmpty()) BodyText("Create a tag first.", Modifier.padding(top = 8.dp))
        for (tag in org.tags) {
            val on = tag.id in topSections
            Row(Modifier.fillMaxWidth().clickable { store.set(LauncherSettings.DrawerTopSections, if (on) topSections - tag.id else topSections + tag.id) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = on, onCheckedChange = { store.set(LauncherSettings.DrawerTopSections, if (on) topSections - tag.id else topSections + tag.id) })
                Text(tag.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    InfoCard {
        Text("Buttons in the tabs pill", style = MaterialTheme.typography.titleMedium)
        BodyText("Shown after the tabs. None by default.")
        for ((id, label) in listOf("add" to "New tag", "sort" to "Sort (opens the current tab's menu)", "settings" to "Drawer settings")) {
            val on = id in buttons
            Row(Modifier.fillMaxWidth().clickable { store.set(LauncherSettings.DrawerTabsButtons, if (on) buttons - id else buttons + id) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = on, onCheckedChange = { store.set(LauncherSettings.DrawerTabsButtons, if (on) buttons - id else buttons + id) })
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    InfoCard {
        Text("Held tab menu", style = MaterialTheme.typography.titleMedium)
        BodyText("What holding a tab offers, in this order.")
        val all = listOf(
            "rename" to "Rename", "icon" to "Icon", "contents" to "Which apps it shows", "apps" to "Choose apps (tag tabs)",
            "sort" to "Sort order of this tab", "move" to "Move left / right", "newtag" to "New tag", "remove" to "Remove tab",
            "edit" to "Edit tabs and tags (settings)", "settings" to "Drawer settings",
        )
        val names = all.toMap()
        tabMenu.forEachIndexed { index, id ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = true, onCheckedChange = { store.set(LauncherSettings.DrawerTabMenu, tabMenu - id) })
                Text(names[id] ?: id, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { store.set(LauncherSettings.DrawerTabMenu, tabMenu.move(index, -1)) }, enabled = index > 0) {
                    LauncherIcon(LauncherIcons.ArrowUp, contentDescription = "Move up", size = 20.dp)
                }
                IconButton(onClick = { store.set(LauncherSettings.DrawerTabMenu, tabMenu.move(index, 1)) }, enabled = index < tabMenu.lastIndex) {
                    LauncherIcon(LauncherIcons.ArrowDown, contentDescription = "Move down", size = 20.dp)
                }
            }
        }
        for ((id, label) in all.filter { it.first !in tabMenu }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = false, onCheckedChange = { store.set(LauncherSettings.DrawerTabMenu, tabMenu + id) })
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
        }
        TextButton(onClick = { store.reset(LauncherSettings.DrawerTabMenu) }) { Text("Reset") }
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

/** Main icon pack, and optionally a different one on the home screen or in the drawer. */
@Composable
internal fun IconPacksEditor(store: SettingsStore) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val packs = remember { dev.liquid.launcher.core.data.IconPacks.installed(context) }
    val main by store.observe(LauncherSettings.IconPack)
    val home by store.observe(LauncherSettings.IconPackHome)
    val drawer by store.observe(LauncherSettings.IconPackDrawer)
    var choosing by remember { mutableStateOf<dev.liquid.launcher.core.settings.TextSetting?>(null) }

    fun name(value: String): String = when (value) {
        LauncherSettings.SAME_AS_MAIN -> "Same as the icon pack"
        "" -> "Apps' own icons"
        else -> packs.firstOrNull { it.pkg == value }?.label ?: "$value (not installed)"
    }

    InfoCard {
        Text("Icon packs", style = MaterialTheme.typography.titleMedium)
        if (packs.isEmpty()) {
            BodyText("No icon packs are installed. Any pack made for Nova or ADW works; install one and come back.")
        }
        for ((setting, title, value) in listOf(
            Triple(LauncherSettings.IconPack, "Icon pack", main),
            Triple(LauncherSettings.IconPackHome, "Home screen", home),
            Triple(LauncherSettings.IconPackDrawer, "Drawer", drawer),
        )) {
            Column(Modifier.fillMaxWidth().clickable { choosing = setting }.padding(vertical = 10.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(name(value), style = MaterialTheme.typography.bodySmall)
            }
        }
        BodyText("One app's icon: hold it, then Change icon.", Modifier.padding(top = 4.dp))
    }

    choosing?.let { setting ->
        val options = buildList {
            if (setting != LauncherSettings.IconPack) add(LauncherSettings.SAME_AS_MAIN)
            add("")
            packs.forEach { add(it.pkg) }
        }
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text(setting.title) },
            text = {
                Column {
                    for (value in options) {
                        Text(
                            name(value),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable {
                                store.set(setting, value)
                                choosing = null
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = null }) { Text("Cancel") } },
        )
    }
}

