package dev.liquid.launcher.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.HomeItem
import dev.liquid.launcher.core.data.HomeLayout
import dev.liquid.launcher.core.data.ItemKind
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.LauncherSettings
import kotlin.math.roundToInt

// ---- widget resize ----

private enum class Edge { Left, Top, Right, Bottom }

/**
 * Frame with a handle on each edge of a selected widget. Dragging a handle shows the new
 * area on the page in whole cells; letting go applies it if the cells are free.
 */
@Composable
internal fun BoxScope.ResizeFrame(item: HomeItem, pageIndex: Int, env: PageEnv, controller: HomeController) {
    val color = env.contentColor
    Box(
        Modifier
            .matchParentSize()
            .padding(4.dp)
            .drawBehind {
                drawRoundRect(color.copy(alpha = 0.85f), style = Stroke(2.dp.toPx()), cornerRadius = CornerRadius(20.dp.toPx()))
            }
    )
    Handle(Edge.Top, Alignment.TopCenter, item, pageIndex, env, controller)
    Handle(Edge.Bottom, Alignment.BottomCenter, item, pageIndex, env, controller)
    Handle(Edge.Left, Alignment.CenterStart, item, pageIndex, env, controller)
    Handle(Edge.Right, Alignment.CenterEnd, item, pageIndex, env, controller)
    if (item.widget == "clock") {
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { controller.openSection(LauncherSettings.Sections.Clock.id) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherIcon(LauncherIcons.Tune, contentDescription = null, tint = Color.White, size = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text("Clock settings", style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun BoxScope.Handle(edge: Edge, alignment: Alignment, item: HomeItem, pageIndex: Int, env: PageEnv, controller: HomeController) {
    val columns = env.grid.columns
    val rows = env.grid.rows
    Box(
        Modifier
            .align(alignment)
            .size(36.dp)
            .pointerInput(item, edge, columns, rows) {
                var dx = 0f
                var dy = 0f
                fun cell(): Size {
                    val grid = controller.pageGrids[pageIndex]?.size ?: return Size(1f, 1f)
                    return Size(grid.width / columns.toFloat(), grid.height / rows.toFloat())
                }
                detectDragGestures(
                    onDragStart = {
                        dx = 0f
                        dy = 0f
                        controller.resizing = ResizePreview(item.id, item.x, item.y, item.w, item.h)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dx += amount.x
                        dy += amount.y
                        val c = cell()
                        val cx = (dx / c.width).roundToInt()
                        val cy = (dy / c.height).roundToInt()
                        var x = item.x
                        var y = item.y
                        var w = item.w
                        var h = item.h
                        when (edge) {
                            Edge.Left -> {
                                x = (item.x + cx).coerceIn(0, item.x + item.w - 1)
                                w = item.w + item.x - x
                            }
                            Edge.Right -> w = (item.w + cx).coerceIn(1, columns - item.x)
                            Edge.Top -> {
                                y = (item.y + cy).coerceIn(0, item.y + item.h - 1)
                                h = item.h + item.y - y
                            }
                            Edge.Bottom -> h = (item.h + cy).coerceIn(1, rows - item.y)
                        }
                        controller.resizing = ResizePreview(item.id, x, y, w, h)
                    },
                    onDragEnd = {
                        controller.resizing?.let { controller.commitResize(it) }
                        controller.resizing = null
                    },
                    onDragCancel = { controller.resizing = null },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (edge == Edge.Top || edge == Edge.Bottom) 28.dp else 8.dp, if (edge == Edge.Top || edge == Edge.Bottom) 8.dp else 28.dp)
                .background(env.contentColor, RoundedCornerShape(50))
                .border(1.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(50))
        )
    }
}

// ---- pages ----

/**
 * Edit mode's page overview: a miniature of every page. Tap one to go there; move it left
 * or right, make it the page Home returns to, or remove it.
 */
@Composable
internal fun PagesSheet(
    layout: HomeLayout,
    grid: GridConfig,
    current: Int,
    controller: HomeController,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    val color = if (glass.lightGlass(Surfaces.Pill, lightBackground)) Color(0xFF16181C) else Color.White
    Column(
        modifier
            .fillMaxWidth(0.94f)
            .liquidGlass(backdrop, shape, glass.style(Surfaces.Pill, lightBackground))
            .clip(shape)
            .padding(vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Pages", style = TextStyle(color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text(
                "Home goes to the page with the house",
                style = TextStyle(color = color.copy(alpha = 0.6f), fontSize = 12.sp),
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(layout.pages, key = { _, page -> page.id }) { index, page ->
                PageCard(
                    items = page.items,
                    grid = grid,
                    color = color,
                    current = index == current,
                    isDefault = index == layout.defaultIndex,
                    canLeft = index > 0,
                    canRight = index < layout.pages.lastIndex,
                    canRemove = layout.pages.size > 1,
                    onOpen = { controller.goToPage(index) },
                    onLeft = { controller.movePage(index, index - 1) },
                    onRight = { controller.movePage(index, index + 1) },
                    onDefault = { controller.setDefaultPage(index) },
                    onRemove = { controller.removePage(index) },
                )
            }
            item(key = "add") {
                Box(
                    Modifier
                        .size(MiniWidth, MiniWidth * 1.9f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, color.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable { controller.addPage() },
                    contentAlignment = Alignment.Center,
                ) {
                    LauncherIcon(LauncherIcons.Add, contentDescription = "Add page", tint = color, size = 28.dp)
                }
            }
        }
    }
}

private val MiniWidth = 78.dp

@Composable
private fun PageCard(
    items: List<HomeItem>,
    grid: GridConfig,
    color: Color,
    current: Boolean,
    isDefault: Boolean,
    canLeft: Boolean,
    canRight: Boolean,
    canRemove: Boolean,
    onOpen: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onDefault: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(MiniWidth, MiniWidth * 1.9f)
                .clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = if (current) 0.16f else 0.07f))
                .border(if (current) 2.dp else 1.dp, color.copy(alpha = if (current) 0.9f else 0.3f), RoundedCornerShape(14.dp))
                .clickable(onClick = onOpen)
                .padding(6.dp)
        ) {
            val cw = size.width / grid.columns
            val ch = size.height / grid.rows
            for (item in items) {
                val topLeft = Offset(item.x * cw + 1.5f, item.y * ch + 1.5f)
                val area = Size(item.w * cw - 3f, item.h * ch - 3f)
                if (item.kind == ItemKind.APP) {
                    drawCircle(color.copy(alpha = 0.75f), radius = minOf(cw, ch) * 0.36f, center = topLeft + Offset(area.width / 2, area.height / 2))
                } else {
                    drawRoundRect(color.copy(alpha = 0.45f), topLeft, area, CornerRadius(6.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MiniButton(LauncherIcons.ArrowUp, "Move left", color, enabled = canLeft, rotation = -90f, onClick = onLeft)
            MiniButton(LauncherIcons.Home, "Home page", color.copy(alpha = if (isDefault) 1f else 0.45f), enabled = !isDefault, filled = isDefault, onClick = onDefault)
            MiniButton(LauncherIcons.ArrowUp, "Move right", color, enabled = canRight, rotation = 90f, onClick = onRight)
        }
        if (canRemove) {
            MiniButton(LauncherIcons.Delete, "Remove page", color.copy(alpha = 0.7f), enabled = true, onClick = onRemove)
        }
    }
}

@Composable
private fun MiniButton(
    icon: Int,
    label: String,
    color: Color,
    enabled: Boolean,
    rotation: Float = 0f,
    filled: Boolean = false,
    size: Dp = 26.dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) color.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { rotationZ = rotation }) {
            LauncherIcon(icon, contentDescription = label, tint = color.copy(alpha = if (enabled || filled) color.alpha else 0.25f), size = 16.dp)
        }
    }
}
