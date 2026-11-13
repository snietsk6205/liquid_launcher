package dev.liquid.launcher.core.common

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Colours of a wallpaper the launcher set itself, taken straight from the image at the moment
 * it was set. The system works these out again on its own, but MagicOS can report them late
 * or not at all, so themed icons, the accent and light / dark text read these first.
 *
 * [systemId] is the system's wallpaper id after the launcher set it; when the system reports a
 * different id, the wallpaper was changed outside the launcher and these colours no longer apply.
 */
data class WallpaperTone(
    val primary: Int,
    val secondary: Int?,
    val tertiary: Int?,
    /** Dark text reads better than light text on this wallpaper. */
    val light: Boolean,
    val systemId: Int,
)

object WallpaperTones {
    private const val PREFS = "wallpaper_tone"
    private val _tone = MutableStateFlow<WallpaperTone?>(null)
    @Volatile
    private var loaded = false

    /** The launcher-set wallpaper's colours, or null when the system wallpaper is not the launcher's. */
    val tone: StateFlow<WallpaperTone?> = _tone

    private val _outsideChanges = MutableStateFlow(0)

    /** Counts wallpapers set outside the launcher (another app, the system settings) since start. */
    val outsideChanges: StateFlow<Int> = _outsideChanges

    /** Reads the saved colours once per process. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains("primary")) return
        _tone.value = WallpaperTone(
            primary = prefs.getInt("primary", 0),
            secondary = prefs.getInt("secondary", 0).takeIf { prefs.contains("secondary") },
            tertiary = prefs.getInt("tertiary", 0).takeIf { prefs.contains("tertiary") },
            light = prefs.getBoolean("light", false),
            systemId = prefs.getInt("systemId", -1),
        )
    }

    fun set(context: Context, tone: WallpaperTone) {
        loaded = true
        _tone.value = tone
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            clear()
            putInt("primary", tone.primary)
            tone.secondary?.let { putInt("secondary", it) }
            tone.tertiary?.let { putInt("tertiary", it) }
            putBoolean("light", tone.light)
            putInt("systemId", tone.systemId)
        }.apply()
    }

    /** Colours of [bitmap], worked out the way the system does (on a small copy, a few ms). */
    fun fromBitmap(bitmap: Bitmap, systemId: Int = -1): WallpaperTone? = try {
        val colors = WallpaperColors.fromBitmap(bitmap)
        WallpaperTone(
            primary = colors.primaryColor.toArgb(),
            secondary = colors.secondaryColor?.toArgb(),
            tertiary = colors.tertiaryColor?.toArgb(),
            light = (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0,
            systemId = systemId,
        )
    } catch (e: Throwable) {
        DebugLog.error("Wallpaper", "colours from image failed: $e")
        null
    }

    /** The system's id for the home wallpaper, or -1 when the phone does not say. */
    fun systemId(context: Context): Int = try {
        WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
    } catch (e: Throwable) {
        -1
    }

    /**
     * The launcher's colours if the system wallpaper is still the one the launcher set, else
     * null (and they are dropped, so the system's colours take over).
     */
    fun current(context: Context): WallpaperTone? {
        load(context)
        val tone = _tone.value ?: return null
        if (tone.systemId <= 0) return tone
        val now = systemId(context)
        if (now <= 0 || now == tone.systemId) return tone
        DebugLog.event("Wallpaper", "wallpaper changed outside the launcher (id ${tone.systemId} -> $now), showing the system's")
        clear(context)
        _outsideChanges.value++
        return null
    }

    /** The system wallpaper is no longer the launcher's: fall back to the system's colours. */
    fun clear(context: Context) {
        if (_tone.value == null) return
        _tone.value = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
