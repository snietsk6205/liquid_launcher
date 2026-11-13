package dev.liquid.launcher.feature.home

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.data.LauncherData
import dev.liquid.launcher.core.data.MenuEntry
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
}

/** How the home screen is shown: the real thing, or a miniature inside the settings app. */
enum class HomeMode { Live, Preview, PreviewDrawer, PreviewMenu }

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
    val glass by store.derive { glassConfig() }
    val search by store.derive { searchConfig() }
    val menuEntries by store.derive { MenuEntry.fromIds(get(LauncherSettings.MenuEntries)) }
    val handover by store.derive { get(LauncherSettings.SettleStyle) to get(LauncherSettings.SettleDuration) }
    val image by wallpaper.image.collectAsState()

    val lightBackground = remember(image) { wallpaperIsLight(context) }
    val contentColor = if (lightBackground) Color(0xFF16181C) else Color.White
    val shadow = if (lightBackground) null else Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 4f)

    val density0 = LocalDensity.current
    val currentLayout by rememberUpdatedState(layout)
    controller.pager = rememberPagerState { currentLayout.pages.size.coerceAtLeast(1) }

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
        LaunchedEffect(returnState.homePresses) {
            if (returnState.homePresses == 0) return@LaunchedEffect
            val wasOpen = controller.anyOverlay
            controller.closeAll()
            if (controller.editMode) controller.exitEdit()
            if (!wasOpen && controller.pager.currentPage != 0) controller.pager.animateScrollToPage(0)
        }
    }
    if (live) {
        val dragging = controller.drag != null
        LaunchedEffect(dragging) {
            if (dragging) controller.edgeScrollWhileDragging(with(density0) { 28.dp.toPx() })
        }
    }
    if (mode == HomeMode.PreviewDrawer) {
        LaunchedEffect(Unit) { controller.drawer.snapTo(1f) }
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
    val overlayBackdrop = rememberCombinedBackdrop(homeBackdrop)
    val density = LocalDensity.current
    val settle = handover.first

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged {
                controller.screenHeight = it.height.toFloat().coerceAtLeast(1f)
                controller.screenWidth = it.width.toFloat().coerceAtLeast(1f)
            }
    ) {
        // ---- Home: wallpaper and pages, recorded so glass surfaces can refract them ----
        Box(Modifier.fillMaxSize().layerBackdrop(homeBackdrop)) {
            image?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().layerBackdrop(wallpaperBackdrop),
                    contentScale = ContentScale.Crop,
                )
            }
            val pillSpace = if (pill.enabled || controller.editMode) pill.heightDp + pill.offsetDp + 12 else 0
            // Remembered: a new gesture lambda would restart the detector and cancel a swipe in progress.
            val swipes = remember(gestures, controller.editMode) { Modifier.homeSwipes(gestures, controller) }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = controller.drawer.value
                        val back = returnState.progress.floatValue
                        alpha = (1f - p * 1.6f).coerceIn(0f, 1f) * back
                        var scale = 1f - 0.06f * p
                        if (settle == "scale") scale *= 0.94f + 0.06f * back
                        if (controller.editMode) scale *= 0.9f
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
                    if (page != null) HomePageView(index, page, env, controller)
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
            EditToolbar(controller, glass, lightBackground, homeBackdrop, pill, barModifier)
        } else if (pill.enabled) {
            Pill(pill, glass, lightBackground, homeBackdrop, appIndex, controller, live, barModifier)
        }

        if (live) {
            StatusChips(contentColor, callbacks, Modifier.align(Alignment.TopCenter).statusBarsPadding())
        }

        // ---- Overlays ----
        AppDrawer(controller, drawerConfig, glass, lightBackground, apps, homeBackdrop, live)

        if (live && controller.searchOpen) {
            SearchOverlay(controller, search, glass, lightBackground, overlayBackdrop, apps)
        }
        controller.menu?.let { target ->
            AppMenu(target, menuEntries, controller, glass, lightBackground, overlayBackdrop)
        }
        controller.drag?.let { drag ->
            RemoveZone(drag, controller, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp))
            DragGhost(drag)
        }
        controller.renaming?.let { RenameDialog(it, controller) }
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
                tracker.addPosition(change.uptimeMillis, change.position)
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
        ToolButton(LauncherIcons.Add, "Add page", color) { controller.addPage() }
        ToolButton(LauncherIcons.Delete, "Remove page", color) { controller.removeCurrentPage() }
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
    val label = if (drag.source is DragSource.Home) "Remove" else "Cancel"
    Row(
        modifier
            .onGloballyPositioned { controller.removeZone = it.boundsInRoot().inflate(24f) }
            .clip(RoundedCornerShape(50))
            .background(if (drag.overRemove) Color(0xFFD64545) else Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherIcon(if (drag.source is DragSource.Home) LauncherIcons.Delete else LauncherIcons.Close, contentDescription = null, tint = Color.White, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium))
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
            AppIconImage(drag.icon, Modifier.squareFraction(0.75f), null)
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
        style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium),
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

/** True when the wallpaper is light enough that dark text reads better. Needs no permission. */
fun wallpaperIsLight(context: Context): Boolean = try {
    val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
    colors != null && (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
} catch (e: Exception) {
    false
}
