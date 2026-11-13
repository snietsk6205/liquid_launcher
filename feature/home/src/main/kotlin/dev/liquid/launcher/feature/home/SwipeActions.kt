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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.HomeItem
import dev.liquid.launcher.core.data.LauncherAction

/** What swiping up and down on one home icon does. Tapping still opens the app. */
@Composable
fun SwipeActionsDialog(item: HomeItem, apps: List<AppEntry>, controller: HomeController) {
    var picking by remember(item.id) { mutableStateOf<Boolean?>(null) } // true = up, false = down
    fun label(encoded: String?): String =
        LauncherAction.label(LauncherAction.decode(encoded ?: "none")) { ref -> apps.firstOrNull { it.ref == ref }?.label }

    val direction = picking
    if (direction == null) {
        AlertDialog(
            onDismissRequest = { controller.swipeEditing = null },
            title = { Text("Swipe actions") },
            text = {
                Column {
                    Text("Swipe up or down on this icon to run an action or open another app. Tapping still opens the app.")
                    for ((up, title) in listOf(true to "Swipe up", false to "Swipe down")) {
                        Column(Modifier.fillMaxWidth().clickable { picking = up }.padding(vertical = 12.dp)) {
                            Text(title, style = MaterialTheme.typography.bodyLarge)
                            Text(label(if (up) item.swipeUp else item.swipeDown), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { controller.swipeEditing = null }) { Text("Done") } },
        )
        return
    }

    // Pick an action, or an app to open.
    val visible = remember(apps) { apps.filter { !it.hidden } }
    AlertDialog(
        onDismissRequest = { picking = null },
        title = { Text(if (direction) "Swipe up" else "Swipe down") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                for ((action, name) in LauncherAction.builtIns) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable {
                            controller.setSwipe(item.id, direction, action)
                            picking = null
                        }.padding(vertical = 10.dp),
                    )
                }
                Text("Open an app", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                for (app in visible) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable {
                            controller.setSwipe(item.id, direction, LauncherAction.LaunchApp(app.ref))
                            picking = null
                        }.padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { picking = null }) { Text("Back") } },
    )
}
