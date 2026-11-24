package dev.liquid.launcher.feature.home

import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
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
import dev.liquid.launcher.core.settings.derive

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
    val color = sheetContentColor(glass, lightBackground)
    var holding by remember { mutableStateOf<SavedLayout?>(null) }
    var renaming by remember { mutableStateOf<SavedLayout?>(null) }
    val settings = controller.data.settings
    val timerUntil by settings.derive { get(LauncherSettings.LayoutTimerUntil).toLongOrNull() }
    val timerBack by settings.derive { get(LauncherSettings.LayoutTimerBack) }

    // W2: the shared sheet: the layouts scroll if the sheet is short, the buttons stay at the bottom.
    LiquidSheet(
        onDismiss = { controller.layoutsSheet = false },
        glass = glass,
        lightBackground = lightBackground,
        backdrop = backdrop,
        title = "Layouts",
        actions = {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy((8 * themeGap).dp)) {
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
        },
    ) {
        if (state.layouts.isEmpty()) {
            SheetText(
                "Save this home screen as a layout (pages, pill, icons, gestures and optionally the wallpaper), " +
                    "then set up another one and switch between them here or from the home menu.",
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
                        LayoutMiniature(layout, color, current, controller)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            layout.name,
                            modifier = Modifier.width(78.dp),
                            style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 13.sp, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (layout.wallpaper != null) {
                            // W3: a small badge in the theme's chip colours.
                            val chip = currentTheme?.chip
                            Box(
                                Modifier.padding(top = 3.dp).clip(RoundedCornerShape(50))
                                    .background(chip?.let { Color(it.fill) } ?: color.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                LauncherIcon(LauncherIcons.Image, contentDescription = "Has its own wallpaper", tint = chip?.let { Color(it.text) } ?: color.copy(alpha = 0.7f), size = 13.dp)
                            }
                        }
                    }
                }
            }
        }
        // P8 (0.9.21): a layout on for a while says until when, and can be kept.
        val until = timerUntil
        if (until != null && until > System.currentTimeMillis()) {
            val backName = state.layouts.firstOrNull { it.id == timerBack }?.name ?: "the layout before"
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Until " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(until)) + ", then $backName",
                    modifier = Modifier.weight(1f),
                    style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.8f), fontSize = 13.sp),
                )
                SheetChip("Keep it", LauncherIcons.Check, color) { controller.cancelLayoutTimer() }
            }
        }
    }

    holding?.let { layout ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { holding = null },
            title = { Text(layout.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (layout.id != state.current && state.current != null) {
                        // P8 (0.9.21): for a while, then back to the layout on now.
                        for ((minutes, label) in listOf(30 to "30 minutes", 60 to "1 hour", 120 to "2 hours")) {
                            DialogRow("Switch to it for $label") {
                                holding = null
                                controller.layoutsSheet = false
                                controller.switchLayoutFor(layout.id, minutes)
                            }
                        }
                    }
                    DialogRow("Rename") { renaming = layout; holding = null }
                    DialogRow("Duplicate") { controller.data.layouts.duplicate(layout.id); holding = null }
                    // 0.16.0 (D202): with a wallpaper per layout, a layout always keeps one (Choose its wallpaper changes it).
                    val ownWallpapers = controller.data.settings.get(LauncherSettings.LayoutsOwnWallpaper)
                    if (!(ownWallpapers && layout.wallpaper != null)) {
                        DialogRow(if (layout.wallpaper != null) "Stop switching the wallpaper with it" else "Give it the current wallpaper") {
                            val file = if (layout.wallpaper != null) null else WallpaperStore.get(context).currentFile
                            if (layout.wallpaper == null && file == null) controller.toast("Pick a wallpaper in the launcher first")
                            controller.data.layouts.setWallpaper(layout.id, file, controller.data.wallpapers.get().current)
                            holding = null
                        }
                    }
                    // W8 (P8): any picture of the library as this layout's wallpaper, not only the one on screen.
                    DialogRow("Choose its wallpaper…") {
                        holding = null
                        controller.layoutWallpaperPick = layout.id
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

/** A button of the sheet: a [GlassButton] in the look of Settings → Buttons (0.6.4). */
@Composable
private fun SheetChip(label: String, icon: Int, color: Color, onClick: () -> Unit) {
    // W3: Save as new, Save changes, Undo switch and Keep it are the theme's secondary buttons.
    GlassButton(label, color, icon = icon, emphasis = ControlEmphasis.Secondary, onClick = onClick)
}

/**
 * The layout's home page drawn small (W8, P8: a real preview): its own wallpaper, or the one on screen, behind; the
 * apps' real icons in their places; blocks for widgets and folders.
 */
@Composable
private fun LayoutMiniature(layout: SavedLayout, color: Color, current: Boolean, controller: HomeController) {
    val (columns, rows) = layout.gridSize()
    val page = layout.layout.pages.getOrNull(layout.layout.defaultIndex)
    val shape = RoundedCornerShape(12.dp)
    val apps by controller.data.apps.apps.collectAsState()
    val byRef = remember(apps) { apps.associateBy { it.ref } }
    val context = LocalContext.current
    val onScreen by WallpaperStore.get(context).image.collectAsState()
    val own by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, layout.id, layout.wallpaper) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            controller.data.layouts.wallpaperFile(layout)?.let { file ->
                runCatching {
                    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                    android.graphics.BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    val picture = own ?: onScreen
    Canvas(
        Modifier
            .size(width = 78.dp, height = 140.dp)
            .clip(shape)
            .background(color.copy(alpha = if (current) 0.16f else 0.07f))
            .border(if (current) 2.dp else 1.dp, color.copy(alpha = if (current) 0.9f else 0.3f), shape)
    ) {
        if (picture != null) {
            val scale = maxOf(size.width / picture.width, size.height / picture.height)
            val w = picture.width * scale
            val h = picture.height * scale
            drawImage(
                picture,
                dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - w) / 2f).toInt(), ((size.height - h) / 2f).toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
                alpha = 0.9f,
            )
        }
        val pad = 6.dp.toPx()
        val cw = (size.width - 2 * pad) / columns
        val ch = (size.height - 2 * pad) / rows
        for (item in page?.items.orEmpty()) {
            val topLeft = Offset(pad + item.x * cw + 1.5f, pad + item.y * ch + 1.5f)
            val area = Size(item.w * cw - 3f, item.h * ch - 3f)
            val icon = if (item.kind == ItemKind.APP) item.app?.let { byRef[it] }?.let { it.homeIcon ?: it.icon } else null
            if (icon != null) {
                val d = (minOf(cw, ch) * 0.78f).toInt().coerceAtLeast(1)
                drawImage(
                    icon,
                    dstOffset = androidx.compose.ui.unit.IntOffset((topLeft.x + (area.width - d) / 2f).toInt(), (topLeft.y + (area.height - d) / 2f).toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(d, d),
                )
            } else if (item.kind == ItemKind.APP) {
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
    // 0.16.0 (D202): with a wallpaper per layout, a new layout always comes with the wallpaper on screen.
    val ownWallpapers = remember { controller.data.settings.get(LauncherSettings.LayoutsOwnWallpaper) }
    var withWallpaper by remember { mutableStateOf(ownWallpapers) }
    val wallpaper = remember { WallpaperStore.get(context) }
    AlertDialog(
        onDismissRequest = { controller.savingLayout = false },
        title = { Text("Save layout") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name, for example Study") })
                if (!ownWallpapers) {
                    Row(Modifier.fillMaxWidth().clickable { withWallpaper = !withWallpaper }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = withWallpaper, onCheckedChange = { withWallpaper = it }, enabled = wallpaper.hasManagedWallpaper)
                        Text("Switch to the current wallpaper with it")
                    }
                }
                Text(
                    "Saved: pages and their items, the pill, icons, gestures, the clock and the home menu. Drawer, tags and redirect settings stay shared.",
                    style = TextStyle(fontFamily = HomeTheme.font, fontSize = 12.sp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val recents = Wallpapers.recent(controller.data).map { it.id }
                val saved = controller.data.layouts.saveCurrentAs(name, if (withWallpaper) wallpaper.currentFile else null, controller.data.wallpapers.get().current)
                Wallpapers.setUpLayout(controller.data, saved.id, recents)
                controller.savingLayout = false
                controller.toast("Saved \"${name.trim()}\"")
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { controller.savingLayout = false }) { Text("Cancel") } },
    )
}

/**
 * W8 (P8): the wallpaper library as a grid; the picture tapped becomes the wallpaper of the layout being set up
 * (placed as the library has it), shown whenever that layout comes back.
 */
@Composable
internal fun LayoutWallpaperSheet(controller: HomeController, layoutId: String, glass: GlassConfig, lightBackground: Boolean, backdrop: com.kyant.backdrop.Backdrop) {
    val context = LocalContext.current
    val library by controller.data.wallpapers.state.collectAsState()
    val layouts by controller.data.layouts.state.collectAsState()
    val layout = layouts.layouts.firstOrNull { it.id == layoutId }
    var busy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun choose(item: dev.liquid.launcher.core.data.WallpaperItem) {
        if (busy || layout == null) return
        busy = true
        scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val store = WallpaperStore.get(context)
                    val source = Wallpapers.source(context, controller.data, WallpaperPick.Library(item)) ?: return@runCatching false
                    val bitmap = store.render(source, item.crop, item.parallax.takeIf { it >= 0f } ?: Wallpapers.defaultParallax(controller.data))
                    val file = java.io.File(context.cacheDir, "layout-wallpaper.png")
                    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    controller.data.layouts.setWallpaper(layoutId, file, item.id)
                    file.delete()
                    true
                }.getOrDefault(false)
            }
            busy = false
            controller.toast(
                when {
                    !ok -> "Could not use that picture"
                    layouts.current == layoutId -> "${layout.name} keeps this wallpaper; it shows the next time you switch to it"
                    else -> "${layout.name} will come with this wallpaper"
                },
            )
            if (ok) controller.layoutWallpaperPick = null
        }
    }
    LiquidSheet(
        onDismiss = { controller.layoutWallpaperPick = null },
        glass = glass,
        lightBackground = lightBackground,
        backdrop = backdrop,
        title = "Wallpaper for ${layout?.name ?: "this layout"}",
        actions = {
            Spacer(Modifier.weight(1f))
            GlassButton(if (busy) "Placing…" else "Cancel", LocalSheetContent.current, emphasis = ControlEmphasis.Secondary) { controller.layoutWallpaperPick = null }
        },
        scroll = null,
    ) {
        if (library.items.isEmpty()) {
            SheetText("No pictures in the library yet. Add some in the wallpaper screen.", Modifier.padding(vertical = 8.dp))
        }
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(library.items.size, key = { library.items[it].id }) { i ->
                val item = library.items[i]
                val thumb by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, item.id) {
                    value = controller.data.wallpapers.thumbnail(item)
                }
                val chosen = layout?.wallpaperId == item.id
                Box(
                    Modifier
                        .size(width = 96.dp, height = 150.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(LocalSheetContent.current.copy(alpha = 0.08f))
                        .border(if (chosen) 2.dp else 0.dp, if (chosen) HomeTheme.accent else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable { choose(item) },
                ) {
                    thumb?.let { androidx.compose.foundation.Image(it, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize()) }
                }
            }
        }
    }
}
