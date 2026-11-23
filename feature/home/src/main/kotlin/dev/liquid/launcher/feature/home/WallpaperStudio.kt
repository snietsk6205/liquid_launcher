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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.common.Frame
import dev.liquid.launcher.core.data.ColourFamily
import dev.liquid.launcher.core.data.FolderImage
import dev.liquid.launcher.core.data.LibrarySort
import dev.liquid.launcher.core.data.PickerFlow
import dev.liquid.launcher.core.data.PictureShape
import dev.liquid.launcher.core.data.WallpaperCollection
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
import dev.liquid.launcher.core.settings.derive
import kotlinx.coroutines.launch

/**
 * What the wallpaper screen is trying out. The home screen draws its looks (blur, dim) instead of the saved ones while
 * the screen is open, so they are seen on the real thing. A picture picked here is not shown on the screen until it is
 * placed: since 0.11.1 the screen keeps the current wallpaper until Apply, which ripples to the new one; since 0.16.0
 * (D197) a picture placed and Done stays on the screen behind the sheet until Apply, Cancel or another pick.
 */
@Stable
internal class StudioState(store: SettingsStore, currentLook: dev.liquid.launcher.core.data.WallpaperLook?) {
    /** 0.16.0 (D197): the screen's steps (pick, place, Done, Apply, the peek, selecting) as plain, tested data. */
    var flow by mutableStateOf(PickerFlow())

    /** The picture picked (loaded for placing and Apply), or null for the current wallpaper. */
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

    /** [rendered] as the home screen draws it: the preview after Done (0.16.0). */
    var renderedImage by mutableStateOf<ImageBitmap?>(null)

    /** The picture, placement and parallax [rendered] was made from (0.10.4): a picture picked with the transition arrives rendered. */
    var renderedKey: Any? = null

    /** Its colours, worked out as soon as it is rendered (before Apply), and the themed icon palette from them. */
    var tone: dev.liquid.launcher.core.common.WallpaperTone? = null
    var palette: Pair<Int, Int>? = null

    /** Placing mode: pinch and drag on the screen. */
    val adjusting: Boolean get() = flow.placing

    /** 0.16.0: a picture that stopped showing (a preview that ended, the peek), fading back into the wallpaper. */
    var fade by mutableStateOf<FadeOut?>(null)
    val fadeAlpha = Animatable(0f)

    /** 0.16.0: the screen is going (Cancel after a preview): the sheet sinks while the preview fades back. */
    var leaving by mutableStateOf(false)

    /** 0.16.0: the picture peeked at (long press): its thumbnail at once, then the whole picture, with its own placement. */
    var peekImage by mutableStateOf<ImageBitmap?>(null)
    var peekCrop by mutableStateOf(CropSpec.Centre)
    var peekParallax by mutableFloatStateOf(0f)
    var peekLook: dev.liquid.launcher.core.data.WallpaperLook? = null
    var peekPick: WallpaperPick? = null

    /** A short slide across the pages to show the parallax, or null to follow the pages. */
    var sweep by mutableStateOf<Float?>(null)

    /** "pictures", "look" or "auto". */
    var page by mutableStateOf("pictures")

    /** "recent", "favourites", "all", "col:<collection id>" or a linked folder's uri. */
    var tab by mutableStateOf("recent")

    /** "home", "drawer" or "search": which screen the preview shows. */
    var surface by mutableStateOf("home")
    var homeBlur by mutableIntStateOf(store.get(LauncherSettings.WallpaperHomeBlur))
    var homeDim by mutableIntStateOf(store.get(LauncherSettings.WallpaperHomeDim))
    var drawerBlur by mutableIntStateOf(store.get(LauncherSettings.DrawerHomeBlur))
    var drawerDim by mutableIntStateOf(store.get(LauncherSettings.DrawerDim))
    var searchBlur by mutableIntStateOf(store.get(LauncherSettings.SearchBlur))
    var searchDim by mutableIntStateOf(store.get(LauncherSettings.SearchDim))
    /** 0.16.0 (D203): "off", "same" or "chosen" (the lock screen's own picture). */
    var lockMode by mutableStateOf(store.get(LauncherSettings.WallpaperLockMode))

    /** 0.16.0 (D202): the layout's own look besides blur and dim: colour, and a tint (hex, "" none) with its strength. */
    var homeSaturation by mutableIntStateOf(store.get(LauncherSettings.WallpaperHomeSaturation))
    var homeTint by mutableStateOf(store.get(LauncherSettings.WallpaperHomeTint))
    var homeTintAmount by mutableIntStateOf(store.get(LauncherSettings.WallpaperHomeTintAmount))

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

    /**
     * The look on the screen: the current wallpaper's until a look is edited here (0.11.1); a picture placed or peeked at
     * shows with its own (0.16.0).
     */
    val shownLook: dev.liquid.launcher.core.data.WallpaperLook?
        get() = when (flow.shows) {
            PickerFlow.Shows.PEEK -> peekLook
            PickerFlow.Shows.PLACING, PickerFlow.Shows.PLACED -> pictureLook
            PickerFlow.Shows.CURRENT -> if (pick == null || lookTouched) pictureLook else currentLook
        }
    var forLayout by mutableStateOf(false)
    var busy by mutableStateOf(false)

    /** Controls hidden to look at the whole screen. */
    var peek by mutableStateOf(false)

    /** 0.16.0 (D197): each list's scroll position, kept here so it survives the sheet stepping aside while placing. */
    private val grids = HashMap<String, LazyGridState>()

    fun grid(key: String): LazyGridState = grids.getOrPut(key) { LazyGridState() }

    /** 0.16.0: the order of the pictures (remembered), the colour and shape filters (for this visit), and their card. */
    var sort by mutableStateOf(LibrarySort.of(store.get(LauncherSettings.WallpaperLibrarySort)))
    var family by mutableStateOf<ColourFamily?>(null)
    var shape by mutableStateOf<PictureShape?>(null)
    var sortCard by mutableStateOf(false)

    /** 0.16.0 (D198): the row of collections to add the selected pictures to; a collection being named or changed. */
    var collectionChooser by mutableStateOf(false)
    var naming by mutableStateOf<Naming?>(null)
    var collectionMenu by mutableStateOf<WallpaperCollection?>(null)

    /** How many pictures a delete is about to take away, while it asks first (no Undo without wallpaper notices). */
    var confirmDelete by mutableStateOf<Int?>(null)

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
        renderedImage = null
        renderedKey = null
        tone = null
        palette = null
    }

    /** No picture picked any more (it was deleted): the screen goes back to the current wallpaper. */
    fun clearPick() {
        pick = null
        choosing = null
        source = null
        sourceImage = null
        rendered = null
        renderedImage = null
        renderedKey = null
        tone = null
        palette = null
        pictureLook = currentLook
        lookTouched = false
    }

    /** The part of the picture the wallpaper canvas covers, for a screen of [w] × [h]. */
    fun frame(w: Float, h: Float): Frame? {
        val src = source ?: return null
        return crop.frame(src.width, src.height, w * (1f + parallax), h)
    }

    /** The part of the peeked picture that shows, with its own placement. */
    fun peekFrame(w: Float, h: Float): Frame? {
        val image = peekImage ?: return null
        return peekCrop.frame(image.width, image.height, w * (1f + peekParallax), h)
    }

    /**
     * True while the home screen draws the picked picture instead of the wallpaper: while placing it, and after Done
     * until its finished picture is ready ([shownImage] takes over then, blur and all).
     */
    val drawsSource: Boolean
        get() = sourceImage != null && (flow.shows == PickerFlow.Shows.PLACING || (flow.shows == PickerFlow.Shows.PLACED && renderedImage == null))

    /** True while the home screen draws the picture peeked at (long press). */
    val drawsPeek: Boolean get() = flow.shows == PickerFlow.Shows.PEEK && peekImage != null

    /** 0.16.0 (D197): the placed picture, finished, standing in for the wallpaper after Done (until Apply, Cancel, another pick). */
    val shownImage: ImageBitmap? get() = if (flow.shows == PickerFlow.Shows.PLACED) renderedImage else null
}

/** 0.16.0: a picture fading back into the wallpaper; [frame] null: a finished wallpaper (it slides with the pages). */
internal class FadeOut(val image: ImageBitmap, val frame: ((Float, Float) -> Frame?)?)

/** 0.16.0 (D198): a collection being named: a new one ([collection] null, then [then] gets it) or one renamed. */
internal class Naming(val collection: WallpaperCollection?, val then: ((WallpaperCollection) -> Unit)? = null)

private fun keyOf(pick: WallpaperPick): String = when (pick) {
    is WallpaperPick.Library -> PickerFlow.keyOfLibrary(pick.item.id)
    is WallpaperPick.External -> PickerFlow.keyOfUri(pick.uri.toString())
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
    // 0.16.0: the sheet steps aside while a picture is placed or peeked at, and sinks as the screen goes after a preview.
    val toggled = animateFloatAsState(if (state.peek || state.flow.sheetAside || state.leaving) 0f else 1f, label = "sheet")
    // Rises in like the other glass sheets when the screen opens.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.enter()) }
    // 0.11.1: read while drawing, not composing: the whole screen recomposed on every frame of its entrance before.
    fun shown(): Float = toggled.value * appear.value.coerceIn(0f, 1.05f)
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
    // 0.16.0 (D202): the library opens on the current layout's collection, when it has pictures (All is one tap away).
    LaunchedEffect(Unit) {
        if (controller.sharedPick != null) return@LaunchedEffect
        val mine = Wallpapers.layoutCollection(data) ?: return@LaunchedEffect
        if (WallpaperLibraryOps.inCollection(data.wallpapers.get(), mine).isNotEmpty()) state.tab = "col:$mine"
    }
    // 0.16.0: the colour and shape of pictures added before (for sorting and filtering), and deleted pictures whose Undo is long gone.
    LaunchedEffect(Unit) {
        data.wallpapers.describeMissing()
        data.wallpapers.purge(olderThanMs = 10 * 60_000L)
    }

    fun close() {
        controller.wallpaperStudio = false
    }
    // However the screen closes (Cancel, Apply, back, Home), the drawer preview goes away with it,
    // and icons go back to the colours of the wallpaper actually set.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (controller.drawer.value > 0f) controller.closeDrawer()
            IconPaint.endPreview(controller.scope)
            controller.wallpaperStudioBack = null
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
        // All together (0.11.1): Apply takes the render only when its key matches, so the key may not run ahead of it.
        state.tone = tone
        state.palette = tone?.let { data.paletteFor(it.primary) }
        state.rendered = bitmap
        state.renderedImage = bitmap.asImageBitmap()
        state.renderedKey = key
        dev.liquid.launcher.core.common.ColourTiming.mark("palette ready for Apply")
    }

    /** 0.16.0: what stopped showing fades back into the wallpaper instead of vanishing in one frame. */
    fun fadeOut(image: ImageBitmap?, frame: ((Float, Float) -> Frame?)?) {
        if (image == null) return
        state.fade = FadeOut(image, frame)
        scope.launch {
            state.fadeAlpha.snapTo(1f)
            try {
                state.fadeAlpha.animateTo(0f, tween(MotionScale.ms(420)))
            } finally {
                if (state.fade?.image === image) state.fade = null
            }
        }
    }

    /** The preview of a placed picture ends (another pick, Cancel): it fades back into the current wallpaper. */
    fun endPreview() {
        val shows = state.flow.shows
        if (shows != PickerFlow.Shows.PLACED && shows != PickerFlow.Shows.PLACING) return
        val finished = state.renderedImage
        if (finished != null && shows == PickerFlow.Shows.PLACED) {
            fadeOut(finished, null)
        } else {
            val src = state.source ?: return
            val crop = state.crop
            val parallax = state.parallax
            fadeOut(state.sourceImage) { w, h -> crop.frame(src.width, src.height, w * (1f + parallax), h) }
        }
    }

    /**
     * 0.11.1 (Med): a picture you tap is only picked. It is ringed in the sheet at once and loaded for placing and Apply;
     * the screen keeps the current wallpaper until Apply, which ripples to the new one from the Apply button. (0.10.4
     * to 0.11.0 changed the screen as soon as a picture was tapped.) 0.16.0: while selecting, a tap chooses the picture
     * instead; tapping the picture already picked keeps it as it is, placement included.
     */
    var chooseJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun choose(pick: WallpaperPick) {
        val key = keyOf(pick)
        if (state.flow.selecting) {
            if (pick is WallpaperPick.Library) state.flow = state.flow.toggle(key)
            return
        }
        if (key == state.flow.chosen && key == state.flow.loaded) return
        endPreview()
        state.flow = state.flow.tap(key)
        state.choosing = pick
        chooseJob?.cancel()
        chooseJob = scope.launch {
            state.busy = true
            try {
                val source = Wallpapers.source(context, data, pick)
                if (source == null) {
                    controller.toast("Could not read that picture")
                    state.flow = state.flow.failed(key)
                    state.choosing = state.pick
                } else {
                    val item = (pick as? WallpaperPick.Library)?.item
                    // 0.16.0 (D202): the layout's own picture comes placed as this layout has it.
                    val placed = item?.let { Wallpapers.layoutPlacement(data, it.id) }
                    val crop = placed?.first ?: item?.crop ?: CropSpec.Centre
                    val parallax = placed?.second ?: item?.parallax?.takeIf { it >= 0f } ?: Wallpapers.defaultParallax(data)
                    state.select(pick, source, crop, parallax)
                    state.flow = state.flow.loaded(key)
                }
            } finally {
                state.busy = false
            }
        }
    }

    fun placeCurrent() {
        if (state.flow.waiting) return
        if (state.source != null && state.flow.applies != null) {
            state.flow = state.flow.place()
            return
        }
        val item = library.current?.let { data.wallpapers.find(it) }
        if (item == null) {
            controller.toast("Pick a picture first")
            return
        }
        scope.launch {
            state.busy = true
            val pick = WallpaperPick.Library(item)
            val key = keyOf(pick)
            Wallpapers.source(context, data, pick)?.let { source ->
                state.choosing = pick
                state.flow = state.flow.tap(key)
                val placed = Wallpapers.layoutPlacement(data, item.id)
                state.select(pick, source, placed?.first ?: item.crop, placed?.second ?: item.parallax.takeIf { it >= 0f } ?: Wallpapers.defaultParallax(data))
                state.flow = state.flow.loaded(key).place()
            }
            state.busy = false
        }
    }

    /** Cancel: a picture placed on the screen fades back into the current wallpaper as the sheet goes (0.16.0). */
    fun cancel() {
        if (state.flow.shows == PickerFlow.Shows.PLACED || state.flow.shows == PickerFlow.Shows.PLACING) {
            endPreview()
            state.flow = state.flow.cancel()
            state.leaving = true
            scope.launch {
                kotlinx.coroutines.delay(MotionScale.ms(420).toLong())
                close()
            }
        } else {
            close()
        }
    }

    /** 0.16.0: a long press shows the picture full screen with the home screen over it; the sheet steps aside. */
    fun peekAt(pick: WallpaperPick) {
        val key = keyOf(pick)
        val item = (pick as? WallpaperPick.Library)?.item
        state.peekPick = pick
        state.peekCrop = item?.crop ?: CropSpec.Centre
        state.peekParallax = item?.parallax?.takeIf { it >= 0f } ?: Wallpapers.defaultParallax(data)
        state.peekLook = item?.look
        scope.launch {
            // The thumbnail at once (it is cached), the whole picture as soon as it is decoded.
            val thumb = when (pick) {
                is WallpaperPick.Library -> data.wallpapers.thumbnail(pick.item)
                is WallpaperPick.External -> data.wallpapers.thumbnail(pick.uri)
            }
            state.peekImage = thumb
            state.flow = state.flow.peek(key)
            DebugLog.event("Wallpaper", "peek at a picture (long press)")
            val full = Wallpapers.source(context, data, pick)
            if (full != null && state.flow.peeking == key) state.peekImage = full.asImageBitmap()
        }
    }

    fun endPeek() {
        if (state.flow.peeking == null) return
        val image = state.peekImage
        val crop = state.peekCrop
        val parallax = state.peekParallax
        state.flow = state.flow.endPeek()
        if (image != null) fadeOut(image) { w, h -> crop.frame(image.width, image.height, w * (1f + parallax), h) }
        state.peekImage = null
    }

    /** The peek's "Pick": the picture peeked at becomes the pick. */
    fun pickPeeked() {
        val pick = state.peekPick ?: return endPeek()
        endPeek()
        choose(pick)
    }

    // ---- several pictures at once (0.16.0) ----

    fun selectedIds(): Set<String> = state.flow.selection.mapNotNull { PickerFlow.libraryId(it) }.toSet()

    fun count(n: Int) = if (n == 1) "1 picture" else "$n pictures"

    fun favouriteSelected() {
        val ids = selectedIds()
        if (ids.isEmpty()) return
        val on = !ids.all { data.wallpapers.find(it)?.favourite == true }
        data.wallpapers.setFavourite(ids, on)
        controller.notice(dev.liquid.launcher.core.common.NoticeKind.Info, if (on) "${count(ids.size)} added to Favourites" else "${count(ids.size)} taken out of Favourites")
    }

    fun addSelectedTo(collection: WallpaperCollection?) {
        val ids = selectedIds()
        if (ids.isEmpty()) return
        state.collectionChooser = false
        if (collection == null) {
            data.wallpapers.setFavourite(ids, true)
            controller.notice(dev.liquid.launcher.core.common.NoticeKind.Info, "${count(ids.size)} added to Favourites")
        } else {
            data.wallpapers.addToCollection(ids, collection.id)
            controller.notice(dev.liquid.launcher.core.common.NoticeKind.Info, "${count(ids.size)} added to \"${collection.name}\"")
        }
    }

    fun forgetSelected() {
        val ids = selectedIds()
        if (ids.isEmpty()) return
        Wallpapers.forget(data, ids)
        controller.notice(dev.liquid.launcher.core.common.NoticeKind.Info, "${count(ids.size)} taken out of Recent")
    }

    /**
     * Takes the selected pictures out of the library; a glass notice offers Undo for 10 s (the files wait meanwhile).
     * With wallpaper notices turned off there would be no Undo, so it asks first.
     */
    fun deleteSelected(confirmed: Boolean = false) {
        val ids = selectedIds()
        if (ids.isEmpty()) return
        val notices = data.settings.noticeConfig()
        val undoShows = notices.enabled && dev.liquid.launcher.core.common.NoticeKind.Wallpaper.id in notices.kinds
        if (!undoShows && !confirmed) {
            state.confirmDelete = ids.size
            return
        }
        val keys = ids.map { PickerFlow.keyOfLibrary(it) }.toSet()
        if (state.flow.loaded in keys || state.flow.chosen in keys) {
            endPreview()
            state.clearPick()
        }
        state.flow = state.flow.without(keys).endSelecting()
        val deleted = data.wallpapers.delete(ids) ?: return
        if (!undoShows) {
            data.wallpapers.purge()
            return
        }
        dev.liquid.launcher.core.common.Notices.post(
            dev.liquid.launcher.core.common.Notice(
                dev.liquid.launcher.core.common.NoticeKind.Wallpaper,
                "${count(deleted.items.size)} deleted",
                buttonLabel = "Undo",
                button = { data.wallpapers.restore(deleted) },
                showMs = UNDO_MS,
                holds = true,
            )
        )
    }

    /** Takes a collection away (its pictures stay in the library). */
    fun removeCollection(collection: WallpaperCollection) {
        data.wallpapers.removeCollection(collection.id)
        if (state.tab == "col:" + collection.id) state.tab = "all"
        controller.notice(dev.liquid.launcher.core.common.NoticeKind.Info, "Collection \"${collection.name}\" removed", "Its pictures stay in the library")
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
                data.wallpapers.describeMissing()
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

    /** 0.16.0 (D203): the picture picked (or the current one) becomes the lock screen's own, placed as it is here. */
    fun setLockPicture() {
        val source = state.source
        val crop = state.crop
        val item = (state.pick as? WallpaperPick.Library)?.item ?: library.current?.takeIf { state.pick == null }?.let { data.wallpapers.find(it) }
        scope.launch {
            // The lock screen does not slide with pages: no parallax.
            val bitmap = if (source != null) store.render(source, crop, 0f)
            else item?.let { Wallpapers.prepare(context, data, WallpaperPick.Library(it), parallax = 0f)?.bitmap }
            if (bitmap == null) {
                controller.toast("Pick a picture first")
                return@launch
            }
            state.lockMode = "chosen"
            data.settings.set(LauncherSettings.WallpaperLockMode, "chosen")
            data.settings.set(LauncherSettings.WallpaperLockPicture, item?.id ?: "")
            controller.notice(dev.liquid.launcher.core.common.NoticeKind.Wallpaper, "Setting the lock screen…", "The phone takes a few seconds")
            val error = store.setLockScreen(bitmap)
            controller.notice(
                dev.liquid.launcher.core.common.NoticeKind.Wallpaper,
                if (error == null) "Lock screen wallpaper set" else "The lock screen could not be set",
                error,
            )
        }
    }

    /** The looks set here, kept (at once: the screen reads them as soon as the sheet goes). */
    fun saveLooks() {
        val settings = data.settings
        settings.set(LauncherSettings.WallpaperLockMode, state.lockMode)
        settings.set(LauncherSettings.WallpaperHomeSaturation, state.homeSaturation)
        settings.set(LauncherSettings.WallpaperHomeTint, state.homeTint)
        settings.set(LauncherSettings.WallpaperHomeTintAmount, state.homeTintAmount)
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
        if (state.busy || state.flow.waiting) return
        val pick = state.pick
        val source = state.source
        if (pick == null || source == null) {
            // No new picture: the looks and the layout choice are kept, and the sheet goes; the screen already shows them.
            saveLooks()
            // W8 (P8): the picture on screen keeps its own look (or goes back to the one for all) when it was edited.
            if (state.lookTouched) data.wallpapers.get().current?.let { data.wallpapers.setLook(it, state.pictureLook) }
            // One wallpaper for all (0.16.0: "Each layout has its own wallpaper" off): the layout switch as before.
            if (Wallpapers.ownLayout(data) == null) {
                if (state.forLayout && currentLayout != null && currentLayout.wallpaper == null) {
                    data.layouts.setWallpaper(currentLayout.id, store.currentFile, library.current)
                } else if (!state.forLayout && currentLayout?.wallpaper != null) {
                    data.layouts.setWallpaper(currentLayout.id, null)
                }
            }
            close()
            return
        }
        // 0.11.1 (Med): the screen kept the current wallpaper until now (0.16.0: or showed the placed picture). The new one
        // is ready (rendered with its colours when it was picked or placed), so the transition starts at once: a picture of
        // the screen as it is, sheet and all, ripples away from the Apply button over the new wallpaper and its colours.
        val crop = state.crop
        val parallax = state.parallax
        val look = state.pictureLook
        val lookTouched = state.lookTouched
        // 0.16.0 (D202): the layout that gets it as its own: the current one when each layout has its own, else the
        // current one when "Only for the layout" is on.
        val targetLayout = Wallpapers.ownLayout(data) ?: layouts.current?.takeIf { state.forLayout && currentLayout != null }
        dev.liquid.launcher.core.common.ColourTiming.begin("wallpaper screen")
        DebugLog.event("Wallpaper", "apply: " + if (state.flow.shows == PickerFlow.Shows.PLACED) "the placed picture" else "the picture picked")
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
                    // 0.16.0 (D200): what this replaces, for Undo, before anything is written.
                    controller.keepForUndo((pick as? WallpaperPick.Library)?.item?.id, targetLayout)
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
                    Wallpapers.markUsed(data, item.id, crop, parallax)
                    if (lookTouched) data.wallpapers.setLook(item.id, look)
                }
                store.show(prepared.bitmap, prepared.tone)
                IconPaint.now(prepared.palette)
                HomeTheme.refreshAccent(context)
                dev.liquid.launcher.core.common.ColourTiming.mark("applied: wallpaper and colours on screen")
                close()
            },
            after = { prepared ->
                controller.offerWallpaperUndo("New wallpaper")
                // The files and the system wallpaper once the transition is over, so it never stutters (its use was
                // counted as it showed); the layout's own wallpaper with it.
                val error = Wallpapers.persist(context, data, prepared, layout = targetLayout, markUsed = false)
                if (error != null) controller.toast("Could not set the wallpaper: $error")
            },
        )
    }

    // 0.16.0: Back goes one step back inside the screen first (cards, the peek, selecting, placing), then closes it.
    androidx.compose.runtime.SideEffect {
        controller.wallpaperStudioBack = {
            val back = state.flow.back()
            when {
                state.confirmDelete != null -> state.confirmDelete = null
                state.naming != null -> state.naming = null
                state.collectionMenu != null -> state.collectionMenu = null
                state.collectionChooser -> state.collectionChooser = false
                state.sortCard -> state.sortCard = false
                state.flow.peeking != null -> endPeek()
                back != null -> state.flow = back
                state.peek -> state.peek = false
                else -> cancel()
            }
            true
        }
    }

    // 0.16.0: a picture shared to "Set as Liquid wallpaper" is picked as the screen opens (or while it is open).
    LaunchedEffect(controller.sharedPick) {
        val item = controller.sharedPick ?: return@LaunchedEffect
        controller.sharedPick = null
        state.flow = state.flow.endSelecting()
        state.page = "pictures"
        state.tab = "all"
        state.grid("tab:all").scrollToItem(0)
        choose(WallpaperPick.Library(item))
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.adjusting -> PlaceLayer(state, controller)
            // 0.16.0: the peek ends with a tap anywhere.
            state.flow.peeking != null -> Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { endPeek() }
            )
            // Tapping the preview hides or shows the controls.
            else -> Box(
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
        // 0.16.0: the peek's own bar: back, or pick the picture peeked at.
        Presence(if (state.flow.peeking != null) Unit else null) {
            Box(Modifier.fillMaxSize()) {
                PeekBar(glass, lightBackground, backdrop, color, canPick = !state.flow.selecting, Modifier.align(Alignment.BottomCenter), ::endPeek, ::pickPeeked)
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
                    "pictures" -> PicturesPage(
                        state, library, color, controller,
                        choose = { choose(it) },
                        peek = { peekAt(it) },
                        addPhotos = ::addPhotos,
                        linkFolder = ::linkFolder,
                        newCollection = { state.naming = Naming(null) },
                        gridHeight = gridHeight,
                    )
                    "look" -> LookPage(state, glass, color, currentLayout?.name, ::setLockPicture)
                    else -> AutoPage(data.settings, color)
                }
                if (state.page == "pictures") {
                    Spacer(Modifier.height(10.dp))
                    if (state.flow.selecting) {
                        PictureSelectionBar(
                            state, library, color,
                            onFavourite = ::favouriteSelected,
                            onCollection = { state.collectionChooser = !state.collectionChooser },
                            onForget = ::forgetSelected,
                            onDelete = { deleteSelected() },
                            onAddTo = ::addSelectedTo,
                            onNewCollection = { state.naming = Naming(null) { c -> addSelectedTo(c) } },
                        )
                    } else {
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
                }
                val ownWallpapers by data.settings.derive { get(LauncherSettings.LayoutsOwnWallpaper) }
                if (currentLayout != null && !state.flow.selecting && !ownWallpapers) {
                    ToggleRow("Only for the layout \"${currentLayout.name}\"", state.forLayout, color) { state.forLayout = it }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((10 * themeGap).dp)) {
                    if (state.flow.selecting && state.page == "pictures") {
                        val shownIds = remember(library, state.tab, state.sort, state.family, state.shape) { shownItems(state, library, data).map { PickerFlow.keyOfLibrary(it.id) } }
                        SheetButton("Select all", color, filled = false, modifier = Modifier.weight(1f)) { state.flow = state.flow.selectAll(shownIds) }
                        SheetButton("Done", color, filled = true, modifier = Modifier.weight(1f)) {
                            state.collectionChooser = false
                            state.flow = state.flow.endSelecting()
                        }
                    } else {
                        SheetButton("Cancel", color, filled = false, modifier = Modifier.weight(1f)) { cancel() }
                        SheetButton(
                            if (state.busy) "Working…" else "Apply", color, filled = true,
                            modifier = Modifier.weight(1f).onPlaced { applyButton.value = it },
                        ) { apply() }
                    }
                }
            }
        }
        // 0.11.1: it pops in and out like the other small glass parts (it appeared and vanished in one frame).
        Presence(if (state.peek && !state.adjusting && state.flow.peeking == null) Unit else null) {
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

    // 0.16.0 (D198): naming a new collection, renaming one, or taking one away.
    state.naming?.let { naming ->
        var text by remember(naming) { mutableStateOf(naming.collection?.name ?: "") }
        AlertDialog(
            onDismissRequest = { state.naming = null },
            title = { Text(if (naming.collection == null) "New collection" else "Rename collection") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    placeholder = { Text("For example Study or Projects") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = text.trim()
                    state.naming = null
                    if (name.isEmpty()) return@TextButton
                    val existing = naming.collection
                    if (existing == null) {
                        val made = data.wallpapers.addCollection(name)
                        naming.then?.invoke(made) ?: run { state.tab = "col:" + made.id }
                    } else {
                        data.wallpapers.renameCollection(existing.id, name)
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { state.naming = null }) { Text("Cancel") } },
        )
    }
    state.confirmDelete?.let { n ->
        AlertDialog(
            onDismissRequest = { state.confirmDelete = null },
            title = { Text(if (n == 1) "Delete 1 picture?" else "Delete $n pictures?") },
            text = { Text("They leave the library for good (wallpaper notices are off, so there is no Undo). Pictures in linked folders stay on the phone.") },
            confirmButton = {
                TextButton(onClick = {
                    state.confirmDelete = null
                    deleteSelected(confirmed = true)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { state.confirmDelete = null }) { Text("Cancel") } },
        )
    }
    state.collectionMenu?.let { collection ->
        AlertDialog(
            onDismissRequest = { state.collectionMenu = null },
            title = { Text(collection.name) },
            text = {
                Column {
                    DialogLine("Rename") {
                        state.collectionMenu = null
                        state.naming = Naming(collection)
                    }
                    // 0.16.0 (D202): a collection can be given to the layout you are in.
                    val layout = layouts.current?.let { id -> layouts.layouts.firstOrNull { it.id == id } }
                    if (layout != null && data.settings.get(LauncherSettings.LayoutsOwnCollection) && layout.collection != collection.id) {
                        DialogLine("The collection of \"${layout.name}\"") {
                            state.collectionMenu = null
                            data.layouts.setCollection(layout.id, collection.id)
                            controller.notice(dev.liquid.launcher.core.common.NoticeKind.Info, "\"${collection.name}\" is the collection of \"${layout.name}\"")
                        }
                    }
                    DialogLine("Remove the collection (its pictures stay)") {
                        state.collectionMenu = null
                        removeCollection(collection)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { state.collectionMenu = null }) { Text("Close") } },
        )
    }
}

// ---- pictures ----

/** The pictures of the list shown (a tab or a collection), filtered and in the order chosen. Not for a folder. */
private fun shownItems(state: StudioState, library: dev.liquid.launcher.core.data.WallpaperLibraryState, data: dev.liquid.launcher.core.data.LauncherData): List<WallpaperItem> {
    val tab = state.tab
    val base = when {
        tab == "favourites" -> WallpaperLibraryOps.favourites(library)
        tab == "all" -> WallpaperLibraryOps.all(library)
        tab.startsWith("col:") -> WallpaperLibraryOps.inCollection(library, tab.removePrefix("col:"))
        // 0.16.0 (D202): the current layout's own recents, when each layout has its own.
        tab == "recent" -> Wallpapers.recent(data, library)
        else -> return emptyList()
    }
    return WallpaperLibraryOps.sorted(WallpaperLibraryOps.filtered(base, state.family, state.shape), state.sort, recentList = tab == "recent")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PicturesPage(
    state: StudioState,
    library: dev.liquid.launcher.core.data.WallpaperLibraryState,
    color: Color,
    controller: HomeController,
    choose: (WallpaperPick) -> Unit,
    peek: (WallpaperPick) -> Unit,
    addPhotos: () -> Unit,
    linkFolder: () -> Unit,
    newCollection: () -> Unit,
    gridHeight: androidx.compose.ui.unit.Dp,
) {
    val data = controller.data
    // 0.16.0: the three lists first, easy to see, with the order and filter card and Select on the right.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((6 * themeGap).dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy((6 * themeGap).dp)) {
            TabChip("Recent", state.tab == "recent", color) { state.tab = "recent" }
            TabChip("Favourites", state.tab == "favourites", color) { state.tab = "favourites" }
            TabChip("All", state.tab == "all", color) { state.tab = "all" }
        }
        val filtering = state.family != null || state.shape != null || state.sort != LibrarySort.NEWEST
        IconChip(LauncherIcons.Sort, "Order and filter", state.sortCard || filtering, color) { state.sortCard = !state.sortCard }
        IconChip(LauncherIcons.CheckCircle, "Select", state.flow.selecting, color) {
            state.flow = if (state.flow.selecting) state.flow.endSelecting() else state.flow.startSelecting()
            state.collectionChooser = false
        }
    }
    // Collections (0.16.0, D198), then the linked folders, then making either.
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy((6 * themeGap).dp),
    ) {
        // 0.16.0 (D202): the current layout's collection first.
        val mine = Wallpapers.layoutCollection(data)
        for (collection in library.collections.sortedBy { if (it.id == mine) 0 else 1 }) {
            TabChip(collection.name, state.tab == "col:" + collection.id, color, onLongClick = { state.collectionMenu = collection }) { state.tab = "col:" + collection.id }
        }
        for (folder in library.folders) TabChip(folder.name, state.tab == folder.uri, color) { state.tab = folder.uri }
        TabChip("+ Collection", false, color) { newCollection() }
        TabChip("+ Folder", false, color) { linkFolder() }
    }
    androidx.compose.animation.AnimatedVisibility(state.sortCard) {
        SortCard(state, color, data.settings)
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
    val shownKey = state.flow.chosen
    val pickedId = PickerFlow.libraryId(shownKey) ?: library.current.takeIf { shownKey == null }
    val pickedUri = shownKey?.takeIf { it.startsWith("uri:") }?.removePrefix("uri:")
    // 0.10.4: taller (two rows and a bit; it was one and a half), and the sheet's handle pulls it up further.
    Box(Modifier.fillMaxWidth().height(if (folder != null) gridHeight - 44.dp else gridHeight)) {
        if (folder != null) {
            FolderGrid(folder, pickedUri, color, controller, state.grid("folder:" + folder.uri), choose, peek)
        } else {
            val layoutsNow by data.layouts.state.collectAsState()
            val items = remember(library, layoutsNow.current, state.tab, state.sort, state.family, state.shape) { shownItems(state, library, data) }
            val selecting = state.flow.selecting
            val selection = state.flow.selection
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                // 0.16.0 (D197): the list keeps its place while a picture is placed (the sheet steps aside and comes back).
                state = state.grid("tab:" + state.tab),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "add") { AddThumb(color, addPhotos) }
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    val key = PickerFlow.keyOfLibrary(item.id)
                    LibraryThumb(
                        item, index, item.id == pickedId && !selecting, if (selecting) key in selection else null, color, controller,
                        onHold = { peek(WallpaperPick.Library(item)) },
                    ) { choose(WallpaperPick.Library(item)) }
                }
            }
            if (items.isEmpty()) {
                val filtered = state.family != null || state.shape != null
                val text = when {
                    filtered -> "No picture here matches the filter. \"Order and filter\" above clears it."
                    state.tab == "favourites" -> "Select pictures, or use the heart below, to make them favourites."
                    state.tab.startsWith("col:") -> "Select pictures and add them to this collection."
                    state.tab == "recent" -> null
                    else -> "Add photos with +, or link a folder."
                }
                if (text != null) {
                    Text(
                        text,
                        modifier = Modifier.align(Alignment.Center).padding(top = 60.dp, start = 12.dp, end = 12.dp),
                        style = TextStyle(fontFamily = HomeTheme.font, color = color.copy(alpha = 0.75f), fontSize = 13.sp),
                    )
                }
            }
        }
    }
}

/** 0.16.0: order (newest, most used, colour) and the colour and shape filters. */
@Composable
private fun SortCard(state: StudioState, color: Color, settings: SettingsStore) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.07f))
            .padding(12.dp),
    ) {
        PageLabel("Order", color)
        Segmented(LibrarySort.entries.map { it.id to it.label }, state.sort.id, color) { id ->
            state.sort = LibrarySort.of(id)
            settings.set(LauncherSettings.WallpaperLibrarySort, id)
        }
        PageLabel("Colour", color)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (family in ColourFamily.entries) {
                val on = state.family == family
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(family.swatch))
                        .border(if (on) 2.5.dp else 0.5.dp, color.copy(alpha = if (on) 0.95f else 0.3f), CircleShape)
                        .clickable { state.family = if (on) null else family }
                        .semantics { contentDescription = family.label + if (on) ", chosen" else "" },
                )
            }
        }
        PageLabel("Shape", color)
        Segmented(
            listOf("any" to "Any") + PictureShape.entries.map { it.name to it.label },
            state.shape?.name ?: "any", color,
        ) { id -> state.shape = PictureShape.entries.firstOrNull { it.name == id } }
        if (state.family != null || state.shape != null) {
            Spacer(Modifier.height(8.dp))
            TabChip("Clear the filter", false, color) {
                state.family = null
                state.shape = null
            }
        }
    }
}

/** 0.16.0: what can be done with the pictures selected. */
@Composable
private fun PictureSelectionBar(
    state: StudioState,
    library: dev.liquid.launcher.core.data.WallpaperLibraryState,
    color: Color,
    onFavourite: () -> Unit,
    onCollection: () -> Unit,
    onForget: () -> Unit,
    onDelete: () -> Unit,
    onAddTo: (WallpaperCollection?) -> Unit,
    onNewCollection: () -> Unit,
) {
    val n = state.flow.selection.size
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (n == 0) "Tap pictures to select them" else if (n == 1) "1 picture selected" else "$n pictures selected",
            modifier = Modifier.padding(bottom = 8.dp),
            style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((6 * themeGap).dp)) {
            SmallChip(LauncherIcons.Heart, "Favourite", color, Modifier.weight(1f)) { onFavourite() }
            SmallChip(LauncherIcons.Layers, "Collection", color, Modifier.weight(1f)) { onCollection() }
            SmallChip(LauncherIcons.Recents, "Recent", color, Modifier.weight(0.9f)) { onForget() }
            SmallChip(LauncherIcons.Delete, "Delete", color, Modifier.weight(0.9f)) { onDelete() }
        }
        androidx.compose.animation.AnimatedVisibility(state.collectionChooser) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy((6 * themeGap).dp),
            ) {
                TabChip("Favourites", false, color) { onAddTo(null) }
                for (collection in library.collections) TabChip(collection.name, false, color) { onAddTo(collection) }
                TabChip("+ New collection", false, color) { onNewCollection() }
            }
        }
    }
}

/** 0.16.0: the peek's bar, low in thumb reach: back to the library, or pick the picture. */
@Composable
private fun PeekBar(
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    color: Color,
    canPick: Boolean,
    modifier: Modifier,
    onBack: () -> Unit,
    onPick: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.enter()) }
    Row(
        modifier
            .navigationBarsPadding()
            .padding(12.dp)
            .graphicsLayer {
                val p = appear.value
                alpha = p.coerceIn(0f, 1f)
                translationY = (1f - p) * 40.dp.toPx()
            }
            .liquidGlass(backdrop, shape, glass.style(Surfaces.Menu, lightBackground))
            .clip(shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy((10 * themeGap).dp),
    ) {
        SheetButton("Back", color, filled = false, modifier = Modifier.weight(1f)) { onBack() }
        if (canPick) SheetButton("Pick", color, filled = true, modifier = Modifier.weight(1f)) { onPick() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderGrid(
    folder: LinkedFolder,
    picked: String?,
    color: Color,
    controller: HomeController,
    grid: LazyGridState,
    choose: (WallpaperPick) -> Unit,
    peek: (WallpaperPick) -> Unit,
) {
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
        state = grid,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(list, key = { _, it -> it.uri.toString() }) { index, image ->
            val thumb by produceState<ImageBitmap?>(null, image.uri) { value = controller.data.wallpapers.thumbnail(image.uri) }
            val pick = WallpaperPick.External(image.uri, folder.name)
            Thumb(
                thumb, image.uri.toString() == picked, null, false, color, "Folder picture ${index + 1}",
                onHold = { peek(pick) },
            ) { choose(pick) }
        }
    }
}

@Composable
private fun LibraryThumb(
    item: WallpaperItem,
    index: Int,
    picked: Boolean,
    /** Null when not selecting; else whether this one is selected. */
    selected: Boolean?,
    color: Color,
    controller: HomeController,
    onHold: () -> Unit,
    onClick: () -> Unit,
) {
    val thumb by produceState<ImageBitmap?>(null, item.id) { value = controller.data.wallpapers.thumbnail(item) }
    // Read by the emulator test and by TalkBack: which picture, and whether it is a favourite, picked or selected.
    val description = buildString {
        append("Wallpaper ").append(index + 1)
        if (item.favourite) append(", favourite")
        if (picked) append(", picked")
        if (selected == true) append(", selected")
    }
    Thumb(thumb, picked, selected, item.favourite, color, description, onHold, onClick)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Thumb(
    image: ImageBitmap?,
    picked: Boolean,
    /** Null when not selecting; else whether this one is selected. */
    selected: Boolean?,
    favourite: Boolean,
    color: Color,
    description: String,
    onHold: (() -> Unit)?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    // 0.11.1: picking is the change you see here: the ring thickens, the picture settles in a little and a check pops in.
    val ring by androidx.compose.animation.core.animateFloatAsState(
        if (picked || selected == true) 1f else 0f,
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
            .combinedClickable(onClick = onClick, onLongClick = onHold)
            .semantics { contentDescription = description },
    ) {
        if (image != null) {
            Image(
                image, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val s = 1f - 0.07f * ring.coerceIn(0f, 1.2f)
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
                .border((0.5f + 1.7f * ring.coerceIn(0f, 1f)).dp, color.copy(alpha = 0.25f + 0.7f * ring.coerceIn(0f, 1f)), shape),
        )
        if (selected != null) {
            // 0.16.0: selecting: an empty circle, a filled check once chosen.
            Box(Modifier.align(Alignment.TopStart).padding(6.dp)) {
                if (selected) ThemeCheck(true, color, 18.dp)
                else Box(Modifier.size(18.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.25f)).border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape))
            }
        } else if (ring > 0.01f) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .graphicsLayer {
                        val p = ring.coerceIn(0f, 1.15f)
                        alpha = p.coerceAtMost(1f)
                        scaleX = 0.5f + 0.5f * p
                        scaleY = 0.5f + 0.5f * p
                    },
            ) { ThemeCheck(true, color, 18.dp) }
        }
        if (favourite) {
            // 0.16.0 (Med: the heart vanished when a picture was picked): on a dark badge, inside the picture, above the ring.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) { LauncherIcon(LauncherIcons.Heart, contentDescription = null, tint = Color.White, size = 12.dp) }
        }
    }
}

/** 0.16.0: a round icon button of the library's header (order and filter, select); filled while on. */
@Composable
private fun IconChip(icon: Int, label: String, on: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (on) 0.22f else 0.08f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LauncherIcon(icon, contentDescription = null, tint = color, size = 18.dp) }
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
    Column(
        modifier
            .navigationBarsPadding()
            .padding(12.dp)
            .graphicsLayer {
                val p = appear.value
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
            SheetButton("Done", color, filled = true, modifier = Modifier.weight(1f)) {
                state.flow = state.flow.done()
                DebugLog.event("Wallpaper", "placed: the picture stays on the screen until Apply, Cancel or another pick")
            }
        }
    }
}

// ---- blur and dim ----

@Composable
private fun LookPage(state: StudioState, glass: GlassConfig, color: Color, layoutName: String?, setLockPicture: () -> Unit) {
    Segmented(listOf("home" to "Home", "picture" to "This picture", "drawer" to "Drawer", "search" to "Search"), state.surface, color) { state.surface = it }
    Spacer(Modifier.height(8.dp))
    when (state.surface) {
        "home" -> {
            // 0.16.0 (D202): the home look is the layout's own (blur and dim always were; colour and tint are new).
            if (layoutName != null) PageLabel("For the layout \"$layoutName\"", color)
            ValueSlider("Blur", state.homeBlur, 0, 40, "dp", color) { state.homeBlur = it }
            ValueSlider("Dim", state.homeDim, 0, 80, "%", color) { state.homeDim = it }
            ValueSlider("Colour", state.homeSaturation, 0, 200, "%", color) { state.homeSaturation = it }
            TintRow(state.homeTint, color) { state.homeTint = it }
            if (state.homeTint.isNotEmpty()) ValueSlider("Tint", state.homeTintAmount, 0, 60, "%", color) { state.homeTintAmount = it }
            if (state.pictureLook != null) PageLabel("This picture has its own look (see \"This picture\"), which comes first", color)
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
    // 0.16.0 (D203): the lock screen left alone, the same as the home screen, or a picture of its own.
    PageLabel("Lock screen", color)
    Segmented(listOf("off" to "Left alone", "same" to "Same", "chosen" to "Its own"), state.lockMode, color) { state.lockMode = it }
    if (state.lockMode == "chosen") {
        Spacer(Modifier.height(8.dp))
        SmallChip(LauncherIcons.Lock, "Use the picture picked for the lock screen", color, Modifier.fillMaxWidth()) { setLockPicture() }
    }
}

/** 0.16.0 (D202): the tint over the layout's wallpaper: none, or one of a few colours. */
@Composable
private fun TintRow(value: String, color: Color, onPick: (String) -> Unit) {
    val tints = listOf("", "#000000", "#FFFFFF", "#1E88E5", "#8E24AA", "#E53935", "#FB8C00", "#43A047", "#00ACC1")
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Tint", modifier = Modifier.width(44.dp), style = TextStyle(fontFamily = HomeTheme.font, color = color, fontSize = 14.sp))
        for (hex in tints) {
            val on = value.equals(hex, ignoreCase = true)
            val fill = dev.liquid.launcher.core.settings.ColorSetting.parse(hex)?.let { Color(it) }
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(fill ?: color.copy(alpha = 0.08f))
                    .border(if (on) 2.5.dp else 0.5.dp, color.copy(alpha = if (on) 0.95f else 0.3f), CircleShape)
                    .clickable { onPick(hex) }
                    .semantics { contentDescription = if (hex.isEmpty()) "No tint" else "Tint $hex" },
                contentAlignment = Alignment.Center,
            ) { if (fill == null) Text("–", style = TextStyle(color = color, fontSize = 12.sp)) }
        }
    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabChip(label: String, on: Boolean, color: Color, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    if (LocalControls.current?.theme != null) {
        ThemeChip(label, on, height = 32.dp, onLongClick = onLongClick, onClick = onClick)
        return
    }
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = if (on) 0.22f else 0.06f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
