package dev.liquid.launcher.feature.settings

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.common.KeyboardBand
import dev.liquid.launcher.core.common.KeyboardLine
import dev.liquid.launcher.core.common.WallpaperPalette
import dev.liquid.launcher.core.common.WallpaperTones
import dev.liquid.launcher.core.data.KeyboardTheme
import dev.liquid.launcher.core.data.ThemeDoc
import dev.liquid.launcher.core.data.ThemeStore
import dev.liquid.launcher.core.data.ThemedColors
import dev.liquid.launcher.core.data.WmKeyboard
import dev.liquid.launcher.core.designsystem.WallpaperBlur
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * 0.13.1 (D180, Med: "a setting I can turn on so that when the wallpaper changes, the background of my keyboard also
 * changes"). WM Keyboard follows the wallpaper's colours by itself, but on Android 13 and later no app may read the
 * wallpaper's picture (WM Keyboard paints soft fields of its colours instead). The launcher has the picture: it cuts the
 * part behind the keyboard, in the look chosen (as search shows it, or sharp), and writes it where WM Keyboard can read
 * it, Pictures/Liquid Launcher (it needs its "Photos" permission).
 *
 * WM Keyboard reads a theme's picture once per theme it shows, so a picture rewritten in place would wait until the
 * keyboard restarts. Hence two themes, "Liquid wallpaper 1" and "2", each on its own picture, added once: at every
 * wallpaper change the launcher writes the picture of the theme NOT on screen and switches WM Keyboard to it with its
 * SET_THEME command (it needs "Let other apps control the keyboard"). Every step and WM Keyboard's answer are logged.
 */
object KeyboardWallpaper {
    const val SLOTS = 2
    private const val FOLDER = "Liquid Launcher"
    private val lock = Mutex()
    private var started = false

    fun themeName(slot: Int) = "Liquid wallpaper ${slot + 1}"

    private fun fileName(slot: Int) = "keyboard-wallpaper-${slot + 1}.jpg"

    /** Where WM Keyboard reads slot [slot]'s picture from. */
    @Suppress("DEPRECATION")
    fun path(slot: Int): String =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "$FOLDER/${fileName(slot)}").absolutePath

    /** WM Keyboard may read the pictures: it holds "Photos" (all of them, not only some). */
    fun keyboardCanRead(context: Context): Boolean {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        return context.packageManager.checkPermission(permission, WmKeyboard.PACKAGE) == PackageManager.PERMISSION_GRANTED
    }

    /** The keyboard's height to cut for: the lowest height it opened at lately in search (its own picture's, else a guess). */
    fun keyboardPx(settings: SettingsStore, screenH: Int): Int {
        val openings = settings.get(LauncherSettings.SearchKeyboardOpenings).mapNotNull { it.toIntOrNull() }
        return KeyboardLine.guess(openings, screenH.toFloat()).roundToInt()
    }

    /** The picture for the keyboard from the launcher's wallpaper now, in the look chosen; null without a wallpaper. */
    suspend fun picture(context: Context, settings: SettingsStore, look: String = settings.get(LauncherSettings.KeyboardWallpaperLook)): Bitmap? =
        withContext(Dispatchers.Default) {
            val image = WallpaperStore.get(context).image.value ?: return@withContext null
            val canvas = image.asAndroidBitmap()
            val (screenW, screenH) = WallpaperStore.get(context).screen()
            val r = KeyboardBand.rect(canvas.width, canvas.height, screenW, screenH, keyboardPx(settings, screenH)) ?: return@withContext null
            val readable = if (canvas.config == Bitmap.Config.HARDWARE) canvas.copy(Bitmap.Config.ARGB_8888, false) else canvas
            val band = Bitmap.createBitmap(readable, r.left, r.top, r.width, r.height)
            val density = context.resources.displayMetrics.density
            val blurPx = KeyboardBand.blur(look, settings.get(LauncherSettings.SearchBlur) * density)
            // Blurred at a quarter of its size (search's own copy is made the same way), then back to the band's size.
            val soft = if (blurPx > 0.5f) {
                val small = WallpaperBlur.blur(band, blurPx)
                Bitmap.createScaledBitmap(small, band.width, band.height, true).also { if (it !== small) small.recycle() }
            } else {
                band.copy(Bitmap.Config.ARGB_8888, true)
            }
            if (band !== readable && band !== soft) band.recycle()
            val dim = KeyboardBand.dim(look, settings.get(LauncherSettings.SearchDim))
            if (dim > 0f) Canvas(soft).drawColor(android.graphics.Color.argb((dim * 255).roundToInt(), 0, 0, 0))
            soft
        }

    /**
     * Writes [bitmap] as slot [slot]'s picture in Pictures/Liquid Launcher, the launcher's own file (no permission
     * needed to write it). Null when written, else what went wrong.
     */
    suspend fun write(context: Context, slot: Int, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = fileName(slot)
        val relative = Environment.DIRECTORY_PICTURES + "/" + FOLDER + "/"
        runCatching {
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val existing = resolver.query(
                collection, arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                arrayOf(name, relative), null,
            )?.use { c -> if (c.moveToFirst()) android.content.ContentUris.withAppendedId(collection, c.getLong(0)) else null }
            val uri = existing ?: resolver.insert(collection, ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, relative)
            }) ?: return@runCatching "the picture could not be created in Pictures/$FOLDER"
            resolver.openOutputStream(uri, "wt")?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
                ?: return@runCatching "the picture could not be written"
            // Android may have named it otherwise (a file of that name left by an earlier install): the themes would
            // read the old one.
            @Suppress("DEPRECATION")
            val actual = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            if (actual != null && actual != path(slot)) {
                DebugLog.error("Keyboard", "wallpaper behind the keys: the picture went to $actual instead of ${path(slot)}")
                return@runCatching "the picture was saved as ${File(actual).name}: delete Pictures/$FOLDER/${fileName(slot)} in your files, then try again"
            }
            null
        }.getOrElse { "the picture could not be written: $it" }
    }

    /** The launcher's base theme for the keyboard (the one in Settings › Keyboard), in the wallpaper's colours now. */
    fun theme(context: Context, settings: SettingsStore, slot: Int): ThemeDoc? {
        val base = baseKeyboardTheme(context) ?: return null
        val palette: WallpaperPalette? = WallpaperTones.tone.value?.roles
            ?: runCatching { WallpaperPalette.fromSeed(ThemedColors.seed(context)) }.getOrNull()
        val built = KeyboardTheme.build(base, ThemeStore.doc(settings), palette, themeName(slot), System.currentTimeMillis() + slot)
        // Its other pictures (key textures) travel inside the file; the background is the path WM Keyboard reads.
        return KeyboardTheme.withPicture(ThemeStore.embed(built), path(slot))
    }

    /**
     * The hand-over now: the picture of the theme not on screen is rewritten and WM Keyboard switches to it. [why] is
     * for the log. Returns what happened, in words (also kept for Settings).
     */
    suspend fun follow(context: Context, settings: SettingsStore, why: String): String = lock.withLock {
        val result = followLocked(context, settings, why)
        settings.set(LauncherSettings.KeyboardWallpaperLast, result)
        result
    }

    private suspend fun followLocked(context: Context, settings: SettingsStore, why: String): String {
        if (!WmKeyboard.installed(context)) return "WM Keyboard is not on this phone"
        val picture = picture(context, settings) ?: return "no picture: the wallpaper is not the launcher's own (set one in the launcher's wallpaper screen)"
        val shown = settings.get(LauncherSettings.KeyboardWallpaperSlot).coerceIn(0, SLOTS - 1)
        val next = (shown + 1) % SLOTS
        val error = write(context, next, picture)
        picture.recycle()
        if (error != null) {
            DebugLog.error("Keyboard", "wallpaper behind the keys ($why): $error")
            return error
        }
        val answer = WmKeyboard.send(context, "SET_THEME", mapOf("theme" to themeName(next)))
        val said = WmKeyboard.describe(answer)
        return if (answer.ok) {
            settings.set(LauncherSettings.KeyboardWallpaperSlot, next)
            DebugLog.event("Keyboard", "wallpaper behind the keys ($why): \"${themeName(next)}\" on its new picture; WM Keyboard: $said")
            "Done: WM Keyboard shows \"${themeName(next)}\" on the new picture"
        } else {
            DebugLog.event("Keyboard", "wallpaper behind the keys ($why): picture written, but WM Keyboard did not switch to \"${themeName(next)}\": $said")
            "The picture is ready, but WM Keyboard did not switch to \"${themeName(next)}\": $said"
        }
    }

    /** Both pictures written now (before the themes are added, so each has its picture from the start). */
    suspend fun writeBoth(context: Context, settings: SettingsStore): String? {
        val picture = picture(context, settings) ?: return "no picture: the wallpaper is not the launcher's own"
        var error: String? = null
        for (slot in 0 until SLOTS) error = error ?: write(context, slot, picture)
        picture.recycle()
        return error
    }

    /**
     * Started once by the app: every wallpaper the launcher shows from now on is handed over while the switch is on
     * (after a moment of quiet, so a layout switch's quick changes cost one hand-over).
     */
    @OptIn(FlowPreview::class)
    fun start(context: Context, settings: SettingsStore, scope: CoroutineScope) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch(Dispatchers.Default) {
            WallpaperStore.get(app).image
                .filterNotNull()
                .distinctUntilChanged { a, b -> a === b }
                // The first one is the wallpaper already there when the launcher starts: nothing changed.
                .drop(1)
                .debounce(1_200)
                .collect {
                    if (!settings.get(LauncherSettings.KeyboardWallpaper)) return@collect
                    runCatching { follow(app, settings, "the wallpaper changed") }
                        .onFailure { DebugLog.error("Keyboard", "wallpaper behind the keys: $it") }
                }
        }
    }
}

/** The base theme: a file chosen in Settings, else the one Med made in WM Keyboard (bundled). */
internal fun baseKeyboardTheme(context: Context): ThemeDoc? {
    val own = File(context.filesDir, "keyboard-base.wmtheme.json")
    val text = if (own.isFile) runCatching { own.readText() }.getOrNull()
    else runCatching { context.assets.open("wmkeyboard/template.wmtheme.json").bufferedReader().use { it.readText() } }.getOrNull()
    return text?.let { ThemeDoc.parse(it) }
}
