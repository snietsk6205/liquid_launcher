package dev.liquid.launcher.feature.home

import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.ui.graphics.asAndroidBitmap

import android.app.Activity
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.data.ItemKind
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.data.LauncherData
import dev.liquid.launcher.core.data.MenuEntry
import dev.liquid.launcher.core.designsystem.GlassQuality
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.derive
import dev.liquid.launcher.feature.redirect.RedirectStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Drives the "content settles in" animation that masks the hand-over from the system launcher. */
@Stable
class HomeReturnState {
    /** 0 = just returned (content hidden), 1 = settled. */
    val progress = mutableFloatStateOf(1f)

    /**
     * 0.11.3 (D170): the whole screen's opacity, wallpaper included, while the launcher fades in over the system's home
     * screen after a redirect (its window shows the system wallpaper behind it). 1 when it arrives at once.
     */
    val screen = mutableFloatStateOf(1f)
    var tick by mutableIntStateOf(0)
        private set
    var homePresses by mutableIntStateOf(0)
        private set

    /**
     * Call before the first frame after the activity becomes visible again. Setting the
     * progress here, not in an effect, means no frame is drawn with the content fully visible.
     */
    fun onReturn(animate: Boolean, wholeScreen: Boolean = false) {
        if (animate) progress.floatValue = 0f
        if (animate && wholeScreen) screen.floatValue = 0f
        tick++
    }

    /** Home was pressed while the launcher was already in front. */
    fun onHomePressed() {
        homePresses++
    }

    /** Something another screen asked the home screen to open ("wallpaper"), or null. */
    var request by mutableStateOf<String?>(null)

    /** 0.12.0: the text for a "search" request (from another app or the keyboard), and whether to ask Claude with it. */
    var searchText: String? = null
    var searchAsk = false

    /**
     * Asked by the activity as it is paused: is something showing that will be closed while the
     * launcher is away (drawer, search, a menu)? Set by the home screen.
     */
    var closesWhileAway: () -> Boolean = { false }

    /** Called by the activity once the launcher is no longer visible. Set by the home screen. */
    var onLeft: () -> Unit = {}

    /** 0.11.3: an app was just started from the launcher. Set by the home screen. */
    var appJustStarted: () -> Boolean = { false }
}

/**
 * Page transitions on top of the pager's own slide. [offset] is the page's distance from the
 * centre in pages: negative to the left. Pages on the left stay put in depth / stack / fade,
 * so the page on the right moves over them in both directions.
 */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.pageTransition(style: String, offset: Float) {
    val o = offset.coerceIn(-1f, 1f)
    val a = kotlin.math.abs(o)
    when (style) {
        "depth" -> if (o < 0f) {
            translationX = -o * size.width
            val scale = 1f - 0.18f * a
            scaleX = scale
            scaleY = scale
            alpha = 1f - a
        }
        "stack" -> if (o < 0f) {
            translationX = -o * size.width
            val scale = 1f - 0.05f * a
            scaleX = scale
            scaleY = scale
            alpha = 1f - 0.5f * a
        }
        "fade" -> {
            translationX = -o * size.width
            alpha = 1f - a
        }
        "cube" -> {
            cameraDistance = 14f * density
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (o < 0f) 1f else 0f, 0.5f)
            rotationY = 80f * o
            alpha = (1f - a * 0.3f)
        }
        "carousel" -> {
            cameraDistance = 14f * density
            rotationY = -35f * o
            val scale = 1f - 0.14f * a
            scaleX = scale
            scaleY = scale
        }
    }
}

/** The page's own grid and labels, if it has them. */
private fun pageEnv(env: PageEnv, page: dev.liquid.launcher.core.data.HomePage): PageEnv {
    if (page.columns == null && page.rows == null && page.labels == null) return env
    val grid = env.grid.copy(
        columns = page.columns ?: env.grid.columns,
        rows = page.rows ?: env.grid.rows,
        labels = page.labels ?: env.grid.labels,
    )
    return PageEnv(
        grid, env.clock, env.gestures, env.glass, env.lightBackground, env.contentColor, env.shadow, env.apps,
        env.wallpaperBackdrop, env.editMode, env.interactive,
    )
}

/** Changes with the wallpaper image, so the automatic accent follows a new wallpaper. */
@Composable
private fun savedImageKey(wallpaper: WallpaperStore): Any? = wallpaper.image.collectAsState().value

/** How the home screen is shown: the real thing, or a miniature inside the settings app. */
enum class HomeMode { Live, Preview, PreviewDrawer, PreviewDrawerMotion, PreviewDrawerIndex, PreviewMenu, PreviewHomeMenu, PreviewToday, PreviewSearch, PreviewEffects }

@Composable
fun HomeScreen(
    data: LauncherData,
    wallpaper: WallpaperStore,
    returnState: HomeReturnState,
    callbacks: HomeCallbacks,
    mode: HomeMode = HomeMode.Live,
) {
    // The buttons' look (Settings → Buttons, 0.6.4) for every sheet; sheets that export their glass add it as the buttons' backdrop.
    val look by data.settings.derive { controlLook() }
    val glass by data.settings.derive { glassConfig() }
    // P4 (0.9.13): the theme of buttons and chips, everywhere on the home screen.
    val controlTheme = rememberControlTheme(data.settings)
    androidx.compose.runtime.SideEffect { ThemeInk.update(controlTheme) }
    val controls = remember(look, glass, controlTheme) { ControlEnv(look, glass, null, theme = controlTheme) }
    // Presses of this screen, for the touch effect and the moving wallpaper (0.8.0, D111).
    val touchFx = remember { TouchFx() }
    // W3: the theme's particle bursts, thrown by themed controls and drawn over everything.
    val particles = remember { ParticleBursts() }
    androidx.compose.runtime.CompositionLocalProvider(LocalControls provides controls, LocalTouchFx provides touchFx, LocalParticles provides particles) {
        HomeScreenBody(data, wallpaper, returnState, callbacks, mode)
    }
}

@Composable
private fun HomeScreenBody(
    data: LauncherData,
    wallpaper: WallpaperStore,
    returnState: HomeReturnState,
    callbacks: HomeCallbacks,
    mode: HomeMode,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { HomeController(data, scope, context, callbacks) }
    val live = mode == HomeMode.Live
    val store = data.settings

    val layout by data.layout.layout.collectAsState()
    val apps by data.apps.apps.collectAsState()
    val appIndex = remember(apps) { apps.associateBy { it.ref } }
    val grid by store.derive { gridConfig() }
    val clock by store.derive { clockConfig() }
    val gestures by store.derive { gestureConfig() }
    val pill by store.derive { pillConfig() }
    val drawerConfig by store.derive { drawerConfig() }
    val motion by store.derive { drawerMotion() }
    val indexConfig by store.derive { indexConfig() }
    val look by store.derive { wallpaperLook() }
    val homeMenuConfig by store.derive { homeMenuConfig() }
    val theme by store.derive { themeConfig() }
    val pageTransition by store.derive { get(LauncherSettings.HomePageTransition) }
    val folderConfig by store.derive { folderConfig() }
    val motionConfig by store.derive { motionConfig() }
    val rootView = LocalView.current
    SideEffect {
        Haptics.config = motionConfig
        MotionScale.speed = motionConfig.speed
        Motion.style = motionConfig.style
        PressStyle.style = motionConfig.press
        PressStyle.depth = motionConfig.pressDepth
        PressStyle.wobble = motionConfig.pressWobble
        PressStyle.light = motionConfig.pressLight
        PressStyle.speed = motionConfig.pressSpeed
        EditWobble.degrees = motionConfig.editWobble
        if (live) controller.view = rootView
        controller.motionConfig = motionConfig
    }
    val dock by store.derive { dockConfig() }
    SideEffect { controller.folderConfig = folderConfig }
    val colourTick by data.colourTick.collectAsState()
    // Themed icon colours: icons read them while drawing (see IconPaint).
    val iconPalette by data.iconPalette.collectAsState()
    remember { IconPaint.init(data.iconPalette.value) }
    LaunchedEffect(iconPalette) { IconPaint.fromData(iconPalette, this) }
    if (live) IconPaintTiming()
    // W3: the theme's accent (fixed blue by default, or a colour of the wallpaper) when the theme styles the controls.
    val themeAccent = LocalControls.current?.theme?.accent
    LaunchedEffect(theme, colourTick, savedImageKey(wallpaper), themeAccent) { HomeTheme.apply(context, theme, themeAccent) }
    val glassBase by store.derive { glassConfig() }
    // E3 (0.9.7): every glass surface takes a touch of the block now or next's colour, easing from one to the next.
    val moduleGlass by store.derive { get(LauncherSettings.StudyGlassTint) }
    val tintDay by data.study.day.collectAsState()
    val tintMinute = rememberMinuteTick()
    val blockColour = remember(tintDay, tintMinute, moduleGlass) {
        if (moduleGlass) heroBlock(tintDay, System.currentTimeMillis())?.let { eventColor(it) } else null
    }
    val tintShown by androidx.compose.animation.animateColorAsState(blockColour ?: Color.Transparent, tween(1400), label = "moduleGlass")
    // A4 (0.9.18): on the trip home, under 30 % and not charging, the glass is frosted (no shader), other apps'
    // widgets and the moving wallpaper rest, until home or charging.
    val onTrip by store.derive { get(LauncherSettings.ContextActive) == dev.liquid.launcher.core.data.ContextLayouts.COMMUTE }
    val battery = rememberBattery(live && onTrip)
    val saving = onTrip && battery != null && battery.first in 0..29 && !battery.second
    SideEffect { controller.batterySaving = saving }
    LaunchedEffect(saving) {
        if (saving) controller.notice(dev.liquid.launcher.core.common.NoticeKind.Info, "Battery under 30 %", "Widgets and moving effects rest until you are home or charging")
    }
    // W8 (P8): "The wallpaper's colour": the glass takes the wallpaper's muted tone, and follows it when it changes.
    val wallTone by dev.liquid.launcher.core.common.WallpaperTones.tone.collectAsState()
    val glass = remember(glassBase, tintShown, saving, wallTone) {
        val toned = if (glassBase.tintMode == "tone") wallTone?.roles?.muted?.let { glassBase.copy(tintColor = Color(it)) } ?: glassBase else glassBase
        val base = if (!saving) toned else toned.copy(
            quality = toned.quality.cappedAt(GlassQuality.Frosted),
            largeQuality = toned.largeQuality.cappedAt(GlassQuality.Frosted),
        )
        if (tintShown.alpha < 0.01f) base else base.copy(accentTint = tintShown.copy(alpha = 1f), accentAmount = 0.42f * tintShown.alpha)
    }
    val search by store.derive { searchConfig() }
    val menuEntries by store.derive { MenuEntry.fromIds(get(LauncherSettings.MenuEntries)) }
    val handover by store.derive { get(LauncherSettings.SettleStyle) to get(LauncherSettings.SettleDuration) }
    val savedImage by wallpaper.image.collectAsState()
    // The wallpaper screen tries looks out on the real home screen before they are applied. 0.11.1: not pictures; the
    // screen keeps the current wallpaper until Apply (it shows a picked one full screen only while placing it).
    val studio = remember(controller.wallpaperStudio) {
        if (controller.wallpaperStudio && live) {
            val library = data.wallpapers.get()
            StudioState(store, library.current?.let { id -> library.items.firstOrNull { it.id == id }?.look })
        } else {
            null
        }
    }
    val image = savedImage
    // W8 (P8): the picture's own look, when it has one, over the one set for all.
    val libraryState by data.wallpapers.state.collectAsState()
    val pictureLook = if (studio != null) studio.shownLook else libraryState.current?.let { id -> libraryState.items.firstOrNull { it.id == id }?.look }
    val homeBlurDp = pictureLook?.blur?.takeIf { it >= 0 } ?: studio?.homeBlur ?: look.homeBlurDp
    val homeDim = pictureLook?.dim?.takeIf { it >= 0 }?.div(100f) ?: (studio?.homeDim?.div(100f)) ?: look.homeDim
    val saturation = (pictureLook?.saturation ?: 100) / 100f
    val vignette = (pictureLook?.vignette ?: 0) / 100f
    val searchBlurDp = studio?.searchBlur ?: search.blurDp
    val drawerShown = if (studio != null) drawerConfig.copy(dim = studio.drawerDim / 100f) else drawerConfig
    val motionShown = if (studio != null) motion.copy(homeBlurDp = studio.drawerBlur) else motion
    val density1 = LocalDensity.current
    val blurredWallpaper by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image, homeBlurDp) {
        val source = image
        value = if (source != null && homeBlurDp > 0) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                dev.liquid.launcher.core.designsystem.WallpaperBlur.blur(source.asAndroidBitmap(), homeBlurDp * density1.density).asImageBitmap()
            }
        } else {
            null
        }
    }
    // The blur behind the drawer and behind search: a blurred copy of the wallpaper, made once,
    // that fades in over the sharp one. Costs two textured quads per frame. Up to 0.5.0 the whole
    // home screen went through a live blur whose radius changed every frame, the most expensive
    // thing in the transition (now an option, "Blur the icons too").
    val deepBlurDp = maxOf(motionShown.homeBlurDp, searchBlurDp)
    val deepWallpaper by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image, homeBlurDp, deepBlurDp, motionShown.liveBlur) {
        val source = image
        value = if (source != null && deepBlurDp > 0 && !motionShown.liveBlur) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                dev.liquid.launcher.core.designsystem.WallpaperBlur.blur(source.asAndroidBitmap(), (homeBlurDp + deepBlurDp) * density1.density).asImageBitmap()
            }
        } else {
            null
        }
    }
    if (studio != null) {
        // Preview the screen being adjusted.
        LaunchedEffect(studio.surface) {
            if (studio.surface == "drawer") controller.openDrawer() else if (controller.drawer.value > 0f) controller.closeDrawer()
        }
    }
    // The home screen sinks back a little behind the home menu and behind sheets that cover it
    // (Today, layouts, the widget list), so they read as layers of one space (0.7.0).
    val menuSink by androidx.compose.animation.core.animateFloatAsState(
        if ((controller.homeMenu || controller.layoutsSheet || controller.widgetPicker) && homeMenuConfig.sink) 1f else 0f,
        label = "menuSink",
    )

    // The wallpaper on screen decides the text colour (0.11.1: a picture picked in the wallpaper screen is only shown
    // after Apply, so it no longer does; that also took a colour analysis off the main thread's composing).
    val lightBackground = remember(savedImage, colourTick) { wallpaperIsLight(context) }
    SideEffect { IconPaint.lightWallpaper = lightBackground }
    val contentColor = theme.textColor ?: if (lightBackground) Color(0xFF16181C) else Color.White
    val shadow = if (lightBackground) null else Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 4f)

    val density0 = LocalDensity.current
    val currentLayout by rememberUpdatedState(layout)
    controller.pager = rememberPagerState(initialPage = layout.defaultIndex) { currentLayout.pages.size.coerceAtLeast(1) }

    if (live) {
        StatusBarIcons(lightBackground)
        val overlay by remember { derivedStateOf { controller.anyOverlay } }
        BackHandler(enabled = overlay) { controller.back() }

        // Wind-down (D4): from the chosen time the wallpaper's colour drains over a few seconds; back in the morning.
        val windDownAt by store.derive { get(LauncherSettings.StudyWindDown).toIntOrNull() }
        val windTick = rememberMinuteTick()
        LaunchedEffect(windDownAt, windTick) {
            val target = if (isWindDown(windDownAt)) 1f else 0f
            if (controller.windDown.targetValue != target) controller.windDown.animateTo(target, androidx.compose.animation.core.tween(2400))
        }
        // A4 / C4: layouts that switch by themselves, checked each minute and when the day or the arrival changes.
        val contextTick = rememberMinuteTick()
        val contextDay by data.study.day.collectAsState()
        val contextHome by data.study.homeAt.collectAsState()
        LaunchedEffect(contextTick, contextDay, contextHome) {
            controller.applyContextLayout()
            // P8 (0.9.21): a layout switched to for a while goes back when its time is up.
            controller.checkLayoutTimer()
        }
        // The edit-mode wobble's clock runs only while editing with the wobble on (0.9.4).
        if (controller.editMode && motionConfig.editWobble > 0f) {
            LaunchedEffect(Unit) {
                val start = androidx.compose.runtime.withFrameNanos { it }
                while (true) androidx.compose.runtime.withFrameNanos { EditWobble.time.floatValue = (it - start) / 1e9f }
            }
        }
        LaunchedEffect(returnState.tick) {
            // Back from the app an icon opened: that icon lands as the home screen settles (0.9.4).
            controller.lastLaunchKey?.let { key ->
                controller.lastLaunchKey = null
                IconLanding.land(key)
            }
            val progress = returnState.progress
            val screen = returnState.screen
            if (progress.floatValue < 1f || screen.floatValue < 1f) {
                val (style, duration) = handover
                if (style == "none" || duration == 0) {
                    progress.floatValue = 1f
                    screen.floatValue = 1f
                } else {
                    // 0.11.3: the screen (wallpaper too) fades in over the system's home screen, eased out, and the
                    // content settles in with it.
                    val fromScreen = screen.floatValue
                    animate(0f, 1f, animationSpec = tween(duration, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) { value, _ ->
                        progress.floatValue = value
                        if (fromScreen < 1f) screen.floatValue = fromScreen + (1f - fromScreen) * value
                    }
                    screen.floatValue = 1f
                }
            }
        }
        LaunchedEffect(returnState.request) {
            when (returnState.request) {
                "wallpaper" -> controller.run(LauncherAction.Wallpaper)
                "layouts" -> controller.run(LauncherAction.Layouts)
                "today" -> controller.run(LauncherAction.Today)
                "study" -> {
                    controller.setupStudy()
                    controller.run(LauncherAction.Today)
                }
                // 0.11.2: Settings → Diagnostics → Speed check.
                "speedCheck" -> controller.runSpeedCheck()
                // 0.12.0 (D171): text from another app, the text selection menu or the keyboard.
                "search" -> returnState.searchText?.let { text ->
                    returnState.searchText = null
                    val open = controller.searchOpen
                    if (!open) controller.closeAll(immediately = true)
                    controller.searchQuery = text
                    controller.askClaudeMode = returnState.searchAsk
                    if (!open) controller.openSearch()
                }
            }
            returnState.request = null
        }
        DisposableEffect(controller, returnState) {
            returnState.closesWhileAway = { controller.closesWhileAway }
            returnState.appJustStarted = { controller.appJustStarted }
            returnState.onLeft = { controller.tidyAfterLeaving() }
            onDispose {
                returnState.closesWhileAway = { false }
                returnState.appJustStarted = { false }
                returnState.onLeft = {}
            }
        }
        LaunchedEffect(returnState.homePresses) {
            if (returnState.homePresses == 0) return@LaunchedEffect
            val wasOpen = controller.anyOverlay
            controller.closeAll()
            if (controller.editMode) controller.exitEdit()
            val home = data.layout.layout.value.defaultIndex
            if (!wasOpen && controller.pager.currentPage != home) controller.pager.animateScrollToPage(home)
        }
    }
    if (live) {
        // A wallpaper set in another app: the launcher cannot read it, so glass has nothing to refract. Say so once.
        LaunchedEffect(Unit) {
            var seen = dev.liquid.launcher.core.common.WallpaperTones.outsideChanges.value
            dev.liquid.launcher.core.common.WallpaperTones.outsideChanges.collect { count ->
                if (count > seen) {
                    controller.notice(
                        dev.liquid.launcher.core.common.NoticeKind.Wallpaper,
                        "Wallpaper changed in another app",
                        "Tap to choose it here: glass needs the picture",
                        LauncherAction.WallpaperStudio,
                    )
                }
                seen = count
            }
        }
    }
    if (live) {
        val dragging = controller.drag != null
        LaunchedEffect(dragging) {
            if (dragging) controller.edgeScrollWhileDragging(with(density0) { 28.dp.toPx() })
        }
    }
    SideEffect {
        controller.motion = motion
        controller.logMoves = live
    }
    if (mode == HomeMode.PreviewDrawer || mode == HomeMode.PreviewDrawerIndex) {
        LaunchedEffect(Unit) { controller.drawer.snapTo(1f) }
    }
    // The index preview moves a pretend finger along the letters.
    controller.indexDemo = mode == HomeMode.PreviewDrawerIndex
    if (mode == HomeMode.PreviewDrawerMotion) {
        // Opens and closes the drawer in a loop, so the transition settings can be seen.
        LaunchedEffect(motion) {
            while (true) {
                delay(700)
                controller.openDrawer()
                delay(1700)
                controller.closeDrawer()
                delay(1300)
            }
        }
    }
    if (mode == HomeMode.PreviewSearch) {
        // Settings → Search: opens and closes search in a loop, so its transition can be tuned.
        LaunchedEffect(search, motionConfig.searchTransition) {
            while (true) {
                delay(700)
                controller.openSearch()
                delay(2300)
                controller.closeSearch()
                delay(1400)
            }
        }
    }
    if (mode == HomeMode.PreviewMenu) {
        val sample = apps.firstOrNull { !it.hidden }
        LaunchedEffect(sample, controller.screenHeight) {
            if (sample != null && controller.screenHeight > 1f) {
                val y = controller.screenHeight * 0.62f
                controller.menu = MenuTarget(sample, null, androidx.compose.ui.geometry.Rect(Offset(380f, y), androidx.compose.ui.geometry.Size(160f, 160f)))
            }
        }
    }

    val homeBackdrop = rememberLayerBackdrop()
    val wallpaperBackdrop = rememberLayerBackdrop()
    val drawerLayer = rememberLayerBackdrop()
    // Overlays refract the drawer too when they open over it (long-press menu in the drawer).
    val overlayBackdrop = rememberCombinedBackdrop(homeBackdrop, drawerLayer)
    val drawerLight = lightBackground && drawerConfig.dim < 0.35f
    val morph = pill.enabled && !pill.top && motion.pillMorph && !controller.editMode
    // Where search's field goes (0.7.2, D107): in the pill, above the keyboard, unless it is asked for at the top.
    SideEffect {
        controller.searchInPill = morph && search.field != "top"
        controller.searchField = search.field
        controller.searchKeyboard = search.keyboard
        controller.searchFieldSize = search.fieldSize
        controller.live = live
    }
    val density = LocalDensity.current
    val settle = handover.first

    // For a layout or wallpaper change, the screen is recorded into a layer for one frame, so
    // a picture of the old screen can lie over the new one and melt away. Only while a picture
    // is wanted: recording every frame would cost each frame an extra full-screen layer.
    val shotLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    var capturing by remember { mutableStateOf(false) }
    if (live) {
        SideEffect {
            controller.captureHome = {
                capturing = true
                try {
                    // One frame drawn through the layer, then its picture.
                    androidx.compose.runtime.withFrameNanos { }
                    androidx.compose.runtime.withFrameNanos { }
                    shotLayer.toImageBitmap()
                } finally {
                    capturing = false
                }
            }
        }
    }
    val transitionStyle by store.derive { get(LauncherSettings.WallpaperTransition) }
    if (live) SystemWallpaperOffsets(controller)
    val touchFx = LocalTouchFx.current
    val touchBase by store.derive { touchFxConfig() }
    val wallpaperBase by store.derive { wallpaperFxConfig() }
    // W3: the theme's board effect, a press splash or a moving background, when it has one.
    val themeEffect = LocalControls.current?.theme?.effect
    val touchConfig = remember(touchBase, themeEffect) { ThemeEffects.touch(themeEffect, touchBase) }
    val wallpaperFx = remember(wallpaperBase, themeEffect) { ThemeEffects.wallpaper(themeEffect, wallpaperBase) }
    if (touchFx != null) SideEffect { touchFx.config = touchConfig }
    // W3: what is on screen, written next to the frame times in the debug log (FrameMeter), so their cost is known.
    val costTheme = LocalControls.current?.theme
    SideEffect { FrameCost.describe(wallpaperFx, costTheme) }
    if (live) {
        // 0.10.4: the frame meter names the screen that is open; read in a flow, so the home screen never recomposes for it.
        LaunchedEffect(controller) {
            androidx.compose.runtime.snapshotFlow { openScreenName(controller) }.collect { FrameCost.screen = it }
        }
    }
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current

    Box(
        Modifier
            .fillMaxSize()
            // 0.11.3 (D170): after a redirect the whole screen fades in over the system's home screen (read in the layer).
            .then(if (live) Modifier.graphicsLayer { alpha = returnState.screen.floatValue } else Modifier)
            .onSizeChanged {
                controller.screenHeight = it.height.toFloat().coerceAtLeast(1f)
                controller.screenWidth = it.width.toFloat().coerceAtLeast(1f)
            }
            .then(if (touchFx != null) Modifier.touchFxDowns(touchFx).onGloballyPositioned { touchFx.root = it } else Modifier)
            .then(
                if (live) Modifier.drawWithContent {
                    if (capturing) {
                        shotLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(shotLayer)
                    } else {
                        drawContent()
                    }
                } else Modifier
            )
    ) {
        // ---- Home: wallpaper and pages, recorded so glass surfaces can refract them ----
        // W3: the theme's blur behind sheets (WM Keyboard's window blur), easing in and out with them. Sheets whose
        // point is to preview the screen behind them (clock, widget options, wallpaper) leave it sharp.
        val windowBlurDp = LocalControls.current?.theme?.windowBlurDp ?: 0
        val sheetOpen = controller.layoutsSheet || controller.tagging != null || controller.wallpaperStrip || controller.widgetPicker ||
            controller.todaySheet || controller.homeMenu || controller.menu != null || controller.openFolder != null || controller.reviewSheet != null
        val sheetBlur by androidx.compose.animation.core.animateFloatAsState(
            if (windowBlurDp > 0 && sheetOpen) 1f else 0f,
            androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = MotionScale.stiffness(420f)),
            label = "sheetBlur",
        )
        // W7: the blur behind the study hub, in step with it growing out of the pill.
        val hubBlurDp by store.derive { get(LauncherSettings.StudyHubBlur) }
        Box(Modifier.fillMaxSize().layerBackdrop(homeBackdrop)) {
          // Blurs wallpaper and pages together while the drawer is open.
          Box(
              Modifier
                  .fillMaxSize()
                  .graphicsLayer {
                      val sheetRadius = maxOf(windowBlurDp.dp.toPx() * sheetBlur, hubBlurDp.dp.toPx() * controller.hubAmount)
                      if (!motionShown.liveBlur) {
                          renderEffect = if (sheetRadius >= 0.5f) BlurEffect(sheetRadius, sheetRadius, TileMode.Clamp) else null
                          return@graphicsLayer
                      }
                      val p = controller.drawer.value.coerceIn(0f, 1f)
                      val searchP = if (studio?.surface == "search") 1f else controller.searchAmount
                      val drawerRadius = motionShown.homeBlurDp.dp.toPx() * p
                      val searchRadius = searchBlurDp.dp.toPx() * searchP
                      val radius = maxOf(drawerRadius, searchRadius, sheetRadius)
                      renderEffect = if (radius >= 0.5f) BlurEffect(radius, radius, TileMode.Clamp) else null
                  }
          ) {
            val placing = studio?.takeIf { it.drawsSource }
            val shownWallpaper = placing?.sourceImage ?: blurredWallpaper ?: image
            // The wallpaper takes part in the drawer transition (it drifts and grows a little).
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    if (placing == null) {
                        wallpaperInDrawerTransition(controller.drawer.value, motionShown)
                        when (motionConfig.searchTransition) {
                            "space" -> wallpaperInSearchTransition(controller.searchAmount)
                            // Grows into its blur, as for the drawer (D107).
                            "lift", "zoom" -> wallpaperInSearchTransition(controller.searchAmount, drift = false)
                        }
                        // A wallpaper or layout change: the new wallpaper settles in under the old picture (D103).
                        if (controller.transitionShot != null) with(WallpaperTransitions) { newWallpaperArriving(controller.transitionProgress.value, transitionStyle) }
                    }
                }
            ) {
            if (shownWallpaper != null) {
                WallpaperImage(
                    image = shownWallpaper,
                    frame = if (placing != null) ({ placing.frame(controller.screenWidth, controller.screenHeight) }) else ({ null }),
                    offset = {
                        studio?.sweep ?: run {
                            val pages = controller.pager.pageCount
                            if (pages <= 1) 0.5f else (controller.pager.currentPage + controller.pager.currentPageOffsetFraction) / (pages - 1)
                        }
                    },
                    modifier = Modifier.layerBackdrop(wallpaperBackdrop),
                    filter = { controller.wallpaperFilter(saturation) },
                )
                // 0.11.1: after placing, the placed picture fades back into the wallpaper (Apply then ripples to it).
                val leaving = studio?.takeIf { it.placeLeaving && !it.adjusting }
                val leavingImage = leaving?.sourceImage
                if (leaving != null && leavingImage != null) {
                    WallpaperImage(
                        image = leavingImage,
                        frame = { leaving.frame(controller.screenWidth, controller.screenHeight) },
                        offset = {
                            leaving.sweep ?: run {
                                val pages = controller.pager.pageCount
                                if (pages <= 1) 0.5f else (controller.pager.currentPage + controller.pager.currentPageOffsetFraction) / (pages - 1)
                            }
                        },
                        modifier = Modifier,
                        alpha = { leaving.placeShown.value },
                    )
                }
                val deep = deepWallpaper
                if (deep != null && placing == null) {
                    WallpaperImage(
                        image = deep,
                        frame = { null },
                        offset = {
                            val pages = controller.pager.pageCount
                            if (pages <= 1) 0.5f else (controller.pager.currentPage + controller.pager.currentPageOffsetFraction) / (pages - 1)
                        },
                        modifier = Modifier,
                        alpha = {
                            val p = controller.drawer.value.coerceIn(0f, 1f)
                            val searchP = if (studio?.surface == "search") 1f else controller.searchAmount
                            val depth = deepBlurDp.coerceAtLeast(1).toFloat()
                            maxOf(p * motionShown.homeBlurDp / depth, searchP * searchBlurDp / depth)
                        },
                        filter = { controller.windDownFilter() },
                    )
                }
            }
            // The moving wallpaper (0.8.0, D113): over the picture, under the icons, inside what glass bends.
            if (studio == null) {
                WallpaperFxLayer(
                    wallpaperFx, touchFx,
                    running = {
                        (!live || windowInfo.isWindowFocused) && !controller.batterySaving &&
                            controller.drawer.value < 0.98f && controller.searchAmount < 0.98f && controller.transitionShot == null
                    },
                )
            }
            }
            if (homeDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = homeDim)))
            // W8 (P8): the picture's vignette: its corners darker, the middle as it is.
            if (vignette > 0.01f) {
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        drawRect(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f * vignette),
                                center = center, radius = size.maxDimension * 0.75f,
                            ),
                        )
                    },
                )
            }
            val pillSpace = (if (pill.enabled || controller.editMode) pill.heightDp + pill.offsetDp + 12 else 0) +
                (if (dock.enabled) dock.height.value.toInt() + 8 else 0)
            // Remembered: a new gesture lambda would restart the detector and cancel a swipe in progress.
            val homeView = LocalView.current
            val swipes = remember(gestures, controller.editMode) { Modifier.homeGestures(gestures, controller, homeView) }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = controller.drawer.value
                        val back = returnState.progress.floatValue
                        // The drawer transition's share (fade, glide or sink), then everything else on top of it.
                        homeInDrawerTransition(p, motion)
                        alpha *= back
                        var scale = scaleX
                        if (settle == "scale") scale *= 0.94f + 0.06f * back
                        if (controller.editMode) scale *= 0.9f
                        // The study hub moves the home screen back with its own spring, frame for frame (0.9.1).
                        scale *= 1f - 0.06f * maxOf(menuSink, if (homeMenuConfig.sink) controller.hubAmount else 0f)
                        // Search: the home screen glides down under the finger ("space", D99) or sinks back ("depth").
                        val s = controller.searchAmount
                        if (s > 0f) when (motionConfig.searchTransition) {
                            "lift" -> {
                                // Falls back into the blur, sinking a little, while the pill lifts to the keyboard (D107).
                                val e = 1f - (1f - s) * (1f - s)
                                alpha *= (1f - s * 1.45f).coerceIn(0f, 1f)
                                scale *= 1f - search.homeScale * e
                                translationY += e * 0.035f * size.height
                            }
                            "space" -> {
                                alpha *= (1f - s * 1.7f).coerceIn(0f, 1f)
                                translationY += s * 0.16f * size.height
                                scale *= 1f - motion.homeScale * 0.6f * s
                            }
                            "depth" -> {
                                scale *= 1f - 0.07f * s
                                alpha *= 1f - 0.35f * s
                            }
                            "zoom" -> {
                                // Falls back into the distance while search comes forward (D104).
                                scale *= 1f - search.homeScale * 1.2f * s
                                alpha *= (1f - s * 1.5f).coerceIn(0f, 1f)
                            }
                        }
                        scaleX = scale
                        scaleY = scale
                        // A wallpaper or layout change: the new pages settle into place (D103).
                        if (controller.transitionShot != null) with(WallpaperTransitions) { newScreenArriving(controller.transitionProgress.value, transitionStyle) }
                    }
                    .then(if (live) swipes else Modifier)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(
                        start = grid.marginSideDp.dp,
                        end = grid.marginSideDp.dp,
                        top = (grid.marginTopDp + if (pill.top) pillSpace else 0).dp,
                        bottom = (grid.marginBottomDp + if (!pill.top) pillSpace else 0).dp + 14.dp,
                    )
            ) {
                val env = PageEnv(
                    grid, clock, gestures, glass, lightBackground, contentColor, shadow, appIndex,
                    wallpaperBackdrop, controller.editMode, live,
                )
                HorizontalPager(
                    state = controller.pager,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = layout.pages.size,
                    key = { layout.pages.getOrNull(it)?.id ?: it },
                    userScrollEnabled = live,
                ) { index ->
                    val page = layout.pages.getOrNull(index)
                    if (page != null) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (pageTransition == "slide") Modifier else Modifier.graphicsLayer {
                                        pageTransition(pageTransition, controller.pager.getOffsetDistanceInPages(index))
                                    }
                                )
                        ) {
                            HomePageView(index, page, pageEnv(env, page), controller)
                        }
                    }
                }
                PageDots(
                    count = layout.pages.size,
                    current = controller.pager.currentPage,
                    color = contentColor,
                    modifier = Modifier.align(if (pill.top) Alignment.TopCenter else Alignment.BottomCenter)
                        .padding(vertical = if (pill.top) 0.dp else 0.dp)
                        .graphicsLayer { translationY = with(density) { (if (pill.top) -10 else 12).dp.toPx() } },
                )
            }
          }
        }

        // ---- Pill, or the edit toolbar in its place ----
        // A sheet rising from the bottom (Today, layouts, clock gallery) puts the pill away instead of
        // showing it through its glass (0.6.4).
        // The study hub is not one of them: it grows out of the pill itself, which fades under it (0.9.1).
        val sheetUp = !pill.top && (controller.layoutsSheet || controller.widgetPicker || controller.clockStudio != null)
        val pillAway by androidx.compose.animation.core.animateFloatAsState(if (sheetUp) 1f else 0f, label = "pillAway")
        val barModifier = Modifier
            .align(if (pill.top) Alignment.TopCenter else Alignment.BottomCenter)
            .then(if (pill.top) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
            .padding(vertical = pill.offsetDp.dp)
            .graphicsLayer {
                val p = controller.drawer.value
                val s = controller.searchAmount
                alpha = (1f - p * 2f).coerceIn(0f, 1f) * (1f - s * 2f).coerceIn(0f, 1f) * returnState.progress.floatValue * (1f - pillAway) *
                    (1f - controller.hubAmount * 3f).coerceIn(0f, 1f)
                translationY = (p * 40.dp.toPx() + pillAway * 24.dp.toPx() + s * 40.dp.toPx()) * (if (pill.top) -1 else 1)
            }
        if (controller.editMode && live) {
            if (controller.pagesSheet) {
                PagesSheet(
                    layout = layout,
                    grid = grid,
                    current = controller.pager.currentPage,
                    controller = controller,
                    glass = glass,
                    lightBackground = lightBackground,
                    backdrop = homeBackdrop,
                    modifier = Modifier
                        .align(if (pill.top) Alignment.TopCenter else Alignment.BottomCenter)
                        .then(if (pill.top) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
                        .padding(vertical = (pill.offsetDp + maxOf(pill.heightDp, 54) + 12).dp),
                )
            }
            EditToolbar(controller, glass, lightBackground, homeBackdrop, pill, barModifier)
        } else if (pill.enabled && !morph) {
            Pill(pill, glass, lightBackground, homeBackdrop, appIndex, controller, live, barModifier)
        }

        if (dock.enabled) {
            val belowPill = (pill.enabled || controller.editMode) && !pill.top
            Dock(
                config = dock,
                layout = layout,
                apps = appIndex,
                glass = glass,
                lightBackground = lightBackground,
                backdrop = homeBackdrop,
                color = contentColor,
                shadow = shadow,
                controller = controller,
                interactive = live,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (belowPill) (pill.offsetDp + maxOf(pill.heightDp, 54) + 10).dp else 14.dp)
                    .padding(horizontal = (grid.marginSideDp + 8).dp)
                    .graphicsLayer {
                        val p = controller.drawer.value
                        alpha = (1f - p * 2f).coerceIn(0f, 1f) * (1f - controller.searchAmount * 2f).coerceIn(0f, 1f) * returnState.progress.floatValue
                    },
            )
        }

        if (live) {
            StatusChips(contentColor, callbacks, Modifier.align(Alignment.TopCenter).statusBarsPadding())
        }

        // ---- Overlays ----
        if (studio?.surface == "search") {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = studio.searchDim / 100f)))
        }
        AppDrawer(
            controller = controller,
            config = drawerShown,
            motion = motionShown,
            indexConfig = indexConfig,
            glass = glass,
            lightBackground = lightBackground,
            apps = apps,
            homeBackdrop = homeBackdrop,
            drawerLayer = drawerLayer,
            ownSearchPill = !morph,
            interactive = live && studio == null,
        )
        // Search keeps itself on screen while its progress is above 0, so it can follow the finger and close by itself (D99).
        val searchShown by remember { derivedStateOf { controller.searchOpen || controller.search.value > 0.001f } }
        val searchEnv = PageEnv(grid, clock, gestures, glass, lightBackground, contentColor, shadow, appIndex, wallpaperBackdrop, false, live)
        // With the pill turning into the search field, search sits under the pill (D104).
        val searchHere = live || mode == HomeMode.PreviewSearch
        if (searchHere && searchShown && controller.searchInPill) {
            SearchOverlay(
                controller, search, glass, lightBackground, overlayBackdrop, apps, motionConfig.searchTransition, motion,
                fieldInPill = true, env = searchEnv, pillOffsetDp = pill.offsetDp, live = live,
            )
        }
        if (morph) {
            MorphingPill(
                config = pill,
                drawer = drawerConfig,
                glass = glass,
                lightBackground = lightBackground,
                drawerLight = drawerLight,
                backdrop = overlayBackdrop,
                apps = appIndex,
                controller = controller,
                interactive = live,
                // Its distance from the bottom (navigation bar, keyboard) is worked out inside (D107).
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        // Only the closed pill makes way for a sheet; a pill turned into a search field (drawer or search) stays.
                        val away = pillAway * (1f - controller.morph()).coerceIn(0f, 1f)
                        // With search's field at the top, the pill steps away as search opens.
                        val s = if (controller.searchInPill) 0f else controller.searchAmount
                        alpha = returnState.progress.floatValue * (1f - away) * (1f - s * 2f).coerceIn(0f, 1f) *
                            (1f - controller.hubAmount * 3f).coerceIn(0f, 1f)
                        translationY = away * 24.dp.toPx() + s * 40.dp.toPx()
                    },
            )
        }

        // Each of these keeps its own entrance and leaves through Presence: a quick fade and sink,
        // the same for all of them.
        if (searchHere && searchShown && !controller.searchInPill) {
            SearchOverlay(controller, search, glass, lightBackground, overlayBackdrop, apps, motionConfig.searchTransition, motion, env = searchEnv, live = live)
        }
        // 0.13.0 (D176): overlays leave as a picture of themselves, into what opened them; sheets follow a drag down and
        // the Back gesture.
        val folderAt = controller.folderOrigin
        Presence(
            controller.openFolder?.let { id -> layout.find(id)?.second?.takeIf { it.kind == ItemKind.FOLDER } },
            origin = { folderAt?.let { androidx.compose.ui.geometry.Rect(it.x - 90f, it.y - 90f, it.x + 90f, it.y + 90f) } },
        ) { folder ->
            FolderOverlay(folder, appIndex, controller, glass, lightBackground, overlayBackdrop, grid)
        }
        Presence(controller.menu, origin = { it.anchor }) { target ->
            AppMenu(target, menuEntries, controller, glass, lightBackground, overlayBackdrop)
        }
        if (mode == HomeMode.PreviewHomeMenu) SideEffect { controller.homeMenu = true }
        Presence(if ((live || mode == HomeMode.PreviewHomeMenu) && controller.homeMenu) Unit else null, onClose = { controller.back() }) {
            HomeMenu(homeMenuConfig, glass, lightBackground, overlayBackdrop, appIndex, controller)
        }
        Presence(if (live && controller.layoutsSheet) Unit else null, onClose = { controller.back() }) {
            LayoutsSheet(glass, lightBackground, overlayBackdrop, controller)
        }
        Presence(if (live) controller.widgetOptions?.let { id -> layout.find(id)?.second } else null, onClose = { controller.back() }) { item ->
            WidgetOptionsSheet(item, glass, lightBackground, overlayBackdrop, controller)
        }
        Presence(if (live && controller.wallpaperStrip) Unit else null, onClose = { controller.back() }) {
            WallpaperStrip(glass, lightBackground, overlayBackdrop, controller)
        }
        // 0.13.0 (D178): the flashcards due, from the pill's live item or a card search found.
        Presence(if (live) controller.reviewSheet else null, onClose = { controller.back() }) { start ->
            ReviewSheet(start, glass, lightBackground, overlayBackdrop, controller)
        }
        // 0.11.1: the sheet sinks away when the screen closes (it vanished in one frame); after Apply with a new picture it
        // goes at once, as it leaves inside the picture of the old screen that ripples away.
        Presence(studio, instantExit = { controller.transitionShot != null }) { shownStudio ->
            WallpaperStudio(shownStudio, glass, lightBackground, overlayBackdrop, controller)
        }
        if (mode == HomeMode.PreviewToday) {
            // Settings → Buttons and Study companion: the study hub, open, with the real day.
            // Replayed when the hub's size or bounce changes, so the setting is seen doing what it says.
            val replay by store.derive { get(LauncherSettings.StudyHubBounce) to get(LauncherSettings.StudyHubSize) }
            LaunchedEffect(replay) {
                data.study.refresh()
                controller.closeHub(immediately = true)
                kotlinx.coroutines.delay(160)
                controller.openHub()
            }
        }
        // The study hub (0.9.1): composed while open and while it shrinks back into the pill.
        val hubShown by remember { derivedStateOf { controller.todaySheet || controller.hub.value > 0.001f } }
        if ((live || mode == HomeMode.PreviewToday) && hubShown) {
            StudyHub(glass, lightBackground, overlayBackdrop, controller)
        }
        // C1: a distracting app asked for during a block waits here.
        Presence(
            controller.speedBump.takeIf { live },
            origin = { b -> b.bounds?.let { androidx.compose.ui.geometry.Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) } },
        ) { bump ->
            SpeedBumpOverlay(bump, glass, lightBackground, overlayBackdrop, controller)
        }
        Presence(if (live && controller.widgetPicker) Unit else null, onClose = { controller.back() }) {
            WidgetPicker(glass, lightBackground, overlayBackdrop, controller)
        }
        if (live) {
            // The wallpaper transitions' shaders, compiled before the first change (D103).
            LaunchedEffect(Unit) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    WallpaperTransitions.warmUp()
                    // P4: the real glass's shader, checked (and logged) once before the first glass surface needs it.
                    dev.liquid.launcher.core.designsystem.LiquidShader.warmUp()
                }
            }
            StudyNotices(controller)
            // Follow the calendar while the home screen is composed; read it when the companion is switched on.
            val studyOn by store.derive { get(LauncherSettings.StudyEnabled) }
            androidx.compose.runtime.DisposableEffect(studyOn) {
                data.study.refresh()
                data.study.watch(studyOn)
                onDispose { data.study.watch(false) }
            }
        }
        Presence(controller.clockStudio.takeIf { live }) { clock ->
            ClockStudio(clock, glass, lightBackground, overlayBackdrop, controller)
        }
        if (controller.savingLayout) SaveLayoutDialog(controller)
        controller.pageSettings?.let { PageSettingsDialog(it, controller) }
        controller.drag?.let { drag ->
            RemoveZone(drag, controller, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp))
            DragGhost(drag)
        }
        controller.renaming?.let { RenameDialog(it, controller) }
        Presence(controller.tagging, onClose = { controller.back() }) { app -> ProvideControls(controller, glass, null) { AppTagsSheet(app, controller, glass, lightBackground, overlayBackdrop) } }
        controller.iconPicking?.let { IconPickerDialog(it, controller) }
        controller.swipeEditing?.let { id -> layout.find(id)?.let { (_, item) -> SwipeActionsDialog(item, apps, controller) } }
        // W6: picking apps in the drawer, tagging them together, and a tag's own list of apps.
        val picking by remember { derivedStateOf { controller.selection != null && controller.drawerOpen } }
        // 0.11.1: the bar, the bulk tag sheet and the "Customise" chip leave through Presence too (they vanished in one frame).
        Presence(if (picking) controller.selection else null) { picked -> SelectionBar(controller, picked, glass, lightBackground, overlayBackdrop) }
        if (controller.selection != null && !picking) SideEffect { controller.endSelecting() }
        val bulk = controller.bulkTag
        val picked = controller.selection
        Presence(if (bulk != null && picked != null) bulk to picked else null, onClose = { controller.back() }) { (shownBulk, shownPicked) ->
            ProvideControls(controller, glass, null) { BulkTagSheet(controller, shownBulk, shownPicked, glass, lightBackground, overlayBackdrop) }
        }
        Presence(controller.tagApps, onClose = { controller.back() }) { tag -> ProvideControls(controller, glass, null) { TagAppsSheet(controller, tag, glass, lightBackground, overlayBackdrop) } }
        Presence(controller.layoutWallpaperPick, onClose = { controller.back() }) { id -> ProvideControls(controller, glass, null) { LayoutWallpaperSheet(controller, id, glass, lightBackground, overlayBackdrop) } }
        // W4: "Customise …" after a long press on a surface.
        Presence(controller.customise) { offer -> CustomiseChip(controller, offer, if (lightBackground) Color(0xFF16181C) else Color.White) }
        if (live) TransitionOverlay(controller, transitionStyle)
        if (mode == HomeMode.PreviewEffects && touchFx != null) EffectsDemo(touchFx, glass, lightBackground, overlayBackdrop)
        // What a press throws over the screen (0.8.0, D111), above everything it was pressed on.
        if (touchFx != null) TouchFxLayer(touchFx, touchConfig)
        LocalParticles.current?.let { ParticleLayer(it) }
        if (live) NoticeHost(controller, glass, lightBackground, overlayBackdrop)
        // W1 (0.10.0): after an update, what it brought, once.
        if (live) WhatsNewHost(controller, glass, lightBackground, overlayBackdrop)
    }
}

/**
 * Tells the system which part of a wide (parallax) wallpaper the home screen shows, so the
 * system's own copy matches it around app launches and in recents.
 */
@Composable
private fun SystemWallpaperOffsets(controller: HomeController) {
    val view = LocalView.current
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow {
            if (!controller.pagerReady) null else controller.pager.settledPage to controller.pager.pageCount
        }.collect { value ->
            val (page, count) = value ?: return@collect
            val token = view.windowToken ?: return@collect
            runCatching {
                val manager = WallpaperManager.getInstance(view.context)
                val step = if (count > 1) 1f / (count - 1) else 0f
                manager.setWallpaperOffsetSteps(step, 1f)
                manager.setWallpaperOffsets(token, if (count > 1) page * step else 0.5f, 0.5f)
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, color: Color, modifier: Modifier) {
    if (count <= 1) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == current) 7.dp else 6.dp)
                    .background(color.copy(alpha = if (i == current) 0.9f else 0.35f), CircleShape)
            )
        }
    }
}

@Composable
private fun EditToolbar(
    controller: HomeController,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    pill: PillConfig,
    modifier: Modifier,
) {
    val canUndo by controller.data.layout.canUndo.collectAsState()
    val canRedo by controller.data.layout.canRedo.collectAsState()
    val shape = RoundedCornerShape(percent = pill.cornerPercent)
    val color = if (glass.lightGlass(Surfaces.Pill, lightBackground)) Color(0xFF16181C) else Color.White
    Row(
        modifier
            .fillMaxWidth(maxOf(pill.widthFraction, 0.9f))
            .height(maxOf(pill.heightDp, 54).dp)
            .liquidGlass(backdrop, shape, glass.style(Surfaces.Pill, lightBackground))
            .clip(shape)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton(LauncherIcons.Recents, "Pages", color) { controller.pagesSheet = !controller.pagesSheet }
        ToolButton(LauncherIcons.Widgets, "Widgets", color) { controller.widgetPicker = !controller.widgetPicker }
        ToolButton(LauncherIcons.Undo, "Undo", color, enabled = canUndo) { controller.data.layout.undo() }
        ToolButton(LauncherIcons.Redo, "Redo", color, enabled = canRedo) { controller.data.layout.redo() }
        ToolButton(LauncherIcons.Settings, "Settings", color) { controller.run(LauncherAction.OpenSettings) }
        ToolButton(LauncherIcons.Check, "Done", color) { controller.exitEdit() }
    }
}

@Composable
private fun ToolButton(icon: Int, label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LauncherIcon(icon, contentDescription = label, tint = color.copy(alpha = if (enabled) 1f else 0.35f), size = 22.dp)
    }
}

@Composable
private fun RemoveZone(drag: DragState, controller: HomeController, modifier: Modifier) {
    val label = if (drag.source !is DragSource.NewApp) "Remove" else "Cancel"
    Row(
        modifier
            .onGloballyPositioned { controller.removeZone = it.boundsInRoot().inflate(24f) }
            .clip(RoundedCornerShape(50))
            .background(if (drag.overRemove) Color(0xFFD64545) else Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherIcon(if (drag.source !is DragSource.NewApp) LauncherIcons.Delete else LauncherIcons.Close, contentDescription = null, tint = Color.White, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, style = TextStyle(fontFamily = HomeTheme.font, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium))
    }
}

/** The picked-up item, following the finger. */
@Composable
private fun DragGhost(drag: DragState) {
    val density = LocalDensity.current
    val w = with(density) { drag.size.width.toDp() }
    val h = with(density) { drag.size.height.toDp() }
    Box(
        Modifier
            .offset { (drag.finger - drag.grab).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
            .size(w, h)
            .graphicsLayer {
                scaleX = 1.08f
                scaleY = 1.08f
                alpha = if (drag.overRemove) 0.5f else 0.92f
            },
        contentAlignment = Alignment.Center,
    ) {
        if (drag.icon != null) {
            ArtIcon(drag.icon, drag.art, Modifier.squareFraction(0.75f), null)
        } else {
            Box(Modifier.fillMaxSize().padding(6.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp)))
        }
    }
}

@Composable
private fun StatusChips(color: Color, callbacks: HomeCallbacks, modifier: Modifier) {
    val connected by RedirectStatus.connected.collectAsState()
    val blocked by RedirectStatus.launchBlocked.collectAsState()
    val pausedUntil by RedirectStatus.pausedUntilWall.collectAsState()
    val now by produceState(System.currentTimeMillis(), pausedUntil) {
        while (pausedUntil > System.currentTimeMillis()) {
            value = System.currentTimeMillis()
            delay(1000)
        }
        value = System.currentTimeMillis()
    }
    val text: String
    val onClick: () -> Unit
    when {
        !connected -> {
            text = "Redirect service is off. Tap to set up"
            onClick = callbacks.openSetup
        }
        blocked -> {
            text = "The launcher could not come to the front. Tap for help"
            onClick = callbacks.openSetup
        }
        pausedUntil > now -> {
            text = "Redirect paused (${((pausedUntil - now) / 1000).coerceAtLeast(0)} s). Tap to resume"
            onClick = { RedirectStatus.resume() }
        }
        else -> return
    }
    Text(
        text = text,
        modifier = modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = TextStyle(fontFamily = HomeTheme.font, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
private fun StatusBarIcons(lightBackground: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = lightBackground
        controller.isAppearanceLightNavigationBars = lightBackground
    }
}

/**
 * True when the wallpaper is light enough that dark text reads better: from the launcher's own
 * image when it set the wallpaper, else from the system. Needs no permission.
 */
fun wallpaperIsLight(context: Context): Boolean = dev.liquid.launcher.core.data.ThemedColors.isLight(context)

/**
 * The screen that is open, for the frame meter's lines (0.10.4): the one in front wins. Read in a snapshot flow, so
 * the drawer's and search's springs never recompose anything for it.
 */
internal fun openScreenName(c: HomeController): String = when {
    c.wallpaperStudio -> "wallpaper screen"
    c.clockStudio != null -> "clock gallery"
    c.hub.value > 0.5f -> "study hub"
    c.tagSheet -> "tag list"
    c.openFolder != null -> "folder"
    c.searchOpen || c.search.value > 0.5f -> "search"
    c.drawer.value > 0.5f -> "drawer"
    c.editMode -> "editing"
    c.homeMenu -> "home menu"
    else -> "home"
}
