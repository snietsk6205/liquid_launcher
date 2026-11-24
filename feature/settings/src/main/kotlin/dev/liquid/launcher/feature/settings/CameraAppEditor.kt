package dev.liquid.launcher.feature.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.data.BoardPhotos
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe
import kotlinx.coroutines.launch

/**
 * Settings → Study companion → More: the camera app for board photos (0.11.2, Med: "give me the option to select the
 * camera app I want, not the default one"), and a test photo, so the whole way can be tried outside a class: it is
 * saved in Pictures/ESSAI/Test like a board photo. 0.15.3 (D196): first, and the default, the launcher's own camera
 * (several photos in a row).
 */
@Composable
internal fun CameraAppEditor(store: SettingsStore) {
    val context = LocalContext.current
    val chosen by store.observe(LauncherSettings.StudyCameraApp)
    val mode by store.observe(LauncherSettings.StudyBoardCamera)
    val app = mode == "app"
    val cameras = remember { BoardPhotos.cameras(context) }
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { answer ->
        scope.launch {
            val module = BoardPhotos.finish(context.applicationContext, store, answer.resultCode == Activity.RESULT_OK)
            result = if (module != null) "Saved in Pictures/ESSAI/$module. Board photos go to the class's own folder."
            else "No photo came back from the camera. The debug log says what it answered."
        }
    }

    InfoCard {
        Text("Camera for board photos", style = MaterialTheme.typography.titleMedium)
        BodyText(
            "Board (a class's chip in Today, or the pill's and the home menu's) saves its photos in Pictures/ESSAI/<the class>. " +
                "The launcher's camera takes several in a row; a camera app takes one at a time.",
            Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
        CameraRow("The launcher's camera", "Several photos in a row, each saved at once; the volume keys take photos too", !app) {
            store.set(LauncherSettings.StudyBoardCamera, "launcher")
        }
        CameraRow("The phone's default camera", null, app && chosen.isBlank()) {
            store.set(LauncherSettings.StudyBoardCamera, "app")
            store.reset(LauncherSettings.StudyCameraApp)
        }
        for (camera in cameras) {
            // 0.13.0: every camera; one that cannot take a photo for another app opens as it is, and the photo is picked after.
            val label = if (camera.handsBack) camera.label else camera.label + " (opens; you pick the photo after)"
            CameraRow(label, camera.pkg, app && chosen == camera.pkg) {
                store.set(LauncherSettings.StudyBoardCamera, "app")
                store.set(LauncherSettings.StudyCameraApp, camera.pkg)
            }
        }
        if (app && !BoardPhotos.cameraAllowed(context)) {
            BodyText("Until the camera is allowed for the launcher, a camera app opens as it is and the photo is picked after.")
        }
        if (cameras.isEmpty()) BodyText("No camera app was found on the phone.")
        if (chosen.isNotBlank() && cameras.none { it.pkg == chosen }) {
            BodyText("The camera chosen before ($chosen) is no longer on the phone: the default one is used.")
        }
        OutlinedButton(
            onClick = {
                result = null
                if (!app) {
                    runCatching { context.startActivity(BoardPhotos.cameraIntent(context, "Test")) }
                        .onFailure { result = "The camera could not be opened: ${it.message}" }
                    return@OutlinedButton
                }
                val intent = runCatching { BoardPhotos.start(context, store, "Test") }.getOrNull()
                if (intent == null) result = "No camera app to take the photo." else runCatching { take.launch(intent) }.onFailure {
                    result = "The camera could not be opened: ${it.message}"
                }
            },
            modifier = Modifier.padding(top = 10.dp),
        ) {
            LauncherIcon(LauncherIcons.Camera, contentDescription = null, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            Text("Take a test photo")
        }
        result?.let { BodyText(it, Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun CameraRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
