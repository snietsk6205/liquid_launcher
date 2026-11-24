package dev.liquid.launcher.feature.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.AnimatedImageDrawable
import android.os.BatteryManager
import android.os.PowerManager
import android.view.TextureView
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.data.VideoRules
import dev.liquid.launcher.core.data.VideoSpec
import dev.liquid.launcher.core.data.WallpaperItem
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.derive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 0.16.0 (D207): a video (or animated) wallpaper playing on the home screen, over its poster (the still picture the rest
 * of the launcher uses, which shows until the first frame and whenever it rests). It plays only while the home screen is
 * in front, the screen is on, nothing covers it and the battery rules allow ([VideoRules.pauseReason]); with the screen
 * off it rests where the next unlock starts. [canvasWidth] is the wallpaper's width (wider than the screen with
 * parallax) and [offset] where the pages are (0..1), as for the picture. [onStill] gets a frame every few seconds while
 * it plays, for the glass when it does not bend the video ([VideoRules.glassBendsVideo]); [measure] says the glass bends
 * it now and its cost has not been measured yet.
 */
@Composable
internal fun VideoWallpaperLayer(
    item: WallpaperItem,
    controller: HomeController,
    canvasWidth: Float,
    canvasHeight: Float,
    offset: () -> Float,
    saturation: Float,
    stillEverySeconds: Int,
    onStill: ((ImageBitmap) -> Unit)?,
    measure: String?,
) {
    val spec = item.video ?: return
    val context = LocalContext.current
    val data = controller.data
    val file = remember(item.id, spec.file) { data.wallpapers.videoFileOf(item)?.takeIf { it.exists() } } ?: return
    val battery by data.settings.derive {
        VideoRules.Battery(get(LauncherSettings.VideoPauseSaver), get(LauncherSettings.VideoPauseBelow), get(LauncherSettings.VideoOnlyCharging))
    }

    // What the phone is doing, as far as playing goes.
    // In front: the launcher's window has the focus (as for the moving wallpaper): not behind an app, the shade or recents.
    val inFront = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    var screenOn by remember { mutableStateOf(context.getSystemService(PowerManager::class.java)?.isInteractive != false) }
    var saver by remember { mutableStateOf(context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true) }
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    var playedMs by remember { mutableLongStateOf(0L) }
    var playedThrough by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> screenOn = false
                    Intent.ACTION_SCREEN_ON -> {
                        screenOn = true
                        // A new unlock: "once per unlock" and "for a few seconds" start again.
                        playedMs = 0L
                        playedThrough = false
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> saver = c.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                        level = if (raw < 0) -1 else raw * 100 / scale
                        charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        // The battery broadcast is sticky: the state comes at once.
        context.registerReceiver(receiver, filter)?.let { receiver.onReceive(context, it) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val covered = controller.drawer.value > 0.98f || controller.searchAmount > 0.98f
    val reason = VideoRules.pauseReason(
        spec,
        VideoRules.Situation(inFront, screenOn, covered, saver, level, charging, playedMs, playedThrough),
        battery,
    )
    LaunchedEffect(reason) {
        DebugLog.event("Wallpaper", if (reason.isEmpty()) "video wallpaper: playing" else "video wallpaper: resting ($reason)")
    }
    val playing = reason.isEmpty()

    if (spec.kind == "animated") {
        AnimatedWallpaper(file, spec, playing, canvasWidth, canvasHeight, offset)
        return
    }

    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            setSeekParameters(SeekParameters.EXACT)
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    var videoSize by remember { mutableStateOf(VideoSize.UNKNOWN) }
    var firstFrame by remember { mutableStateOf(false) }
    // A cross-fade where the loop starts again: the last frame, laid over the first ones and fading away.
    var loopFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    val loopFade = remember { Animatable(0f) }
    var restarted by remember { mutableIntStateOf(0) }
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    // Boomerang steps back frame by frame; when the phone cannot keep up, it becomes a loop with a cross-fade.
    var boomerangFailed by remember(item.id) { mutableStateOf(false) }
    val loop = if (spec.loop == "boomerang" && boomerangFailed) "loop" else spec.loop
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }

            override fun onRenderedFirstFrame() {
                if (!firstFrame) DebugLog.event("Wallpaper", "video wallpaper: first frame drawn")
                firstFrame = true
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) restarted++
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) playedThrough = true
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                DebugLog.error("Wallpaper", "video wallpaper cannot play: ${error.errorCodeName} ${error.message}")
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val (clipStart, clipEnd) = remember(spec) { VideoRules.clip(spec) }
    LaunchedEffect(player, file, clipStart, clipEnd, loop) {
        val media = MediaItem.Builder()
            .setUri(android.net.Uri.fromFile(file))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clipStart)
                    .apply { if (clipEnd > clipStart) setEndPositionMs(clipEnd) }
                    .build(),
            )
            .build()
        player.setMediaItem(media)
        player.repeatMode = if (loop == "loop") Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.prepare()
    }
    LaunchedEffect(player, spec.speed) { player.playbackParameters = PlaybackParameters(spec.speed.coerceIn(0.25f, 2f)) }
    LaunchedEffect(playing, loop) {
        if (loop != "boomerang") player.playWhenReady = playing
        // The screen off: back to the start for "once per unlock", ready before the screen shows (D195: nothing late).
        if (!screenOn && spec.plays == "unlock") player.seekTo(0)
    }
    // While it plays: the time played (for "for a few seconds"), and the last frame just before the loop starts again.
    LaunchedEffect(playing, loop, spec.crossfadeMs) {
        if (!playing) return@LaunchedEffect
        var last = System.nanoTime()
        while (isActive) {
            withFrameNanos { }
            val now = System.nanoTime()
            playedMs += (now - last) / 1_000_000
            last = now
            if (loop == "loop" && spec.crossfadeMs > 0 && loopFrame == null) {
                val remaining = player.duration - player.currentPosition
                if (player.duration > 0 && remaining in 0..(90 * spec.speed).toLong()) {
                    textureView?.let { tv -> loopFrame = tv.getBitmap(tv.width / 2, tv.height / 2)?.asImageBitmap() }
                }
            }
        }
    }
    LaunchedEffect(restarted) {
        if (restarted == 0 || loopFrame == null) return@LaunchedEffect
        loopFade.snapTo(1f)
        loopFade.animateTo(0f, tween(spec.crossfadeMs))
        loopFrame = null
    }
    // Boomerang: forward, then back frame by frame (seeking, smooth after "Re-encode" makes every frame a key frame).
    LaunchedEffect(playing, loop) {
        if (loop != "boomerang" || !playing) return@LaunchedEffect
        val step = (1000f / 30f * spec.speed).toLong().coerceAtLeast(10)
        while (isActive) {
            player.playWhenReady = true
            while (isActive && player.playbackState != Player.STATE_ENDED) delay(40)
            player.playWhenReady = false
            var position = player.currentPosition
            var steps = 0
            val started = System.nanoTime()
            while (isActive && position > 0) {
                position = (position - step).coerceAtLeast(0)
                player.seekTo(position)
                // Wait for the frame (READY again) before the next step.
                var waited = 0
                while (isActive && player.playbackState != Player.STATE_READY && waited < 400) {
                    delay(8)
                    waited += 8
                }
                steps++
                val seconds = (System.nanoTime() - started) / 1e9f
                if (seconds > 1.5f && steps / seconds < 15f) {
                    DebugLog.event(
                        "Wallpaper",
                        "video wallpaper: Boomerang too slow on this phone (${(steps / seconds).roundToInt()} frames a second back" +
                            (if (!spec.allKeyFrames) "; Re-encode makes every frame a key frame" else "") + "): plays as a loop with a cross-fade",
                    )
                    boomerangFailed = true
                    return@LaunchedEffect
                }
            }
        }
    }
    // The glass's still frame, every few seconds while it plays (when the glass does not bend the video).
    LaunchedEffect(playing, onStill != null, stillEverySeconds) {
        val give = onStill ?: return@LaunchedEffect
        if (!playing) return@LaunchedEffect
        while (isActive) {
            delay(if (firstFrame) stillEverySeconds * 1000L else 400L)
            val tv = textureView ?: continue
            if (!firstFrame) continue
            tv.getBitmap((tv.width / 2).coerceAtLeast(1), (tv.height / 2).coerceAtLeast(1))?.let { give(it.asImageBitmap()) }
        }
    }
    // What the glass costs over it: frames measured for 6 s of playing, logged and kept (D207).
    if (measure != null) {
        LaunchedEffect(playing, measure) {
            if (!playing) return@LaunchedEffect
            delay(1500)
            val budgetNs = 1_000_000_000L / 90 * 3 / 2
            var frames = 0
            var late = 0
            var worst = 0L
            var last = withFrameNanos { it }
            val end = last + 6_000_000_000L
            while (isActive) {
                val now = withFrameNanos { it }
                val delta = now - last
                last = now
                frames++
                if (delta > budgetNs) late++
                worst = maxOf(worst, delta)
                if (now > end) break
            }
            if (frames == 0) return@LaunchedEffect
            val share = late.toFloat() / frames
            DebugLog.event(
                "Wallpaper",
                "Frames: video wallpaper, the glass $measure: ${(share * 100).roundToInt()} % of $frames frames late (worst ${worst / 1_000_000} ms)",
            )
            val before = data.settings.get(LauncherSettings.VideoMeasured)
            val keep = (before.split(';').filter { it.isNotBlank() && !it.startsWith("$measure=") } + "$measure=${"%.3f".format(java.util.Locale.ROOT, share)}").joinToString(";")
            data.settings.set(LauncherSettings.VideoMeasured, keep)
        }
    }

    // The video, as wide as the wallpaper (parallax), sliding with the pages; placed as its settings say.
    val alpha = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(firstFrame) { if (firstFrame) Animatable(0f).animateTo(1f, tween(MotionScale.ms(220))) { alpha.floatValue = value } }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.floatValue },
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).also { tv ->
                    player.setVideoTextureView(tv)
                    textureView = tv
                }
            },
            modifier = Modifier
                .layout { measurable, constraints ->
                    val w = canvasWidth.roundToInt().coerceAtLeast(1)
                    val h = canvasHeight.roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                }
                .graphicsLayer { translationX = -offset() * (canvasWidth - size.width).coerceAtLeast(0f) },
            update = { tv ->
                val size = videoSize
                if (size.width > 0 && size.height > 0 && tv.width > 0) tv.setTransform(videoMatrix(spec, size, tv.width.toFloat(), tv.height.toFloat()))
                tv.setLayerPaint(
                    if (kotlin.math.abs(saturation - 1f) < 0.01f) null
                    else Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) }) },
                )
            },
        )
        loopFrame?.let { frame ->
            Image(
                frame, contentDescription = null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = loopFade.value },
            )
        }
    }
}

/**
 * Where the decoded frames go in a TextureView of [w] × [h], which stretches them to its size by itself: undo that,
 * turn them upright, then scale and place them as [VideoRules.place] says.
 */
private fun videoMatrix(spec: VideoSpec, size: VideoSize, w: Float, h: Float): Matrix {
    val cw = size.width.toFloat()
    val ch = size.height.toFloat()
    val rotation = size.unappliedRotationDegrees
    val turned = rotation == 90 || rotation == 270
    val dw = if (turned) ch else cw
    val dh = if (turned) cw else ch
    val (scale, x, y) = VideoRules.place(spec, dw.toInt(), dh.toInt(), w, h)
    return Matrix().apply {
        setScale(cw / w, ch / h)
        postRotate(rotation.toFloat(), cw / 2f, ch / 2f)
        postTranslate((dw - cw) / 2f, (dh - ch) / 2f)
        postScale(scale, scale)
        postTranslate(x, y)
    }
}

/** A GIF or an animated WebP wallpaper (Android's animated image; it plays at its own pace, forward). */
@Composable
private fun AnimatedWallpaper(file: java.io.File, spec: VideoSpec, playing: Boolean, canvasWidth: Float, canvasHeight: Float, offset: () -> Float) {
    var drawable by remember(file) { mutableStateOf<AnimatedImageDrawable?>(null) }
    LaunchedEffect(file) {
        drawable = withContext(Dispatchers.IO) {
            try {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) as? AnimatedImageDrawable
            } catch (e: Throwable) {
                DebugLog.error("Wallpaper", "animated wallpaper cannot play: $e")
                null
            }
        }
    }
    val d = drawable ?: return
    val playingNow by rememberUpdatedState(playing)
    LaunchedEffect(d, spec.loop) { d.repeatCount = if (spec.loop == "once") 0 else AnimatedImageDrawable.REPEAT_INFINITE }
    LaunchedEffect(d, playingNow) { if (playingNow) d.start() else d.stop() }
    AndroidView(
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.MATRIX } },
        modifier = Modifier
            .layout { measurable, constraints ->
                val w = canvasWidth.roundToInt().coerceAtLeast(1)
                val h = canvasHeight.roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
            }
            .graphicsLayer { translationX = -offset() * (canvasWidth - size.width).coerceAtLeast(0f) },
        update = { view ->
            if (view.drawable !== d) view.setImageDrawable(d)
            val (scale, x, y) = VideoRules.place(spec, d.intrinsicWidth, d.intrinsicHeight, canvasWidth, canvasHeight)
            view.imageMatrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(x, y)
            }
        },
    )
}
