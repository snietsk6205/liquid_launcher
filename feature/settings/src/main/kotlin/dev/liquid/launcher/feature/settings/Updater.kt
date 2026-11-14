package dev.liquid.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.liquid.launcher.core.common.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A build published on the project's GitHub releases. */
data class Release(val version: String, val build: Int, val url: String, val size: Long, val page: String)

sealed interface UpdateCheck {
    data class Newer(val release: Release) : UpdateCheck
    data class Current(val release: Release) : UpdateCheck
    data class Failed(val message: String) : UpdateCheck
}

/**
 * The in-app updater: asks GitHub for the latest release, downloads its APK and hands it to
 * the system installer. Nothing happens without the user asking (or switching on the daily
 * check). Builds are signed with the same key, so they install over this one and keep
 * everything.
 */
object Updater {
    const val REPOSITORY = "snietsk6205/liquid_launcher"
    private const val LATEST = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    private const val PREFS = "updater"

    /** This install's build number (the CI run number). */
    fun installedBuild(context: Context): Long = try {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    } catch (e: Exception) {
        0
    }

    fun installedVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: Exception) {
        ""
    }

    suspend fun check(context: Context): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(LATEST).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "LiquidLauncher-updater")
            }
            val code = connection.responseCode
            if (code != 200) {
                connection.disconnect()
                return@withContext UpdateCheck.Failed(if (code == 404) "No release found (is the repository private?)" else "GitHub answered $code")
            }
            val text = connection.inputStream.use { it.readBytes().decodeToString() }
            connection.disconnect()
            val json = Json.parseToJsonElement(text).jsonObject
            val release = parse(json) ?: return@withContext UpdateCheck.Failed("The latest release has no APK")
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("lastCheck", System.currentTimeMillis()).apply()
            DebugLog.event("Updater", "latest release ${release.version} build ${release.build}, installed build ${installedBuild(context)}")
            if (release.build > installedBuild(context)) UpdateCheck.Newer(release) else UpdateCheck.Current(release)
        } catch (e: Exception) {
            DebugLog.error("Updater", "check failed: $e")
            UpdateCheck.Failed("Could not reach GitHub (${e.javaClass.simpleName})")
        }
    }

    /** The release in GitHub's answer: its version tag and the APK asset ("…-build31.apk"). */
    internal fun parse(json: JsonObject): Release? {
        val tag = json["tag_name"]?.jsonPrimitive?.content ?: return null
        val page = json["html_url"]?.jsonPrimitive?.content.orEmpty()
        val asset = json["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { asset ->
            asset["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true
        } ?: return null
        val name = asset["name"]!!.jsonPrimitive.content
        val build = Regex("build(\\d+)").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val url = asset["browser_download_url"]?.jsonPrimitive?.content ?: return null
        val size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L
        return Release(tag.removePrefix("v"), build, url, size, page)
    }

    /** True when the daily check is on and the last check is more than a day old. */
    fun dueForDailyCheck(context: Context, enabled: Boolean): Boolean {
        if (!enabled) return false
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("lastCheck", 0)
        return System.currentTimeMillis() - last > 24 * 3_600_000L
    }

    /** Downloads the release's APK into the app's cache; [progress] gets 0..1. */
    suspend fun download(context: Context, release: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "LiquidLauncher-${release.version}-build${release.build}.apk")
        val connection = (URL(release.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "LiquidLauncher-updater")
        }
        val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.size
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        connection.disconnect()
        DebugLog.event("Updater", "downloaded ${target.length() / 1024} KB")
        target
    }

    /** Whether the user allowed this app to install apps (Android asks once per app). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Hands the APK to the system installer, which asks for confirmation. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
