package dev.liquid.launcher.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import dev.liquid.launcher.core.data.LauncherData
import dev.liquid.launcher.core.designsystem.WallpaperStore

/** Settings sections that show a live preview, and what the preview shows. */
object HomePreviews {
    val sections = setOf("home", "icons", "clock", "pill", "glass", "drawer", "tags", "drawerMotion", "menu")

    fun modeFor(sectionId: String): HomeMode = when (sectionId) {
        "drawer", "tags" -> HomeMode.PreviewDrawer
        "drawerMotion" -> HomeMode.PreviewDrawerMotion
        "menu" -> HomeMode.PreviewMenu
        else -> HomeMode.Preview
    }
}

/**
 * The real home screen, drawn at full phone size and scaled down to fit, with touches
 * blocked. It reads the same settings as the live home, so every change shows at once.
 */
@Composable
fun HomeSectionPreview(sectionId: String) {
    val context = LocalContext.current
    val data = remember { LauncherData.get(context) }
    val wallpaper = remember { WallpaperStore.get(context) }
    val returnState = remember { HomeReturnState() }
    val callbacks = remember { HomeCallbacks({}, {}) }
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val screenW = with(density) { window.width.toDp() }
    val screenH = with(density) { window.height.toDp() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = minOf(maxWidth / screenW, maxHeight / screenH)
        Box(
            Modifier
                .requiredSize(screenW, screenH)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            HomeScreen(data, wallpaper, returnState, callbacks, HomePreviews.modeFor(sectionId))
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
        )
    }
}
