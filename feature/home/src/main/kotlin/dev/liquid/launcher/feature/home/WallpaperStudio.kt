package dev.liquid.launcher.feature.home

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.designsystem.GlassQuality
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import kotlinx.coroutines.launch
import java.io.File

/**
 * What the wallpaper screen is trying out. The home screen draws these values instead of the
 * saved ones while the screen is open, so every change is seen on the real thing.
 */
@Stable
internal class StudioState(store: SettingsStore) {
    /** A picked image not applied yet, or null for the current wallpaper. */
    var candidate by mutableStateOf<Bitmap?>(null)
    var candidateImage by mutableStateOf<ImageBitmap?>(null)

    /** "home", "drawer" or "search": which screen the preview shows. */
    var surface by mutableStateOf("home")
    var homeBlur by mutableIntStateOf(store.get(LauncherSettings.WallpaperHomeBlur))
    var homeDim by mutableIntStateOf(store.get(LauncherSettings.WallpaperHomeDim))
    var drawerBlur by mutableIntStateOf(store.get(LauncherSettings.DrawerHomeBlur))
    var drawerDim by mutableIntStateOf(store.get(LauncherSettings.DrawerDim))
    var searchBlur by mutableIntStateOf(store.get(LauncherSettings.SearchBlur))
    var searchDim by mutableIntStateOf(store.get(LauncherSettings.SearchDim))
    var lockToo by mutableStateOf(store.get(LauncherSettings.WallpaperLockToo))
    var busy by mutableStateOf(false)

    /** Controls hidden to look at the whole screen. */
    var peek by mutableStateOf(false)

    fun pick(bitmap: Bitmap?) {
        candidate = bitmap
        candidateImage = bitmap?.asImageBitmap()
    }
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
    val history by store.history.collectAsState()
    val current by store.image.collectAsState()
    val scope = rememberCoroutineScope()
    val color = if (glass.lightGlass(Surfaces.Menu, lightBackground)) Color(0xFF16181C) else Color.White
    val sheetShape = RoundedCornerShape(32.dp)
    val shown by animateFloatAsState(if (state.peek) 0f else 1f, label = "sheet")

    fun close() {
        controller.wallpaperStudio = false
    }

    fun apply() {
        val settings = controller.data.settings
        scope.launch {
            state.busy = true
            val picked = state.candidate
            val error = if (picked != null) {
                store.apply(picked, state.lockToo, settings.get(LauncherSettings.WallpaperHistory))
            } else {
                null
            }
            settings.set(LauncherSettings.WallpaperHomeBlur, state.homeBlur)
            settings.set(LauncherSettings.WallpaperHomeDim, state.homeDim)
            settings.set(LauncherSettings.DrawerHomeBlur, state.drawerBlur)
            settings.set(LauncherSettings.DrawerDim, state.drawerDim)
            settings.set(LauncherSettings.SearchBlur, state.searchBlur)
            settings.set(LauncherSettings.SearchDim, state.searchDim)
            settings.set(LauncherSettings.WallpaperLockToo, state.lockToo)
            state.busy = false
            if (error != null) controller.toast("Could not set the wallpaper: $error") else close()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Tapping the preview hides or shows the controls.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.peek = !state.peek }
        )
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
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            // Which screen to preview and adjust.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((id, label) in listOf("home" to "Home", "drawer" to "Drawer", "search" to "Search")) {
                    val on = state.surface == id
                    Box(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(color.copy(alpha = if (on) 0.2f else 0.06f))
                            .clickable { state.surface = id },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = TextStyle(color = color, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Picture: photos, the current one, recent ones.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Thumb(null, selected = false, color = color, label = "Photos") {
                    controller.pickImage { uri ->
                        if (uri != null) scope.launch {
                            state.busy = true
                            state.pick(store.decode(uri))
                            state.busy = false
                        }
                    }
                }
                Thumb(current, selected = state.candidate == null, color = color, label = if (current == null) "System" else null) { state.pick(null) }
                for (file in history) {
                    HistoryThumb(file, store, color) {
                        scope.launch {
                            state.busy = true
                            state.pick(store.decodeFile(file))
                            state.busy = false
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
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
                    style = TextStyle(color = color.copy(alpha = 0.7f), fontSize = 12.sp),
                )
            }
            Row(Modifier.fillMaxWidth().clickable { state.lockToo = !state.lockToo }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Also on the lock screen", modifier = Modifier.weight(1f), style = TextStyle(color = color, fontSize = 14.sp))
                Switch(
                    checked = state.lockToo,
                    onCheckedChange = { state.lockToo = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = color.copy(alpha = 0.5f), checkedThumbColor = color),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetButton("Cancel", color, filled = false, modifier = Modifier.weight(1f)) { close() }
                SheetButton(if (state.busy) "Working…" else "Apply", color, filled = true, modifier = Modifier.weight(1f)) { if (!state.busy) apply() }
            }
        }
        if (state.peek) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
                    .liquidGlass(backdrop, RoundedCornerShape(50), glass.style(Surfaces.Menu, lightBackground))
                    .clip(RoundedCornerShape(50))
                    .clickable { state.peek = false }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) { Text("Show controls", style = TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)) }
        }
    }
}

@Composable
private fun Thumb(image: ImageBitmap?, selected: Boolean, color: Color, label: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(width = 52.dp, height = 92.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.1f))
            .border(if (selected) 2.dp else 1.dp, color.copy(alpha = if (selected) 0.95f else 0.25f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (label != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (image == null && label == "Photos") LauncherIcon(LauncherIcons.Add, contentDescription = null, tint = color, size = 22.dp)
                Text(label, style = TextStyle(color = color, fontSize = 11.sp))
            }
        }
    }
}

@Composable
private fun HistoryThumb(file: File, store: WallpaperStore, color: Color, onClick: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(null, file) { value = store.thumbnail(file) }
    Thumb(thumb, selected = false, color = color, label = null, onClick = onClick)
}

@Composable
private fun ValueSlider(label: String, value: Int, min: Int, max: Int, unit: String, color: Color, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(44.dp), style = TextStyle(color = color, fontSize = 14.sp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color.copy(alpha = 0.8f), inactiveTrackColor = color.copy(alpha = 0.2f)),
        )
        Text("$value $unit", modifier = Modifier.width(52.dp).padding(start = 8.dp), style = TextStyle(color = color, fontSize = 13.sp))
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
    ) { Text(label, style = TextStyle(color = if (filled) inverse else color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)) }
}
