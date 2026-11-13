package dev.liquid.launcher.feature.home

import android.app.ActivityOptions
import android.graphics.Rect as AndroidRect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.AppRef
import dev.liquid.launcher.core.data.HomeItem
import dev.liquid.launcher.core.data.HomePage
import dev.liquid.launcher.core.data.ItemKind
import dev.liquid.launcher.core.data.LauncherAction
import kotlin.math.min
import kotlin.math.roundToInt

/** Holds a node's coordinates without causing recomposition when they change. */
class CoordinatesHolder {
    var value: LayoutCoordinates? = null
}

/** What every page needs from the screen around it. */
class PageEnv(
    val grid: GridConfig,
    val clock: ClockConfig,
    val gestures: GestureConfig,
    val glass: GlassConfig,
    val lightBackground: Boolean,
    val contentColor: Color,
    val shadow: Shadow?,
    val apps: Map<AppRef, AppEntry>,
    val wallpaperBackdrop: Backdrop,
    val editMode: Boolean,
    val interactive: Boolean,
)

@Composable
fun HomePageView(index: Int, page: HomePage, env: PageEnv, controller: HomeController) {
    Box(Modifier.fillMaxSize()) {
        // Empty space: double tap and long press. Icons sit above and take their own touches.
        if (env.interactive) {
            val gestures = env.gestures
            val editMode = env.editMode
            // Remembered so a recomposition between two taps does not restart the detector.
            val taps = remember(gestures, editMode, controller) {
                Modifier.pointerInput(gestures, editMode) {
                    detectTapGestures(
                        onTap = { if (editMode) controller.exitEdit() },
                        onDoubleTap = if (editMode || gestures.doubleTap == LauncherAction.None) {
                            null
                        } else {
                            { controller.run(gestures.doubleTap) }
                        },
                        onLongPress = { if (!editMode) controller.run(gestures.longPress) },
                    )
                }
            }
            Box(Modifier.fillMaxSize().then(taps))
        }
        val columns = env.grid.columns
        val rows = env.grid.rows
        Layout(
            content = {
                for (item in page.items) {
                    key(item.id) { ItemView(item, env, controller) }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onPlaced { controller.pageGrids[index] = it }
                .drawBehind {
                    val cellW = size.width / columns
                    val cellH = size.height / rows
                    if (env.editMode) {
                        val outline = env.contentColor.copy(alpha = 0.14f)
                        for (y in 0 until rows) for (x in 0 until columns) {
                            drawRoundRect(
                                outline, Offset(x * cellW + 3, y * cellH + 3), Size(cellW - 6, cellH - 6),
                                CornerRadius(14.dp.toPx()), style = Stroke(1.dp.toPx()),
                            )
                        }
                    }
                    val drag = controller.drag ?: return@drawBehind
                    val target = controller.previewCell() ?: return@drawBehind
                    if (target.first != index || drag.overRemove) return@drawBehind
                    drawRoundRect(
                        env.contentColor.copy(alpha = 0.22f),
                        Offset(target.second * cellW + 4, target.third * cellH + 4),
                        Size(drag.cells.width * cellW - 8, drag.cells.height * cellH - 8),
                        CornerRadius(16.dp.toPx()),
                    )
                },
        ) { measurables, constraints ->
            val cellW = constraints.maxWidth.toFloat() / columns
            val cellH = constraints.maxHeight.toFloat() / rows
            val placeables = measurables.mapIndexed { i, m ->
                val item = page.items[i]
                val w = (item.w.coerceAtMost(columns) * cellW).roundToInt()
                val h = (item.h.coerceAtMost(rows) * cellH).roundToInt()
                m.measure(Constraints.fixed(w, h))
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { i, p ->
                    val item = page.items[i]
                    p.place((item.x * cellW).roundToInt(), (item.y * cellH).roundToInt())
                }
            }
        }
    }
}

@Composable
private fun ItemView(item: HomeItem, env: PageEnv, controller: HomeController) {
    val holder = remember { CoordinatesHolder() }
    val beingDragged = (controller.drag?.source as? DragSource.Home)?.itemId == item.id
    val base = Modifier
        .fillMaxSize()
        .onPlaced { holder.value = it }
        .graphicsLayer { alpha = if (beingDragged) 0f else 1f }
    when (item.kind) {
        ItemKind.APP -> {
            val app = item.app?.let { env.apps[it] }
            if (app == null) {
                // Uninstalled or not loaded yet. Always emit one node per item (the grid layout
                // places children by index); show a placeholder only while editing.
                Box(base.iconGestures({ holder.value }, dragOnly(item, null, holder, controller), env.interactive && env.editMode)) {
                    if (env.editMode) {
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .squareFraction(env.grid.iconFraction)
                                .background(env.contentColor.copy(alpha = 0.2f), CircleShape)
                        )
                    }
                }
                return
            }
            AppIconView(
                app = app,
                iconFraction = env.grid.iconFraction,
                label = if (env.grid.labels) app.label else null,
                labelSizeSp = env.grid.labelSizeSp,
                color = env.contentColor,
                shadow = env.shadow,
                modifier = base.iconGestures({ holder.value }, appHandler(item, app, holder, controller), env.interactive),
            )
        }

        ItemKind.WIDGET -> {
            Box(base.iconGestures({ holder.value }, dragOnly(item, null, holder, controller), env.interactive)) {
                if (item.widget == "clock") {
                    ClockWidget(env.clock, env.contentColor, env.shadow, env.glass, env.lightBackground, env.wallpaperBackdrop)
                }
            }
        }
    }
}

/** Icon and optional label, centred in whatever space it is given. */
@Composable
fun AppIconView(
    app: AppEntry,
    iconFraction: Float,
    label: String?,
    labelSizeSp: Int,
    color: Color,
    shadow: Shadow?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AppIconImage(app.icon, Modifier.squareFraction(iconFraction), app.label)
        }
        if (label != null) {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                style = TextStyle(color = color, fontSize = labelSizeSp.sp, textAlign = TextAlign.Center, shadow = shadow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AppIconImage(icon: ImageBitmap?, modifier: Modifier, description: String?) {
    if (icon != null) {
        Image(bitmap = icon, contentDescription = description, modifier = modifier)
    } else {
        Box(modifier.background(Color.Gray.copy(alpha = 0.3f), CircleShape))
    }
}

/** A square filling [fraction] of the smaller side of the available space. */
fun Modifier.squareFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val side = (min(constraints.maxWidth, constraints.maxHeight) * fraction).roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { placeable.place(0, 0) }
}

@Composable
private fun appHandler(item: HomeItem, app: AppEntry, holder: CoordinatesHolder, controller: HomeController): IconGestureHandler {
    val view = LocalView.current
    return remember(item, app, controller) {
        object : IconGestureHandler {
            override fun onTap() {
                if (controller.editMode) return
                val bounds = holder.value?.takeIf { it.isAttached }?.boundsInRoot()
                val rect = bounds?.let { AndroidRect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
                val options = rect?.let {
                    ActivityOptions.makeClipRevealAnimation(view, it.left, it.top, it.width(), it.height()).toBundle()
                }
                controller.launch(app, rect, options)
            }

            override fun onLongPress() {
                val bounds = holder.value?.takeIf { it.isAttached }?.boundsInRoot() ?: return
                controller.menu = MenuTarget(app, item.id, bounds)
            }

            override fun onDragStart(grab: Offset, finger: Offset) = startHomeDrag(item, app.icon, grab, finger, holder, controller)
            override fun onDrag(finger: Offset) = controller.moveDrag(finger)
            override fun onDragEnd() = controller.endDrag()
            override fun onDragCancel() = controller.cancelDrag()
        }
    }
}

@Composable
private fun dragOnly(item: HomeItem, icon: ImageBitmap?, holder: CoordinatesHolder, controller: HomeController): IconGestureHandler =
    remember(item, controller) {
        object : IconGestureHandler {
            override fun onTap() = Unit
            override fun onLongPress() {
                if (!controller.editMode) controller.run(LauncherAction.EditHome)
            }

            override fun onDragStart(grab: Offset, finger: Offset) = startHomeDrag(item, icon, grab, finger, holder, controller)
            override fun onDrag(finger: Offset) = controller.moveDrag(finger)
            override fun onDragEnd() = controller.endDrag()
            override fun onDragCancel() = controller.cancelDrag()
        }
    }

private fun startHomeDrag(
    item: HomeItem,
    icon: ImageBitmap?,
    grab: Offset,
    finger: Offset,
    holder: CoordinatesHolder,
    controller: HomeController,
) {
    val size = holder.value?.size ?: IntSize.Zero
    controller.startDrag(
        DragState(
            source = DragSource.Home(item.id),
            icon = icon,
            widget = item.widget,
            cells = IntSize(item.w, item.h),
            grab = grab,
            size = Size(size.width.toFloat(), size.height.toFloat()),
            start = finger,
        )
    )
}
