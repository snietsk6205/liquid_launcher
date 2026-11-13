package dev.liquid.launcher.feature.home

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.common.CropSpec
import dev.liquid.launcher.core.common.Frame
import dev.liquid.launcher.core.data.FolderImage
import dev.liquid.launcher.core.data.LinkedFolder
import dev.liquid.launcher.core.data.WallpaperItem
import dev.liquid.launcher.core.data.WallpaperLibraryOps
import dev.liquid.launcher.core.designsystem.GlassQuality
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import kotlinx.coroutines.launch

/**
 * What the wallpaper screen is trying out. The home screen draws these values instead of the
 * saved ones while the screen is open, so every change is seen on the real thing.
 */
@Stable
internal class StudioState(store: SettingsStore) {
    /** The picture being tried, or null for the current wallpaper. */
    var pick by mutableStateOf<WallpaperPick?>(null)

    /** That picture, decoded for placing. */
    var source by mutableStateOf<Bitmap?>(null)
    var sourceImage by mutableStateOf<ImageBitmap?>(null)
    var crop by mutableStateOf(CropSpec.Centre)
    var parallax by mutableFloatStateOf(store.get(LauncherSettings.WallpaperParallax) / 100f)

    /** The wallpaper as it will be applied (placed and rendered), once ready. */
    var rendered by mutableStateOf<Bitmap?>(null)
    var candidateImage by mutableStateOf<ImageBitmap?>(null)

    /** Placing mode: pinch and drag on the screen. */
    var adjusting by mutableStateOf(false)

    /** A short slide across the pages to show the parallax, or null to follow the pages. */
    var sweep by mutableStateOf<Float?>(null)

    /** "pictures", "look" or "auto". */
    var page by mutableStateOf("pictures")

    /** "recent", "favourites", "all" or a linked folder's uri. */
    var tab by mutableStateOf("recent")

    /** "home", "drawer" or "search": which screen the preview shows. */
    var surface by mutableStateOf("home")
    var homeBlur by mutableIntStateOf(store.get(LauncherSettings.WallpaperHomeBlur))
    var homeDim by mutableIntStateOf(store.get(LauncherSettings.WallpaperHomeDim))
    var drawerBlur by mutableIntStateOf(store.get(LauncherSettings.DrawerHomeBlur))
    var drawerDim by mutableIntStateOf(store.get(LauncherSettings.DrawerDim))
    var searchBlur by mutableIntStateOf(store.get(LauncherSettings.SearchBlur))
    var searchDim by mutableIntStateOf(store.get(LauncherSettings.SearchDim))
    var lockToo by mutableStateOf(store.get(LauncherSettings.WallpaperLockToo))
    var forLayout by mutableStateOf(false)
    var busy by mutableStateOf(false)

    /** Controls hidden to look at the whole screen. */
    var peek by mutableStateOf(false)
    var holding by mutableStateOf<WallpaperItem?>(null)

    fun select(pick: WallpaperPick, source: Bitmap, crop: CropSpec, parallax: Float) {
        this.pick = pick
        this.source = source
        sourceImage = source.asImageBitmap()
        this.crop = crop
        this.parallax = parallax
        rendered = null
        candidateImage = null
    }

    /** The part of the picture the wallpaper canvas covers, for a screen of [w] × [h]. */
    fun frame(w: Float, h: Float): Frame? {
        val src = source ?: return null
        return crop.frame(src.width, src.height, w * (1f + parallax), h)
    }

    /** True while the home screen should draw the picture itself (placing, or before the render is ready). */
    val drawsSource: Boolean get() = sourceImage != null && (adjusting || candidateImage == null)
}

/** The wallpaper screen: a glass sheet over the live home screen. */
@Composable
internal fun WallpaperStudio(
    state: StudioState,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    controller: HomeController,
) {
    val context = LocalContext.current
    val store = remember { WallpaperStore.get(context) }
    val data = controller.data
    val library by data.wallpapers.state.collectAsState()
    val layouts by data.layouts.state.collectAsState()
    val scope = rememberCoroutineScope()
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    val shown by animateFloatAsState(if (state.peek || state.adjusting) 0f else 1f, label = "sheet")
    val currentLayout = layouts.layouts.firstOrNull { it.id == layouts.current }
    LaunchedEffect(currentLayout?.id) { state.forLayout = currentLayout?.wallpaper != null }

    fun close() {
        controller.wallpaperStudio = false
    }
    // However the screen closes (Cancel, Apply, back, Home), the drawer preview goes away with it.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (controller.drawer.value > 0f) controller.closeDrawer() }
    }
    // The placed picture is rendered once it stops moving, so blur and dim can be judged on it.
    LaunchedEffect(state.source, state.crop, state.parallax, state.adjusting) {
        val source = state.source ?: return@LaunchedEffect
        if (state.adjusting) return@LaunchedEffect
        val bitmap = store.render(source, state.crop, state.parallax)
        state.rendered = bitmap
        state.candidateImage = bitmap.asImageBitmap()
    }

    fun choose(pick: WallpaperPick) {
        scope.launch {
            state.busy = true
            val source = Wallpapers.source(context, data, pick)
            if (source == null) {
                controller.toast("Could not read that picture")
            } else {
                val item = (pick as? WallpaperPick.Library)?.item
                state.select(pick, source, item?.crop ?: CropSpec.Centre, item?.parallax?.takeIf { it >= 0f } ?: Wallpapers.defaultParallax(data))
            }
            state.busy = false
        }
    }

    fun placeCurrent() {
        if (state.source != null) {
            state.adjusting = true
            return
        }
        val item = library.current?.let { data.wallpapers.find(it) }
        if (item == null) {
            controller.toast("Pick a picture first")
            return
        }
        scope.launch {
            state.busy = true
            Wallpapers.source(context, data, WallpaperPick.Library(item))?.let { source ->
                state.select(WallpaperPick.Library(item), source, item.crop, item.parallax.takeIf { it >= 0f } ?: Wallpapers.defaultParallax(data))
                state.adjusting = true
            }
            state.busy = false
        }
    }

    fun addPhotos() {
        controller.pickImages { uris ->
            if (uris.isEmpty()) return@pickImages
            scope.launch {
                state.busy = true
                val added = uris.mapNotNull { data.wallpapers.importUri(it) }
                state.busy = false
                state.tab = "all"
                added.firstOrNull()?.let { choose(WallpaperPick.Library(it)) }
                if (added.size > 1) controller.toast("${added.size} pictures added to your library")
            }
        }
    }

    fun linkFolder() {
        controller.pickFolder { uri ->
            if (uri == null) return@pickFolder
            val folder = data.wallpapers.addFolder(uri)
            if (folder == null) controller.toast("That folder could not be opened") else state.tab = folder.uri
        }
    }

    fun apply() {
        val settings = data.settings
        scope.launch {
            state.busy = true
            settings.set(LauncherSettings.WallpaperLockToo, state.lockToo)
            var error: String? = null
            val pick = state.pick
            val source = state.source
            if (pick != null && source != null) {
                val bitmap = state.rendered ?: store.render(source, state.crop, state.parallax)
                store.show(bitmap)
                error = Wallpapers.persist(context, data, PreparedWallpaper(bitmap, pick, state.crop, state.parallax))
                val layoutId = layouts.current
                if (error == null && state.forLayout && layoutId != null) {
                    data.layouts.setWallpaper(layoutId, store.currentFile, data.wallpapers.get().current)
                }
            } else if (state.forLayout && currentLayout != null && currentLayout.wallpaper == null) {
                data.layouts.setWallpaper(currentLayout.id, store.currentFile, library.current)
            } else if (!state.forLayout && currentLayout?.wallpaper != null) {
                data.layouts.setWallpaper(currentLayout.id, null)
            }
            settings.set(LauncherSettings.WallpaperHomeBlur, state.homeBlur)
            settings.set(LauncherSettings.WallpaperHomeDim, state.homeDim)
            settings.set(LauncherSettings.DrawerHomeBlur, state.drawerBlur)
            settings.set(LauncherSettings.DrawerDim, state.drawerDim)
            settings.set(LauncherSettings.SearchBlur, state.searchBlur)
            settings.set(LauncherSettings.SearchDim, state.searchDim)
            state.busy = false
            if (error != null) controller.toast("Could not set the wallpaper: $error") else close()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (state.adjusting) {
            PlaceLayer(state, controller)
            PlaceBar(state, glass, lightBackground, backdrop, color, Modifier.align(Alignment.BottomCenter))
        } else {
            // Tapping the preview hides or shows the controls.
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.peek = !state.peek }
            )
        }
        val sheetShape = RoundedCornerShape(32.dp)
        if (shown > 0.01f) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .graphicsLayer {
                        translationY = (1f - shown) * (size.height + 40.dp.toPx())
                        alpha = shown
                    }
                    .liquidGlass(backdrop, sheetShape, glass.style(Surfaces.Menu, lightBackground))
                    .clip(sheetShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Segmented(
                    listOf("pictures" to "Pictures", "look" to "Blur and dim", "auto" to "Automatic"),
                    state.page, color,
                ) { state.page = it }
                Spacer(Modifier.height(12.dp))
                when (state.page) {
                    "pictures" -> PicturesPage(state, library, color, controller, ::choose, ::addPhotos, ::linkFolder)
                    "look" -> LookPage(state, glass, color)
                    else -> AutoPage(data.settings, color)
                }
                if (state.page == "pictures") {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallChip(LauncherIcons.Crop, "Place", color, Modifier.weight(1f)) { placeCurrent() }
                        val pickedItem = (state.pick as? WallpaperPick.Library)?.item?.let { data.wallpapers.find(it.id) }
                            ?: library.current?.takeIf { state.pick == null }?.let { data.wallpapers.find(it) }
                        if (pickedItem != null) {
                            SmallChip(
                                if (pickedItem.favourite) LauncherIcons.Heart else LauncherIcons.HeartOutline,
                                if (pickedItem.favourite) "Favourite" else "Add to favourites",
                                color, Modifier.weight(1.3f),
                            ) { data.wallpapers.setFavourite(pickedItem.id, !pickedItem.favourite) }
                        }
                    }
                }
                if (currentLayout != null) {
                    ToggleRow("Only for the layout \"${currentLayout.name}\"", state.forLayout, color) { state.forLayout = it }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetButton("Cancel", color, filled = false, modifier = Modifier.weight(1f)) { close() }
                    SheetButton(if (state.busy) "Working…" else "Apply", color, filled = true, modifier = Modifier.weight(1f)) { if (!state.busy) apply() }
                }
            }
        }
        if (state.peek && !state.adjusting) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
                    .liquidGlass(backdrop, RoundedCornerShape(50), glass.style(Surfaces.Menu, lightBackground))
                    .clip(RoundedCornerShape(50))
                    .clickable { state.peek = false }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) { Text("Show controls", style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)) }
        }
    }

    state.holding?.let { item ->
        AlertDialog(
            onDismissRequest = { state.holding = null },
            title = { Text("Wallpaper") },
            text = {
                Column {
                    DialogLine(if (item.favourite) "Remove from favourites" else "Add to favourites") {
                        data.wallpapers.setFavourite(item.id, !item.favourite)
                        state.holding = null
                    }
                    if (item.lastUsed > 0) {
                        DialogLine("Remove from recent") {
                            data.wallpapers.forget(item.id)
                            state.holding = null
                        }
                    }
                    DialogLine("Delete from the library") {
                        data.wallpapers.remove(item.id)
                        state.holding = null
                    }
                }
            },
            confirmButton = { TextButton(onClick = { state.holding = null }) { Text("Close") } },
        )
    }
}

// ---- pictures ----

@Composable
private fun PicturesPage(
    state: StudioState,
    library: dev.liquid.launcher.core.data.WallpaperLibraryState,
    color: Color,
    controller: HomeController,
    choose: (WallpaperPick) -> Unit,
    addPhotos: () -> Unit,
    linkFolder: () -> Unit,
) {
    val data = controller.data
    // Where to look: recent (each picture once), favourites, everything, then each linked folder.
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TabChip("Recent", state.tab == "recent", color) { state.tab = "recent" }
        TabChip("Favourites", state.tab == "favourites", color) { state.tab = "favourites" }
        TabChip("All", state.tab == "all", color) { state.tab = "all" }
        for (folder in library.folders) TabChip(folder.name, state.tab == folder.uri, color) { state.tab = folder.uri }
        TabChip("+ Folder", false, color) { linkFolder() }
    }
    Spacer(Modifier.height(10.dp))
    val folder = library.folders.firstOrNull { it.uri == state.tab }
    val pickedId = (state.pick as? WallpaperPick.Library)?.item?.id ?: library.current.takeIf { state.pick == null }
    val pickedUri = (state.pick as? WallpaperPick.External)?.uri
    Box(Modifier.fillMaxWidth().height(232.dp)) {
        if (folder != null) {
            FolderGrid(folder, pickedUri, color, controller, choose)
        } else {
            val items = remember(library, state.tab) {
                when (state.tab) {
                    "favourites" -> WallpaperLibraryOps.favourites(library)
                    "all" -> WallpaperLibraryOps.all(library)
                    else -> WallpaperLibraryOps.recent(library)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "add") { AddThumb(color, addPhotos) }
                items(items, key = { it.id }) { item ->
                    LibraryThumb(item, item.id == pickedId, color, controller, onHold = { state.holding = item }) { choose(WallpaperPick.Library(item)) }
                }
            }
            if (items.isEmpty() && state.tab != "recent") {
                Text(
                    if (state.tab == "favourites") "Hold a picture, or use the heart below, to make it a favourite." else "Add photos with +, or link a folder.",
                    modifier = Modifier.align(Alignment.Center).padding(top = 60.dp),
                    style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.75f), fontSize = 13.sp),
                )
            }
        }
    }
    if (folder != null) {
        Text(
            "Remove this folder",
            modifier = Modifier.padding(top = 6.dp).clickable {
                data.wallpapers.removeFolder(folder.uri)
                state.tab = "recent"
            },
            style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.7f), fontSize = 12.sp),
        )
    }
}

@Composable
private fun FolderGrid(folder: LinkedFolder, picked: android.net.Uri?, color: Color, controller: HomeController, choose: (WallpaperPick) -> Unit) {
    val images by produceState<List<FolderImage>?>(null, folder.uri) { value = controller.data.wallpapers.folderImages(folder) }
    val list = images
    if (list == null) {
        Text("Reading the folder…", style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.75f), fontSize = 13.sp))
        return
    }
    if (list.isEmpty()) {
        Text("No pictures in this folder (sub-folders are not read).", style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.75f), fontSize = 13.sp))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(list, key = { it.uri.toString() }) { image ->
            val thumb by produceState<ImageBitmap?>(null, image.uri) { value = controller.data.wallpapers.thumbnail(image.uri) }
            Thumb(thumb, image.uri == picked, color, onHold = null) { choose(WallpaperPick.External(image.uri, folder.name)) }
        }
    }
}

@Composable
private fun LibraryThumb(item: WallpaperItem, selected: Boolean, color: Color, controller: HomeController, onHold: () -> Unit, onClick: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(null, item.id) { value = controller.data.wallpapers.thumbnail(item) }
    Box {
        Thumb(thumb, selected, color, onHold, onClick)
        if (item.favourite) {
            LauncherIcon(LauncherIcons.Heart, contentDescription = "Favourite", tint = Color.White, size = 13.dp, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Thumb(image: ImageBitmap?, selected: Boolean, color: Color, onHold: (() -> Unit)?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.56f)
            .clip(shape)
            .background(color.copy(alpha = 0.1f))
            .border(if (selected) 2.dp else 0.5.dp, color.copy(alpha = if (selected) 0.95f else 0.25f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onHold),
    ) {
        if (image != null) Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun AddThumb(color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.56f)
            .clip(shape)
            .background(color.copy(alpha = 0.1f))
            .border(0.5.dp, color.copy(alpha = 0.25f), shape)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LauncherIcon(LauncherIcons.Add, contentDescription = null, tint = color, size = 22.dp)
        Text("Photos", style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 11.sp))
    }
}

// ---- placing ----

/** Pinch and drag anywhere on the screen to place the picture; the home screen shows it live. */
@Composable
private fun PlaceLayer(state: StudioState, controller: HomeController) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val src = state.source ?: return@detectTransformGestures
                    val w = controller.screenWidth
                    val h = controller.screenHeight
                    // Zoom around the fingers: the point under them stays put.
                    val centre = Offset(w / 2f, h / 2f)
                    val keep = (centroid - centre) * (1f - 1f / zoom)
                    state.crop = state.crop.moved(src.width, src.height, w * (1f + state.parallax), h, pan.x - keep.x, pan.y - keep.y, zoom)
                }
            }
    )
}

@Composable
private fun PlaceBar(state: StudioState, glass: GlassConfig, lightBackground: Boolean, backdrop: Backdrop, color: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(28.dp)
    val scope = rememberCoroutineScope()
    fun sweep() {
        scope.launch {
            val anim = Animatable(0f)
            state.sweep = 0f
            anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) { state.sweep = value }
            anim.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) { state.sweep = value }
            state.sweep = null
        }
    }
    Column(
        modifier
            .navigationBarsPadding()
            .padding(12.dp)
            .liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground))
            .clip(shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("Pinch and drag to place the picture", style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(10.dp))
        Text("Parallax: slides a little with the pages", style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.75f), fontSize = 12.sp))
        Spacer(Modifier.height(6.dp))
        Segmented(
            listOf("0" to "Still", "15" to "Light", "30" to "Strong"),
            (state.parallax * 100).toInt().let { p -> when { p <= 5 -> "0"; p <= 22 -> "15"; else -> "30" } },
            color,
        ) {
            state.parallax = it.toInt() / 100f
            if (state.parallax > 0f) sweep()
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetButton("Reset", color, filled = false, modifier = Modifier.weight(1f)) { state.crop = CropSpec.Centre }
            SheetButton("Done", color, filled = true, modifier = Modifier.weight(1f)) { state.adjusting = false }
        }
    }
}

// ---- blur and dim ----

@Composable
private fun LookPage(state: StudioState, glass: GlassConfig, color: Color) {
    Segmented(listOf("home" to "Home", "drawer" to "Drawer", "search" to "Search"), state.surface, color) { state.surface = it }
    Spacer(Modifier.height(8.dp))
    when (state.surface) {
        "home" -> {
            ValueSlider("Blur", state.homeBlur, 0, 40, "dp", color) { state.homeBlur = it }
            ValueSlider("Dim", state.homeDim, 0, 80, "%", color) { state.homeDim = it }
        }
        "drawer" -> {
            ValueSlider("Blur", state.drawerBlur, 0, 40, "dp", color) { state.drawerBlur = it }
            ValueSlider("Dim", state.drawerDim, 0, 90, "%", color) { state.drawerDim = it }
        }
        else -> {
            ValueSlider("Blur", state.searchBlur, 0, 40, "dp", color) { state.searchBlur = it }
            ValueSlider("Dim", state.searchDim, 0, 90, "%", color) { state.searchDim = it }
        }
    }
    if (glass.quality == GlassQuality.Tint && state.surface != "home") {
        Text(
            "Blur here is off on this phone (the glass benchmark capped glass to tint).",
            style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.7f), fontSize = 12.sp),
        )
    }
    ToggleRow("Also on the lock screen", state.lockToo, color) { state.lockToo = it }
}

// ---- automatic changes ----

@Composable
private fun AutoPage(settings: SettingsStore, color: Color) {
    // Read so the page follows its own changes.
    val values by settings.values.collectAsState()
    values.size
    PageLabel("Change automatically", color)
    Segmented(
        listOf("off" to "Off", "unlock" to "Each unlock", "daily" to "Daily"),
        settings.get(LauncherSettings.WallpaperRotation), color,
    ) { settings.set(LauncherSettings.WallpaperRotation, it) }
    PageLabel("Between", color)
    Segmented(
        listOf("favourites" to "Favourites", "recent" to "Recent", "all" to "All", "folders" to "Folders"),
        settings.get(LauncherSettings.WallpaperRotationPool), color,
    ) { settings.set(LauncherSettings.WallpaperRotationPool, it) }
    ToggleRow("Shuffle", settings.get(LauncherSettings.WallpaperRotationShuffle), color) { settings.set(LauncherSettings.WallpaperRotationShuffle, it) }
    PageLabel("Transition when the wallpaper or layout changes", color)
    Segmented(
        listOf("liquid" to "Liquid", "fade" to "Fade", "depth" to "Depth", "slide" to "Slide", "none" to "None"),
        settings.get(LauncherSettings.WallpaperTransition), color,
    ) { settings.set(LauncherSettings.WallpaperTransition, it) }
    Text(
        "Changes happen while the screen is off. \"Shuffle\" in the home menu's wallpaper strip changes it now.",
        modifier = Modifier.padding(top = 8.dp),
        style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.7f), fontSize = 12.sp),
    )
}

@Composable
private fun PageLabel(text: String, color: Color) {
    Text(text, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp), style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.8f), fontSize = 12.sp))
}

// ---- small parts ----

@Composable
internal fun Segmented(options: List<Pair<String, String>>, selected: String, color: Color, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((id, label) in options) {
            val on = selected == id
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = if (on) 0.22f else 0.06f))
                    .clickable { onSelect(id) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                )
            }
        }
    }
}

@Composable
private fun TabChip(label: String, on: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = if (on) 0.22f else 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, maxLines = 1, style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal))
    }
}

@Composable
private fun SmallChip(icon: Int, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherIcon(icon, contentDescription = null, tint = color, size = 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, color: Color, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 14.sp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = color.copy(alpha = 0.5f), checkedThumbColor = color),
        )
    }
}

@Composable
private fun DialogLine(label: String, onClick: () -> Unit) {
    Text(label, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), style = TextStyle(fontSize = 16.sp))
}

@Composable
private fun ValueSlider(label: String, value: Int, min: Int, max: Int, unit: String, color: Color, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(44.dp), style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 14.sp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color.copy(alpha = 0.8f), inactiveTrackColor = color.copy(alpha = 0.2f)),
        )
        Text("$value $unit", modifier = Modifier.width(52.dp).padding(start = 8.dp), style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 13.sp))
    }
}

@Composable
private fun SheetButton(label: String, color: Color, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val inverse = if (color == Color.White) Color(0xFF16181C) else Color.White
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = TextStyle(fontFamily = HomeTheme.font, color = if (filled) inverse else color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)) }
}
