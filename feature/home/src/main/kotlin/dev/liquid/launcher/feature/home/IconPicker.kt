package dev.liquid.launcher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.IconPack
import dev.liquid.launcher.core.data.IconPacks
import dev.liquid.launcher.core.settings.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picks one icon for one app from any installed icon pack. The pack's own match for the app
 * and names that contain the app's name come first; typing filters the rest.
 */
@Composable
fun IconPickerDialog(app: AppEntry, controller: HomeController) {
    val context = LocalContext.current
    val packs = remember { IconPacks.installed(context) }
    var selected by remember { mutableStateOf(packs.firstOrNull()?.pkg) }
    var query by remember { mutableStateOf("") }
    val pack by produceState<IconPack?>(null, selected) {
        value = selected?.let { pkg -> withContext(Dispatchers.IO) { IconPacks.load(context, pkg) } }
    }
    val names by produceState(emptyList<String>(), pack, query) {
        val p = pack
        value = if (p == null) emptyList() else withContext(Dispatchers.Default) { ordered(p, app, query) }
    }

    AlertDialog(
        onDismissRequest = { controller.iconPicking = null },
        title = { Text("Icon for ${app.label}") },
        text = {
            Column {
                // With themed icons on, this app's icon (its own or the one picked here) follows the wallpaper unless kept.
                val themed by controller.data.settings.observe(dev.liquid.launcher.core.settings.LauncherSettings.ThemedIcons)
                if (themed) {
                    // The dialog's copy of the app does not change; read the switch from the live list.
                    val all by controller.data.apps.apps.collectAsState()
                    val keep = all.firstOrNull { it.ref == app.ref }?.keepColours ?: app.keepColours
                    Row(
                        Modifier.fillMaxWidth().clickable { controller.data.apps.setKeepColours(app.ref, !keep) }.padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Keep this icon's own colours")
                            Text(
                                "Off: it follows the wallpaper like the other icons (a picked icon becomes two-tone in the wallpaper's colours)",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                        }
                        androidx.compose.material3.Switch(checked = keep, onCheckedChange = { controller.data.apps.setKeepColours(app.ref, it) })
                    }
                }
                if (packs.isEmpty()) {
                    Text("No icon packs are installed. Install one from the Play Store (any pack made for Nova or ADW works), then come back here.")
                    return@Column
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (info in packs) {
                        FilterChip(selected = info.pkg == selected, onClick = { selected = info.pkg }, label = { Text(info.label) })
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Search icons") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                val p = pack
                if (p == null) {
                    Text("Loading…", modifier = Modifier.padding(16.dp))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(names, key = { it }) { name ->
                            PackIcon(p, name) {
                                controller.data.apps.setIcon(app.ref, "${p.info.pkg}|$name")
                                controller.iconPicking = null
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { controller.iconPicking = null }) { Text("Cancel") }
        },
        dismissButton = {
            if (app.customIcon) {
                TextButton(onClick = {
                    controller.data.apps.setIcon(app.ref, null)
                    controller.iconPicking = null
                }) { Text("Default icon") }
            }
        },
    )
}

/** The pack's match first, then names containing a word of the app's label, then the rest; filtered by [query]. */
private fun ordered(pack: IconPack, app: AppEntry, query: String): List<String> {
    val all = pack.allIcons()
    val match = pack.filter.icons["${app.component.packageName}/${app.component.className}"]
    val words = app.label.lowercase().split(' ', '-', '_').filter { it.length >= 3 }
    val q = query.trim().lowercase()
    val filtered = if (q.isEmpty()) all else all.filter { q in it.lowercase().replace('_', ' ') }
    val suggested = filtered.filter { name -> words.any { it in name.lowercase() } }
    return (listOfNotNull(match?.takeIf { q.isEmpty() || it in filtered }) + suggested + filtered).distinct()
}

@Composable
private fun PackIcon(pack: IconPack, name: String, onClick: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, pack, name) {
        value = withContext(Dispatchers.IO) { pack.bitmap(name, 144)?.asImageBitmap() }
    }
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Gray.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIconImage(bitmap, Modifier.size(48.dp), name)
    }
}
