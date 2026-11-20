package dev.liquid.launcher.core.data

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * E1 board photos (0.11.2): the camera writes into a private file of the launcher, the way every camera app supports,
 * and the launcher then saves it into Pictures/ESSAI/<module>/<date>_<module>.jpg.
 *
 * Up to 0.11.1 the camera was handed a hidden ("pending") gallery entry, which not every camera can write into, and the
 * photo was revealed only if the camera's answer came back to the same launcher window; one restarted while the camera
 * was open lost it (Med: "a folder appeared in ESSAI and then TD micro, but the picture doesn't save"). Now the photo
 * being taken is written down in the settings, so a restarted launcher still saves it, and a photo is kept whenever its
 * file has data, whatever the camera answers. The camera app can be chosen ("study.cameraApp").
 */
object BoardPhotos {
    /** The app's FileProvider (also used for updates), and the cache folder it shares for photos. */
    private const val AUTHORITY_SUFFIX = ".updates"
    private const val FOLDER = "photos"

    /** A photo left by a launcher restarted with the camera open is saved by the next start after this long. */
    private const val STALE_MS = 30 * 60_000L

    /** A photo being taken: its private file, the module it belongs to, the name it gets in the gallery, when. */
    data class Pending(val path: String, val module: String, val name: String, val at: Long)

    fun encode(p: Pending): String = listOf(p.path, p.module, p.name, p.at.toString()).joinToString("\n")

    fun decode(raw: String): Pending? {
        val parts = raw.split("\n")
        if (parts.size != 4 || parts[0].isBlank()) return null
        return Pending(parts[0], parts[1], parts[2], parts[3].toLongOrNull() ?: return null)
    }

    /** The module as a folder name: letters, digits, spaces, "_" and "-" only; "Notes" when nothing is left. */
    fun folderName(module: String): String = module.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifEmpty { "Notes" }

    /** The camera apps on the phone, as (package, name), by name. */
    fun cameras(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        return pm.queryIntentActivities(Intent(MediaStore.ACTION_IMAGE_CAPTURE), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    /**
     * The intent that takes [module]'s photo with the chosen camera (the phone's default when none is chosen or the
     * chosen one is gone), after writing down what is being taken; null when the phone has no camera app.
     */
    fun start(context: Context, settings: SettingsStore, module: String, now: Long = System.currentTimeMillis()): Intent? {
        val folder = folderName(module)
        val dir = File(context.cacheDir, FOLDER).apply { mkdirs() }
        val file = File(dir, "$now.jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        val capture = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            clipData = ClipData.newRawUri("photo", uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pm = context.packageManager
        val chosen = settings.get(LauncherSettings.StudyCameraApp).trim()
        val withChosen = chosen.takeIf { it.isNotEmpty() }?.let { pkg ->
            Intent(capture).setPackage(pkg).takeIf { it.resolveActivity(pm) != null }
        }
        if (chosen.isNotEmpty() && withChosen == null) DebugLog.event("Study", "board photo: the chosen camera ($chosen) is gone; the phone's default is used")
        val intent = withChosen ?: capture.takeIf { it.resolveActivity(pm) != null } ?: return null
        val date = java.time.LocalDate.now().toString()
        settings.set(LauncherSettings.StudyPendingPhoto, encode(Pending(file.path, folder, "${date}_$folder.jpg", now)))
        DebugLog.event("Study", "board photo: camera opened for \"$folder\" (${intent.`package` ?: "the phone's default camera"})")
        return intent
    }

    /**
     * The camera answered ([answeredOk]; null: no answer came, a photo left from before a restart). Saves the photo
     * written down by [start] when its file has data and returns its module; null when there was none to save.
     */
    suspend fun finish(context: Context, settings: SettingsStore, answeredOk: Boolean?): String? {
        val pending = decode(settings.get(LauncherSettings.StudyPendingPhoto)) ?: return null
        settings.reset(LauncherSettings.StudyPendingPhoto)
        val answer = when (answeredOk) {
            true -> "the camera said done"
            false -> "the camera said cancelled"
            null -> "found after the launcher restarted"
        }
        val file = File(pending.path)
        val size = file.length()
        if (size <= 0L) {
            file.delete()
            DebugLog.event("Study", "board photo: no photo came back for \"${pending.module}\" ($answer)")
            return null
        }
        val saved = withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, pending.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ESSAI/${pending.module}")
                    put(MediaStore.Images.Media.DATE_TAKEN, pending.at)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
                try {
                    resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("no stream")
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                    true
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } catch (e: Exception) {
                DebugLog.error("Study", "board photo: could not save it in the gallery: $e")
                false
            }
        }
        if (!saved) {
            // Kept for the next try: written down again, the file stays.
            settings.set(LauncherSettings.StudyPendingPhoto, encode(pending))
            return null
        }
        file.delete()
        DebugLog.event("Study", "board photo: saved in Pictures/ESSAI/${pending.module} (${size / 1024} KB; $answer)")
        return pending.module
    }

    /**
     * At start: a photo written down more than [STALE_MS] ago whose answer never came (the launcher was restarted while
     * the camera was open) is saved if it has data; and private files no longer written down are cleared.
     */
    suspend fun sweep(context: Context, settings: SettingsStore, now: Long = System.currentTimeMillis()): String? {
        val pending = decode(settings.get(LauncherSettings.StudyPendingPhoto))
        val module = if (pending != null && now - pending.at > STALE_MS) finish(context, settings, null) else null
        val keep = decode(settings.get(LauncherSettings.StudyPendingPhoto))?.path
        withContext(Dispatchers.IO) {
            File(context.cacheDir, FOLDER).listFiles()?.forEach { f ->
                if (f.path != keep && now - f.lastModified() > STALE_MS) f.delete()
            }
        }
        return module
    }
}
