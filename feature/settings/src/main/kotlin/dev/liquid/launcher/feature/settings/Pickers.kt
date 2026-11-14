package dev.liquid.launcher.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.Fuzzy
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.data.LauncherData

/** Label of an action as the user sees it, with app names resolved. */
@Composable
internal fun actionLabel(action: LauncherAction): String {
    val data = LauncherData.get(LocalContext.current)
    val apps by data.apps.apps.collectAsState()
    val layouts by data.layouts.state.collectAsState()
    return LauncherAction.label(action, { id -> layouts.layouts.firstOrNull { it.id == id }?.name }) { ref -> apps.firstOrNull { it.ref == ref }?.label }
}

/** Choose a built-in action or "open an app". */
@Composable
internal fun ActionPickerDialog(
    title: String,
    current: LauncherAction?,
    includeNone: Boolean = true,
    onDismiss: () -> Unit,
    onPick: (LauncherAction) -> Unit,
) {
    var pickingApp by remember { mutableStateOf(false) }
    var pickingScreenApp by remember { mutableStateOf(false) }
    var screensOf by remember { mutableStateOf<AppEntry?>(null) }
    val savedLayouts = LauncherData.get(LocalContext.current).layouts.state.collectAsState().value.layouts
    if (pickingApp) {
        AppPickerDialog(title = "Open which app?", onDismiss = { pickingApp = false }) { app ->
            pickingApp = false
            onPick(LauncherAction.LaunchApp(app.ref))
        }
        return
    }
    screensOf?.let { app ->
        ScreenPickerDialog(app, onDismiss = { screensOf = null }) { screen ->
            screensOf = null
            onPick(LauncherAction.LaunchActivity(screen.pkg, screen.cls, screen.label))
        }
        return
    }
    if (pickingScreenApp) {
        AppPickerDialog(title = "A screen of which app?", onDismiss = { pickingScreenApp = false }) { app ->
            pickingScreenApp = false
            screensOf = app
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(LauncherAction.builtIns.filter { includeNone || it.first != LauncherAction.None }) { (action, label) ->
                    ChoiceLine(label, selected = action == current) { onPick(action) }
                }
                items(LauncherAction.sections) { (id, label) ->
                    val action = LauncherAction.Section(id)
                    ChoiceLine("Open: $label", selected = action == current) { onPick(action) }
                }
                items(savedLayouts) { layout ->
                    val action = LauncherAction.SwitchLayout(layout.id)
                    ChoiceLine("Switch to layout: ${layout.name}", selected = action == current) { onPick(action) }
                }
                item {
                    ChoiceLine(
                        if (current is LauncherAction.LaunchApp) "Open an app (${actionLabel(current)})" else "Open an app…",
                        selected = current is LauncherAction.LaunchApp,
                    ) { pickingApp = true }
                }
                item {
                    ChoiceLine(
                        if (current is LauncherAction.LaunchActivity) "Open a screen of an app (${current.label})" else "Open a screen of an app…",
                        selected = current is LauncherAction.LaunchActivity,
                    ) { pickingScreenApp = true }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ChoiceLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Searchable list of installed apps. */
@Composable
internal fun AppPickerDialog(title: String, onDismiss: () -> Unit, onPick: (AppEntry) -> Unit) {
    val data = LauncherData.get(LocalContext.current)
    val apps by data.apps.apps.collectAsState()
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) { if (query.isBlank()) apps else Fuzzy.rank(query, apps, { it.label }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search apps") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.key }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon = app.icon
                            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(32.dp)) else Box(Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(app.label + if (app.isWorkProfile) " (work)" else "", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The screens of one app that can be opened directly, its launch screen first. */
@Composable
internal fun ScreenPickerDialog(app: AppEntry, onDismiss: () -> Unit, onPick: (dev.liquid.launcher.core.data.AppActivity) -> Unit) {
    val context = LocalContext.current
    val screens by androidx.compose.runtime.produceState<List<dev.liquid.launcher.core.data.AppActivity>?>(null, app.ref) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dev.liquid.launcher.core.data.Activities.of(context, app.component.packageName) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Screens of ${app.label}") },
        text = {
            val list = screens
            when {
                list == null -> Text("Reading…")
                list.isEmpty() -> Text("This app lets other apps open none of its screens directly.")
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.cls }) { screen ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(screen) }.padding(vertical = 8.dp)) {
                            Text(screen.label, style = MaterialTheme.typography.bodyLarge)
                            Text(screen.shortName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Back") } },
    )
}
