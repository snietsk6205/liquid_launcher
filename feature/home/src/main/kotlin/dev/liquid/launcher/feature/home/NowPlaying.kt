package dev.liquid.launcher.feature.home

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import dev.liquid.launcher.core.common.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 0.13.0 (D178): what is playing, for the pill's live item. Android shows the media sessions of other apps to a
 * notification listener only, so this goes through the launcher's own (the one "Quiet during blocks" uses): without
 * notification access nothing is read and the live item simply never shows music. Started while a live item is on
 * screen, stopped when none is.
 */
internal object NowPlaying {
    /** A track playing (or paused at [changedAt], uptime ms) in [pkg]. [art] is small (72 px). */
    data class Track(
        val title: String,
        val artist: String,
        val playing: Boolean,
        val pkg: String,
        val art: ImageBitmap?,
        val changedAt: Long,
    )

    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track

    private val handler = Handler(Looper.getMainLooper())
    private var users = 0
    private var manager: MediaSessionManager? = null
    private val sessions = HashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var current: MediaController? = null
    private var artFor = ""
    private var art: ImageBitmap? = null

    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { list -> onSessions(list.orEmpty()) }

    fun hasAccess(context: Context): Boolean =
        android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.contains(context.packageName) == true

    private fun component(context: Context) = ComponentName(context.packageName, "dev.liquid.launcher.QuietService")

    /** Starts listening (counted: the last [release] stops). False when it cannot (no notification access). */
    fun acquire(context: Context): Boolean {
        users++
        if (manager != null) return true
        if (!hasAccess(context)) return false
        val m = context.getSystemService(MediaSessionManager::class.java) ?: return false
        return try {
            m.addOnActiveSessionsChangedListener(listener, component(context), handler)
            manager = m
            onSessions(m.getActiveSessions(component(context)))
            true
        } catch (e: SecurityException) {
            DebugLog.event("Live", "media sessions not allowed: ${e.message}")
            false
        } catch (e: Exception) {
            DebugLog.error("Live", "media sessions failed: $e")
            false
        }
    }

    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        manager?.let { runCatching { it.removeOnActiveSessionsChangedListener(listener) } }
        manager = null
        sessions.values.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        sessions.clear()
        current = null
        _track.value = null
    }

    private fun onSessions(list: List<MediaController>) {
        val tokens = list.map { it.sessionToken }.toSet()
        sessions.keys.filter { it !in tokens }.forEach { token ->
            sessions.remove(token)?.let { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        }
        for (c in list) {
            if (c.sessionToken in sessions) continue
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = update()
                override fun onMetadataChanged(metadata: MediaMetadata?) = update()
                override fun onSessionDestroyed() = update()
            }
            runCatching { c.registerCallback(callback, handler) }
            sessions[c.sessionToken] = c to callback
        }
        update()
    }

    private fun state(c: MediaController): Int = runCatching { c.playbackState?.state ?: PlaybackState.STATE_NONE }.getOrDefault(PlaybackState.STATE_NONE)

    private fun update() {
        val controllers = sessions.values.map { it.first }
        val before = _track.value
        val playing = controllers.firstOrNull { state(it) == PlaybackState.STATE_PLAYING || state(it) == PlaybackState.STATE_BUFFERING }
        // Paused: the one that was playing stays (for a while, see the live item) until another plays.
        val chosen = playing ?: controllers.firstOrNull { it.packageName == before?.pkg && state(it) == PlaybackState.STATE_PAUSED }
        current = chosen
        if (chosen == null) {
            _track.value = null
            return
        }
        val metadata = runCatching { chosen.metadata }.getOrNull()
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.trim()
            ?: ""
        if (title.isEmpty()) {
            _track.value = null
            return
        }
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.trim()
            ?: ""
        val id = chosen.packageName + "\n" + title + "\n" + artist
        if (id != artFor) {
            artFor = id
            art = runCatching {
                val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                bitmap?.let { Bitmap.createScaledBitmap(it, 72, 72, true).asImageBitmap() }
            }.getOrNull()
        }
        val isPlaying = playing != null
        val changed = before == null || before.playing != isPlaying || before.pkg != chosen.packageName
        _track.value = Track(title, artist, isPlaying, chosen.packageName, art, if (changed) SystemClock.uptimeMillis() else before!!.changedAt)
    }

    /** Play or pause what the live item shows. */
    fun toggle() {
        val c = current ?: return
        runCatching {
            if (state(c) == PlaybackState.STATE_PLAYING || state(c) == PlaybackState.STATE_BUFFERING) c.transportControls.pause()
            else c.transportControls.play()
        }
    }

    /** Opens the app playing (its own screen for the session when it gives one). */
    fun open(context: Context, launch: (String) -> Unit) {
        val c = current ?: return
        val activity = runCatching { c.sessionActivity }.getOrNull()
        if (activity != null) {
            val options = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else null
            val sent = runCatching { activity.send(context, 0, null, null, null, null, options) }.isSuccess
            if (sent) return
        }
        launch(c.packageName)
    }
}

/** What is playing while this is composed (null without notification access, or when nothing plays). */
@Composable
internal fun rememberNowPlaying(enabled: Boolean): State<NowPlaying.Track?> {
    val context = LocalContext.current
    // Notification access can be given while the launcher runs: looked at again each minute until it is.
    val tick = rememberMinuteTick()
    val access = remember(enabled, tick) { enabled && NowPlaying.hasAccess(context) }
    DisposableEffect(access) {
        if (access) NowPlaying.acquire(context.applicationContext)
        onDispose { if (access) NowPlaying.release() }
    }
    return NowPlaying.track.collectAsState()
}
