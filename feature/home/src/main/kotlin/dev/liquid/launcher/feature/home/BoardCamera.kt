package dev.liquid.launcher.feature.home

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowInsets as AndroidWindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.common.NoticeKind
import dev.liquid.launcher.core.common.Notices
import dev.liquid.launcher.core.data.BoardPhotos
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 0.15.3 (D196, Med: "I take a photo, it saves to its right place, then I'm back on the home screen, so I can't take
 * multiple pictures at once"; he chose the launcher's own camera): board photos taken inside the launcher, as many as
 * wanted in a row. One tap on Board (Today's chip, the pill's or the home menu's) opens it on the class of the moment;
 * each photo is saved at once in Pictures/ESSAI/<class> (CameraX writes it into the gallery itself), and flies from the
 * screen into the corner while it saves, so the next can be taken straight away; Done (or Back) closes it once every
 * photo is saved, and the home screen says how many were. The volume keys take photos too; a tap focuses there; two
 * fingers zoom, and the presets under the shutter jump. The screen stays portrait and the photos come out upright
 * however the phone is held (boards are wide: most are taken sideways); the controls turn to face the person.
 *
 * Plain photos, sharp and quick (CameraX, latency first, JPEG at 95): what the phone's camera app adds (night, HDR,
 * beauty) is not there. A camera app is still a choice in Settings › Study companion › More.
 */

/** What the camera screen shows; the activity's keys (volume, Back) reach it through here. */
internal class BoardCameraState(val module: String) {
    val folder: String = BoardPhotos.folderName(module)
    val openedAt: Long = SystemClock.uptimeMillis()

    /** Whether the launcher may use the camera; null while Android asks. */
    var allowed by mutableStateOf<Boolean?>(null)

    /** False once Android no longer asks (refused for good): the launcher's settings page is the way then. */
    var askable by mutableStateOf(true)

    /** Why there is no camera (no camera on the phone, or it would not start); null while all is well. */
    var failure by mutableStateOf<String?>(null)

    /** Photos saved, and photos taken that are still being saved. */
    var saved by mutableIntStateOf(0)
    var saving by mutableIntStateOf(0)

    /** The last photo, as the screen showed it (small), and where it was saved. */
    var last by mutableStateOf<ImageBitmap?>(null)
    var lastUri by mutableStateOf<Uri?>(null)

    /** A short line under the class's name (a photo not saved…), for a moment. */
    var note by mutableStateOf<String?>(null)
    var noteRun by mutableIntStateOf(0)

    /** Done or Back: the screen closes once every photo taken is saved. */
    var leaving by mutableStateOf(false)

    /** Takes a photo; set while the camera is ready (the volume keys use it too). */
    var shoot: (() -> Unit)? = null

    fun say(text: String) {
        note = text
        noteRun++
    }

    fun fail(text: String, error: Throwable?) {
        failure = text
        DebugLog.error("Study", "board camera: $text" + if (error != null) " ($error)" else "")
    }
}

/** The board camera: portrait, over everything, the screen kept on while it is open. Opened by [BoardPhotos.cameraIntent]. */
class BoardCameraActivity : ComponentActivity() {
    private val state by lazy {
        BoardCameraState(intent.getStringExtra(BoardPhotos.EXTRA_MODULE)?.takeIf { it.isNotBlank() } ?: "Notes")
    }

    private val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> answered(granted) }

    private fun answered(granted: Boolean) {
        state.allowed = granted
        state.askable = granted || shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)
        DebugLog.event("Study", "board camera: the camera was " + if (granted) "allowed" else "refused")
    }

    private fun askAgain() {
        if (state.askable) {
            ask.launch(android.Manifest.permission.CAMERA)
        } else {
            // Refused for good: only the launcher's own settings page can allow it now.
            runCatching {
                startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", packageName, null)),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dark()
        DebugLog.event("Study", "board camera: opened for \"${state.folder}\"")
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (state.leaving) finish() else state.leaving = true
                }
            },
        )
        if (BoardPhotos.cameraAllowed(this)) state.allowed = true else ask.launch(android.Manifest.permission.CAMERA)
        setContent { BoardCamera(state, owner = this, onAsk = { askAgain() }, onDone = { finish() }) }
    }

    override fun onResume() {
        super.onResume()
        // Back from the launcher's settings page with the camera allowed there.
        if (state.allowed == false && BoardPhotos.cameraAllowed(this)) state.allowed = true
    }

    /** Black, edge to edge under the camera's cut-out, the status bar away (a swipe brings it), light bar icons. */
    private fun dark() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setDecorFitsSystemWindows(false)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.insetsController?.apply {
            setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
            hide(AndroidWindowInsets.Type.statusBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // The volume keys are a shutter while the camera is ready (as in camera apps); the volume stays as it is.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val shoot = state.shoot ?: return super.onKeyDown(keyCode, event)
            if (event.repeatCount == 0) shoot()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) && state.shoot != null) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        if (isFinishing && state.saved > 0) {
            val n = state.saved
            val photos = if (n == 1) "1 photo" else "$n photos"
            DebugLog.event("Study", "board camera: closed, $photos in Pictures/ESSAI/${state.folder}")
            Notices.post(NoticeKind.Study, "Board photos: ${state.folder}", "$photos in Pictures/ESSAI/${state.folder}")
        }
        super.onDestroy()
    }
}

@Composable
internal fun BoardCamera(state: BoardCameraState, owner: LifecycleOwner, onAsk: () -> Unit, onDone: () -> Unit) {
    // Leaving waits for the photos still being saved (closing the camera would drop them), six seconds at most.
    LaunchedEffect(state.leaving) {
        if (!state.leaving) return@LaunchedEffect
        if (state.saving > 0) {
            state.say("Saving…")
            withTimeoutOrNull(6_000) { snapshotFlow { state.saving }.first { it == 0 } }
        }
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (state.allowed) {
            true -> if (state.failure == null) CameraLive(state, owner)
            false -> CameraRefused(state, onAsk)
            null -> Unit
        }
        state.failure?.let { CameraMissing(it) { state.leaving = true } }
    }
}

/** One photo flying from the screen into the corner while it is saved. */
private class Flight(val image: ImageBitmap, val id: Int)

@Composable
private fun CameraLive(state: BoardCameraState, owner: LifecycleOwner) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val previewView = remember {
        PreviewView(context).apply {
            // A TextureView: the preview fades in and its frame can be taken for the flying photo.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    val shown = remember { Animatable(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    val blink = remember { Animatable(0f) }
    var focus by remember { mutableStateOf<Offset?>(null) }
    var focusRun by remember { mutableIntStateOf(0) }
    var turn by remember { mutableFloatStateOf(0f) }
    var flights by remember { mutableStateOf(listOf<Flight>()) }
    var nextFlight by remember { mutableIntStateOf(0) }
    var landed by remember { mutableIntStateOf(0) }
    var thumb by remember { mutableStateOf<Rect?>(null) }

    // The back camera (the front one on a phone without), its preview and its photos, for as long as this screen shows.
    LaunchedEffect(Unit) {
        val provider = try {
            ProcessCameraProvider.awaitInstance(context)
        } catch (e: Exception) {
            state.fail("The camera could not start", e)
            return@LaunchedEffect
        }
        val selector = listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)
            .firstOrNull { runCatching { provider.hasCamera(it) }.getOrDefault(false) }
        if (selector == null) {
            state.fail("No camera on this phone", null)
            return@LaunchedEffect
        }
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val photos = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(95)
            .build()
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(owner, selector, preview, photos)
            capture = photos
            DebugLog.event("Study", "board camera: ready ${SystemClock.uptimeMillis() - state.openedAt} ms after opening")
        } catch (e: Exception) {
            state.fail("The camera could not start", e)
        }
    }
    // Zoom as the camera has it (its range, and where a pinch or a preset left it).
    DisposableEffect(camera) {
        val cam = camera ?: return@DisposableEffect onDispose { }
        val observer = Observer<ZoomState> { z ->
            zoom = z.zoomRatio
            minZoom = z.minZoomRatio
            maxZoom = z.maxZoomRatio
        }
        cam.cameraInfo.zoomState.observe(owner, observer)
        onDispose { cam.cameraInfo.zoomState.removeObserver(observer) }
    }
    // The preview fades in with its first frames (the black before is the camera starting).
    DisposableEffect(previewView) {
        val observer = Observer<PreviewView.StreamState> { s ->
            if (s == PreviewView.StreamState.STREAMING) scope.launch { shown.animateTo(1f, tween(MotionScale.ms(260))) }
        }
        previewView.previewStreamState.observe(owner, observer)
        onDispose { previewView.previewStreamState.removeObserver(observer) }
    }
    // Upright photos whichever way the phone is held; the controls turn the nearest way round to face the person.
    DisposableEffect(capture) {
        val photos = capture ?: return@DisposableEffect onDispose { }
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return
                val (rotation, angle) = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270 to -90f
                    in 135 until 225 -> Surface.ROTATION_180 to 180f
                    in 225 until 315 -> Surface.ROTATION_90 to 90f
                    else -> Surface.ROTATION_0 to 0f
                }
                if (photos.targetRotation != rotation) photos.targetRotation = rotation
                val d = ((angle - turn) % 360f + 540f) % 360f - 180f
                if (abs(d) > 1f) turn += d
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
    val turned by animateFloatAsState(turn, spring(dampingRatio = 0.8f, stiffness = MotionScale.stiffness(300f)), label = "turn")

    fun shoot() {
        val photos = capture ?: return
        if (state.leaving) return
        val now = System.currentTimeMillis()
        val pressed = SystemClock.uptimeMillis()
        // What the screen showed, small: it flies to the corner at once while the photo itself is saved.
        val seen = runCatching { previewView.bitmap }.getOrNull()?.let { full ->
            val small = Bitmap.createScaledBitmap(full, (full.width / 3).coerceAtLeast(1), (full.height / 3).coerceAtLeast(1), true)
            if (small !== full) full.recycle()
            small.asImageBitmap()
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            BoardPhotos.photoValues(state.module, now),
        ).build()
        state.saving++
        try {
            photos.takePicture(
                options,
                context.mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        state.saving--
                        state.saved++
                        outputFileResults.savedUri?.let { state.lastUri = it }
                        DebugLog.event(
                            "Study",
                            "board camera: photo ${state.saved} saved in Pictures/ESSAI/${state.folder}, " +
                                "${SystemClock.uptimeMillis() - pressed} ms after the shutter",
                        )
                    }

                    override fun onError(exception: ImageCaptureException) {
                        state.saving--
                        state.say("A photo was not saved")
                        DebugLog.error("Study", "board camera: a photo was not saved (${exception.imageCaptureError}: ${exception.message})")
                    }
                },
            )
        } catch (e: Exception) {
            state.saving--
            state.say("The photo could not be taken")
            DebugLog.error("Study", "board camera: the photo could not be taken: $e")
            return
        }
        Haptics.tick(view)
        scope.launch {
            blink.snapTo(0.6f)
            blink.animateTo(0f, tween(MotionScale.ms(200)))
        }
        if (seen != null) flights = flights + Flight(seen, nextFlight++)
    }
    SideEffect { state.shoot = if (capture != null && !state.leaving) ({ shoot() }) else null }
    DisposableEffect(Unit) { onDispose { state.shoot = null } }

    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown.value })
        // A tap focuses there; two fingers zoom.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(camera) {
                    detectTapGestures(
                        onTap = { at ->
                            val cam = camera ?: return@detectTapGestures
                            val point = previewView.meteringPointFactory.createPoint(at.x, at.y)
                            val action = FocusMeteringAction.Builder(point).setAutoCancelDuration(4, TimeUnit.SECONDS).build()
                            runCatching { cam.cameraControl.startFocusAndMetering(action) }
                            focus = at
                            focusRun++
                        },
                    )
                }
                .pointerInput(camera) {
                    detectTransformGestures { _, _, gestureZoom, _ ->
                        val cam = camera ?: return@detectTransformGestures
                        if (gestureZoom == 1f || maxZoom <= minZoom) return@detectTransformGestures
                        val z = (zoom * gestureZoom).coerceIn(minZoom, maxZoom)
                        zoom = z
                        runCatching { cam.cameraControl.setZoomRatio(z) }
                    }
                },
        )
        focus?.let { at ->
            key(focusRun) {
                val ring = remember { Animatable(0f) }
                LaunchedEffect(Unit) { ring.animateTo(1f, tween(MotionScale.ms(1100), easing = LinearEasing)) }
                val half = with(density) { 34.dp.toPx() }
                Box(
                    Modifier
                        .offset { IntOffset((at.x - half).roundToInt(), (at.y - half).roundToInt()) }
                        .size(68.dp)
                        .graphicsLayer {
                            val p = ring.value
                            val settle = (p / 0.2f).coerceAtMost(1f)
                            val s = 1.35f - 0.35f * (1f - (1f - settle) * (1f - settle))
                            scaleX = s
                            scaleY = s
                            alpha = if (p < 0.7f) 1f else (1f - (p - 0.7f) / 0.3f)
                        }
                        .border(1.5.dp, Color.White, CircleShape),
                )
            }
        }
        // The shutter's blink.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = blink.value }.background(Color.Black))

        // The class, and a line for a moment when something needs saying.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(top = 14.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth()) {
                RoundButton(LauncherIcons.Close, "Close", turned, Modifier.align(Alignment.CenterStart)) { state.leaving = true }
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.38f))
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.folder, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("Pictures/ESSAI", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp, maxLines = 1)
                }
            }
            val note = state.note
            LaunchedEffect(state.noteRun) {
                if (state.note == null) return@LaunchedEffect
                delay(2_600)
                state.note = null
            }
            AnimatedVisibility(visible = note != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    note ?: "",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // Zoom presets, then the last photo, the shutter and Done.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (maxZoom > minZoom + 0.05f) {
                ZoomPresets(zoom, minZoom, maxZoom, turned) { z -> camera?.let { cam -> runCatching { cam.cameraControl.setZoomRatio(z) } } }
                Spacer(Modifier.height(22.dp))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 34.dp).height(84.dp)) {
                Thumbnail(
                    image = state.last,
                    count = state.saved,
                    saving = state.saving > 0,
                    turn = turned,
                    landed = landed,
                    modifier = Modifier.align(Alignment.CenterStart),
                    onBounds = { thumb = it },
                ) {
                    val uri = state.lastUri ?: return@Thumbnail
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        )
                    }.onFailure { state.say("No app to show the photo") }
                }
                Shutter(enabled = capture != null && !state.leaving, modifier = Modifier.align(Alignment.Center)) { shoot() }
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.saved + state.saving > 0,
                    enter = fadeIn() + scaleIn(initialScale = 0.7f),
                    exit = fadeOut() + scaleOut(targetScale = 0.7f),
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(onClickLabel = "Done") { state.leaving = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        LauncherIcon(LauncherIcons.Check, "Done", Modifier.graphicsLayer { rotationZ = turned }, tint = Color.Black, size = 26.dp)
                    }
                }
            }
        }

        for (flight in flights) {
            key(flight.id) {
                FlyingPhoto(flight, thumb) {
                    state.last = flight.image
                    landed++
                    flights = flights - flight
                }
            }
        }
    }
}

/** A photo flying from the whole screen into the corner, its corners rounding as it goes. */
@Composable
private fun FlyingPhoto(flight: Flight, target: Rect?, onLanded: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(MotionScale.ms(460), easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
        onLanded()
    }
    val radius = with(LocalDensity.current) { 14.dp.toPx() }
    Image(
        bitmap = flight.image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .layout { measurable, constraints ->
                val p = t.value
                val w0 = constraints.maxWidth.toFloat()
                val h0 = constraints.maxHeight.toFloat()
                val end = target ?: Rect(w0 / 2f, h0, w0 / 2f, h0)
                val w = lerp(w0, end.width, p).roundToInt().coerceAtLeast(1)
                val h = lerp(h0, end.height, p).roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(lerp(0f, end.left, p).roundToInt(), lerp(0f, end.top, p).roundToInt())
                }
            }
            .graphicsLayer {
                shape = RoundedCornerShape(radius * t.value)
                clip = true
            },
    )
}

@Composable
private fun Shutter(enabled: Boolean, modifier: Modifier, onShoot: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val inner by animateFloatAsState(if (pressed) 0.82f else 1f, spring(dampingRatio = 0.5f, stiffness = 900f), label = "shutter")
    Box(
        modifier
            .size(80.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .border(4.dp, Color.White, CircleShape)
            .padding(9.dp)
            .clickable(interaction, indication = null, enabled = enabled, onClickLabel = "Take a photo") { onShoot() },
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = inner; scaleY = inner }.background(Color.White, CircleShape))
    }
}

/** The last photo (its look on the screen), how many are saved, and a ring turning while some are still being saved. */
@Composable
private fun Thumbnail(
    image: ImageBitmap?,
    count: Int,
    saving: Boolean,
    turn: Float,
    landed: Int,
    modifier: Modifier,
    onBounds: (Rect) -> Unit,
    onOpen: () -> Unit,
) {
    // A small bounce as each photo lands.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(landed) {
        if (landed == 0) return@LaunchedEffect
        pop.snapTo(1.14f)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
    }
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.size(56.dp).onGloballyPositioned { onBounds(it.boundsInRoot()) }.graphicsLayer { rotationZ = turn; scaleX = pop.value; scaleY = pop.value }) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color.White.copy(alpha = 0.1f))
                .border(1.5.dp, Color.White.copy(alpha = if (image != null) 0.9f else 0.45f), shape)
                .clickable(enabled = image != null, onClickLabel = "Show the last photo") { onOpen() },
        ) {
            if (image != null) Image(bitmap = image, contentDescription = "The last photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (saving) {
            val spin = rememberInfiniteTransition(label = "saving")
            val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "saving")
            Box(
                Modifier.fillMaxSize().padding(3.dp).drawBehind {
                    drawArc(Color.White, startAngle = angle, sweepAngle = 90f, useCenter = false, style = Stroke(width = 2.5.dp.toPx()))
                },
            )
        }
        if (count > 0) {
            Text(
                count.toString(),
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 7.dp, y = (-7).dp)
                    .widthIn(min = 22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** Presets under the shutter (the wide lens's when the phone has one, 1×, 2×, 5×); the one in use shows the zoom. */
@Composable
private fun ZoomPresets(zoom: Float, min: Float, max: Float, turn: Float, onZoom: (Float) -> Unit) {
    val presets = buildList {
        if (min < 0.95f) add(min)
        add(1f)
        if (max >= 2f) add(2f)
        if (max >= 5f) add(5f)
    }
    val active = presets.lastOrNull { it <= zoom + 0.05f } ?: presets.first()
    Row(
        Modifier.clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.32f)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (preset in presets) {
            val on = preset == active
            val size by animateFloatAsState(if (on) 40f else 34f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "zoom")
            Box(
                Modifier
                    .size(size.dp)
                    .clip(CircleShape)
                    .background(if (on) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable(onClickLabel = "Zoom ${zoomLabel(preset)}") { onZoom(preset) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    zoomLabel(if (on) zoom else preset),
                    color = if (on) Color(0xFFFFD60A) else Color.White,
                    fontSize = if (on) 13.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.graphicsLayer { rotationZ = turn },
                )
            }
        }
    }
}

private fun zoomLabel(z: Float): String =
    if (abs(z - z.roundToInt()) < 0.05f) "${z.roundToInt()}×" else String.format(Locale.ROOT, "%.1f×", z)

@Composable
private fun RoundButton(icon: Int, label: String, turn: Float, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(onClickLabel = label) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        LauncherIcon(icon, label, Modifier.graphicsLayer { rotationZ = turn }, tint = Color.White, size = 22.dp)
    }
}

/** The camera refused: what it is for, and the way to allow it (Android's question, or the launcher's settings page). */
@Composable
private fun CameraRefused(state: BoardCameraState, onAsk: () -> Unit) {
    Message(
        title = "The camera is needed for board photos",
        text = "Each photo is saved at once in Pictures/ESSAI/${state.folder}. The launcher uses the camera only while this screen is open.",
        action = if (state.askable) "Allow the camera" else "Open the launcher's settings (Permissions › Camera)",
        onAction = onAsk,
        onClose = { state.leaving = true },
    )
}

@Composable
private fun CameraMissing(reason: String, onClose: () -> Unit) {
    Message(
        title = reason,
        text = "Board photos can be taken with a camera app instead: Settings › Study companion › More.",
        action = null,
        onAction = {},
        onClose = onClose,
    )
}

@Composable
private fun Message(title: String, text: String, action: String?, onAction: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LauncherIcon(LauncherIcons.Camera, null, tint = Color.White.copy(alpha = 0.7f), size = 44.dp)
            Spacer(Modifier.height(18.dp))
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(text, color = Color.White.copy(alpha = 0.72f), fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            if (action != null) {
                Text(
                    action,
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .clickable { onAction() }
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                "Close",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.clip(RoundedCornerShape(24.dp)).clickable { onClose() }.padding(horizontal = 22.dp, vertical = 13.dp),
            )
        }
    }
}
