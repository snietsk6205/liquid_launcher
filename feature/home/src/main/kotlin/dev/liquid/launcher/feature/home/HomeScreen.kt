package dev.liquid.launcher.feature.home

import android.app.Activity
import android.app.ActivityOptions
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.graphics.Rect
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.liquid.launcher.core.designsystem.GlassQuality
import dev.liquid.launcher.core.designsystem.LauncherIcon
import dev.liquid.launcher.core.designsystem.LauncherIcons
import dev.liquid.launcher.core.designsystem.WallpaperStore
import dev.liquid.launcher.core.designsystem.liquidGlass
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore
import dev.liquid.launcher.core.settings.observe
import dev.liquid.launcher.feature.redirect.RedirectStatus
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Drives the "content settles in" animation that masks the hand-over from the system launcher. */
@Stable
class HomeReturnState {
    /** 0 = just returned (content hidden), 1 = settled. */
    val progress = mutableFloatStateOf(1f)
    var tick by mutableIntStateOf(0)
        private set

    /**
     * Call before the first frame after the activity becomes visible again. Setting the
     * progress here, not in an effect, means no frame is drawn with the content fully visible.
     */
    fun onReturn(animate: Boolean) {
        if (animate) progress.floatValue = 0f
        tick++
    }
}

/**
 * Temporary home screen for Phase 1A: wallpaper, clock, all apps and a glass bar. It exists so
 * the redirect has something real to show and so glass can be judged on the device.
 */
@Composable
fun HomeScreen(
    store: SettingsStore,
    wallpaper: WallpaperStore,
    apps: AppsRepository,
    returnState: HomeReturnState,
    onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val context = LocalContext.current
    val image by wallpaper.image.collectAsState()
    val appList by apps.apps.collectAsState()
    val columns by store.observe(LauncherSettings.HomeColumns)
    val showLabels by store.observe(LauncherSettings.HomeLabels)
    val clock24h by store.observe(LauncherSettings.Clock24h)
    val settleStyle by store.observe(LauncherSettings.SettleStyle)
    val settleDuration by store.observe(LauncherSettings.SettleDuration)
    val glassQuality by store.observe(LauncherSettings.GlassQuality)
    val pauseSeconds by store.observe(LauncherSettings.SystemHomePause)

    val darkText = remember(image) { wallpaperSupportsDarkText(context) }
    val contentColor = if (darkText) Color(0xFF16181C) else Color.White
    val shadow = if (darkText) null else Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 4f)

    // Status and navigation bar icons follow the wallpaper's brightness.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = darkText
            controller.isAppearanceLightNavigationBars = darkText
        }
    }

    LaunchedEffect(returnState.tick) {
        val progress = returnState.progress
        if (progress.floatValue < 1f) {
            if (settleStyle == "none" || settleDuration == 0) {
                progress.floatValue = 1f
            } else {
                animate(0f, 1f, animationSpec = tween(settleDuration)) { value, _ ->
                    progress.floatValue = value
                }
            }
        }
    }

    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            image?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = returnState.progress.floatValue
                        alpha = p
                        if (settleStyle == "scale") {
                            val s = 0.94f + 0.06f * p
                            scaleX = s
                            scaleY = s
                        }
                    }
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Clock(clock24h, contentColor, shadow)
                StatusChips(contentColor, onOpenSetup)
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(appList, key = { it.key }) { app ->
                        AppIcon(app, showLabels, contentColor, shadow, apps)
                    }
                }
            }
        }

        // The glass bar is a sibling of the recorded content, never a child of it.
        val barShape = RoundedCornerShape(28.dp)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxWidth()
                .height(56.dp)
                .liquidGlass(
                    backdrop = backdrop,
                    shape = barShape,
                    quality = GlassQuality.fromSetting(glassQuality),
                    tint = if (darkText) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.18f),
                )
                .clip(barShape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(
                icon = LauncherIcons.Settings,
                label = "Settings",
                color = contentColor,
                modifier = Modifier.weight(1f),
                onClick = onOpenSettings,
            )
            BarButton(
                icon = LauncherIcons.Home,
                label = "System launcher",
                color = contentColor,
                modifier = Modifier.weight(1f),
                onClick = { RedirectStatus.openSystemLauncher(context, pauseSeconds * 1000L) },
            )
        }
    }
}

@Composable
private fun Clock(clock24h: Boolean, color: Color, shadow: Shadow?) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000 + 25)
        }
    }
    val time = remember(now, clock24h) {
        LocalTime.now().format(DateTimeFormatter.ofPattern(if (clock24h) "HH:mm" else "h:mm", Locale.getDefault()))
    }
    val date = remember(now) {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
    Text(
        text = time,
        style = TextStyle(color = color, fontSize = 92.sp, fontWeight = FontWeight.Light, shadow = shadow),
    )
    Text(
        text = date,
        style = TextStyle(color = color.copy(alpha = 0.8f), fontSize = 16.sp, shadow = shadow),
    )
}

@Composable
private fun StatusChips(color: Color, onOpenSetup: () -> Unit) {
    val connected by RedirectStatus.connected.collectAsState()
    val blocked by RedirectStatus.launchBlocked.collectAsState()
    val pausedUntil by RedirectStatus.pausedUntilWall.collectAsState()
    val now by produceState(System.currentTimeMillis(), pausedUntil) {
        while (pausedUntil > System.currentTimeMillis()) {
            value = System.currentTimeMillis()
            delay(1000)
        }
        value = System.currentTimeMillis()
    }
    val paused = pausedUntil > now

    if (!connected) {
        Chip("Redirect service is off. Tap to set up", color, onOpenSetup)
    } else if (blocked) {
        Chip("The launcher could not come to the front. Tap for help", color, onOpenSetup)
    } else if (paused) {
        val seconds = ((pausedUntil - now) / 1000).coerceAtLeast(0)
        Chip("Redirect paused ($seconds s). Tap to resume", color) { RedirectStatus.resume() }
    }
}

@Composable
private fun Chip(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIcon(
    app: AppEntry,
    showLabel: Boolean,
    color: Color,
    shadow: Shadow?,
    apps: AppsRepository,
) {
    val view = LocalView.current
    // Plain holder: updating it must not recompose.
    val bounds = remember { Rect() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                val b = it.boundsInWindow()
                bounds.set(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
            }
            .combinedClickable(
                onClick = {
                    val options = ActivityOptions.makeClipRevealAnimation(
                        view, bounds.left, bounds.top, bounds.width(), bounds.height(),
                    )
                    apps.launch(app, Rect(bounds), options.toBundle())
                },
                onLongClick = { apps.showAppInfo(app, Rect(bounds)) },
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(bitmap = icon, contentDescription = app.label, modifier = Modifier.size(52.dp))
        } else {
            Spacer(Modifier.size(52.dp))
        }
        if (showLabel) {
            Text(
                text = app.label,
                modifier = Modifier
                    .padding(top = 4.dp, start = 2.dp, end = 2.dp)
                    .fillMaxWidth(),
                style = TextStyle(color = color, fontSize = 11.sp, textAlign = TextAlign.Center, shadow = shadow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BarButton(icon: Int, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherIcon(icon, contentDescription = null, tint = color, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, style = TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium))
    }
}

/** True when the wallpaper is light enough that dark text reads better. Needs no permission. */
private fun wallpaperSupportsDarkText(context: android.content.Context): Boolean = try {
    val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
    colors != null && (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
} catch (e: Exception) {
    false
}
