package dev.liquid.launcher.feature.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.data.AppRef
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.data.LauncherData
import dev.liquid.launcher.core.data.PillSlot
import dev.liquid.launcher.core.data.SlotGestures
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Settings → Pill → Study item (0.9.1): what the pill's study item shows (an app's icon or your own
 * picture are picked here; the icon, frame, size, colour and ring are the options below), and a way to put
 * it in the pill. Tapping the item grows the study hub out of the pill; holding it writes a note.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PillStudyEditor(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data = remember { LauncherData.get(context) }
    val apps by data.apps.apps.collectAsState()
    val look by store.observe(LauncherSettings.PillStudyLook)
    val appKey by store.observe(LauncherSettings.PillStudyApp)
    val picture by store.observe(LauncherSettings.PillStudyPicture)
    val raw by store.observe(LauncherSettings.PillSlots)
    val slots = remember(raw) { PillSlot.decodeAll(raw) }
    val inPill = slots.any { it == PillSlot.Study || (it is PillSlot.Action && it.action == LauncherAction.Today) }
    var pickApp by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        scope.launch {
            val path = withContext(Dispatchers.IO) { savePicture(context, uri) }
            working = false
            if (path != null) {
                store.set(LauncherSettings.PillStudyPicture, path)
                store.set(LauncherSettings.PillStudyLook, "picture")
            }
        }
    }

    InfoCard {
        Text("Study item", style = MaterialTheme.typography.titleMedium)
        BodyText(
            "The pill's \"Today\" button or \"Study block\" item. A tap grows the study hub out of the pill, holding it writes a note " +
                "for tonight's check-in, and a ring around it fills as the day's blocks are done.",
        )
        val appName = AppRef.parse(appKey)?.let { ref -> apps.firstOrNull { it.ref == ref }?.label }
        BodyText(
            "Now: " + when (look) {
                "app" -> "the icon of " + (appName ?: "an app (pick one)")
                "picture" -> if (picture.isNotBlank()) "your picture" else "your picture (pick one)"
                "circle" -> "a liquid circle"
                "text" -> "the block's name"
                else -> "an icon"
            } + if (inPill) "" else ". It is not in the pill yet.",
            Modifier.padding(top = 4.dp),
        )
        FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickApp = true }) { Text("Use an app's icon") }
            OutlinedButton(
                enabled = !working,
                onClick = {
                    try {
                        pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } catch (e: Exception) {
                        DebugLog.error("Settings", "photo picker failed: $e")
                    }
                },
            ) { Text(if (working) "Saving…" else "Use a picture") }
            if (!inPill) {
                OutlinedButton(onClick = {
                    // Next to the search field, where the thumb rests.
                    store.set(LauncherSettings.PillSlots, (slots + PillSlot.Action(LauncherAction.Today)).map { it.encode() })
                }) { Text("Add it to the pill") }
            }
        }
    }
    if (pickApp) {
        AppPickerDialog("Show which app's icon?", onDismiss = { pickApp = false }) { app ->
            pickApp = false
            store.set(LauncherSettings.PillStudyApp, app.ref.key)
            store.set(LauncherSettings.PillStudyLook, "app")
        }
    }
}

/**
 * Copies the picked picture into the launcher's files as a small square (256 px, centre crop), so it loads
 * at once and survives the photo being deleted. A new name each time, so the home screen sees the change;
 * the older copies are removed. Returns the file's path, or null.
 */
private fun savePicture(context: Context, uri: Uri): String? = try {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 256) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: throw IllegalStateException("not a picture")
    val side = minOf(decoded.width, decoded.height)
    val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
    val scaled = if (side > 256) Bitmap.createScaledBitmap(square, 256, 256, true) else square
    val dir = File(context.filesDir, "pill").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "study-${System.currentTimeMillis()}.png")
    file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
    file.absolutePath
} catch (e: Exception) {
    DebugLog.error("Settings", "could not keep the picture: $e")
    null
}

/** The four gestures of one pill item; each one "as usual" until set. */
@Composable
internal fun SlotGesturesDialog(slot: PillSlot, title: String, store: SettingsStore, onDismiss: () -> Unit) {
    val raw by store.observe(LauncherSettings.PillSlotGestures)
    val all = remember(raw) { SlotGestures.decodeAll(raw) }
    val code = slot.encode()
    val current = all[code] ?: SlotGestures()
    var picking by remember { mutableStateOf<String?>(null) }
    val isStudy = slot == PillSlot.Study || (slot is PillSlot.Action && slot.action == LauncherAction.Today)

    fun save(next: SlotGestures) = store.set(LauncherSettings.PillSlotGestures, SlotGestures.encodeAll(all + (code to next)))

    val rows = listOf(
        Triple("tap", "Tap", current.tap),
        Triple("long", "Long press", current.longPress),
        Triple("up", "Swipe up", current.swipeUp),
        Triple("down", "Swipe down", current.swipeDown),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                rows.forEach { (id, label, action) ->
                    val usual = when (id) {
                        "tap" -> "as usual"
                        "long" -> if (isStudy) "as usual (write a note)" else "the pill's long press"
                        else -> "the pill's swipe"
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { picking = id }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (action == null) usual else actionLabel(action),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (action != null) {
                            TextButton(onClick = {
                                save(
                                    when (id) {
                                        "tap" -> current.copy(tap = null)
                                        "long" -> current.copy(longPress = null)
                                        "up" -> current.copy(swipeUp = null)
                                        else -> current.copy(swipeDown = null)
                                    }
                                )
                            }) { Text("Usual") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
    picking?.let { id ->
        val label = rows.first { it.first == id }.second
        ActionPickerDialog(label, current = rows.first { it.first == id }.third, onDismiss = { picking = null }) { action ->
            picking = null
            save(
                when (id) {
                    "tap" -> current.copy(tap = action)
                    "long" -> current.copy(longPress = action)
                    "up" -> current.copy(swipeUp = action)
                    else -> current.copy(swipeDown = action)
                }
            )
        }
    }
}
