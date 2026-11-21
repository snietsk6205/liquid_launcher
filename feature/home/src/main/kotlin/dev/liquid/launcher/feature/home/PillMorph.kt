package dev.liquid.launcher.feature.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.AppRef
import dev.liquid.launcher.core.data.LauncherAction
import dev.liquid.launcher.core.data.PillSlot
import dev.liquid.launcher.core.designsystem.liquidGlass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private val SearchHeight = 54.dp
private val SearchSideMargin = 20.dp
private val SearchBottomMargin = 14.dp

/**
 * The home pill and the drawer's search field as one glass shape. As the drawer opens, the
 * pill grows into the search field (size, corners and height follow the drawer), its items
 * fade out and the search field fades in, so home and drawer read as one space.
 *
 * Since 0.7.1 (D104) it does the same for search: a swipe down grows the pill into the search
 * field at the bottom of the screen, which rides up with the keyboard; the results open above it.
 *
 * Geometry is set during layout from the drawer's (or search's) position, so moving them only
 * re-measures this one element.
 */
@Composable
fun MorphingPill(
    config: PillConfig,
    drawer: DrawerConfig,
    glass: GlassConfig,
    lightBackground: Boolean,
    drawerLight: Boolean,
    backdrop: Backdrop,
    apps: Map<AppRef, AppEntry>,
    controller: HomeController,
    interactive: Boolean,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val insets = rememberFieldInsets()
    val open by remember { derivedStateOf { controller.drawer.value >= 0.5f } }
    // Search grows the pill too (unless its field is at the top); the two are never open together.
    val searchMode by remember { derivedStateOf { controller.searchInPill && controller.search.value > controller.drawer.value && controller.search.value > 0.001f } }
    val searchOpen by remember { derivedStateOf { controller.search.value >= 0.5f } }
    val showItems by remember { derivedStateOf { controller.morph() < 0.45f } }
    val showSearch by remember { derivedStateOf { controller.morph() > 0.02f } }
    // Tabs inside the search pill make it taller; it then rounds less so the tabs keep their width.
    val bar = if (drawer.tabsInPill) controller.tabsBar else null
    val extra = if (bar != null) tabsRowHeight(drawer.tabsSize) else 0.dp
    val shape = remember(config.cornerPercent, bar != null) {
        RoundedCornerShape(MorphCorner(config.cornerPercent, if (bar != null) 30 else 50) { controller.morph() })
    }
    val gestures = remember(config, controller) { Modifier.morphGestures(config, controller) }
    // W3: the home pill's and the drawer pill's text in the theme's pill looks, else light or dark from the glass.
    val homeColor = pillContentColor(dev.liquid.launcher.core.data.ThemePill.HOME, if (glass.lightGlass(Surfaces.Pill, lightBackground)) Color(0xFF16181C) else Color.White)
    val searchColor = pillContentColor(dev.liquid.launcher.core.data.ThemePill.DRAWER, if (glass.lightGlass(Surfaces.Drawer, drawerLight)) Color(0xFF16181C) else Color.White, lightBehind = drawerLight)
    val surface = if (open && !searchMode) Surfaces.Drawer else Surfaces.Pill
    val homeWidth = with(density) { (controller.screenWidth * config.widthFraction).toDp() }
    val searchWidth = with(density) { controller.screenWidth.toDp() } - SearchSideMargin * 2
    val flexible = config.slots.any { it == PillSlot.Search || it == PillSlot.Spacer }
    val stretch = rememberLiquidStretch(controller, insets)

    Box(
        modifier
            .morphLayout(controller, config, if (searchMode) 0.dp else extra, insets)
            // The closed pill's place, which the study hub grows out of (0.9.1).
            .onGloballyPositioned {
                if (!interactive) return@onGloballyPositioned
                val m = controller.morph()
                if (m < 0.01f) controller.pillBounds = it.boundsInRoot()
                // W2: where the drawer's field sits, so the tag list over the drawer stops above it.
                controller.drawerPillTop = if (m > 0.99f && !searchMode) it.boundsInRoot().top else null
            }
            .graphicsLayer {
                // Search only (the drawer's morph stays as Fable made it): a drop stretching the way it travels.
                if (!(controller.searchInPill && controller.search.value > controller.drawer.value)) return@graphicsLayer
                val st = stretch.value
                scaleY = 1f + st
                scaleX = 1f - st * 0.55f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
            .morphPillSurface({ controller.morph() }, backdrop, shape, glass.style(surface, if (open) drawerLight else lightBackground))
            .clip(shape)
            .then(if (interactive) gestures else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (showItems) {
            Row(
                Modifier
                    .requiredSize(homeWidth, config.heightDp.dp)
                    .graphicsLayer { alpha = (1f - controller.morph() * 2.6f).coerceIn(0f, 1f) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (flexible) Arrangement.spacedBy(4.dp) else Arrangement.SpaceEvenly,
            ) {
                for (slot in config.slots) PillSlotView(slot, config, homeColor, apps, controller, interactive && !open && !searchOpen)
            }
        }
        if (showSearch && searchMode) {
            val fieldSize = controller.searchFieldSize
            SearchPillField(
                controller = controller,
                color = homeColor,
                interactive = interactive && searchOpen,
                canFocus = interactive,
                modifier = Modifier
                    .requiredSize(with(density) { (controller.screenWidth * fieldSize.widthShare).toDp() }, fieldSize.heightDp.dp)
                    .graphicsLayer { alpha = ((controller.search.value - 0.35f) / 0.65f).coerceIn(0f, 1f) },
            )
        } else if (showSearch) {
            androidx.compose.foundation.layout.Column(
                Modifier
                    .requiredSize(searchWidth, SearchHeight + extra)
                    .graphicsLayer { alpha = ((controller.drawer.value - 0.35f) / 0.65f).coerceIn(0f, 1f) },
            ) {
                if (bar != null) {
                    TabsRow(
                        bar, drawer.tabsSize, drawer.tabsIcons, searchColor, interactive && open,
                        Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp).requiredSize(searchWidth - 12.dp, extra - 6.dp),
                    )
                }
                DrawerSearchField(
                    controller = controller,
                    color = searchColor,
                    autoKeyboard = drawer.autoKeyboard,
                    interactive = interactive && open,
                    modifier = Modifier.requiredSize(searchWidth, SearchHeight),
                )
            }
        }
    }
}

/**
 * How much the morphing pill stretches (0.8.0, D115): from how fast search's field travels,
 * springing back with a wobble once it stops, like a drop that lands. Up to 0.14 either way.
 */
@Composable
private fun rememberLiquidStretch(controller: HomeController, insets: FieldInsets): androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val stretch = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(controller, insets) {
        var lastY = controller.fieldBottomPx
        var lastT = System.nanoTime()
        var smooth = 0f
        // 0.13.0 (D175): the stretch follows the pill's own speed (one motion now: the pill only rises with the
        // keyboard), smoothed so it swells and eases instead of following each frame, and it settles without a wobble
        // unless Bounce asks for one (it wobbled at 0.62 damping even with Bounce at 0).
        snapshotFlow { controller.search.value }.collectLatest {
            androidx.compose.runtime.withFrameNanos { }
            val y = controller.fieldBottomPx
            val t = System.nanoTime()
            val dt = ((t - lastT) / 1e9f).coerceIn(1f / 240f, 0.1f)
            val v = (y - lastY) / controller.screenHeight.coerceAtLeast(1f) / dt
            lastY = y
            lastT = t
            // Settings → Search, Liquid stretch and Bounce (0.8.1, D118).
            val amount = controller.data.settings.get(dev.liquid.launcher.core.settings.LauncherSettings.SearchStretch) / 100f
            val bounce = controller.data.settings.get(dev.liquid.launcher.core.settings.LauncherSettings.SearchBounce) / 100f
            if (amount <= 0f) return@collectLatest
            val target = (kotlin.math.abs(v) * 0.12f * amount).coerceIn(0f, 0.22f * amount)
            smooth += (target - smooth) * 0.3f
            stretch.snapTo(smooth)
            // No new position for a moment: it has landed, and settles (with a wobble only when Bounce asks for one).
            kotlinx.coroutines.delay(48)
            smooth = 0f
            stretch.animateTo(
                0f,
                androidx.compose.animation.core.spring(
                    dampingRatio = if (bounce <= 0.001f) 1f else (1f - 0.6f * bounce).coerceIn(0.35f, 1f),
                    stiffness = MotionScale.stiffness(420f),
                ),
            )
        }
    }
    return stretch
}

/** Corner size that follows the drawer: [from] percent on the home screen, [to] percent in the drawer. */
private class MorphCorner(private val from: Int, private val to: Int, private val progress: () -> Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val p = progress().coerceIn(0f, 1f)
        return shapeSize.minDimension * (from + (to - from) * p) / 100f
    }
}

/**
 * Size and distance from the bottom of the screen between the pill's and the search field's, by
 * the drawer's or search's position. The bottom includes the navigation bar and the keyboard
 * (read here, while laying out, so they never recompose anything).
 */
private fun Modifier.morphLayout(controller: HomeController, config: PillConfig, extra: androidx.compose.ui.unit.Dp, insets: FieldInsets): Modifier =
    layout { measurable, constraints ->
        val p = controller.morph().coerceIn(0f, 1f)
        val full = constraints.maxWidth.toFloat()
        val searchy = controller.searchInPill && controller.search.value > controller.drawer.value
        // 0.11.3: search's field has its own size and gap (Settings → Search → Layout); the drawer's keeps Fable's.
        val size = controller.searchFieldSize
        val openWidth = if (searchy) full * size.widthShare else full - SearchSideMargin.toPx() * 2
        val openHeight = if (searchy) size.heightDp.dp.toPx() else (SearchHeight + extra).toPx()
        val width = lerp(full * config.widthFraction, openWidth, p)
        val height = lerp(config.heightDp.dp.toPx(), openHeight, p)
        val gap = if (searchy) size.gapDp.dp.toPx() else SearchBottomMargin.toPx()
        val bottom = fieldBottom(controller, insets, p, config.offsetDp.dp.toPx(), gap, searchy)
        if (searchy) controller.fieldBottomPx = bottom
        val placeable = measurable.measure(Constraints.fixed(width.roundToInt(), height.roundToInt()))
        val total = (height + bottom).roundToInt().coerceAtMost(constraints.maxHeight.coerceAtLeast(1))
        layout(constraints.maxWidth, total) {
            placeable.place(((full - width) / 2f).roundToInt(), 0)
        }
    }

/**
 * The insets the pill and search's field sit on: navigation bar, keyboard now, and the keyboard
 * the running animation is heading for. Read while laying out or drawing.
 */
@androidx.compose.runtime.Stable
internal class FieldInsets(
    private val density: Density,
    private val nav: WindowInsets,
    private val ime: WindowInsets,
    private val target: WindowInsets,
) {
    fun nav(): Float = nav.getBottom(density).toFloat()
    fun ime(): Float = if (KeyboardLeaving.active) 0f else ime.getBottom(density).toFloat()
    fun imeTarget(): Float = if (KeyboardLeaving.active) 0f else target.getBottom(density).toFloat()
}

/**
 * 0.11.3 (D170): the launcher is leaving (paused) with nothing open, and a keyboard still on screen is only on its way
 * out (an app was started from search): the pill and the fields are laid out without it at once, so the picture the
 * system takes as the launcher leaves shows the screen as it will be on return. Set and cleared by the activity.
 */
object KeyboardLeaving {
    private val state = androidx.compose.runtime.mutableStateOf(false)
    var active: Boolean
        get() = state.value
        set(value) {
            state.value = value
        }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun rememberFieldInsets(): FieldInsets {
    val density = LocalDensity.current
    val nav = WindowInsets.navigationBars
    val ime = WindowInsets.ime
    val target = WindowInsets.imeAnimationTarget
    return remember(density, nav, ime, target) { FieldInsets(density, nav, ime, target) }
}

/**
 * Distance in px from the bottom of the screen to the bottom of the pill turning into a field
 * at [p] (0 = the pill, 1 = the field). For search the field rides on the keyboard (0.11.3: the
 * launcher slides the keyboard in with the pill, D169), never above it, and never dips below a
 * keyboard that is on screen.
 */
internal fun fieldBottom(controller: HomeController, insets: FieldInsets, p: Float, closedOffset: Float, openMargin: Float, searchy: Boolean): Float {
    val nav = insets.nav()
    val rise = controller.keyboardRise
    if (searchy && rise > 0f) {
        // 0.13.0 (D175): only up, from where the pill rests to just above the keyboard's height, on search's spring
        // (past 1 when it bounces). The keyboard rises under it in step ([keyboardTop]), so the two meet at the end
        // and the pill is never below the keyboard or above an empty gap.
        val rest = nav + closedOffset
        val bottom = rest + (maxOf(nav, rise) + openMargin - rest) * p.coerceAtLeast(0f)
        val ime = controller.searchFieldLine(nav, insets.ime(), insets.imeTarget())
        return maxOf(bottom, ime + openMargin, keyboardTop(controller, p) + openMargin)
    }
    val ime = if (searchy) controller.searchFieldLine(nav, insets.ime(), insets.imeTarget()) else insets.ime()
    val closed = maxOf(nav, ime) + closedOffset
    val bottom = lerp(closed, maxOf(nav, ime) + openMargin, p.coerceIn(0f, 1f))
    return if (ime > nav) maxOf(bottom, ime + openMargin) else bottom
}

/**
 * Where search's open field will end up (px from the bottom of the screen to its bottom): above the keyboard's
 * height once it is known, else above the height it usually opens at. Search lays its results out from here, so
 * they stay still while the keyboard slides in and only ride with the field.
 */
/** 0.13.0 (D175): the top of the rising keyboard (its picture until it is ready), px from the bottom: in step with search. */
internal fun keyboardTop(controller: HomeController, p: Float): Float =
    if (controller.keyboardRise > 0f) controller.keyboardRise * p.coerceIn(0f, 1f) else 0f

internal fun fieldBottomPlanned(controller: HomeController, insets: FieldInsets, openMargin: Float): Float =
    controller.searchFieldPlanned(insets.nav(), insets.ime(), insets.imeTarget()) + openMargin

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * One set of gestures for both states, so a swipe that starts on the pill keeps working
 * after it has turned into the search field. Closed: the pill's own tap, hold and swipes.
 * Open: dragging moves the drawer.
 */
private fun Modifier.morphGestures(config: PillConfig, controller: HomeController): Modifier = this
    .pointerInput(config.tap, config.longPress) {
        detectTapGestures(
            onTap = { if (controller.morph() < 0.5f) controller.run(config.tap) },
            onLongPress = { if (controller.morph() < 0.5f) controller.run(config.longPress) },
        )
    }
    .pointerInput(config.swipeUp, config.swipeDown) {
        val tracker = VelocityTracker()
        var total = 0f
        var fromOpen = false
        var fromSearch = false
        val follow = config.swipeUp == LauncherAction.OpenDrawer
        detectVerticalDragGestures(
            onDragStart = {
                tracker.resetTracking()
                total = 0f
                fromOpen = controller.drawer.value >= 0.5f
                fromSearch = controller.search.value >= 0.5f
            },
            onVerticalDrag = { change, dy ->
                tracker.addPointerInputChange(change)
                total += dy
                change.consume()
                when {
                    // The search field: pushing it up puts search away (it came down; D104).
                    fromSearch -> controller.dragSearchBy(dy / controller.screenHeight)
                    fromOpen || (follow && (total < 0 || controller.drawer.value > 0f)) -> controller.dragDrawerBy(-dy / controller.screenHeight)
                }
            },
            onDragEnd = {
                val velocity = tracker.calculateVelocity().y
                val threshold = 40.dp.toPx()
                when {
                    fromSearch -> controller.settleSearch(velocity)
                    fromOpen || (follow && controller.drawer.value > 0f) -> controller.settleDrawer(velocity)
                    total < -threshold || velocity < -1000f -> controller.run(config.swipeUp)
                    total > threshold || velocity > 1000f -> controller.run(config.swipeDown)
                }
            },
            onDragCancel = {
                when {
                    fromSearch -> controller.settleSearch(0f)
                    fromOpen || follow -> controller.settleDrawer(0f)
                }
            },
        )
    }
