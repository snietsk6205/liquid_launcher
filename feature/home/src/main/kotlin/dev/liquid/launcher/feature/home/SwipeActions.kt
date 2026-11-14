package dev.liquid.launcher.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.Activities
import dev.liquid.launcher.core.data.AppActivity
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.HomeItem
import dev.liquid.launcher.core.data.LauncherAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Gestures = listOf("up" to "Swipe up", "down" to "Swipe down", "double" to "Double tap")

/** What swiping up, swiping down and double-tapping one home icon do. A tap still opens the app. */
@Composable
fun SwipeActionsDialog(item: HomeItem, apps: List<AppEntry>, controller: HomeController) {
    var picking by remember(item.id) { mutableStateOf<String?>(null) }
    var screensOf by remember(item.id) { mutableStateOf<AppEntry?>(null) }
    var choosingScreenApp by remember(item.id) { mutableStateOf(false) }
    fun label(encoded: String?): String =
        LauncherAction.label(LauncherAction.decode(encoded ?: "none")) { ref -> apps.firstOrNull { it.ref == ref }?.label }
    fun current(which: String) = when (which) {
        "up" -> item.swipeUp
        "down" -> item.swipeDown
        else -> item.doubleTap
    }
    fun set(which: String, action: LauncherAction) {
        controller.data.layout.layout.value.let { layout ->
            dev.liquid.launcher.core.data.LayoutOps.setIconAction(layout, item.id, which, action.encode())?.let { controller.data.layout.commit(it) }
        }
        picking = null
        screensOf = null
        choosingScreenApp = false
    }

    val which = picking
    if (which == null) {
        AlertDialog(
            onDismissRequest = { controller.swipeEditing = null },
            title = { Text("Gestures on this icon") },
            text = {
                Column {
                    Text("A tap still opens the app. With a double tap set, a single tap waits a moment to tell the two apart.")
                    for ((id, title) in Gestures) {
                        Column(Modifier.fillMaxWidth().clickable { picking = id }.padding(vertical = 12.dp)) {
                            Text(title, style = MaterialTheme.typography.bodyLarge)
                            Text(label(current(id)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { controller.swipeEditing = null }) { Text("Done") } },
        )
        return
    }
    val title = Gestures.first { it.first == which }.second
    val visible = remember(apps) { apps.filter { !it.hidden } }

    // A screen of one app.
    screensOf?.let { app ->
        val context = LocalContext.current
        val screens by produceState<List<AppActivity>?>(null, app.ref) {
            value = withContext(Dispatchers.IO) { Activities.of(context, app.component.packageName) }
        }
        AlertDialog(
            onDismissRequest = { screensOf = null },
            title = { Text("Screens of ${app.label}") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    val list = screens
                    when {
                        list == null -> Text("Reading…")
                        list.isEmpty() -> Text("This app lets other apps open none of its screens directly.")
                        else -> for (screen in list) {
                            Column(Modifier.fillMaxWidth().clickable { set(which, LauncherAction.LaunchActivity(screen.pkg, screen.cls, screen.label)) }.padding(vertical = 8.dp)) {
                                Text(screen.label, style = MaterialTheme.typography.bodyLarge)
                                Text(screen.shortName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { screensOf = null }) { Text("Back") } },
        )
        return
    }

    // Pick an action, an app to open, or one of its screens.
    AlertDialog(
        onDismissRequest = { picking = null },
        title = { Text(if (choosingScreenApp) "Screen of which app?" else title) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (!choosingScreenApp) {
                    for ((action, name) in LauncherAction.builtIns) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { set(which, action) }.padding(vertical = 10.dp),
                        )
                    }
                    Text(
                        "Open a screen of an app…",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { choosingScreenApp = true }.padding(vertical = 10.dp),
                    )
                    Text("Open an app", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                for (app in visible) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (choosingScreenApp) screensOf = app else set(which, LauncherAction.LaunchApp(app.ref))
                        }.padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (choosingScreenApp) choosingScreenApp = false else picking = null }) { Text("Back") } },
    )
}
