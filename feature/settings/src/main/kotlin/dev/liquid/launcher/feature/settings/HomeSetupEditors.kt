package dev.liquid.launcher.feature.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.data.LauncherData
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.StringListSetting
import dev.liquid.launcher.core.settings.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Brings the home screen to the front and asks it to open [what] ("wallpaper" or "layouts"). */
internal fun openOnHome(context: Context, what: String) {
    val intent = Intent()
        .setClassName(context.packageName, "dev.liquid.launcher.MainActivity")
        .putExtra("dev.liquid.launcher.OPEN", what)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    context.startActivity(intent)
}

/** The two pills of the home menu: items, order, and moving items between them. */
@Composable
internal fun HomeMenuEditor(store: SettingsStore) {
    val setup by store.observe(LauncherSettings.HomeMenuSetup)
    val actions by store.observe(LauncherSettings.HomeMenuActions)
    var adding by remember { mutableStateOf<StringListSetting?>(null) }

    InfoCard {
        Text("How it works", style = MaterialTheme.typography.titleMedium)
        BodyText(
            "Hold empty space on the home screen: two glass pills rise at the bottom. Tap an item, or keep your finger down, " +
                "slide onto an item and let go. \"Long press on empty space\" in Gestures decides whether this menu opens."
        )
    }
    PillList("Set-up pill (upper)", LauncherSettings.HomeMenuSetup, setup, LauncherSettings.HomeMenuActions, store) { adding = it }
    PillList("Actions pill (lower)", LauncherSettings.HomeMenuActions, actions, LauncherSettings.HomeMenuSetup, store) { adding = it }
    adding?.let { target ->
        ActionPickerDialog(title = "Add to the pill", current = null, includeNone = false, onDismiss = { adding = null }) { action ->
            store.set(target, store.get(target) + action.encode())
            adding = null
        }
    }
}

@Composable
private fun PillList(
    title: String,
    setting: StringListSetting,
    items: List<String>,
    other: StringListSetting,
    store: SettingsStore,
    onAdd: (StringListSetting) -> Unit,
) {
    InfoCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (items.isEmpty()) BodyText("Empty: this pill is not shown.")
        items.forEachIndexed { index, encoded ->
            val action = LauncherAction.decode(encoded)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(actionLabel(action), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { store.set(setting, items.moved(index, -1)) }, enabled = index > 0) {
                    LauncherIcon(LauncherIcons.ArrowUp, contentDescription = "Move left", size = 20.dp)
                }
                IconButton(onClick = { store.set(setting, items.moved(index, 1)) }, enabled = index < items.lastIndex) {
                    LauncherIcon(LauncherIcons.ArrowDown, contentDescription = "Move right", size = 20.dp)
                }
                IconButton(onClick = {
                    store.set(setting, items.filterIndexed { i, _ -> i != index })
                    store.set(other, store.get(other) + encoded)
                }) {
                    LauncherIcon(LauncherIcons.Layers, contentDescription = "Move to the other pill", size = 20.dp)
                }
                IconButton(onClick = { store.set(setting, items.filterIndexed { i, _ -> i != index }) }) {
                    LauncherIcon(LauncherIcons.Close, contentDescription = "Remove", size = 20.dp)
                }
            }
        }
        Row {
            OutlinedButton(onClick = { onAdd(setting) }) { Text("Add item") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { store.reset(setting) }) { Text("Reset") }
        }
    }
}

private fun List<String>.moved(index: Int, by: Int): List<String> {
    val target = index + by
    if (target !in indices) return this
    val list = toMutableList()
    list.add(target, list.removeAt(index))
    return list
}

/** Entry to the live wallpaper screen; the values themselves are listed below it. */
@Composable
internal fun WallpaperEditor() {
    val context = LocalContext.current
    InfoCard {
        Text("Wallpaper screen", style = MaterialTheme.typography.titleMedium)
        BodyText(
            "Your wallpaper library: recent wallpapers (each once), favourites, everything you added, and phone folders you link. " +
                "Place a picture with pinch and drag, give it parallax, and set blur and dim for the home screen, the drawer and " +
                "search, all seen live on the real screens before you apply."
        )
        Button(onClick = { openOnHome(context, "wallpaper") }, modifier = Modifier.padding(top = 8.dp)) { Text("Open the wallpaper screen") }
        BodyText(
            "Quick switch: hold empty space on the home screen → Wallpaper shows your favourites and recent wallpapers in one row, with Shuffle. " +
                "A saved layout can bring its own wallpaper (\"Only for the layout\" in the wallpaper screen).",
            Modifier.padding(top = 8.dp),
        )
    }
}

/** Saved layouts (list, export, import) and the full backup. */
@Composable
internal fun LayoutsAndBackupEditor() {
    val context = LocalContext.current
    val data = remember { LauncherData.get(context) }
    val state by data.layouts.state.collectAsState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLayout = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val id = exporting ?: return@rememberLauncherForActivityResult
        exporting = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { data.layouts.export(id, it) } ?: false }.getOrDefault(false)
            }
            message = if (ok) "Layout exported" else "Export failed"
        }
    }
    val importLayout = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { data.layouts.import(it) } }.getOrNull()
            }
            message = if (added != null) "Imported \"${added.name}\". Apps that are not installed stay empty." else "That file is not a saved layout"
        }
    }
    val writeBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { data.backup(it) } != null }.getOrDefault(false)
            }
            message = if (ok) "Backup saved" else "Backup failed"
        }
    }
    val readBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmRestore = uri }

    InfoCard {
        Text("Saved layouts", style = MaterialTheme.typography.titleMedium)
        BodyText("Switch between them from the home menu or the layouts sheet. Hold a layout there to rename, duplicate or give it a wallpaper.")
        if (state.layouts.isEmpty()) BodyText("None yet.", Modifier.padding(top = 8.dp))
        for (layout in state.layouts) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(layout.name + if (layout.id == state.current) "  (current)" else "", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${layout.layout.pages.size} page(s)" + if (layout.wallpaper != null) ", own wallpaper" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = {
                    exporting = layout.id
                    exportLayout.launch("${layout.name}.lllayout")
                }) { Text("Export") }
                IconButton(onClick = { data.layouts.delete(layout.id) }) {
                    LauncherIcon(LauncherIcons.Delete, contentDescription = "Delete", size = 20.dp)
                }
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { openOnHome(context, "layouts") }) { Text("Open layouts") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { importLayout.launch(arrayOf("*/*")) }) { Text("Import a layout") }
        }
    }

    InfoCard {
        Text("Backup", style = MaterialTheme.typography.titleMedium)
        BodyText(
            "One file with your settings, home screen, tags and tabs, app names and icons, saved layouts and wallpapers. " +
                "Counters and measurements of this phone are left out."
        )
        Row(Modifier.padding(top = 8.dp)) {
            Button(onClick = {
                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                writeBackup.launch("liquid-launcher-$stamp.llbackup")
            }) { Text("Back up") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { readBackup.launch(arrayOf("*/*")) }) { Text("Restore…") }
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("Everything you set up now is replaced by the backup. The redirect keeps working.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    scope.launch {
                        val error = withContext(Dispatchers.IO) {
                            runCatching { context.contentResolver.openInputStream(uri)?.use { data.restore(it) } ?: "Could not open the file" }
                                .getOrElse { it.message ?: it.toString() }
                        }
                        WallpaperStore.get(context).load()
                        message = error?.let { "Restore failed: $it" } ?: "Restored"
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }
}

/** Presets, font import and theme files. The theme's options are listed below this card. */
@Composable
internal fun ThemeEditor(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontsDir = remember { java.io.File(context.filesDir, "fonts") }
    var message by remember { mutableStateOf<String?>(null) }
    val fontFile by store.observe(LauncherSettings.ThemeFontFile)

    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    val display = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "font.ttf"
                    val clean = display.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    fontsDir.mkdirs()
                    context.contentResolver.openInputStream(uri)?.use { input -> java.io.File(fontsDir, clean).outputStream().use { input.copyTo(it) } }
                    // Fails here, not later on the home screen, if the file is not a font.
                    android.graphics.Typeface.createFromFile(java.io.File(fontsDir, clean))
                    clean
                }.getOrNull()
            }
            if (name == null) {
                message = "That file could not be used as a font (TTF or OTF)"
            } else {
                store.set(LauncherSettings.ThemeFontFile, name)
                store.set(LauncherSettings.ThemeFont, "file")
                message = "Font imported and in use"
            }
        }
    }
    val exportTheme = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { dev.liquid.launcher.core.data.Themes.export(store, fontsDir, it) } != null }.getOrDefault(false)
            }
            message = if (ok) "Theme exported" else "Export failed"
        }
    }
    val importTheme = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { dev.liquid.launcher.core.data.Themes.import(store, fontsDir, it) } ?: false }.getOrDefault(false)
            }
            message = if (ok) "Theme applied" else "That file is not a theme"
        }
    }

    InfoCard {
        Text("Presets", style = MaterialTheme.typography.titleMedium)
        BodyText("A starting point: colours, fonts, icon shape and the glass look. Glass quality is not changed.")
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            for (preset in dev.liquid.launcher.core.data.Themes.presets) {
                OutlinedButton(onClick = { dev.liquid.launcher.core.data.Themes.apply(store, preset) }) { Text(preset.name) }
            }
        }
    }
    InfoCard {
        Text("Font file and theme files", style = MaterialTheme.typography.titleMedium)
        BodyText(if (fontFile.isBlank()) "No font imported." else "Imported font: $fontFile")
        Row(Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { pickFont.launch(arrayOf("font/*", "application/octet-stream", "application/x-font-ttf", "*/*")) }) { Text("Import a font") }
        }
        Row(Modifier.padding(top = 8.dp)) {
            Button(onClick = { exportTheme.launch("my-theme.lltheme") }) { Text("Export theme") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { importTheme.launch(arrayOf("*/*")) }) { Text("Import theme") }
        }
    }
    message?.let { text ->
        AlertDialog(onDismissRequest = { message = null }, text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } })
    }
}
