package dev.liquid.launcher.feature.home

import android.app.ActivityOptions
import android.graphics.Rect as AndroidRect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.AppEntry
import dev.liquid.launcher.core.data.AppRef
import dev.liquid.launcher.core.data.HomeItem
import dev.liquid.launcher.core.designsystem.liquidGlass

/** Compose shape for a folder tile. "icons" follows the theme's icon shape. */
private fun folderShape(config: FolderConfig): androidx.compose.ui.graphics.Shape {
    val id = when (config.shape) {
        "icons" -> config.iconShape
        "circle" -> "circle"
        "squircle" -> "squircle"
        else -> "rounded"
    }
    if (id == "system" || id == "rounded") return RoundedCornerShape(28)
    return androidx.compose.foundation.shape.GenericShape { size, _ ->
        dev.liquid.launcher.core.data.IconShapes.path(id, size.minDimension)?.let { addPath(it.asComposePath()) }
    }
}

/** A folder on the home screen: its preview (grid, stack or first app), and its name if labels are on. */
@Composable
internal fun FolderIcon(item: HomeItem, env: PageEnv, config: FolderConfig, modifier: Modifier) {
    val apps = item.folder.orEmpty().take(4).mapNotNull { env.apps[it] }
    val shape = remember(config.shape, config.iconShape) { folderShape(config) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val tile = Modifier
                .squareFraction(env.grid.iconFraction)
                .then(
                    when (config.background) {
                        "glass" -> Modifier.liquidGlass(env.wallpaperBackdrop, RoundedCornerShape(28), env.glass.style(Surfaces.Menu, env.lightBackground)).clip(shape)
                        "accent" -> Modifier.clip(shape).background(HomeTheme.accent.copy(alpha = 0.6f))
                        "none" -> Modifier.clip(shape)
                        else -> Modifier.clip(shape).background(env.contentColor.copy(alpha = 0.18f))
                    }
                )
            when (config.preview) {
                "stack" -> Box(tile, contentAlignment = Alignment.Center) {
                    // Up to three icons fanned out, the first on top.
                    apps.take(3).reversed().forEachIndexed { i, app ->
                        val pos = apps.take(3).size - 1 - i
                        AppIcon(
                            app, true,
                            Modifier.fillMaxSize(0.62f).graphicsLayer {
                                rotationZ = (pos - 1) * -10f
                                translationX = (pos - 1) * size.width * -0.16f
                                translationY = pos * size.height * 0.05f
                            },
                            null,
                        )
                    }
                }
                "first" -> Box(tile.padding(6.dp)) {
                    apps.firstOrNull()?.let { AppIcon(it, true, Modifier.fillMaxSize(0.74f).align(Alignment.TopStart), null) }
                    Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        for (app in apps.drop(1).take(3)) AppIcon(app, true, Modifier.fillMaxSize(0.3f), null)
                    }
                }
                else -> Box(tile.padding(6.dp)) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (row in 0 until 2) {
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                for (col in 0 until 2) {
                                    val app = apps.getOrNull(row * 2 + col)
                                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                                        if (app != null) AppIcon(app, true, Modifier.fillMaxSize(), null)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (env.grid.labels) {
            Text(
                text = item.name ?: "Folder",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                style = TextStyle(fontFamily = HomeTheme.font, color = env.contentColor, fontSize = env.grid.labelSizeSp.sp, textAlign = TextAlign.Center, shadow = env.shadow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * An open folder: a glass card over a dimmed home screen. The name can be edited in place;
 * apps open with a tap and have the usual menu on a long press, which can take them out.
 */
@Composable
internal fun FolderOverlay(
    folder: HomeItem,
    apps: Map<AppRef, AppEntry>,
    controller: HomeController,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    grid: GridConfig,
) {
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    val shape = RoundedCornerShape(32.dp)
    val appear = remember(folder.id) { Animatable(0f) }
    LaunchedEffect(folder.id) { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 600f)) }
    var name by remember(folder.id) { mutableStateOf(folder.name ?: "Folder") }
    // However the folder closes (back, outside tap, opening an app), keep the typed name.
    val latestName by androidx.compose.runtime.rememberUpdatedState(name)
    androidx.compose.runtime.DisposableEffect(folder.id) {
        onDispose { controller.renameFolder(folder.id, latestName) }
    }
    val entries = folder.folder.orEmpty().mapNotNull { apps[it] }
    val columns = controller.folderConfig.columns
    val origin = remember(folder.id) { controller.folderOrigin }
    var card by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    val draggingOut = controller.drag?.source is DragSource.FolderApp
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (draggingOut) 0f else 1f }
            .background(Color.Black.copy(alpha = 0.25f))
            .pointerInput(folder.id) {
                detectTapGestures {
                    controller.renameFolder(folder.id, name)
                    controller.openFolder = null
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .onPlaced { if (it.isAttached) card = it.boundsInRoot() }
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    // Grows out of the folder that was tapped.
                    val bounds = card
                    if (origin != null && bounds != null && bounds.width > 0f && bounds.height > 0f) {
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                            ((origin.x - bounds.left) / bounds.width).coerceIn(-1f, 2f),
                            ((origin.y - bounds.top) / bounds.height).coerceIn(-1f, 2f),
                        )
                        scaleX = 0.25f + 0.75f * p
                        scaleY = 0.25f + 0.75f * p
                    } else {
                        scaleX = 0.88f + 0.12f * p
                        scaleY = 0.88f + 0.12f * p
                    }
                }
                .liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground))
                .clip(shape)
                // Taps inside the card do not close it.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(color),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { controller.renameFolder(folder.id, name) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            for (row in entries.chunked(columns)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    for (app in row) FolderApp(app, folder.id, color, grid, controller, Modifier.weight(1f))
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FolderApp(app: AppEntry, folderId: String, color: Color, grid: GridConfig, controller: HomeController, modifier: Modifier) {
    val holder = remember { CoordinatesHolder() }
    val view = LocalView.current
    val handler = remember(app, folderId) {
        object : IconGestureHandler {
            override fun onTap() {
                val bounds = holder.value?.takeIf { it.isAttached }?.boundsInRoot()
                val rect = bounds?.let { AndroidRect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
                val options = rect?.let { ActivityOptions.makeClipRevealAnimation(view, it.left, it.top, it.width(), it.height()).toBundle() }
                controller.openFolder = null
                controller.launch(app, rect, options)
            }

            override fun onLongPress() {
                val bounds = holder.value?.takeIf { it.isAttached }?.boundsInRoot() ?: return
                controller.menu = MenuTarget(app, null, bounds, folderId = folderId)
            }

            // Hold and move: the folder closes and the app follows the finger onto the home screen.
            override fun onDragStart(grab: Offset, finger: Offset) {
                val size = holder.value?.size ?: androidx.compose.ui.unit.IntSize.Zero
                controller.menu = null
                // The folder stays composed (invisible) until the drag ends, so this gesture keeps running.
                controller.startDrag(
                    DragState(
                        source = DragSource.FolderApp(folderId, app.ref),
                        icon = app.homeIcon,
                        art = app.homeArt,
                        widget = null,
                        cells = androidx.compose.ui.unit.IntSize(1, 1),
                        grab = grab,
                        size = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()),
                        start = finger,
                    )
                )
            }

            override fun onDrag(finger: Offset) = controller.moveDrag(finger)
            override fun onDragEnd() = controller.endDrag()
            override fun onDragCancel() = controller.cancelDrag()
        }
    }
    Column(
        modifier
            .onPlaced { holder.value = it }
            .iconGestures({ holder.value }, handler),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app, true, Modifier.size(52.dp), app.label)
        Text(
            app.label,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = grid.labelSizeSp.sp, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
