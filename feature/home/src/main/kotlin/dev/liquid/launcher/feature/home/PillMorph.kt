package dev.liquid.launcher.feature.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.layout.layout
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
import kotlin.math.roundToInt

private val SearchHeight = 54.dp
private val SearchSideMargin = 20.dp
private val SearchBottomMargin = 14.dp

/**
 * The home pill and the drawer's search field as one glass shape. As the drawer opens, the
 * pill grows into the search field (size, corners and height follow the drawer), its items
 * fade out and the search field fades in, so home and drawer read as one space.
 *
 * Geometry is set during layout from the drawer's position, so moving the drawer only
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
    val open by remember { derivedStateOf { controller.drawer.value >= 0.5f } }
    val showItems by remember { derivedStateOf { controller.drawer.value < 0.45f } }
    val showSearch by remember { derivedStateOf { controller.drawer.value > 0.02f } }
    val shape = remember(config.cornerPercent) {
        RoundedCornerShape(MorphCorner(config.cornerPercent, 50) { controller.drawer.value })
    }
    val gestures = remember(config, controller) { Modifier.morphGestures(config, controller) }
    val homeColor = if (glass.lightGlass(Surfaces.Pill, lightBackground)) Color(0xFF16181C) else Color.White
    val searchColor = if (glass.lightGlass(Surfaces.Drawer, drawerLight)) Color(0xFF16181C) else Color.White
    val surface = if (open) Surfaces.Drawer else Surfaces.Pill
    val homeWidth = with(density) { (controller.screenWidth * config.widthFraction).toDp() }
    val searchWidth = with(density) { controller.screenWidth.toDp() } - SearchSideMargin * 2
    val flexible = config.slots.any { it == PillSlot.Search || it == PillSlot.Spacer }

    Box(
        modifier
            .morphLayout(controller, config)
            .liquidGlass(backdrop, shape, glass.style(surface, if (open) drawerLight else lightBackground))
            .clip(shape)
            .then(if (interactive) gestures else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (showItems) {
            Row(
                Modifier
                    .requiredSize(homeWidth, config.heightDp.dp)
                    .graphicsLayer { alpha = (1f - controller.drawer.value * 2.6f).coerceIn(0f, 1f) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (flexible) Arrangement.spacedBy(4.dp) else Arrangement.SpaceEvenly,
            ) {
                for (slot in config.slots) PillSlotView(slot, config, homeColor, apps, controller, interactive && !open)
            }
        }
        if (showSearch) {
            DrawerSearchField(
                controller = controller,
                color = searchColor,
                autoKeyboard = drawer.autoKeyboard,
                interactive = interactive && open,
                modifier = Modifier
                    .requiredSize(searchWidth, SearchHeight)
                    .graphicsLayer { alpha = ((controller.drawer.value - 0.35f) / 0.65f).coerceIn(0f, 1f) },
            )
        }
    }
}

/** Corner size that follows the drawer: [from] percent on the home screen, [to] percent in the drawer. */
private class MorphCorner(private val from: Int, private val to: Int, private val progress: () -> Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val p = progress().coerceIn(0f, 1f)
        return shapeSize.minDimension * (from + (to - from) * p) / 100f
    }
}

/** Size and bottom margin between the pill's and the search field's, by drawer position. */
private fun Modifier.morphLayout(controller: HomeController, config: PillConfig): Modifier =
    layout { measurable, constraints ->
        val p = controller.drawer.value.coerceIn(0f, 1f)
        val full = constraints.maxWidth.toFloat()
        val width = lerp(full * config.widthFraction, full - SearchSideMargin.toPx() * 2, p)
        val height = lerp(config.heightDp.dp.toPx(), SearchHeight.toPx(), p)
        val bottom = lerp(config.offsetDp.dp.toPx(), SearchBottomMargin.toPx(), p)
        val placeable = measurable.measure(Constraints.fixed(width.roundToInt(), height.roundToInt()))
        layout(constraints.maxWidth, (height + bottom).roundToInt()) {
            placeable.place(((full - width) / 2f).roundToInt(), 0)
        }
    }

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * One set of gestures for both states, so a swipe that starts on the pill keeps working
 * after it has turned into the search field. Closed: the pill's own tap, hold and swipes.
 * Open: dragging moves the drawer.
 */
private fun Modifier.morphGestures(config: PillConfig, controller: HomeController): Modifier = this
    .pointerInput(config.tap, config.longPress) {
        detectTapGestures(
            onTap = { if (controller.drawer.value < 0.5f) controller.run(config.tap) },
            onLongPress = { if (controller.drawer.value < 0.5f) controller.run(config.longPress) },
        )
    }
    .pointerInput(config.swipeUp, config.swipeDown) {
        val tracker = VelocityTracker()
        var total = 0f
        var fromOpen = false
        val follow = config.swipeUp == LauncherAction.OpenDrawer
        detectVerticalDragGestures(
            onDragStart = {
                tracker.resetTracking()
                total = 0f
                fromOpen = controller.drawer.value >= 0.5f
            },
            onVerticalDrag = { change, dy ->
                tracker.addPointerInputChange(change)
                total += dy
                change.consume()
                if (fromOpen || (follow && (total < 0 || controller.drawer.value > 0f))) {
                    controller.dragDrawerBy(-dy / controller.screenHeight)
                }
            },
            onDragEnd = {
                val velocity = tracker.calculateVelocity().y
                val threshold = 40.dp.toPx()
                when {
                    fromOpen || (follow && controller.drawer.value > 0f) -> controller.settleDrawer(velocity)
                    total < -threshold || velocity < -1000f -> controller.run(config.swipeUp)
                    total > threshold || velocity > 1000f -> controller.run(config.swipeDown)
                }
            },
            onDragCancel = { if (fromOpen || follow) controller.settleDrawer(0f) },
        )
    }
