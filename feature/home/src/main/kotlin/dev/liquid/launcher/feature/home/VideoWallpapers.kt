package dev.liquid.launcher.feature.home

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.data.LauncherData
import dev.liquid.launcher.core.data.VideoSpec
import dev.liquid.launcher.core.data.WallpaperItem
import dev.liquid.launcher.core.settings.LauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * 0.16.0 (D207): video wallpapers. A video (or a GIF, or an animated WebP) is kept in the library with a poster: its
 * still frame, which every other part of the launcher uses as the picture (thumbnails, colours, the phone's own
 * wallpaper, layouts, the glass when it does not bend the video). The home screen plays the moving file over it
 * ([VideoWallpaperLayer]); "system" mode also makes it the phone's live wallpaper ([LiquidVideoWallpaperService]).
 */
object VideoWallpapers {
    /** A video by its type. */
    fun isVideo(context: Context, uri: Uri): Boolean = context.contentResolver.getType(uri)?.startsWith("video/") == true

    /** A GIF or WebP that moves (Android's animated image), checked off the main thread. */
    suspend fun isAnimated(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val type = context.contentResolver.getType(uri) ?: return@withContext false
        if (type != "image/gif" && type != "image/webp") return@withContext false
        try {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.contentResolver, uri)) is AnimatedImageDrawable
        } catch (e: Throwable) {
            false
        }
    }

    /** Imports a video or an animated picture into the library (copied in, with its poster); null when it cannot be read. */
    suspend fun import(context: Context, data: LauncherData, uri: Uri, folder: String? = null): WallpaperItem? = withContext(Dispatchers.IO) {
        val animated = !isVideo(context, uri)
        val type = context.contentResolver.getType(uri) ?: ""
        val extension = when {
            type == "image/gif" -> "gif"
            type == "image/webp" -> "webp"
            type.contains("webm") -> "webm"
            else -> "mp4"
        }
        val temp = File(context.cacheDir, "video-import.$extension")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } } ?: return@withContext null
        } catch (e: Throwable) {
            DebugLog.error("Wallpaper", "video import: could not read it: $e")
            return@withContext null
        }
        if (animated) {
            val poster = try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(temp)) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
            } catch (e: Throwable) {
                DebugLog.error("Wallpaper", "animated picture: no first frame: $e")
                temp.delete()
                return@withContext null
            }
            return@withContext data.wallpapers.addVideo(temp, extension, poster, VideoSpec(file = "", kind = "animated", width = poster.width, height = poster.height), folder)
        }
        val probe = probe(temp) ?: run {
            temp.delete()
            return@withContext null
        }
        val poster = frameAt(temp, 0L) ?: run {
            temp.delete()
            return@withContext null
        }
        data.wallpapers.addVideo(temp, extension, poster, VideoSpec(file = "", durationMs = probe.first, width = probe.second, height = probe.third), folder)
    }

    /** Its duration (ms) and upright size, or null when Android cannot read it as a video. */
    private fun probe(file: File): Triple<Long, Int, Int>? = try {
        MediaMetadataRetriever().use { m ->
            m.setDataSource(file.path)
            val duration = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) w = h.also { h = w }
            if (w <= 0 || h <= 0) null else Triple(duration, w, h)
        }
    } catch (e: Throwable) {
        DebugLog.error("Wallpaper", "video import: not readable as a video: $e")
        null
    }

    /** The frame at [ms] (at most about the screen's size), for the poster. */
    fun frameAt(file: File, ms: Long): Bitmap? = try {
        MediaMetadataRetriever().use { m ->
            m.setDataSource(file.path)
            val w = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val scale = if (w > 0 && h > 0) minOf(1f, 1920f / maxOf(w, h)) else 1f
            if (w > 0 && h > 0 && scale < 1f) {
                m.getScaledFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST, (w * scale).toInt(), (h * scale).toInt())
            } else {
                m.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
            }
        }
    } catch (e: Throwable) {
        DebugLog.error("Wallpaper", "video: no frame at $ms ms: $e")
        null
    }

    /** A new still frame, taken [ms] into the video: the poster (and so the colours, the glass's still, the phone's wallpaper). */
    suspend fun setPoster(data: LauncherData, item: WallpaperItem, ms: Long): Boolean = withContext(Dispatchers.IO) {
        val spec = item.video ?: return@withContext false
        val file = data.wallpapers.videoFileOf(item) ?: return@withContext false
        val frame = (if (spec.kind == "animated") null else frameAt(file, ms)) ?: return@withContext false
        data.wallpapers.setPoster(item.id, frame)
        data.wallpapers.setVideo(item.id, spec.copy(posterMs = ms))
        true
    }

    /**
     * Re-encodes a video for this phone (D207): H.264, at most the size and frame rate of the settings, without sound;
     * with [blur] > 0 the blur is in the frames (made once, never live: the smoothness rule); for Boomerang every frame
     * becomes a key frame (it steps back frame by frame). The file is replaced when it worked. Returns null or what
     * went wrong.
     */
    suspend fun reencode(context: Context, data: LauncherData, item: WallpaperItem, blur: Float = 0f, allKeyFrames: Boolean = false): String? {
        val spec = item.video ?: return "not a video"
        if (spec.kind == "animated") return "an animated picture plays as it is"
        val source = data.wallpapers.videoFileOf(item)?.takeIf { it.exists() } ?: return "its file is missing"
        val size = data.settings.get(LauncherSettings.VideoMaxHeight).toIntOrNull() ?: 1080
        val fps = data.settings.get(LauncherSettings.VideoMaxFps).toFloatOrNull() ?: 30f
        val out = File(context.cacheDir, "reencode-${item.id}.mp4")
        out.delete()
        val effects = buildList<Effect> {
            add(Presentation.createForShortSide(size))
            add(FrameDropEffect.createDefaultFrameDropEffect(fps))
            if (blur > 0f) add(GaussianBlur(blur))
        }
        val encoder = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setiFrameIntervalSeconds(if (allKeyFrames) 0f else 1f).build())
            .build()
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source)))
            .setRemoveAudio(true)
            .setEffects(Effects(emptyList(), effects))
            .build()
        val started = android.os.SystemClock.uptimeMillis()
        DebugLog.event("Wallpaper", "video re-encode started: ${size}p, ${fps.toInt()} fps" + (if (blur > 0f) ", blur $blur" else "") + if (allKeyFrames) ", every frame a key frame" else "")
        val error = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<String?> { cont ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setEncoderFactory(encoder)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(null)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (cont.isActive) cont.resume(exportException.message ?: exportException.toString())
                        }
                    })
                    .build()
                transformer.start(edited, out.path)
                cont.invokeOnCancellation { transformer.cancel() }
            }
        }
        if (error != null || !out.exists()) {
            DebugLog.error("Wallpaper", "video re-encode failed: $error")
            out.delete()
            return error ?: "no file came out"
        }
        val took = android.os.SystemClock.uptimeMillis() - started
        return withContext(Dispatchers.IO) {
            val before = source.length()
            val probe = probe(out)
            if (!out.renameTo(source)) {
                out.copyTo(source, overwrite = true)
                out.delete()
            }
            val label = "H.264 ${size}p ${fps.toInt()} fps" + (if (blur > 0f) ", blurred" else "") + if (allKeyFrames) ", every frame a key frame" else ""
            data.wallpapers.setVideo(
                item.id,
                spec.copy(
                    encoded = label, allKeyFrames = allKeyFrames,
                    durationMs = probe?.first ?: spec.durationMs, width = probe?.second ?: spec.width, height = probe?.third ?: spec.height,
                ),
            )
            DebugLog.event("Wallpaper", "video re-encoded in $took ms: ${before / 1024} KB -> ${source.length() / 1024} KB ($label)")
            null
        }
    }

    // ---- the phone's live wallpaper ("system" mode) ----

    /** The phone's own wallpaper is the launcher's live video wallpaper. */
    fun isOurLiveWallpaper(context: Context): Boolean = try {
        WallpaperManager.getInstance(context).wallpaperInfo?.packageName == context.packageName
    } catch (e: Throwable) {
        false
    }

    /** Android's screen for choosing a live wallpaper, on the launcher's (the user confirms there; no app may set one silently). */
    fun liveWallpaperIntent(context: Context): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, LiquidVideoWallpaperService::class.java))

    /** Where the live wallpaper service (its own process) reads which video to play and how. */
    fun liveConfig(context: Context): File = File(context.filesDir, "wallpaper/live.json")

    /** Writes what the live wallpaper plays: [item]'s video with its settings (null: nothing, the service shows black). */
    fun writeLiveConfig(context: Context, data: LauncherData, item: WallpaperItem?) {
        val file = liveConfig(context)
        val spec = item?.video
        val video = item?.let { data.wallpapers.videoFileOf(it) }
        if (spec == null || video == null) {
            file.delete()
            return
        }
        val json = org.json.JSONObject()
            .put("file", video.path).put("kind", spec.kind).put("startMs", spec.startMs).put("endMs", spec.endMs)
            .put("loop", spec.loop).put("speed", spec.speed.toDouble()).put("fit", spec.fit)
            .put("panX", spec.panX.toDouble()).put("panY", spec.panY.toDouble())
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(json.toString())
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        context.sendBroadcast(Intent(LiquidVideoWallpaperService.ACTION_CONFIG).setPackage(context.packageName))
    }
}
