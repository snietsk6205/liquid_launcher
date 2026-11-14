package dev.liquid.launcher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.OrganisationOps
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons

/** Dialogs opened from the drawer's tabs. */
internal sealed interface TabDialog {
    data class Rename(val tabId: String, val name: String) : TabDialog
    data class Icon(val tabId: String) : TabDialog
    data class Contents(val tabId: String) : TabDialog
    data class Apps(val tagId: String) : TabDialog
    data object NewTag : TabDialog
}

@Composable
internal fun TabDialogView(dialog: TabDialog, controller: HomeController, onChange: (TabDialog?) -> Unit) {
    val org by controller.data.organisation.value.collectAsState()
    val repo = controller.data.organisation
    when (dialog) {
        is TabDialog.Rename -> {
            var text by remember(dialog) { mutableStateOf(dialog.name) }
            AlertDialog(
                onDismissRequest = { onChange(null) },
                title = { Text("Rename tab") },
                text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        repo.update { OrganisationOps.renameTab(it, dialog.tabId, text) }
                        onChange(null)
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { onChange(null) }) { Text("Cancel") } },
            )
        }
        is TabDialog.Icon -> {
            val current = org.tabs.firstOrNull { it.id == dialog.tabId }?.icon
            fun pick(name: String?) {
                repo.update { OrganisationOps.editTab(it, dialog.tabId) { t -> t.copy(icon = name) } }
                onChange(null)
            }
            AlertDialog(
                onDismissRequest = { onChange(null) },
                title = { Text("Tab icon") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (row in LauncherIcons.named.entries.chunked(5)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for ((name, icon) in row) {
                                    val selected = name == current
                                    Box(
                                        Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { pick(name) },
                                        contentAlignment = Alignment.Center,
                                    ) { LauncherIcon(icon, contentDescription = name, size = 22.dp) }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { pick(null) }) { Text("Default") } },
                dismissButton = { TextButton(onClick = { onChange(null) }) { Text("Cancel") } },
            )
        }
        is TabDialog.Contents -> {
            val tab = org.tabs.firstOrNull { it.id == dialog.tabId }
            if (tab == null) {
                androidx.compose.runtime.SideEffect { onChange(null) }
                return
            }
            var chosen by remember(dialog) { mutableStateOf(tab.sources) }
            val options = buildList {
                add("personal" to "All apps")
                add("work" to "Work profile apps")
                add("untagged" to "Apps without a tag")
                for (tag in org.tags) add("tag:${tag.id}" to "Tag: ${tag.name}")
            }
            AlertDialog(
                onDismissRequest = { onChange(null) },
                title = { Text("Apps in \"${tab.name}\"") },
                text = {
                    Column(Modifier.heightIn(max = 420.dp)) {
                        Text("The tab shows apps matching any ticked line.", style = MaterialTheme.typography.bodySmall)
                        LazyColumn {
                            items(options, key = { it.first }) { (source, label) ->
                                val fixed = tab.source == "personal" && source == "personal"
                                val on = source in chosen
                                Row(
                                    Modifier.fillMaxWidth().clickable(enabled = !fixed) { chosen = if (on) chosen - source else chosen + source },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = on, enabled = !fixed, onCheckedChange = { chosen = if (on) chosen - source else chosen + source })
                                    Text(label)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        repo.update { OrganisationOps.setTabSources(it, tab.id, chosen) }
                        onChange(null)
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { onChange(null) }) { Text("Cancel") } },
            )
        }
        is TabDialog.Apps -> {
            val tag = org.tag(dialog.tagId)
            if (tag == null) {
                androidx.compose.runtime.SideEffect { onChange(null) }
                return
            }
            val apps by controller.data.apps.apps.collectAsState()
            var filter by remember(dialog) { mutableStateOf("") }
            val shown = apps.filter { !it.hidden && (filter.isBlank() || it.label.contains(filter, ignoreCase = true)) }
                // Apps already in the tag first, so the current choice is visible at a glance.
                .sortedByDescending { tag.id in it.tags }
            AlertDialog(
                onDismissRequest = { onChange(null) },
                title = { Text("Apps tagged ${tag.name}") },
                text = {
                    Column {
                        OutlinedTextField(value = filter, onValueChange = { filter = it }, singleLine = true, placeholder = { Text("Filter") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        LazyColumn(Modifier.heightIn(max = 380.dp)) {
                            items(shown, key = { it.key }) { app ->
                                val on = tag.id in app.tags
                                Row(
                                    Modifier.fillMaxWidth().clickable { controller.data.apps.setTag(app.ref, tag.id, !on) }.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = on, onCheckedChange = { controller.data.apps.setTag(app.ref, tag.id, it) })
                                    AppIcon(app, false, Modifier.size(28.dp), null)
                                    Spacer(Modifier.width(10.dp))
                                    Text(app.label, maxLines = 1)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { onChange(null) }) { Text("Done") } },
            )
        }
        TabDialog.NewTag -> {
            var name by remember { mutableStateOf("") }
            var asTab by remember { mutableStateOf(true) }
            var top by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { onChange(null) },
                title = { Text("New tag") },
                text = {
                    Column {
                        OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("For example Study, Media or Favourites") })
                        Row(Modifier.fillMaxWidth().clickable { asTab = !asTab }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = asTab, onCheckedChange = { asTab = it })
                            Text("Show it as a drawer tab")
                        }
                        Row(Modifier.fillMaxWidth().clickable { top = !top }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = top, onCheckedChange = { top = it })
                            Text("Show it as a section at the top of the main list")
                        }
                        Text("Next you choose its apps.", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val tag = repo.tagNamed(name) ?: return@TextButton
                        if (asTab) repo.update { OrganisationOps.addTab(it, tag.name, "tag:${tag.id}") }
                        if (top) {
                            val key = dev.liquid.launcher.core.settings.LauncherSettings.DrawerTopSections
                            val current = controller.data.settings.get(key)
                            if (tag.id !in current) controller.data.settings.set(key, current + tag.id)
                        }
                        onChange(TabDialog.Apps(tag.id))
                    }) { Text("Next") }
                },
                dismissButton = { TextButton(onClick = { onChange(null) }) { Text("Cancel") } },
            )
        }
    }
}
