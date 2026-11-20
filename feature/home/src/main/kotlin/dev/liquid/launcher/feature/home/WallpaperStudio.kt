package dev.liquid.launcher.feature.home

import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * What the wallpaper screen is trying out. The home screen draws its looks (blur, dim) instead of the saved ones while
 * the screen is open, so they are seen on the real thing. A picture picked here is not: since 0.11.1 the screen keeps
 * the current wallpaper until Apply, which ripples to the new one (full screen before that only while placing it).
 */
@Stable
internal class StudioState(store: SettingsStore, currentLook: dev.liquid.launcher.core.data.WallpaperLook?) {
    /** The picture picked, or null for the current wallpaper. */
    var pick by mutableStateOf<WallpaperPick?>(null)

    /** 0.11.1: the picture tapped last, ringed at once while it loads (then it is [pick]). */
    var choosing by mutableStateOf<WallpaperPick?>(null)

    /** That picture, decoded for placing. */
    var source by mutableStateOf<Bitmap?>(null)
    var sourceImage by mutableStateOf<ImageBitmap?>(null)
    var crop by mutableStateOf(CropSpec.Centre)
    var parallax by mutableFloatStateOf(store.get(LauncherSettings.WallpaperParallax) / 100f)

    /** The wallpaper as it will be applied (placed and rendered), once ready. */
    var rendered by mutableStateOf<Bitmap?>(null)

    /** The picture, placement and parallax [rendered] was made from (0.10.4): a picture picked with the transition arrives rendered. */
    var renderedKey: Any? = null

    /** Its colours, worked out as soon as it is rendered (before Apply), and the themed icon palette from them. */
    var tone: dev.liquid.launcher.core.common.WallpaperTone? = null
    var palette: Pair<Int, Int>? = null

    /** Placing mode: pinch and drag on the screen. */
    var adjusting by mutableStateOf(false)

    /** 0.11.1: 1 while placing, then down to 0: the placed picture fades back into the wallpaper on screen. */
    val placeShown = Animatable(0f)
    var placeLeaving by mutableStateOf(false)

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

    /** The look kept with the wallpaper on screen when the screen opened (0.11.1; null: none of its own). */
    val currentLook = currentLook

    /**
     * W8 (P8): the look of the picture that Apply sets, when it has its own (blur, dim, saturation, vignette); null: as
     * for all. 0.11.1: starts as the current wallpaper's own look; until then it started empty, and Apply wiped the look.
     */
    var pictureLook by mutableStateOf(currentLook)

    /** "This picture" was changed here, so Apply keeps the look edited (0.11.1). */
    var lookTouched by mutableStateOf(false)

    fun editLook(look: dev.liquid.launcher.core.data.WallpaperLook?) {
        pictureLook = look
        lookTouched = true
    }

    /** The picture's own look on the screen: the current wallpaper's until a look is edited here (0.11.1). */
    val shownLook: dev.liquid.launcher.core.data.WallpaperLook? get() = if (pick == null || lookTouched) pictureLook else currentLook
    var forLayout by mutableStateOf(false)
    var busy by mutableStateOf(false)

    /** Controls hidden to look at the whole screen. */
    var peek by mutableStateOf(false)
    var holding by mutableStateOf<WallpaperItem?>(null)

    fun select(pick: WallpaperPick, source: Bitmap, crop: CropSpec, parallax: Float) {
        // A library picture brings its own look with it.
        if (pick != this.pick) {
            pictureLook = (pick as? WallpaperPick.Library)?.item?.look
            lookTouched = false
        }
        this.pick = pick
        this.source = source
        sourceImage = source.asImageBitmap()
        this.crop = crop
        this.parallax = parallax
        rendered = null
        tone = null
        palette = null
    }

    /** The part of the picture the wallpaper canvas covers, for a screen of [w] × [h]. */
    fun frame(w: Float, h: Float): Frame? {
        val src = source ?: return null
        return crop.frame(src.width, src.height, w * (1f + parallax), h)
    }

    /** True while the home screen draws the picked picture instead of the wallpaper: only while placing it (0.11.1). */
    val drawsSource: Boolean get() = sourceImage != null && adjusting
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
    val color = sheetContentColor(glass, lightBackground)
    val toggled = animateFloatAsState(if (state.peek || state.adjusting) 0f else 1f, label = "sheet")
    // Rises in like the other glass sheets when the screen opens.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.enter()) }
    // 0.11.1: read while drawing, not composing: the whole screen recomposed on every frame of its entrance before.
    // It sinks away again when the screen closes (Presence).
    val presence = LocalPresence.current
    fun shown(): Float = toggled.value * appear.value.coerceIn(0f, 1.05f) * presence.value
    val sheetVisible by remember { androidx.compose.runtime.derivedStateOf { shown() > 0.01f } }
    // 0.10.4: the pictures take more room (two rows and a bit), and the handle on top pulls the sheet up to about
    // three and a half rows; it stays where you left it.
    val tall = remember { Animatable(if (data.settings.get(LauncherSettings.WallpaperSheetTall)) 1f else 0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val compactGrid = 300.dp
    val screenDp = with(density) { controller.screenHeight.toDp() }
    val expandedGrid = (screenDp * 0.56f).coerceIn(compactGrid, 560.dp)
    val gridHeight = compactGrid + (expandedGrid - compactGrid) * tall.value
    val currentLayout = layouts.layouts.firstOrNull { it.id == layouts.current }
    LaunchedEffect(currentLayout?.id) { state.forLayout = currentLayout?.wallpaper != null }

    fun close() {
        controller.wallpaperStudio = false
    }
    // However the screen closes (Cancel, Apply, back, Home), the drawer preview goes away with it,
    // and icons go back to the colours of the wallpaper actually set.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (controller.drawer.value > 0f) controller.closeDrawer()
            IconPaint.endPreview(controller.scope)
        }
    }
    // The placed picture is rendered once it stops moving, with its colours, so Apply has nothing left to wait for.
    LaunchedEffect(state.source, state.crop, state.parallax, state.adjusting) {
        val source = state.source ?: return@LaunchedEffect
        if (state.adjusting) return@LaunchedEffect
        val key = Triple(source, state.crop, state.parallax)
        if (state.rendered != null && state.renderedKey == key) return@LaunchedEffect
        val bitmap = store.render(source, state.crop, state.parallax)
        dev.liquid.launcher.core.common.ColourTiming.mark("picture placed")
        // Colours now, before Apply (0.11.1: kept for Apply; the icons on screen keep the current wallpaper's).
        val tone = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { dev.liquid.launcher.core.common.WallpaperTones.fromBitmap(bitmap) }
        // All four together (0.11.1): Apply takes the render only when its key matches, so the key may not run ahead of it.
        state.tone = tone
        state.palette = tone?.let { data.paletteFor(it.primary) }
        state.rendered = bitmap
        state.renderedKey = key
        dev.liquid.launcher.core.common.ColourTiming.mark("palette ready for Apply")
    }
    // After placing, the placed picture fades back into the wallpaper on screen instead of vanishing (0.11.1).
    LaunchedEffect(state.adjusting) {
        if (state.adjusting) {
            state.placeLeaving = false
            state.placeShown.snapTo(1f)
        } else if (state.placeShown.value > 0f) {
            state.placeLeaving = true
            try {
                state.placeShown.animateTo(0f, androidx.compose.animation.core.tween(MotionScale.ms(420)))
            } finally {
                state.placeLeaving = false
            }
        }
    }

    /**
     * 0.11.1 (Med): a picture you tap is only picked. It is ringed in the sheet at once and loaded for placing and Apply;
     * the screen keeps the current wallpaper until Apply, which ripples to the new one from the Apply button. (0.10.4
     * to 0.11.0 changed the screen as soon as a picture was tapped.)
     */
    var chooseJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun choose(pick: WallpaperPick) {
        state.choosing = pick
        chooseJob?.cancel()
        chooseJob = scope.launch {
            state.busy = true
            try {
                val source = Wallpapers.source(context, data, pick)
                if (source == null) {
                    controller.toast("Could not read that picture")
                    state.choosing = state.pick
                } else {
                    val item = (pick as? WallpaperPick.Library)?.item
                    val crop = item?.crop ?: CropSpec.Centre
                    val parallax = item?.parallax?.takeIf { it >= 0f } ?: Wallpapers.defaultParallax(data)
                    state.select(pick, source, crop, parallax)
                }
            } finally {
                state.busy = false
            }
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
                state.choosing = WallpaperPick.Library(item)
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

    /** The looks set here, kept (at once: the screen reads them as soon as the sheet goes). */
    fun saveLooks() {
        val settings = data.settings
        settings.set(LauncherSettings.WallpaperLockToo, state.lockToo)
        settings.set(LauncherSettings.WallpaperHomeBlur, state.homeBlur)
        settings.set(LauncherSettings.WallpaperHomeDim, state.homeDim)
        settings.set(LauncherSettings.DrawerHomeBlur, state.drawerBlur)
        settings.set(LauncherSettings.DrawerDim, state.drawerDim)
        settings.set(LauncherSettings.SearchBlur, state.searchBlur)
        settings.set(LauncherSettings.SearchDim, state.searchDim)
    }

    /** Where the Apply button is: a new wallpaper ripples out from there (0.11.1). */
    val applyButton = remember { CoordinatesHolder() }

    fun apply() {
        // Still loading the picture last tapped: Apply waits for it (the button says "Working…").
        if (state.busy || (state.choosing != null && state.choosing != state.pick)) return
        val pick = state.pick
        val source = state.source
        if (pick == null || source == null) {
            // No new picture: the looks and the layout choice are kept, and the sheet goes; the screen already shows them.
            saveLooks()
            // W8 (P8): the picture on screen keeps its own look (or goes back to the one for all) when it was edited.
            if (state.lookTouched) data.wallpapers.get().current?.let { data.wallpapers.setLook(it, state.pictureLook) }
            if (state.forLayout && currentLayout != null && currentLayout.wallpaper == null) {
                data.layouts.setWallpaper(currentLayout.id, store.currentFile, library.current)
            } else if (!state.forLayout && currentLayout?.wallpaper != null) {
                data.layouts.setWallpaper(currentLayout.id, null)
            }
            close()
            return
        }
        // 0.11.1 (Med): the screen kept the current wallpaper until now. The new one is ready (rendered with its colours
        // when it was picked or placed), so the transition starts at once: a picture of the screen as it is, sheet and
        // all, ripples away from the Apply button over the new wallpaper, its icon colours and looks.
        val crop = state.crop
        val parallax = state.parallax
        val look = state.pictureLook
        val lookTouched = state.lookTouched
        val forLayout = state.forLayout
        val layoutId = layouts.current
        dev.liquid.launcher.core.common.ColourTiming.begin("wallpaper screen")
        controller.transitionOrigin = applyButton.value?.takeIf { it.isAttached }?.boundsInRoot()?.center
        controller.changeScreen(
            prepare = {
                state.busy = true
                try {
                    val ready = state.rendered?.takeIf { state.renderedKey == Triple(source, crop, parallax) }
                    val bitmap = ready ?: store.render(source, crop, parallax)
                    val tone = (if (ready != null) state.tone else null)
                        ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { dev.liquid.launcher.core.common.WallpaperTones.fromBitmap(bitmap) }
                    val palette = (if (ready != null) state.palette else null) ?: tone?.let { data.paletteFor(it.primary) }
                    // Closed meanwhile (Cancel, Back, Home): nothing changes.
                    if (!controller.wallpaperStudio) return@changeScreen null
                    // A folder's picture joins the library now, so it is the current picture as soon as it shows.
                    val item = when (pick) {
                        is WallpaperPick.Library -> pick.item
                        is WallpaperPick.External -> data.wallpapers.importUri(pick.uri, pick.folder)
                    }
                    PreparedWallpaper(bitmap, item?.let { WallpaperPick.Library(it) } ?: pick, crop, parallax, tone, palette)
                } finally {
                    state.busy = false
                }
            },
            commit = { prepared ->
                saveLooks()
                (prepared.pick as? WallpaperPick.Library)?.item?.let { item ->
                    data.wallpapers.markUsed(item.id, crop, parallax)
                    if (lookTouched) data.wallpapers.setLook(item.id, look)
                }
                store.show(prepared.bitmap, prepared.tone)
                IconPaint.now(prepared.palette)
                HomeTheme.refreshAccent(context)
                dev.liquid.launcher.core.common.ColourTiming.mark("applied: wallpaper and colours on screen")
                close()
            },
            after = { prepared ->
                // The files and the system wallpaper once the transition is over, so it never stutters.
                val error = Wallpapers.persist(context, data, prepared)
                if (error != null) {
                    controller.toast("Could not set the wallpaper: $error")
                } else if (forLayout && layoutId != null) {
                    data.layouts.setWallpaper(layoutId, store.currentFile, data.wallpapers.get().current)
                }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (state.adjusting) {
            PlaceLayer(state, controller)
        } else {
            // Tapping the preview hides or shows the controls.
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.peek = !state.peek }
            )
        }
        // 0.11.1: the placing bar rises in and sinks away (it appeared and vanished in one frame);
        // above the placing layer, which takes the pinches everywhere else.
        Presence(if (state.adjusting) Unit else null) {
            Box(Modifier.fillMaxSize()) {
                PlaceBar(state, glass, lightBackground, backdrop, color, Modifier.align(Alignment.BottomCenter))
            }
        }
        val sheetShape = RoundedCornerShape(32.dp)
        if (sheetVisible) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .graphicsLayer {
                        val s = shown()
                        translationY = (1f - s) * (size.height + 40.dp.toPx())
                        alpha = s.coerceIn(0f, 1f)
                    }
                    .sheetSurface(backdrop, sheetShape, glass, lightBackground)
                    .bloomLight()
                    .clip(sheetShape)
                    .boardPicture(sheetShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(start = 16.dp, end = 16.dp, top = if (state.page == "pictures") 2.dp else 14.dp, bottom = 14.dp),
            ) {
                if (state.page == "pictures") {
                    SheetHandle(color, tall, with(density) { (expandedGrid - compactGrid).toPx() }) { up ->
                        data.settings.set(LauncherSettings.WallpaperSheetTall, up)
                    }
                }
                Segmented(
                    listOf("pictures" to "Pictures", "look" to "Blur and dim", "auto" to "Automatic"),
                    state.page, color,
                ) { state.page = it }
                Spacer(Modifier.height(12.dp))
                when (state.page) {
                    "pictures" -> PicturesPage(state, library, color, controller, { choose(it) }, ::addPhotos, ::linkFolder, gridHeight)
                    "look" -> LookPage(state, glass, color)
                    else -> AutoPage(data.settings, color)
                }
                if (state.page == "pictures") {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((8 * themeGap).dp)) {
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((10 * themeGap).dp)) {
                    SheetButton("Cancel", color, filled = false, modifier = Modifier.weight(1f)) { close() }
                    SheetButton(
                        if (state.busy) "Working…" else "Apply", color, filled = true,
                        modifier = Modifier.weight(1f).onPlaced { applyButton.value = it },
                    ) { apply() }
                }
            }
        }
        // 0.11.1: it pops in and out like the other small glass parts (it appeared and vanished in one frame).
        Presence(if (state.peek && !state.adjusting) Unit else null) {
            val pop = remember { Animatable(0f) }
            LaunchedEffect(Unit) { pop.animateTo(1f, Motion.pop()) }
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp)
                        .graphicsLayer {
                            val p = pop.value
                            alpha = p.coerceIn(0f, 1f)
                            scaleX = 0.8f + 0.2f * p
                            scaleY = 0.8f + 0.2f * p
                            translationY = (1f - p) * 12.dp.toPx()
                        }
                        .liquidGlass(backdrop, RoundedCornerShape(50), glass.style(Surfaces.Menu, lightBackground))
                        .clip(RoundedCornerShape(50))
                        .clickable { state.peek = false }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) { Text("Show controls", style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)) }
            }
        }
    }

    state.holding?.let { item ->
        AlertDialog(
            onDismissRequest = { state.holding = null },
            title = { Text("Wallpaper") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
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
    gridHeight: androidx.compose.ui.unit.Dp,
) {
    val data = controller.data
    // Where to look: recent (each picture once), favourites, everything, then each linked folder.
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy((6 * themeGap).dp)) {
        TabChip("Recent", state.tab == "recent", color) { state.tab = "recent" }
        TabChip("Favourites", state.tab == "favourites", color) { state.tab = "favourites" }
        TabChip("All", state.tab == "all", color) { state.tab = "all" }
        for (folder in library.folders) TabChip(folder.name, state.tab == folder.uri, color) { state.tab = folder.uri }
        TabChip("+ Folder", false, color) { linkFolder() }
    }
    val folder = library.folders.firstOrNull { it.uri == state.tab }
    if (folder != null) {
        // What can be done with the linked folder, in plain sight above its pictures.
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabChip(if (folder.deep) "Sub-folders: shown" else "Sub-folders: hidden", folder.deep, color) {
                data.wallpapers.setFolderDeep(folder.uri, !folder.deep)
            }
            TabChip("Remove folder", false, color) {
                data.wallpapers.removeFolder(folder.uri)
                state.tab = "recent"
                controller.toast("\"${folder.name}\" is no longer linked. Its pictures stay on the phone")
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    // 0.11.1: the picture tapped is ringed at once, while it loads; with none tapped, the current wallpaper.
    val shownPick = state.choosing ?: state.pick
    val pickedId = (shownPick as? WallpaperPick.Library)?.item?.id ?: library.current.takeIf { shownPick == null }
    val pickedUri = (shownPick as? WallpaperPick.External)?.uri
    // 0.10.4: taller (two rows and a bit; it was one and a half), and the sheet's handle pulls it up further.
    Box(Modifier.fillMaxWidth().height(if (folder != null) gridHeight - 44.dp else gridHeight)) {
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
}

@Composable
private fun FolderGrid(folder: LinkedFolder, picked: android.net.Uri?, color: Color, controller: HomeController, choose: (WallpaperPick) -> Unit) {
    val images by produceState<List<FolderImage>?>(null, folder.uri, folder.deep) {
        value = null
        value = controller.data.wallpapers.folderImages(folder)
    }
    val list = images
    if (list == null) {
        Text("Reading the folder…", style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.75f), fontSize = 13.sp))
        return
    }
    if (list.isEmpty()) {
        Text(if (folder.deep) "No pictures in this folder or its sub-folders." else "No pictures in this folder. \"Sub-folders\" above also looks inside its folders.", style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.75f), fontSize = 13.sp))
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
    // 0.11.1: picking is the change you see here: the ring thickens, the picture settles in a little and a check pops in.
    val picked by androidx.compose.animation.core.animateFloatAsState(
        if (selected) 1f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = 0.68f, stiffness = MotionScale.stiffness(520f)),
        label = "picked",
    )
    // Thumbnails load after the grid shows: they fade in rather than pop.
    val loaded by androidx.compose.animation.core.animateFloatAsState(
        if (image != null) 1f else 0f, androidx.compose.animation.core.tween(MotionScale.ms(220)), label = "thumb",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.56f)
            .clip(shape)
            .background(color.copy(alpha = 0.1f))
            .combinedClickable(onClick = onClick, onLongClick = onHold),
    ) {
        if (image != null) {
            Image(
                image, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val s = 1f - 0.07f * picked.coerceIn(0f, 1.2f)
                    scaleX = s
                    scaleY = s
                    alpha = loaded
                    this.shape = shape
                    clip = true
                },
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .border((0.5f + 1.7f * picked.coerceIn(0f, 1f)).dp, color.copy(alpha = 0.25f + 0.7f * picked.coerceIn(0f, 1f)), shape),
        )
        if (picked > 0.01f) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .graphicsLayer {
                        val p = picked.coerceIn(0f, 1.15f)
                        alpha = p.coerceAtMost(1f)
                        scaleX = 0.5f + 0.5f * p
                        scaleY = 0.5f + 0.5f * p
                    },
            ) { ThemeCheck(true, color, 18.dp) }
        }
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
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.enter()) }
    val presence = LocalPresence.current
    Column(
        modifier
            .navigationBarsPadding()
            .padding(12.dp)
            .graphicsLayer {
                val p = appear.value * presence.value
                alpha = p.coerceIn(0f, 1f)
                translationY = (1f - p) * 40.dp.toPx()
            }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((10 * themeGap).dp)) {
            SheetButton("Reset", color, filled = false, modifier = Modifier.weight(1f)) { state.crop = CropSpec.Centre }
            SheetButton("Done", color, filled = true, modifier = Modifier.weight(1f)) { state.adjusting = false }
        }
    }
}

// ---- blur and dim ----

@Composable
private fun LookPage(state: StudioState, glass: GlassConfig, color: Color) {
    Segmented(listOf("home" to "Home", "picture" to "This picture", "drawer" to "Drawer", "search" to "Search"), state.surface, color) { state.surface = it }
    Spacer(Modifier.height(8.dp))
    when (state.surface) {
        "home" -> {
            ValueSlider("Blur", state.homeBlur, 0, 40, "dp", color) { state.homeBlur = it }
            ValueSlider("Dim", state.homeDim, 0, 80, "%", color) { state.homeDim = it }
            if (state.pictureLook != null) PageLabel("This picture has its own look (see \"This picture\")", color)
        }
        "picture" -> {
            // W8 (P8): a look kept with this picture, used whenever it is the wallpaper.
            val own = state.pictureLook
            ToggleRow("Its own look", own != null, color) { on ->
                state.editLook(if (on) dev.liquid.launcher.core.data.WallpaperLook(blur = state.homeBlur, dim = state.homeDim) else null)
            }
            if (own != null) {
                ValueSlider("Blur", own.blur.coerceAtLeast(0), 0, 40, "dp", color) { state.editLook(own.copy(blur = it)) }
                ValueSlider("Dim", own.dim.coerceAtLeast(0), 0, 80, "%", color) { state.editLook(own.copy(dim = it)) }
                ValueSlider("Saturation", own.saturation, 0, 200, "%", color) { state.editLook(own.copy(saturation = it)) }
                ValueSlider("Vignette", own.vignette, 0, 100, "%", color) { state.editLook(own.copy(vignette = it)) }
            }
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
    // W3: in the theme, the choices are its chips (selected: its selected chip).
    if (LocalControls.current?.theme != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((6 * themeGap).dp)) {
            // 0.10.4: each choice gets room by its label's length, so "Blur and dim" is not cut.
            for ((id, label) in options) ThemeChip(label, selected == id, Modifier.weight(label.length.coerceAtLeast(5).toFloat()), height = 34.dp) { onSelect(id) }
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((6 * themeGap).dp)) {
        for ((id, label) in options) {
            val on = selected == id
            Box(
                Modifier
                    .weight(label.length.coerceAtLeast(5).toFloat())
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
    if (LocalControls.current?.theme != null) {
        ThemeChip(label, on, height = 32.dp, onClick = onClick)
        return
    }
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
    if (LocalControls.current?.theme != null) {
        ThemeChip(label, false, modifier, height = 38.dp, icon = icon, onClick = onClick)
        return
    }
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
    // W3: Cancel is a secondary button of the theme, Apply its main button.
    if (LocalControls.current?.theme != null) {
        GlassButton(label, color, modifier, emphasis = if (filled) ControlEmphasis.Main else ControlEmphasis.Secondary, onClick = onClick)
        return
    }
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

/**
 * The wallpaper sheet's handle (0.10.4): drag it up for more rows of pictures, down for fewer; a tap switches between
 * the two. [rangePx] is how much taller the pictures get at the top.
 */
@Composable
private fun SheetHandle(color: Color, tall: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, rangePx: Float, onSettle: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    fun settle(up: Boolean) {
        scope.launch { tall.animateTo(if (up) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 500f)) }
        onSettle(up)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .pointerInput(rangePx) {
                if (rangePx <= 1f) return@pointerInput
                val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        total = 0f
                    },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        // The handle moves with the sheet, so the speed is taken from the finger's own travel.
                        total += dy
                        tracker.addPosition(change.uptimeMillis, Offset(0f, total))
                        scope.launch { tall.snapTo((tall.value - dy / rangePx).coerceIn(0f, 1f)) }
                    },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().y
                        settle(if (kotlin.math.abs(v) > 600f) v < 0f else tall.value > 0.5f)
                    },
                    onDragCancel = { settle(tall.value > 0.5f) },
                )
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { settle(tall.value < 0.5f) }
            .semantics { contentDescription = "Wallpaper sheet handle" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.45f)))
    }
}
