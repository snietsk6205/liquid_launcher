package dev.liquid.launcher.feature.settings

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.liquid.launcher.core.data.AutoBackup
import dev.liquid.launcher.core.data.LauncherData
import dev.liquid.launcher.core.data.SettingsAI
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * W5 (0.10.1): Settings → System → Backup and Claude. Back up now (wallpapers when chosen), every day into a folder,
 * and a restore that first shows what it would change. Then Claude: the whole registry as Markdown with the rules
 * for answering, sent with your question to the Claude app or saved as a file, and its answer applied after you
 * see every change, with one tap to undo.
 */

private fun stamp(time: Long, pattern: String = "d MMM yyyy, HH:mm"): String = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))

/** "Backups" for a tree URI of the folder "Documents/Backups"; the tree's own id when it has no path. */
private fun folderName(uri: String): String = runCatching {
    DocumentsContract.getTreeDocumentId(Uri.parse(uri)).substringAfter(':').ifEmpty { "the chosen folder" }
}.getOrDefault("the chosen folder")

@Composable
internal fun BackupEditor(store: SettingsStore) {
    val context = LocalContext.current
    val data = remember { LauncherData.get(context) }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Pair<Uri, LauncherData.BackupPreview>?>(null) }
    val auto by store.observe(LauncherSettings.BackupAuto)
    val folder by store.observe(LauncherSettings.BackupFolder)
    val last by store.observe(LauncherSettings.BackupLast)
    val withWallpapers by store.observe(LauncherSettings.BackupWallpapers)
    // The daily job follows the switch and the folder.
    LaunchedEffect(auto, folder) { withContext(Dispatchers.IO) { AutoBackup.schedule(context) } }

    val writeBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { data.backup(it, AutoBackup.dirs(store)) } != null }.getOrDefault(false)
            }
            message = if (ok) "Backup saved" else "Backup failed"
        }
    }
    val readBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { data.previewBackup(it) } }.getOrNull()
            }
            if (result == null || result.error != null) message = "Cannot read this backup: ${result?.error ?: "the file did not open"}"
            else preview = uri to result
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        store.set(LauncherSettings.BackupFolder, uri.toString())
        store.set(LauncherSettings.BackupAuto, true)
        scope.launch {
            val error = withContext(Dispatchers.IO) { AutoBackup.run(context) }
            message = error?.let { "The first backup into ${folderName(uri.toString())} failed: $it" }
                ?: "Backed up into ${folderName(uri.toString())}. A new copy is written every day."
        }
    }

    InfoCard {
        Text("Back up now", style = MaterialTheme.typography.titleMedium)
        BodyText(
            "One file with your settings, home screen, tags and tabs, app names and icons, saved layouts, the theme and its " +
                "pictures, fonts" + (if (withWallpapers) " and wallpapers" else "; wallpapers when \"Include wallpapers\" is on") +
                ". Counters and measurements of this phone are left out.",
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { writeBackup.launch("liquid-launcher-${stamp(System.currentTimeMillis(), "yyyy-MM-dd")}.llbackup") }) { Text("Back up now") }
            OutlinedButton(onClick = { readBackup.launch(arrayOf("*/*")) }) { Text("Restore…") }
        }
    }

    InfoCard {
        Text("Every day", style = MaterialTheme.typography.titleMedium)
        BodyText(
            if (folder.isBlank()) "Choose a folder (on the phone or a cloud drive) and a backup is written into it every day."
            else "Into ${folderName(folder)}, " + (if (auto) "every day" else "paused (\"Back up every day\" is off)") + ".",
        )
        last.takeIf { it.isNotBlank() }?.let { text ->
            val time = text.substringBefore('|').toLongOrNull() ?: 0L
            val what = text.substringAfter('|')
            BodyText(
                if (what.startsWith("!")) "Last try ${stamp(time)} failed: ${what.removePrefix("!")}" else "Last backup ${stamp(time)}: $what",
                Modifier.padding(top = 4.dp),
            )
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickFolder.launch(null) }) { Text(if (folder.isBlank()) "Choose a folder" else "Change folder") }
            if (folder.isNotBlank()) {
                TextButton(onClick = {
                    scope.launch {
                        val error = withContext(Dispatchers.IO) { AutoBackup.run(context) }
                        message = error?.let { "Backup failed: $it" } ?: "Backed up into ${folderName(folder)}"
                    }
                }) { Text("Back up there now") }
            }
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
    preview?.let { (uri, p) ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Restore this backup?") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (p.created > 0) Text("Made ${stamp(p.created)}", style = MaterialTheme.typography.bodyMedium)
                    for (line in p.parts) Text("• $line", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        if (p.settings.isEmpty()) "No option changes." else "${p.settings.size} option(s) change:",
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp),
                    )
                    for ((title, now, then) in p.settings.take(80)) {
                        Text("$title: $now → $then", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (p.settings.size > 80) Text("and ${p.settings.size - 80} more", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Everything you set up now is replaced by the backup. The redirect keeps working.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    preview = null
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
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun ClaudeEditor(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var patch by remember { mutableStateOf<SettingsAI.Patch?>(null) }
    var undo by remember { mutableStateOf<SettingsAI.Undo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val claudeApp by store.observe(LauncherSettings.StudyClaudeApp)
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    fun document() = SettingsAI.export(store, version, question)

    val saveMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = document()
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false)
            }
            message = if (ok) "Saved. Give the file to Claude with your question." else "Could not save the file"
        }
    }

    InfoCard {
        Text("Tune with Claude", style = MaterialTheme.typography.titleMedium)
        BodyText(
            "The launcher writes down every option (what it does, its range, its default and its value now) with the rules " +
                "for answering: only a list of changes, as JSON. Claude's answer comes back here, and you see every change " +
                "before it is applied. The launcher itself sends nothing anywhere.",
        )
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Your question") },
            placeholder = { Text("For example: make the drawer calmer and the glass clearer") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Liquid Launcher settings")
                    .putExtra(Intent.EXTRA_TEXT, document())
                val direct = claudeApp.trim().takeIf { it.isNotEmpty() }?.let { Intent(send).setPackage(it) }
                if (direct == null || !startSafely(context, direct)) {
                    startSafely(context, Intent.createChooser(send, "Send to Claude").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Send to Claude") }
            OutlinedButton(onClick = { saveMarkdown.launch("liquid-launcher-settings.md") }) { Text("Export for AI") }
        }
    }

    InfoCard {
        Text("Apply Claude's suggestions", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = answer,
            onValueChange = {
                answer = it
                patch = null
            },
            label = { Text("Claude's answer") },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (text.isNotBlank()) {
                    answer = text
                    patch = SettingsAI.parse(text, store)
                } else {
                    message = "The clipboard is empty"
                }
            }) { Text("Paste") }
            Button(onClick = { patch = SettingsAI.parse(answer, store) }, enabled = answer.isNotBlank()) { Text("Check") }
        }
        patch?.let { p ->
            Column(Modifier.padding(top = 10.dp)) {
                p.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                p.why?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium) }
                for (change in p.changes) {
                    val title = change.setting?.title ?: change.id
                    if (change.problem == null) {
                        Text("$title: ${change.now} → ${change.then}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
                    } else {
                        Text(
                            "$title (${change.id}): ${change.problem}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                val valid = p.valid.size
                if (valid > 0) {
                    Button(
                        onClick = {
                            undo = SettingsAI.apply(p, store)
                            patch = null
                            answer = ""
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text(if (valid == 1) "Apply 1 change" else "Apply $valid changes") }
                }
            }
        }
        undo?.let { u ->
            Row(Modifier.padding(top = 8.dp)) {
                BodyText("Applied.", Modifier.padding(top = 10.dp, end = 8.dp))
                OutlinedButton(onClick = {
                    SettingsAI.undo(u, store)
                    undo = null
                    message = "Undone: the options are as they were"
                }) { Text("Undo") }
            }
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}
