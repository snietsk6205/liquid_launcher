package dev.liquid.launcher.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.ItemKind
import dev.liquid.launcher.core.data.SavedLayout
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.LauncherSettings

/** Grid size stored in a saved layout's settings, or the defaults. */
private fun SavedLayout.gridSize(): Pair<Int, Int> {
    fun int(key: String, default: Int) = settings[key]?.removePrefix("i:")?.toIntOrNull() ?: default
    return int(LauncherSettings.HomeColumns.key, LauncherSettings.HomeColumns.default) to int(LauncherSettings.HomeRows.key, LauncherSettings.HomeRows.default)
}

/** Saved layouts in a glass sheet: tap one to switch, hold it for more. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LayoutsSheet(
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    controller: HomeController,
) {
    val state by controller.data.layouts.state.collectAsState()
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    val shape = RoundedCornerShape(32.dp)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 520f)) }
    var holding by remember { mutableStateOf<SavedLayout?>(null) }
    var renaming by remember { mutableStateOf<SavedLayout?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controller.layoutsSheet = false },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp)
                .graphicsLayer {
                    alpha = appear.value.coerceIn(0f, 1f)
                    translationY = (1f - appear.value) * 40.dp.toPx()
                }
                .liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground))
                .clip(shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(vertical = 16.dp),
        ) {
            Text(
                "Layouts",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(12.dp))
            if (state.layouts.isEmpty()) {
                Text(
                    "Save this home screen as a layout (pages, pill, icons, gestures and optionally the wallpaper), " +
                        "then set up another one and switch between them here or from the home menu.",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.8f), fontSize = 14.sp),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for (layout in state.layouts) {
                        val current = layout.id == state.current
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = {
                                        controller.layoutsSheet = false
                                        if (!current) controller.switchLayout(layout.id)
                                    },
                                    onLongClick = { holding = layout },
                                )
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            LayoutMiniature(layout, color, current)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                layout.name,
                                modifier = Modifier.width(78.dp),
                                style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 13.sp, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (layout.wallpaper != null) {
                                LauncherIcon(LauncherIcons.Image, contentDescription = "Has its own wallpaper", tint = color.copy(alpha = 0.6f), size = 14.dp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetChip("Save as new…", LauncherIcons.Save, color) { controller.savingLayout = true }
                if (state.current != null) {
                    SheetChip("Save changes", LauncherIcons.Check, color) {
                        controller.data.layouts.updateCurrent()
                        controller.toast("Layout saved")
                    }
                }
                if (controller.data.layouts.canUndoSwitch) {
                    SheetChip("Undo switch", LauncherIcons.Undo, color) {
                        controller.data.layouts.undoSwitch()
                        controller.layoutsSheet = false
                    }
                }
            }
        }
    }

    holding?.let { layout ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { holding = null },
            title = { Text(layout.name) },
            text = {
                Column {
                    DialogRow("Rename") { renaming = layout; holding = null }
                    DialogRow("Duplicate") { controller.data.layouts.duplicate(layout.id); holding = null }
                    DialogRow(if (layout.wallpaper != null) "Stop switching the wallpaper with it" else "Give it the current wallpaper") {
                        val file = if (layout.wallpaper != null) null else WallpaperStore.get(context).currentFile
                        if (layout.wallpaper == null && file == null) controller.toast("Pick a wallpaper in the launcher first")
                        controller.data.layouts.setWallpaper(layout.id, file, controller.data.wallpapers.get().current)
                        holding = null
                    }
                    DialogRow("Add it to the home menu") {
                        val key = LauncherSettings.HomeMenuSetup
                        val encoded = dev.liquid.launcher.core.data.LauncherAction.SwitchLayout(layout.id).encode()
                        val current = controller.data.settings.get(key)
                        if (encoded !in current) controller.data.settings.set(key, current + encoded)
                        controller.toast("Added to the home menu")
                        holding = null
                    }
                    DialogRow("Delete") { controller.data.layouts.delete(layout.id); holding = null }
                }
            },
            confirmButton = { TextButton(onClick = { holding = null }) { Text("Close") } },
        )
    }
    renaming?.let { layout ->
        var name by remember(layout) { mutableStateOf(layout.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename layout") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { controller.data.layouts.rename(layout.id, name); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DialogRow(label: String, onClick: () -> Unit) {
    Text(label, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), style = TextStyle(fontFamily = HomeTheme.font, fontSize = 16.sp))
}

@Composable
private fun SheetChip(label: String, icon: Int, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherIcon(icon, contentDescription = null, tint = color, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}

/** The layout's home page drawn small: dots for apps, blocks for widgets and folders. */
@Composable
private fun LayoutMiniature(layout: SavedLayout, color: Color, current: Boolean) {
    val (columns, rows) = layout.gridSize()
    val page = layout.layout.pages.getOrNull(layout.layout.defaultIndex)
    val shape = RoundedCornerShape(12.dp)
    Canvas(
        Modifier
            .size(width = 78.dp, height = 140.dp)
            .clip(shape)
            .background(color.copy(alpha = if (current) 0.16f else 0.07f))
            .border(if (current) 2.dp else 1.dp, color.copy(alpha = if (current) 0.9f else 0.3f), shape)
            .padding(6.dp)
    ) {
        val cw = size.width / columns
        val ch = size.height / rows
        for (item in page?.items.orEmpty()) {
            val topLeft = Offset(item.x * cw + 1.5f, item.y * ch + 1.5f)
            val area = Size(item.w * cw - 3f, item.h * ch - 3f)
            if (item.kind == ItemKind.APP) {
                drawCircle(color.copy(alpha = 0.75f), radius = minOf(cw, ch) * 0.36f, center = topLeft + Offset(area.width / 2, area.height / 2))
            } else {
                drawRoundRect(color.copy(alpha = 0.45f), topLeft, area, CornerRadius(5.dp.toPx()))
            }
        }
    }
}

/** Name the current setup and save it as a new layout. */
@Composable
internal fun SaveLayoutDialog(controller: HomeController) {
    val context = LocalContext.current
    val count = controller.data.layouts.state.collectAsState().value.layouts.size
    var name by remember { mutableStateOf(if (count == 0) "Main" else "Layout ${count + 1}") }
    var withWallpaper by remember { mutableStateOf(false) }
    val wallpaper = remember { WallpaperStore.get(context) }
    AlertDialog(
        onDismissRequest = { controller.savingLayout = false },
        title = { Text("Save layout") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name, for example Study") })
                Row(Modifier.fillMaxWidth().clickable { withWallpaper = !withWallpaper }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = withWallpaper, onCheckedChange = { withWallpaper = it }, enabled = wallpaper.hasManagedWallpaper)
                    Text("Switch to the current wallpaper with it")
                }
                Text(
                    "Saved: pages and their items, the pill, icons, gestures, the clock and the home menu. Drawer, tags and redirect settings stay shared.",
                    style = TextStyle(fontFamily = HomeTheme.font, fontSize = 12.sp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                controller.data.layouts.saveCurrentAs(name, if (withWallpaper) wallpaper.currentFile else null, controller.data.wallpapers.get().current)
                controller.savingLayout = false
                controller.toast("Saved \"${name.trim()}\"")
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { controller.savingLayout = false }) { Text("Cancel") } },
    )
}
