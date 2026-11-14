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
    var tick by mutableIntStateOf(0)
        private set
    var homePresses by mutableIntStateOf(0)
        private set

    /**
     * Call before the first frame after the activity becomes visible again. Setting the
     * progress here, not in an effect, means no frame is drawn with the content fully visible.
     */
    fun onReturn(animate: Boolean) {
        if (animate) progress.floatValue = 0f
        tick++
    }

    /** Home was pressed while the launcher was already in front. */
    fun onHomePressed() {
        homePresses++
    }

    /** Something another screen asked the home screen to open ("wallpaper"), or null. */
    var request by mutableStateOf<String?>(null)
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
enum class HomeMode { Live, Preview, PreviewDrawer, PreviewDrawerMotion, PreviewDrawerIndex, PreviewMenu, PreviewHomeMenu }

@Composable
fun HomeScreen(
    data: LauncherData,
    wallpaper: WallpaperStore,
    returnState: HomeReturnState,
    callbacks: HomeCallbacks,
    mode: HomeMode = HomeMode.Live,
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
    val dock by store.derive { dockConfig() }
    SideEffect { controller.folderConfig = folderConfig }
    val colourTick by data.colourTick.collectAsState()
    // Themed icon colours: icons read them while drawing (see IconPaint).
    val iconPalette by data.iconPalette.collectAsState()
    remember { IconPaint.init(data.iconPalette.value) }
    LaunchedEffect(iconPalette) { IconPaint.fromData(iconPalette, this) }
    if (live) IconPaintTiming()
    LaunchedEffect(theme, colourTick, savedImageKey(wallpaper)) { HomeTheme.apply(context, theme) }
    val glass by store.derive { glassConfig() }
    val search by store.derive { searchConfig() }
    val menuEntries by store.derive { MenuEntry.fromIds(get(LauncherSettings.MenuEntries)) }
    val handover by store.derive { get(LauncherSettings.SettleStyle) to get(LauncherSettings.SettleDuration) }
    val savedImage by wallpaper.image.collectAsState()
    // The wallpaper screen tries values out on the real home screen before they are applied.
    val studio = remember(controller.wallpaperStudio) { if (controller.wallpaperStudio && live) StudioState(store) else null }
    val image = studio?.candidateImage ?: savedImage
    val homeBlurDp = studio?.homeBlur ?: look.homeBlurDp
    val homeDim = (studio?.homeDim?.div(100f)) ?: look.homeDim
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
    if (studio != null) {
        // Preview the screen being adjusted.
        LaunchedEffect(studio.surface) {
            if (studio.surface == "drawer") controller.openDrawer() else if (controller.drawer.value > 0f) controller.closeDrawer()
        }
    }
    val menuSink by androidx.compose.animation.core.animateFloatAsState(
        if (controller.homeMenu && homeMenuConfig.sink) 1f else 0f, label = "menuSink",
    )

    val lightBackground = remember(savedImage, colourTick, studio?.candidateImage) {
        // A wallpaper being tried out decides the text colour of the preview too.
        studio?.rendered?.let { dev.liquid.launcher.core.common.WallpaperTones.fromBitmap(it)?.light } ?: wallpaperIsLight(context)
    }
    val contentColor = theme.textColor ?: if (lightBackground) Color(0xFF16181C) else Color.White
    val shadow = if (lightBackground) null else Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 4f)

    val density0 = LocalDensity.current
    val currentLayout by rememberUpdatedState(layout)
    controller.pager = rememberPagerState(initialPage = layout.defaultIndex) { currentLayout.pages.size.coerceAtLeast(1) }

    if (live) {
        StatusBarIcons(lightBackground)
        val overlay by remember { derivedStateOf { controller.anyOverlay } }
        BackHandler(enabled = overlay) { controller.back() }

        LaunchedEffect(returnState.tick) {
            val progress = returnState.progress
            if (progress.floatValue < 1f) {
                val (style, duration) = handover
                if (style == "none" || duration == 0) {
                    progress.floatValue = 1f
                } else {
                    animate(0f, 1f, animationSpec = tween(duration)) { value, _ -> progress.floatValue = value }
                }
            }
        }
        LaunchedEffect(returnState.request) {
            when (returnState.request) {
                "wallpaper" -> controller.run(LauncherAction.Wallpaper)
                "layouts" -> controller.run(LauncherAction.Layouts)
            }
            returnState.request = null
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
        val dragging = controller.drag != null
        LaunchedEffect(dragging) {
            if (dragging) controller.edgeScrollWhileDragging(with(density0) { 28.dp.toPx() })
        }
    }
    SideEffect { controller.motion = motion }
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

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged {
                controller.screenHeight = it.height.toFloat().coerceAtLeast(1f)
                controller.screenWidth = it.width.toFloat().coerceAtLeast(1f)
            }
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
        Box(Modifier.fillMaxSize().layerBackdrop(homeBackdrop)) {
          // Blurs wallpaper and pages together while the drawer is open.
          Box(
              Modifier
                  .fillMaxSize()
                  .graphicsLayer {
                      val p = controller.drawer.value.coerceIn(0f, 1f)
                      val searchP = if (studio?.surface == "search") 1f else controller.searchAmount
                      // A phone that only holds the frame rate with tint glass (glass benchmark) gets no blur here either.
                      val tint = glass.quality == GlassQuality.Tint
                      val drawerRadius = if (tint) 0f else motionShown.homeBlurDp.dp.toPx() * p
                      val searchRadius = if (tint) 0f else searchBlurDp.dp.toPx() * searchP
                      val radius = maxOf(drawerRadius, searchRadius)
                      renderEffect = if (radius >= 0.5f) BlurEffect(radius, radius, TileMode.Clamp) else null
                  }
          ) {
            val placing = studio?.takeIf { it.drawsSource }
            val shownWallpaper = placing?.sourceImage ?: blurredWallpaper ?: image
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
                )
            }
            if (homeDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = homeDim)))
            val pillSpace = (if (pill.enabled || controller.editMode) pill.heightDp + pill.offsetDp + 12 else 0) +
                (if (dock.enabled) dock.height.value.toInt() + 8 else 0)
            // Remembered: a new gesture lambda would restart the detector and cancel a swipe in progress.
            val swipes = remember(gestures, controller.editMode) { Modifier.homeSwipes(gestures, controller) }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = controller.drawer.value
                        val back = returnState.progress.floatValue
                        alpha = (1f - p.coerceIn(0f, 1f) * 1.6f).coerceIn(0f, 1f) * back
                        var scale = 1f - motion.homeScale * p
                        if (settle == "scale") scale *= 0.94f + 0.06f * back
                        if (controller.editMode) scale *= 0.9f
                        scale *= 1f - 0.06f * menuSink
                        scaleX = scale
                        scaleY = scale
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
        val barModifier = Modifier
            .align(if (pill.top) Alignment.TopCenter else Alignment.BottomCenter)
            .then(if (pill.top) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
            .padding(vertical = pill.offsetDp.dp)
            .graphicsLayer {
                val p = controller.drawer.value
                alpha = (1f - p * 2f).coerceIn(0f, 1f) * returnState.progress.floatValue
                translationY = p * 40.dp.toPx() * (if (pill.top) -1 else 1)
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
                        alpha = (1f - p * 2f).coerceIn(0f, 1f) * returnState.progress.floatValue
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .graphicsLayer { alpha = returnState.progress.floatValue },
            )
        }

        if (live && controller.searchOpen) {
            SearchOverlay(controller, search, glass, lightBackground, overlayBackdrop, apps)
        }
        controller.openFolder?.let { id ->
            layout.find(id)?.second?.takeIf { it.kind == ItemKind.FOLDER }?.let { folder ->
                FolderOverlay(folder, appIndex, controller, glass, lightBackground, overlayBackdrop, grid)
            }
        }
        controller.menu?.let { target ->
            AppMenu(target, menuEntries, controller, glass, lightBackground, overlayBackdrop)
        }
        if (mode == HomeMode.PreviewHomeMenu) SideEffect { controller.homeMenu = true }
        if ((live || mode == HomeMode.PreviewHomeMenu) && controller.homeMenu) {
            HomeMenu(homeMenuConfig, glass, lightBackground, overlayBackdrop, appIndex, controller)
        }
        if (live && controller.layoutsSheet) {
            LayoutsSheet(glass, lightBackground, overlayBackdrop, controller)
        }
        if (live && controller.wallpaperStrip) {
            WallpaperStrip(glass, lightBackground, overlayBackdrop, controller)
        }
        if (studio != null) {
            WallpaperStudio(studio, glass, lightBackground, overlayBackdrop, controller)
        }
        if (controller.savingLayout) SaveLayoutDialog(controller)
        controller.pageSettings?.let { PageSettingsDialog(it, controller) }
        controller.drag?.let { drag ->
            RemoveZone(drag, controller, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp))
            DragGhost(drag)
        }
        controller.renaming?.let { RenameDialog(it, controller) }
        controller.tagging?.let { TagsDialog(it, controller) }
        controller.iconPicking?.let { IconPickerDialog(it, controller) }
        controller.swipeEditing?.let { id -> layout.find(id)?.let { (_, item) -> SwipeActionsDialog(item, apps, controller) } }
        if (live) TransitionOverlay(controller, transitionStyle)
        if (live) FlashLabel(controller, glass, lightBackground, overlayBackdrop, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 14.dp))
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

/** Swipes on the home screen. A swipe up that opens the drawer moves it with the finger. */
private fun Modifier.homeSwipes(gestures: GestureConfig, controller: HomeController): Modifier =
    pointerInput(gestures, controller.editMode) {
        if (controller.editMode) return@pointerInput
        val tracker = VelocityTracker()
        var total = 0f
        val follow = gestures.swipeUp == LauncherAction.OpenDrawer
        detectVerticalDragGestures(
            onDragStart = {
                tracker.resetTracking()
                total = 0f
                controller.menu = null
            },
            onVerticalDrag = { change, dy ->
                tracker.addPointerInputChange(change)
                total += dy
                change.consume()
                if (follow && (total < 0 || controller.drawer.value > 0f)) {
                    controller.dragDrawerBy(-dy / controller.screenHeight)
                }
            },
            onDragEnd = {
                val velocity = tracker.calculateVelocity().y
                val threshold = 48.dp.toPx()
                DebugLog.event("Home", "swipe: " + total.toInt() + " px, " + velocity.toInt() + " px/s")
                when {
                    follow && controller.drawer.value > 0f -> controller.settleDrawer(velocity)
                    total < -threshold || velocity < -1200f -> controller.run(gestures.swipeUp)
                    total > threshold || velocity > 1200f -> controller.run(gestures.swipeDown)
                }
            },
            onDragCancel = { if (follow) controller.settleDrawer(0f) },
        )
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
        ToolButton(LauncherIcons.Schedule, "Add clock", color) { controller.addClock() }
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
