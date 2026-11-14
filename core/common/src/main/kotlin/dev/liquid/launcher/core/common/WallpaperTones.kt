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
 * [systemId] is the system's wallpaper id these colours were last known to belong to: the id
 * after the launcher's own write, or the id that was current when the launcher showed a
 * picture it has not handed to the system yet. -1 = not known yet. A different id later on,
 * with no write of the launcher's own under way, means the wallpaper was changed elsewhere.
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

    // ---- the launcher's own writes to the system wallpaper ----

    private val lock = Any()
    private var writesInFlight = 0
    private var lastOwnWriteAt = -1_000_000L
    private val ownIds = ArrayDeque<Int>()

    /** A system id that differs from the tone's, and when it was first seen (uptime). */
    private var strangerId = -1
    private var strangerSince = 0L

    /** The launcher starts handing a wallpaper to the system: id changes are its own until [endOwnWrite] and a little after. */
    fun beginOwnWrite() = synchronized(lock) { writesInFlight++ }

    /**
     * The launcher's write finished and the system now reports [id]. With [applies] (the picture
     * written is still the one shown) the colours are tied to that id.
     */
    fun endOwnWrite(context: Context, id: Int, applies: Boolean) {
        synchronized(lock) {
            writesInFlight = (writesInFlight - 1).coerceAtLeast(0)
            lastOwnWriteAt = android.os.SystemClock.uptimeMillis()
            if (id > 0) {
                ownIds.addLast(id)
                while (ownIds.size > OWN_IDS) ownIds.removeFirst()
            }
            strangerId = -1
        }
        if (applies && id > 0) _tone.value?.let { if (it.systemId != id) set(context, it.copy(systemId = id)) }
    }

    /** The system wallpaper with [id] is the launcher's (found unchanged, nothing was written). */
    fun adopt(context: Context, id: Int, applies: Boolean) {
        if (id <= 0) return
        synchronized(lock) {
            ownIds.addLast(id)
            while (ownIds.size > OWN_IDS) ownIds.removeFirst()
            strangerId = -1
        }
        if (applies) _tone.value?.let { if (it.systemId != id) set(context, it.copy(systemId = id)) }
    }

    /**
     * True while the launcher is writing the system wallpaper and for a few seconds after. In
     * that time the system is busy, its wallpaper id moves, and MagicOS can bring the system
     * launcher to the front by itself.
     */
    val ownWriteActive: Boolean
        get() = synchronized(lock) {
            writesInFlight > 0 || android.os.SystemClock.uptimeMillis() - lastOwnWriteAt < OWN_WRITE_GRACE_MS
        }

    /**
     * The launcher shows a picture it has not handed to the system (yet): remember the id the
     * system has now, so a later change made elsewhere is still noticed.
     */
    fun rebase(context: Context) {
        if (ownWriteActive) return
        val now = systemId(context)
        val tone = _tone.value ?: return
        if (now > 0 && tone.systemId != now) set(context, tone.copy(systemId = now))
    }

    /**
     * The launcher's colours if the system wallpaper is still the launcher's, else null (and
     * they are dropped, so the system's colours take over).
     *
     * 0.4.1 to 0.5.0 treated any other id as "changed elsewhere". With two of the launcher's
     * own writes under way (each takes seconds on MagicOS and they could finish out of order)
     * the stored id was an older write's, so the launcher dropped its own wallpaper, and with
     * it the picture glass refracts. Now an id only counts as someone else's when no write of
     * the launcher's is running or just finished, it is not one the launcher wrote, and it has
     * been seen twice, [CONFIRM_MS] apart.
     */
    fun current(context: Context): WallpaperTone? {
        load(context)
        val tone = _tone.value ?: return null
        if (tone.systemId <= 0) return tone
        val now = systemId(context)
        if (now <= 0 || now == tone.systemId) {
            synchronized(lock) { strangerId = -1 }
            return tone
        }
        val uptime = android.os.SystemClock.uptimeMillis()
        val verdict = synchronized(lock) {
            when {
                writesInFlight > 0 || uptime - lastOwnWriteAt < OWN_WRITE_GRACE_MS -> 0
                now in ownIds -> 1
                strangerId != now -> {
                    strangerId = now
                    strangerSince = uptime
                    0
                }
                uptime - strangerSince < CONFIRM_MS -> 0
                else -> 2
            }
        }
        when (verdict) {
            0 -> return tone
            1 -> {
                // One of the launcher's own writes: the colours shown are the newest, keep them.
                set(context, tone.copy(systemId = now))
                return _tone.value
            }
        }
        DebugLog.event("Wallpaper", "wallpaper changed outside the launcher (id ${tone.systemId} -> $now), showing the system's")
        clear(context)
        _outsideChanges.value++
        return null
    }

    private const val OWN_IDS = 12
    private const val OWN_WRITE_GRACE_MS = 8000L
    private const val CONFIRM_MS = 1500L

    /** The system wallpaper is no longer the launcher's: fall back to the system's colours. */
    fun clear(context: Context) {
        if (_tone.value == null) return
        _tone.value = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
