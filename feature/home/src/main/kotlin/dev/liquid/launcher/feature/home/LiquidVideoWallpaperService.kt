package dev.liquid.launcher.feature.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.json.JSONObject
import java.io.File

/**
 * 0.16.0 (D207): the launcher's video wallpaper as the phone's live wallpaper ("system" mode), so the video also shows on
 * the lock screen, behind recents and behind apps as they close. It runs in its own process (`:wallpaper`), reads what
 * to play from `files/wallpaper/live.json` (written by [VideoWallpapers.writeLiveConfig]) and plays only when Android
 * shows it and the launcher is not in front (the launcher plays its own copy, which the glass can bend; two decoders at
 * once only for the moment of a hand-over). It rests with the screen off.
 */
class LiquidVideoWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = VideoEngine()

    private inner class VideoEngine : Engine() {
        private var player: ExoPlayer? = null
        private var surface: SurfaceHolder? = null
        private var visible = false
        private var launcherInFront = false
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_CONFIG -> load()
                    ACTION_HOME_SHOWN -> launcherInFront = true
                    ACTION_HOME_HIDDEN -> launcherInFront = false
                }
                update()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            launcherInFront = File(filesDir, HOME_IN_FRONT).exists()
            val filter = IntentFilter().apply {
                addAction(ACTION_CONFIG)
                addAction(ACTION_HOME_SHOWN)
                addAction(ACTION_HOME_HIDDEN)
            }
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surface = holder
            load()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            update()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            release()
            surface = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            runCatching { unregisterReceiver(receiver) }
            release()
            super.onDestroy()
        }

        /** Plays what `live.json` says, on the wallpaper's surface (the video fills it, cropped in the middle). */
        private fun load() {
            val holder = surface ?: return
            release()
            val config = try {
                JSONObject(VideoWallpapers.liveConfig(this@LiquidVideoWallpaperService).readText())
            } catch (e: Throwable) {
                Log.i(TAG, "nothing to play: $e")
                return
            }
            val file = File(config.optString("file"))
            if (!file.exists()) return
            val start = config.optLong("startMs", 0)
            val end = config.optLong("endMs", 0)
            val item = MediaItem.Builder()
                .setUri(android.net.Uri.fromFile(file))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(start)
                        .apply { if (end > start) setEndPositionMs(end) }
                        .build(),
                )
                .build()
            player = ExoPlayer.Builder(this@LiquidVideoWallpaperService).build().apply {
                volume = 0f
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                repeatMode = if (config.optString("loop") == "once") Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                playbackParameters = PlaybackParameters(config.optDouble("speed", 1.0).toFloat().coerceIn(0.25f, 2f))
                setVideoSurface(holder.surface)
                setMediaItem(item)
                prepare()
            }
            Log.i(TAG, "live wallpaper: ${file.name}")
            update()
        }

        private fun update() {
            val p = player ?: return
            p.playWhenReady = visible && !launcherInFront
        }

        private fun release() {
            player?.release()
            player = null
        }
    }

    companion object {
        private const val TAG = "LiquidLauncher"

        /** The launcher wrote a new `live.json`. */
        const val ACTION_CONFIG = "dev.liquid.launcher.action.LIVE_WALLPAPER_CONFIG"

        /** The launcher's home screen came to the front (it plays its own copy then), or went. */
        const val ACTION_HOME_SHOWN = "dev.liquid.launcher.action.HOME_SHOWN"
        const val ACTION_HOME_HIDDEN = "dev.liquid.launcher.action.HOME_HIDDEN"

        /** A file that exists while the launcher's home screen is in front (read by a service that starts late). */
        const val HOME_IN_FRONT = "wallpaper/home-in-front"
    }
}
