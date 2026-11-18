package dev.liquid.launcher.core.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * W5 (0.10.1): a backup every day into a folder you chose with the system's picker (any folder, a cloud drive's
 * too), named by date; the newest copies are kept ("Copies kept", 7 by default) and older ones deleted. Android's job
 * scheduler runs it about once a day while the battery is not low, also after a restart.
 */
object AutoBackup {
    private const val JOB_ID = 0x1B4C
    private val name = Regex("^liquid-launcher-\\d{4}-\\d{2}-\\d{2}\\.llbackup$")

    /** Starts or stops the daily job to match the settings. */
    fun schedule(context: Context) {
        val settings = SettingsStore.get(context)
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val on = settings.get(LauncherSettings.BackupAuto) && settings.get(LauncherSettings.BackupFolder).isNotBlank()
        if (!on) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, BackupJobService::class.java))
            .setPeriodic(24 * 60 * 60 * 1000L, 6 * 60 * 60 * 1000L)
            .setRequiresBatteryNotLow(true)
            .setPersisted(true)
            .build()
        val result = runCatching { scheduler.schedule(job) }.getOrDefault(JobScheduler.RESULT_FAILURE)
        DebugLog.event("Backup", if (result == JobScheduler.RESULT_SUCCESS) "daily backup scheduled" else "daily backup could not be scheduled")
    }

    /** Writes today's backup into the folder and deletes the oldest beyond the copies kept; null on success. */
    fun run(context: Context): String? {
        val settings = SettingsStore.get(context)
        val folder = settings.get(LauncherSettings.BackupFolder).takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: return "No folder chosen"
        val resolver = context.contentResolver
        val result = runCatching {
            val treeId = DocumentsContract.getTreeDocumentId(folder)
            val parent = DocumentsContract.buildDocumentUriUsingTree(folder, treeId)
            val file = "liquid-launcher-" + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + ".llbackup"
            // Today's copy replaces an earlier one of the same day.
            children(context, folder).filter { it.second == file }.forEach { DocumentsContract.deleteDocument(resolver, it.first) }
            val target = DocumentsContract.createDocument(resolver, parent, "application/zip", file) ?: error("the folder refused a new file")
            resolver.openOutputStream(target)?.use { out -> LauncherData.get(context).backup(out, dirs(settings)) } ?: error("could not write the file")
            // The newest copies stay.
            val keep = settings.get(LauncherSettings.BackupKeep)
            children(context, folder).filter { name.matches(it.second) }.sortedByDescending { it.second }.drop(keep)
                .forEach { DocumentsContract.deleteDocument(resolver, it.first) }
            file
        }
        val now = System.currentTimeMillis()
        return result.fold(
            onSuccess = { file ->
                settings.set(LauncherSettings.BackupLast, "$now|$file")
                DebugLog.event("Backup", "daily backup written: $file")
                null
            },
            onFailure = { e ->
                val why = e.message ?: e.toString()
                settings.set(LauncherSettings.BackupLast, "$now|!$why")
                DebugLog.error("Backup", "daily backup failed: $why")
                why
            },
        )
    }

    /** The folders whose files go into a backup: wallpapers only when chosen. */
    fun dirs(settings: SettingsStore): List<String> =
        listOf("layouts", "fonts", "theme") + if (settings.get(LauncherSettings.BackupWallpapers)) listOf("wallpaper") else emptyList()

    /** The folder's files: document URI and display name. */
    private fun children(context: Context, tree: Uri): List<Pair<Uri, String>> {
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val list = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val out = ArrayList<Pair<Uri, String>>()
        context.contentResolver.query(
            list, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                out += DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)) to (c.getString(1) ?: "")
            }
        }
        return out
    }
}

/** The daily job: writes the backup off the main thread. */
class BackupJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            val error = AutoBackup.run(applicationContext)
            jobFinished(params, error != null && error != "No folder chosen")
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
